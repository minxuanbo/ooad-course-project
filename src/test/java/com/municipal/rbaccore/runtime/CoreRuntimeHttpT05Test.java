package com.municipal.rbaccore.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbac.ApiException;
import com.municipal.rbaccore.bootstrap.InitialAuthorizationService;
import com.municipal.rbaccore.bootstrap.RoleBootstrapPlan;
import com.municipal.rbaccore.importing.*;
import com.municipal.rbaccore.logging.*;
import com.municipal.rbaccore.model.*;
import com.municipal.rbaccore.repository.*;
import com.municipal.rbaccore.security.CoreManaged;
import com.municipal.rbaccore.service.MaintenanceGuard;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** T05通过真实嵌入式HTTP服务器验证新链路，不能以直接调用Service替代接口验收。 */
@SpringBootTest(
    classes = CoreRuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-runtime-test")
@ActiveProfiles("core-http-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(CoreRuntimeHttpT05Test.Fixtures.class)
class CoreRuntimeHttpT05Test {
  private static final String LOGIN = "/api/auth/login";
  private static final String LOGOUT = "/api/auth/logout";
  private static final String USERS = "/api/users";
  private static final String TREE = "/api/departments/tree";
  private static final String LOGS = "/api/authorization-logs";
  private static final String ME = "/api/me";
  private static final String PERMISSIONS = "/api/me/permissions";
  private static final String UNREGISTERED = "/api/t05-unregistered";
  private static final String PASSWORD = "t05-employee-password";
  private static final String ADMIN_PASSWORD = "t05-admin-password";
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired CoreImportService importer;
  @Autowired InitialAuthorizationService bootstrap;
  @Autowired PasswordEncoder encoder;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired DepartmentRepository departments;
  @Autowired PlatformTransactionManager transactions;
  @Autowired MaintenanceGuard maintenance;
  @Autowired FailureEvents failures;
  private long rootId;
  private long departmentManagerId;
  private String departmentManager;

  /** 测试辅助入口只在本测试上下文注册，不进入产品目录。 */
  @TestConfiguration(proxyBeanMethods = false)
  static class Fixtures {
    /** 捕获安全运维事件，以真实数据库故障验证日志降级行为。 */
    @Bean
    FailureEvents failureEvents() {
      return new FailureEvents();
    }

    /** 复用真实T03导入服务，避免用少量模拟数据替代万人链路。 */
    @Bean
    CoreImportService importer(
        DepartmentRepository departments,
        UserRepository users,
        ImportSourceRepository sources,
        PasswordEncoder encoder) {
      return new CoreImportService(
          departments, users, sources, new WorkbookImportPlanner(), encoder);
    }

    /** 用实际Spring处理器验证未登记的管理入口默认拒绝。 */
    @Bean
    UnregisteredController unregisteredController() {
      return new UnregisteredController();
    }
  }

  /** 仅测试收集异常类型，不接收或保存原始异常与身份资料。 */
  static class FailureEvents {
    private final java.util.concurrent.atomic.AtomicInteger count =
        new java.util.concurrent.atomic.AtomicInteger();

    /** 记录数据库日志写入失败通知的次数。 */
    @org.springframework.context.event.EventListener
    public void onFailure(CoreAuditWriter.WriteFailure event) {
      assertFalse(event.exceptionType().contains("SQL"));
      count.incrementAndGet();
    }
  }

  /** 此处理器不在正式源码中，不能被初始化为业务API。 */
  @RestController
  @CoreManaged
  static class UnregisteredController {
    /** 即使处理器存在，缺少资源映射也不能直接执行。 */
    @GetMapping(UNREGISTERED)
    public Map<String, Boolean> read() {
      return Map.of("executed", true);
    }
  }

  /** 每个测试类一次全量初始化；强制中途失败后检查回滚，再执行成功。 */
  @BeforeAll
  void t05InitializeRealPopulationAndAuthorization() {
    assertEquals("rbac_core_t05_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    jdbc.update("UPDATE permissions SET parent_id=NULL");
    for (String table :
        List.of(
            "authorization_logs",
            "bootstrap_receipts",
            "import_sources",
            "user_roles",
            "role_permissions",
            "permission_apis",
            "users",
            "roles",
            "permissions",
            "api_resources",
            "systems")) {
      jdbc.update("DELETE FROM " + table);
    }
    jdbc.update("UPDATE departments SET parent_id=NULL");
    jdbc.update("DELETE FROM departments");
    importer.importDirectory(
        Path.of("."), new BootstrapCredentials(PASSWORD, ADMIN_PASSWORD, "admin"));
    RoleBootstrapPlan preview = bootstrap.preview(Path.of("."));
    assertEquals(
        Map.of("系统管理员", 1L, "公司领导", 64L, "部门经理", 106L, "项目经理", 72L, "普通员工", 9758L),
        preview.plannedCounts());
    assertEquals(10000, preview.assignments().size());
    assertEquals(0, roles.count());
    long failingUser = id("SG000005");
    jdbc.execute("DROP TRIGGER IF EXISTS t05_fail_seed");
    jdbc.execute(
        "CREATE TRIGGER t05_fail_seed BEFORE INSERT ON user_roles FOR EACH ROW BEGIN IF"
            + " NEW.user_id="
            + failingUser
            + " THEN SIGNAL SQLSTATE '23000' SET MYSQL_ERRNO=1062,MESSAGE_TEXT='T05 forced"
            + " failure'; END IF; END");
    try {
      assertEquals(
          "DATA_CONFLICT",
          assertThrows(ApiException.class, () -> bootstrap.initialize(Path.of("."))).getCode());
      assertEquals(0, roles.count());
      assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM systems", Integer.class));
      assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM permissions", Integer.class));
      assertEquals(
          0, jdbc.queryForObject("SELECT count(*) FROM bootstrap_receipts", Integer.class));
      assertEquals(10001, users.count());
    } finally {
      jdbc.execute("DROP TRIGGER t05_fail_seed");
    }
    assertEquals(10001, bootstrap.initialize(Path.of(".")).createdAssignments());
    assertEquals(5, roles.count());
    assertEquals(10001, jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class));
    assertEquals(20, jdbc.queryForObject("SELECT count(*) FROM api_resources", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM bootstrap_receipts", Integer.class));
    rootId = jdbc.queryForObject("SELECT id FROM departments WHERE parent_id IS NULL", Long.class);
    departmentManager =
        jdbc.queryForObject(
            "SELECT username FROM users WHERE position='部长' ORDER BY id LIMIT 1", String.class);
    departmentManagerId = id(departmentManager);
    assertEquals(
        12,
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE()",
            Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                + "AND table_name IN ('sessions','login_identifiers','companies')",
            Integer.class));
  }

  /** 未知账号应进入正式登录校验并返回统一失败，不能缺失实际Controller。 */
  @Test
  @DisplayName("T05 实际HTTP未知账号统一返回401 LOGIN_FAILED")
  void t05RejectsUnknownAccountThroughRealHttp() {
    ResponseEntity<Map> response =
        http.postForEntity(
            LOGIN,
            Map.of("account", "unknown-t05-account", "password", "test-password"),
            Map.class);
    assertEquals(401, response.getStatusCode().value());
    assertEquals("LOGIN_FAILED", response.getBody().get("code"));
  }

  /** 工号和手机号返回同一安全身份，正式调用实际执行CloneFactory映射。 */
  @Test
  @DisplayName("T05 工号手机号同身份同权限，普通员工两入口均拒绝")
  void t05DualLoginAndManagementAdmission() throws Exception {
    JsonNode byNumber = loginData("SG000001", PASSWORD);
    String phone =
        jdbc.queryForObject("SELECT phone FROM users WHERE username='SG000001'", String.class);
    JsonNode byPhone = loginData(phone, PASSWORD);
    assertEquals(byNumber.get("user"), byPhone.get("user"));
    String numberToken = "Bearer " + byNumber.get("token").asText();
    String phoneToken = "Bearer " + byPhone.get("token").asText();
    assertEquals(
        body(request("GET", PERMISSIONS, numberToken, null)).get("data"),
        body(request("GET", PERMISSIONS, phoneToken, null)).get("data"));
    assertEquals(
        body(request("GET", TREE, numberToken, null)).get("data"),
        body(request("GET", TREE, phoneToken, null)).get("data"));
    assertEquals(43200, byNumber.get("expiresIn").asInt());
    assertEquals(5, byNumber.get("user").size());
    assertFalse(byNumber.toString().contains("passwordHash"));
    assertFalse(byNumber.get("user").has("phone"));
    assertFalse(byNumber.get("user").has("employeeNo"));
    String ordinaryPhone =
        jdbc.queryForObject("SELECT phone FROM users WHERE username='SG010000'", String.class);
    for (String account : List.of("SG010000", ordinaryPhone)) {
      ResponseEntity<String> response =
          request("POST", LOGIN, null, Map.of("account", account, "password", PASSWORD));
      assertEquals(403, response.getStatusCode().value());
      assertEquals("MANAGEMENT_ACCESS_DENIED", body(response).get("code").asText());
      assertTrue(body(response).get("data").isNull());
      assertOneLog(response, "LOGIN_FAILED", false);
    }
  }

  /** 停用、错误口令和未知输入统一401，旧身份也按当前状态重新检查。 */
  @Test
  @DisplayName("T05 停用及错误口令统一401，已有Bearer立即失效")
  void t05DisabledAndWrongPasswordAreUniform() throws Exception {
    String token = token("SG000001", PASSWORD);
    String phone =
        jdbc.queryForObject("SELECT phone FROM users WHERE username='SG000001'", String.class);
    try {
      jdbc.update("UPDATE users SET enabled=0 WHERE username='SG000001'");
      assertEquals(401, request("GET", ME, token, null).getStatusCode().value());
      for (String account : List.of("SG000001", phone)) {
        ResponseEntity<String> disabled =
            request("POST", LOGIN, null, Map.of("account", account, "password", PASSWORD));
        assertEquals(401, disabled.getStatusCode().value());
        assertEquals("LOGIN_FAILED", body(disabled).get("code").asText());
      }
    } finally {
      jdbc.update("UPDATE users SET enabled=1 WHERE username='SG000001'");
    }
    for (String account : List.of("SG000001", phone, "00999999999999999")) {
      ResponseEntity<String> wrong =
          request("POST", LOGIN, null, Map.of("account", account, "password", "wrong-password"));
      assertEquals(401, wrong.getStatusCode().value());
      assertEquals("LOGIN_FAILED", body(wrong).get("code").asText());
    }
    assertEquals(200, request("POST", LOGOUT, token, null).getStatusCode().value());
    assertEquals(401, request("GET", ME, token, null).getStatusCode().value());
  }

  /** 同用户双字段同值允许，缺手机号保留工号入口，跨用户歧义不得任选一人。 */
  @Test
  @DisplayName("T05 手机号缺失、同值、跨用户碰撞及前导零边界")
  void t05IdentifierBoundaries() throws Exception {
    String original =
        jdbc.queryForObject("SELECT phone FROM users WHERE username='SG000001'", String.class);
    try {
      jdbc.update("UPDATE users SET phone=NULL WHERE username='SG000001'");
      assertEquals(id("SG000001"), loginData("SG000001", PASSWORD).get("user").get("id").asLong());
      assertEquals(
          401,
          request("POST", LOGIN, null, Map.of("account", original, "password", PASSWORD))
              .getStatusCode()
              .value());
      jdbc.update("UPDATE users SET phone=username WHERE username='SG000001'");
      assertEquals(id("SG000001"), loginData("SG000001", PASSWORD).get("user").get("id").asLong());
    } finally {
      jdbc.update("UPDATE users SET phone=? WHERE username='SG000001'", original);
    }
    long collision = fixture("t05-collision", "SG000001", "employee", PASSWORD);
    try {
      assertEquals(
          401,
          request("POST", LOGIN, null, Map.of("account", "SG000001", "password", PASSWORD))
              .getStatusCode()
              .value());
    } finally {
      removeFixture(collision);
    }
    long leading = fixture("0003", "00123456789", "company-leader", " spaced password ");
    try {
      assertEquals(leading, loginData("0003", " spaced password ").get("user").get("id").asLong());
      assertEquals(
          leading, loginData("00123456789", " spaced password ").get("user").get("id").asLong());
      assertEquals(
          401,
          request("POST", LOGIN, null, Map.of("account", "3", "password", " spaced password "))
              .getStatusCode()
              .value());
      assertEquals(
          401,
          request("POST", LOGIN, null, Map.of("account", "0003", "password", "spaced password"))
              .getStatusCode()
              .value());
    } finally {
      removeFixture(leading);
    }
  }

  /** 权限撤回后本人查询仍可用，但管理请求及再次登录立即拒绝。 */
  @Test
  @DisplayName("T05 角色撤回与名称变化不绕过实际授权")
  void t05UsesCurrentPermissionsRatherThanRoleNames() throws Exception {
    String token = token(departmentManager, PASSWORD);
    long roleId =
        jdbc.queryForObject(
            "SELECT role_id FROM user_roles WHERE user_id=?", Long.class, departmentManagerId);
    try {
      jdbc.update("UPDATE roles SET name='任意改名' WHERE id=?", roleId);
      assertEquals(200, request("GET", USERS, token, null).getStatusCode().value());
      jdbc.update("DELETE FROM user_roles WHERE user_id=?", departmentManagerId);
      assertEquals(200, request("GET", ME, token, null).getStatusCode().value());
      assertEquals(0, body(request("GET", PERMISSIONS, token, null)).get("data").size());
      assertEquals(403, request("GET", USERS, token, null).getStatusCode().value());
      assertEquals(
          403,
          request("POST", LOGIN, null, Map.of("account", departmentManager, "password", PASSWORD))
              .getStatusCode()
              .value());
    } finally {
      jdbc.update(
          "INSERT IGNORE INTO user_roles(user_id,role_id) VALUES(?,?)",
          departmentManagerId,
          roleId);
      jdbc.update("UPDATE roles SET name='部门经理' WHERE id=?", roleId);
    }
  }

  /** 外系统相同模板、无角色与仅父菜单都不能获得本系统管理准入。 */
  @Test
  @DisplayName("T05 外系统同路径与父菜单不能越过RBAC登录边界")
  void t05RejectsExternalAndNavigationOnlyRoles() throws Exception {
    long account = fixture("t05-no-access", null, null, PASSWORD);
    long menuRole = createRole("t05-menu-only", "rbac.workspace");
    long externalSystem = 0;
    long externalRole = 0;
    try {
      assertEquals(
          403,
          request("POST", LOGIN, null, Map.of("account", "t05-no-access", "password", PASSWORD))
              .getStatusCode()
              .value());
      jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", account, menuRole);
      assertEquals(
          403,
          request("POST", LOGIN, null, Map.of("account", "t05-no-access", "password", PASSWORD))
              .getStatusCode()
              .value());
      jdbc.update("INSERT INTO systems(code,name,enabled) VALUES('t05-other','系统管理后台',1)");
      externalSystem =
          jdbc.queryForObject("SELECT id FROM systems WHERE code='t05-other'", Long.class);
      jdbc.update(
          "INSERT INTO permissions(system_id,code,name,type,enabled)"
              + " VALUES(?,'other.users','其他系统人员','BUTTON',1)",
          externalSystem);
      jdbc.update(
          "INSERT INTO api_resources(system_id,http_method,path_template,enabled)"
              + " VALUES(?,'GET','/api/users',1)",
          externalSystem);
      long permission =
          jdbc.queryForObject(
              "SELECT id FROM permissions WHERE system_id=?", Long.class, externalSystem);
      long api =
          jdbc.queryForObject(
              "SELECT id FROM api_resources WHERE system_id=?", Long.class, externalSystem);
      jdbc.update("INSERT INTO permission_apis(permission_id,api_id) VALUES(?,?)", permission, api);
      jdbc.update("INSERT INTO roles(code,name,enabled) VALUES('t05-external','系统管理员',1)");
      externalRole =
          jdbc.queryForObject("SELECT id FROM roles WHERE code='t05-external'", Long.class);
      jdbc.update(
          "INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)",
          externalRole,
          permission);
      jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", account, externalRole);
      assertEquals(
          403,
          request("POST", LOGIN, null, Map.of("account", "t05-no-access", "password", PASSWORD))
              .getStatusCode()
              .value());
    } finally {
      removeFixture(account);
      removeRole(menuRole);
      if (externalRole != 0) removeRole(externalRole);
      if (externalSystem != 0) {
        jdbc.update(
            "DELETE FROM permission_apis WHERE permission_id IN (SELECT id FROM permissions WHERE"
                + " system_id=?)",
            externalSystem);
        jdbc.update("DELETE FROM permissions WHERE system_id=?", externalSystem);
        jdbc.update("DELETE FROM api_resources WHERE system_id=?", externalSystem);
        jdbc.update("DELETE FROM systems WHERE id=?", externalSystem);
      }
    }
  }

  /** 同API的多个权限按OR合并，任一启用链失效须立即重新计算。 */
  @Test
  @DisplayName("T05 多权限OR及用户角色权限API系统启停实时生效")
  void t05RecomputesEnabledChainsAndOrMappings() throws Exception {
    long account = fixture("t05-chain", null, null, PASSWORD);
    long role = createRole("t05-chain-role", "rbac.users.read");
    long alternative = 0;
    try {
      jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", account, role);
      String token = token("t05-chain", PASSWORD);
      jdbc.update(
          "INSERT INTO permissions(system_id,code,name,type,enabled) SELECT"
              + " id,'t05.alternative','备用查询','BUTTON',1 FROM systems WHERE code='rbac'");
      alternative =
          jdbc.queryForObject(
              "SELECT id FROM permissions WHERE code='t05.alternative'", Long.class);
      long api =
          jdbc.queryForObject(
              "SELECT id FROM api_resources WHERE http_method='GET' AND path_template='/api/users'",
              Long.class);
      jdbc.update(
          "INSERT INTO permission_apis(permission_id,api_id) VALUES(?,?)", alternative, api);
      jdbc.update(
          "INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)", role, alternative);
      jdbc.update(
          "UPDATE permissions SET enabled=0 WHERE code IN ('rbac.users.read','rbac.workspace')");
      assertEquals(200, request("GET", USERS, token, null).getStatusCode().value());
      jdbc.update("UPDATE permissions SET enabled=0 WHERE id=?", alternative);
      assertEquals(403, request("GET", USERS, token, null).getStatusCode().value());
      jdbc.update("UPDATE permissions SET enabled=1 WHERE id=?", alternative);
      for (String sql :
          List.of(
              "UPDATE roles SET enabled=? WHERE code='t05-chain-role'",
              "UPDATE api_resources SET enabled=? WHERE http_method='GET' AND"
                  + " path_template='/api/users'",
              "UPDATE systems SET enabled=? WHERE code='rbac'")) {
        jdbc.update(sql, false);
        assertEquals(403, request("GET", USERS, token, null).getStatusCode().value());
        jdbc.update(sql, true);
        assertEquals(200, request("GET", USERS, token, null).getStatusCode().value());
      }
      assertEquals(
          0, body(request("GET", USERS + "?q=%25", token, null)).get("data").get("total").asLong());
      assertEquals(
          0, body(request("GET", USERS + "?q=_", token, null)).get("data").get("total").asLong());
    } finally {
      jdbc.update("UPDATE systems SET enabled=1 WHERE code='rbac'");
      jdbc.update(
          "UPDATE api_resources SET enabled=1 WHERE http_method='GET' AND"
              + " path_template='/api/users'");
      jdbc.update(
          "UPDATE permissions SET enabled=1 WHERE code IN ('rbac.users.read','rbac.workspace')");
      removeFixture(account);
      removeRole(role);
      if (alternative != 0) {
        jdbc.update("DELETE FROM permission_apis WHERE permission_id=?", alternative);
        jdbc.update("DELETE FROM permissions WHERE id=?", alternative);
      }
    }
  }

  /** 真实日志约束故障只产生安全运维事件，不能改写已判定的允许或拒绝。 */
  @Test
  @DisplayName("T05 日志独立事务失败不改变授权结果且不泄漏敏感参数")
  void t05IsolatesAuditStorageFailure() throws Exception {
    String admin = token("admin", ADMIN_PASSWORD);
    String leader = token("SG000001", PASSWORD);
    int previous = failures.count.get();
    jdbc.execute(
        "CREATE TRIGGER t05_fail_log BEFORE INSERT ON authorization_logs FOR EACH ROW SIGNAL"
            + " SQLSTATE '23000' SET MYSQL_ERRNO=1062,MESSAGE_TEXT='T05 forced log failure'");
    try {
      assertEquals(200, request("GET", USERS, admin, null).getStatusCode().value());
      assertEquals(403, request("GET", LOGS, leader, null).getStatusCode().value());
      assertEquals(401, request("GET", USERS, null, null).getStatusCode().value());
      assertEquals(previous + 3, failures.count.get());
    } finally {
      jdbc.execute("DROP TRIGGER t05_fail_log");
    }
    ResponseEntity<String> restored = request("GET", USERS, admin, null);
    assertEquals(200, restored.getStatusCode().value());
    assertOneLog(restored, "AUTHORIZATION", true);
    String stored =
        jdbc.queryForList("SELECT username,reason,request_path FROM authorization_logs").toString();
    assertFalse(stored.contains(PASSWORD));
    assertFalse(stored.contains(ADMIN_PASSWORD));
    assertFalse(stored.contains(admin));
    assertFalse(stored.contains("do-not-store-this-query"));
  }

  /** 后端按部门树裁剪SQL范围，总数与分页不会泄漏兄弟部门。 */
  @Test
  @DisplayName("T05 部门树、搜索分页及总数受真实范围限制")
  void t05ScopesTreeAndPaginationInDatabase() throws Exception {
    String token = token(departmentManager, PASSWORD);
    long departmentId =
        jdbc.queryForObject(
            "SELECT department_id FROM users WHERE id=?", Long.class, departmentManagerId);
    long expected =
        jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE department_id=?", Long.class, departmentId);
    JsonNode tree = body(request("GET", TREE, token, null)).get("data");
    assertEquals(1, tree.size());
    assertEquals(departmentId, tree.get(0).get("id").asLong());
    assertTrue(tree.get(0).get("parentId").isNull());
    assertNotNull(
        jdbc.queryForObject(
            "SELECT parent_id FROM departments WHERE id=?", Long.class, departmentId));
    JsonNode first = body(request("GET", USERS + "?size=1", token, null)).get("data");
    JsonNode second = body(request("GET", USERS + "?size=1&page=1", token, null)).get("data");
    assertEquals(expected, first.get("total").asLong());
    assertEquals(expected, second.get("total").asLong());
    assertTrue(
        first.get("items").get(0).get("id").asLong()
            < second.get("items").get(0).get("id").asLong());
    assertFalse(first.get("items").get(0).has("phone"));
    assertEquals(
        expected,
        body(request("GET", USERS + "?q=sg", token, null)).get("data").get("total").asLong());
    JsonNode beyond =
        body(request("GET", USERS + "?page=100000&size=100", token, null)).get("data");
    assertEquals(0, beyond.get("items").size());
    assertEquals(expected, beyond.get("total").asLong());
    ResponseEntity<String> largestResponse =
        request("GET", USERS + "?page=2147483647&size=100", token, null);
    assertEquals(200, largestResponse.getStatusCode().value());
    JsonNode largest = body(largestResponse).get("data");
    assertEquals(0, largest.get("items").size());
    assertEquals(expected, largest.get("total").asLong());
    assertEquals(
        0,
        body(request("GET", USERS + "?q=not-a-person-t05", token, null))
            .get("data")
            .get("total")
            .asLong());
    assertEquals(
        403,
        request("GET", USERS + "?departmentId=" + rootId, token, null).getStatusCode().value());
    assertEquals(
        403,
        request("GET", USERS + "?departmentId=999999999", token, null).getStatusCode().value());
    String rootToken = token("admin", ADMIN_PASSWORD);
    assertEquals(
        404,
        request("GET", USERS + "?departmentId=999999999", rootToken, null).getStatusCode().value());
    assertEquals(
        10001, body(request("GET", USERS, rootToken, null)).get("data").get("total").asLong());
    assertEquals(403, request("GET", UNREGISTERED, rootToken, null).getStatusCode().value());
  }

  /** 日志必须同时具备实际API与共同根，不能只凭公司领导或日志角色名称放行。 */
  @Test
  @DisplayName("T05 日志权限和根范围独立校验，单次判定只记录一次")
  void t05RequiresGlobalScopeAndLogPermission() throws Exception {
    String leader = token("SG000001", PASSWORD);
    ResponseEntity<String> denied = request("GET", LOGS, leader, null);
    assertEquals(403, denied.getStatusCode().value());
    assertOneLog(denied, "AUTHORIZATION", false);
    long logRole = createRole("t05-log-only", "rbac.logs.read");
    try {
      jdbc.update(
          "INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", departmentManagerId, logRole);
      String local = token(departmentManager, PASSWORD);
      ResponseEntity<String> scopeDenied = request("GET", LOGS, local, null);
      assertEquals(403, scopeDenied.getStatusCode().value());
      assertEquals("MANAGEMENT_SCOPE_DENIED", body(scopeDenied).get("code").asText());
      assertOneLog(scopeDenied, "AUTHORIZATION", false);
      long rootLogOnly = fixture("t05-root-log", "00123456780", null, PASSWORD);
      try {
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", rootLogOnly, logRole);
        String rootLogToken = token("t05-root-log", PASSWORD);
        assertEquals(200, request("GET", LOGS, rootLogToken, null).getStatusCode().value());
        assertEquals(403, request("GET", USERS, rootLogToken, null).getStatusCode().value());
      } finally {
        removeFixture(rootLogOnly);
      }
    } finally {
      removeRole(logRole);
    }
    ResponseEntity<String> anonymous = request("GET", USERS, null, null);
    assertEquals(401, anonymous.getStatusCode().value());
    assertOneLog(anonymous, "UNAUTHENTICATED", false);
    ResponseEntity<String> allowed =
        request("GET", USERS + "?q=do-not-store-this-query", token("admin", ADMIN_PASSWORD), null);
    assertEquals(200, allowed.getStatusCode().value());
    assertOneLog(allowed, "AUTHORIZATION", true);
    assertEquals(
        USERS,
        jdbc.queryForObject(
            "SELECT request_path FROM authorization_logs WHERE request_id=?",
            String.class,
            allowed.getHeaders().getFirst("X-Request-Id")));
  }

  /** 相同快照翻页不重复、不纳入新记录；伪造和筛选变化均被拒绝。 */
  @Test
  @DisplayName("T05 日志快照总数、keyset分页、UTC边界及游标完整性")
  void t05PaginatesLogsWithStableSnapshot() throws Exception {
    jdbc.update("DELETE FROM authorization_logs WHERE username='t05-log-fixture'");
    for (int index = 0; index < 5; index++) {
      jdbc.update(
          "INSERT INTO"
              + " authorization_logs(occurred_at,username,app_code,allowed,request_id,reason,event_type,http_method,request_path,http_status)"
              + " VALUES('2026-01-01"
              + " 00:00:00','t05-log-fixture','rbac',?,'t05-case','OK','AUTHORIZATION','GET','/api/users',200)",
          index % 2);
    }
    String token = token("admin", ADMIN_PASSWORD);
    String filter = "?username=t05-log-fixture&limit=2";
    JsonNode first = body(request("GET", LOGS + filter, token, null)).get("data");
    assertEquals(5, first.get("total").asLong());
    assertTrue(first.get("hasMore").asBoolean());
    assertFalse(first.get("items").get(0).has("archivedAt"));
    String cursor = first.get("nextCursor").asText();
    jdbc.update(
        "INSERT INTO"
            + " authorization_logs(occurred_at,username,app_code,allowed,request_id,reason,event_type,http_method,request_path,http_status)"
            + " VALUES('2026-01-01"
            + " 00:00:00','t05-log-fixture','rbac',1,'t05-new','OK','AUTHORIZATION','GET','/api/users',200)");
    Set<Long> seen = new HashSet<>();
    first.get("items").forEach(item -> seen.add(item.get("id").asLong()));
    JsonNode next;
    do {
      next = body(request("GET", LOGS + filter + "&cursor=" + cursor, token, null)).get("data");
      assertEquals(5, next.get("total").asLong());
      next.get("items").forEach(item -> assertTrue(seen.add(item.get("id").asLong())));
      cursor = next.get("nextCursor").isNull() ? null : next.get("nextCursor").asText();
    } while (cursor != null);
    assertEquals(5, seen.size());
    assertFalse(next.get("hasMore").asBoolean());
    assertEquals(
        400,
        request(
                "GET",
                LOGS
                    + "?username=t05-log-fixture&limit=3&cursor="
                    + first.get("nextCursor").asText(),
                token,
                null)
            .getStatusCode()
            .value());
    assertEquals(
        400, request("GET", LOGS + "?cursor=invalid-cursor", token, null).getStatusCode().value());
    assertEquals(
        0,
        body(request(
                "GET", LOGS + "?username=t05-log-fixture&to=2026-01-01T00:00:00Z", token, null))
            .get("data")
            .get("total")
            .asLong());
    assertEquals(
        6,
        body(request(
                "GET",
                LOGS
                    + "?username=t05-log-fixture&from=2026-01-01T00:00:00Z&to=2026-01-01T00:00:01Z",
                token,
                null))
            .get("data")
            .get("total")
            .asLong());
  }

  /** API格式错误在DTO边界返回400，而非进入服务或悄悄强制转换。 */
  @Test
  @DisplayName("T05 严格字符串、分页参数和日志时间区间校验")
  void t05ValidatesApiInput() throws Exception {
    for (Object account : List.of(123, true, List.of("SG000001"), "", " ", "x".repeat(101))) {
      assertEquals(
          400,
          request("POST", LOGIN, null, Map.of("account", account, "password", PASSWORD))
              .getStatusCode()
              .value());
    }
    String admin = token("admin", ADMIN_PASSWORD);
    for (String query :
        List.of(
            "?page=-1",
            "?size=0",
            "?size=101",
            "?enabled=yes",
            "?departmentId=0",
            "?q=" + "x".repeat(101))) {
      assertEquals(400, request("GET", USERS + query, admin, null).getStatusCode().value());
    }
    for (String query :
        List.of(
            "?limit=0",
            "?limit=501",
            "?allowed=yes",
            "?userId=0",
            "?from=today",
            "?from=2026-01-01T00:00:00Z&to=2026-01-01T00:00:00Z")) {
      assertEquals(400, request("GET", LOGS + query, admin, null).getStatusCode().value());
    }
    assertEquals(404, request("POST", LOGS + "/archive", admin, Map.of()).getStatusCode().value());
  }

  /** 已执行回执防止重授，后续人工撤回与资源停用不能被启动修复。 */
  @Test
  @DisplayName("T05 批量初始化重复执行保留人工撤权和资源调整")
  void t05DoesNotRestoreAdjustedGrants() {
    long roleId =
        jdbc.queryForObject(
            "SELECT role_id FROM user_roles WHERE user_id=?", Long.class, departmentManagerId);
    try {
      jdbc.update("DELETE FROM user_roles WHERE user_id=?", departmentManagerId);
      jdbc.update("UPDATE permissions SET enabled=0 WHERE code='rbac.users.disable'");
      InitialAuthorizationService.Report report = bootstrap.initialize(Path.of("."));
      assertTrue(report.alreadyInitialized());
      assertEquals(0, report.createdAssignments());
      assertEquals(
          0,
          jdbc.queryForObject(
              "SELECT count(*) FROM user_roles WHERE user_id=?",
              Integer.class,
              departmentManagerId));
      assertEquals(
          false,
          jdbc.queryForObject(
              "SELECT enabled FROM permissions WHERE code='rbac.users.disable'", Boolean.class));
    } finally {
      jdbc.update(
          "INSERT IGNORE INTO user_roles(user_id,role_id) VALUES(?,?)",
          departmentManagerId,
          roleId);
      jdbc.update("UPDATE permissions SET enabled=1 WHERE code='rbac.users.disable'");
    }
  }

  /** 首次可信基线也必须具有完整K，分散的人员管理角色不能拼成全局维护者。 */
  @Test
  @DisplayName("T05 最后一名完整维护者保护按实际K并整体回滚")
  void t05ProtectsLastCompleteMaintainer() {
    assertEquals(
        "LAST_ADMIN_REQUIRED",
        assertThrows(
                ApiException.class,
                () ->
                    new TransactionTemplate(transactions)
                        .executeWithoutResult(
                            status -> {
                              maintenance.lock();
                              users.findById(id("admin")).orElseThrow().setEnabled(false);
                              maintenance.retainOne();
                            }))
            .getCode());
    assertEquals(
        true,
        jdbc.queryForObject("SELECT enabled FROM users WHERE username='admin'", Boolean.class));
  }

  /** 已知账号的内部ID查询仅用于测试夹具，不从姓名推断身份。 */
  private long id(String username) {
    return jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
  }

  /** 返回真实HTTP响应，URI已编码，不由客户端再次编码查询条件。 */
  private ResponseEntity<String> request(String method, String path, String token, Object payload)
      throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) headers.set("Authorization", token);
    return http.exchange(
        URI.create(http.getRootUri() + path),
        HttpMethod.valueOf(method),
        new HttpEntity<>(payload == null ? null : json.writeValueAsString(payload), headers),
        String.class);
  }

  /** 统一响应必须可解析且不以测试注入身份跳过实际登录。 */
  private JsonNode body(ResponseEntity<String> response) throws Exception {
    return json.readTree(response.getBody());
  }

  /** 成功登录才取得实际Bearer令牌。 */
  private JsonNode loginData(String account, String password) throws Exception {
    ResponseEntity<String> response =
        request("POST", LOGIN, null, Map.of("account", account, "password", password));
    assertEquals(200, response.getStatusCode().value(), () -> "登录失败状态=" + response.getStatusCode());
    return body(response).get("data");
  }

  /** 每次测试使用真实登录产生的身份，不传客户端userId。 */
  private String token(String account, String password) throws Exception {
    return "Bearer " + loginData(account, password).get("token").asText();
  }

  /** 固定requestId下必须恰好一条最小记录。 */
  private void assertOneLog(ResponseEntity<String> response, String event, boolean allowed) {
    String requestId = response.getHeaders().getFirst("X-Request-Id");
    assertNotNull(requestId);
    List<Map<String, Object>> records =
        jdbc.queryForList(
            "SELECT event_type,allowed FROM authorization_logs WHERE request_id=?", requestId);
    assertEquals(1, records.size());
    assertEquals(event, records.getFirst().get("event_type"));
    assertEquals(allowed, records.getFirst().get("allowed"));
  }

  /** 临时账号只用于明确的登录边界，生产初始化仍只使用已确认清单。 */
  private long fixture(String username, String phone, String roleCode, String password) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              User user =
                  new User(
                      username,
                      encoder.encode(password),
                      "T05测试人员",
                      departments.findById(rootId).orElseThrow(),
                      true);
              user.updateProfile(phone, "测试岗位");
              if (roleCode != null) {
                user.assignRole(
                    roles.findAll().stream()
                        .filter(role -> role.getCode().equals(roleCode))
                        .findFirst()
                        .orElseThrow());
              }
              return users.save(user).getId();
            });
  }

  /** 测试角色只关联指定的现有权限，用于验证日志范围独立于完整K。 */
  private long createRole(String code, String permissionCode) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              Role role = roles.save(new Role(code, "测试角色"));
              long permissionId =
                  jdbc.queryForObject(
                      "SELECT id FROM permissions WHERE code=?", Long.class, permissionCode);
              jdbc.update(
                  "INSERT INTO role_permissions(role_id,permission_id) VALUES(?,?)",
                  role.getId(),
                  permissionId);
              return role.getId();
            });
  }

  /** 明确清理临时账号的日志引用和关联，不关闭外键或动原始人员。 */
  private void removeFixture(long userId) {
    jdbc.update("DELETE FROM authorization_logs WHERE user_id=?", userId);
    jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
    jdbc.update("DELETE FROM users WHERE id=?", userId);
  }

  /** 临时角色清理不修改五个初始角色。 */
  private void removeRole(long roleId) {
    jdbc.update("DELETE FROM user_roles WHERE role_id=?", roleId);
    jdbc.update("DELETE FROM role_permissions WHERE role_id=?", roleId);
    jdbc.update("DELETE FROM roles WHERE id=?", roleId);
  }
}
