package com.municipal.rbaccore.service.ddd.domain.model;

/** 搜索约束包含当前页窗口，所有可扩张列表都在数据库计算总数。 */
public final class SearchCriteria {
  private SearchCriteria() {}

  /** 按已核实的组织范围搜索人员。 */
  public record Users(
      OrganizationFacts.Scope scope, String q, Boolean enabled, Long roleId, int page, int size) {}

  /** 共享角色支持名称、编码与启停过滤。 */
  public record Roles(String q, Boolean enabled, int page, int size) {}

  /** API目录按系统、方法及模板分页。 */
  public record Apis(ApplicationSystem system, String q, int page, int size) {}
}
