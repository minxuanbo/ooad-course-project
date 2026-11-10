package com.municipal.rbaccore.service;

import com.municipal.rbac.ApiException;
import com.municipal.rbaccore.model.User;
import com.municipal.rbaccore.repository.DepartmentRepository;
import com.municipal.rbaccore.repository.UserRepository;
import com.municipal.rbaccore.security.RbacCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 后续授权写票共用根锁及完整K判断，不按系统管理员角色名计数。 */
@Service
@RequiredArgsConstructor
public class MaintenanceGuard {
  private final DepartmentScopeService scope;
  private final DepartmentRepository departments;
  private final UserRepository users;
  private final EffectivePermissionService permissions;

  /** 必须在调用方写事务中先锁同一个已核实根，避免各票使用不同锁点。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lock() {
    departments.lockById(scope.trustedRootId()).orElseThrow(ApiException::scopeDenied);
  }

  /** 按修改后完整状态至少保留一名独立具备全部K的根用户，不能拼不同人员权限。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void retainOne() {
    for (User user : users.findByDepartmentIdAndEnabledTrue(scope.trustedRootId())) {
      if (permissions.routes(user.getId()).containsAll(RbacCatalog.maintenanceRoutes())) {
        return;
      }
    }
    throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED", "必须保留至少一名具备完整维护权限的有效管理员");
  }
}
