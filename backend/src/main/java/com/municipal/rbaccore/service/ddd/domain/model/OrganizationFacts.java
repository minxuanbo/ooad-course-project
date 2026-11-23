package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.List;
import java.util.Set;

/** 当前事务内的可信根来源和有效部门关系。 */
public record OrganizationFacts(
    Long trustedRootId, List<Department> departments, Set<Long> roleOwnerIds) {
  /** 不涉及部门角色的旧事实入口保持空占用集合。 */
  public OrganizationFacts(Long trustedRootId, List<Department> departments) {
    this(trustedRootId, departments, Set.of());
  }

  /** 占用事实不可由调用方在读模型构建途中改变。 */
  public OrganizationFacts {
    roleOwnerIds = Set.copyOf(roleOwnerIds);
  }

  /** 限定人员查询使用的部门集合。 */
  public record Scope(Set<Long> departmentIds) {}

  /** 组织统计按直接归属汇总，不把同一个人计入多个直接部门。 */
  public record Count(Long departmentId, long directUsers) {}

  /** 部门删除前核验的事实。 */
  public record Usage(boolean hasUsers, boolean hasChildren, boolean hasRoles) {
    /** 原有组织使用事实不含角色归属。 */
    public Usage(boolean hasUsers, boolean hasChildren) {
      this(hasUsers, hasChildren, false);
    }
  }

  /** 按范围获取的可见统计。 */
  public record Statistics(long total, long enabled, List<Count> directCounts) {}
}
