package com.municipal.rbaccore.service.port.department;

/** HTTP与用例共用的组织类型取值，层级规则由各业务实现独立校验。 */
public enum DepartmentType {
  HEADQUARTERS,
  COMPANY,
  DEPARTMENT,
  PROJECT
}
