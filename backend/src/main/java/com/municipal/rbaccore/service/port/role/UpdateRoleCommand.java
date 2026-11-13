package com.municipal.rbaccore.service.port.role;

import lombok.Getter;
import lombok.Setter;

/** null只表示该字段缺省，输入中的显式null已在API拒绝。 */
@Getter
@Setter
public class UpdateRoleCommand {
  private String name;
  private Boolean enabled;
}
