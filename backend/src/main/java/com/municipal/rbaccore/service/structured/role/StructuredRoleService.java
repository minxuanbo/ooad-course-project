package com.municipal.rbaccore.service.structured.role;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.PermissionJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.role.CreateRoleCommand;
import com.municipal.rbaccore.service.port.role.ReplaceRolePermissionsCommand;
import com.municipal.rbaccore.service.port.role.RolePageResult;
import com.municipal.rbaccore.service.port.role.RolePermissionsResult;
import com.municipal.rbaccore.service.port.role.RoleQuery;
import com.municipal.rbaccore.service.port.role.RoleResult;
import com.municipal.rbaccore.service.port.role.RoleUseCase;
import com.municipal.rbaccore.service.port.role.UpdateRoleCommand;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import com.municipal.rbaccore.service.structured.security.StructuredGlobalManagementAccess;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** T09角色定义和直接权限维护，复用统一授权、共同根锁及完整K保护。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredRoleService implements RoleUseCase {
  private final RoleJpaRepository roles;
  private final PermissionJpaRepository permissions;
  private final StructuredGlobalManagementAccess access;

  /** 计数和取页共用快照，避免并发写入造成条目与总数不一致。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RolePageResult list(String header, RoleQuery query) {
    access.require(header);
    String search = query.getQ() == null ? "" : query.getQ().toLowerCase(Locale.ROOT);
    String pattern =
        "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    Specification<RoleEntity> filter =
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
      return new RolePageResult(List.of(), total, query.getPage(), query.getSize());
    }
    List<RoleResult> items =
        roles
            .findAll(filter, PageRequest.of(query.getPage(), query.getSize(), Sort.by("id")))
            .getContent()
            .stream()
            .map(this::result)
            .toList();
    return new RolePageResult(items, total, query.getPage(), query.getSize());
  }

  /** 返回保存真值而非有效权限投影，停用项仍可识别和移除。 */
  public RolePermissionsResult selected(String header, Long id) {
    access.require(header);
    return selectedResult(role(id));
  }

  /** 创建空权限启用角色；编码判重与数据库唯一约束均不按角色名授权。 */
  @Transactional
  public RoleResult create(String header, CreateRoleCommand command) {
    return write(
        header,
        () -> {
          if (roles.existsByCode(command.getCode())) {
            throw new BusinessException(409, "DATA_CONFLICT", "角色编码已存在");
          }
          return result(roles.saveAndFlush(new RoleEntity(command.getCode(), command.getName())));
        });
  }

  /** 更名及启停在同一事务提交，保护失败时所有字段一起回滚。 */
  @Transactional
  public RoleResult patch(String header, Long id, UpdateRoleCommand command) {
    return write(
        header,
        () -> {
          RoleEntity role = role(id);
          CloneFactory.copyNotNull(role, command);
          return result(roles.saveAndFlush(role));
        });
  }

  /** 先校验完整集合及既有系统边界，再整体替换；不静默清理外系统授权。 */
  @Transactional
  public RolePermissionsResult replacePermissions(
      String header, Long id, ReplaceRolePermissionsCommand command) {
    return write(
        header,
        () -> {
          RoleEntity role = role(id);
          if (role.getPermissions().stream()
              .anyMatch(
                  permission ->
                      !RbacCatalog.SYSTEM_CODE.equals(permission.getSystem().getCode()))) {
            throw BusinessException.bad("INVALID_ARGUMENT", "角色包含当前范围之外的授权，请先复核所属系统");
          }
          List<PermissionEntity> selected = permissions.findAllById(command.getIds());
          if (selected.size() != command.getIds().size()
              || selected.stream()
                  .anyMatch(
                      permission ->
                          !permission.isEnabled()
                              || !permission.getSystem().isEnabled()
                              || !RbacCatalog.SYSTEM_CODE.equals(
                                  permission.getSystem().getCode()))) {
            throw BusinessException.bad("INVALID_ARGUMENT", "只能保存本权限系统的启用权限");
          }
          Set<Long> wanted = new HashSet<>(command.getIds());
          for (PermissionEntity existing : List.copyOf(role.getPermissions())) {
            if (!wanted.contains(existing.getId())) role.revokePermission(existing);
          }
          for (PermissionEntity permission : selected) role.grantPermission(permission);
          roles.saveAndFlush(role);
          return selectedResult(role);
        });
  }

  /** 和T07共用同一锁，拿锁后重新鉴权；保存错误就地翻译且整个事务回滚。 */
  private <T> T write(String header, Supplier<T> change) {
    try {
      return access.write(header, change);
    } catch (DataIntegrityViolationException | ConcurrencyFailureException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", "角色保存冲突，请刷新后重试");
    } catch (DataAccessException exception) {
      throw new BusinessException(500, "INTERNAL_ERROR", "角色保存失败，请重试");
    }
  }

  /** 全局范围内未知角色返回稳定404，不泄漏表和SQL细节。 */
  private RoleEntity role(Long id) {
    return roles
        .findById(id)
        .orElseThrow(() -> new BusinessException(404, "RESOURCE_NOT_FOUND", "角色不存在"));
  }

  /** 同名字段由课程CloneFactory映射，primitive状态显式转换。 */
  private RoleResult result(RoleEntity role) {
    RoleResult result = CloneFactory.copy(new RoleResult(), role);
    result.setEnabled(role.isEnabled());
    return result;
  }

  /** 固定ID顺序，不加入祖先菜单或衍生权限。 */
  private RolePermissionsResult selectedResult(RoleEntity role) {
    return new RolePermissionsResult(
        role.getId(),
        role.getPermissions().stream().map(PermissionEntity::getId).sorted().toList());
  }
}
