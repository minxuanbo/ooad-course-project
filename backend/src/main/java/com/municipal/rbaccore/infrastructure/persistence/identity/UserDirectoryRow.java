package com.municipal.rbaccore.infrastructure.persistence.identity;

/** 外部目录的最小人员投影，不查询手机号、口令及角色集合。 */
public record UserDirectoryRow(
    Long id, String username, String name, Long departmentId, String departmentName) {}
