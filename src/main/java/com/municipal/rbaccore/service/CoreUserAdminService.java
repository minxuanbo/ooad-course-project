package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbac.ApiException;
import com.municipal.rbac.RequestAuditFilter;
import com.municipal.rbaccore.model.ApiResource;
import com.municipal.rbaccore.model.Department;
import com.municipal.rbaccore.model.Permission;
import com.municipal.rbaccore.model.Role;
import com.municipal.rbaccore.model.User;
import com.municipal.rbaccore.repository.DepartmentRepository;
import com.municipal.rbaccore.repository.RoleRepository;
import com.municipal.rbaccore.repository.UserRepository;
import com.municipal.rbaccore.security.CurrentIdentity;
import com.municipal.rbaccore.security.RbacCatalog;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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

  /** 当前角色读取保留全部已保存元数据，含停用或现已不可分配项。 */
  public UserAdminModels.RolesViewResult roles(String header, Long id) {
    CurrentIdentity actor = auth.require(header);
    User target = target(actor, id);
    return rolesView(target);
  }

  /** 候选只包含启用角色，且其有效权限ID集合为本人有效权限ID集合的子集。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public UserAdminModels.AssignablePageResult assignableRoles(
      String header, Long id, UserAdminModels.AssignableSearch query) {
    CurrentIdentity actor = auth.require(header);
    target(actor, id);
    String search = query.getQ() == null ? "" : query.getQ().toLowerCase(Locale.ROOT);
    Specification<Role> filter = assignableFilter(ownPermissionIds(actor), search);
    long total = roles.count(filter);
    long offset = (long) query.getPage() * query.getSize();
    if (offset >= total) {
      return new UserAdminModels.AssignablePageResult(
          List.of(), total, query.getPage(), query.getSize());
    }
    List<RoleModels.RoleResult> items =
        roles
            .findAll(filter, PageRequest.of(query.getPage(), query.getSize(), Sort.by("id")))
            .getContent()
            .stream()
            .map(this::roleResult)
            .toList();
    return new UserAdminModels.AssignablePageResult(items, total, query.getPage(), query.getSize());
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
          List<Role> selected = roles.findAllById(wanted);
          if (selected.size() != wanted.size()) {
            throw ApiException.bad("INVALID_ARGUMENT", "存在未知的角色ID");
          }
          Specification<Role> allowed =
              assignableFilter(ownPermissionIds(actor), "")
                  .and((role, query, builder) -> role.get("id").in(wanted));
          if (!wanted.isEmpty() && roles.count(allowed) != wanted.size()) {
            throw ApiException.scopeDenied();
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
  public UserAdminModels.UserWithRolesResult disable(String header, Long id) {
    return write(
        header,
        actor -> {
          User target = target(actor, id);
          requireGlobalAdministratorProtection(actor, target);
          target.setEnabled(false);
          users.saveAndFlush(target);
          return withRoles(target);
        });
  }

  /** 参数解析前只做最新身份、具体API、目标范围及适用的人员保护，写事务仍锁后重验。 */
  public void checkTarget(String header, Long id, boolean protectSelf) {
    CurrentIdentity actor = reauthenticate(header);
    if (id == null) return;
    User target = target(actor, id);
    if (protectSelf) requireSelfProtection(actor, target);
    requireGlobalAdministratorProtection(actor, target);
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
      throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "人员保存失败，请重试");
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
    CurrentIdentity subject =
        new CurrentIdentity(target.getId(), target.getUsername(), target.getDepartment().getId());
    if (scope.isGlobal(subject)
        && permissions.routes(target.getId()).containsAll(RbacCatalog.maintenanceRoutes())) {
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
                    return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "人员不存在");
                  }
                  return ApiException.scopeDenied();
                });
    requireInScope(actor, user.getDepartment().getId());
    return user;
  }

  /** 权限身份而不是API路径才是角色授权子集；同API可被不同权限复用。 */
  private Set<Long> ownPermissionIds(CurrentIdentity actor) {
    return permissions.permissions(actor.id()).stream()
        .map(Permission::getId)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 数据库排除任何超出本人有效权限的角色，外系统权限不因过滤而变成空集。 */
  private Specification<Role> assignableFilter(Set<Long> ownIds, String search) {
    String pattern =
        "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    return (root, query, builder) -> {
      Subquery<Long> forbidden = query.subquery(Long.class);
      Root<Role> candidate = forbidden.correlate(root);
      Join<Role, Permission> permission = candidate.join("permissions");
      Join<Permission, ApiResource> api = permission.join("apis");
      Predicate outside =
          ownIds.isEmpty() ? builder.conjunction() : builder.not(permission.get("id").in(ownIds));
      forbidden
          .select(permission.get("id"))
          .where(
              builder.isTrue(permission.get("enabled")),
              builder.isTrue(permission.get("system").get("enabled")),
              builder.isTrue(api.get("enabled")),
              builder.equal(permission.get("system").get("id"), api.get("system").get("id")),
              outside);
      return builder.and(
          builder.isTrue(root.get("enabled")),
          builder.not(builder.exists(forbidden)),
          builder.or(
              builder.like(builder.lower(root.get("name")), pattern, '\\'),
              builder.like(builder.lower(root.get("code")), pattern, '\\')));
    };
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
