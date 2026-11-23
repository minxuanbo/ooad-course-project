package com.municipal.rbaccore.service.ddd.application.role;

import com.municipal.rbaccore.service.ddd.application.support.DddRequestAccess;
import com.municipal.rbaccore.service.ddd.application.support.DddResults;
import com.municipal.rbaccore.service.ddd.application.support.DddScope;
import com.municipal.rbaccore.service.ddd.application.support.DddWrite;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.model.policy.PermissionSelectionPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.OrganizationRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.PermissionRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.RoleRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.role.*;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.ManagedApplications;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 全局角色用例负责门禁、事务、领域行为及公开结果。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddRoleService implements RoleUseCase {
  private final ManagedApplications applications;
  private final RoleRepository roles;
  private final PermissionRepository permissions;
  private final ManagementAccessUseCase access;
  private final DddWrite writes;
  private final DddRequestAccess requests;
  private final DddScope scope;
  private final UserRepository users;
  private final OrganizationRepository organizations;

  /** 写定义保持共同根门禁；局部只读限定到本人子树部门角色。 */
  public void checkTarget(String header, Long id, boolean write) {
    if (write) {
      access.require(header);
      return;
    }
    CurrentIdentity actor = requests.require(header);
    OrganizationFacts facts = scope.facts();
    boolean global = OrganizationPolicy.global(facts, actor.departmentId());
    if (!global) {
      if (users.find(new User.Key(actor.id())).orElseThrow(BusinessException::unauthorized).level()
          < Role.FIRST_MANAGER_LEVEL) throw BusinessException.scopeDenied();
      if (id != null) {
        Role row = roles.find(new Role.Key(id)).orElseThrow(BusinessException::scopeDenied);
        if (row.type() != Role.Type.DEPARTMENT_CUSTOM
            || row.ownerDepartment() == null
            || !scope
                .filter(facts, actor.departmentId(), null)
                .departmentIds()
                .contains(row.ownerDepartment().id())) throw BusinessException.scopeDenied();
      }
    }
  }

  /** 部门角色定义只能由可信根有效五级管理员维护。 */
  private void requireDefinition(String header, Role.Type type) {
    if (type != Role.Type.DEPARTMENT_CUSTOM) return;
    CurrentIdentity actor = requests.require(header);
    if (users.find(new User.Key(actor.id())).orElseThrow(BusinessException::unauthorized).level()
        != Role.ADMIN_LEVEL) throw BusinessException.scopeDenied();
  }

  /** 在同一数据库快照查询角色条目及总数。 */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RolePageResult list(String header, RoleQuery query) {
    checkTarget(header, null, false);
    CurrentIdentity actor = requests.require(header);
    OrganizationFacts facts = scope.facts();
    boolean global = OrganizationPolicy.global(facts, actor.departmentId());
    DomainPage<Role> page =
        roles.search(
            new SearchCriteria.Roles(
                query.getQ(),
                query.getEnabled(),
                query.getPage(),
                query.getSize(),
                query.getType() == null ? null : Role.Type.valueOf(query.getType().name()),
                query.getOwnerDepartmentId(),
                global ? null : scope.filter(facts, actor.departmentId(), null).departmentIds()));
    return new RolePageResult(
        page.items().stream().map(DddResults::role).toList(),
        page.total(),
        page.page(),
        page.size());
  }

  /** 读回已选的显式权限，停用项目也可用于识别与撤销。 */
  @Override
  public RolePermissionsResult selected(String header, Long id) {
    checkTarget(header, id, false);
    return selection(role(id));
  }

  /** 自定义角色从空权限开始，编码唯一由数据库兜底。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public RoleResult create(String header, CreateRoleCommand command) {
    return writes.global(
        header,
        () -> {
          Role created;
          if (command.getType() == RoleType.DEPARTMENT_CUSTOM) {
            requireDefinition(header, Role.Type.DEPARTMENT_CUSTOM);
            Department owner =
                organizations
                    .find(new Department.Key(command.getOwnerDepartmentId()))
                    .filter(node -> !node.deleted())
                    .orElseThrow(() -> new BusinessException(404, "RESOURCE_NOT_FOUND", "所属部门不存在"));
            created = Role.departmental(command.getCode(), command.getName(), owner);
          } else created = Role.custom(command.getCode(), command.getName(), null);
          return DddResults.role(roles.save(created));
        });
  }

  /** 更名及启停不修改固定级别或现有权限集合。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public RoleResult patch(String header, Long id, UpdateRoleCommand command) {
    return writes.global(
        header,
        () -> {
          Role current = role(id);
          requireDefinition(header, current.type());
          return DddResults.role(
              roles.save(current.revise(command.getName(), command.getEnabled())));
        });
  }

  /** 应用明确解析所选权限，领域整体校验、替换直接集合。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public RolePermissionsResult replacePermissions(
      String header, Long id, ReplaceRolePermissionsCommand command) {
    return writes.global(
        header,
        () -> {
          Role role = role(id);
          requireDefinition(header, role.type());
          Set<Permission> selected =
              permissions.select(new Permission.Selection(Set.copyOf(command.getIds())));
          return selection(
              roles.save(
                  PermissionSelectionPolicy.replace(
                      role, selected, command.getIds().size(), applications.codes())));
        });
  }

  /** 门禁通过后，未知角色按资源不存在返回。 */
  private Role role(Long id) {
    return roles
        .find(new Role.Key(id))
        .orElseThrow(() -> new BusinessException(404, "RESOURCE_NOT_FOUND", "角色不存在"));
  }

  /** 按稳定顺序返回持久化的完整编号。 */
  private RolePermissionsResult selection(Role role) {
    return new RolePermissionsResult(
        role.id(), role.permissions().stream().map(Permission::id).sorted().toList());
  }
}
