package com.municipal.rbaccore.r23;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.port.department.*;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.importing.ImportPlan;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.role.ReplaceRolePermissionsCommand;
import com.municipal.rbaccore.service.port.security.*;
import com.municipal.rbaccore.service.port.user.*;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.department.StructuredDepartmentService;
import com.municipal.rbaccore.service.structured.role.StructuredRoleService;
import com.municipal.rbaccore.service.structured.security.*;
import com.municipal.rbaccore.service.structured.user.StructuredUserManagementService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** R24结构化用例读取边界。替身控制不一致快照和能力矩阵，真实事务另由MySQL验证。 */
class R24StructuredReadBoundaryTest {
  private static final String HEADER = "Bearer unit-boundary";
  private static final RbacCatalog.Route CREATE = new RbacCatalog.Route("POST", "/api/departments");
  private static final RbacCatalog.Route EDIT =
      new RbacCatalog.Route("PATCH", "/api/departments/{id}");
  private static final RbacCatalog.Route DELETE =
      new RbacCatalog.Route("DELETE", "/api/departments/{id}");

  /** 按钮取决于真实路由和人员级别，不能由管理员名称或树根位置推断。 */
  @Test
  @DisplayName("R24 结构化部门按钮逐项受路由、级别和节点类型控制")
  void R24_projectsIndependentDepartmentCapabilities() {
    for (Set<RbacCatalog.Route> routes :
        List.of(Set.<RbacCatalog.Route>of(), Set.of(CREATE), Set.of(EDIT), Set.of(DELETE))) {
      Fixture f = new Fixture();
      when(f.permissions.routes(1L)).thenReturn(routes);
      List<DepartmentResult> rows = f.departmentService.list(HEADER);
      assertEquals(routes.contains(CREATE), rows.getFirst().getCanCreate());
      assertEquals(routes.contains(EDIT), rows.getFirst().getCanEdit());
      assertFalse(rows.getFirst().getCanDelete());
      assertFalse(rows.get(1).getCanDelete(), "存在下级仍不得删除");
      assertEquals(routes.contains(DELETE), rows.get(2).getCanDelete());
      assertFalse(rows.get(2).getCanCreate());
    }
    Fixture employee = new Fixture();
    when(employee.authority.level(employee.actor)).thenReturn(1);
    assertTrue(
        employee.departmentService.list(HEADER).stream()
            .noneMatch(row -> row.getCanEdit() || row.getCanCreate()));
    Fixture local = new Fixture();
    when(local.scope.isGlobal(local.actor)).thenReturn(false);
    assertFalse(local.departmentService.list(HEADER).getFirst().getCanEdit());
    Fixture detached = new Fixture();
    detached.child.moveTo(null);
    assertNull(detached.departmentService.list(HEADER).get(1).getParentId());
  }

  /** 查询预检可以只读；权限已撤销、墓碑或非可信总公司类型一律拒绝。 */
  @Test
  @DisplayName("R24 结构化部门预检区分只读写入，拒绝撤权、墓碑和非可信根类型")
  void R24_checksReadWriteAndInconsistentDepartmentSnapshots() {
    Fixture f = new Fixture();
    assertDoesNotThrow(() -> f.departmentService.checkTarget(HEADER, 2L, false));
    when(f.permissions.allows(any(), any(), any())).thenReturn(false);
    assertCode("PERMISSION_DENIED", () -> f.departmentService.checkTarget(HEADER, 2L, true));
    verify(f.departments, never()).saveAndFlush(any());
    Fixture deleted = new Fixture();
    deleted.child.setDeleted(true);
    assertCode(
        "MANAGEMENT_SCOPE_DENIED", () -> deleted.departmentService.checkTarget(HEADER, 2L, false));
    Fixture untrusted = new Fixture();
    untrusted.child.setType(DepartmentType.HEADQUARTERS);
    CreateDepartmentCommand command = new CreateDepartmentCommand();
    command.setParentId(2L);
    command.setName("新部门");
    command.setType(DepartmentType.DEPARTMENT);
    assertCode(
        "DEPARTMENT_TYPE_INVALID", () -> untrusted.departmentService.create(HEADER, command));
    verify(untrusted.departments, never()).saveAndFlush(any());
  }

  /** 根来源不合法或无法覆盖全树时不能产生全局权限。 */
  @Test
  @DisplayName("R24 结构化全局范围拒绝非根来源和不完整子树")
  void R24_rootSourceMustBeRootAndComplete() {
    DepartmentJpaRepository departments = mock(DepartmentJpaRepository.class);
    ImportSourceJpaRepository sources = mock(ImportSourceJpaRepository.class);
    DepartmentEntity root = department(1L, null, DepartmentType.HEADQUARTERS);
    ImportPlan.Organization row = new ImportPlan.Organization(2, "总公司", "负责人", "", "总公司", "root");
    when(sources.findByKindAndSourceKey(ImportSourceEntity.Kind.ORGANIZATION, "总公司"))
        .thenReturn(Optional.of(ImportSourceEntity.organization(row, "hash", "[]", root)));
    when(departments.subtreeIds(1L)).thenReturn(List.of(1L));
    when(departments.countByDeletedFalse()).thenReturn(2L);
    StructuredDepartmentScopeService scope =
        new StructuredDepartmentScopeService(departments, sources);
    CurrentIdentity actor = new CurrentIdentity(1L, "admin", 1L);
    assertFalse(scope.isGlobal(actor));
    root.moveTo(department(2L, null, DepartmentType.COMPANY));
    assertFalse(scope.isGlobal(actor));
  }

  /** 同级和本人保护应在候选读取及写入锁后继续生效。 */
  @Test
  @DisplayName("R24 人员候选和角色替换执行同级保护，后台调用缺操作元数据拒绝")
  void R24_personnelGuardsAndMissingOperationFailClosed() {
    Fixture f = new Fixture();
    assertCode(
        "MANAGEMENT_SCOPE_DENIED",
        () -> f.userService.assignableRoles(HEADER, 2L, new AssignableRoleQuery()));
    ReplaceUserRolesCommand command = new ReplaceUserRolesCommand();
    command.setIds(List.of());
    assertCode("MANAGEMENT_SCOPE_DENIED", () -> f.userService.replaceRoles(HEADER, 2L, command));
    when(f.requests.operation()).thenReturn(null);
    assertCode("SESSION_INVALID", () -> f.userService.checkTarget(HEADER, null, false));
    StructuredGlobalManagementAccess access =
        new StructuredGlobalManagementAccess(f.auth, f.requests, f.permissions, f.scope, f.guard);
    assertCode("SESSION_INVALID", () -> access.require(HEADER));
    Fixture missingScope = new Fixture();
    when(missingScope.scope.allowedIds(missingScope.actor)).thenReturn(List.of(1L));
    when(missingScope.departments.existsById(2L)).thenReturn(true);
    assertCode("MANAGEMENT_SCOPE_DENIED", () -> missingScope.userService.roles(HEADER, 2L));
  }

  /** 保存真值不可静默清掉外目录授权，停用系统权限不可新授。 */
  @Test
  @DisplayName("R24 角色写入和锁定项保留未知应用及停用系统的边界")
  void R24_roleApplicationBoundariesAreExplicit() {
    Fixture f = new Fixture();
    StructuredGlobalManagementAccess access = mock(StructuredGlobalManagementAccess.class);
    doAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(1)).get())
        .when(access)
        .write(any(), any());
    PermissionJpaRepository permissions = mock(PermissionJpaRepository.class);
    StructuredRoleService service =
        new StructuredRoleService(
            () -> Set.of("rbac"),
            f.roles,
            permissions,
            access,
            f.auth,
            f.permissions,
            f.requests,
            f.scope,
            f.authority,
            f.departments);
    RoleEntity role = new RoleEntity("sample", "样例");
    ApplicationSystemEntity foreign = application(9L, "foreign", true);
    PermissionEntity outside = permission(9L, foreign);
    role.grantPermission(outside);
    when(f.roles.findById(1L)).thenReturn(Optional.of(role));
    ReplaceRolePermissionsCommand command = new ReplaceRolePermissionsCommand();
    command.setIds(List.of(9L));
    assertCode("INVALID_ARGUMENT", () -> service.replacePermissions(HEADER, 1L, command));
    role.revokePermission(outside);
    when(permissions.findAllById(command.getIds())).thenReturn(List.of(outside));
    assertCode("INVALID_ARGUMENT", () -> service.replacePermissions(HEADER, 1L, command));
    PermissionEntity inactiveSystem = permission(10L, application(10L, "rbac", false));
    when(permissions.findAllById(command.getIds())).thenReturn(List.of(inactiveSystem));
    assertCode("INVALID_ARGUMENT", () -> service.replacePermissions(HEADER, 1L, command));
    verify(f.roles, never()).saveAndFlush(any());

    when(f.authority.canManage(any(StructuredPersonnelAuthority.AuthoritySnapshot.class), any()))
        .thenReturn(true);
    when(f.authority.snapshot(f.actor))
        .thenReturn(new StructuredPersonnelAuthority.AuthoritySnapshot(1L, 5, true));
    ReflectionTestUtils.setField(role, "id", 1L);
    f.target.assignRole(role);
    role.grantPermission(outside);
    assertEquals(List.of(1L), f.userService.roles(HEADER, 2L).getLockedRoleIds());
    role.revokePermission(outside);
    role.grantPermission(inactiveSystem);
    assertTrue(f.userService.roles(HEADER, 2L).getLockedRoleIds().isEmpty());
  }

  /** 断言稳定业务码，避免任意异常也被计为安全拒绝。 */
  private void assertCode(String code, org.junit.jupiter.api.function.Executable operation) {
    assertEquals(code, assertThrows(BusinessException.class, operation).getCode());
  }

  /** 构造有明确身份的组织事实。 */
  private static DepartmentEntity department(
      Long id, DepartmentEntity parent, DepartmentType type) {
    DepartmentEntity result = new DepartmentEntity("d" + id, "部门" + id);
    ReflectionTestUtils.setField(result, "id", id);
    result.setType(type);
    result.moveTo(parent);
    return result;
  }

  /** 应用启停是持久化事实，不能以编码是否常见替代。 */
  private static ApplicationSystemEntity application(Long id, String code, boolean enabled) {
    ApplicationSystemEntity result = new ApplicationSystemEntity(code, code);
    ReflectionTestUtils.setField(result, "id", id);
    result.setEnabled(enabled);
    return result;
  }

  /** 权限保持真实可变实体结构以验证关系与锁定投影。 */
  private static PermissionEntity permission(Long id, ApplicationSystemEntity system) {
    PermissionEntity result = new PermissionEntity(system, "p" + id, "权限", PermissionType.BUTTON);
    ReflectionTestUtils.setField(result, "id", id);
    return result;
  }

  /** 同一请求的组织和能力输入，不用它证明数据库外键或回滚。 */
  private static final class Fixture {
    private final DepartmentEntity root = department(1L, null, DepartmentType.HEADQUARTERS);
    private final DepartmentEntity child = department(2L, root, DepartmentType.DEPARTMENT);
    private final DepartmentEntity project = department(3L, child, DepartmentType.PROJECT);
    private final CurrentIdentity actor = new CurrentIdentity(1L, "admin", 1L);
    private final UserEntity target = new UserEntity("target", "encoded", "目标人员", child, true);
    private final DepartmentJpaRepository departments = mock(DepartmentJpaRepository.class);
    private final UserJpaRepository users = mock(UserJpaRepository.class);
    private final RoleJpaRepository roles = mock(RoleJpaRepository.class);
    private final StructuredAuthenticationService auth =
        mock(StructuredAuthenticationService.class);
    private final StructuredDepartmentScopeService scope =
        mock(StructuredDepartmentScopeService.class);
    private final StructuredPersonnelAuthority authority = mock(StructuredPersonnelAuthority.class);
    private final StructuredEffectivePermissionService permissions =
        mock(StructuredEffectivePermissionService.class);
    private final StructuredMaintenanceGuard guard = mock(StructuredMaintenanceGuard.class);
    private final RequestContext requests = mock(RequestContext.class);
    private final StructuredDepartmentService departmentService;
    private final StructuredUserManagementService userService;

    /** 显式默认能力使每个变体只改变待核对的一个条件。 */
    private Fixture() {
      when(auth.require(HEADER)).thenReturn(actor);
      when(scope.allowedIds(actor)).thenReturn(List.of(1L, 2L, 3L));
      when(scope.isGlobal(actor)).thenReturn(true);
      when(scope.trustedRootId()).thenReturn(1L);
      when(authority.level(actor)).thenReturn(5);
      when(authority.snapshot(actor))
          .thenReturn(new StructuredPersonnelAuthority.AuthoritySnapshot(1L, 5, true));
      when(permissions.routes(1L)).thenReturn(Set.of(CREATE, EDIT, DELETE));
      when(permissions.allows(any(), any(), any())).thenReturn(true);
      when(requests.operation())
          .thenReturn(new RequestContext.Operation("PATCH", "/api/departments/{id}"));
      when(departments.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(root, child, project));
      when(departments.findById(2L)).thenReturn(Optional.of(child));
      ReflectionTestUtils.setField(target, "id", 2L);
      when(users.findById(2L)).thenReturn(Optional.of(target));
      departmentService =
          new StructuredDepartmentService(
              departments, users, roles, auth, scope, authority, permissions, guard, requests);
      userService =
          new StructuredUserManagementService(
              () -> Set.of("rbac"),
              auth,
              requests,
              permissions,
              scope,
              guard,
              users,
              authority,
              roles,
              departments);
    }
  }
}
