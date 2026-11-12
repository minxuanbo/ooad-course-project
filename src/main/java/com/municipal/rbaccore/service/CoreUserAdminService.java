package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbac.ApiException;
import com.municipal.rbac.RequestAuditFilter;
import com.municipal.rbaccore.model.Department;
import com.municipal.rbaccore.model.Role;
import com.municipal.rbaccore.model.User;
import com.municipal.rbaccore.repository.ApiResourceRepository;
import com.municipal.rbaccore.repository.DepartmentRepository;
import com.municipal.rbaccore.repository.RoleRepository;
import com.municipal.rbaccore.repository.UserRepository;
import com.municipal.rbaccore.security.CurrentIdentity;
import com.municipal.rbaccore.security.RbacCatalog;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerMapping;

/** T11人员在部门范围内的角色分配、停用与调动，复用共同根锁及完整K保护。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoreUserAdminService {
  private final CoreAuthService auth;
  private final EffectivePermissionService permissions;
  private final DepartmentScopeService scope;
  private final MaintenanceGuard guard;
  private final UserRepository users;
  private final RoleRepository roles;
  private final DepartmentRepository departments;
  private final ApiResourceRepository apis;

  /** 当前角色读取保留全部已保存元数据，含停用或现已不可分配项。 */
  public UserAdminModels.RolesViewResult roles(String header, Long id) {
    CurrentIdentity actor = auth.require(header);
    User target = target(actor, id);
    return rolesView(target);
  }

  /** 候选只包含启用角色，且其有效API集合为本人有效API集合的子集。 */
  public UserAdminModels.AssignablePageResult assignableRoles(
      String header, Long id, UserAdminModels.AssignableSearch query) {
    CurrentIdentity actor = auth.require(header);
    target(actor, id);
    Set<RbacCatalog.Route> actorRoutes = permissions.routes(actor.id());
    String search = query.getQ() == null ? "" : query.getQ().toLowerCase(Locale.ROOT);
    List<RoleModels.RoleResult> candidates =
        roles.findByEnabledTrueOrderByIdAsc().stream()
            .filter(role -> matches(role, search))
            .filter(role -> actorRoutes.containsAll(roleRoutes(role)))
            .map(this::roleResult)
            .toList();
    long total = candidates.size();
    long offset = (long) query.getPage() * query.getSize();
    if (offset >= total) {
      return new UserAdminModels.AssignablePageResult(
          List.of(), total, query.getPage(), query.getSize());
    }
    int from = (int) offset;
    int to = (int) Math.min(offset + query.getSize(), total);
    return new UserAdminModels.AssignablePageResult(
        candidates.subList(from, to), total, query.getPage(), query.getSize());
  }

  /** 整组校验后在同一写事务内整体替换，失败回滚全部关联。 */
  @Transactional
  public UserAdminModels.UserWithRolesResult replaceRoles(
      String header, Long id, UserAdminModels.ReplaceRolesCommand command) {
    return write(
        header,
        actor -> {
          User target = target(actor, id);
          requireSelfProtection(actor, target);
          requireGlobalAdministratorProtection(actor, target);
          Set<Long> wanted = new LinkedHashSet<>(command.getIds());
          if (wanted.size() != command.getIds().size()) {
            throw ApiException.bad("INVALID_ARGUMENT", "角色ID不能重复");
          }
          List<Role> selected = roles.findAllById(wanted);
          if (selected.size() != wanted.size()) {
            throw ApiException.bad("INVALID_ARGUMENT", "存在未知的角色ID");
          }
          Set<RbacCatalog.Route> actorRoutes = permissions.routes(actor.id());
          for (Role role : selected) {
            if (!role.isEnabled() || !actorRoutes.containsAll(roleRoutes(role))) {
              throw ApiException.scopeDenied();
            }
          }
          for (Role existing : List.copyOf(target.getRoles())) {
            if (!wanted.contains(existing.getId())) target.removeRole(existing);
          }
          for (Role role : selected) target.assignRole(role);
          users.saveAndFlush(target);
          return withRoles(target);
        });
  }

  /** 停用立即生效，保留部门及角色关系；本票不提供人员恢复。 */
  @Transactional
  public UserAdminModels.UserWithRolesResult disable(
      String header, Long id, UserAdminModels.DisableCommand command) {
    return write(
        header,
        actor -> {
          if (!Boolean.FALSE.equals(command.getEnabled())) {
            throw ApiException.bad("INVALID_ARGUMENT", "本票只支持停用人员");
          }
          User target = target(actor, id);
          requireGlobalAdministratorProtection(actor, target);
          target.setEnabled(false);
          users.saveAndFlush(target);
          return withRoles(target);
        });
  }

  /** 原部门与目标部门都必须在本部门及下级范围，失败保留原归属与授权。 */
  @Transactional
  public UserAdminModels.UserWithRolesResult move(
      String header, Long id, UserAdminModels.MoveCommand command) {
    return write(
        header,
        actor -> {
          User target = target(actor, id);
          requireSelfProtection(actor, target);
          requireGlobalAdministratorProtection(actor, target);
          Long departmentId = command.getDepartmentId();
          requireInScope(actor, departmentId);
          Department department =
              departments.findById(departmentId).orElseThrow(ApiException::scopeDenied);
          target.moveTo(department);
          users.saveAndFlush(target);
          return withRoles(target);
        });
  }

  /** 与T07/T09共用同一根锁：加锁后重新鉴权、校验并修改，最后检查完整K。 */
  private <T> T write(String header, Function<CurrentIdentity, T> change) {
    try {
      guard.lock();
      CurrentIdentity actor = reauthenticate(header);
      T result = change.apply(actor);
      guard.retainOne();
      return result;
    } catch (DataIntegrityViolationException | ConcurrencyFailureException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "DATA_CONFLICT", "人员保存冲突，请刷新后重试");
    } catch (DataAccessException exception) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "人员保存失败，请重试");
    }
  }

  /** 拿锁后按Spring服务端方法与模板重新鉴权，不能沿用加锁前的授权结论。 */
  private CurrentIdentity reauthenticate(String header) {
    CurrentIdentity actor = auth.require(header);
    HttpServletRequest request = RequestAuditFilter.current();
    if (request == null) throw ApiException.unauthorized();
    String template =
        String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    if (!permissions.allows(actor.id(), request.getMethod(), template)) {
      throw ApiException.forbidden();
    }
    return actor;
  }

  /** 部门管理员不能修改自己的授权或所属部门，共同根账号仍受完整K约束。 */
  private void requireSelfProtection(CurrentIdentity actor, User target) {
    if (actor.id().equals(target.getId()) && !scope.isGlobal(actor)) {
      throw ApiException.scopeDenied();
    }
  }

  /** 部门管理员不能修改其他有效全局管理员，全局操作者的最后管理员保护由锁内K检查负责。 */
  private void requireGlobalAdministratorProtection(CurrentIdentity actor, User target) {
    if (actor.id().equals(target.getId()) || scope.isGlobal(actor)) return;
    if (permissions.routes(target.getId()).containsAll(RbacCatalog.maintenanceRoutes())) {
      throw ApiException.scopeDenied();
    }
  }

  /** 范围外对象不暴露是否存在；已获共同根范围者请求未知目标返回404。 */
  private void requireInScope(CurrentIdentity actor, Long departmentId) {
    if (scope.allowedIds(actor).contains(departmentId)) return;
    if (scope.isGlobal(actor) && !departments.existsById(departmentId)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "部门不存在");
    }
    throw ApiException.scopeDenied();
  }

  /** 人员查询先定位目标部门，未知人员对非共同根范围统一范围拒绝。 */
  private User target(CurrentIdentity actor, Long id) {
    User user =
        users
            .findById(id)
            .orElseThrow(
                () -> {
                  if (scope.isGlobal(actor)) {
                    return new ApiException(
                        HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "人员不存在");
                  }
                  return ApiException.scopeDenied();
                });
    requireInScope(actor, user.getDepartment().getId());
    return user;
  }

  /** 候选角色贡献的有效API，沿启用角色、权限、API及同系统链计算。 */
  private Set<RbacCatalog.Route> roleRoutes(Role role) {
    return apis.roleEffectiveApis(role.getId(), RbacCatalog.SYSTEM_CODE).stream()
        .map(api -> new RbacCatalog.Route(api.getHttpMethod(), api.getPathTemplate()))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 候选搜索只匹配名称或编码，不按角色名推导授权。 */
  private boolean matches(Role role, String search) {
    if (search.isEmpty()) return true;
    return role.getName().toLowerCase(Locale.ROOT).contains(search)
        || role.getCode().toLowerCase(Locale.ROOT).contains(search);
  }

  /** 角色元数据按ID升序输出，状态显式转换不依赖primitive映射。 */
  private RoleModels.RoleResult roleResult(Role role) {
    RoleModels.RoleResult result = CloneFactory.copy(new RoleModels.RoleResult(), role);
    result.setEnabled(role.isEnabled());
    return result;
  }

  /** 当前角色包含停用项及不可再分配项，供前端清理而非静默保留。 */
  private UserAdminModels.RolesViewResult rolesView(User user) {
    List<RoleModels.RoleResult> assigned =
        user.getRoles().stream()
            .sorted(Comparator.comparing(Role::getId))
            .map(this::roleResult)
            .toList();
    return new UserAdminModels.RolesViewResult(withRoles(user), assigned);
  }

  /** 同名字段由课程CloneFactory映射，关系和状态在事务内显式补充。 */
  private UserAdminModels.UserWithRolesResult withRoles(User user) {
    UserAdminModels.UserWithRolesResult result =
        CloneFactory.copy(new UserAdminModels.UserWithRolesResult(), user);
    result.setEnabled(user.isEnabled());
    result.setDepartmentId(user.getDepartment().getId());
    result.setRoleIds(
        user.getRoles().stream().map(Role::getId).sorted().collect(Collectors.toList()));
    return result;
  }
}
