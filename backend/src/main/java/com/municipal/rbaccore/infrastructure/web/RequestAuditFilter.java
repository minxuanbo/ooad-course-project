package com.municipal.rbaccore.infrastructure.web;

import com.municipal.rbaccore.service.port.audit.AuditEvent;
import com.municipal.rbaccore.service.port.audit.AuditWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import org.springframework.web.filter.OncePerRequestFilter;

/** 新旧链路共享请求标识与最小事件采集，不记录口令、令牌、请求体或查询串。 */
@Component
@RequiredArgsConstructor
public class RequestAuditFilter extends OncePerRequestFilter {
  private final AuditWriter recorder;

  /** 后台调用没有请求上下文，不能构造虚假HTTP身份。 */
  public static HttpServletRequest current() {
    RequestAttributes context = RequestContextHolder.getRequestAttributes();
    return context instanceof ServletRequestAttributes a ? a.getRequest() : null;
  }

  /** requestId沿用同一过滤器生成的关联标识。 */
  public static String requestId() {
    HttpServletRequest r = current();
    return r == null ? "" : String.valueOf(r.getAttribute("requestId"));
  }

  /** 异常响应只记录稳定错误码。 */
  public static void error(String code) {
    HttpServletRequest r = current();
    if (r != null) r.setAttribute("errorCode", code);
  }

  /** 已验证身份统一记录内部ID与工号，不另存手机号登录输入。 */
  public static void actor(Long id, String account) {
    HttpServletRequest r = current();
    if (r != null) {
      r.setAttribute("auditUserId", id);
      r.setAttribute("auditAccount", account);
    }
  }

  /** AOP已产生一次最终鉴权记录时，过滤器不得再记录同一请求的403。 */
  public static void recorded() {
    HttpServletRequest request = current();
    if (request != null) request.setAttribute("auditRecorded", true);
  }

  /** 在最终HTTP状态产生后记录登录或未被管理切面记录的拒绝事件。 */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String id = UUID.randomUUID().toString();
    request.setAttribute("requestId", id);
    response.setHeader("X-Request-Id", id);
    try {
      chain.doFilter(request, response);
    } finally {
      String path = request.getRequestURI();
      int status = response.getStatus();
      boolean login = path.equals("/api/auth/login") && request.getMethod().equals("POST");
      if (path.startsWith("/api/")
          && (login || status == 401 || status == 403)
          && !Boolean.TRUE.equals(request.getAttribute("auditRecorded"))) {
        String event =
            login
                ? (status < 400 ? "LOGIN_SUCCESS" : "LOGIN_FAILED")
                : (status == 401 ? "UNAUTHENTICATED" : "REQUEST_DENIED");
        recorder.record(
            new AuditEvent(
                (Long) request.getAttribute("auditUserId"),
                (String) request.getAttribute("auditAccount"),
                event,
                status < 400,
                id,
                java.util.Objects.toString(request.getAttribute("errorCode"), "OK"),
                request.getMethod(),
                path,
                status));
      }
    }
  }
}
