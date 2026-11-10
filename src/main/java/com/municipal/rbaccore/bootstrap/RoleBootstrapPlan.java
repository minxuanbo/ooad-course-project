package com.municipal.rbaccore.bootstrap;

import com.municipal.rbaccore.importing.ImportPlan;
import java.util.List;
import java.util.Map;

/** 可核对的首次授权清单；独立管理员从T03来源映射取得，不从员工岗位猜测。 */
public record RoleBootstrapPlan(
    ImportPlan source,
    List<Assignment> assignments,
    Map<String, Long> plannedCounts,
    String manifestSha256) {

  /** 员工以工号和来源行定位，角色取用户明确确认的规则。 */
  public record Assignment(String username, int sourceRow, InitialRole role) {}
}
