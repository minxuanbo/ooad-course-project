package com.municipal.rbaccore.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbac.RequestAuditFilter;
import com.municipal.rbaccore.model.*;
import com.municipal.rbaccore.repository.*;
import com.municipal.rbaccore.security.RbacCatalog;
import com.municipal.rbaccore.service.EffectivePermissionService;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** T09通过真实MySQL和随机端口HTTP核对角色维护、即时授权和原子保护。 */
@SpringBootTest(
    classes = CoreRuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-role-test")
@ActiveProfiles("core-role-test")
@Import(CoreRolesHttpT09Test.LockFixtures.class)
class CoreRolesHttpT09Test {
  private static final String LOGIN = "/api/auth/login";
  private static final String ROLES = "/api/roles";
  private static final String USERS = "/api/users";
  private static final String LOGS = "/api/authorization-logs";
  private static final String TREE = "/api/permissions/tree";
  private static final String PERMISSIONS = "/api/permissions";
  private static final String PASSWORD = "t09-isolated-fixture-password";
  @Autowired TestRestTemplate http;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder encoder;
  @Autowired DepartmentRepository departments;
  @Autowired UserRepository users;
  @Autowired RoleRepository roles;
  @Autowired PermissionRepository permissions;
  @Autowired ApiResourceRepository apis;
  @Autowired ApplicationSystemRepository systems;
  @Autowired PlatformTransactionManager transactions;
  @Autowired EffectivePermissionService effective;
  @Autowired LockObservation observedLock;
  private long systemId;
  private long rootId;
  private long childId;
  private long adminRoleId;
  private final Map<String, Long> permissionIds = new LinkedHashMap<>();

  /** 并发同步只观察真实锁入口，不替换数据库或鉴权实现。 */
  @TestConfiguration(proxyBeanMethods = false)
  static class LockFixtures {
    /** 提供HTTP通过AOP后即将取得共同根锁的确定同步点。 */
    @Bean
    LockObservation lockObservation() {
      return new LockObservation();
    }
  }

  /** 锁观察器避免测试依靠固定睡眠碰运气。 */
  @Aspect
  static class LockObservation {
    private final AtomicReference<CountDownLatch> entered = new AtomicReference<>();

    /** 通知测试线程后继续执行实际根锁。 */
    @Before("execution(* com.municipal.rbaccore.service.MaintenanceGuard.lock(..))")
    public void beforeLock() {
      CountDownLatch latch = entered.get();
      if (latch != null && RequestAuditFilter.current() != null) latch.countDown();
    }
  }

  /** 每例从可丢弃专库重建少量真实关联，不使用生产初始化或旧业务数据。 */
  @BeforeEach
  void t09Prepare() {
    assertEquals("rbac_core_t09_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    jdbc.execute("DROP TRIGGER IF EXISTS t09_fail_permission");
    jdbc.execute("DROP TRIGGER IF EXISTS t09_fail_role");
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
            "systems")) jdbc.update("DELETE FROM " + table);
    jdbc.update("UPDATE departments SET parent_id=NULL");
    jdbc.update("DELETE FROM departments");
    permissionIds.clear();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              Department root = departments.save(new Department("t09-root", "核实根部门"));
              Department child = new Department("t09-child", "测试子部门");
              child.moveTo(root);
              departments.save(child);
              rootId = root.getId();
              childId = child.getId();
              ApplicationSystem system = systems.save(new ApplicationSystem("rbac", "权限管理系统"));
              systemId = system.getId();
              Permission menu =
                  permissions.save(
                      new Permission(system, "rbac.workspace", "父菜单", Permission.Type.MENU));
              permissionIds.put("rbac.workspace", menu.getId());
              for (Map.Entry<String, List<RbacCatalog.Route>> entry :
                  RbacCatalog.definitions().entrySet()) {
                Permission permission =
                    new Permission(system, entry.getKey(), entry.getKey(), Permission.Type.BUTTON);
                permission.moveTo(menu);
                for (RbacCatalog.Route route : entry.getValue()) {
                  permission.mapApi(
                      apis.save(new ApiResource(system, route.method(), route.path())));
                }
                permissions.save(permission);
                permissionIds.put(entry.getKey(), permission.getId());
              }
              jdbc.update(
                  "INSERT INTO import_sources(source_type,source_key,source_json,department_id)"
                      + " VALUES('ORGANIZATION','总公司','[]',?)",
                  rootId);
            });
    adminRoleId = account("t09-admin", rootId, new ArrayList<>(permissionIds.values()));
  }

  /** 必要定义操作与直接权限读回遵守契约，无额外角色字段。 */
  @Test
  @DisplayName("T09 新建空角色、改名启停和权限保存读回")
  void t09RoleLifecycle() throws Exception {
    String admin = token("t09-admin");
    JsonNode created =
        success(request("POST", ROLES, admin, Map.of("code", " new-role ", "name", " 新角色 ")), 201);
    long id = created.get("id").asLong();
    assertEquals("new-role", created.get("code").asText());
    assertEquals("新角色", created.get("name").asText());
    assertTrue(created.get("enabled").asBoolean());
    assertEquals(4, created.size());
    assertEquals(0, success(request("GET", selected(id), admin, null), 200).get("ids").size());
    JsonNode renamed = success(request("PATCH", role(id), admin, Map.of("name", " 更名 ")), 200);
    assertEquals("更名", renamed.get("name").asText());
    assertTrue(renamed.get("enabled").asBoolean());
    assertEquals("new-role", renamed.get("code").asText());
    success(request("PATCH", role(id), admin, Map.of("enabled", false)), 200);
    List<Long> ids =
        List.of(permissionIds.get("rbac.logs.read"), permissionIds.get("rbac.users.read"));
    JsonNode saved = success(request("PUT", selected(id), admin, Map.of("ids", ids)), 200);
    assertEquals(id, saved.get("roleId").asLong());
    assertEquals(ids.stream().sorted().toList(), savedIds(id));
    success(request("PUT", selected(id), admin, Map.of("ids", ids)), 200);
    assertEquals(2, savedIds(id).size());
    assertEquals(saved, success(request("GET", selected(id), admin, null), 200));
    success(request("PUT", selected(id), admin, Map.of("ids", List.of())), 200);
    assertTrue(savedIds(id).isEmpty());
  }

  /** 相同筛选控制总数与页面，百分号和下划线视为普通查询文字。 */
  @Test
  @DisplayName("T09 角色分页、AND筛选、大小写搜索和极大越末页")
  void t09SearchAndPages() throws Exception {
    String admin = token("t09-admin");
    for (String code : List.of("Alpha_1", "alpha%2", "Beta")) create(admin, code, "显示" + code);
    long disabled = jdbc.queryForObject("SELECT id FROM roles WHERE code='Alpha_1'", Long.class);
    success(request("PATCH", role(disabled), admin, Map.of("enabled", false)), 200);
    JsonNode first = success(request("GET", ROLES + "?q=ALPHA&size=1", admin, null), 200);
    JsonNode next = success(request("GET", ROLES + "?q=ALPHA&size=1&page=1", admin, null), 200);
    assertEquals(2, first.get("total").asInt());
    assertEquals(1, next.get("items").size());
    assertTrue(
        first.get("items").get(0).get("id").asLong() < next.get("items").get(0).get("id").asLong());
    assertEquals(
        1,
        success(request("GET", ROLES + "?q=alpha&enabled=false", admin, null), 200)
            .get("total")
            .asInt());
    for (String q : List.of("%25", "_")) {
      assertEquals(
          1, success(request("GET", ROLES + "?q=" + q, admin, null), 200).get("total").asInt());
    }
    assertEquals(
        2,
        success(
                request(
                    "GET",
                    ROLES + "?q=" + "%20".repeat(60) + "AlPhA" + "%20".repeat(60),
                    admin,
                    null),
                200)
            .get("total")
            .asInt());
    JsonNode beyond =
        success(request("GET", ROLES + "?page=2147483647&size=100", admin, null), 200);
    assertEquals(4, beyond.get("total").asInt());
    assertEquals(0, beyond.get("items").size());
    assertEquals(
        0, success(request("GET", ROLES + "?q=absent", admin, null), 200).get("total").asInt());
  }

  /** 实际MySQL二进制排序规则和唯一索引共同决定编码相等。 */
  @Test
  @DisplayName("T09 编码trim判重与MySQL大小写重音语义一致")
  void t09CodeUniqueness() throws Exception {
    String admin = token("t09-admin");
    for (String code : List.of("Cafe", "cafe", "café")) create(admin, code, code);
    assertError(
        request("POST", ROLES, admin, Map.of("code", " Cafe ", "name", "不能覆盖")),
        409,
        "DATA_CONFLICT");
    assertEquals(4, roles.count());
    assertEquals(
        "Cafe", jdbc.queryForObject("SELECT name FROM roles WHERE code='Cafe'", String.class));
    assertEquals(
        "utf8mb4_0900_bin",
        jdbc.queryForObject(
            "SELECT COLLATION_NAME FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE()"
                + " AND TABLE_NAME='roles' AND COLUMN_NAME='code'",
            String.class));
  }

  /** 只有规定字段和严格类型可以进入Service，null和空PATCH均拒绝。 */
  @Test
  @DisplayName("T09 拒绝非法定义、未知字段及隐式类型转换")
  void t09RejectsInvalidDefinitions() throws Exception {
    String admin = token("t09-admin");
    long target = create(admin, "target", "初始名称");
    for (Object body :
        List.of(
            Map.of(),
            Map.of("code", "x"),
            Map.of("code", " ", "name", "n"),
            Map.of("code", "x".repeat(101), "name", "n"),
            Map.of("code", 123, "name", "n"),
            Map.of("code", "x", "name", "n", "enabled", false))) {
      assertError(request("POST", ROLES, admin, body), 400, "INVALID_ARGUMENT");
    }
    for (Object body :
        List.of(
            Map.of(),
            Map.of("name", " "),
            Map.of("name", "x".repeat(101)),
            Map.of("enabled", "true"),
            Map.of("enabled", 1),
            Map.of("code", "changed"),
            Map.of("name", "n", "id", target),
            Map.of("nonEmptyPatch", true),
            Collections.singletonMap("name", null),
            Collections.singletonMap("enabled", null))) {
      assertError(request("PATCH", role(target), admin, body), 400, "INVALID_ARGUMENT");
    }
    assertEquals(
        "初始名称", jdbc.queryForObject("SELECT name FROM roles WHERE id=?", String.class, target));
    for (String path :
        List.of(
            ROLES + "?page=-1",
            ROLES + "?page=1.5",
            ROLES + "?size=0",
            ROLES + "?size=101",
            ROLES + "?enabled=1",
            ROLES + "?enabled=TRUE",
            ROLES + "?q=" + "x".repeat(101),
            selected(0),
            selected(9007199254740992L),
            ROLES + "/1.5/permissions")) {
      assertError(request("GET", path, admin, null), 400, "INVALID_ARGUMENT");
    }
    for (String method : List.of("GET", "PUT", "PATCH")) {
      String path = method.equals("PATCH") ? role(99999999) : selected(99999999);
      Object body =
          method.equals("GET")
              ? null
              : method.equals("PUT") ? Map.of("ids", List.of()) : Map.of("name", "new");
      assertError(request(method, path, admin, body), 404, "RESOURCE_NOT_FOUND");
    }
  }

  /** 关联数组先完整验证，已停用项可读可移除但不能重新授予。 */
  @Test
  @DisplayName("T09 非法权限数组及停用权限不产生部分替换")
  void t09RejectsInvalidPermissionSets() throws Exception {
    String admin = token("t09-admin");
    long userRead = permissionIds.get("rbac.users.read");
    long retired = permissionIds.get("rbac.logs.read");
    long target = account("t09-target", rootId, List.of(userRead, retired));
    jdbc.update("UPDATE permissions SET enabled=0 WHERE id=?", retired);
    assertEquals(2, success(request("GET", selected(target), admin, null), 200).get("ids").size());
    for (Object body :
        List.of(
            Map.of(),
            Collections.singletonMap("ids", null),
            Map.of("ids", 1),
            Map.of("ids", List.of(userRead, userRead)),
            Map.of("ids", Arrays.asList(userRead, null)),
            Map.of("ids", List.of("1")),
            Map.of("ids", List.of(1.5)),
            Map.of("ids", List.of(-1)),
            Map.of("ids", List.of(userRead, 99999999L)),
            Map.of("ids", List.of(retired)),
            Map.of("ids", List.of(), "uniqueIds", true))) {
      assertError(request("PUT", selected(target), admin, body), 400, "INVALID_ARGUMENT");
      assertEquals(List.of(userRead, retired).stream().sorted().toList(), savedIds(target));
    }
    success(request("PUT", selected(target), admin, Map.of("ids", List.of(userRead))), 200);
    assertEquals(List.of(userRead), savedIds(target));
  }

  /** 真实外系统关联不能经本票整体替换被静默清除。 */
  @Test
  @DisplayName("T09 跨系统选权拒绝且保留目标已有外系统关系")
  void t09PreservesForeignAssignments() throws Exception {
    long foreign =
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  ApplicationSystem system =
                      systems.save(new ApplicationSystem("other", "外系统测试夹具"));
                  return permissions
                      .save(new Permission(system, "other.read", "外系统权限", Permission.Type.MENU))
                      .getId();
                });
    String admin = token("t09-admin");
    long local = account("t09-local", rootId, List.of(permissionIds.get("rbac.users.read")));
    List<Long> original = savedIds(local);
    assertError(
        request("PUT", selected(local), admin, Map.of("ids", List.of(foreign))),
        400,
        "INVALID_ARGUMENT");
    assertEquals(original, savedIds(local));
    long mixed =
        account("t09-mixed", rootId, List.of(foreign, permissionIds.get("rbac.users.read")));
    List<Long> mixedOriginal = savedIds(mixed);
    assertError(
        request("PUT", selected(mixed), admin, Map.of("ids", List.of())), 400, "INVALID_ARGUMENT");
    assertEquals(mixedOriginal, savedIds(mixed));
  }

  /** 登录令牌不携带授权快照，保存和清空立即改变下一次真实请求。 */
  @Test
  @DisplayName("T09 授权撤销立即影响HTTP与再次登录，多角色共享权限")
  void t09ChangesActualAuthorization() throws Exception {
    String admin = token("t09-admin");
    long target = account("t09-employee", childId, List.of());
    assertError(login("t09-employee"), 403, "MANAGEMENT_ACCESS_DENIED");
    long userRead = permissionIds.get("rbac.users.read");
    success(request("PUT", selected(target), admin, Map.of("ids", List.of(userRead))), 200);
    String employee = token("t09-employee");
    assertEquals(200, request("GET", USERS, employee, null).getStatusCode().value());
    assertError(request("GET", LOGS, employee, null), 403, "PERMISSION_DENIED");
    success(
        request(
            "PUT",
            selected(target),
            admin,
            Map.of("ids", List.of(userRead, permissionIds.get("rbac.logs.read")))),
        200);
    assertError(request("GET", LOGS, employee, null), 403, "MANAGEMENT_SCOPE_DENIED");
    success(request("PUT", selected(target), admin, Map.of("ids", List.of())), 200);
    assertError(request("GET", USERS, employee, null), 403, "PERMISSION_DENIED");
    assertError(login("t09-employee"), 403, "MANAGEMENT_ACCESS_DENIED");
    assertEquals(200, request("GET", USERS, admin, null).getStatusCode().value());
    success(
        request(
            "PUT",
            selected(target),
            admin,
            Map.of("ids", List.of(permissionIds.get("rbac.workspace")))),
        200);
    assertError(login("t09-employee"), 403, "MANAGEMENT_ACCESS_DENIED");
  }

  /** 停用保留关联并阻止登录，启用恢复按当前有效链判断。 */
  @Test
  @DisplayName("T09 角色启停和停用期间配权均使用最新状态")
  void t09RoleEnablement() throws Exception {
    String admin = token("t09-admin");
    long target = account("t09-switch", rootId, List.of(permissionIds.get("rbac.users.read")));
    String member = token("t09-switch");
    success(request("PATCH", role(target), admin, Map.of("enabled", false)), 200);
    assertError(request("GET", USERS, member, null), 403, "PERMISSION_DENIED");
    assertError(login("t09-switch"), 403, "MANAGEMENT_ACCESS_DENIED");
    success(
        request(
            "PUT",
            selected(target),
            admin,
            Map.of("ids", List.of(permissionIds.get("rbac.logs.read")))),
        200);
    assertError(login("t09-switch"), 403, "MANAGEMENT_ACCESS_DENIED");
    success(request("PATCH", role(target), admin, Map.of("enabled", true)), 200);
    assertEquals(200, request("GET", LOGS, member, null).getStatusCode().value());
    assertError(request("GET", USERS, member, null), 403, "PERMISSION_DENIED");
  }

  /** 修改自己最后一组维护授权时必须回滚整个角色变更，包括名称。 */
  @Test
  @DisplayName("T09 最后完整管理员的角色状态和权限受同一保护")
  void t09ProtectsLastAdministrator() throws Exception {
    String admin = token("t09-admin");
    assertError(
        request("PATCH", role(adminRoleId), admin, Map.of("enabled", false, "name", "不得保存")),
        409,
        "LAST_ADMIN_REQUIRED");
    assertEquals(
        "t09-admin",
        jdbc.queryForObject("SELECT name FROM roles WHERE id=?", String.class, adminRoleId));
    List<Long> original = savedIds(adminRoleId);
    List<Long> partial =
        original.stream()
            .filter(id -> !id.equals(permissionIds.get("rbac.roles.permissions.write")))
            .toList();
    assertError(
        request("PUT", selected(adminRoleId), admin, Map.of("ids", partial)),
        409,
        "LAST_ADMIN_REQUIRED");
    assertEquals(original, savedIds(adminRoleId));
    success(request("PATCH", role(adminRoleId), admin, Map.of("name", "普通名字不影响授权")), 200);
    assertEquals(200, request("GET", ROLES, admin, null).getStatusCode().value());
  }

  /** 所有新增端点先验证身份、实际API和根范围，再解析输入。 */
  @Test
  @DisplayName("T09 无权及部门账号不能通过非法输入或角色名称绕过拒绝")
  void t09AccessPrecedesValidation() throws Exception {
    account("t09-reader", rootId, List.of(permissionIds.get("rbac.roles.read")));
    account("t09-department", childId, new ArrayList<>(permissionIds.values()));
    account("t09-no-role-api", rootId, List.of(permissionIds.get("rbac.users.read")));
    String reader = token("t09-reader");
    String department = token("t09-department");
    String noAccess = token("t09-no-role-api");
    List<String[]> routes =
        List.of(
            new String[] {"GET", ROLES + "?size=0"},
            new String[] {"GET", selected(0)},
            new String[] {"POST", ROLES},
            new String[] {"PATCH", role(0)},
            new String[] {"PUT", selected(0)});
    for (String[] route : routes) {
      Object body = route[0].equals("GET") ? null : Map.of();
      assertError(request(route[0], route[1], null, body), 401, "AUTHENTICATION_REQUIRED");
      assertError(request(route[0], route[1], noAccess, body), 403, "PERMISSION_DENIED");
      assertError(request(route[0], route[1], department, body), 403, "MANAGEMENT_SCOPE_DENIED");
    }
    assertEquals(200, request("GET", ROLES, reader, null).getStatusCode().value());
    assertError(request("GET", TREE, reader, null), 403, "PERMISSION_DENIED");
    ResponseEntity<String> denied = request("POST", ROLES, reader, Map.of());
    assertError(denied, 403, "PERMISSION_DENIED");
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM authorization_logs WHERE request_id=?",
            Integer.class,
            denied.getHeaders().getFirst("X-Request-Id")));
  }

  /** POST成功状态必须与最小日志一致，不能把201记成默认200。 */
  @Test
  @DisplayName("T09 角色创建最小日志记录实际201状态且不含请求体")
  void t09LogsActualCreationStatus() throws Exception {
    ResponseEntity<String> response =
        request(
            "POST",
            ROLES,
            token("t09-admin"),
            Map.of("code", "audit-created", "name", "只存角色不存请求体"));
    success(response, 201);
    String requestId = response.getHeaders().getFirst("X-Request-Id");
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM authorization_logs WHERE request_id=?",
            Integer.class,
            requestId));
    assertEquals(
        201,
        jdbc.queryForObject(
            "SELECT http_status FROM authorization_logs WHERE request_id=?",
            Integer.class,
            requestId));
    assertEquals(
        ROLES,
        jdbc.queryForObject(
            "SELECT request_path FROM authorization_logs WHERE request_id=?",
            String.class,
            requestId));
  }

  /** 一个用户多个角色可汇总K，而不同用户的零散能力不能拼成维护者。 */
  @Test
  @DisplayName("T09 最后管理员逐用户计算完整K并支持本人多角色汇总")
  void t09CountsCompleteUsersRatherThanRoles() throws Exception {
    long critical = permissionIds.get("rbac.roles.permissions.write");
    List<Long> partial = permissionIds.values().stream().filter(id -> id != critical).toList();
    account("t09-partial", rootId, partial);
    account("t09-fragment", rootId, List.of(critical));
    String admin = token("t09-admin");
    assertError(
        request("PATCH", role(adminRoleId), admin, Map.of("enabled", false)),
        409,
        "LAST_ADMIN_REQUIRED");
    long split =
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  Role original = roles.findById(adminRoleId).orElseThrow();
                  Permission permission = permissions.findById(critical).orElseThrow();
                  original.revokePermission(permission);
                  Role second = roles.save(new Role("t09-split", "同一人另一角色"));
                  second.grantPermission(permission);
                  Long userId =
                      jdbc.queryForObject(
                          "SELECT id FROM users WHERE username='t09-admin'", Long.class);
                  users.findById(userId).orElseThrow().assignRole(second);
                  return second.getId();
                });
    success(request("PATCH", role(adminRoleId), admin, Map.of("name", "多角色完整维护者")), 200);
    assertError(
        request("PUT", selected(split), admin, Map.of("ids", List.of())),
        409,
        "LAST_ADMIN_REQUIRED");
    assertEquals(List.of(critical), savedIds(split));
  }

  /** 实际路由可由任意有效权限覆盖，不绑定目录权限编码或角色名称。 */
  @Test
  @DisplayName("T09 任一权限映射实际API即可放行，完整K采用同一OR语义")
  void t09HonorsAlternativeApiMappings() throws Exception {
    long alternative = alternativePermission("t09-alternative-read", "rbac.roles.read");
    account("t09-api-reader", rootId, List.of(alternative));
    String reader = token("t09-api-reader");
    success(request("GET", ROLES, reader, null), 200);
    success(request("GET", selected(adminRoleId), reader, null), 200);
    assertError(
        request("POST", ROLES, reader, Map.of("code", "x", "name", "x")), 403, "PERMISSION_DENIED");
    String admin = token("t09-admin");
    List<Long> original = savedIds(adminRoleId);
    List<Long> replacement = new ArrayList<>(original);
    replacement.remove(permissionIds.get("rbac.roles.read"));
    replacement.add(alternative);
    success(request("PUT", selected(adminRoleId), admin, Map.of("ids", replacement)), 200);
    success(request("GET", ROLES, admin, null), 200);
  }

  /** 父节点启停不级联直接按钮授权，其他启用角色仍可提供相同API。 */
  @Test
  @DisplayName("T09 父菜单不隐含授权、多角色OR及无角色准入")
  void t09KeepsDirectSelectionSemantics() throws Exception {
    String admin = token("t09-admin");
    long read = permissionIds.get("rbac.users.read");
    long first = account("t09-multi", childId, List.of(read));
    long second =
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  Role role = roles.save(new Role("t09-second", "另一个角色"));
                  role.grantPermission(permissions.findById(read).orElseThrow());
                  Long userId =
                      jdbc.queryForObject(
                          "SELECT id FROM users WHERE username='t09-multi'", Long.class);
                  users.findById(userId).orElseThrow().assignRole(role);
                  return role.getId();
                });
    String member = token("t09-multi");
    success(
        request(
            "PUT",
            selected(first),
            admin,
            Map.of("ids", List.of(permissionIds.get("rbac.workspace")))),
        200);
    success(
        request(
            "PATCH",
            PERMISSIONS + "/" + permissionIds.get("rbac.workspace"),
            admin,
            Map.of("enabled", false)),
        200);
    success(request("GET", USERS, member, null), 200);
    success(request("PATCH", role(second), admin, Map.of("enabled", false)), 200);
    assertError(request("GET", USERS, member, null), 403, "PERMISSION_DENIED");
    assertError(login("t09-multi"), 403, "MANAGEMENT_ACCESS_DENIED");
    jdbc.update(
        "DELETE FROM user_roles WHERE user_id=(SELECT id FROM users WHERE username='t09-multi')");
    assertError(login("t09-multi"), 403, "MANAGEMENT_ACCESS_DENIED");
  }

  /** 非约束故障也必须整体回滚，SQL或驱动消息不得出现在响应中。 */
  @Test
  @DisplayName("T09 关联写入约束冲突409、存储故障500均原子回滚")
  void t09RollsBackStorageFailures() throws Exception {
    String admin = token("t09-admin");
    long oldPermission = permissionIds.get("rbac.users.read");
    long nextPermission = permissionIds.get("rbac.logs.read");
    long target = account("t09-rollback", rootId, List.of(oldPermission));
    for (String state : List.of("23000", "45000")) {
      int errno = state.equals("23000") ? 1062 : 1644;
      jdbc.execute(
          "CREATE TRIGGER t09_fail_permission BEFORE INSERT ON role_permissions FOR EACH ROW BEGIN"
              + " IF NEW.role_id="
              + target
              + " AND NEW.permission_id="
              + nextPermission
              + " THEN SIGNAL SQLSTATE '"
              + state
              + "' SET MYSQL_ERRNO="
              + errno
              + ",MESSAGE_TEXT='T09 deliberate storage failure'; END IF; END");
      try {
        assertError(
            request("PUT", selected(target), admin, Map.of("ids", List.of(nextPermission))),
            state.equals("23000") ? 409 : 500,
            state.equals("23000") ? "DATA_CONFLICT" : "INTERNAL_ERROR");
        assertEquals(List.of(oldPermission), savedIds(target));
      } finally {
        jdbc.execute("DROP TRIGGER t09_fail_permission");
      }
    }
    jdbc.execute(
        "CREATE TRIGGER t09_fail_role BEFORE UPDATE ON roles FOR EACH ROW BEGIN IF NEW.id="
            + target
            + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T09 rejected definition'; END IF;"
            + " END");
    try {
      assertError(
          request("PATCH", role(target), admin, Map.of("name", "不得保存", "enabled", false)),
          500,
          "INTERNAL_ERROR");
      assertEquals(
          "t09-rollback",
          jdbc.queryForObject("SELECT name FROM roles WHERE id=?", String.class, target));
      assertTrue(
          jdbc.queryForObject("SELECT enabled FROM roles WHERE id=?", Boolean.class, target));
    } finally {
      jdbc.execute("DROP TRIGGER t09_fail_role");
    }
    success(request("PUT", selected(target), admin, Map.of("ids", List.of(nextPermission))), 200);
    assertEquals(List.of(nextPermission), savedIds(target));
  }

  /** 角色定义和选权两个入口共同串行化，不能各自把另一人当作保底管理员。 */
  @Test
  @DisplayName("T09 两名管理员同时停用角色及撤空权限只能成功一次")
  void t09SerializesRoleWriters() throws Exception {
    long other = account("t09-other-admin", rootId, new ArrayList<>(permissionIds.values()));
    String first = token("t09-admin");
    String second = token("t09-other-admin");
    List<Integer> statuses =
        concurrently(
            () -> request("PATCH", role(adminRoleId), first, Map.of("enabled", false)),
            () -> request("PUT", selected(other), second, Map.of("ids", List.of())));
    assertEquals(List.of(200, 409), statuses);
    assertEquals(1, completeAdministrators());
    assertEquals(permissionIds.size(), savedIds(adminRoleId).size());
    assertTrue(savedIds(other).isEmpty() || savedIds(other).size() == permissionIds.size());
  }

  /** 资源映射和角色变更跨票共用根锁，保护是同一事务规则。 */
  @Test
  @DisplayName("T09 与T07同时撤销不同维护者关键能力仍保留完整K")
  void t09SerializesWithResourceChanges() throws Exception {
    long privateRead = alternativePermission("t09-private-users-read", "rbac.users.read");
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              Role admin = roles.findById(adminRoleId).orElseThrow();
              admin.revokePermission(
                  permissions.findById(permissionIds.get("rbac.users.read")).orElseThrow());
              admin.grantPermission(permissions.findById(privateRead).orElseThrow());
            });
    long other = account("t09-cross-admin", rootId, new ArrayList<>(permissionIds.values()));
    String first = token("t09-admin");
    String second = token("t09-cross-admin");
    assertEquals(
        List.of(200, 409),
        concurrently(
            () ->
                request("PATCH", PERMISSIONS + "/" + privateRead, first, Map.of("enabled", false)),
            () -> request("PATCH", role(other), second, Map.of("enabled", false))));
    assertEquals(1, completeAdministrators());
  }

  /** 唯一编码同时创建也必须保留一份定义，重复请求不能覆盖首次名称。 */
  @Test
  @DisplayName("T09 并发创建同编码返回一次201与一次409")
  void t09SerializesDuplicateCreation() throws Exception {
    String admin = token("t09-admin");
    assertEquals(
        List.of(201, 409),
        concurrently(
            () -> request("POST", ROLES, admin, Map.of("code", "same", "name", "first")),
            () -> request("POST", ROLES, admin, Map.of("code", "same", "name", "second"))));
    assertEquals(
        1, jdbc.queryForObject("SELECT count(*) FROM roles WHERE code='same'", Integer.class));
  }

  /** 等锁时拥有的权限在事务执行前已被撤回，则原请求不能继续保存。 */
  @Test
  @DisplayName("T09 等待共同根锁后重新检查授权并拒绝过期请求")
  void t09RechecksAfterWaiting() throws Exception {
    long writer = account("t09-waiting", rootId, List.of(permissionIds.get("rbac.roles.write")));
    String token = token("t09-waiting");
    CountDownLatch entered = new CountDownLatch(1);
    observedLock.entered.set(entered);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicReference<Future<ResponseEntity<String>>> waiting = new AtomicReference<>();
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              status -> {
                departments.lockById(rootId).orElseThrow();
                waiting.set(
                    executor.submit(
                        () -> request("PATCH", role(adminRoleId), token, Map.of("name", "过期写入"))));
                try {
                  assertTrue(entered.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(exception);
                }
                jdbc.update("DELETE FROM role_permissions WHERE role_id=?", writer);
              });
      assertError(waiting.get().get(20, TimeUnit.SECONDS), 403, "PERMISSION_DENIED");
      assertEquals(
          "t09-admin",
          jdbc.queryForObject("SELECT name FROM roles WHERE id=?", String.class, adminRoleId));
    } finally {
      observedLock.entered.set(null);
      executor.shutdownNow();
    }
  }

  /** 系统整体停用后无法继续用旧令牌配置角色，已有授权关系保持不变。 */
  @Test
  @DisplayName("T09 不可用系统拒绝角色写入和再次管理登录")
  void t09RejectsDisabledSystem() throws Exception {
    String admin = token("t09-admin");
    List<Long> original = savedIds(adminRoleId);
    jdbc.update("UPDATE systems SET enabled=0 WHERE id=?", systemId);
    assertError(
        request("PUT", selected(adminRoleId), admin, Map.of("ids", List.of())),
        403,
        "PERMISSION_DENIED");
    assertError(login("t09-admin"), 403, "MANAGEMENT_ACCESS_DENIED");
    assertEquals(original, savedIds(adminRoleId));
  }

  /** 复制实际API映射构造OR测试夹具，不依赖目录权限编码放行。 */
  private long alternativePermission(String code, String sourceCode) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              Permission source = permissions.findById(permissionIds.get(sourceCode)).orElseThrow();
              Permission alternative =
                  new Permission(
                      systems.findById(systemId).orElseThrow(), code, code, Permission.Type.BUTTON);
              for (ApiResource api : source.getApis()) alternative.mapApi(api);
              return permissions.save(alternative).getId();
            });
  }

  /** 同步发起两个真实HTTP请求，排序结果以消除线程调度顺序差异。 */
  private List<Integer> concurrently(
      Callable<ResponseEntity<String>> first, Callable<ResponseEntity<String>> second)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();
    try {
      for (Callable<ResponseEntity<String>> action : List.of(first, second)) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  assertTrue(start.await(10, TimeUnit.SECONDS));
                  return action.call().getStatusCode().value();
                }));
      }
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> future : futures) statuses.add(future.get(20, TimeUnit.SECONDS));
      return statuses.stream().sorted().toList();
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  /** 使用公共有效权限算法逐个核对实际根用户，不能跨用户合并能力。 */
  private long completeAdministrators() {
    return jdbc
        .queryForList(
            "SELECT id FROM users WHERE enabled=1 AND department_id=?", Long.class, rootId)
        .stream()
        .filter(id -> effective.routes(id).containsAll(RbacCatalog.maintenanceRoutes()))
        .count();
  }

  /** 测试账号以真实JPA角色关系建立，名称本身不产生权限。 */
  private long account(String username, long departmentId, List<Long> ids) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              Role role = roles.save(new Role(username, username));
              for (Long id : ids) role.grantPermission(permissions.findById(id).orElseThrow());
              User user =
                  new User(
                      username,
                      encoder.encode(PASSWORD),
                      username,
                      departments.findById(departmentId).orElseThrow(),
                      true);
              user.assignRole(role);
              users.save(user);
              return role.getId();
            });
  }

  /** 从已提交数据库核对整体替换结果，不依靠内存对象。 */
  private List<Long> savedIds(long id) {
    return jdbc.queryForList(
        "SELECT permission_id FROM role_permissions WHERE role_id=? ORDER BY permission_id",
        Long.class,
        id);
  }

  /** 路径构造集中，保持实际URL与已评审模板一致。 */
  private String role(long id) {
    return ROLES + "/" + id;
  }

  /** 直接权限集合使用独立子资源，不能由候选页推断。 */
  private String selected(long id) {
    return role(id) + "/permissions";
  }

  /** 创建必须经HTTP并验证201状态，不能在测试中替代待验收行为。 */
  private long create(String token, String code, String name) throws Exception {
    return success(request("POST", ROLES, token, Map.of("code", code, "name", name)), 201)
        .get("id")
        .asLong();
  }

  /** 准入测试沿真实账号口令入口，不注入可信身份。 */
  private ResponseEntity<String> login(String username) throws Exception {
    return request("POST", LOGIN, null, Map.of("account", username, "password", PASSWORD));
  }

  /** 只有真实登录成功才能取得后续请求的Bearer。 */
  private String token(String username) throws Exception {
    return "Bearer " + success(login(username), 200).get("token").asText();
  }

  /** 请求使用真实随机端口，保留原始编码查询且发送严格JSON。 */
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

  /** 成功也必须有统一外壳和requestId，实体不可直接外泄。 */
  private JsonNode success(ResponseEntity<String> response, int status) throws Exception {
    assertEquals(status, response.getStatusCode().value(), response::getBody);
    JsonNode body = json.readTree(response.getBody());
    assertEquals("OK", body.get("code").asText());
    assertFalse(body.get("requestId").asText().isBlank());
    return body.get("data");
  }

  /** 错误码与HTTP状态同时核对，失败不能泄漏密码或SQL。 */
  private void assertError(ResponseEntity<String> response, int status, String code)
      throws Exception {
    assertEquals(status, response.getStatusCode().value(), response::getBody);
    JsonNode body = json.readTree(response.getBody());
    assertEquals(code, body.get("code").asText());
    assertTrue(body.get("data").isNull());
    assertFalse(response.getBody().contains(PASSWORD));
    assertFalse(response.getBody().contains("SQL"));
  }
}
