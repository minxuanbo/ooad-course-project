package com.municipal.rbaccore.r34;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.municipal.rbaccore.config.RuntimeConfiguration;
import com.municipal.rbaccore.shared.ManagementContractFixture;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** R34运行期应用登记与撤销的共享真实MySQL/HTTP契约。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-user-test")
@ActiveProfiles("core-user-test")
class R34ApplicationManagementContractTest {
  private static final String SYSTEMS = "/api/systems";
  private static final String LOGIN = "/api/auth/login";
  private static final String ME = "/api/me";
  private static final String MY_PERMISSIONS = "/api/me/permissions";
  private static final String USERS = "/api/users";
  @Autowired
  TestRestTemplate http;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  PasswordEncoder encoder;

  /** 测试专库内每项独立重建，避免上项授权污染下一项。 */
  @BeforeEach
  void prepare() {
    ManagementContractFixture.prepare(jdbc, encoder);
  }

  /** 登记、编辑、移除及恢复只改接入状态，不删除原目录或历史。 */
  @Test
  @DisplayName("R34 管理员在界面契约新增第三个系统并可移除恢复")
  void R34_CreateReviseRemoveAndRestore() {
    String admin = token(1);
    ResponseEntity<JsonNode> created =
        call(HttpMethod.POST, SYSTEMS, admin, Map.of("code", "third-system", "name", "第三个系统"));
    assertEquals(201, created.getStatusCode().value(), String.valueOf(created.getBody()));
    JsonNode system = created.getBody().path("data");
    long id = system.path("id").asLong();
    assertTrue(id > 0);
    assertEquals("PENDING", system.path("connectionStatus").asText());
    assertTrue(system.path("enabled").asBoolean());
    assertEquals(3, call(HttpMethod.GET, SYSTEMS, admin, null).getBody().path("data").size());
    assertEquals(
        200,
        call(
                HttpMethod.PATCH,
                SYSTEMS + "/" + id,
                admin,
                Map.of("name", "第三系统改名", "connectionStatus", "CONNECTED"))
            .getStatusCode()
            .value());
    assertEquals(
        200, call(HttpMethod.DELETE, SYSTEMS + "/" + id, admin, null).getStatusCode().value());
    assertFalse(jdbc.queryForObject("SELECT enabled FROM systems WHERE id=?", Boolean.class, id));
    assertEquals(
        200,
        call(HttpMethod.PATCH, SYSTEMS + "/" + id, admin, Map.of("enabled", true))
            .getStatusCode()
            .value());
    assertTrue(jdbc.queryForObject("SELECT enabled FROM systems WHERE id=?", Boolean.class, id));
  }

  /** 独立API和可信根都不可被界面输入代替。 */
  @Test
  @DisplayName("R34 未授予应用维护、未认证及未知系统均安全拒绝")
  void R34_RejectUnauthorizedAndUnknown() {
    assertEquals(
        401,
        call(HttpMethod.POST, SYSTEMS, "", Map.of("code", "new", "name", "新系统"))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        call(HttpMethod.POST, SYSTEMS, token(2), Map.of("code", "new", "name", "新系统"))
            .getStatusCode()
            .value());
    assertEquals(
        403, call(HttpMethod.DELETE, SYSTEMS + "/999999", token(1), null).getStatusCode().value());
    assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM systems", Integer.class));
  }

  /** 应用管理和其他管理入口一致，坏输入不能抢在身份及实际API门禁前返回。 */
  @Test
  @DisplayName("R34/R18 应用非法输入仍先检查身份权限和范围")
  void R34_AccessPrecedesInvalidInput() {
    for (String header : java.util.List.of("", token(2))) {
      int expected = header.isEmpty() ? 401 : 403;
      assertEquals(expected, call(HttpMethod.POST, SYSTEMS, header, Map.of()).getStatusCode().value());
      assertEquals(expected, call(HttpMethod.PATCH, SYSTEMS + "/bad", header, Map.of()).getStatusCode().value());
      assertEquals(expected, call(HttpMethod.GET, SYSTEMS + "/page?size=0", header, null).getStatusCode().value());
    }
  }

  /** 重复编码、空白、长度、非法状态及越界ID不能进入写入。 */
  @Test
  @DisplayName("R34 应用编码冲突及字段边界不会留下部分目录")
  void R34_RejectConflictAndInvalidFields() {
    String admin = token(1);
    assertEquals(
        409,
        call(HttpMethod.POST, SYSTEMS, admin, Map.of("code", "rbac", "name", "重复"))
            .getStatusCode()
            .value());
    for (Map<String, String> body :
        java.util.List.of(
            Map.of("code", " ", "name", "空白"),
            Map.of("code", "a".repeat(101), "name", "太长"),
            Map.of("code", "valid", "name", " "),
            Map.of("code", "valid", "name", "名".repeat(192)))) {
      assertEquals(400, call(HttpMethod.POST, SYSTEMS, admin, body).getStatusCode().value());
    }
    assertEquals(
        400, call(HttpMethod.PATCH, SYSTEMS + "/2", admin, Map.of()).getStatusCode().value());
    assertEquals(
        400,
        call(HttpMethod.PATCH, SYSTEMS + "/2", admin, Map.of("connectionStatus", "UNKNOWN"))
            .getStatusCode()
            .value());
    assertEquals(
        400,
        call(HttpMethod.DELETE, SYSTEMS + "/9007199254740992", admin, null)
            .getStatusCode()
            .value());
    String original = jdbc.queryForObject("SELECT name FROM systems WHERE id=2", String.class);
    for (String field : java.util.List.of("name", "enabled", "connectionStatus")) {
      Map<String, Object> mixed = new java.util.HashMap<>(Map.of("name", "不应保存", "enabled", false));
      mixed.put(field, null);
      assertEquals(400, call(HttpMethod.PATCH, SYSTEMS + "/2", admin, mixed).getStatusCode().value());
      assertEquals(original, jdbc.queryForObject("SELECT name FROM systems WHERE id=2", String.class));
      assertTrue(jdbc.queryForObject("SELECT enabled FROM systems WHERE id=2", Boolean.class));
    }
  }

  /** 撤销自己的管理受众不能令系统永久失去恢复维护入口。 */
  @Test
  @DisplayName("R34 移除RBAC会触发最后维护者保护并完整回滚")
  void R34_RetainManagementApplication() {
    assertEquals(
        409, call(HttpMethod.DELETE, SYSTEMS + "/1", token(1), null).getStatusCode().value());
    assertTrue(jdbc.queryForObject("SELECT enabled FROM systems WHERE id=1", Boolean.class));
  }

  /** 应用管理在数据库筛选分页，原选择器目录不改变响应结构。 */
  @Test
  @DisplayName("R34 应用分页查询正常异常和极大越末页均符合契约")
  void R34_SearchAndPageBoundaries() {
    String admin = token(1);
    JsonNode first =
        call(HttpMethod.GET, SYSTEMS + "/page?page=0&size=1", admin, null).getBody().path("data");
    assertEquals(2, first.path("total").asInt());
    assertEquals(1, first.path("items").size());
    JsonNode beyond =
        call(HttpMethod.GET, SYSTEMS + "/page?page=2147483647&size=100", admin, null)
            .getBody()
            .path("data");
    assertEquals(2, beyond.path("total").asInt());
    assertEquals(0, beyond.path("items").size());
    assertEquals(
        1,
        call(HttpMethod.GET, SYSTEMS + "/page?q=RBAC", admin, null)
            .getBody()
            .path("data")
            .path("total")
            .asInt());
    assertEquals(
        0,
        call(HttpMethod.GET, SYSTEMS + "/page?q=%25", admin, null)
            .getBody()
            .path("data")
            .path("total")
            .asInt());
    for (String query : java.util.List.of("size=0", "size=101", "page=-1", "enabled=yes")) {
      assertEquals(
          400,
          call(HttpMethod.GET, SYSTEMS + "/page?" + query, admin, null).getStatusCode().value());
    }
  }

  /** 接受长度上限和单字段修改，启停筛选与目录当前事实一致。 */
  @Test
  @DisplayName("R34 应用长度上限、单独接入状态及启停筛选可真实往返")
  void R34_ValidBoundariesAndCurrentState() {
    String admin = token(1);
    JsonNode created = call(HttpMethod.POST, SYSTEMS, admin,
        Map.of("code", "a".repeat(100), "name", "名".repeat(191))).getBody().path("data");
    long id = created.path("id").asLong();
    assertEquals(100, created.path("code").asText().length());
    assertEquals(191, created.path("name").asText().length());
    assertEquals(200, call(HttpMethod.PATCH, SYSTEMS + "/" + id, admin,
        Map.of("connectionStatus", "CONNECTED")).getStatusCode().value());
    assertEquals(200, call(HttpMethod.DELETE, SYSTEMS + "/" + id, admin, null).getStatusCode().value());
    JsonNode inactive = call(HttpMethod.GET, SYSTEMS + "/page?enabled=false", admin, null).getBody().path("data");
    assertEquals(1, inactive.path("total").asInt());
    assertEquals(id, inactive.path("items").get(0).path("id").asLong());
    assertEquals(2, call(HttpMethod.GET, SYSTEMS + "/page?enabled=true", admin, null).getBody().path("data").path("total").asInt());
    assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM systems", Integer.class));
  }

  /** 登录资格与某个菜单的实际API各自独立，不凭岗位或角色名称准入。 */
  @Test
  @DisplayName("R08/R09 只授登录可进入空工作区，加单菜单才可查询该菜单")
  void R08_LoginOnlyAndSingleMenu() {
    jdbc.update("DELETE FROM role_permissions WHERE role_id=2");
    jdbc.update(
        "INSERT INTO role_permissions SELECT 2,id FROM permissions WHERE code='rbac.login'");
    String reader = token(2);
    assertEquals(200, call(HttpMethod.GET, ME, reader, null).getStatusCode().value());
    assertEquals(403, call(HttpMethod.GET, USERS, reader, null).getStatusCode().value());
    jdbc.update(
        "INSERT INTO permissions(id,system_id,code,name,type,enabled)"
            + " VALUES(800,1,'single.menu','单个菜单','MENU',1)");
    jdbc.update(
        "INSERT INTO permission_apis SELECT 800,id FROM api_resources WHERE http_method='GET' AND"
            + " path_template='/api/users'");
    jdbc.update("INSERT INTO role_permissions VALUES(2,800)");
    assertEquals(200, call(HttpMethod.GET, USERS, reader, null).getStatusCode().value());
    assertEquals(
        403,
        call(HttpMethod.POST, "/api/roles", reader, Map.of("code", "denied", "name", "不可创建"))
            .getStatusCode()
            .value());
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id=2 AND permission_id IN (SELECT id FROM"
            + " permissions WHERE code='rbac.login')");
    assertEquals(403, call(HttpMethod.GET, ME, reader, null).getStatusCode().value());
  }

  /** 直接核对本人权限响应，逐个断开有效链并恢复，子权限不补授父菜单。 */
  @Test
  @DisplayName("R08/R09 本人权限逐项排除停用角色权限API及应用")
  void R09_ExcludeInactivePermissionChains() {
    String admin = token(1);
    long permission =
        jdbc.queryForObject("SELECT id FROM permissions WHERE code='rbac.users.read'", Long.class);
    long api =
        jdbc.queryForObject(
            "SELECT api_id FROM permission_apis WHERE permission_id=?", Long.class, permission);
    for (String sql :
        java.util.List.of(
            "UPDATE permissions SET enabled=%s WHERE id=" + permission,
            "UPDATE api_resources SET enabled=%s WHERE id=" + api)) {
      jdbc.update(sql.formatted(0));
      assertFalse(ids(admin).contains(permission));
      jdbc.update(sql.formatted(1));
      assertTrue(ids(admin).contains(permission));
    }
    jdbc.update("DELETE FROM role_permissions WHERE role_id=1 AND permission_id=?", permission);
    jdbc.update("INSERT INTO role_permissions VALUES(4,?)", permission);
    jdbc.update("INSERT INTO user_roles VALUES(1,4)");
    jdbc.update("UPDATE roles SET enabled=0 WHERE id=4");
    assertFalse(ids(admin).contains(permission));
    jdbc.update("INSERT INTO role_permissions VALUES(1,?)", permission);
    assertTrue(ids(admin).contains(permission));
    jdbc.update("UPDATE roles SET enabled=1 WHERE id=4");
    jdbc.update("UPDATE systems SET enabled=0 WHERE id=1");
    assertEquals(403, call(HttpMethod.GET, MY_PERMISSIONS, admin, null).getStatusCode().value());
    jdbc.update("UPDATE systems SET enabled=1 WHERE id=1");
    assertTrue(ids(admin).contains(permission));
    jdbc.update(
        "INSERT INTO permissions(id,system_id,code,name,type,enabled)"
            + " VALUES(800,1,'unassigned.parent','未授父菜单','MENU',1)");
    jdbc.update("UPDATE permissions SET parent_id=800 WHERE id=?", permission);
    assertTrue(ids(admin).contains(permission));
    assertFalse(ids(admin).contains(800L));
    jdbc.update("UPDATE permissions SET enabled=0 WHERE id=800");
    assertTrue(ids(admin).contains(permission));
  }

  /** 响应只比较稳定权限ID，不以API通拒代替过滤断言。 */
  private java.util.Set<Long> ids(String token) {
    ResponseEntity<JsonNode> response = call(HttpMethod.GET, MY_PERMISSIONS, token, null);
    assertEquals(200, response.getStatusCode().value());
    java.util.Set<Long> ids = new java.util.HashSet<>();
    response.getBody().path("data").forEach(item -> ids.add(item.path("id").asLong()));
    return ids;
  }

  /** 使用真实账号口令建立测试会话。 */
  private String token(int id) {
    ResponseEntity<JsonNode> response =
        call(
            HttpMethod.POST,
            LOGIN,
            "",
            Map.of("account", "stage4-" + id, "password", ManagementContractFixture.PASSWORD));
    assertEquals(200, response.getStatusCode().value(), String.valueOf(response.getBody()));
    return response.getBody().path("data").path("token").asText();
  }

  /** 统一HTTP信封，断言服务端实际返回而非直调业务方法。 */
  private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (!token.isEmpty()) headers.setBearerAuth(token);
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }
}
