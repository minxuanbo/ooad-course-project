package com.municipal.rbaccore.service.structured.user;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.role.RoleResult;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import com.municipal.rbaccore.service.port.security.RequestContext;
import com.municipal.rbaccore.service.port.user.AssignableRolePageResult;
import com.municipal.rbaccore.service.port.user.AssignableRoleQuery;
import com.municipal.rbaccore.service.port.user.ReplaceUserRolesCommand;
import com.municipal.rbaccore.service.port.user.TransferUserCommand;
import com.municipal.rbaccore.service.port.user.UserManagementUseCase;
import com.municipal.rbaccore.service.port.user.UserRolesResult;
import com.municipal.rbaccore.service.port.user.UserWithRolesResult;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.security.StructuredDepartmentScopeService;
import com.municipal.rbaccore.service.structured.security.StructuredEffectivePermissionService;
import com.municipal.rbaccore.service.structured.security.StructuredMaintenanceGuard;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** T11人员在部门范围内的角色分配、停用与调动，复用共同根锁及完整K保护。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredUserManagementService implements UserManagementUseCase {
  private final StructuredAuthenticationService auth;
  private final RequestContext requestContext;
  private final StructuredEffectivePermissionService permissions;
  private final StructuredDepartmentScopeService scope;
  private final StructuredMaintenanceGuard guard;
  private final UserJpaRepository users;
  private final com.municipal.rbaccore.service.structured.security.StructuredPersonnelAuthority
      authority;
  private final RoleJpaRepository roles;
  private final DepartmentJpaRepository departments;

  /** 当前角色读取保留全部已保存元数据，含停用或现已不可分配项。 */
  public UserRolesResult roles(String header, Long id) {
    CurrentIdentity actor = auth.require(header);
    UserEntity target = target(actor, id);
    return rolesView(target);
  }

  /** 候选只包含启用角色，且其有效权限ID集合为本人有效权限ID集合的子集。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AssignableRolePageResult assignableRoles(
      String header, Long id, AssignableRoleQuery query) {
    CurrentIdentity actor = auth.require(header);
    authority.requireManage(actor, target(actor, id));
    String search = query.getQ() == null ? "" : query.getQ().toLowerCase(Locale.ROOT);
    Specification<RoleEntity> filter =
        assignableFilter(ownPermissionIds(actor), authority.level(actor), search);
    long total = roles.count(filter);
    long offset = (long) query.getPage() * query.getSize();
    if (offset >= total) {
      return new AssignableRolePageResult(List.of(), total, query.getPage(), query.getSize());
    }
    List<RoleResult> items =
        roles
            .findAll(filter, PageRequest.of(query.getPage(), query.getSize(), Sort.by("id")))
            .getContent()
            .stream()
            .map(this::roleResult)
            .toList();
    return new AssignableRolePageResult(items, total, query.getPage(), query.getSize());
  }

  /** 整组校验后在同一写事务内整体替换，失败回滚全部关联。 */
  @Transactional
  public UserWithRolesResult replaceRoles(String header, Long id, ReplaceUserRolesCommand command) {
    return write(
        header,
        actor -> {
          UserEntity target = target(actor, id);
          authority.requireManage(actor, target);
          Set<Long> wanted = new LinkedHashSet<>(command.getIds());
          List<RoleEntity> selected = roles.findAllById(wanted);
          if (selected.size() != wanted.size()) {
            throw BusinessException.bad("INVALID_ARGUMENT", "存在未知的角色ID");
          }
          Specification<RoleEntity> allowed =
              assignableFilter(ownPermissionIds(actor), authority.level(actor), "")
                  .and((role, query, builder) -> role.get("id").in(wanted));
          if (!wanted.isEmpty() && roles.count(allowed) != wanted.size()) {
            throw BusinessException.scopeDenied();
          }
          for (RoleEntity existing : List.copyOf(target.getRoles())) {
            if (!wanted.contains(existing.getId())) target.removeRole(existing);
          }
          for (RoleEntity role : selected) target.assignRole(role);
          users.saveAndFlush(target);
          return withRoles(target);
        });
  }

  /** 停用立即生效，保留部门及角色关系；本票不提供人员恢复。 */
  @Transactional
  public UserWithRolesResult disable(String header, Long id) {
    return write(
        header,
        actor -> {
          UserEntity target = target(actor, id);
          authority.requireManage(actor, target);
          target.setEnabled(false);
          users.saveAndFlush(target);
          return withRoles(target);
        });
  }

  /** 参数解析前只做最新身份、具体API、目标范围及适用的人员保护，写事务仍锁后重验。 */
  public void checkTarget(String header, Long id, boolean protectSelf) {
    CurrentIdentity actor = reauthenticate(header);
    if (id == null) return;
    UserEntity target = target(actor, id);
    if (protectSelf) authority.requireManage(actor, target);
  }

  /** 原部门与目标部门都必须在本部门及下级范围，失败保留原归属与授权。 */
  @Transactional
  public UserWithRolesResult move(String header, Long id, TransferUserCommand command) {
    return write(
        header,
        actor -> {
          UserEntity target = target(actor, id);
          authority.requireManage(actor, target);
          Long departmentId = command.getDepartmentId();
          requireInScope(actor, departmentId);
          DepartmentEntity department =
              departments.findById(departmentId).orElseThrow(BusinessException::scopeDenied);
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
      throw new BusinessException(409, "DATA_CONFLICT", "人员保存冲突，请刷新后重试");
    } catch (DataAccessException exception) {
      throw new BusinessException(500, "INTERNAL_ERROR", "人员保存失败，请重试");
    }
  }

  /** 拿锁后按Spring服务端方法与模板重新鉴权，不能沿用加锁前的授权结论。 */
  private CurrentIdentity reauthenticate(String header) {
    CurrentIdentity actor = auth.require(header);
    RequestContext.Operation operation = requestContext.operation();
    if (operation == null) throw BusinessException.unauthorized();
    String template = operation.template();
    if (!permissions.allows(actor.id(), operation.method(), template)) {
      throw BusinessException.forbidden();
    }
    return actor;
  }

  /** 范围外对象不暴露是否存在；已获共同根范围者请求未知目标返回404。 */
  private void requireInScope(CurrentIdentity actor, Long departmentId) {
    if (scope.allowedIds(actor).contains(departmentId)) return;
    if (scope.isGlobal(actor) && !departments.existsById(departmentId)) {
      throw new BusinessException(404, "RESOURCE_NOT_FOUND", "部门不存在");
    }
    throw BusinessException.scopeDenied();
  }

  /** 人员查询先定位目标部门，未知人员对非共同根范围统一范围拒绝。 */
  private UserEntity target(CurrentIdentity actor, Long id) {
    UserEntity user =
        users
            .findById(id)
            .orElseThrow(
                () -> {
                  if (scope.isGlobal(actor)) {
                    return new BusinessException(404, "RESOURCE_NOT_FOUND", "人员不存在");
                  }
                  return BusinessException.scopeDenied();
                });
    requireInScope(actor, user.getDepartment().getId());
    return user;
  }

  /** 权限身份而不是API路径才是角色授权子集；同API可被不同权限复用。 */
  private Set<Long> ownPermissionIds(CurrentIdentity actor) {
    return permissions.allPermissions(actor.id()).stream()
        .map(PermissionEntity::getId)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 数据库排除任何超出本人有效权限的角色，外系统权限不因过滤而变成空集。 */
  private Specification<RoleEntity> assignableFilter(Set<Long> ownIds, int level, String search) {
    String pattern =
        "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    return (root, query, builder) -> {
      Subquery<Long> forbidden = query.subquery(Long.class);
      Root<RoleEntity> candidate = forbidden.correlate(root);
      Join<RoleEntity, PermissionEntity> permission = candidate.join("permissions");
      Predicate outside =
          ownIds.isEmpty() ? builder.conjunction() : builder.not(permission.get("id").in(ownIds));
      forbidden
          .select(permission.get("id"))
          .where(
              builder.or(
                  builder.notEqual(permission.get("system").get("code"), RbacCatalog.SYSTEM_CODE),
                  builder.and(
                      builder.isTrue(permission.get("enabled")),
                      builder.isTrue(permission.get("system").get("enabled")),
                      outside)));
      return builder.and(
          builder.isTrue(root.get("enabled")),
          level == 5
              ? builder.conjunction()
              : builder.and(
                  builder.isNotNull(root.get("managementLevel")),
                  builder.le(root.get("managementLevel"), level)),
          builder.not(builder.exists(forbidden)),
          builder.or(
              builder.like(builder.lower(root.get("name")), pattern, '\\'),
              builder.like(builder.lower(root.get("code")), pattern, '\\')));
    };
  }

  /** 角色元数据按ID升序输出，状态显式转换不依赖primitive映射。 */
  private RoleResult roleResult(RoleEntity role) {
    RoleResult result = CloneFactory.copy(new RoleResult(), role);
    result.setEnabled(role.isEnabled());
    return result;
  }

  /** 当前角色包含停用项及不可再分配项，供前端清理而非静默保留。 */
  private UserRolesResult rolesView(UserEntity user) {
    List<RoleResult> assigned =
        user.getRoles().stream()
            .sorted(Comparator.comparing(RoleEntity::getId))
            .map(this::roleResult)
            .toList();
    return new UserRolesResult(withRoles(user), assigned);
  }

  /** 同名字段由课程CloneFactory映射，关系和状态在事务内显式补充。 */
  private UserWithRolesResult withRoles(UserEntity user) {
    UserWithRolesResult result = CloneFactory.copy(new UserWithRolesResult(), user);
    result.setEnabled(user.isEnabled());
    result.setDepartmentId(user.getDepartment().getId());
    result.setRoleIds(
        user.getRoles().stream().map(RoleEntity::getId).sorted().collect(Collectors.toList()));
    return result;
  }
}
