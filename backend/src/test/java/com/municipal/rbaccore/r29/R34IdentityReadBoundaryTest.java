package com.municipal.rbaccore.r29;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.ddd.application.identity.DddApplicationIdentityService;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.*;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.identity.*;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.security.*;
import com.municipal.rbaccore.service.structured.identity.StructuredApplicationIdentityService;
import com.municipal.rbaccore.service.structured.security.StructuredDepartmentScopeService;
import com.municipal.rbaccore.service.structured.security.StructuredEffectivePermissionService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** R29范围快照与目录读取不一致时的失败关闭；不声称替身执行了数据库。 */
class R34IdentityReadBoundaryTest {
  private static final String DEPARTMENT_USERS = "/auth/departments/{departmentId}/users";
  private static final String INVALID_API = "/invalid";
  private static final String HEADER = "Bearer identity-boundary";
  private static final String SYSTEM = "external";

  /** 目录快照与权限快照不一致时，停用或待接入权限所属应用均不能导出。 */
  @Test
  @DisplayName("R28/R29 structured权限所属应用停用或待接入不能贡献短码")
  void R28_inconsistentPermissionApplicationIsNotExported() {
    for (boolean pending : List.of(true, false)) {
      Fixture f = new Fixture();
      ApplicationSystemEntity stale = new ApplicationSystemEntity(SYSTEM, "陈旧应用快照");
      ReflectionTestUtils.setField(stale, "id", 1L);
      if (pending)
        stale.setConnectionStatus(
            com.municipal.rbaccore.service.port.resource.ConnectionStatus.PENDING);
      else stale.setEnabled(false);
      PermissionEntity broken = new PermissionEntity(stale, "stale", "不应导出", PermissionType.MENU);
      ReflectionTestUtils.setField(broken, "id", 2L);
      when(f.permissionRows.directPermissions(1L, Set.of(1L), Set.of(SYSTEM)))
          .thenReturn(List.of(f.entityPermission, broken));
      assertEquals(List.of("directory"), f.structured.current(HEADER).permissions());
    }
  }

  /** 两策略都必须区分越界已存在与范围内却读取不到的目录。 */
  @Test
  @DisplayName("R29 两策略范围内目录缺失返回404，越界已存在返回403")
  void R29_directoryReadInconsistencyFailsClosed() {
    Fixture f = new Fixture();
    for (ApplicationIdentityUseCase service : List.of(f.structured, f.ddd)) {
      assertEquals(
          "IDENTITY_DEPARTMENT_NOT_FOUND",
          assertThrows(
                  BusinessException.class, () -> service.departmentUsers(HEADER, 1L, null, 1, 20))
              .getCode());
      when(f.directory.department(9L))
          .thenReturn(
              Optional.of(new ApplicationDepartmentResult(9L, "foreign", "外部门", null, false)));
      assertEquals(
          "MANAGEMENT_SCOPE_DENIED",
          assertThrows(
                  BusinessException.class, () -> service.departmentUsers(HEADER, 9L, null, 1, 20))
              .getCode());
    }
    verify(f.directory, never()).users(any(), any(), anyInt(), anyInt());
  }

  /** 启用的权限映射到停用或另一系统API时，不得导出可用短码。 */
  @Test
  @DisplayName("R29 结构化短码拒绝停用系统和跨系统API映射")
  void R29_invalidApiSystemDoesNotExportShortCode() {
    for (boolean disabled : new boolean[] {true, false}) {
      Fixture f = new Fixture();
      ApplicationSystemEntity foreign = new ApplicationSystemEntity("different", "另一应用");
      ReflectionTestUtils.setField(foreign, "id", 2L);
      foreign.setEnabled(!disabled);
      ApiResourceEntity invalid = new ApiResourceEntity(f.entitySystem, "GET", INVALID_API);
      PermissionEntity broken =
          new PermissionEntity(f.entitySystem, "broken", "坏映射", PermissionType.API);
      broken.mapApi(invalid);
      // 仓储可能读到历史坏关联；实体写入口本身已拒绝跨系统映射。
      ReflectionTestUtils.setField(invalid, "system", foreign);
      ReflectionTestUtils.setField(broken, "id", 2L);
      when(f.permissionRows.directPermissions(1L, Set.of(1L), Set.of(SYSTEM)))
          .thenReturn(List.of(f.entityPermission, broken));
      ApplicationIdentityResult result = f.structured.current(HEADER);
      assertEquals(List.of("directory"), result.permissions());
      assertFalse(result.permissionApis().containsKey("broken"));
    }
  }

  /** 两策略使用各自事实模型，统一外部端口只提供目录和令牌输入。 */
  @Test
  @DisplayName("R28 结构化身份读取过滤仓储返回的停用及待接入权限事实")
  void R28_stalePermissionSystemsDoNotExport() {
    for (boolean pending : List.of(true, false)) {
      Fixture f = new Fixture();
      ApplicationSystemEntity unavailable = new ApplicationSystemEntity("stale", "陈旧系统");
      ReflectionTestUtils.setField(unavailable, "id", 2L);
      unavailable.setEnabled(pending);
      unavailable.setConnectionStatus(
          pending
              ? com.municipal.rbaccore.service.port.resource.ConnectionStatus.PENDING
              : com.municipal.rbaccore.service.port.resource.ConnectionStatus.CONNECTED);
      PermissionEntity stale =
          new PermissionEntity(unavailable, "stale", "陈旧权限", PermissionType.BUTTON);
      ReflectionTestUtils.setField(stale, "id", 2L);
      when(f.permissionRows.directPermissions(1L, Set.of(1L), Set.of(SYSTEM)))
          .thenReturn(List.of(f.entityPermission, stale));
      assertEquals(List.of("directory"), f.structured.current(HEADER).permissions());
    }
  }

  /** 两策略使用各自事实模型，统一外部端口只提供目录和令牌输入。 */
  private static final class Fixture {
    private final AuthenticationUseCase auth = mock(AuthenticationUseCase.class);
    private final IdentityTokenStore tokens = mock(IdentityTokenStore.class);
    private final RequestContext requests = mock(RequestContext.class);
    private final IdentityDirectoryGateway directory = mock(IdentityDirectoryGateway.class);
    private final PermissionJpaRepository permissionRows = mock(PermissionJpaRepository.class);
    private final ApplicationSystemEntity entitySystem =
        new ApplicationSystemEntity(SYSTEM, "外部应用");
    private final PermissionEntity entityPermission =
        new PermissionEntity(entitySystem, "directory", "组织查询", PermissionType.API);
    private final StructuredApplicationIdentityService structured;
    private final DddApplicationIdentityService ddd;

    /** 最小有效直接映射让测试抵达目录边界，不通过跳过认证来构造结果。 */
    private Fixture() {
      when(tokens.system(HEADER)).thenReturn(SYSTEM);
      when(tokens.requireForSystem(HEADER, SYSTEM)).thenReturn(1L);
      CurrentIdentity actor = new CurrentIdentity(1L, "sample", 1L);
      when(auth.requireForSystem(HEADER, SYSTEM)).thenReturn(actor);
      ReflectionTestUtils.setField(entitySystem, "id", 1L);
      ReflectionTestUtils.setField(entityPermission, "id", 1L);
      ApiResourceEntity api = new ApiResourceEntity(entitySystem, "GET", DEPARTMENT_USERS);
      ReflectionTestUtils.setField(api, "id", 1L);
      entityPermission.mapApi(api);
      UserJpaRepository userRows = mock(UserJpaRepository.class);
      DepartmentEntity entityRoot = new DepartmentEntity("root", "根");
      ReflectionTestUtils.setField(entityRoot, "id", 1L);
      UserEntity entityUser = new UserEntity("sample", "encoded", "示例", entityRoot, true);
      when(userRows.findById(1L)).thenReturn(Optional.of(entityUser));
      when(permissionRows.directPermissions(1L, Set.of(1L), Set.of(SYSTEM)))
          .thenReturn(List.of(entityPermission));
      ApplicationSystemJpaRepository systemRows = mock(ApplicationSystemJpaRepository.class);
      when(systemRows.findByCode(SYSTEM)).thenReturn(Optional.of(entitySystem));
      StructuredDepartmentScopeService scope = mock(StructuredDepartmentScopeService.class);
      when(scope.allowedIds(actor)).thenReturn(List.of(1L));
      when(scope.isGlobal(actor)).thenReturn(true);
      StructuredEffectivePermissionService effective =
          mock(StructuredEffectivePermissionService.class);
      when(effective.applicableRoleIds(1L)).thenReturn(Set.of(1L));
      structured =
          new StructuredApplicationIdentityService(
              auth,
              tokens,
              permissionRows,
              effective,
              systemRows,
              userRows,
              requests,
              directory,
              mock(ApiOperationCatalog.class),
              scope);

      ApplicationSystem system = new ApplicationSystem(1L, SYSTEM, "外部应用", true);
      ApiResource domainApi =
          new ApiResource(1L, system, new ApiResource.Route("GET", DEPARTMENT_USERS), true);
      Permission permission =
          new Permission(
              1L, system, null, "directory", "组织查询", Permission.Type.API, true, Set.of(domainApi));
      Department root = new Department(1L, "root", "根", null, Department.Type.HEADQUARTERS, false);
      User user =
          new User(
              1L,
              "sample",
              "encoded",
              "示例",
              null,
              null,
              root,
              true,
              Set.of(new Role(1L, "role", "外部角色", null, true, Set.of(permission))));
      AuthorizationRepository authorization = mock(AuthorizationRepository.class);
      when(authorization.facts(new User.Key(1L)))
          .thenReturn(Optional.of(new AuthorizationFacts(user)));
      OrganizationRepository organizations = mock(OrganizationRepository.class);
      when(organizations.load()).thenReturn(new OrganizationFacts(1L, List.of(root)));
      ApplicationSystemRepository systems = mock(ApplicationSystemRepository.class);
      when(systems.catalog()).thenReturn(List.of(system));
      ddd =
          new DddApplicationIdentityService(
              auth,
              tokens,
              authorization,
              mock(UserRepository.class),
              systems,
              requests,
              organizations,
              directory,
              mock(ApiOperationCatalog.class));
    }
  }
}
