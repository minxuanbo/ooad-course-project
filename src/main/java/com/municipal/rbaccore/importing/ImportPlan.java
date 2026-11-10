package com.municipal.rbaccore.importing;

import java.util.List;

/** 保存只读校验结果；来源行与目标归属分开，保留合并前的组织和人员资料。 */
public record ImportPlan(List<Organization> organizations, List<Employee> employees,
    String organizationSha256, String employeeSha256) {

  /** 一个原始组织节点，可与其他来源节点映射到同一个目标部门。 */
  public record Organization(int row, String name, String manager, String parent,
      String targetName, String targetCode) {
  }

  /** 员工的六个原始字段及解析后的目标部门，不包含口令或授权信息。 */
  public record Employee(int row, String name, String username, String company,
      String department, String position, String phone, String targetDepartment) {
  }
}
