package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.Set;

/** 人员账号与有效的管理级别，同级保护也能读取当前持有的停用角色。 */
public record User(
    Long id,
    String username,
    String passwordHash,
    String name,
    String phone,
    String position,
    Department department,
    boolean enabled,
    Set<Role> roles) {
  /** 将持久化账号身份与命令中的编号区分开。 */
  public record Key(Long value) {}

  /** 双入口使用同一检索文本，匹配结果可能为空或歧义。 */
  public record LoginIdentity(String value) {}

  /** 读取角色快照，避免从结果对象绕过整体替换。 */
  public User {
    roles = Set.copyOf(roles);
  }

  /** 只有启用的固定基础角色贡献级别。 */
  public int level() {
    return roles.stream()
        .filter(Role::enabled)
        .map(Role::managementLevel)
        .filter(java.util.Objects::nonNull)
        .mapToInt(Integer::intValue)
        .max()
        .orElse(Role.DEFAULT_LEVEL);
  }

  /** 将整组角色作为一次聚合更新。 */
  public User replaceRoles(Set<Role> selected) {
    return new User(
        id, username, passwordHash, name, phone, position, department, enabled, selected);
  }

  /** 停用账号，保留已授角色和来源信息。 */
  public User disable() {
    return new User(id, username, passwordHash, name, phone, position, department, false, roles);
  }

  /** 人员调动仅更新部门，职位和授权不按来源规则重算。 */
  public User transfer(Department target) {
    return new User(id, username, passwordHash, name, phone, position, target, enabled, roles);
  }
}
