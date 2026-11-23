package com.municipal.rbaccore.service.port.role;

/** 角色来源与适用范围元数据，不按名称推断身份或提升管理级别。 */
public enum RoleType {
  SYSTEM_BUILTIN,
  APPLICATION_PRESET,
  DEPARTMENT_CUSTOM,
  GLOBAL_CUSTOM
}
