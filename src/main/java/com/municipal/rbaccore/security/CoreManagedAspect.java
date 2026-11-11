package com.municipal.rbaccore.security;

import com.municipal.rbac.ApiException;
import com.municipal.rbac.AuditEvent;
import com.municipal.rbac.AuditWriter;
import com.municipal.rbac.RequestAuditFilter;
import com.municipal.rbaccore.service.CoreAuthService;
import com.municipal.rbaccore.service.EffectivePermissionService;
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
public class CoreManagedAspect {
  private final CoreAuthService auth;
  private final EffectivePermissionService permissions;
  private final AuditWriter audit;

  /** 记录一次最终授权结果；匿名拒绝交原过滤器记录，已识别请求由本切面去重。 */
  @Around(
      "@within(com.municipal.rbaccore.security.CoreManaged) ||"
          + " @annotation(com.municipal.rbaccore.security.CoreManaged)")
  public Object authorize(ProceedingJoinPoint point) throws Throwable {
    HttpServletRequest request = RequestAuditFilter.current();
    if (request == null) {
      throw ApiException.unauthorized();
    }
    CurrentIdentity actor = auth.require(request.getHeader("Authorization"));
    String template =
        String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    int status = 200;
    String reason = "OK";
    try {
      if (!permissions.allows(actor.id(), request.getMethod(), template)) {
        throw ApiException.forbidden();
      }
      Object result = point.proceed();
      if (result instanceof ResponseEntity<?> response) {
        status = response.getStatusCode().value();
      }
      return result;
    } catch (ApiException exception) {
      status = exception.getStatus().value();
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
              status < 400,
              RequestAuditFilter.requestId(),
              reason,
              request.getMethod(),
              template,
              status));
    }
  }
}
