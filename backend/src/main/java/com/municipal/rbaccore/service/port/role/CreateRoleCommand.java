package com.municipal.rbaccore.service.port.role;

import lombok.Getter;
import lombok.Setter;

/** 创建后编码不可变，新角色默认启用且没有权限。 */
@Getter
@Setter
public class CreateRoleCommand {
  private String code;
  private String name;
}
