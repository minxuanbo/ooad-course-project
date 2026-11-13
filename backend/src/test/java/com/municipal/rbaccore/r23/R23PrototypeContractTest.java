package com.municipal.rbaccore.r23;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.municipal.rbaccore.config.RuntimeConfiguration;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.util.List;
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

/** R23—R28获批原型的真实MySQL及HTTP增量契约。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-user-test")
@ActiveProfiles("core-user-test")
class R23PrototypeContractTest {
  private static final String LOGIN = "/api/auth/login";
  private static final String DEPARTMENTS = "/api/departments";
  private static final String USERS = "/api/users";
  private static final String PASSWORD = "isolated-stage4-fixture";
  @Autowired TestRestTemplate http;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;

  /** 专库内构造独立根管理员、部门经理和跨系统无API权限，不触及业务库。 */
  @BeforeEach
  void prepare() {
    assertEquals("rbac_core_t11_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
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
            "systems")) jdbc.update("DELETE FROM " + table);
    jdbc.update("UPDATE departments SET parent_id=NULL");
    jdbc.update("DELETE FROM departments");
    jdbc.update("INSERT INTO departments(id,code,name) VALUES(1,'root','总公司')");
    jdbc.update(
        "INSERT INTO departments(id,code,name,parent_id)"
            + " VALUES(2,'dept','工程部',1),(3,'project','项目',2)");
    jdbc.update(
        "INSERT INTO import_sources(source_type,source_key,source_json,department_id)"
            + " VALUES('ORGANIZATION','总公司','[]',1)");
    jdbc.update(
        "INSERT INTO systems(id,code,name,enabled)"
            + " VALUES(1,'rbac','RBAC',1),(2,'business','业务系统',1)");
    jdbc.update(
        "INSERT INTO roles(id,code,name,enabled)"
            + " VALUES(1,'system-admin','管理员',1),(2,'department-manager','经理',1),(3,'employee','员工',1),(4,'custom','自定义',1)");
    jdbc.update(
        "UPDATE roles SET management_level=CASE id WHEN 1 THEN 5 WHEN 2 THEN 3 WHEN 3 THEN 1 ELSE"
            + " NULL END");
    jdbc.update(
        "UPDATE departments SET type=CASE id WHEN 1 THEN 'HEADQUARTERS' WHEN 3 THEN 'PROJECT' ELSE"
            + " 'DEPARTMENT' END");
    long next = 1;
    for (Map.Entry<String, List<RbacCatalog.Route>> entry : RbacCatalog.definitions().entrySet()) {
      long permission = next++;
      jdbc.update(
          "INSERT INTO permissions(id,system_id,code,name,type,enabled) VALUES(?,1,?,?,'BUTTON',1)",
          permission,
          entry.getKey(),
          entry.getKey());
      jdbc.update("INSERT INTO role_permissions VALUES(1,?)", permission);
      if (RbacCatalog.PERSONNEL.contains(entry.getKey()))
        jdbc.update("INSERT INTO role_permissions VALUES(2,?)", permission);
      for (RbacCatalog.Route route : entry.getValue()) {
        long api = next++;
        jdbc.update(
            "INSERT INTO api_resources(id,system_id,http_method,path_template,enabled)"
                + " VALUES(?,1,?,?,1)",
            api,
            route.method(),
            route.path());
        jdbc.update("INSERT INTO permission_apis VALUES(?,?)", permission, api);
      }
    }
    jdbc.update(
        "INSERT INTO permissions(id,system_id,code,name,type,enabled)"
            + " VALUES(999,2,'sensitive','业务操作','BUTTON',1)");
    jdbc.update("INSERT INTO role_permissions VALUES(3,999)");
    for (int id : List.of(1, 2, 3, 4))
      jdbc.update(
          "INSERT INTO users(id,username,password_hash,name,department_id,enabled,position)"
              + " VALUES(?,?,?,?,?,1,'只读职位')",
          id,
          "stage4-" + id,
          encoder.encode(PASSWORD),
          "人员" + id,
          id == 1 ? 1 : 2);
    jdbc.update("INSERT INTO user_roles VALUES(1,1),(2,2),(3,2),(4,3)");
  }

  /** 无API映射不代表没有权限，普通员工角色不可越过跨系统权限子集。 */
  @Test
  @DisplayName("R24 跨系统无API权限仍限制角色候选")
  void R24_AllSystemsSubset() {
    JsonNode response =
        call(HttpMethod.GET, USERS + "/4/assignable-roles?page=0&size=20", token(2), null)
            .getBody();
    assertNotNull(response);
    assertFalse(
        response.path("data").path("items").findValuesAsText("code").contains("employee"),
        response.toString());
    assertFalse(
        response.path("data").path("items").findValuesAsText("code").contains("custom"),
        response.toString());
  }

  /** 同级人员角色、停用和调动都只读，不能利用不同写入口绕过。 */
  @Test
  @DisplayName("R24 同级人员所有写操作拒绝")
  void R24_PeerProtection() {
    String token = token(2);
    assertEquals(
        403,
        call(HttpMethod.PATCH, USERS + "/3/status", token, Map.of("enabled", false))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        call(HttpMethod.PUT, USERS + "/3/roles", token, Map.of("ids", List.of()))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        call(HttpMethod.PUT, USERS + "/3/department", token, Map.of("departmentId", 3))
            .getStatusCode()
            .value());
  }

  /** 部门写接口需真实落库并体现层级类型、统计与根保护。 */
  @Test
  @DisplayName("R23 部门新增改名删除与范围总览")
  void R23_DepartmentLifecycle() {
    String token = token(1);
    ResponseEntity<JsonNode> created =
        call(
            HttpMethod.POST,
            DEPARTMENTS,
            token,
            Map.of("parentId", 2, "name", " 新项目 ", "type", "PROJECT"));
    assertEquals(200, created.getStatusCode().value(), String.valueOf(created.getBody()));
    long id = created.getBody().path("data").path("id").asLong();
    assertTrue(id > 0);
    assertEquals("新项目", created.getBody().path("data").path("name").asText());
    assertEquals("PROJECT", created.getBody().path("data").path("type").asText());
    assertEquals(
        200,
        call(HttpMethod.PATCH, DEPARTMENTS + "/" + id, token, Map.of("name", "改名项目"))
            .getStatusCode()
            .value());
    assertEquals(
        200, call(HttpMethod.DELETE, DEPARTMENTS + "/" + id, token, null).getStatusCode().value());
    assertEquals(
        409, call(HttpMethod.DELETE, DEPARTMENTS + "/1", token, null).getStatusCode().value());
    JsonNode overview = call(HttpMethod.GET, "/api/overview", token, null).getBody().path("data");
    assertEquals(4, overview.path("totalUsers").asInt());
    assertEquals(3, overview.path("totalDepartments").asInt());
  }

  /** 部门树的每项总数满足直属加直接子树，职位与停用角色分别展示。 */
  @Test
  @DisplayName("R25/R26 人数聚合及人员摘要使用真实资料")
  void R26_SummariesAndCounts() {
    String admin = token(1);
    jdbc.update("UPDATE roles SET enabled=0 WHERE id=3");
    JsonNode list =
        call(HttpMethod.GET, USERS + "?page=0&size=20", admin, null).getBody().path("data");
    JsonNode employee = list.path("items").get(3);
    assertEquals("只读职位", employee.path("position").asText());
    assertTrue(employee.path("enabled").asBoolean());
    assertFalse(employee.path("roles").get(0).path("enabled").asBoolean());
    JsonNode tree = call(HttpMethod.GET, DEPARTMENTS, admin, null).getBody().path("data");
    assertEquals(4, tree.get(0).path("totalUsers").asInt());
    assertEquals(1, tree.get(0).path("directUsers").asInt());
    assertEquals(3, tree.get(1).path("totalUsers").asInt());
    assertEquals(3, tree.get(1).path("directUsers").asInt());
    assertEquals(0, tree.get(2).path("totalUsers").asInt());
    JsonNode identity =
        call(HttpMethod.GET, "/api/me", admin, null).getBody().path("data").path("user");
    assertEquals(5, identity.path("managementLevel").asInt());
    assertTrue(identity.path("globalScope").asBoolean());
    assertEquals(25, identity.path("routes").size());
  }

  /** 级别与权限子集叠加，授为本级后按新级别保护；自定义角色停用仍保护持有人。 */
  @Test
  @DisplayName("R23/R27 授予上限和目标级别保护不因停用自定义角色绕过")
  void R27_GrantLevelAndCustomProtection() {
    String manager = token(2);
    jdbc.update("DELETE FROM role_permissions WHERE role_id=3");
    assertEquals(
        403,
        call(HttpMethod.PUT, USERS + "/4/roles", manager, Map.of("ids", List.of(1)))
            .getStatusCode()
            .value());
    assertEquals(
        200,
        call(HttpMethod.PUT, USERS + "/4/roles", manager, Map.of("ids", List.of(2)))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        call(HttpMethod.PUT, USERS + "/4/roles", manager, Map.of("ids", List.of()))
            .getStatusCode()
            .value());
    jdbc.update("DELETE FROM user_roles WHERE user_id=4");
    jdbc.update("INSERT INTO user_roles VALUES(4,4)");
    jdbc.update("UPDATE roles SET enabled=0 WHERE id=4");
    assertEquals(
        403,
        call(HttpMethod.PATCH, USERS + "/4/status", manager, Map.of("enabled", false))
            .getStatusCode()
            .value());
    assertEquals(
        200,
        call(HttpMethod.PUT, USERS + "/4/roles", token(1), Map.of("ids", List.of()))
            .getStatusCode()
            .value());
  }

  /** 严格下级、合法类型、空节点和同级名称规则由服务端校验，未知ID及匿名也覆盖。 */
  @Test
  @DisplayName("R24 部门维护错误码及局部范围独立于页面按钮")
  void R24_DepartmentBoundaries() {
    String admin = token(1), manager = token(2);
    assertEquals(401, call(HttpMethod.POST, DEPARTMENTS, "", Map.of()).getStatusCode().value());
    assertEquals(
        403,
        call(HttpMethod.PATCH, DEPARTMENTS + "/2", manager, Map.of("name", "本人部门"))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        call(HttpMethod.PATCH, DEPARTMENTS + "/1", manager, Map.of("name", "上级部门"))
            .getStatusCode()
            .value());
    assertEquals(
        404,
        call(HttpMethod.PATCH, DEPARTMENTS + "/99999", admin, Map.of("name", "未知"))
            .getStatusCode()
            .value());
    assertEquals(
        "DEPARTMENT_NOT_EMPTY",
        call(HttpMethod.DELETE, DEPARTMENTS + "/2", admin, null).getBody().path("code").asText());
    assertEquals(
        "DEPARTMENT_TYPE_INVALID",
        call(
                HttpMethod.POST,
                DEPARTMENTS,
                manager,
                Map.of("parentId", 2, "name", "非法分公司", "type", "COMPANY"))
            .getBody()
            .path("code")
            .asText());
    assertEquals(
        "DEPARTMENT_TYPE_INVALID",
        call(
                HttpMethod.POST,
                DEPARTMENTS,
                admin,
                Map.of("parentId", 3, "name", "项目下级", "type", "PROJECT"))
            .getBody()
            .path("code")
            .asText());
    assertEquals(
        "DEPARTMENT_NAME_CONFLICT",
        call(
                HttpMethod.POST,
                DEPARTMENTS,
                manager,
                Map.of("parentId", 2, "name", " 项目 ", "type", "PROJECT"))
            .getBody()
            .path("code")
            .asText());
    assertEquals(
        400,
        call(
                HttpMethod.POST,
                DEPARTMENTS,
                manager,
                Map.of("parentId", 2, "name", " ", "type", "PROJECT"))
            .getStatusCode()
            .value());
    assertEquals(
        400,
        call(HttpMethod.PATCH, DEPARTMENTS + "/3", manager, Map.of("name", "项目", "parentId", 1))
            .getStatusCode()
            .value());
    assertEquals(
        200,
        call(HttpMethod.PATCH, DEPARTMENTS + "/3", manager, Map.of("name", "项目改名"))
            .getStatusCode()
            .value());
    assertEquals(
        200, call(HttpMethod.DELETE, DEPARTMENTS + "/3", manager, null).getStatusCode().value());
    assertEquals(
        404,
        call(HttpMethod.PATCH, DEPARTMENTS + "/3", admin, Map.of("name", "墓碑"))
            .getStatusCode()
            .value());
  }

  /** 通过真实登录取得令牌，不用绕过鉴权的测试身份。 */
  private String token(int id) {
    ResponseEntity<JsonNode> result =
        call(HttpMethod.POST, LOGIN, "", Map.of("account", "stage4-" + id, "password", PASSWORD));
    assertEquals(200, result.getStatusCode().value(), String.valueOf(result.getBody()));
    return result.getBody().path("data").path("token").asText();
  }

  /** 统一HTTP封装，保留稳定错误响应供契约断言。 */
  private ResponseEntity<JsonNode> call(HttpMethod method, String path, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    return http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
  }
}
