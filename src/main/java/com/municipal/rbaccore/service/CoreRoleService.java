package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbac.ApiException;
import com.municipal.rbaccore.model.Permission;
import com.municipal.rbaccore.model.Role;
import com.municipal.rbaccore.repository.PermissionRepository;
import com.municipal.rbaccore.repository.RoleRepository;
import com.municipal.rbaccore.security.RbacCatalog;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** T09角色定义和直接权限维护，复用统一授权、共同根锁及完整K保护。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreRoleService {
  private final RoleRepository roles;
  private final PermissionRepository permissions;
  private final GlobalManagementAccess access;
  private final MaintenanceGuard maintenance;

  /** 名称/编码与状态以AND组合，固定ID升序；越末页保留同条件总数。 */
  public RoleModels.PageResult list(String header, RoleModels.Search query) {
    access.require(header);
    String search = query.getQ() == null ? "" : query.getQ().toLowerCase(Locale.ROOT);
    String pattern =
        "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    Specification<Role> filter =
        (root, criteria, builder) ->
            builder.and(
                builder.or(
                    builder.like(builder.lower(root.get("name")), pattern, '\\'),
                    builder.like(builder.lower(root.get("code")), pattern, '\\')),
                query.getEnabled() == null
                    ? builder.conjunction()
                    : builder.equal(root.get("enabled"), query.getEnabled()));
    long total = roles.count(filter);
    if ((long) query.getPage() * query.getSize() >= total) {
      return new RoleModels.PageResult(List.of(), total, query.getPage(), query.getSize());
    }
    List<RoleModels.RoleResult> items =
        roles
            .findAll(filter, PageRequest.of(query.getPage(), query.getSize(), Sort.by("id")))
            .getContent()
            .stream()
            .map(this::result)
            .toList();
    return new RoleModels.PageResult(items, total, query.getPage(), query.getSize());
  }

  /** 返回保存真值而非有效权限投影，停用项仍可识别和移除。 */
  public RoleModels.PermissionsResult selected(String header, Long id) {
    access.require(header);
    return selectedResult(role(id));
  }

  /** 创建空权限启用角色；编码判重与数据库唯一约束均不按角色名授权。 */
  @Transactional
  public RoleModels.RoleResult create(String header, RoleModels.CreateCommand command) {
    return write(
        header,
        () -> {
          if (roles.existsByCode(command.getCode())) {
            throw new ApiException(HttpStatus.CONFLICT, "DATA_CONFLICT", "角色编码已存在");
          }
          return result(roles.saveAndFlush(new Role(command.getCode(), command.getName())));
        });
  }

  /** 更名及启停在同一事务提交，保护失败时所有字段一起回滚。 */
  @Transactional
  public RoleModels.RoleResult patch(String header, Long id, RoleModels.PatchCommand command) {
    return write(
        header,
        () -> {
          Role role = role(id);
          if (command.getName() != null) role.rename(command.getName());
          if (command.getEnabled() != null) role.setEnabled(command.getEnabled());
          return result(roles.saveAndFlush(role));
        });
  }

  /** 先校验完整集合及既有系统边界，再整体替换；不静默清理外系统授权。 */
  @Transactional
  public RoleModels.PermissionsResult replacePermissions(
      String header, Long id, RoleModels.PermissionsCommand command) {
    return write(
        header,
        () -> {
          Role role = role(id);
          if (role.getPermissions().stream()
              .anyMatch(
                  permission ->
                      !RbacCatalog.SYSTEM_CODE.equals(permission.getSystem().getCode()))) {
            throw ApiException.bad("INVALID_ARGUMENT", "角色包含当前范围之外的授权，请先复核所属系统");
          }
          List<Permission> selected = permissions.findAllById(command.getIds());
          if (selected.size() != command.getIds().size()
              || selected.stream()
                  .anyMatch(
                      permission ->
                          !permission.isEnabled()
                              || !permission.getSystem().isEnabled()
                              || !RbacCatalog.SYSTEM_CODE.equals(
                                  permission.getSystem().getCode()))) {
            throw ApiException.bad("INVALID_ARGUMENT", "只能保存本权限系统的启用权限");
          }
          Set<Long> wanted = new HashSet<>(command.getIds());
          for (Permission existing : List.copyOf(role.getPermissions())) {
            if (!wanted.contains(existing.getId())) role.revokePermission(existing);
          }
          for (Permission permission : selected) role.grantPermission(permission);
          roles.saveAndFlush(role);
          return selectedResult(role);
        });
  }

  /** 和T07共用同一锁，拿锁后重新鉴权；保存错误就地翻译且整个事务回滚。 */
  private <T> T write(String header, Supplier<T> change) {
    try {
      maintenance.lock();
      access.require(header);
      T result = change.get();
      maintenance.retainOne();
      return result;
    } catch (DataIntegrityViolationException | ConcurrencyFailureException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "DATA_CONFLICT", "角色保存冲突，请刷新后重试");
    } catch (DataAccessException exception) {
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "角色保存失败，请重试");
    }
  }

  /** 全局范围内未知角色返回稳定404，不泄漏表和SQL细节。 */
  private Role role(Long id) {
    return roles
        .findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "角色不存在"));
  }

  /** 同名字段由课程CloneFactory映射，primitive状态显式转换。 */
  private RoleModels.RoleResult result(Role role) {
    RoleModels.RoleResult result = CloneFactory.copy(new RoleModels.RoleResult(), role);
    result.setEnabled(role.isEnabled());
    return result;
  }

  /** 固定ID顺序，不加入祖先菜单或衍生权限。 */
  private RoleModels.PermissionsResult selectedResult(Role role) {
    return new RoleModels.PermissionsResult(
        role.getId(), role.getPermissions().stream().map(Permission::getId).sorted().toList());
  }
}
