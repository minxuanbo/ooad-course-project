package com.municipal.rbaccore.service.port.resource;

/** 菜单/按钮/API类型是共享业务值，不依赖JPA实体的内嵌枚举。 */
public enum PermissionType {
  MENU,
  BUTTON,
  API
}
