package com.municipal.rbaccore.infrastructure.web;

import com.municipal.rbaccore.service.port.security.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

/** 把Servlet请求转换成只读服务端操作上下文，禁止前端自报授权结论。 */
@Component
public class HttpRequestContext implements RequestContext {
  /** 请求追踪号由过滤器统一生成。 */
  public String requestId() {
    return RequestAuditFilter.requestId();
  }

  /** 错误内容限制为已翻译的业务错误码。 */
  public void error(String code) {
    RequestAuditFilter.error(code);
  }

  /** 主体必须已由认证用例核实。 */
  public void actor(Long id, String username) {
    RequestAuditFilter.actor(id, username);
  }

  /** 后台用例不能伪造HTTP方法或路径模板。 */
  public Operation operation() {
    HttpServletRequest request = RequestAuditFilter.current();
    return request == null
        ? null
        : new Operation(
            request.getMethod(),
            String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)));
  }
}
