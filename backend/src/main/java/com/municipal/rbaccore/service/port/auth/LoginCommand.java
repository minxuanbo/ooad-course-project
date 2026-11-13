package com.municipal.rbaccore.service.port.auth;

import lombok.Getter;
import lombok.Setter;

/** 登录用例输入，不生成含口令的toString。 */
@Getter
@Setter
public class LoginCommand {
  private String account;
  private String password;
}
