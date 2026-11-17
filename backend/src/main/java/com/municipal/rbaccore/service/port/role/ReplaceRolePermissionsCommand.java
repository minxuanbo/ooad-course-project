package com.municipal.rbaccore.service.port.role;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 角色直接权限的完整替换集合。 */
@Getter
@Setter
public class ReplaceRolePermissionsCommand {
  private List<Long> ids;
}
