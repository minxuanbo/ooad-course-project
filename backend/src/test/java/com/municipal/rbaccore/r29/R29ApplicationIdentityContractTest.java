package com.municipal.rbaccore.r29;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** R29在真实MySQL及HTTP边界验证课程身份协议、跨系统隔离和即时授权变化。 */
@SpringBootTest(
    classes = RuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.config.name=application-core-resource-test",
      "app.identity.enabled=true",
      "app.identity.operations-file=classpath:identity-test-operations.json",
      "app.identity.allowed-origins=http://127.0.0.1:5174"
    })
@ActiveProfiles("core-resource-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class R29ApplicationIdentityContractTest {
  private static final String LOGIN = "/auth/login";
  private static final String ME = "/auth/me";
  private static final String CHECK = "/auth/permissions/MTR";
  private static final String RBAC_LOGIN = "/api/auth/login";
  private static final String RBAC_ME = "/api/me";
  private static final String SYSTEMS = "/api/systems";
  private static final String ROLE_PERMISSIONS = "/api/roles/2/permissions";
  private static final String PERMISSION_APIS = "/api/permissions/103/apis";
  private static final String PERMISSION_STATE = "/api/permissions/101";
  private static final String API_STATE = "/api/api-resources/100";
  private static final String USER_ROLES = "/api/users/2/roles";
  private static final String USER_LOOKUP = "/auth/users/3";
  private static final String MISSING_USER_LOOKUP = "/auth/users/999999";
  private static final String INVALID_USER_LOOKUP = "/auth/users/0";
  private static final String UNSAFE_USER_LOOKUP = "/auth/users/9007199254740992";
  private static final String UNKNOWN_PERMISSION = "/auth/permissions/CRS";
  private static final String TARGET_CANDIDATES = "/api/users/3/assignable-roles";
  private static final String TARGET_ROLES = "/api/users/3/roles";
  private static final String IDENTITY_API_STATE = "/api/api-resources/105";
  private static final String PASSWORD = "r29-fixture-password";
  private static final String APP_LOGOUT = "/auth/logout";
  private static final String DIRECTORY = "/auth/departments";
  private static final String DEPARTMENT_USERS = "/auth/departments/1/users";
  private static final String API_AUTHORITY = "/auth/api-permissions/createTerm";
  @Autowired
  TestRestTemplate http;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  PasswordEncoder passwords;

  /** 每项只清理明确的本机专用测试库，重建最小可信根及完整维护者。 */
  @BeforeEach
  void R29_PrepareIsolatedFixture() {
    assertEquals("rbac_core_t07_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
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
    jdbc.update(
        "INSERT INTO departments(id,code,name,type) VALUES(1,'r29-root','联调总公司','HEADQUARTERS')");
    jdbc.update(
        "INSERT INTO import_sources(source_type,source_key,source_json,department_id)"
            + " VALUES('ORGANIZATION','总公司','[]',1)");
    jdbc.update(
        "INSERT INTO systems(id,code,name,enabled)"
            + " VALUES(1,'rbac','RBAC',1),(2,'course-project-management','课程项目管理',1),(3,'unapproved','未接入系统',1)");
    jdbc.update(
        "INSERT INTO roles(id,code,name,enabled,management_level)"
            + " VALUES(1,'r29-admin','完整维护者',1,5),(2,'r29-course','课程教师',1,NULL),(3,'r29-ordinary','普通员工',1,1)");
    for (int id = 1; id <= 3; id++) {
      String username = id == 1 ? "r29-admin" : id == 2 ? "r29-employee" : "r29-unassigned";
      jdbc.update(
          "INSERT INTO users(id,username,password_hash,name,department_id,enabled)"
              + " VALUES(?,?,?,?,1,1)",
          id,
          username,
          passwords.encode(PASSWORD),
          "虚构员工" + id);
      jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", id, id);
    }
    long next = 1;
    for (Map.Entry<String, List<RbacCatalog.Route>> entry : RbacCatalog.definitions().entrySet()) {
      long permission = next;
      jdbc.update(
          "INSERT INTO permissions(id,system_id,code,name,type,enabled) VALUES(?,1,?,?,'BUTTON',1)",
          permission,
          entry.getKey(),
          entry.getKey());
      jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES(1,?)", permission);
      for (RbacCatalog.Route route : entry.getValue()) {
        jdbc.update(
            "INSERT INTO api_resources(id,system_id,http_method,path_template,enabled)"
                + " VALUES(?,1,?,?,1)",
            next,
            route.method(),
            route.path());
        jdbc.update(
            "INSERT INTO permission_apis(permission_id,api_id) VALUES(?,?)", permission, next++);
      }
    }
    String[] codes = {"LOGIN", "MTR", "MTRC", "TC", "TE", "TD", "TCM"};
    for (int index = 0; index < codes.length; index++) {
      long id = 100 + index;
      jdbc.update(
          "INSERT INTO permissions(id,system_id,code,name,type,enabled) VALUES(?,2,?,?,'BUTTON',1)",
          id,
          codes[index],
          codes[index]);
      jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES(1,?),(2,?)", id, id);
    }
    jdbc.update("UPDATE permissions SET type='API' WHERE id BETWEEN 103 AND 108");
    String[][] routes = {
      {"POST", "/terms"},
      {"PATCH", "/terms/{termId}"},
      {"DELETE", "/terms/{termId}"},
      {"PUT", "/current-term"},
      {"DELETE", "/current-term"}
    };
    for (int index = 0; index < routes.length; index++) {
      jdbc.update(
          "INSERT INTO api_resources(id,system_id,http_method,path_template,enabled)"
              + " VALUES(?,2,?,?,1)",
          100 + index,
          routes[index][0],
          routes[index][1]);
      jdbc.update(
          "INSERT INTO permission_apis(permission_id,api_id) VALUES(?,?)",
          index < 3 ? 103 + index : 106,
          100 + index);
    }
    jdbc.update(
        "INSERT INTO permissions(id,system_id,code,name,type,enabled)"
            + " VALUES(107,2,'UL','身份查询','BUTTON',1)");
    jdbc.update(
        "INSERT INTO api_resources(id,system_id,http_method,path_template,enabled)"
            + " VALUES(105,2,'GET','/auth/users/{userId}',1)");
    jdbc.update("INSERT INTO permission_apis VALUES(107,105)");
    jdbc.update("INSERT INTO role_permissions VALUES(1,107)");
    jdbc.update(
        "INSERT INTO permissions(id,system_id,code,name,type,enabled)"
            + " VALUES(108,2,'TEC','新增教师','BUTTON',1)");
    jdbc.update(
        "INSERT INTO api_resources(id,system_id,http_method,path_template,enabled)"
            + " VALUES(106,2,'POST','/teachers',1)");
    jdbc.update("INSERT INTO permission_apis VALUES(108,106),(108,105)");
    jdbc.update("INSERT INTO role_permissions VALUES(1,108),(2,108)");
  }

  /** 工号认证与教师准入独立于RBAC管理准入，两个令牌受众不能混用。 */
  @Test
  @DisplayName("R29 课程教师可登录但无RBAC管理准入，身份令牌按系统隔离")
  void R29_CourseLoginDoesNotBypassManagement() {
    String token = courseLogin();
    JsonNode identity = request(ME, HttpMethod.GET, token, null, 200);
    assertEquals("2", identity.path("subject").asText());
    assertEquals("r29-course", identity.path("roles").get(0).asText());
    assertTrue(identity.path("permissions").toString().contains("\"TC\""));
    assertEquals(
        "course-project-management",
        jdbc.queryForObject(
            "SELECT app_code FROM authorization_logs WHERE event_type='LOGIN_SUCCESS' AND"
                + " request_path='/auth/login' ORDER BY id DESC LIMIT 1",
            String.class));
    request(
        RBAC_LOGIN,
        HttpMethod.POST,
        null,
        Map.of("account", "r29-employee", "password", PASSWORD),
        403);
    request(RBAC_ME, HttpMethod.GET, token, null, 401);
    request(ME, HttpMethod.GET, adminLogin(), null, 401);
  }

  /** 真正通过RBAC角色接口撤权，旧课程令牌的下一次查询立即得到最新权限。 */
  @Test
  @DisplayName("R29 RBAC角色写入实时撤回课程权限且保留课程身份")
  void R29_RevocationUsesCurrentRelations() {
    String course = courseLogin();
    request(
        ROLE_PERMISSIONS, HttpMethod.PUT, adminLogin(), Map.of("ids", List.of(100, 101, 102)), 200);
    JsonNode identity = request(ME, HttpMethod.GET, course, null, 200);
    assertFalse(identity.path("permissions").toString().contains("\"TC\""));
    assertEquals("r29-course", identity.path("roles").get(0).asText());
  }

  /** 映射必须属于课程系统，API停用也不能继续导出对应短权限码。 */
  @Test
  @DisplayName("R29 拒绝跨系统映射并按API状态实时关闭课程权限")
  void R29_CrossSystemMappingAndApiState() {
    String course = courseLogin();
    String admin = adminLogin();
    request(PERMISSION_APIS, HttpMethod.PUT, admin, Map.of("ids", List.of(1)), 400);
    assertTrue(
        request(ME, HttpMethod.GET, course, null, 200)
            .path("permissions")
            .toString()
            .contains("\"TC\""));
    request(API_STATE, HttpMethod.PATCH, admin, Map.of("enabled", false), 200);
    assertFalse(
        request(ME, HttpMethod.GET, course, null, 200)
            .path("permissions")
            .toString()
            .contains("\"TC\""));
  }

  /** 不能用同一短码把已停用的一条路由重新放行；菜单检查也必须读取当前值。 */
  @Test
  @DisplayName("R29 多路由短码要求全部映射有效，菜单撤回即时生效")
  void R29_CoarseAuthorityFailsClosed() {
    String course = courseLogin();
    assertEquals(
        2,
        request(ME, HttpMethod.GET, course, null, 200).path("permissionApis").path("TCM").size());
    jdbc.update("UPDATE api_resources SET enabled=0 WHERE id=104");
    assertFalse(
        request(ME, HttpMethod.GET, course, null, 200)
            .path("permissions")
            .toString()
            .contains("\"TCM\""));
    jdbc.update("DELETE FROM permission_apis WHERE permission_id=103");
    assertFalse(
        request(ME, HttpMethod.GET, course, null, 200)
            .path("permissions")
            .toString()
            .contains("\"TC\""));
    assertTrue(request(CHECK, HttpMethod.GET, course, null, 200).path("allowed").asBoolean());
    request(PERMISSION_STATE, HttpMethod.PATCH, adminLogin(), Map.of("enabled", false), 200);
    assertFalse(request(CHECK, HttpMethod.GET, course, null, 200).path("allowed").asBoolean());
  }

  /** 资源目录只增加显式接入的课程系统，未批准系统仍不得通过目录枚举。 */
  @Test
  @DisplayName("R29 正式资源目录精确包含RBAC和课程系统")
  void R29_CatalogReadsRegisteredSystems() {
    JsonNode systems = request(SYSTEMS, HttpMethod.GET, adminLogin(), null, 200).path("data");
    assertEquals(3, systems.size());
    assertTrue(systems.toString().contains("course-project-management"));
    assertTrue(systems.toString().contains("unapproved"));
  }

  /** 错口令、账号停用和未经接入的systems输入均失败关闭。 */
  @Test
  @DisplayName("R29 课程认证拒绝错口令停用账号及伪造系统")
  void R29_InvalidIdentityFailsClosed() {
    request(
        LOGIN,
        HttpMethod.POST,
        null,
        Map.of(
            "username",
            "r29-employee",
            "password",
            "wrong",
            "systems",
            List.of("course-project-management")),
        401);
    request(
        LOGIN,
        HttpMethod.POST,
        null,
        Map.of("username", "r29-employee", "password", PASSWORD, "systems", List.of("rbac")),
        400);
    String token = courseLogin();
    jdbc.update("UPDATE users SET enabled=0 WHERE id=2");
    request(ME, HttpMethod.GET, token, null, 401);
  }

  /** 人员角色替换是真实管理写入，不能靠重新登录或刷新令牌体现撤权。 */
  @Test
  @DisplayName("R29 人员角色撤回和恢复在原课程令牌下一请求生效")
  void R29_PersonRoleChangesAreImmediate() {
    String token = courseLogin();
    String admin = adminLogin();
    request(USER_ROLES, HttpMethod.PUT, admin, Map.of("ids", List.of(3)), 200);
    assertEquals(
        "APPLICATION_ACCESS_DENIED",
        request(ME, HttpMethod.GET, token, null, 403).path("code").asText());
    request(USER_ROLES, HttpMethod.PUT, admin, Map.of("ids", List.of(2)), 200);
    request(ME, HttpMethod.GET, token, null, 200);
  }

  /** 无教师标记和系统停用不能通过普通员工的账号凭据绕过准入。 */
  @Test
  @DisplayName("R29 教师准入、系统启用和未知短码均失败关闭")
  void R29_ApplicationEligibilityAndUnknownPermission() {
    assertEquals(
        "APPLICATION_ACCESS_DENIED",
        request(
                LOGIN,
                HttpMethod.POST,
                null,
                Map.of(
                    "username",
                    "r29-unassigned",
                    "password",
                    PASSWORD,
                    "systems",
                    List.of("course-project-management")),
                403)
            .path("code")
            .asText());
    String token = courseLogin();
    assertFalse(
        request(UNKNOWN_PERMISSION, HttpMethod.GET, token, null, 200).path("allowed").asBoolean());
    jdbc.update("UPDATE systems SET enabled=0 WHERE id=2");
    request(ME, HttpMethod.GET, token, null, 403);
  }

  /** 身份最小投影有独立UL门禁，不允许普通教师枚举全体员工。 */
  @Test
  @DisplayName("R29 身份查询先鉴权，允许后校验不存在主键与最小字段")
  void R29_UserLookupIsPermissionControlled() {
    String token = courseLogin();
    jdbc.update("DELETE FROM role_permissions WHERE role_id=2 AND permission_id=108");
    request(USER_LOOKUP, HttpMethod.GET, token, null, 403);
    jdbc.update("INSERT INTO role_permissions VALUES(2,107)");
    JsonNode user = request(USER_LOOKUP, HttpMethod.GET, token, null, 200);
    assertEquals("r29-unassigned", user.path("no").asText());
    assertEquals("虚构员工3", user.path("name").asText());
    assertEquals(3, user.size());
    request(MISSING_USER_LOOKUP, HttpMethod.GET, token, null, 404);
    request(INVALID_USER_LOOKUP, HttpMethod.GET, token, null, 400);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM authorization_logs WHERE request_path=? AND"
                + " event_type='AUTHORIZATION' AND allowed=1 AND http_status=200",
            Integer.class,
            USER_LOOKUP));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM authorization_logs WHERE request_path=? AND"
                + " event_type='AUTHORIZATION' AND allowed=0 AND http_status=403",
            Integer.class,
            USER_LOOKUP));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM authorization_logs WHERE request_path=? AND"
                + " event_type='AUTHORIZATION' AND allowed=1 AND http_status=404",
            Integer.class,
            MISSING_USER_LOOKUP));
  }

  /** HTTP入口限制JavaScript安全整数，不能让超界Long进入查人用例。 */
  @Test
  @DisplayName("R29 身份用户ID拒绝安全整数上限之外的值")
  void R29_UserIdentityRejectsUnsafeInteger() {
    String token = courseLogin();
    jdbc.update("INSERT INTO role_permissions VALUES(2,107)");
    request(UNSAFE_USER_LOOKUP, HttpMethod.GET, token, null, 400);
  }

  /** 浏览器身份直连只允许配置来源，管理端不会沿用课程CORS规则。 */
  @Test
  @DisplayName("R29 课程前端CORS仅放行显式来源")
  void R29_CorsOnlyAllowsConfiguredOrigin() {
    HttpHeaders headers = new HttpHeaders();
    headers.setOrigin("http://127.0.0.1:5174");
    headers.setAccessControlRequestMethod(HttpMethod.GET);
    headers.setAccessControlRequestHeaders(List.of("authorization"));
    ResponseEntity<String> accepted =
        http.exchange(ME, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
    assertEquals("http://127.0.0.1:5174", accepted.getHeaders().getAccessControlAllowOrigin());
    headers.setOrigin("http://unapproved.invalid");
    assertEquals(
        403,
        http.exchange(ME, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class)
            .getStatusCode()
            .value());
  }

  /** 候选查询和保存同时比较课程权限，未知系统即使停用也不能被过滤为空角色。 */
  @Test
  @DisplayName("R29 人员授予完整子集包含课程权限，未知系统污染仍整组拒绝")
  void R29_AssignmentSubsetIncludesCourseAndRejectsUnknownSystem() {
    jdbc.update(
        "INSERT INTO roles(id,code,name,enabled,management_level)"
            + " VALUES(4,'r29-course-project','课程操作项目经理',1,2),(5,'r29-poison','污染角色',1,2),(6,'r29-manager','部门经理',1,3)");
    jdbc.update(
        "INSERT INTO permissions(id,system_id,code,name,type,enabled)"
            + " VALUES(200,3,'foreign','未知系统权限','BUTTON',0)");
    jdbc.update("INSERT INTO role_permissions VALUES(4,103),(5,200)");
    jdbc.update("INSERT INTO role_permissions SELECT 6,id FROM permissions WHERE system_id=1");
    jdbc.update(
        "INSERT INTO users(id,username,password_hash,name,department_id,enabled)"
            + " VALUES(4,'r29-manager',?,'虚构部门经理',1,1)",
        passwords.encode(PASSWORD));
    jdbc.update("INSERT INTO user_roles VALUES(4,6)");
    String manager =
        request(
                RBAC_LOGIN,
                HttpMethod.POST,
                null,
                Map.of("account", "r29-manager", "password", PASSWORD),
                200)
            .path("data")
            .path("token")
            .asText();
    JsonNode before =
        request(TARGET_CANDIDATES, HttpMethod.GET, manager, null, 200).path("data").path("items");
    assertFalse(containsRole(before, 4));
    assertTrue(containsRole(before, 5));
    request(TARGET_ROLES, HttpMethod.PUT, manager, Map.of("ids", List.of(4)), 403);
    jdbc.update("INSERT INTO role_permissions VALUES(6,103)");
    JsonNode after =
        request(TARGET_CANDIDATES, HttpMethod.GET, manager, null, 200).path("data").path("items");
    assertTrue(containsRole(after, 4));
    assertTrue(containsRole(after, 5));
    request(TARGET_ROLES, HttpMethod.PUT, manager, Map.of("ids", List.of(4)), 200);
    request(TARGET_ROLES, HttpMethod.PUT, adminLogin(), Map.of("ids", List.of(5)), 200);
  }

  /** 检查真实分页候选，不能把显示名称或字符串包含当作角色身份。 */
  private boolean containsRole(JsonNode items, long id) {
    for (JsonNode item : items) {
      if (item.path("id").asLong() == id) return true;
    }
    return false;
  }

  /** 老师新增教师能力也保护身份查询，任一路由停用不能继续导出同一粗粒度短码。 */
  @Test
  @DisplayName("R29 TEC必须同时映射新增教师和老师真实身份查询路由")
  void R29_TeacherCreateCoversItsLookupPrerequisite() {
    String token = courseLogin();
    assertTrue(
        request(ME, HttpMethod.GET, token, null, 200)
            .path("permissions")
            .toString()
            .contains("\"TEC\""));
    request(IDENTITY_API_STATE, HttpMethod.PATCH, adminLogin(), Map.of("enabled", false), 200);
    assertFalse(
        request(ME, HttpMethod.GET, token, null, 200)
            .path("permissions")
            .toString()
            .contains("\"TEC\""));
  }

  /** 新登记应用无须更改代码；角色码和权限码均来自数据，令牌受众不能跨应用。 */
  @Test
  @DisplayName("R29 任意登记应用的身份角色权限与即时停用")
  void R29_RegisteredApplicationIsGeneric() {
    jdbc.update(
        "INSERT INTO permissions(id,system_id,code,name,type,enabled)"
            + " VALUES(300,3,'LOGIN','登录','BUTTON',1)");
    jdbc.update("INSERT INTO roles(id,code,name,enabled) VALUES(7,'student','学生',1)");
    jdbc.update("INSERT INTO role_permissions VALUES(7,300)");
    jdbc.update("INSERT INTO user_roles VALUES(3,7)");
    String token =
        request(
                LOGIN,
                HttpMethod.POST,
                null,
                Map.of(
                    "username",
                    "r29-unassigned",
                    "password",
                    PASSWORD,
                    "systems",
                    List.of("unapproved")),
                200)
            .path("accessToken")
            .asText();
    JsonNode me = request(ME, HttpMethod.GET, token, null, 200);
    assertEquals("student", me.path("roles").get(0).asText());
    assertEquals("LOGIN", me.path("permissions").get(0).asText());
    jdbc.update("DELETE FROM user_roles WHERE user_id=3 AND role_id=7");
    request(ME, HttpMethod.GET, token, null, 403);
  }

  /** R32外部令牌必须是带受众的JWT，不携带授权快照。 */
  @Test
  @DisplayName("R32 外部登录签发JWT身份字段且不携带角色权限")
  void R32_JwtMatchesTeacherContract() throws Exception {
    String[] pieces = courseLogin().split("\\.");
    assertEquals(3, pieces.length, "外部身份必须返回三段JWT");
    com.fasterxml.jackson.databind.ObjectMapper mapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
    JsonNode payload = mapper.readTree(java.util.Base64.getUrlDecoder().decode(pieces[1]));
    JsonNode header = mapper.readTree(java.util.Base64.getUrlDecoder().decode(pieces[0]));
    assertEquals("HS256", header.path("alg").asText());
    assertEquals("2", payload.path("sub").asText());
    JsonNode audience = payload.path("aud");
    assertEquals(
        "course-project-management",
        audience.isArray() ? audience.get(0).asText() : audience.asText());
    if (audience.isArray()) assertEquals(1, audience.size());
    assertFalse(payload.path("iss").asText().isBlank());
    assertFalse(payload.path("jti").asText().isBlank());
    assertEquals(43200, payload.path("exp").asLong() - payload.path("iat").asLong());
    assertFalse(payload.has("roles"));
    assertFalse(payload.has("permissions"));
    assertFalse(payload.has("identity"));
  }

  /** 退出只撤销当前外部身份，不能拿管理端令牌冒充课程身份。 */
  @Test
  @DisplayName("R32 外部退出返回204且原令牌立即失效，其他会话仍有效")
  void R32_LogoutIsAudienceScoped() {
    String first = courseLogin();
    String second = courseLogin();
    request(APP_LOGOUT, HttpMethod.POST, null, null, 401);
    String management = adminLogin();
    request(APP_LOGOUT, HttpMethod.POST, management, null, 401);
    request(RBAC_ME, HttpMethod.GET, management, null, 200);
    request(APP_LOGOUT, HttpMethod.POST, first, null, 204);
    request(ME, HttpMethod.GET, first, null, 401);
    request(ME, HttpMethod.GET, second, null, 200);
  }

  /** 即使授权已撤回，也允许本人销毁尚有效的外部访问令牌。 */
  @Test
  @DisplayName("R32 撤权后仍可退出，不会重新授予应用资格")
  void R32_LogoutAfterRevocation() {
    String token = courseLogin();
    jdbc.update("DELETE FROM user_roles WHERE user_id=2");
    request(ME, HttpMethod.GET, token, null, 403);
    request(APP_LOGOUT, HttpMethod.POST, token, null, 204);
    request(ME, HttpMethod.GET, token, null, 401);
  }

  /** 部门列表只逐层返回，分页从1开始，墓碑与外部父引用不泄漏。 */
  @Test
  @DisplayName("R32 外部部门逐层分页、编码搜索、hasChildren及范围根")
  void R32_DepartmentDirectoryIsLayeredAndScoped() {
    R32_PrepareDirectory();
    String token = courseLogin();
    JsonNode roots = request(DIRECTORY, HttpMethod.GET, token, null, 200);
    assertEquals(1, roots.path("page").asInt());
    assertEquals(20, roots.path("pageSize").asInt());
    assertEquals(1, roots.path("total").asInt());
    assertEquals(1, roots.path("items").get(0).path("id").asInt());
    assertTrue(roots.path("items").get(0).path("hasChildren").asBoolean());
    JsonNode children =
        request(DIRECTORY + "?parentId=1&pageSize=1", HttpMethod.GET, token, null, 200);
    assertEquals(2, children.path("total").asInt());
    assertEquals(1, children.path("items").size());
    JsonNode filtered =
        request(DIRECTORY + "?parentId=1&keyword=r32-b", HttpMethod.GET, token, null, 200);
    assertEquals(3, filtered.path("items").get(0).path("id").asInt());
    jdbc.update("UPDATE users SET department_id=2 WHERE id=2");
    JsonNode local = request(DIRECTORY, HttpMethod.GET, token, null, 200).path("items").get(0);
    assertEquals(2, local.path("id").asInt());
    assertTrue(local.path("parentId").isNull());
    request(DIRECTORY + "?parentId=1", HttpMethod.GET, token, null, 403);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM authorization_logs WHERE request_path=? AND"
                + " event_type='AUTHORIZATION' AND http_status=403 AND allowed=0",
            Integer.class,
            DIRECTORY));
    request(DIRECTORY + "?page=0", HttpMethod.GET, token, null, 400);
    request(DIRECTORY + "?pageSize=101", HttpMethod.GET, token, null, 400);
    assertEquals(
        0,
        request(DIRECTORY + "?page=2147483647&pageSize=100", HttpMethod.GET, token, null, 200)
            .path("items")
            .size());
  }

  /** 部门用户查询不递归、不返回联系方式，越界与失权均拒绝。 */
  @Test
  @DisplayName("R32 部门用户精确直属分页、最小字段、缺失部门及即时撤权")
  void R32_DepartmentUsersAreMinimalAndPermissionControlled() {
    R32_PrepareDirectory();
    String token = courseLogin();
    JsonNode result = request(DEPARTMENT_USERS + "?pageSize=2", HttpMethod.GET, token, null, 200);
    assertEquals(3, result.path("total").asInt());
    assertEquals(2, result.path("items").size());
    JsonNode user = result.path("items").get(0);
    assertEquals(5, user.size());
    assertEquals("r29-admin", user.path("no").asText());
    assertFalse(user.has("phone"));
    assertEquals(
        1,
        request(DEPARTMENT_USERS + "?keyword=r29-employee", HttpMethod.GET, token, null, 200)
            .path("total")
            .asInt());
    request("/auth/departments/999999/users", HttpMethod.GET, token, null, 404);
    jdbc.update("UPDATE api_resources SET enabled=0 WHERE id=111");
    request(DEPARTMENT_USERS, HttpMethod.GET, token, null, 403);
    jdbc.update("UPDATE api_resources SET enabled=1 WHERE id=111");
    jdbc.update("UPDATE users SET department_id=2 WHERE id=2");
    request(DEPARTMENT_USERS, HttpMethod.GET, token, null, 403);
  }

  /** 全局API判定按配置路由读取当前映射，不把对象权限合并进统一身份。 */
  @Test
  @DisplayName("R32 operationId按应用解析，API停用与映射撤回立即影响allowed")
  void R32_ApiAuthorityUsesCurrentMapping() {
    String token = courseLogin();
    JsonNode result = request(API_AUTHORITY, HttpMethod.GET, token, null, 200);
    assertEquals("createTerm", result.path("operationId").asText());
    assertTrue(result.path("allowed").asBoolean());
    assertEquals(2, result.size());
    jdbc.update("UPDATE api_resources SET enabled=0 WHERE id=100");
    assertFalse(
        request(API_AUTHORITY, HttpMethod.GET, token, null, 200).path("allowed").asBoolean());
    jdbc.update("UPDATE api_resources SET enabled=1 WHERE id=100");
    jdbc.update("DELETE FROM permission_apis WHERE api_id=100");
    assertFalse(
        request(API_AUTHORITY, HttpMethod.GET, token, null, 200).path("allowed").asBoolean());
    assertEquals(
        "API_OPERATION_NOT_FOUND",
        request("/auth/api-permissions/missingOperation", HttpMethod.GET, token, null, 404)
            .path("code")
            .asText());
    assertTrue(
        request("/auth/api-permissions/objectOperation", HttpMethod.GET, token, null, 200)
            .path("allowed")
            .asBoolean());
  }

  /** 身份字符串格式合法但本系统没有对应对象时按协议返回不存在。 */
  @Test
  @DisplayName("R32 字符串用户标识不存在时返回协议404而非内部解析异常")
  void R32_UnknownStringIdentityReturnsContractError() {
    String token = courseLogin();
    assertEquals(
        "IDENTITY_USER_NOT_FOUND",
        request("/auth/users/sso-missing", HttpMethod.GET, token, null, 404).path("code").asText());
    request(MISSING_USER_LOOKUP, HttpMethod.GET, token, null, 404);
  }

  /** 伪造JWT任何一个字节都不能作为已签发的有效身份使用。 */
  @Test
  @DisplayName("R32 JWT签名篡改、伪造受众与无签名令牌均拒绝")
  void R32_TamperedJwtIsRejected() {
    String token = courseLogin();
    String[] parts = token.split("\\.");
    assertEquals(3, parts.length);
    request(ME, HttpMethod.GET, parts[0] + "." + parts[1] + ".invalid", null, 401);
    request(ME, HttpMethod.GET, "eyJhbGciOiJub25lIn0.eyJzdWIiOiIyIn0.", null, 401);
    request(ME, HttpMethod.GET, "not-issued", null, 401);
  }

  /** 混合入口的全局门禁按配置角色适用，对象关系由课程服务继续判定。 */
  @Test
  @DisplayName("R32 混合对象入口的条件全局门禁取配置角色且实时读取")
  void R32_MixedOperationKeepsObjectBoundary() {
    String token = courseLogin();
    String path = "/auth/api-permissions/mixedOperation";
    assertTrue(request(path, HttpMethod.GET, token, null, 200).path("allowed").asBoolean());
    jdbc.update("UPDATE api_resources SET enabled=0 WHERE id=104");
    assertFalse(request(path, HttpMethod.GET, token, null, 200).path("allowed").asBoolean());
    jdbc.update("UPDATE roles SET code='object-actor' WHERE id=2");
    assertTrue(request(path, HttpMethod.GET, token, null, 200).path("allowed").asBoolean());
  }

  /** 独立准备目录授权及不同部门人员，不改变旧R29用例夹具。 */
  private void R32_PrepareDirectory() {
    jdbc.update(
        "INSERT INTO departments(id,code,name,parent_id,type)"
            + " VALUES(2,'r32-a','本部部门',1,'DEPARTMENT'),(3,'r32-b','分公司',1,'COMPANY'),(4,'r32-project','部门项目',2,'PROJECT')");
    jdbc.update(
        "INSERT INTO departments(id,code,name,parent_id,type,deleted)"
            + " VALUES(5,'r32-deleted','墓碑部门',1,'DEPARTMENT',1)");
    for (int id = 110; id <= 111; id++) {
      jdbc.update(
          "INSERT INTO permissions(id,system_id,code,name,type,enabled) VALUES(?,2,?,?,'API',1)",
          id,
          "DIR" + id,
          "目录查询" + id);
      jdbc.update(
          "INSERT INTO api_resources(id,system_id,http_method,path_template,enabled)"
              + " VALUES(?,2,'GET',?,1)",
          id,
          id == 110 ? DIRECTORY : "/auth/departments/{departmentId}/users");
      jdbc.update("INSERT INTO permission_apis VALUES(?,?)", id, id);
      jdbc.update("INSERT INTO role_permissions VALUES(1,?),(2,?)", id, id);
    }
    jdbc.update(
        "INSERT INTO users(id,username,password_hash,name,department_id,enabled)"
            + " VALUES(4,'r32-child',?,'子部门员工',2,1)",
        passwords.encode(PASSWORD));
  }

  /** 从老师既有登录协议取得课程应用受众JWT。 */
  private String courseLogin() {
    return request(
            LOGIN,
            HttpMethod.POST,
            null,
            Map.of(
                "username",
                "r29-employee",
                "password",
                PASSWORD,
                "systems",
                List.of("course-project-management")),
            200)
        .path("accessToken")
        .asText();
  }

  /** 管理令牌仍必须走原RBAC准入入口。 */
  private String adminLogin() {
    return request(
            RBAC_LOGIN,
            HttpMethod.POST,
            null,
            Map.of("account", "r29-admin", "password", PASSWORD),
            200)
        .path("data")
        .path("token")
        .asText();
  }

  /** 只报告状态和安全错误，断言中不输出请求口令或令牌。 */
  private JsonNode request(
      String path, HttpMethod method, String token, Object body, int expected) {
    HttpHeaders headers = new HttpHeaders();
    if (token != null) headers.setBearerAuth(token);
    ResponseEntity<JsonNode> response =
        http.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    assertEquals(expected, response.getStatusCode().value(), path);
    return response.getBody();
  }
}
