package com.municipal.rbaccore.service.port.user;

/** 人员角色、停用和调动的共享事务用例。 */
public interface UserManagementUseCase {

  /** 读取完整已保存角色。 */
  UserRolesResult roles(String header, Long id);

  /** 按当前有效Permission子集分页读取候选。 */
  AssignableRolePageResult assignableRoles(String header, Long id, AssignableRoleQuery query);

  /** 整体替换角色，失败全回滚。 */
  UserWithRolesResult replaceRoles(String header, Long id, ReplaceUserRolesCommand command);

  /** 停用账号并立即影响后续管理请求。 */
  UserWithRolesResult disable(String header, Long id);

  /** 校验原/目标双范围并调动部门。 */
  UserWithRolesResult move(String header, Long id, TransferUserCommand command);

  /** 在参数解析前检查已定位目标，写入阶段仍须重验。 */
  void checkTarget(String header, Long id, boolean protectSelf);
}
