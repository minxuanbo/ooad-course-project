package com.municipal.rbaccore.r23;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.ddd.application.role.DddRoleService;
import com.municipal.rbaccore.service.ddd.application.support.*;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.*;
import com.municipal.rbaccore.service.ddd.domain.repository.*;
import com.municipal.rbaccore.service.port.department.DepartmentType;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.role.*;
import com.municipal.rbaccore.service.port.security.*;
import com.municipal.rbaccore.service.port.user.*;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.role.StructuredRoleService;
import com.municipal.rbaccore.service.structured.security.*;
import com.municipal.rbaccore.service.structured.user.StructuredUserManagementService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** R23/R28部门角色的故障事实与授权组合；替身不替代真实MySQL事务验证。 */
class R28RoleBoundaryTest {
  private static final String HEADER = "Bearer department-boundary";
  private final Department root = department(1L, null, false);
  private final Department child = department(2L, 1L, false);

  /** 缺失、删除、越界和目标不适用分别拒绝，来源分类不隐式提升级别。 */
  @Test
  @DisplayName("R23/R28 部门角色归属和目标祖先必须同时成立")
  void R28_domainAssignmentRequiresEveryScopeFact() {
    Role valid = role(child);
    assertTrue(RoleAssignmentPolicy.departmental(valid, 3, Set.of(2L), Set.of(1L, 2L)));
    assertFalse(RoleAssignmentPolicy.departmental(valid, 1, Set.of(2L), Set.of(2L)));
    assertFalse(
        RoleAssignmentPolicy.departmental(
            Role.custom("global", "全局", true), 3, Set.of(2L), Set.of(2L)));
    assertFalse(RoleAssignmentPolicy.departmental(role(null), 3, Set.of(2L), Set.of(2L)));
    assertFalse(
        RoleAssignmentPolicy.departmental(
            role(department(2L, 1L, true)), 3, Set.of(2L), Set.of(2L)));
    assertFalse(RoleAssignmentPolicy.departmental(valid, 3, Set.of(1L), Set.of(2L)));
    assertFalse(RoleAssignmentPolicy.departmental(valid, 3, Set.of(2L), Set.of(1L)));
    assertFalse(role(null).applicable(Set.of(2L)));
    Role empty = Role.custom("empty", "空角色", true);
    assertTrue(RoleAssignmentPolicy.canAssign(empty, 5, true, Set.of(), Set.of("rbac")));
    assertFalse(
        RoleAssignmentPolicy.canAssign(
            empty.revise(null, false), 5, true, Set.of(), Set.of("rbac")));
    assertFalse(RoleAssignmentPolicy.canAssign(empty, 3, false, Set.of(), Set.of("rbac")));
    assertNull(new SearchCriteria.Roles(null, null, 0, 20).visibleOwners());
  }

  /** 损坏组织环在祖先读取时终止，待接入两端都不能贡献实际路由。 */
  @Test
  @DisplayName("R28 损坏祖先链和PENDING应用均失败关闭")
  void R28_cyclesAndPendingApisCannotCreateAuthority() {
    OrganizationFacts cycle = new OrganizationFacts(1L, List.of(department(1L, 2L, false), child));
    assertEquals(Set.of(1L, 2L), OrganizationPolicy.ancestors(cycle, 2L));
    ApplicationSystem connected = new ApplicationSystem(1L, "rbac", "权限", true);
    ApplicationSystem pending =
        new ApplicationSystem(1L, "rbac", "权限", true, ApplicationSystem.Connection.PENDING);
    for (boolean pendingPermission : List.of(true, false)) {
      ApiResource api =
          new ApiResource(
              1L,
              pendingPermission ? connected : pending,
              new ApiResource.Route("GET", "/items"),
              true);
      Permission permission =
          new Permission(
              1L,
              pendingPermission ? pending : connected,
              null,
              "read",
              "读取",
              Permission.Type.API,
              true,
              Set.of(api));
      User user = user(root, Set.of(new Role(1L, "base", "基础", 5, true, Set.of(permission))));
      assertTrue(AuthorizationPolicy.routes(new AuthorizationFacts(user)).isEmpty());
    }
  }

  /** 有实际读取API仍不能越过局部领导及所属范围，缺元数据不能降级为全局读取。 */
  @Test
  @DisplayName("R28 structured局部角色读取拒绝低级别、缺失所属及范围外角色")
  void R28_structuredReadsFailClosed() {
    StructuredFixture f = new StructuredFixture();
    when(f.requests.operation()).thenReturn(null);
    assertCode("SESSION_INVALID", () -> f.service.checkTarget(HEADER, null, false));
    when(f.requests.operation()).thenReturn(new RequestContext.Operation("GET", "/api/roles"));
    when(f.scope.isGlobal(f.actor)).thenReturn(false);
    when(f.authority.level(f.actor)).thenReturn(1);
    assertCode("MANAGEMENT_SCOPE_DENIED", () -> f.service.checkTarget(HEADER, null, false));
    when(f.authority.level(f.actor)).thenReturn(3);
    for (DepartmentEntity owner : new DepartmentEntity[] {null, entityDepartment(9L, false)}) {
      RoleEntity damaged = entityRole(owner);
      when(f.roles.findById(9L)).thenReturn(Optional.of(damaged));
      assertCode("MANAGEMENT_SCOPE_DENIED", () -> f.service.checkTarget(HEADER, 9L, false));
    }
    when(f.roles.findById(9L)).thenReturn(Optional.of(entityRole(f.owner)));
    assertDoesNotThrow(() -> f.service.checkTarget(HEADER, 9L, false));
  }

  /** 根范围读取资格不等于部门定义资格，删除墓碑也不能成为新所属。 */
  @Test
  @DisplayName("R28 structured部门角色定义必须是有效管理员且所属未删除")
  void R28_structuredDefinitionIsAdministratorOnly() {
    StructuredFixture f = new StructuredFixture();
    when(f.authority.snapshot(f.actor))
        .thenReturn(new StructuredPersonnelAuthority.AuthoritySnapshot(1L, 4, false));
    CreateRoleCommand command = create();
    assertCode("MANAGEMENT_SCOPE_DENIED", () -> f.service.create(HEADER, command));
    when(f.roles.findById(9L)).thenReturn(Optional.of(entityRole(f.owner)));
    assertCode(
        "MANAGEMENT_SCOPE_DENIED", () -> f.service.patch(HEADER, 9L, new UpdateRoleCommand()));
    ReplaceRolePermissionsCommand selection = new ReplaceRolePermissionsCommand();
    selection.setIds(List.of());
    assertCode(
        "MANAGEMENT_SCOPE_DENIED", () -> f.service.replacePermissions(HEADER, 9L, selection));
    when(f.authority.snapshot(f.actor))
        .thenReturn(new StructuredPersonnelAuthority.AuthoritySnapshot(1L, 5, true));
    f.owner.setDeleted(true);
    assertCode("RESOURCE_NOT_FOUND", () -> f.service.create(HEADER, command));
    verify(f.roles, never()).saveAndFlush(any());
  }

  /** 锁定项允许原样保留，不能由错误归属或普通员工资格撤回。 */
  @Test
  @DisplayName("R23/R28 structured陈旧部门角色锁定且不再贡献有效授权")
  void R28_structuredLockedAssociationsAndMalformedOwnership() {
    for (int variant = 0; variant < 6; variant++) {
      StructuredFixture f = new StructuredFixture();
      DepartmentEntity owner = variant == 0 ? null : entityDepartment(2L, variant == 1);
      RoleEntity assigned = entityRole(owner);
      f.target.assignRole(assigned);
      when(f.authority.snapshot(f.actor))
          .thenReturn(
              new StructuredPersonnelAuthority.AuthoritySnapshot(1L, variant == 4 ? 1 : 3, false));
      if (variant == 2) when(f.scope.allowedIds(f.actor)).thenReturn(List.of(1L));
      if (variant == 3) when(f.departments.ancestorIds(1L)).thenReturn(Set.of(1L));
      assertEquals(
          variant == 5 ? List.of() : List.of(9L), f.personnel.roles(HEADER, 2L).getLockedRoleIds());
      if (variant == 0) {
        StructuredEffectivePermissionService effective =
            new StructuredEffectivePermissionService(
                mock(ApiResourceJpaRepository.class),
                f.users,
                f.departments,
                f.permissionRows,
                () -> Set.of("rbac"));
        assertTrue(effective.applicableRoleIds(2L).isEmpty());
        TransferUserCommand move = new TransferUserCommand();
        move.setDepartmentId(1L);
        f.personnel.move(HEADER, 2L, move);
        assertTrue(f.target.getRoles().isEmpty());
      }
    }
  }

  /** DDD应用门禁对不一致快照重读，归属事实缺失绝不等同于全局角色。 */
  @Test
  @DisplayName("R28 DDD局部读取和部门角色写入独立验证身份及有效级别")
  void R28_dddReadAndDefinitionBoundaries() {
    DddFixture f = new DddFixture();
    when(f.users.find(new User.Key(1L))).thenReturn(Optional.of(user(child, Set.of())));
    assertCode("MANAGEMENT_SCOPE_DENIED", () -> f.service.checkTarget(HEADER, null, false));
    when(f.users.find(new User.Key(1L)))
        .thenReturn(
            Optional.of(user(child, Set.of(new Role(1L, "manager", "经理", 3, true, Set.of())))));
    for (Department owner : new Department[] {null, department(9L, null, false)}) {
      when(f.roles.find(new Role.Key(9L))).thenReturn(Optional.of(role(owner)));
      assertCode("MANAGEMENT_SCOPE_DENIED", () -> f.service.checkTarget(HEADER, 9L, false));
    }
    when(f.roles.find(new Role.Key(9L))).thenReturn(Optional.of(role(child)));
    assertDoesNotThrow(() -> f.service.checkTarget(HEADER, 9L, false));
    assertCode("MANAGEMENT_SCOPE_DENIED", () -> f.service.create(HEADER, create()));
    when(f.users.find(new User.Key(1L))).thenReturn(Optional.empty());
    assertCode("SESSION_INVALID", () -> f.service.checkTarget(HEADER, null, false));
    assertCode("SESSION_INVALID", () -> f.service.create(HEADER, create()));
    when(f.users.find(new User.Key(1L)))
        .thenReturn(
            Optional.of(user(root, Set.of(new Role(1L, "admin", "管理员", 5, true, Set.of())))));
    when(f.organizations.find(new Department.Key(2L)))
        .thenReturn(Optional.of(department(2L, 1L, true)));
    assertCode("RESOURCE_NOT_FOUND", () -> f.service.create(HEADER, create()));
    verify(f.roles, never()).save(any());
  }

  /** 没有人员和下级仍有角色所属时，删除能力必须明确关闭。 */
  @Test
  @DisplayName("R24/R28 DDD空部门存在所属角色时不显示删除能力")
  void R28_ownedDepartmentIsNotDeletable() {
    UserRepository users = mock(UserRepository.class);
    ManagementAuthorization authorization = mock(ManagementAuthorization.class);
    when(authorization.routes(1L))
        .thenReturn(Set.of(new RbacCatalog.Route("DELETE", "/api/departments/{id}")));
    User admin = user(root, Set.of(new Role(1L, "admin", "管理员", 5, true, Set.of())));
    when(users.statistics(any())).thenReturn(new OrganizationFacts.Statistics(0, 0, List.of()));
    DddDepartmentView view = new DddDepartmentView(users, authorization);
    assertFalse(
        view.build(admin, new OrganizationFacts(1L, List.of(root, child), Set.of(2L)))
            .tree()
            .get(1)
            .getCanDelete());
  }

  /** 构造明确来源的部门，异常事实只在测试替身中使用。 */
  private Department department(Long id, Long parent, boolean deleted) {
    return new Department(
        id,
        "d" + id,
        "部门" + id,
        parent,
        parent == null ? Department.Type.HEADQUARTERS : Department.Type.DEPARTMENT,
        deleted);
  }

  /** 使用显式归属模拟历史坏关联，数据库新写入口仍有约束。 */
  private Role role(Department owner) {
    return new Role(
        9L,
        "department-role",
        "部门角色",
        null,
        true,
        Set.of(),
        Role.Type.DEPARTMENT_CUSTOM,
        owner,
        null);
  }

  /** 不含个人资料的固定用例主体。 */
  private User user(Department department, Set<Role> roles) {
    return new User(1L, "actor", "encoded", "操作者", null, null, department, true, roles);
  }

  /** 所属字段不由角色名称推断。 */
  private static DepartmentEntity entityDepartment(Long id, boolean deleted) {
    DepartmentEntity result = new DepartmentEntity("d" + id, "部门" + id);
    ReflectionTestUtils.setField(result, "id", id);
    result.setDeleted(deleted);
    result.setType(DepartmentType.DEPARTMENT);
    return result;
  }

  /** 已有坏数据无法经正常写入口建立，以反射表达仓储读出的故障事实。 */
  private static RoleEntity entityRole(DepartmentEntity owner) {
    RoleEntity result = new RoleEntity("department-role", "部门角色");
    ReflectionTestUtils.setField(result, "id", 9L);
    ReflectionTestUtils.setField(result, "type", RoleType.DEPARTMENT_CUSTOM);
    ReflectionTestUtils.setField(result, "ownerDepartment", owner);
    return result;
  }

  /** 部门角色创建请求的已校验应用命令。 */
  private CreateRoleCommand create() {
    CreateRoleCommand command = new CreateRoleCommand();
    command.setCode("created");
    command.setName("新角色");
    command.setType(RoleType.DEPARTMENT_CUSTOM);
    command.setOwnerDepartmentId(2L);
    return command;
  }

  /** 只接受明确业务错误码，防止把NPE误记为安全拒绝。 */
  private void assertCode(String expected, org.junit.jupiter.api.function.Executable action) {
    assertEquals(expected, assertThrows(BusinessException.class, action).getCode());
  }

  /** 结构化用例以真实实体表达关系，替身只控制仓储与门禁事实。 */
  private static final class StructuredFixture {
    private final CurrentIdentity actor = new CurrentIdentity(1L, "actor", 1L);
    private final DepartmentEntity owner = entityDepartment(2L, false);
    private final DepartmentEntity targetDepartment = entityDepartment(1L, false);
    private final UserEntity target =
        new UserEntity("target", "encoded", "目标", targetDepartment, true);
    private final StructuredAuthenticationService auth =
        mock(StructuredAuthenticationService.class);
    private final StructuredDepartmentScopeService scope =
        mock(StructuredDepartmentScopeService.class);
    private final StructuredPersonnelAuthority authority = mock(StructuredPersonnelAuthority.class);
    private final StructuredEffectivePermissionService effective =
        mock(StructuredEffectivePermissionService.class);
    private final StructuredGlobalManagementAccess access =
        mock(StructuredGlobalManagementAccess.class);
    private final RequestContext requests = mock(RequestContext.class);
    private final RoleJpaRepository roles = mock(RoleJpaRepository.class);
    private final UserJpaRepository users = mock(UserJpaRepository.class);
    private final DepartmentJpaRepository departments = mock(DepartmentJpaRepository.class);
    private final PermissionJpaRepository permissionRows = mock(PermissionJpaRepository.class);
    private final StructuredRoleService service;
    private final StructuredUserManagementService personnel;

    /** 默认具有真实路由资格，测试分别改变级别、归属和祖先事实。 */
    private StructuredFixture() {
      when(auth.require(HEADER)).thenReturn(actor);
      when(scope.isGlobal(actor)).thenReturn(true);
      when(scope.allowedIds(actor)).thenReturn(List.of(1L, 2L));
      when(authority.level(actor)).thenReturn(5);
      when(authority.snapshot(actor))
          .thenReturn(new StructuredPersonnelAuthority.AuthoritySnapshot(1L, 5, true));
      when(authority.canManage(any(StructuredPersonnelAuthority.AuthoritySnapshot.class), any()))
          .thenReturn(true);
      when(effective.allows(any(), any(), any())).thenReturn(true);
      when(requests.operation()).thenReturn(new RequestContext.Operation("GET", "/api/roles"));
      when(departments.findById(2L)).thenReturn(Optional.of(owner));
      when(departments.findById(1L)).thenReturn(Optional.of(targetDepartment));
      when(departments.ancestorIds(1L)).thenReturn(Set.of(1L, 2L));
      ReflectionTestUtils.setField(target, "id", 2L);
      when(users.findById(2L)).thenReturn(Optional.of(target));
      doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
          .when(access)
          .write(any(), any());
      service =
          new StructuredRoleService(
              () -> Set.of("rbac"),
              roles,
              permissionRows,
              access,
              auth,
              effective,
              requests,
              scope,
              authority,
              departments);
      personnel =
          new StructuredUserManagementService(
              () -> Set.of("rbac"),
              auth,
              requests,
              effective,
              scope,
              mock(StructuredMaintenanceGuard.class),
              users,
              authority,
              roles,
              departments);
    }
  }

  /** DDD门禁输入仅来自领域对象与端口，不引用实体作为领域决策。 */
  private final class DddFixture {
    private final CurrentIdentity actor = new CurrentIdentity(1L, "actor", 2L);
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final DddRequestAccess requests = mock(DddRequestAccess.class);
    private final DddWrite writes = mock(DddWrite.class);
    private final DddRoleService service;

    /** 全局写协议仍由独立测试覆盖，这里仅触发定义资格的重读。 */
    private DddFixture() {
      when(requests.require(HEADER)).thenReturn(actor);
      when(organizations.load()).thenReturn(new OrganizationFacts(1L, List.of(root, child)));
      doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
          .when(writes)
          .global(any(), any());
      service =
          new DddRoleService(
              () -> Set.of("rbac"),
              roles,
              mock(PermissionRepository.class),
              mock(ManagementAccessUseCase.class),
              writes,
              requests,
              new DddScope(organizations),
              users,
              organizations);
    }
  }
}
