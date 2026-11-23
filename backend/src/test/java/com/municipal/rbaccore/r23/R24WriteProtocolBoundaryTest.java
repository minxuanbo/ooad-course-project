package com.municipal.rbaccore.r23;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.ddd.application.department.DddDepartmentService;
import com.municipal.rbaccore.service.ddd.application.support.DddDepartmentView;
import com.municipal.rbaccore.service.ddd.application.support.DddRequestAccess;
import com.municipal.rbaccore.service.ddd.application.support.DddScope;
import com.municipal.rbaccore.service.ddd.application.support.DddWrite;
import com.municipal.rbaccore.service.ddd.application.user.DddUserManagementService;
import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.Department;
import com.municipal.rbaccore.service.ddd.domain.model.DomainRuleViolation;
import com.municipal.rbaccore.service.ddd.domain.model.OrganizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.Role;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import com.municipal.rbaccore.service.ddd.domain.repository.AuthorizationRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.OrganizationRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.RoleRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.MaintenanceProtection;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import com.municipal.rbaccore.service.port.user.TransferUserCommand;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R24纯用例协议测试注入领域端口结果，不冒充数据库事务测试。 */
class R24WriteProtocolBoundaryTest {
  /** 范围预读和按ID读取之间出现墓碑时，不能把用户调入该部门。 */
  @Test
  @DisplayName("R24 DDD部门只读预检不写入，调动遇到墓碑读取明确拒绝")
  void R24_readPreflightAndDeletedTransferTarget() {
    Department root = new Department(1L, "root", "根", null, Department.Type.HEADQUARTERS, false);
    Department child = new Department(2L, "child", "下级", 1L, Department.Type.DEPARTMENT, false);
    User admin =
        new User(
            1L,
            "admin",
            "encoded",
            "管理员",
            null,
            null,
            root,
            true,
            Set.of(new Role(1L, "base", "基础五级", 5, true, Set.of())));
    OrganizationRepository organizations = mock(OrganizationRepository.class);
    when(organizations.load()).thenReturn(new OrganizationFacts(1L, List.of(root, child)));
    when(organizations.find(new Department.Key(2L)))
        .thenReturn(
            Optional.of(new Department(2L, "child", "墓碑", 1L, Department.Type.DEPARTMENT, true)));
    UserRepository users = mock(UserRepository.class);
    when(users.find(new User.Key(1L))).thenReturn(Optional.of(admin));
    DddRequestAccess requests = mock(DddRequestAccess.class);
    when(requests.require("Bearer test")).thenReturn(new CurrentIdentity(1L, "admin", 1L));
    DddWrite writes =
        new DddWrite(
            mock(MaintenanceProtection.class), requests, mock(ManagementAccessUseCase.class));
    DddScope scope = new DddScope(organizations);
    DddDepartmentService departments =
        new DddDepartmentService(
            mock(AuthenticationUseCase.class),
            requests,
            writes,
            scope,
            mock(DddDepartmentView.class),
            organizations,
            users);
    assertDoesNotThrow(() -> departments.checkTarget("Bearer test", 2L, false));
    AuthorizationRepository authorization = mock(AuthorizationRepository.class);
    when(authorization.facts(new User.Key(1L)))
        .thenReturn(Optional.of(new AuthorizationFacts(admin, Set.of(1L))));
    DddUserManagementService personnel =
        new DddUserManagementService(
            () -> Set.of("rbac"),
            mock(AuthenticationUseCase.class),
            users,
            mock(RoleRepository.class),
            organizations,
            authorization,
            scope,
            requests,
            writes);
    TransferUserCommand command = new TransferUserCommand();
    command.setDepartmentId(2L);
    assertEquals(
        "MANAGEMENT_SCOPE_DENIED",
        assertThrows(BusinessException.class, () -> personnel.move("Bearer test", 1L, command))
            .getCode());
    verify(users, never()).save(any());
  }

  /** 可执行回调的领域错误应沿既有HTTP语义翻译，不执行成功后的维护者检查。 */
  @Test
  @DisplayName("R24 写协议翻译人员拒绝与非法层级，失败不执行成功后置条件")
  void R24_domainCallbackFailuresRetainMeaning() {
    MaintenanceProtection maintenance = mock(MaintenanceProtection.class);
    DddRequestAccess requests = mock(DddRequestAccess.class);
    when(requests.require("Bearer test")).thenReturn(new CurrentIdentity(1L, "admin", 1L));
    DddWrite writes = new DddWrite(maintenance, requests, mock(ManagementAccessUseCase.class));
    for (DomainRuleViolation.Rule rule :
        List.of(
            DomainRuleViolation.Rule.ROLE_NOT_ASSIGNABLE, DomainRuleViolation.Rule.INVALID_CHILD)) {
      BusinessException failure =
          assertThrows(
              BusinessException.class,
              () ->
                  writes.scoped(
                      "Bearer test",
                      actor -> {
                        assertEquals(1L, actor.id());
                        throw new DomainRuleViolation(rule, "已翻译的领域说明");
                      }));
      assertEquals(
          rule == DomainRuleViolation.Rule.ROLE_NOT_ASSIGNABLE ? 403 : 400, failure.getStatus());
      assertEquals(
          rule == DomainRuleViolation.Rule.ROLE_NOT_ASSIGNABLE
              ? "MANAGEMENT_SCOPE_DENIED"
              : "DEPARTMENT_TYPE_INVALID",
          failure.getCode());
    }
    verify(maintenance, never()).retainOne();
  }

  /** 领域仓储没有可信根时必须拒绝，不能将任意parent为空的部门认成共同根。 */
  @Test
  @DisplayName("R24 来源仓储缺少可信根时拒绝组织范围")
  void R24_missingTrustedRootFailsClosed() {
    OrganizationRepository repository = mock(OrganizationRepository.class);
    when(repository.load()).thenReturn(new OrganizationFacts(null, List.of()));
    assertEquals(
        "MANAGEMENT_SCOPE_DENIED",
        assertThrows(BusinessException.class, () -> new DddScope(repository).facts()).getCode());
  }
}
