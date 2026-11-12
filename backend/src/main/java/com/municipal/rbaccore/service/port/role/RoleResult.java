package com.municipal.rbaccore.service.port.role;

import lombok.Getter;
import lombok.Setter;

/** 角色定义的安全表示，primitive状态从实体显式转换。 */
@Getter
@Setter
public class RoleResult {
  private Long id;
  private String code;
  private String name;
  private Boolean enabled;
}
