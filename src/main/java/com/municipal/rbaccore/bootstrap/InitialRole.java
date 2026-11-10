package com.municipal.rbaccore.bootstrap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 用户确认的五个共享角色，不按系统或部门复制角色定义。 */
@Getter
@RequiredArgsConstructor
public enum InitialRole {
  SYSTEM_ADMIN("system-admin", "系统管理员"),
  COMPANY_LEADER("company-leader", "公司领导"),
  DEPARTMENT_MANAGER("department-manager", "部门经理"),
  PROJECT_MANAGER("project-manager", "项目经理"),
  EMPLOYEE("employee", "普通员工");

  private final String code;
  private final String name;
}
