package com.municipal.rbaccore.infrastructure.persistence.mapping;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.service.port.role.RoleResult;

/** 角色持久化事实到传输结果的技术映射，不执行授权决策。 */
public final class RoleResultMapping {
  private RoleResultMapping() {}

  /** 同名同类型交由真实处理器，关系标识和名称显式展开。 */
  public static RoleResult result(RoleEntity role) {
    RoleResult result = CloneFactory.copy(new RoleResult(), role);
    result.setEnabled(role.isEnabled());
    if (role.getOwnerDepartment() != null) {
      result.setOwnerDepartmentId(role.getOwnerDepartment().getId());
      result.setOwnerDepartmentName(role.getOwnerDepartment().getName());
    }
    if (role.getApplication() != null) {
      result.setApplicationId(role.getApplication().getId());
      result.setApplicationName(role.getApplication().getName());
    }
    return result;
  }
}
