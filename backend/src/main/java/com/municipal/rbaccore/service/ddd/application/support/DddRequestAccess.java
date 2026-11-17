package com.municipal.rbaccore.service.ddd.application.support;

import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.ManagementAuthorization;
import com.municipal.rbaccore.service.port.security.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 从请求上下文取得真实服务端模板，按当次身份执行管理鉴权。 */
@Component
@RequiredArgsConstructor
public class DddRequestAccess {
  private final AuthenticationUseCase authentication;
  private final ManagementAuthorization authorization;
  private final RequestContext context;

  /** 写事务拿根锁后重新调用，不保存预检的判断结果。 */
  public CurrentIdentity require(String header) {
    CurrentIdentity identity = authentication.require(header);
    RequestContext.Operation operation = context.operation();
    if (operation == null) throw BusinessException.unauthorized();
    if (!authorization.allows(identity.id(), operation.method(), operation.template())) {
      throw BusinessException.forbidden();
    }
    return identity;
  }
}
