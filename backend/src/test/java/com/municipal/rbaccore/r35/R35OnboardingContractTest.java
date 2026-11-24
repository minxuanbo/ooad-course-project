package com.municipal.rbaccore.r35;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.municipal.rbaccore.config.RuntimeConfiguration;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.onboarding.OnboardingPlan;
import com.municipal.rbaccore.service.port.onboarding.OnboardingUseCase;
import com.municipal.rbaccore.shared.ManagementContractFixture;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/** R35在隔离MySQL和真实HTTP中验证接入向导，不使用正式账号或业务库。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.name=application-core-user-test",
      "app.identity.enabled=true",
      "app.identity.allowed-origins=http://localhost:5174"
    })
@ActiveProfiles("core-user-test")
class R35OnboardingContractTest {
  private static final String LOGIN = "/api/auth/login";
  private static final String WIZARD = "/api/systems/2/onboarding";
  private static final String EXTERNAL_LOGIN = "/auth/login";
  @Autowired
  TestRestTemplate http;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  PasswordEncoder encoder;
  @Autowired
  com.fasterxml.jackson.databind.ObjectMapper mapper;
  @Autowired
  OnboardingUseCase service;

  /** 每项重建隔离夹具，向导不得修改本人的人员角色集合。 */
  @BeforeEach
  void prepare() {
    ManagementContractFixture.prepare(jdbc, encoder);
    jdbc.update("UPDATE systems SET connection_status='PENDING' WHERE id=2");
  }

  /** 向导读取既有目录，不在预览时偷偷登记或重授。 */
  @Test
  @DisplayName("R35 管理员读取既有待接入应用的向导快照")
  void R35_SnapshotAndReadOnlyImport() {
    String admin = token();
    JsonNode snapshot = call(HttpMethod.GET, WIZARD, admin, null, 200).path("data");
    assertEquals("business", snapshot.path("system").path("code").asText());
    assertEquals(64, snapshot.path("plan").path("baseFingerprint").asText().length());
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM api_resources", Long.class);
    JsonNode preview =
        call(
                HttpMethod.POST,
                WIZARD + "/import",
                admin,
                Map.of(
                    "document",
                    "{\"openapi\":\"3.0.3\",\"paths\":{\"/documents\":{\"get\":{\"operationId\":\"listDocuments\"}}}}"),
                200)
            .path("data");
    assertEquals("listDocuments", preview.path("operations").get(0).path("operationId").asText());
    assertTrue(preview.path("operations").get(0).path("requiresPermission").asBoolean());
    assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM api_resources", Long.class));
  }

  /** 三种向导操作都先拒绝未认证和无权访问，不把坏输入抢先当作格式错误。 */
  @Test
  @DisplayName("R35 向导匿名及无API账号在解析输入前拒绝")
  void R35_RejectBeforeParsing() {
    for (String path : List.of(WIZARD + "/import", WIZARD + "/check", WIZARD + "/complete")) {
      call(HttpMethod.POST, path, "", Map.of(), 401);
      call(HttpMethod.POST, path, token(2), Map.of(), 403);
    }
    call(HttpMethod.GET, "/api/systems/999999/onboarding", token(), null, 403);
  }

  /** 完成接入真正写入运行期目录，原JWT下一次查询读取当前数据库。 */
  @Test
  @DisplayName("R35 完成接入后动态operationId生效，原授权和人员角色保留")
  void R35_PublishAndUseDynamicCatalog() {
    String admin = token();
    long usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM user_roles", Long.class);
    long rbacBefore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id"
                + " WHERE p.system_id=1",
            Long.class);
    Map<String, Object> plan = plan(admin);
    JsonNode checked =
        call(HttpMethod.POST, WIZARD + "/check", admin, Map.of("plan", plan), 200).path("data");
    assertTrue(checked.path("configurationValid").asBoolean(), checked.path("errors").toString());
    assertEquals(
        "PENDING",
        jdbc.queryForObject("SELECT connection_status FROM systems WHERE id=2", String.class));
    JsonNode published =
        call(
                HttpMethod.POST,
                WIZARD + "/complete",
                admin,
                Map.of("plan", plan, "businessConfirmed", true, "revocationConfirmed", true),
                200)
            .path("data");
    assertEquals("CONNECTED", published.path("system").path("connectionStatus").asText());
    assertEquals(usersBefore, jdbc.queryForObject("SELECT COUNT(*) FROM user_roles", Long.class));
    assertEquals(
        rbacBefore,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_permissions rp JOIN permissions p ON p.id=rp.permission_id"
                + " WHERE p.system_id=1",
            Long.class));
    assertEquals(
        999L,
        jdbc.queryForObject(
            "SELECT id FROM permissions WHERE system_id=2 AND code='sensitive'", Long.class));
    String application =
        call(
                HttpMethod.POST,
                EXTERNAL_LOGIN,
                "",
                Map.of(
                    "username",
                    "stage4-4",
                    "password",
                    ManagementContractFixture.PASSWORD,
                    "systems",
                    List.of("business")),
                200)
            .path("accessToken")
            .asText();
    assertTrue(
        call(HttpMethod.GET, "/auth/api-permissions/listDocuments", application, null, 200)
            .path("allowed")
            .asBoolean());
    long api =
        jdbc.queryForObject(
            "SELECT id FROM api_resources WHERE system_id=2 AND path_template='/documents'",
            Long.class);
    call(HttpMethod.PATCH, "/api/api-resources/" + api, admin, Map.of("enabled", false), 200);
    assertFalse(
        call(HttpMethod.GET, "/auth/api-permissions/listDocuments", application, null, 200)
            .path("allowed")
            .asBoolean());
    call(HttpMethod.PATCH, "/api/api-resources/" + api, admin, Map.of("enabled", true), 200);
    assertTrue(
        call(HttpMethod.GET, "/auth/api-permissions/listDocuments", application, null, 200)
            .path("allowed")
            .asBoolean());
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", plan, "businessConfirmed", true, "revocationConfirmed", true),
        409);
    JsonNode current = call(HttpMethod.GET, WIZARD, admin, null, 200).path("data").path("plan");
    JsonNode report =
        call(
                HttpMethod.POST,
                WIZARD + "/check",
                admin,
                Map.of(
                    "plan",
                    current,
                    "testAccount",
                    "stage4-4",
                    "testPassword",
                    ManagementContractFixture.PASSWORD),
                200)
            .path("data");
    assertEquals(6, report.path("protocolChecks").size(), report.toString());
    report
        .path("protocolChecks")
        .forEach(check -> assertEquals("PASS", check.path("status").asText(), check.toString()));
  }

  /** 关联错误返回配置错误列表，完成时拒绝且不留下半成品。 */
  @Test
  @DisplayName("R35 重复项、循环、跨草稿映射及缺登录资格均不能发布")
  void R35_InvalidConfigurationReferences() {
    String admin = token();
    List<java.util.function.Consumer<Map<String, Object>>> cases = new ArrayList<>();
    cases.add(
        value -> rows(value, "apis").add(new LinkedHashMap<>(rows(value, "apis").getFirst())));
    cases.add(
        value ->
            rows(value, "permissions")
                .add(new LinkedHashMap<>(rows(value, "permissions").getFirst())));
    cases.add(value -> rows(value, "permissions").removeFirst());
    cases.add(value -> rows(value, "permissions").get(3).put("apiKeys", List.of("GET /missing")));
    cases.add(
        value ->
            rows(value, "permissions")
                .get(3)
                .put("apiKeys", List.of("GET /documents", "GET /documents")));
    cases.add(value -> rows(value, "permissions").get(3).put("apiKeys", List.of()));
    cases.add(value -> rows(value, "apis").getFirst().put("enabled", false));
    cases.add(value -> rows(value, "permissions").get(2).put("parentCode", "DOC_MENU"));
    cases.add(value -> rows(value, "permissions").get(3).put("parentCode", "missing"));
    cases.add(value -> rows(value, "permissions").get(3).put("parentCode", "LOGIN"));
    cases.add(value -> value.put("loginPermissionCode", "missing"));
    cases.add(value -> rows(value, "permissions").get(1).put("enabled", false));
    cases.add(value -> rows(value, "permissions").get(1).put("type", "API"));
    cases.add(value -> rows(value, "permissions").get(1).put("apiKeys", List.of("GET /documents")));
    cases.add(
        value ->
            rows(value, "operations")
                .add(new LinkedHashMap<>(rows(value, "operations").getFirst())));
    cases.add(
        value -> rows(value, "operations").getFirst().put("permissionRoles", List.of("missing")));
    cases.add(
        value ->
            rows(value, "operations")
                .getFirst()
                .put("permissionRoles", List.of("employee", "employee")));
    cases.add(value -> rows(value, "operations").getFirst().put("path", "/missing"));
    cases.add(
        value -> {
          rows(value, "permissions").get(3).put("type", "BUTTON");
          rows(value, "permissions").get(3).put("apiKeys", List.of());
        });
    cases.add(
        value ->
            rows(value, "roleGrants")
                .add(new LinkedHashMap<>(rows(value, "roleGrants").getFirst())));
    cases.add(value -> rows(value, "roleGrants").getFirst().put("roleId", 999999));
    cases.add(
        value -> rows(value, "roleGrants").getFirst().put("permissionCodes", List.of("missing")));
    cases.add(
        value ->
            rows(value, "roleGrants").getFirst().put("permissionCodes", List.of("LOGIN", "LOGIN")));
    cases.add(value -> value.put("roleGrants", List.of()));
    for (java.util.function.Consumer<Map<String, Object>> mutation : cases) {
      Map<String, Object> desired = mutable(plan(admin));
      mutation.accept(desired);
      JsonNode report =
          call(HttpMethod.POST, WIZARD + "/check", admin, Map.of("plan", desired), 200)
              .path("data");
      assertFalse(report.path("configurationValid").asBoolean(), desired.toString());
      assertFalse(report.path("errors").isEmpty());
      call(
          HttpMethod.POST,
          WIZARD + "/complete",
          admin,
          Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
          400);
    }
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM application_profiles", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM api_operations", Integer.class));
  }

  /** 所有字段格式由MVC在资格预检之后拒绝，错误输入不落库。 */
  @Test
  @DisplayName("R35 空值、长度、入口地址和确认字段有直接边界用例")
  void R35_InputBoundaries() {
    String admin = token();
    for (String url :
        List.of(
            "javascript:alert(1)",
            "https://user:secret@example.com",
            "https://example.com/#fragment",
            "not a url",
            "ftp://example.com")) {
      Map<String, Object> desired = plan(admin);
      desired.put("entryUrl", url);
      call(HttpMethod.POST, WIZARD + "/check", admin, Map.of("plan", desired), 400);
    }
    Map<String, Object> desired = plan(admin);
    for (Object bad : List.of("", "a".repeat(65))) {
      desired.put("baseFingerprint", bad);
      call(HttpMethod.POST, WIZARD + "/check", admin, Map.of("plan", desired), 400);
    }
    desired = mutable(plan(admin));
    rows(desired, "apis").add(null);
    call(HttpMethod.POST, WIZARD + "/check", admin, Map.of("plan", desired), 400);
    call(
        HttpMethod.POST,
        WIZARD + "/check",
        admin,
        Map.of("plan", plan(admin), "testAccount", "stage4-4"),
        400);
    call(
        HttpMethod.POST,
        WIZARD + "/check",
        admin,
        Map.of("plan", plan(admin), "testPassword", "fixture"),
        400);
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", plan(admin), "businessConfirmed", false, "revocationConfirmed", true),
        400);
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", plan(admin), "businessConfirmed", true, "revocationConfirmed", false),
        400);
    call(HttpMethod.POST, WIZARD + "/import", admin, Map.of("document", "x".repeat(1048577)), 400);
    call(HttpMethod.GET, "/api/systems/1/onboarding", admin, null, 409);
    call(HttpMethod.GET, "/api/systems/bad/onboarding", admin, null, 400);
  }

  /** 等待期间别人修改配置时，旧指纹不能覆盖目录或元数据。 */
  @Test
  @DisplayName("R35 并发指纹变化、已移除应用及非五级账号不能完成接入")
  void R35_StaleInactiveAndLevelProtection() {
    String admin = token();
    Map<String, Object> desired = plan(admin);
    jdbc.update("UPDATE permissions SET enabled=0 WHERE id=999");
    call(HttpMethod.POST, WIZARD + "/check", admin, Map.of("plan", desired), 409);
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
        409);
    desired = plan(admin);
    jdbc.update("UPDATE systems SET enabled=0 WHERE id=2");
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
        409);
    desired = plan(admin);
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
        400);
    jdbc.update(
        "INSERT INTO role_permissions SELECT 2,id FROM permissions WHERE code LIKE"
            + " 'rbac.systems.onboarding.%'");
    call(HttpMethod.GET, WIZARD, token(2), null, 403);
  }

  /** 对象权限元数据与部分门禁保留，动态修改无需重启且不会恢复人工撤权。 */
  @Test
  @DisplayName("R35 元数据二次维护保留主键及其他应用关系，撤销独立LOGIN即时拒绝")
  void R35_ReconfigureAndRevokeLogin() {
    String admin = token();
    Map<String, Object> desired = plan(admin);
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
        200);
    long originalPermission =
        jdbc.queryForObject(
            "SELECT id FROM permissions WHERE system_id=2 AND code='DOC_READ'", Long.class);
    JsonNode published = call(HttpMethod.GET, WIZARD, admin, null, 200).path("data").path("plan");
    desired = mutable(mapper.convertValue(published, Map.class));
    rows(desired, "operations").getFirst().put("requiresPermission", false);
    rows(desired, "permissions").get(0).put("parentCode", "");
    desired.put("entryUrl", null);
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
        200);
    assertEquals(
        originalPermission,
        jdbc.queryForObject(
            "SELECT id FROM permissions WHERE system_id=2 AND code='DOC_READ'", Long.class));
    String application =
        call(
                HttpMethod.POST,
                EXTERNAL_LOGIN,
                "",
                Map.of(
                    "username",
                    "stage4-4",
                    "password",
                    ManagementContractFixture.PASSWORD,
                    "systems",
                    List.of("business")),
                200)
            .path("accessToken")
            .asText();
    assertTrue(
        call(HttpMethod.GET, "/auth/api-permissions/listDocuments", application, null, 200)
            .path("allowed")
            .asBoolean());
    List<Long> ids =
        jdbc.queryForList("SELECT permission_id FROM role_permissions WHERE role_id=3", Long.class);
    List<Long> without =
        jdbc.queryForList(
            "SELECT rp.permission_id FROM role_permissions rp JOIN permissions p ON"
                + " p.id=rp.permission_id WHERE rp.role_id=3 AND p.code<>'LOGIN'",
            Long.class);
    call(HttpMethod.PUT, "/api/roles/3/permissions", admin, Map.of("ids", without), 200);
    call(HttpMethod.GET, "/auth/me", application, null, 403);
    call(HttpMethod.PUT, "/api/roles/3/permissions", admin, Map.of("ids", ids), 200);
    call(HttpMethod.GET, "/auth/me", application, null, 200);
    published = call(HttpMethod.GET, WIZARD, admin, null, 200).path("data").path("plan");
    desired = mutable(mapper.convertValue(published, Map.class));
    desired.put("operations", List.of());
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
        200);
    call(HttpMethod.GET, "/auth/api-permissions/listDocuments", application, null, 404);
  }

  /** 深复制让各项变更只影响本项草稿，不污染合法基线。 */
  @SuppressWarnings("unchecked")
  private Map<String, Object> mutable(Map<String, Object> value) {
    return mapper.convertValue(value, LinkedHashMap.class);
  }

  /** 输入已在本测试构造为列表，转换只是便于表达业务变更。 */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(Map<String, Object> value, String key) {
    return (List<Map<String, Object>>) value.get(key);
  }

  /** 同一HTTP状态可能有多个原因，直接断言稳定业务码方便需求追溯。 */
  @Test
  @DisplayName("R35 新错误码直接覆盖且失败不写配置")
  void R35_StableErrorCodes() {
    String admin = token();
    assertEquals(
        "OPENAPI_INVALID",
        call(
                HttpMethod.POST,
                WIZARD + "/import",
                admin,
                Map.of("document", "openapi: 2.0\npaths: {}"),
                400)
            .path("code")
            .asText());
    assertEquals(
        "ONBOARDING_RBAC_RESERVED",
        call(HttpMethod.GET, "/api/systems/1/onboarding", admin, null, 409).path("code").asText());
    Map<String, Object> desired = plan(admin);
    desired.put("roleGrants", List.of());
    assertEquals(
        "ONBOARDING_INVALID",
        call(
                HttpMethod.POST,
                WIZARD + "/complete",
                admin,
                Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
                400)
            .path("code")
            .asText());
    desired = plan(admin);
    jdbc.update("UPDATE systems SET connection_status='CONNECTED' WHERE id=2");
    assertEquals(
        "ONBOARDING_STALE",
        call(
                HttpMethod.POST,
                WIZARD + "/complete",
                admin,
                Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
                409)
            .path("code")
            .asText());
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM application_profiles", Integer.class));
  }

  /** 新前端来源随显式接入启用，不要求改环境变量或重启。 */
  @Test
  @DisplayName("R35 新应用前端Origin启用后动态放行，移除后立即拒绝")
  void R35_DynamicIdentityOrigin() {
    String admin = token();
    HttpHeaders headers = new HttpHeaders();
    headers.setOrigin("https://documents.example");
    headers.set("Access-Control-Request-Method", "GET");
    headers.set("Access-Control-Request-Headers", "Authorization");
    assertEquals(
        403,
        http.exchange("/auth/me", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
    Map<String, Object> desired = plan(admin);
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
        200);
    ResponseEntity<String> allowed =
        http.exchange("/auth/me", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    assertEquals(200, allowed.getStatusCode().value());
    assertEquals("https://documents.example", allowed.getHeaders().getAccessControlAllowOrigin());
    call(HttpMethod.DELETE, "/api/systems/2", admin, null, 200);
    assertEquals(
        403,
        http.exchange("/auth/me", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
  }

  /** 待接入的真实拒绝不能伪装成成功，空白凭据不发登录请求。 */
  @Test
  @DisplayName("R35 待接入与无凭据检查分别表达PENDING，低级别根用户仍拒绝")
  void R35_PendingProbeAndRootLevel() {
    String admin = token();
    JsonNode pending =
        call(
                HttpMethod.POST,
                WIZARD + "/check",
                admin,
                Map.of(
                    "plan",
                    plan(admin),
                    "testAccount",
                    "stage4-4",
                    "testPassword",
                    ManagementContractFixture.PASSWORD),
                200)
            .path("data");
    assertEquals("PENDING", pending.path("protocolChecks").get(0).path("status").asText());
    JsonNode blank =
        call(
                HttpMethod.POST,
                WIZARD + "/check",
                admin,
                Map.of("plan", plan(admin), "testAccount", " ", "testPassword", " "),
                200)
            .path("data");
    assertEquals("TEST_ACCOUNT", blank.path("protocolChecks").get(0).path("code").asText());
    Map<String, Object> invalid = mutable(plan(admin));
    invalid.put("loginPermissionCode", "missing");
    assertFalse(
        call(
                HttpMethod.POST,
                WIZARD + "/check",
                admin,
                Map.of(
                    "plan",
                    invalid,
                    "testAccount",
                    "stage4-4",
                    "testPassword",
                    ManagementContractFixture.PASSWORD),
                200)
            .path("data")
            .path("configurationValid")
            .asBoolean());
    jdbc.update("UPDATE users SET department_id=1 WHERE id=2");
    jdbc.update(
        "INSERT INTO role_permissions SELECT 2,id FROM permissions WHERE code LIKE"
            + " 'rbac.systems.onboarding.%'");
    call(HttpMethod.GET, WIZARD, token(2), null, 403);
  }

  /** 人工确认和选权权限在Service仍有防线，不能靠DTO或先前检查放行。 */
  @Test
  @DisplayName("R35 Service重复检查两项确认，变更角色子集还需当前选权API")
  void R35_ServiceConfirmationAndRoleApi() {
    String admin = token();
    OnboardingPlan desired = mapper.convertValue(plan(admin), OnboardingPlan.class);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", WIZARD + "/complete");
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/systems/{id}/onboarding/complete");
    RequestAttributes previous = RequestContextHolder.getRequestAttributes();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      for (boolean[] flags : List.of(new boolean[] {false, true}, new boolean[] {true, false})) {
        BusinessException error =
            assertThrows(
                BusinessException.class,
                () -> service.complete("Bearer " + admin, 2L, desired, flags[0], flags[1]));
        assertEquals("ONBOARDING_CONFIRMATION_REQUIRED", error.getCode());
      }
    } finally {
      RequestContextHolder.setRequestAttributes(previous);
    }
    long permission =
        jdbc.queryForObject(
            "SELECT id FROM permissions WHERE code='rbac.roles.permissions.write'", Long.class);
    jdbc.update("DELETE FROM role_permissions WHERE role_id=1 AND permission_id=?", permission);
    try {
      call(
          HttpMethod.POST,
          WIZARD + "/complete",
          admin,
          Map.of("plan", plan(admin), "businessConfirmed", true, "revocationConfirmed", true),
          403);
    } finally {
      jdbc.update("INSERT INTO role_permissions VALUES(1,?)", permission);
    }
  }

  /** 二次配置可以撤映射和角色子集，但不能删除已经登记的API。 */
  @Test
  @DisplayName("R35 保留目录并明确撤回映射，旧接口不允许无痕删除")
  void R35_RevokeMappingAndRetainApi() {
    String admin = token();
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", plan(admin), "businessConfirmed", true, "revocationConfirmed", true),
        200);
    Map<String, Object> desired =
        mutable(
            mapper.convertValue(
                call(HttpMethod.GET, WIZARD, admin, null, 200).path("data").path("plan"),
                Map.class));
    Map<String, Object> removed = mutable(desired);
    removed.put("apis", List.of());
    assertFalse(
        call(HttpMethod.POST, WIZARD + "/check", admin, Map.of("plan", removed), 200)
            .path("data")
            .path("configurationValid")
            .asBoolean());
    for (Map<String, Object> permission : rows(desired, "permissions")) {
      if (permission.get("code").equals("DOC_READ")) {
        permission.put("type", "BUTTON");
        permission.put("apiKeys", List.of());
        permission.put("parentCode", "");
      }
    }
    rows(desired, "operations").getFirst().put("requiresPermission", false);
    rows(desired, "roleGrants").stream()
        .filter(row -> ((Number) row.get("roleId")).intValue() == 3)
        .findFirst()
        .orElseThrow()
        .put("permissionCodes", List.of("LOGIN", "sensitive"));
    desired.put("entryUrl", " ");
    call(
        HttpMethod.POST,
        WIZARD + "/complete",
        admin,
        Map.of("plan", desired, "businessConfirmed", true, "revocationConfirmed", true),
        200);
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM permission_apis pa JOIN permissions p ON p.id=pa.permission_id"
                + " WHERE p.system_id=2",
            Integer.class));
    assertEquals(
        1,
        jdbc.queryForObject("SELECT COUNT(*) FROM api_resources WHERE system_id=2", Integer.class));
    jdbc.update("UPDATE roles SET enabled=0 WHERE id=3");
    assertFalse(
        call(HttpMethod.POST, WIZARD + "/check", admin, Map.of("plan", plan(admin)), 200)
            .path("data")
            .path("configurationValid")
            .asBoolean());
  }

  /** 故障发生在已写部分目录之后，整事务仍须回滚并翻译稳定冲突。 */
  @Test
  @DisplayName("R35 真实MySQL接入元数据写故障回滚目录与全部关系")
  void R35_DatabaseFailureRollsBack() {
    String admin = token();
    long before = jdbc.queryForObject("SELECT COUNT(*) FROM permissions", Long.class);
    jdbc.execute(
        "CREATE TRIGGER r35_fail_profile BEFORE INSERT ON application_profiles FOR EACH ROW SIGNAL"
            + " SQLSTATE '45000' SET MESSAGE_TEXT='isolated write failure'");
    try {
      JsonNode result =
          call(
              HttpMethod.POST,
              WIZARD + "/complete",
              admin,
              Map.of("plan", plan(admin), "businessConfirmed", true, "revocationConfirmed", true),
              409);
      assertEquals("DATA_CONFLICT", result.path("code").asText());
      assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM permissions", Long.class));
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM api_resources WHERE system_id=2", Integer.class));
    } finally {
      jdbc.execute("DROP TRIGGER r35_fail_profile");
    }
  }

  /** 最小合法草稿保留已有权限，新登录项不隐式取得业务接口。 */
  private Map<String, Object> plan(String admin) {
    JsonNode snapshot = call(HttpMethod.GET, WIZARD, admin, null, 200).path("data").path("plan");
    Map<String, Object> plan = new LinkedHashMap<>();
    plan.put("baseFingerprint", snapshot.path("baseFingerprint").asText());
    plan.put("entryUrl", "https://documents.example");
    plan.put("loginPermissionCode", "LOGIN");
    plan.put("apis", List.of(Map.of("method", "GET", "path", "/documents", "enabled", true)));
    List<Map<String, Object>> permissions = new ArrayList<>();
    permissions.add(permission("sensitive", "原业务操作", "BUTTON", null, List.of()));
    permissions.add(permission("LOGIN", "登录应用", "BUTTON", null, List.of()));
    permissions.add(permission("DOC_MENU", "文档管理", "MENU", null, List.of()));
    permissions.add(permission("DOC_READ", "读取文档", "API", "DOC_MENU", List.of("GET /documents")));
    plan.put("permissions", permissions);
    plan.put(
        "operations",
        List.of(
            Map.of(
                "operationId",
                "listDocuments",
                "method",
                "GET",
                "path",
                "/documents",
                "requiresPermission",
                true,
                "permissionRoles",
                List.of())));
    plan.put(
        "roleGrants",
        List.of(
            Map.of(
                "roleId",
                3,
                "permissionCodes",
                List.of("sensitive", "LOGIN", "DOC_MENU", "DOC_READ"))));
    return plan;
  }

  /** 明确表达可空父级，避免Map.of不接受null造成伪RED。 */
  private Map<String, Object> permission(
      String code, String name, String type, String parent, List<String> apis) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("code", code);
    result.put("name", name);
    result.put("type", type);
    result.put("parentCode", parent);
    result.put("enabled", true);
    result.put("apiKeys", apis);
    return result;
  }

  /** HTTP只携带本项虚构账号口令，响应或失败信息不打印JWT。 */
  private String token() {
    return token(1);
  }

  /** 为当前测试建立管理受众会话。 */
  private String token(int id) {
    return call(
            HttpMethod.POST,
            LOGIN,
            "",
            Map.of("account", "stage4-" + id, "password", ManagementContractFixture.PASSWORD),
            200)
        .path("data")
        .path("token")
        .asText();
  }

  /** 真实HTTP状态和稳定错误码参与断言，不把测试令牌写入错误信息。 */
  private JsonNode call(HttpMethod method, String path, String token, Object body, int expected) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (!token.isEmpty()) headers.setBearerAuth(token);
    ResponseEntity<JsonNode> response =
        http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    assertEquals(
        expected,
        response.getStatusCode().value(),
        () ->
            path
                + " code="
                + (response.getBody() == null ? "" : response.getBody().path("code").asText()));
    return response.getBody();
  }
}
