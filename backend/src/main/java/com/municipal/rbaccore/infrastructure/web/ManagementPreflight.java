package com.municipal.rbaccore.infrastructure.web;

import com.municipal.rbaccore.api.department.DepartmentController;
import com.municipal.rbaccore.api.resource.ResourceController;
import com.municipal.rbaccore.api.role.RoleController;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.api.user.UserController;
import com.municipal.rbaccore.service.port.audit.AuditEvent;
import com.municipal.rbaccore.service.port.audit.AuditWriter;
import com.municipal.rbaccore.service.port.department.DepartmentUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.resource.ResourceUseCase;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import com.municipal.rbaccore.service.port.user.UserManagementUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** 管理接口在MVC解析参数前先拒绝匿名、无权及越界请求，真实AOP仍负责执行期鉴权。 */
@Component
@RequiredArgsConstructor
public class ManagementPreflight implements HandlerInterceptor {
  private final ManagementAccessUseCase access;
  private final ResourceUseCase resources;
  private final UserManagementUseCase users;
  private final DepartmentUseCase departments;
  private final AuditWriter audit;

  /** 只处理已评审的管理控制器，人员接口保留部门范围而不要求共同根。 */
  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod method)
        || (!ResourceController.class.isAssignableFrom(method.getBeanType())
            && !RoleController.class.isAssignableFrom(method.getBeanType())
            && !UserController.class.isAssignableFrom(method.getBeanType())
            && !DepartmentController.class.isAssignableFrom(method.getBeanType()))) {
      return true;
    }
    String template =
        String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
    String header = request.getHeader("Authorization");
    try {
      Long id = targetId(request);
      if (DepartmentController.class.isAssignableFrom(method.getBeanType())) {
        departments.checkTarget(header, id, !request.getMethod().equals("GET"));
        return true;
      }
      if (UserController.class.isAssignableFrom(method.getBeanType())) {
        boolean protectSelf =
            !request.getMethod().equals("GET")
                || template.equals("/api/users/{id}/assignable-roles");
        users.checkTarget(header, id, protectSelf);
        return true;
      }
      access.require(header);
      if (id != null
          && (template.equals("/api/permissions/{id}")
              || template.equals("/api/permissions/{id}/apis"))) {
        resources.checkPermissionTarget(header, id);
      } else if (id != null && template.equals("/api/api-resources/{id}")) {
        resources.checkApiTarget(header, id);
      } else if (template.equals("/api/permissions/tree")
          || template.equals("/api/api-resources")) {
        Long systemId = safeId(request.getParameter("systemId"));
        if (systemId != null) resources.checkSystemTarget(header, systemId);
      }
      return true;
    } catch (BusinessException exception) {
      recordDenial(request, template, exception);
      throw exception;
    }
  }

  /** 使用Spring已匹配的路径变量，不从前端请求体读取可信目标或权限。 */
  private Long targetId(HttpServletRequest request) {
    Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (variables instanceof Map<?, ?> pathVariables) {
      Object id = pathVariables.get("id");
      return id instanceof String value ? safeId(value) : null;
    }
    return null;
  }

  /** 仅合法ID参与提前范围判断，格式错误留给后续API参数校验统一返回400。 */
  private Long safeId(String value) {
    if (value == null) return null;
    try {
      long id = Long.parseLong(value);
      return id > 0 && id <= ApiLimits.MAX_ID ? id : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  /** 提前拒绝未进入Controller AOP，已识别身份仍记录一次最小判定，匿名交过滤器补记。 */
  private void recordDenial(
      HttpServletRequest request, String template, BusinessException exception) {
    Long userId = (Long) request.getAttribute("auditUserId");
    if (userId == null) return;
    RequestAuditFilter.recorded();
    audit.record(
        new AuditEvent(
            userId,
            (String) request.getAttribute("auditAccount"),
            "AUTHORIZATION",
            false,
            RequestAuditFilter.requestId(),
            exception.getCode(),
            request.getMethod(),
            template,
            exception.getStatus()));
  }
}
