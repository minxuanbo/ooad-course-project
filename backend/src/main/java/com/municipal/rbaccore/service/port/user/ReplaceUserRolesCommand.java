package com.municipal.rbaccore.service.port.user;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 人员角色的完整替换集合，空数组表示清空全部关联。 */
@Getter
@Setter
public class ReplaceUserRolesCommand {
  private List<Long> ids;
}
