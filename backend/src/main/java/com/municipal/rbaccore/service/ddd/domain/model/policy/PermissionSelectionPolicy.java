package com.municipal.rbaccore.service.ddd.domain.model.policy;

import com.municipal.rbaccore.service.ddd.domain.model.DomainRuleViolation;
import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import com.municipal.rbaccore.service.ddd.domain.model.Role;
import java.util.Set;

/** 检查共享角色完整选权，不静默删除外系统的已有直接权限。 */
public final class PermissionSelectionPolicy {
  private PermissionSelectionPolicy() {}

  /** 未找到、非启用或非本系统权限使整次替换失败。 */
  public static Role replace(Role role, Set<Permission> selected, int requested) {
    if (role.permissions().stream().anyMatch(permission -> !permission.system().isRbac())) {
      throw invalid("角色包含当前范围之外的授权，请先复核所属系统");
    }
    if (selected.size() != requested
        || !selected.stream().allMatch(AuthorizationPolicy::activeRbac)) {
      throw invalid("只能保存本权限系统的启用权限");
    }
    return role.replacePermissions(selected);
  }

  /** 保留可稳定翻译的领域规则原因。 */
  private static DomainRuleViolation invalid(String message) {
    return new DomainRuleViolation(DomainRuleViolation.Rule.INVALID_PERMISSION, message);
  }
}
