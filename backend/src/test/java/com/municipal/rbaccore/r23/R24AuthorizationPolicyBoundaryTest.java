package com.municipal.rbaccore.r23;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R09/R15/R24领域边界不依赖HTTP过滤，异常事实必须失败关闭并保留原聚合。 */
class R24AuthorizationPolicyBoundaryTest {
  private final ApplicationSystem rbac = new ApplicationSystem(1L, "rbac", "权限系统", true);
  private final ApplicationSystem other = new ApplicationSystem(2L, "other", "其他系统", true);
  private final Department root = new Department(1L, "root", "总公司", null, Department.Type.HEADQUARTERS, false);

  /** 任何一条映射失效均不得导出API短码，菜单无映射仍能表达资格。 */
  @Test
  @DisplayName("R09 跨系统和停用系统映射不能贡献路由或导出短码")
  void R09_rejectsForeignAndDisabledApplicationMappings() {
    ApiResource valid = new ApiResource(1L, rbac, new ApiResource.Route("GET", "/items"), true);
    ApiResource foreign = new ApiResource(2L, other, new ApiResource.Route("GET", "/foreign"), true);
    ApiResource stopped = new ApiResource(3L, new ApplicationSystem(3L, "stopped", "停用", false),
        new ApiResource.Route("GET", "/stopped"), true);
    Permission readable = permission(1L, rbac, Set.of(valid));
    Permission polluted = permission(2L, rbac, Set.of(foreign));
    Permission inactive = permission(3L, rbac, Set.of(stopped));
    AuthorizationFacts facts = facts(true, root, Set.of(readable, polluted, inactive));
    assertEquals(Set.of(valid.route()), AuthorizationPolicy.routes(facts));
    assertEquals(Set.of(readable), ApplicationAuthorizationPolicy.exported(facts, "rbac"));
    assertTrue(AuthorizationPolicy.directPermissions(new AuthorizationFacts(facts.user().disable())).isEmpty());
    assertTrue(AuthorizationPolicy.activeRbac(readable));
    assertFalse(AuthorizationPolicy.activeRbac(permission(4L, other, Set.of())));
    assertTrue(rbac.isRbac());
    assertFalse(other.isRbac());
  }

  /** 共享角色不能静默丢弃未知应用项，兼容入口也须执行同一子集检查。 */
  @Test
  @DisplayName("R09/R27 默认RBAC选权拒绝未知系统的既有权限与待分配角色")
  void R27_roleSelectionRejectsUnknownApplications() {
    Permission local = permission(1L, rbac, Set.of());
    Permission foreign = permission(2L, other, Set.of());
    Role role = new Role(1L, "custom", "自定义", null, true, Set.of(foreign));
    assertThrows(DomainRuleViolation.class, () -> PermissionSelectionPolicy.replace(role, Set.of(local), 1));
    assertEquals(Set.of(foreign), role.permissions());
    assertFalse(RoleAssignmentPolicy.canAssign(role, 5, true, Set.of(1L, 2L)));
    Role empty = Role.custom(" custom ", " 角色 ", false);
    assertFalse(empty.enabled());
    assertEquals("custom", empty.code());
    assertEquals(Set.of(local), PermissionSelectionPolicy.replace(empty, Set.of(local), 1).permissions());
    AuthorizationFacts.AssignableRoleCriteria criteria = new AuthorizationFacts.AssignableRoleCriteria(
        5, true, Set.of(1L), null, 1, 20);
    assertEquals(Set.of("rbac"), criteria.systemCodes());
    assertThrows(UnsupportedOperationException.class, () -> criteria.allowedPermissionIds().clear());
  }

  /** 父级所属系统和异常父链均在产生新值之前拒绝。 */
  @Test
  @DisplayName("R14 权限树拒绝跨系统父级及不经过当前节点的既有循环")
  void R14_rejectsForeignParentsAndExistingCycle() {
    Permission current = permission(9L, rbac, Set.of());
    Permission foreign = menu(2L, other, null);
    assertThrows(DomainRuleViolation.class, () -> PermissionTreePolicy.revise(
        current, Map.of(2L, foreign), 2L, null, null));
    Permission first = menu(2L, rbac, 3L);
    Permission second = menu(3L, rbac, 2L);
    assertThrows(DomainRuleViolation.class, () -> PermissionTreePolicy.revise(
        current, Map.of(2L, first, 3L, second), 2L, null, null));
    assertNull(current.parentId());
    assertEquals("新名称", current.revise(null, "新名称", null, null).name());
    ApiResource api = new ApiResource(1L, rbac, new ApiResource.Route("GET", "/items"), true);
    assertSame(api, api.changeAvailability(null));
  }

  /** 维护者必须启用且直属可信根，级别足够不能弥补错误归属。 */
  @Test
  @DisplayName("R15 停用管理员和非可信根管理员不能作为最后维护者")
  void R15_requiresEnabledRootAdministrator() {
    assertFalse(MaintenancePolicy.qualifies(facts(false, root, Set.of()), 1L, Set.of()));
    Department child = new Department(2L, "child", "部门", 1L, Department.Type.DEPARTMENT, false);
    assertFalse(MaintenancePolicy.qualifies(facts(true, child, Set.of()), 1L, Set.of()));
    assertTrue(MaintenancePolicy.qualifies(facts(true, root, Set.of()), 1L, Set.of()));
  }

  /** 组织类型允许矩阵逐项检查，不依靠Service绕过纯领域规则。 */
  @Test
  @DisplayName("R24 组织层级允许矩阵及未知根、损坏循环全部有确定结果")
  void R24_checksHierarchyMatrixAndDamagedScopes() {
    Map<Department.Type, Set<Department.Type>> allowed = Map.of(
        Department.Type.HEADQUARTERS, Set.of(Department.Type.COMPANY, Department.Type.DEPARTMENT, Department.Type.PROJECT),
        Department.Type.COMPANY, Set.of(Department.Type.DEPARTMENT, Department.Type.PROJECT),
        Department.Type.DEPARTMENT, Set.of(Department.Type.PROJECT),
        Department.Type.PROJECT, Set.of());
    for (Department.Type parentType : Department.Type.values()) {
      Department parent = new Department(1L, "node", "组织", null, parentType, false);
      for (Department.Type childType : Department.Type.values()) {
        assertEquals(allowed.get(parentType).contains(childType),
            OrganizationPolicy.allowsChild(parent, childType, 1L), parentType + "/" + childType);
      }
    }
    assertFalse(OrganizationPolicy.allowsChild(root, Department.Type.COMPANY, 9L));
    OrganizationFacts empty = new OrganizationFacts(1L, List.of());
    assertTrue(OrganizationPolicy.scope(empty, 1L).departmentIds().isEmpty());
    assertFalse(OrganizationPolicy.global(empty, 1L));
    Department a = new Department(1L, "a", "甲", 2L, Department.Type.DEPARTMENT, false);
    Department b = new Department(2L, "b", "乙", 1L, Department.Type.DEPARTMENT, false);
    OrganizationFacts cycle = new OrganizationFacts(1L, List.of(a, b));
    assertEquals(Set.of(1L, 2L), OrganizationPolicy.scope(cycle, 1L).departmentIds());
    assertFalse(OrganizationPolicy.global(cycle, 1L));
    Department disconnected = new Department(3L, "orphan", "其他根", null, Department.Type.COMPANY, false);
    assertFalse(OrganizationPolicy.global(new OrganizationFacts(1L, List.of(root, disconnected)), 1L));
  }

  /** 只配置当前用例关心的权限集合，其他身份字段不参与断言。 */
  private AuthorizationFacts facts(boolean enabled, Department department, Set<Permission> permissions) {
    Role administrator = new Role(1L, "administrator", "管理员", 5, true, permissions);
    return new AuthorizationFacts(new User(1L, "admin", "unused-hash", "示例", null, null,
        department, enabled, Set.of(administrator)));
  }

  /** API类型需要明确映射，单位策略测试可直接提供边界事实。 */
  private Permission permission(Long id, ApplicationSystem system, Set<ApiResource> apis) {
    return new Permission(id, system, null, "p" + id, "权限", Permission.Type.API, true, apis);
  }

  /** 构造树位置的最小菜单节点，避免把API映射混入父链测试。 */
  private Permission menu(Long id, ApplicationSystem system, Long parent) {
    return new Permission(id, system, parent, "menu" + id, "菜单", Permission.Type.MENU, true, Set.of());
  }
}
