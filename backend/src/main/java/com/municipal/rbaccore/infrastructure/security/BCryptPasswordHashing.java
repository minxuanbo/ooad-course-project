package com.municipal.rbaccore.infrastructure.security;

import com.municipal.rbaccore.service.port.security.PasswordHashing;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Spring Security加密适配器，业务层只依赖口令哈希端口。 */
@Component
@RequiredArgsConstructor
public class BCryptPasswordHashing implements PasswordHashing {
  private final PasswordEncoder encoder;

  /** 使用现有强度和逐次随机盐。 */
  public String encode(CharSequence rawPassword) {
    return encoder.encode(rawPassword);
  }

  /** 非法输入和历史哈希统一为失败，不向调用方泄漏底层格式。 */
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    try {
      return encoder.matches(rawPassword, encodedPassword);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
