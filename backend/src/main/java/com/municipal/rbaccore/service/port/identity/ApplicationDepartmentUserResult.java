package com.municipal.rbaccore.service.port.identity;

/** 外部部门人员不含手机号、口令、内部角色或管理能力。 */
public record ApplicationDepartmentUserResult(
    String userId, String no, String name, Long departmentId, String departmentName) {}
