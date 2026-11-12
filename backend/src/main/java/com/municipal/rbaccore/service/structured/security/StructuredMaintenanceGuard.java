package com.municipal.rbaccore.service.structured.security;

import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.MaintenanceProtection;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 后续授权写票共用根锁及完整K判断，不按系统管理员角色名计数。 */
@Service
@RequiredArgsConstructor
public class StructuredMaintenanceGuard implements MaintenanceProtection {
  private final StructuredDepartmentScopeService scope;
  private final DepartmentJpaRepository departments;
  private final UserJpaRepository users;
  private final StructuredEffectivePermissionService permissions;

  /** 必须在调用方写事务中先锁同一个已核实根，避免各票使用不同锁点。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock() {
    departments.lockById(scope.trustedRootId()).orElseThrow(BusinessException::scopeDenied);
  }

  /** 按修改后完整状态至少保留一名独立具备全部K的根用户，不能拼不同人员权限。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void retainOne() {
    for (UserEntity user : users.findByDepartmentIdAndEnabledTrue(scope.trustedRootId())) {
      if (permissions.routes(user.getId()).containsAll(RbacCatalog.maintenanceRoutes())) {
        return;
      }
    }
    throw new BusinessException(409, "LAST_ADMIN_REQUIRED", "必须保留至少一名具备完整维护权限的有效管理员");
  }
}
