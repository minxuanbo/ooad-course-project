package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
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
            && actor.level() > target.level());
  }

  /** 停用角色不可授，直接外系统权限即使停用也拒绝整个角色。 */
  public static boolean canAssign(
      Role role, int actorLevel, boolean customPermitted, Set<Long> allowedPermissionIds) {
    return canAssign(
        role,
        actorLevel,
        customPermitted,
        allowedPermissionIds,
        Set.of(ApplicationSystem.CODE_RBAC));
  }

  /** 已登记系统仍受级别与完整计划权限子集约束，未知系统即使停用也拒绝。 */
  public static boolean canAssign(
      Role role,
      int actorLevel,
      boolean customPermitted,
      Set<Long> allowedPermissionIds,
      Set<String> systems) {
    return role.enabled()
        && canRemove(role, actorLevel, customPermitted, allowedPermissionIds, systems);
  }

  /** 删除关联仍检查级别与有效权限子集；全局及应用角色要求管理员，部门角色使用专门范围重载。 */
  public static boolean canRemove(
      Role role,
      int actorLevel,
      boolean customPermitted,
      Set<Long> allowedPermissionIds,
      Set<String> systems) {
    if (!withinLevel(role, actorLevel, customPermitted)) return false;
    for (Permission permission : role.permissions()) {
      if (!systems.contains(permission.system().code())) return false;
      if (AuthorizationPolicy.active(permission, systems)
          && !allowedPermissionIds.contains(permission.id())) {
        return false;
      }
    }
    return true;
  }

  /** 部门角色必须由所属部门或其上级领导分配，目标仍处于角色适用子树。 */
  public static boolean departmental(
      Role role, int actorLevel, Set<Long> manageableOwners, Set<Long> targetAncestors) {
    return actorLevel >= Role.FIRST_MANAGER_LEVEL
        && role.type() == Role.Type.DEPARTMENT_CUSTOM
        && role.ownerDepartment() != null
        && !role.ownerDepartment().deleted()
        && manageableOwners.contains(role.ownerDepartment().id())
        && role.applicable(targetAncestors);
  }

  /** 角色来源、当前适用范围与权限子集共同决定新增资格。 */
  public static boolean canAssign(
      Role role,
      int level,
      boolean admin,
      Set<Long> allowed,
      Set<String> systems,
      Set<Long> manageableOwners,
      Set<Long> targetAncestors) {
    return role.enabled()
        && role.applicable(targetAncestors)
        && canRemove(role, level, admin, allowed, systems, manageableOwners, targetAncestors);
  }

  /** 管理员可清理过期部门关联；局部领导只能撤回适用且在其范围内的定义。 */
  public static boolean canRemove(
      Role role,
      int level,
      boolean admin,
      Set<Long> allowed,
      Set<String> systems,
      Set<Long> manageableOwners,
      Set<Long> targetAncestors) {
    boolean custom = admin || departmental(role, level, manageableOwners, targetAncestors);
    return canRemove(role, level, custom, allowed, systems);
  }

  /** 自定义与固定级别角色都由明确元数据判定。 */
  private static boolean withinLevel(Role role, int actorLevel, boolean customPermitted) {
    return role.managementLevel() == null ? customPermitted : role.managementLevel() <= actorLevel;
  }
}
