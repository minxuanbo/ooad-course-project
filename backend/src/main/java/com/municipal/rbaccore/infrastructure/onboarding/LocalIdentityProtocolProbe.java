package com.municipal.rbaccore.infrastructure.onboarding;

import com.fasterxml.jackson.databind.JsonNode;
import com.municipal.rbaccore.service.port.onboarding.*;
import java.time.Duration;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** 自动测试真实协议，限制到本进程HTTP端口，不使用用户填写的入口地址。 */
@Component
@RequiredArgsConstructor
public class LocalIdentityProtocolProbe implements IdentityProtocolProbe {
  private final ApplicationContext context;

  /** 错误正文不回传，避免未来服务错误内容意外包含测试凭据。 */
  @Override
  public List<OnboardingReport.Check> run(String system, String account, String password) {
    List<OnboardingReport.Check> checks = new ArrayList<>();
    String token = null;
    boolean loggedOut = false;
    IdentityProtocolClient client = client();
    try {
      ResponseEntity<JsonNode> login =
          client.login(new IdentityProtocolClient.LoginBody(account, password, List.of(system)));
      JsonNode body = login.getBody();
      int status = login.getStatusCode().value();
      if (status != 200 || body == null || !body.path("accessToken").isTextual()) {
        checks.add(
            new OnboardingReport.Check(
                "LOGIN",
                "FAIL",
                status == 403 ? "应用未启用或测试账号没有登录资格；待接入应用预期拒绝，启用后复检" : "测试登录未通过，请核对专用账号及身份服务",
                status));
        return checks;
      }
      token = "Bearer " + body.path("accessToken").asText();
      checks.add(new OnboardingReport.Check("LOGIN", "PASS", "真实应用登录成功", status));
      ResponseEntity<JsonNode> me = client.me(token);
      add(
          checks,
          "IDENTITY",
          me.getStatusCode().value() == 200
              && me.getBody() != null
              && system.equals(me.getBody().path("system").asText()),
          "当前身份与应用受众一致",
          me.getStatusCode().value());
      ResponseEntity<JsonNode> isolated = client.management(token);
      add(
          checks,
          "AUDIENCE",
          isolated.getStatusCode().value() == 401,
          "应用JWT不能进入RBAC管理端",
          isolated.getStatusCode().value());
      ResponseEntity<JsonNode> denied =
          client.permission(token, "onboarding-denied-" + UUID.randomUUID());
      add(
          checks,
          "DENIED_PERMISSION",
          denied.getStatusCode().value() == 200
              && denied.getBody() != null
              && !denied.getBody().path("allowed").asBoolean(true),
          "未授予的随机短码被拒绝",
          denied.getStatusCode().value());
      ResponseEntity<Void> logout = client.logout(token);
      loggedOut = logout.getStatusCode().value() == 204;
      add(checks, "LOGOUT", loggedOut, "已退出本次测试会话", logout.getStatusCode().value());
      ResponseEntity<JsonNode> revoked = client.me(token);
      add(
          checks,
          "SESSION_REVOKED",
          revoked.getStatusCode().value() == 401,
          "退出后的同JWT立即失效",
          revoked.getStatusCode().value());
    } catch (RestClientException exception) {
      checks.add(new OnboardingReport.Check("TRANSPORT", "FAIL", "身份协议请求未完成，请稍后重试", null));
    } finally {
      if (token != null && !loggedOut) {
        try {
          ResponseEntity<Void> cleanup = client.logout(token);
          add(
              checks,
              "CLEANUP",
              cleanup.getStatusCode().value() == 204,
              "清理本次测试会话",
              cleanup.getStatusCode().value());
        } catch (RestClientException exception) {
          checks.add(new OnboardingReport.Check("CLEANUP", "FAIL", "本次测试会话清理未确认", null));
        }
      }
    }
    return checks;
  }

  /** 每项结果独立展示，失败不可伪装成对象权限或业务功能已通过。 */
  private void add(
      List<OnboardingReport.Check> results, String code, boolean passed, String label, int status) {
    results.add(
        new OnboardingReport.Check(
            code, passed ? "PASS" : "FAIL", passed ? label : label + "（结果不符合预期）", status));
  }

  /** 不采用反向代理Host头或任意远端URL，防止变成通用HTTP代理。 */
  private IdentityProtocolClient client() {
    if (!(context instanceof ServletWebServerApplicationContext web) || web.getWebServer() == null)
      throw new IllegalStateException("身份探测需要正在运行的HTTP服务");
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));
    RestClient rest =
        RestClient.builder()
            .baseUrl("http://127.0.0.1:" + web.getWebServer().getPort())
            .requestFactory(factory)
            .defaultStatusHandler(status -> status.isError(), (request, response) -> {})
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rest))
        .build()
        .createClient(IdentityProtocolClient.class);
  }
}
