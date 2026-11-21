package com.municipal.rbaccore.api.identity.vo;

/** 统一身份部门仅用于逐层选择，不暴露人员统计或管理按钮能力。 */
public record ApplicationDepartmentVO(
    Long id, String code, String name, Long parentId, boolean hasChildren) {}
