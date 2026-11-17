package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.List;
import java.util.Set;

/** 当前事务内的可信根来源和有效部门关系。 */
public record OrganizationFacts(Long trustedRootId, List<Department> departments) {
  /** 限定人员查询使用的部门集合。 */
  public record Scope(Set<Long> departmentIds) {}

  /** 组织统计按直接归属汇总，不把同一个人计入多个直接部门。 */
  public record Count(Long departmentId, long directUsers) {}

  /** 部门删除前核验的事实。 */
  public record Usage(boolean hasUsers, boolean hasChildren) {}

  /** 按范围获取的可见统计。 */
  public record Statistics(long total, long enabled, List<Count> directCounts) {}
}
