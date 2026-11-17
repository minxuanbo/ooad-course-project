package com.municipal.rbaccore.service.port.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 登录沿用既有token、user、expiresIn响应外形。 */
@Getter
@Setter
@AllArgsConstructor
public class LoginResult {
  private String token;
  private IdentityResult user;
  private long expiresIn;
}
