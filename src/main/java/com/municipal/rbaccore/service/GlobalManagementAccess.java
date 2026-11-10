package com.municipal.rbaccore.service;

import com.municipal.rbac.ApiException;
import com.municipal.rbac.RequestAuditFilter;
import com.municipal.rbaccore.security.CurrentIdentity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerMapping;

/** 全局管理同时检查最新实际API和可信共同根，写操作拿根锁后再次调用。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalManagementAccess {
  private final CoreAuthService auth;
  private final EffectivePermissionService permissions;
  private final DepartmentScopeService scope;

  /** 使用Spring服务端模板重新鉴权，不能继续使用等待根锁前的旧授权结果。 */
  public void require(String header) {
    CurrentIdentity actor = auth.require(header);
    HttpServletRequest request = RequestAuditFilter.current();
    if (request == null) throw ApiException.unauthorized();
    String template =
        String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    if (!permissions.allows(actor.id(), request.getMethod(), template))
      throw ApiException.forbidden();
    if (!scope.isGlobal(actor)) throw ApiException.scopeDenied();
  }
}
