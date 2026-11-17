package com.municipal.rbaccore.service.port.role;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 已保存的直接权限，包括供清理的停用项，不补父级或子节点。 */
@Getter
@Setter
@AllArgsConstructor
public class RolePermissionsResult {
  private Long roleId;
  private List<Long> ids;
}
