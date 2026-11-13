package com.municipal.rbaccore.service.port.department;

/** 组织层级类型；总公司身份仍由可信导入来源单独验证。 */
public enum DepartmentType {
  HEADQUARTERS,
  COMPANY,
  DEPARTMENT,
  PROJECT;

  /** 公司可建部门或项目，总公司另可建分公司，部门只能建项目。 */
  public boolean accepts(DepartmentType child) {
    return switch (this) {
      case HEADQUARTERS -> child == COMPANY || child == DEPARTMENT || child == PROJECT;
      case COMPANY -> child == DEPARTMENT || child == PROJECT;
      case DEPARTMENT -> child == PROJECT;
      case PROJECT -> false;
    };
  }
}
