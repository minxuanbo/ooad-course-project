package com.municipal.rbaccore.api.identity.vo;

/** 统一身份人员最小目录，不返回联系方式或角色。 */
public record ApplicationDepartmentUserVO(
    String userId, String no, String name, Long departmentId, String departmentName) {}
