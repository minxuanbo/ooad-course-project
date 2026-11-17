package com.municipal.rbaccore.service.ddd.application.user;

import com.municipal.rbaccore.service.ddd.application.support.*;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.AuthorizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.model.policy.RoleAssignmentPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.*;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.user.*;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 编排人员角色调整、账号停用和部门调动，授权规则保持在领域。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddUserManagementService implements UserManagementUseCase {
  private final AuthenticationUseCase authentication;
  private final UserRepository users;
  private final RoleRepository roles;
  private final OrganizationRepository organizations;
  private final AuthorizationRepository authorization;
  private final DddScope scope;
  private final DddRequestAccess requests;
  private final DddWrite writes;

  /** 返回全部已保存角色，包含停用与不可再授项目。 */
  @Override
  public UserRolesResult roles(String header, Long id) {
    CurrentIdentity actor = authentication.require(header);
    User user = target(actor, scope.facts(), id);
    return new UserRolesResult(DddResults.updated(user), DddResults.assigned(user));
  }

  /** 校验目标后将明确的授权子集条件传给分页仓储。 */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public AssignableRolePageResult assignableRoles(
      String header, Long id, AssignableRoleQuery query) {
    CurrentIdentity actor = authentication.require(header);
    OrganizationFacts organization = scope.facts();
    AuthorizationFacts operator = operator(actor.id());
    requireEditable(operator.user(), organization, target(actor, organization, id));
    DomainPage<Role> page =
        authorization.candidates(
            new AuthorizationFacts.AssignableRoleCriteria(
                operator.user().level(),
                customPermitted(operator.user(), organization),
                subset(operator),
                query.getQ(),
                query.getPage(),
                query.getSize()));
    return new AssignableRolePageResult(
        page.items().stream().map(DddResults::role).toList(),
        page.total(),
        page.page(),
        page.size());
  }

  /** 在写锁后对目标与选中角色逐个重新执行完整领域规则。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public UserWithRolesResult replaceRoles(String header, Long id, ReplaceUserRolesCommand command) {
    return writes.scoped(
        header,
        actor -> {
          OrganizationFacts organization = scope.facts();
          User target = target(actor, organization, id);
          AuthorizationFacts operator = operator(actor.id());
          requireEditable(operator.user(), organization, target);
          Set<Long> ids = Set.copyOf(command.getIds());
          Set<Role> selected = roles.select(new Role.Selection(ids));
          if (selected.size() != ids.size())
            throw BusinessException.bad("INVALID_ARGUMENT", "存在未知的角色ID");
          Set<Long> allowed = subset(operator);
          boolean custom = customPermitted(operator.user(), organization);
          if (!selected.stream()
              .allMatch(
                  role ->
                      RoleAssignmentPolicy.canAssign(
                          role, operator.user().level(), custom, allowed))) {
            throw BusinessException.scopeDenied();
          }
          return DddResults.updated(users.save(target.replaceRoles(selected)));
        });
  }

  /** 停用只改变账号状态，保留现有角色和部门。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public UserWithRolesResult disable(String header, Long id) {
    return writes.scoped(
        header,
        actor -> {
          OrganizationFacts organization = scope.facts();
          User target = target(actor, organization, id);
          requireEditable(person(actor.id()), organization, target);
          return DddResults.updated(users.save(target.disable()));
        });
  }

  /** 调动的原部门和目标部门必须都在当前子树内。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public UserWithRolesResult move(String header, Long id, TransferUserCommand command) {
    return writes.scoped(
        header,
        actor -> {
          OrganizationFacts organization = scope.facts();
          User target = target(actor, organization, id);
          requireEditable(person(actor.id()), organization, target);
          scope.filter(organization, actor.departmentId(), command.getDepartmentId());
          Department department =
              organizations
                  .find(new Department.Key(command.getDepartmentId()))
                  .filter(node -> !node.deleted())
                  .orElseThrow(BusinessException::scopeDenied);
          return DddResults.updated(users.save(target.transfer(department)));
        });
  }

  /** MVC解析请求体前检查实际权限与可见目标，写入仍须锁后再次检查。 */
  @Override
  public void checkTarget(String header, Long id, boolean protectSelf) {
    CurrentIdentity actor = requests.require(header);
    if (id == null) return;
    OrganizationFacts organization = scope.facts();
    User target = target(actor, organization, id);
    if (protectSelf) requireEditable(person(actor.id()), organization, target);
  }

  /** 越界主体不区分目标不存在与不在范围。 */
  private User target(CurrentIdentity actor, OrganizationFacts organization, Long id) {
    User user =
        users
            .find(new User.Key(id))
            .orElseThrow(
                () ->
                    OrganizationPolicy.global(organization, actor.departmentId())
                        ? new BusinessException(404, "RESOURCE_NOT_FOUND", "人员不存在")
                        : BusinessException.scopeDenied());
    scope.filter(organization, actor.departmentId(), user.department().id());
    return user;
  }

  /** 获取当前操作人的完整直接授权事实。 */
  private AuthorizationFacts operator(Long id) {
    return authorization.facts(new User.Key(id)).orElseThrow(BusinessException::unauthorized);
  }

  /** 基础人员保护无需加载完整权限图。 */
  private User person(Long id) {
    return users.find(new User.Key(id)).orElseThrow(BusinessException::unauthorized);
  }

  /** 判断当前目标是否低于操作者且满足特殊保护。 */
  private void requireEditable(User actor, OrganizationFacts organization, User target) {
    if (!RoleAssignmentPolicy.canManage(
        actor, OrganizationPolicy.global(organization, actor.department().id()), target)) {
      throw BusinessException.scopeDenied();
    }
  }

  /** 自定义角色的授予者还必须直属可信共同根。 */
  private boolean customPermitted(User actor, OrganizationFacts organization) {
    return actor.level() == Role.ADMIN_LEVEL
        && OrganizationPolicy.global(organization, actor.department().id());
  }

  /** 授予子集按完整权限编号比较，而非可执行API。 */
  private Set<Long> subset(AuthorizationFacts operator) {
    return AuthorizationPolicy.directPermissions(operator).stream()
        .map(Permission::id)
        .collect(Collectors.toUnmodifiableSet());
  }
}
