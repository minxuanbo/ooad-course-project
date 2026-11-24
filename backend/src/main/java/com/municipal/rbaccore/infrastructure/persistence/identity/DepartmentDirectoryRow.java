package com.municipal.rbaccore.infrastructure.persistence.identity;

/** Spring Data目录投影，只含组织标识、名称和直接子节点数。 */
public record DepartmentDirectoryRow(
    Long id, String code, String name, Long parentId, Long children) {}
