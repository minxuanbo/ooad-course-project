package com.municipal.rbaccore.service.structured.security;

import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import com.municipal.rbaccore.service.port.security.RequestContext;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 全局管理同时检查最新实际API和可信共同根，写操作拿根锁后再次调用。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredGlobalManagementAccess implements ManagementAccessUseCase {
  private final StructuredAuthenticationService auth;
  private final RequestContext requestContext;
  private final StructuredEffectivePermissionService permissions;
  private final StructuredDepartmentScopeService scope;
  private final StructuredMaintenanceGuard maintenance;

  /** 使用Spring服务端模板重新鉴权，不能继续使用等待根锁前的旧授权结果。 */
  public void require(String header) {
    CurrentIdentity actor = auth.require(header);
    RequestContext.Operation operation = requestContext.operation();
    if (operation == null) throw BusinessException.unauthorized();
    String template = operation.template();
    if (!permissions.allows(actor.id(), operation.method(), template))
      throw BusinessException.forbidden();
    if (!scope.isGlobal(actor)) throw BusinessException.scopeDenied();
  }

  /** 在调用方写事务中统一加锁、重鉴权、修改和完整维护能力检查的顺序。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> T write(String header, Supplier<T> change) {
    maintenance.lock();
    require(header);
    T result = change.get();
    maintenance.retainOne();
    return result;
  }
}
