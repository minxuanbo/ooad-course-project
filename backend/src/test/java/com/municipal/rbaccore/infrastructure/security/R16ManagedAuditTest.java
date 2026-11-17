package com.municipal.rbaccore.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.port.audit.AuditEvent;
import com.municipal.rbaccore.service.port.audit.AuditWriter;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.ManagementAuthorization;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/** R16区分访问控制结果与业务执行结果，只验证切面责任而不模拟数据库业务。 */
class R16ManagedAuditTest {
  /** 访问获准后的参数/资源/冲突/内部故障不能被记为权限拒绝。 */
  @ParameterizedTest
  @ValueSource(ints = {400, 404, 409, 500})
  @DisplayName("R16 鉴权允许与后续业务错误分别记录")
  void R16_businessFailureRetainsAllowedDecision(int status) throws Throwable {
    AuditEvent event = invoke(status, true);
    assertTrue(event.allowed(), "业务失败不能冒充鉴权拒绝");
    assertEquals(status, event.httpStatus());
    assertEquals("BUSINESS_ERROR", event.reason());
    assertEquals("/api/roles", event.requestPath());
    assertEquals("request-test", event.requestId());
  }

  /** 人员/系统范围拒绝也属于安全拒绝，不能因入口API通过而记为允许。 */
  @Test
  @DisplayName("R16 业务范围拒绝仍记为鉴权拒绝")
  void R16_scopeFailureRemainsDenied() throws Throwable {
    assertFalse(invoke(403, true).allowed());
  }

  /** API未获准时不得执行业务，拒绝记录只产生一次。 */
  @Test
  @DisplayName("R16 API权限拒绝不执行业务且只采集一次")
  void R16_permissionFailureDoesNotExecuteBusiness() throws Throwable {
    assertFalse(invoke(403, false).allowed());
  }

  /** 使用真实请求上下文和切面，替身只提供其依赖端口的已知输入。 */
  private AuditEvent invoke(int status, boolean permitted) throws Throwable {
    AuthenticationUseCase auth = mock(AuthenticationUseCase.class);
    ManagementAuthorization permissions = mock(ManagementAuthorization.class);
    AuditWriter audit = mock(AuditWriter.class);
    ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/roles");
    request.addHeader("Authorization", "Bearer test");
    request.setAttribute("requestId", "request-test");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/roles");
    when(auth.require("Bearer test")).thenReturn(new CurrentIdentity(1L, "admin", 1L));
    when(permissions.allows(1L, "GET", "/api/roles")).thenReturn(permitted);
    when(point.proceed()).thenThrow(new BusinessException(status, "BUSINESS_ERROR", "测试业务错误"));
    org.springframework.web.context.request.RequestAttributes previous =
        RequestContextHolder.getRequestAttributes();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      assertThrows(
          BusinessException.class,
          () -> new ManagedAspect(auth, permissions, audit).authorize(point));
      if (!permitted) verify(point, never()).proceed();
      ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
      verify(audit, times(1)).record(event.capture());
      assertTrue((Boolean) request.getAttribute("auditRecorded"));
      return event.getValue();
    } finally {
      RequestContextHolder.setRequestAttributes(previous);
    }
  }
}
