package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.Set;

/** 提供完整的角色、权限、API链路事实，包含未启用及外系统项。 */
public record AuthorizationFacts(User user) {
  /** 固定授权候选查询条件，在数据库中先过滤再分页。 */
  public record AssignableRoleCriteria(
      int level,
      boolean customPermitted,
      Set<Long> allowedPermissionIds,
      String q,
      int page,
      int size,
      Set<String> systemCodes) {
    /** 未扩展系统目录的调用维持原RBAC语义。 */
    public AssignableRoleCriteria(
        int level,
        boolean customPermitted,
        Set<Long> allowedPermissionIds,
        String q,
        int page,
        int size) {
      this(
          level,
          customPermitted,
          allowedPermissionIds,
          q,
          page,
          size,
          Set.of(ApplicationSystem.CODE_RBAC));
    }

    /** 条件集合不可变，避免查询期间改变授权边界。 */
    public AssignableRoleCriteria {
      allowedPermissionIds = Set.copyOf(allowedPermissionIds);
      systemCodes = Set.copyOf(systemCodes);
    }
  }
}
