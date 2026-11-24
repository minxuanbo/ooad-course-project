package com.municipal.rbaccore.service.port.resource;

/** 本系统目录、树及API映射的共享用例。 */
public interface ResourceUseCase {
  /** 应用管理列表使用同筛选分页，与下拉目录分别授权。 */
  SystemPageResult searchSystems(String header, SystemQuery query);

  /** 登记待接入应用，不自动创建业务权限或授予角色。 */
  SystemResult createSystem(String header, CreateSystemCommand command);

  /** 修改应用名称、启停和接入状态，保留稳定编码及关系。 */
  SystemResult patchSystem(String header, Long id, UpdateSystemCommand command);

  /** 逻辑移除受管应用，保留历史与配置供审核或恢复。 */
  SystemResult removeSystem(String header, Long id);

  /** 返回本系统目录。 */
  java.util.List<SystemResult> systems(String header);

  /** 返回同系统权限树。 */
  java.util.List<ResourcePermissionResult> tree(String header, Long systemId);

  /** 按方法/路径分页查询API。 */
  ApiPageResult apis(String header, ApiQuery query);

  /** 返回完整已保存映射。 */
  PermissionApisResult mapping(String header, Long permissionId);

  /** 提前检查直接权限目标范围。 */
  void checkPermissionTarget(String header, Long id);

  /** 提前检查直接API目标范围。 */
  void checkApiTarget(String header, Long id);

  /** 提前检查系统范围。 */
  void checkSystemTarget(String header, Long id);

  /** 整体替换同系统API映射。 */
  PermissionApisResult replaceMapping(String header, Long id, ReplacePermissionApisCommand command);

  /** 更新允许字段，保留parentId三态。 */
  ResourcePermissionResult patchPermission(String header, Long id, UpdatePermissionCommand command);

  /** 更新API启用状态。 */
  ApiResult patchApi(String header, Long id, UpdateApiCommand command);
}
