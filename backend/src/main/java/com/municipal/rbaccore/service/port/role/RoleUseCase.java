package com.municipal.rbaccore.service.port.role;

/** 全局共享角色定义及直接选权用例。 */
public interface RoleUseCase {

  /** 按文字和状态数据库分页。 */
  RolePageResult list(String header, RoleQuery query);

  /** 返回完整已保存权限ID。 */
  RolePermissionsResult selected(String header, Long id);

  /** 创建启用的空权限角色。 */
  RoleResult create(String header, CreateRoleCommand command);

  /** 更名或启停，保留其他字段。 */
  RoleResult patch(String header, Long id, UpdateRoleCommand command);

  /** 整体替换直接权限且保护完整维护者。 */
  RolePermissionsResult replacePermissions(
      String header, Long id, ReplaceRolePermissionsCommand command);
}
