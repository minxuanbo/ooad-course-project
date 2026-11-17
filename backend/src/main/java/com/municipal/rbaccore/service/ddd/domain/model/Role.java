package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.Set;

/** 共享角色聚合，直接权限集合保留停用项及外系统事实供授予保护判断。 */
public record Role(
    Long id,
    String code,
    String name,
    Integer managementLevel,
    boolean enabled,
    Set<Permission> permissions) {
  public static final int DEFAULT_LEVEL = 1;
  public static final int FIRST_MANAGER_LEVEL = 2;
  public static final int ADMIN_LEVEL = 5;

  /** 明确的角色查找键。 */
  public record Key(Long value) {}

  /** 批量解析请求选择的角色。 */
  public record Selection(Set<Long> ids) {}

  /** 批量编辑的权限集合不能从构造者处变更。 */
  public Role {
    permissions = Set.copyOf(permissions);
  }

  /** 自定义角色总是以空权限开始，无固定管理等级。 */
  public static Role custom(String code, String name, Boolean enabled) {
    return new Role(null, code.trim(), name.trim(), null, !Boolean.FALSE.equals(enabled), Set.of());
  }

  /** 修改可变元数据，身份、编码及固定级别沿用原值。 */
  public Role revise(String name, Boolean enabled) {
    return new Role(
        id,
        code,
        name == null ? this.name : name.trim(),
        managementLevel,
        enabled == null ? this.enabled : enabled,
        permissions);
  }

  /** 一次整体替换已核实的直接权限。 */
  public Role replacePermissions(Set<Permission> selected) {
    return new Role(id, code, name, managementLevel, enabled, selected);
  }
}
