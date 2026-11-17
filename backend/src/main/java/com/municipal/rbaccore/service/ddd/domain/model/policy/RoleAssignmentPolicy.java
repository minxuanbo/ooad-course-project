package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import com.municipal.rbaccore.service.ddd.domain.model.Role;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import java.util.Set;

/** 目标保护、授予上限和完整直接权限子集是独立的授权条件。 */
public final class RoleAssignmentPolicy {
  private RoleAssignmentPolicy() {}

  /** 仅可信根的有效五级用户获得人员保护例外。 */
  public static boolean canManage(User actor, boolean global, User target) {
    return (global && actor.level() == Role.ADMIN_LEVEL)
        || (actor.level() >= Role.FIRST_MANAGER_LEVEL
            && !actor.id().equals(target.id())
            && actor.level() > target.level()
            && target.roles().stream().noneMatch(role -> role.managementLevel() == null));
  }

  /** 停用角色不可授，直接外系统权限即使停用也拒绝整个角色。 */
  public static boolean canAssign(
      Role role, int actorLevel, boolean customPermitted, Set<Long> allowedPermissionIds) {
    if (!role.enabled() || !withinLevel(role, actorLevel, customPermitted)) return false;
    for (Permission permission : role.permissions()) {
      if (!permission.system().isRbac()) return false;
      if (AuthorizationPolicy.activeRbac(permission)
          && !allowedPermissionIds.contains(permission.id())) {
        return false;
      }
    }
    return true;
  }

  /** 自定义与固定级别角色都由明确元数据判定。 */
  private static boolean withinLevel(Role role, int actorLevel, boolean customPermitted) {
    return role.managementLevel() == null ? customPermitted : role.managementLevel() <= actorLevel;
  }
}
