package com.municipal.rbaccore.infrastructure.security;

import com.municipal.rbaccore.infrastructure.web.RequestAuditFilter;
import com.municipal.rbaccore.service.port.audit.AuditEvent;
import com.municipal.rbaccore.service.port.audit.AuditWriter;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.ManagementAuthorization;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

/** 真实Controller的AOP门禁，匹配服务端HTTP方法及Spring模板，不接受前端授权结论。 */
@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class ManagedAspect {
  private final AuthenticationUseCase auth;
  private final ManagementAuthorization permissions;
  private final AuditWriter audit;

  /** 记录一次最终授权结果；匿名拒绝交原过滤器记录，已识别请求由本切面去重。 */
  @Around(
      "@within(com.municipal.rbaccore.service.port.security.Managed) ||"
          + " @annotation(com.municipal.rbaccore.service.port.security.Managed)")
  public Object authorize(ProceedingJoinPoint point) throws Throwable {
    HttpServletRequest request = RequestAuditFilter.current();
    if (request == null) {
      throw BusinessException.unauthorized();
    }
    CurrentIdentity actor = auth.require(request.getHeader("Authorization"));
    String template =
        String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    int status = 200;
    boolean allowed = false;
    String reason = "OK";
    try {
      if (!permissions.allows(actor.id(), request.getMethod(), template)) {
        throw BusinessException.forbidden();
      }
      allowed = true;
      Object result = point.proceed();
      if (result instanceof ResponseEntity<?> response) {
        status = response.getStatusCode().value();
        allowed = status != 401 && status != 403;
      }
      return result;
    } catch (BusinessException exception) {
      status = exception.getStatus();
      if (status == 401 || status == 403) {
        allowed = false;
      }
      reason = exception.getCode();
      throw exception;
    } catch (Throwable exception) {
      status = 500;
      reason = "INTERNAL_ERROR";
      throw exception;
    } finally {
      RequestAuditFilter.recorded();
      audit.record(
          new AuditEvent(
              actor.id(),
              actor.username(),
              "AUTHORIZATION",
              allowed,
              RequestAuditFilter.requestId(),
              reason,
              request.getMethod(),
              template,
              status));
    }
  }
}
