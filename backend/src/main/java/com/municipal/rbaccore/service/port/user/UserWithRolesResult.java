package com.municipal.rbaccore.service.port.user;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 已保存角色集合的安全表示，roleIds为整体替换后的完整关联。 */
@Getter
@Setter
@NoArgsConstructor
public class UserWithRolesResult {
  private Long id;
  private String username;
  private String name;
  private Long departmentId;
  private Boolean enabled;
  private List<Long> roleIds;
}
