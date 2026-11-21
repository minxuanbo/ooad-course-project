package com.municipal.rbaccore.service.port.identity;

/** 最小组织目录投影，hasChildren仅表达未删除的直接下级。 */
public record ApplicationDepartmentResult(
    Long id, String code, String name, Long parentId, boolean hasChildren) {}
