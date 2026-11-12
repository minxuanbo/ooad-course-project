package com.municipal.rbaccore.service.port.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 当前身份只返回契约中的安全字段。 */
@Getter
@Setter
@NoArgsConstructor
public class IdentityResult {
  private Long id;
  private String username;
  private String name;
  private Long departmentId;
  private Boolean enabled;
}
