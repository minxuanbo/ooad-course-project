package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.List;

/** 导入核验所需的现存组织、用户、来源及数据库排序规则事实。 */
public record InitializationState(
    boolean binaryIdentifiers,
    List<Department> departments,
    List<Account> accounts,
    List<Source> sources) {
  /** 按来源类型和原始唯一键索引。 */
  public enum Kind {
    ORGANIZATION,
    EMPLOYEE,
    BOOTSTRAP
  }

  /** 不携带口令哈希的核验事实；roleCount确认首次分配前无遗留关联。 */
  public record Account(
      Long id,
      String username,
      String phone,
      Long departmentId,
      String departmentCode,
      long roleCount) {}

  /** 来源字段、摘要、行号以及实际所连目标。 */
  public record Source(
      Kind kind,
      String key,
      String hash,
      Integer row,
      List<String> cells,
      Long departmentId,
      Long userId) {}
}
