package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.List;

/** 不含密码的原件内容、已解析目标和来源摘要。 */
public record ImportManifest(
    List<Organization> organizations,
    List<Person> people,
    String organizationHash,
    String peopleHash) {
  public static final String ROOT_NAME = "总公司";
  public static final String MERGED_LEADERS = "公司领导";

  /** 组织原行的名称、负责人、父级及稳定目标。 */
  public record Organization(
      int row, String name, String leader, String parent, String targetName, String targetCode) {
    /** 保存原件三列原文。 */
    public List<String> cells() {
      return List.of(name, leader, parent);
    }
  }

  /** 人员原行的六列及解析后目标部门。 */
  public record Person(
      int row,
      String name,
      String username,
      String company,
      String department,
      String position,
      String phone,
      String target) {
    /** 保留原件列顺序和前导零。 */
    public List<String> cells() {
      return List.of(name, username, company, department, position, phone);
    }
  }
}
