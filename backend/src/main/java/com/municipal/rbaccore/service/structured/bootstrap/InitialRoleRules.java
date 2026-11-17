package com.municipal.rbaccore.service.structured.bootstrap;

import com.municipal.rbaccore.service.port.bootstrap.InitialRole;
import com.municipal.rbaccore.service.port.importing.ImportPlan;

/** 只用于用户已确认的首次授权规则，不参与运行时权限判定。 */
public final class InitialRoleRules {
  private InitialRoleRules() {}

  /** 依据原始组织归属及精确岗位确定初始角色，不把全部部门负责人视为管理员。 */
  public static InitialRole classify(ImportPlan.Employee employee) {
    String company = employee.company().trim();
    String department = employee.department().trim();
    String position = employee.position().trim();
    if (company.equals(department) || (company.equals("总公司") && department.equals("公司领导"))) {
      return InitialRole.COMPANY_LEADER;
    }
    return switch (position) {
      case "部长", "副部长" -> InitialRole.DEPARTMENT_MANAGER;
      case "项目经理", "项目副经理" -> InitialRole.PROJECT_MANAGER;
      default -> InitialRole.EMPLOYEE;
    };
  }
}
