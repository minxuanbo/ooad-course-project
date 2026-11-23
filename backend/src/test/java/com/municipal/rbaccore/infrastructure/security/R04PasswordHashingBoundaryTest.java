package com.municipal.rbaccore.infrastructure.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** R04加密适配器在非法口令输入时统一失败，不向业务层暴露第三方格式异常。 */
class R04PasswordHashingBoundaryTest {
  /** BCrypt会拒绝null明文，适配器应将其收敛为匹配失败。 */
  @Test
  @DisplayName("R04 BCrypt非法明文输入失败关闭且合法匹配仍使用真实摘要")
  void R04_illegalRawPasswordFailsClosed() {
    BCryptPasswordHashing hashing = new BCryptPasswordHashing(new BCryptPasswordEncoder(4));
    String encoded = hashing.encode("test-password");
    assertFalse(hashing.matches(null, encoded));
    assertTrue(hashing.matches("test-password", encoded));
    assertFalse(hashing.matches("other-password", encoded));
  }
}
