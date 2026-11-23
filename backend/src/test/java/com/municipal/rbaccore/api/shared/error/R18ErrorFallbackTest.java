package com.municipal.rbaccore.api.shared.error;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.port.security.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** R18必要头与底层约束错误的兜底翻译保持固定契约，不泄露实现细节。 */
class R18ErrorFallbackTest {
  /** 即使异常消息含内部文本，公开响应仍只有稳定错误码和追踪号。 */
  @Test
  @DisplayName("R18 缺少身份头与数据库约束兜底均隐藏内部信息")
  void R18_missingHeaderAndConstraintRemainSanitized() {
    RequestContext requests = mock(RequestContext.class);
    when(requests.requestId()).thenReturn("test-request");
    ApiExceptionHandler handler = new ApiExceptionHandler(requests);
    com.municipal.rbaccore.api.shared.ApiResponse<Object> missing =
        handler.missingHeader(new IllegalStateException("internal-header-secret")).getBody();
    assertEquals("AUTHENTICATION_REQUIRED", missing.code());
    assertEquals("请先登录", missing.message());
    assertEquals("test-request", missing.requestId());
    com.municipal.rbaccore.api.shared.ApiResponse<Object> conflict =
        handler.conflict(new DataIntegrityViolationException("sql-and-password")).getBody();
    assertEquals("DATA_CONFLICT", conflict.code());
    assertFalse(conflict.message().contains("sql"));
    assertNull(conflict.data());
    verify(requests).error("AUTHENTICATION_REQUIRED");
    verify(requests).error("DATA_CONFLICT");
  }
}
