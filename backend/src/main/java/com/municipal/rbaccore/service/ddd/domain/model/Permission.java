package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.Set;

/** 权限聚合保存直属父节点及直接映射，祖先关系不隐式授予权限。 */
public record Permission(
    Long id,
    ApplicationSystem system,
    Long parentId,
    String code,
    String name,
    Type type,
    boolean enabled,
    Set<ApiResource> apis) {
  /** 本系统定义菜单、按钮及必须映射真实API的权限类型。 */
  public enum Type {
    MENU,
    BUTTON,
    API
  }

  /** 持久化身份与协议字段分离。 */
  public record Key(Long value) {}

  /** 批量解析权限，保留所有停用项目供写入校验。 */
  public record Selection(Set<Long> ids) {}

  /** 保留直接映射事实，对外暴露只读集合。 */
  public Permission {
    apis = Set.copyOf(apis);
  }

  /** 显式替换直接映射，输入由映射策略校验。 */
  public Permission replaceApis(Set<ApiResource> selected) {
    return new Permission(id, system, parentId, code, name, type, enabled, selected);
  }

  /** 更新树位置、显示名称、类型及状态，同时保留权限身份和映射。 */
  public Permission revise(Long parent, String newName, Type newType, Boolean active) {
    return new Permission(
        id,
        system,
        parent,
        code,
        newName == null ? name : newName,
        newType == null ? type : newType,
        active == null ? enabled : active,
        apis);
  }
}
