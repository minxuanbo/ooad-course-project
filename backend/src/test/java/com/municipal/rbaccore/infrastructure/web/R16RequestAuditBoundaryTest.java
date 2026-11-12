package com.municipal.rbaccore.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.api.shared.error.ApiExceptionHandler;
import com.municipal.rbaccore.service.port.audit.AuditWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.RequestContextHolder;

/** R16/R20验证后台调用、非API请求和异常响应的日志与隐私边界。 */
class R16RequestAuditBoundaryTest {
  /** 后台调用没有虚假请求身份，未知异常的响应不得泄漏内部消息。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R16/R20 后台调用没有虚假请求身份，未知异常的响应不得泄漏内部消息。")
  void backgroundCallsHaveNoRequestAndInternalErrorsNeverExposeSecrets() {
    org.springframework.web.context.request.RequestAttributes previous =
        RequestContextHolder.getRequestAttributes();
    RequestContextHolder.resetRequestAttributes();
    try {
      assertNull(RequestAuditFilter.current());
      assertEquals("", RequestAuditFilter.requestId());
      RequestAuditFilter.actor(1L, "background");
      RequestAuditFilter.error("test");
      org.springframework.http.ResponseEntity<com.municipal.rbaccore.api.shared.ApiResponse<Object>>
          response =
              new ApiExceptionHandler(new HttpRequestContext())
                  .unexpected(new IllegalStateException("secret-token-and-sql"));
      assertEquals(500, response.getStatusCode().value());
      assertEquals("INTERNAL_ERROR", response.getBody().code());
      assertEquals("服务器内部错误", response.getBody().message());
      assertNull(response.getBody().data());
    } finally {
      RequestContextHolder.setRequestAttributes(previous);
    }
  }

  /** 非API请求只生成追踪号，不混入鉴权事件统计。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R16/R20 非API请求只生成追踪号，不混入鉴权事件统计。")
  void nonApiRequestsAreNotRecorded() throws Exception {
    AuditWriter recorder = mock(AuditWriter.class);
    MockHttpServletResponse response = new MockHttpServletResponse();
    new RequestAuditFilter(recorder)
        .doFilterInternal(
            new MockHttpServletRequest("GET", "/favicon.ico"),
            response,
            (request, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(404));
    assertNotNull(response.getHeader("X-Request-Id"));
    verifyNoInteractions(recorder);
  }
}
