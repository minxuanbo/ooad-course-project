package com.municipal.rbaccore.service.ddd.domain.model;

/** 部门聚合使用稳定编码及来源墓碑，形成可见组织子树。 */
public record Department(
    Long id, String code, String name, Long parentId, Type type, boolean deleted) {
  /** 组织类型与应用参数隔离，层级约束由领域策略执行。 */
  public enum Type {
    HEADQUARTERS,
    COMPANY,
    DEPARTMENT,
    PROJECT
  }

  /** 部门数据库身份。 */
  public record Key(Long value) {}

  /** 建立已核验的下级节点。 */
  public static Department child(String code, String name, Long parent, Type type) {
    return new Department(null, code, name.trim(), parent, type, false);
  }

  /** 改名不更换编码、类型或父节点。 */
  public Department rename(String name) {
    return new Department(id, code, name.trim(), parentId, type, deleted);
  }

  /** 删除源节点时保留来源外键和原始编码。 */
  public Department tombstone() {
    return new Department(id, code, name, parentId, type, true);
  }
}
