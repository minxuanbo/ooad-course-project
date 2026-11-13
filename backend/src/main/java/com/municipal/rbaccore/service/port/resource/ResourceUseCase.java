package com.municipal.rbaccore.service.port.resource;

/** 本系统目录、树及API映射的共享用例。 */
public interface ResourceUseCase {

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
