package com.municipal.rbaccore.infrastructure.onboarding;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.service.port.onboarding.OnboardingReport;
import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;

/** 自动探测的真实HTTP故障边界，服务端响应为明确测试桩，不冒称业务集成。 */
class R35ProtocolProbeBoundaryTest {
  /** 各错误状态都形成失败报告，并尝试退出实际建立的测试会话。 */
  @Test
  @DisplayName("R35 真实HTTP探测覆盖身份隔离、坏响应和会话清理失败")
  void R35_ProtocolFailuresAndCleanup() throws Exception {
    for (String mode :
        List.of(
            "OK",
            "LOGIN_DENIED",
            "LOGIN_EMPTY",
            "LOGIN_TOKEN_MISSING",
            "ME_STATUS",
            "ME_EMPTY",
            "ME_OTHER_SYSTEM",
            "AUDIENCE_ALLOWED",
            "PERMISSION_STATUS",
            "PERMISSION_EMPTY",
            "PERMISSION_GRANTED",
            "LOGOUT_RETRY",
            "LOGOUT_FAILED",
            "NETWORK_LOGIN",
            "NETWORK_AFTER_LOGIN")) {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      AtomicBoolean loggedOut = new AtomicBoolean();
      server.createContext("/", exchange -> respond(exchange, mode, loggedOut));
      server.start();
      try {
        ServletWebServerApplicationContext context = mock(ServletWebServerApplicationContext.class);
        WebServer web = mock(WebServer.class);
        when(context.getWebServer()).thenReturn(web);
        when(web.getPort()).thenReturn(server.getAddress().getPort());
        List<OnboardingReport.Check> report =
            new LocalIdentityProtocolProbe(context)
                .run("application-a", "probe", "fixture-password");
        assertFalse(report.isEmpty(), mode);
        if (mode.equals("OK")) {
          assertEquals(6, report.size());
          assertTrue(report.stream().allMatch(row -> row.status().equals("PASS")));
          assertTrue(loggedOut.get());
        } else {
          assertTrue(report.stream().anyMatch(row -> row.status().equals("FAIL")), mode + report);
          if (!mode.startsWith("LOGIN_")
              && !mode.equals("NETWORK_LOGIN")
              && !mode.equals("NETWORK_AFTER_LOGIN")
              && !mode.equals("LOGOUT_FAILED")) assertTrue(loggedOut.get(), mode);
        }
      } finally {
        server.stop(0);
      }
    }
  }

  /** 非HTTP或尚未启动的容器不能被当作可用身份协议。 */
  @Test
  @DisplayName("R35 未运行HTTP时探测显式失败")
  void R35_RequiresLiveHttpContext() {
    assertThrows(
        IllegalStateException.class,
        () ->
            new LocalIdentityProtocolProbe(mock(ApplicationContext.class))
                .run("app", "fixture", "fixture"));
    assertThrows(
        IllegalStateException.class,
        () ->
            new LocalIdentityProtocolProbe(mock(ServletWebServerApplicationContext.class))
                .run("app", "fixture", "fixture"));
  }

  /** 按协议路径返回受控结果，不读取或回显登录请求体。 */
  private void respond(HttpExchange exchange, String mode, AtomicBoolean loggedOut)
      throws java.io.IOException {
    String path = exchange.getRequestURI().getPath();
    int status = 200;
    String body = "{}";
    if (mode.equals("NETWORK_LOGIN")
        || mode.equals("NETWORK_AFTER_LOGIN") && !path.equals("/auth/login")) {
      exchange.close();
      return;
    }
    if (path.equals("/auth/login")) {
      if (mode.equals("LOGIN_DENIED")) status = 403;
      else if (mode.equals("LOGIN_EMPTY")) body = "";
      else if (mode.equals("LOGIN_TOKEN_MISSING")) body = "{\"accessToken\":3}";
      else body = "{\"accessToken\":\"isolated-probe-token\"}";
    } else if (path.equals("/auth/me")) {
      if (loggedOut.get()) status = 401;
      else if (mode.equals("ME_STATUS")) status = 403;
      else if (mode.equals("ME_EMPTY")) body = "";
      else
        body =
            mode.equals("ME_OTHER_SYSTEM")
                ? "{\"system\":\"other\"}"
                : "{\"system\":\"application-a\"}";
    } else if (path.equals("/api/me")) status = mode.equals("AUDIENCE_ALLOWED") ? 200 : 401;
    else if (path.startsWith("/auth/permissions/")) {
      if (mode.equals("PERMISSION_STATUS")) status = 403;
      else if (mode.equals("PERMISSION_EMPTY")) body = "";
      else body = mode.equals("PERMISSION_GRANTED") ? "{\"allowed\":true}" : "{\"allowed\":false}";
    } else if (path.equals("/auth/logout")) {
      if (mode.equals("LOGOUT_FAILED") || mode.equals("LOGOUT_RETRY") && !loggedOut.get()) {
        status = 500;
        if (mode.equals("LOGOUT_RETRY")) loggedOut.set(true);
      } else {
        status = 204;
        loggedOut.set(true);
      }
    }
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(
        status, status == 204 ? -1 : bytes.length == 0 ? -1 : bytes.length);
    if (status != 204 && bytes.length > 0) exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
