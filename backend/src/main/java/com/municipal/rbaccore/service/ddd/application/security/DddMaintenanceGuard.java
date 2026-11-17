package com.municipal.rbaccore.service.ddd.application.security;

import com.municipal.rbaccore.service.ddd.domain.model.ApiResource;
import com.municipal.rbaccore.service.ddd.domain.model.Department;
import com.municipal.rbaccore.service.ddd.domain.model.policy.MaintenancePolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.AuthorizationRepository;
import com.municipal.rbaccore.service.ddd.domain.repository.OrganizationRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.MaintenanceProtection;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 使用共享可信根行串行化授权修改，独立使用领域规则判断完整维护者。 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DddMaintenanceGuard implements MaintenanceProtection {
  private static final Set<ApiResource.Route> REQUIRED =
      RbacCatalog.maintenanceRoutes().stream()
          .map(route -> new ApiResource.Route(route.method(), route.path()))
          .collect(Collectors.toUnmodifiableSet());

  private final OrganizationRepository organization;
  private final AuthorizationRepository authorization;

  /** 锁前只查询根编号，不在持久化上下文保留权限或组织快照。 */
  @Override
  public void lock() {
    organization.lock(root()).orElseThrow(BusinessException::scopeDenied);
  }

  /** 在同一事务读取保存后的事实并拒绝无人独立覆盖K的变更。 */
  @Override
  public void retainOne() {
    Department.Key root = root();
    if (authorization.rootAccounts(root).stream()
        .noneMatch(facts -> MaintenancePolicy.qualifies(facts, root.value(), REQUIRED))) {
      throw new BusinessException(409, "LAST_ADMIN_REQUIRED", "必须保留至少一名具备完整维护权限的有效管理员");
    }
  }

  /** 根编号由来源记录决定。 */
  private Department.Key root() {
    return organization.trustedRoot().orElseThrow(BusinessException::scopeDenied);
  }
}
