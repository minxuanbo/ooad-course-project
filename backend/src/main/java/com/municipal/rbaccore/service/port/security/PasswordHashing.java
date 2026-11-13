package com.municipal.rbaccore.service.port.security;

/** 本RBAC本地口令哈希支撑，隔离Spring Security类型。 */
public interface PasswordHashing {

  /** 每次编码生成独立盐。 */
  String encode(CharSequence rawPassword);

  /** 校验口令，非法哈希失败关闭。 */
  boolean matches(CharSequence rawPassword, String encodedPassword);
}
