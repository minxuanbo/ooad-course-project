package com.municipal.rbaccore.service.port.user;

import com.municipal.rbaccore.service.port.role.RoleResult;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 当前人员与完整角色元数据，角色可包含停用或现已不可分配项。 */
@Getter
@Setter
@AllArgsConstructor
public class UserRolesResult {
  private UserWithRolesResult user;
  private List<RoleResult> roles;
}
