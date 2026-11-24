package com.municipal.rbaccore.infrastructure.onboarding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.ddd.application.onboarding.DddOnboardingService;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationOnboarding;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import com.municipal.rbaccore.service.ddd.domain.repository.OnboardingRepository;
import com.municipal.rbaccore.service.port.auth.*;
import com.municipal.rbaccore.service.port.onboarding.*;
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import com.municipal.rbaccore.service.port.security.*;
import com.municipal.rbaccore.service.structured.onboarding.StructuredOnboardingService;
import com.municipal.rbaccore.service.structured.security.StructuredGlobalManagementAccess;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 端口故障的纯技术分类，不替代真实MySQL事务与协议契约。 */
class R35OrchestrationBoundaryTest {
  /** 待接入只把明确403分类为PENDING，网络无状态和401都须保留失败。 */
  @Test
  @DisplayName("R35 待接入协议失败按实际状态分类，不能把网络或口令错误当成预期拒绝")
  void R35_ProbeStatusClassification() {
    ApplicationSystemJpaRepository systems = mock(ApplicationSystemJpaRepository.class);
    ApplicationSystemEntity row = new ApplicationSystemEntity("example", "示例");
    ReflectionTestUtils.setField(row, "id", 2L);
    row.setConnectionStatus(ConnectionStatus.PENDING);
    when(systems.findById(2L)).thenReturn(Optional.of(row));
    AuthenticationUseCase auth = mock(AuthenticationUseCase.class);
    IdentityResult identity = new IdentityResult();
    identity.setManagementLevel(5);
    when(auth.me("header")).thenReturn(identity);
    OnboardingCodec codec = mock(OnboardingCodec.class);
    when(codec.fingerprint(any(), any())).thenReturn("fingerprint");
    IdentityProtocolProbe probe = mock(IdentityProtocolProbe.class);
    List<OnboardingReport.Check> failure =
        List.of(
            new OnboardingReport.Check("NETWORK", "FAIL", "网络失败", null),
            new OnboardingReport.Check("LOGIN", "FAIL", "口令拒绝", 401),
            new OnboardingReport.Check("LOGIN", "FAIL", "待接入拒绝", 403));
    when(probe.run(any(), any(), any())).thenReturn(failure);
    RoleJpaRepository roleRows = mock(RoleJpaRepository.class);
    StructuredOnboardingService structured =
        new StructuredOnboardingService(
            systems,
            mock(PermissionJpaRepository.class),
            mock(ApiResourceJpaRepository.class),
            roleRows,
            mock(ApplicationProfileJpaRepository.class),
            mock(ApiOperationJpaRepository.class),
            new OperationMetadataJson(new com.fasterxml.jackson.databind.ObjectMapper()),
            mock(StructuredGlobalManagementAccess.class),
            auth,
            mock(ManagementAuthorization.class),
            codec,
            mock(OpenApiReader.class),
            probe);
    OnboardingRepository repository = mock(OnboardingRepository.class);
    ApplicationOnboarding.Configuration empty =
        new ApplicationOnboarding.Configuration(
            null, null, List.of(), List.of(), List.of(), List.of());
    when(repository.find(any()))
        .thenReturn(
            Optional.of(
                new ApplicationOnboarding(
                    new ApplicationSystem(
                        2L, "example", "示例", true, ApplicationSystem.Connection.PENDING),
                    empty,
                    List.of(
                        new ApplicationOnboarding.RoleOption(
                            3L, "employee", "员工", "SYSTEM_BUILTIN", true)))));
    DddOnboardingService ddd =
        new DddOnboardingService(
            repository,
            mock(ManagementAccessUseCase.class),
            auth,
            mock(ManagementAuthorization.class),
            null,
            codec,
            mock(OpenApiReader.class),
            probe);
    // 配置校验仍使用真实策略，只在技术端口注入无状态故障。
    com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity role =
        new com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity("employee", "员工");
    ReflectionTestUtils.setField(role, "id", 3L);
    when(roleRows.findAll(any(org.springframework.data.domain.Sort.class)))
        .thenReturn(List.of(role));
    OnboardingPlan desired =
        new OnboardingPlan(
            "fingerprint",
            null,
            "LOGIN",
            List.of(),
            List.of(new OnboardingPlan.Permission("LOGIN", "登录", "BUTTON", null, true, List.of())),
            List.of(),
            List.of(new OnboardingPlan.Grant(3L, List.of("LOGIN"))));
    for (OnboardingUseCase service : List.of(structured, ddd)) {
      OnboardingReport report = service.check("header", 2L, desired, "tester", "secret");
      assertTrue(report.configurationValid(), report.errors().toString());
      assertEquals(
          List.of("FAIL", "FAIL", "PENDING"),
          report.protocolChecks().stream().map(OnboardingReport.Check::status).toList());
    }
  }
}
