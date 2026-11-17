package com.municipal.rbaccore.service.ddd.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 首次授权的确定性计划，领域规则由原件及共享目录数据生成。 */
public record BootstrapPlan(
    ImportManifest source, List<Assignment> employees, String manifestHash) {
  public static final String RECEIPT_KEY = "rbac-initial-roles";
  public static final String WORKSPACE_CODE = "rbac.workspace";
  public static final String WORKSPACE_NAME = "权限管理";
  public static final String SYSTEM_NAME = "RBAC权限管理系统";

  /** 分类计划用稳定基础角色级别求值。 */
  public record Assignment(String username, int sourceRow, int level) {}

  /** RBAC固定权限目录及清单追踪所用的顺序。 */
  public record PermissionSeed(
      String code, List<ApiResource.Route> routes, boolean scopedManagers) {}

  /** 角色目录数据及该角色应有的直接权限。 */
  public record RoleSeed(String code, String name, int level, Set<String> permissions) {}

  /** 校验后的整批发布数据，应用层提供事务及完成时刻。 */
  public record Publication(
      List<PermissionSeed> permissions,
      List<RoleSeed> roles,
      Map<Long, Integer> assignments,
      String manifestHash,
      Instant completedAt) {}

  /** 首次授权已有回执和目录条目的状态。 */
  public record Catalog(boolean empty, String receiptHash) {}
}
