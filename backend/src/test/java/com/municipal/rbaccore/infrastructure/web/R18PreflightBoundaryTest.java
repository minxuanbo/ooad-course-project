package com.municipal.rbaccore.infrastructure.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.api.resource.ResourceController;
import com.municipal.rbaccore.api.resource.dto.ResourceSystemQueryDTO;
import com.municipal.rbaccore.api.role.RoleController;
import com.municipal.rbaccore.api.role.dto.RoleQueryDTO;
import com.municipal.rbaccore.service.port.audit.AuditWriter;
import com.municipal.rbaccore.service.port.department.DepartmentUseCase;
import com.municipal.rbaccore.service.port.resource.ResourceUseCase;
import com.municipal.rbaccore.service.port.role.RoleUseCase;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import com.municipal.rbaccore.service.port.user.UserManagementUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/** R18预鉴权只作用于已评审控制器，格式校验和其他处理器不被误接管。 */
class R18PreflightBoundaryTest {
  private static final String ICON = "/favicon.ico";
  private static final String ROLES = "/api/roles";

  /** 已匹配资源路由却没有合法对象变量时，只执行入口检查，不伪造目标。 */
  @Test
  @DisplayName("R18 API资源缺少路径变量保持入口鉴权")
  void R18_apiResourceWithoutPathVariablesKeepsGate() throws Exception {
    ManagementAccessUseCase access = mock(ManagementAccessUseCase.class);
    ResourceUseCase resources = mock(ResourceUseCase.class);
    ManagementPreflight preflight =
        new ManagementPreflight(
            access,
            resources,
            mock(UserManagementUseCase.class),
            mock(DepartmentUseCase.class),
            mock(RoleUseCase.class),
            mock(AuditWriter.class));
    MockHttpServletRequest request =
        new MockHttpServletRequest("PATCH", "/api/api-resources/not-an-id");
    request.addHeader("Authorization", "Bearer test");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/api-resources/{id}");
    HandlerMethod handler =
        new HandlerMethod(
            new ResourceController(resources),
            ResourceController.class.getMethod("tree", String.class, ResourceSystemQueryDTO.class));
    assertTrue(preflight.preHandle(request, new MockHttpServletResponse(), handler));
    verify(access).require("Bearer test");
    verifyNoInteractions(resources);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/permissions/tree");
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, java.util.Map.of("id", "9"));
    assertTrue(preflight.preHandle(request, new MockHttpServletResponse(), handler));
    verify(access, times(2)).require("Bearer test");
    verifyNoInteractions(resources);
  }

  /** 静态资源与非管理方法不应触发后台权限检查。 */
  @Test
  @DisplayName("R18 预鉴权不干预静态资源和未纳入管理的处理器")
  void R18_ignoresUnmanagedHandlers() throws Exception {
    ManagementAccessUseCase access = mock(ManagementAccessUseCase.class);
    ResourceUseCase resources = mock(ResourceUseCase.class);
    UserManagementUseCase users = mock(UserManagementUseCase.class);
    DepartmentUseCase departments = mock(DepartmentUseCase.class);
    AuditWriter audit = mock(AuditWriter.class);
    ManagementPreflight preflight =
        new ManagementPreflight(
            access, resources, users, departments, mock(RoleUseCase.class), audit);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", ICON);
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertTrue(preflight.preHandle(request, response, new Object()));
    assertTrue(
        preflight.preHandle(
            request,
            response,
            new HandlerMethod(new Object(), Object.class.getMethod("toString"))));
    verifyNoInteractions(access, resources, users, departments, mock(RoleUseCase.class), audit);
  }

  /** 没有Spring路径变量时仍检查入口权限，不能从其他属性猜目标ID。 */
  @Test
  @DisplayName("R18 缺少合法路径变量时只检查管理入口，不伪造对象目标")
  void R18_missingPathVariablesKeepRouteGate() throws Exception {
    ManagementAccessUseCase access = mock(ManagementAccessUseCase.class);
    ResourceUseCase resources = mock(ResourceUseCase.class);
    RoleUseCase roles = mock(RoleUseCase.class);
    ManagementPreflight preflight =
        new ManagementPreflight(
            access,
            resources,
            mock(UserManagementUseCase.class),
            mock(DepartmentUseCase.class),
            roles,
            mock(AuditWriter.class));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", ROLES);
    request.addHeader("Authorization", "Bearer test");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, ROLES);
    HandlerMethod handler =
        new HandlerMethod(
            new RoleController(mock(RoleUseCase.class)),
            RoleController.class.getMethod("list", String.class, RoleQueryDTO.class));
    assertTrue(preflight.preHandle(request, new MockHttpServletResponse(), handler));
    verify(roles).checkTarget("Bearer test", null, false);
    verifyNoInteractions(access);
    verifyNoInteractions(resources);
  }

  /** null和带符号的字符串不得通过十进制转换器，而合法输入仍按Long解析。 */
  @Test
  @DisplayName("R18 十进制参数绑定拒绝null和符号且不截断大整数")
  void R18_decimalBindingRejectsMissingRawText() {
    WebDataBinder binder = new WebDataBinder(null);
    new RoleParameterBinding().bindDecimalParameters(binder);
    java.beans.PropertyEditor editor = binder.findCustomEditor(Long.class, null);
    assertThrows(IllegalArgumentException.class, () -> editor.setAsText(null));
    assertThrows(IllegalArgumentException.class, () -> editor.setAsText("+1"));
    editor.setAsText("9007199254740991");
    assertEquals(9007199254740991L, editor.getValue());
  }
}
