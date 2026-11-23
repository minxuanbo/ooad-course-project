package com.municipal.rbaccore.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.port.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** R16快照游标的范围和签名不能因格式正确而跳过核验。 */
class R16AuditCursorBoundaryTest {
  /** 通过生产签名器构造错误范围，分别验证每一项区间和绑定约束。 */
  @Test
  @DisplayName("R16 有效签名仍须检查快照、末条ID和筛选绑定")
  void R16_rejectsInvalidSignedRanges() {
    AuditPageCursor cursors = new AuditPageCursor();
    assertEquals(new AuditPageCursor.Position(10, 5), cursors.decode(cursors.encode(10, 5, "filter"), "filter", 10));
    for (long[] values : List.of(new long[] {-1, 1}, new long[] {11, 5}, new long[] {10, 0}, new long[] {10, 11})) {
      assertInvalid(() -> cursors.decode(cursors.encode(values[0], values[1], "filter"), "filter", 10));
    }
    assertInvalid(() -> cursors.decode(cursors.encode(10, 5, "filter"), "changed-filter", 10));
    assertInvalid(() -> cursors.decode("invalid!", "filter", 10));
    assertInvalid(() -> cursors.decode(base64("10:5:filter"), "filter", 10));
    assertInvalid(() -> cursors.decode(base64("10:5:filter:wrong-signature"), "filter", 10));
  }

  /** 加密提供者故障必须停止生成游标，不能输出未签名分页参数。 */
  @Test
  @DisplayName("R16 HMAC提供者缺失时失败关闭")
  void R16_hmacProviderFailureStopsCursorCreation() throws Exception {
    AuditPageCursor cursors = new AuditPageCursor();
    try (MockedStatic<Mac> mac = mockStatic(Mac.class)) {
      mac.when(() -> Mac.getInstance("HmacSHA256")).thenThrow(new NoSuchAlgorithmException("provider unavailable"));
      assertEquals("运行环境缺少HMAC支持", assertThrows(IllegalStateException.class,
          () -> cursors.encode(10, 5, "filter")).getMessage());
    }
  }

  /** 无效游标使用固定输入错误码，不向客户端展示密钥或验签细节。 */
  private void assertInvalid(org.junit.jupiter.api.function.Executable action) {
    BusinessException failure = assertThrows(BusinessException.class, action);
    assertEquals(400, failure.getStatus());
    assertEquals("INVALID_CURSOR", failure.getCode());
  }

  /** 直接提供未签名坏输入，断言生产解码器确实拒绝。 */
  private String base64(String value) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
