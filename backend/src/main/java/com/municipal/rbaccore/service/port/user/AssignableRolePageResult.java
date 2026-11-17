package com.municipal.rbaccore.service.port.user;

import com.municipal.rbaccore.service.port.role.RoleResult;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 相同筛选条件下的候选角色页内容及完整总数。 */
@Getter
@Setter
@AllArgsConstructor
public class AssignableRolePageResult {
  private List<RoleResult> items;
  private long total;
  private int page;
  private int size;
}
