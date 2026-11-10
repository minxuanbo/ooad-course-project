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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** T07在独立MySQL及实际HTTP入口验证资源目录、映射与共同根保护。 */
@SpringBootTest(
    classes = CoreRuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-resource-test")
@ActiveProfiles("core-resource-test")
@Import(CoreResourcesHttpT07Test.LockFixtures.class)
class CoreResourcesHttpT07Test {
  private static final String LOGIN = "/api/auth/login";
  private static final String SYSTEMS = "/api/systems";
  private static final String TREE = "/api/permissions/tree";
  private static final String APIS = "/api/api-resources";
  private static final String PERMISSIONS = "/api/permissions";
  private static final String USERS = "/api/users";
  private static final String LOGS = "/api/authorization-logs";
  private static final String PASSWORD = "t07-fixture-password";
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
  private final Map<String, Long> permissionIds = new HashMap<>();
  private final Map<RbacCatalog.Route, Long> apiIds = new HashMap<>();

  /** 测试仅观察真实根锁入口，不替换仓储、权限计算或数据库。 */
  @TestConfiguration(proxyBeanMethods = false)
  static class LockFixtures {
    /** 为并发测试提供确定的“已完成AOP、将等待根锁”同步点。 */
    @Bean
    LockObservation lockObservation() {
      return new LockObservation();
    }
  }

  /** 同步实际HTTP请求和数据库写事务，避免用固定睡眠模拟竞争。 */
  @Aspect
  static class LockObservation {
    private final AtomicReference<CountDownLatch> entered = new AtomicReference<>();

    /** 只通知测试等待点，实际加锁和事务仍执行原实现。 */
    @Before("execution(* com.municipal.rbaccore.service.MaintenanceGuard.lock(..))")
    public void beforeLock() {
      CountDownLatch gate = entered.get();
      if (gate != null && RequestAuditFilter.current() != null) gate.countDown();
    }
  }

  /** 每项测试使用独立提交的小型授权夹具，不复用产品初始化或关闭外键。 */
  @BeforeEach
  void t07PrepareIsolatedCatalog() {
    assertEquals("rbac_core_t07_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    jdbc.execute("DROP TRIGGER IF EXISTS t07_fail_mapping");
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
    permissionIds.clear();
    apiIds.clear();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              Department root = departments.save(new Department("t07-root", "测试共同根"));
              Department child = new Department("t07-child", "测试子部门");
              child.moveTo(root);
              departments.save(child);
              rootId = root.getId();
              childId = child.getId();
              ApplicationSystem system = systems.save(new ApplicationSystem("rbac", "RBAC权限管理系统"));
              systemId = system.getId();
              Role admin = roles.save(new Role("t07-admin", "可改名的完整维护者"));
              Permission menu =
                  permissions.save(
                      new Permission(system, "rbac.workspace", "导航容器", Permission.Type.MENU));
              permissionIds.put("rbac.workspace", menu.getId());
              for (Map.Entry<String, List<RbacCatalog.Route>> definition :
                  RbacCatalog.definitions().entrySet()) {
                Permission permission =
                    new Permission(
                        system, definition.getKey(), definition.getKey(), Permission.Type.BUTTON);
                permission.moveTo(menu);
                for (RbacCatalog.Route route : definition.getValue()) {
                  ApiResource api =
                      apis.save(new ApiResource(system, route.method(), route.path()));
                  permission.mapApi(api);
                  apiIds.put(route, api.getId());
                }
                permissions.save(permission);
                permissionIds.put(definition.getKey(), permission.getId());
                admin.grantPermission(permission);
              }
              User user = new User("t07-admin", encoder.encode(PASSWORD), "测试维护者", root, true);
              user.assignRole(admin);
              users.save(user);
              jdbc.update(
                  "INSERT INTO import_sources(source_type,source_key,source_json,department_id)"
                      + " VALUES('ORGANIZATION','总公司','[]',?)",
                  rootId);
            });
  }

  /** 资源目录必须来自真实新链路，并通过统一安全外壳返回。 */
  @Test
  @DisplayName("T07 实际HTTP读取系统目录与API分页")
  void t07ReadsRealCatalog() throws Exception {
    String token = token("t07-admin");
    ResponseEntity<String> response = request("GET", SYSTEMS, token, null);
    assertEquals(200, response.getStatusCode().value());
    JsonNode data = data(response);
    assertEquals(1, data.size());
    assertEquals(systemId, data.get(0).get("id").asLong());
    assertEquals("rbac", data.get(0).get("code").asText());
    JsonNode first = data(request("GET", APIS + "?size=1", token, null));
    assertEquals(20, first.get("total").asLong());
    assertEquals(1, first.get("items").size());
    assertTrue(first.get("items").get(0).has("method"));
    assertTrue(first.get("items").get(0).has("path"));
  }

  /** 分页、搜索、停用目录及树的序号均来自当前数据库。 */
  @Test
  @DisplayName("T07 查询同筛选total、极大页码、完整映射及权限树")
  void t07QueriesStablePagesAndTrees() throws Exception {
    String token = token("t07-admin");
    JsonNode roots = data(request("GET", TREE, token, null));
    assertEquals(1, roots.size());
    assertEquals(10, roots.get(0).get("children").size());
    assertTrue(roots.get(0).get("parentId").isNull());
    JsonNode second = data(request("GET", APIS + "?size=1&page=1", token, null));
    JsonNode first = data(request("GET", APIS + "?size=1", token, null));
    assertTrue(
        first.get("items").get(0).get("id").asLong()
            < second.get("items").get(0).get("id").asLong());
    assertEquals(20, second.get("total").asLong());
    JsonNode beyond = data(request("GET", APIS + "?page=2147483647&size=100", token, null));
    assertEquals(0, beyond.get("items").size());
    assertEquals(20, beyond.get("total").asLong());
    assertEquals(
        apiIds.keySet().stream().filter(route -> route.method().equals("GET")).count(),
        data(request("GET", APIS + "?q=gEt", token, null)).get("total").asLong());
    assertEquals(
        apiIds.keySet().stream().filter(route -> route.method().equals("GET")).count(),
        data(request(
                "GET", APIS + "?q=" + "%20".repeat(60) + "gEt" + "%20".repeat(60), token, null))
            .get("total")
            .asLong());
    assertEquals(0, data(request("GET", APIS + "?q=%25", token, null)).get("total").asLong());
    long target = permissionIds.get("rbac.resources.read");
    JsonNode mapping = data(request("GET", mapping(target), token, null));
    assertEquals(4, mapping.get("ids").size());
    assertEquals(4, mapping.get("items").size());
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM systems", Integer.class));
  }

  /** 实际保存后再GET，显式null清父级，缺省字段保持不变。 */
  @Test
  @DisplayName("T07 权限节点PATCH与映射整体替换均持久化")
  void t07PersistsNodeAndMappingChanges() throws Exception {
    String token = token("t07-admin");
    long target = permissionIds.get("rbac.logs.read");
    long usersApi = apiIds.get(new RbacCatalog.Route("GET", USERS));
    JsonNode original = data(request("GET", mapping(target), token, null));
    List<Long> ids = List.of(original.get("ids").get(0).asLong(), usersApi);
    assertEquals(
        2, data(request("PUT", mapping(target), token, Map.of("ids", ids))).get("ids").size());
    assertEquals(
        2, data(request("PUT", mapping(target), token, Map.of("ids", ids))).get("ids").size());
    assertEquals(2, data(request("GET", mapping(target), token, null)).get("ids").size());
    assertEquals(
        0,
        data(request("PUT", mapping(target), token, Map.of("ids", List.of()))).get("ids").size());
    Map<String, Object> detach = new HashMap<>();
    detach.put("parentId", null);
    JsonNode patched = data(request("PATCH", PERMISSIONS + "/" + target, token, detach));
    assertTrue(patched.get("parentId").isNull());
    assertTrue(patched.get("enabled").asBoolean());
    assertEquals("BUTTON", patched.get("type").asText());
    data(
        request(
            "PATCH", PERMISSIONS + "/" + target, token, Map.of("type", "MENU", "enabled", false)));
    assertEquals(
        false,
        jdbc.queryForObject("SELECT enabled FROM permissions WHERE id=?", Boolean.class, target));
    JsonNode tree = data(request("GET", TREE, token, null));
    assertEquals(2, tree.size());
    assertFalse(tree.get(1).get("enabled").asBoolean());
  }

  /** API复用按OR，原映射撤销或API启停后同一Bearer即刻反映实际权限。 */
  @Test
  @DisplayName("T07 多权限API复用与撤映射启停实时生效")
  void t07ChangesActualAuthorizationImmediately() throws Exception {
    String admin = token("t07-admin");
    long usersApi = apiIds.get(new RbacCatalog.Route("GET", USERS));
    long alternate = extraPermission("t07-alternate", Permission.Type.BUTTON, List.of(usersApi));
    account("t07-probe", rootId, List.of(alternate));
    jdbc.update(
        "INSERT INTO role_permissions(role_id,permission_id) SELECT id,? FROM roles WHERE"
            + " code='t07-admin'",
        alternate);
    String probe = token("t07-probe");
    long source = permissionIds.get("rbac.users.read");
    List<Long> original = savedApiIds(source);
    data(
        request(
            "PUT",
            mapping(source),
            admin,
            Map.of("ids", original.stream().filter(id -> id != usersApi).toList())));
    assertEquals(200, request("GET", USERS, probe, null).getStatusCode().value());
    data(request("PUT", mapping(source), admin, Map.of("ids", original)));
    data(request("PUT", mapping(alternate), admin, Map.of("ids", List.of())));
    assertError(request("GET", USERS, probe, null), 403, "PERMISSION_DENIED");
    assertEquals(
        403,
        request("POST", LOGIN, null, Map.of("account", "t07-probe", "password", PASSWORD))
            .getStatusCode()
            .value());
    long logsApi = apiIds.get(new RbacCatalog.Route("GET", LOGS));
    data(request("PATCH", APIS + "/" + logsApi, admin, Map.of("enabled", false)));
    assertError(request("GET", LOGS, admin, null), 403, "PERMISSION_DENIED");
    JsonNode retained =
        data(request("GET", mapping(permissionIds.get("rbac.logs.read")), admin, null));
    assertEquals(1, retained.get("ids").size());
    assertFalse(retained.get("items").get(0).get("enabled").asBoolean());
    data(request("PATCH", APIS + "/" + logsApi, admin, Map.of("enabled", true)));
    assertEquals(200, request("GET", LOGS, admin, null).getStatusCode().value());
  }

  /** 根成员位置不授予资源操作，部门账号即使拥有对应API也不满足全局范围。 */
  @Test
  @DisplayName("T07 资源读写同时要求实际权限与共同根")
  void t07RequiresActualPermissionAndGlobalScope() throws Exception {
    account("t07-personnel", rootId, List.of(permissionIds.get("rbac.users.read")));
    account(
        "t07-local",
        childId,
        List.of(
            permissionIds.get("rbac.resources.read"), permissionIds.get("rbac.resources.write")));
    account("t07-write-only", rootId, List.of(permissionIds.get("rbac.resources.write")));
    String noResource = token("t07-personnel");
    String local = token("t07-local");
    String writeOnly = token("t07-write-only");
    long target = permissionIds.get("rbac.logs.read");
    assertError(request("GET", SYSTEMS, noResource, null), 403, "PERMISSION_DENIED");
    assertError(
        request("PATCH", PERMISSIONS + "/" + target, noResource, Map.of("enabled", false)),
        403,
        "PERMISSION_DENIED");
    assertError(request("GET", SYSTEMS, local, null), 403, "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PUT", mapping(target), local, Map.of("ids", List.of())),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(request("GET", SYSTEMS, null, null), 401, "AUTHENTICATION_REQUIRED");
    assertError(request("GET", SYSTEMS, writeOnly, null), 403, "PERMISSION_DENIED");
    data(request("PATCH", PERMISSIONS + "/" + target, writeOnly, Map.of("enabled", false)));
  }

  /** 关联输入、树规则和只读字段错误必须整体拒绝，不静默去重或转换。 */
  @Test
  @DisplayName("T07 严格请求类型、树循环按钮子节点及不存在目标")
  void t07RejectsInvalidInputAndTrees() throws Exception {
    String token = token("t07-admin");
    long target = permissionIds.get("rbac.logs.read");
    long api = apiIds.get(new RbacCatalog.Route("GET", LOGS));
    for (Object invalid :
        List.of(
            Map.of(),
            Map.of("ids", List.of(api, api)),
            Map.of("ids", List.of(0)),
            Map.of("ids", List.of(9007199254740992L)),
            Map.of("ids", List.of("1")),
            Map.of("ids", List.of(1.2)),
            Map.of("ids", List.of(999999999L)),
            Map.of("ids", "not-array"))) {
      assertError(request("PUT", mapping(target), token, invalid), 400, "INVALID_ARGUMENT");
    }
    for (Object invalid :
        List.of(
            Map.of(),
            Map.of("name", "readonly"),
            Map.of("enabled", "false"),
            Map.of("parentId", target),
            Map.of("parentId", target + 0.5),
            Map.of("type", "OTHER"),
            Map.of("parentId", 999999999L),
            Map.of("parentIdPresent", true))) {
      assertError(
          request("PATCH", PERMISSIONS + "/" + target, token, invalid), 400, "INVALID_ARGUMENT");
    }
    Map<String, Object> nullEnabled = new HashMap<>();
    nullEnabled.put("enabled", null);
    assertError(
        request("PATCH", PERMISSIONS + "/" + target, token, nullEnabled), 400, "INVALID_ARGUMENT");
    long menu = permissionIds.get("rbac.workspace");
    assertError(
        request("PATCH", PERMISSIONS + "/" + menu, token, Map.of("parentId", target)),
        400,
        "INVALID_ARGUMENT");
    assertError(
        request("PATCH", PERMISSIONS + "/" + menu, token, Map.of("type", "BUTTON")),
        400,
        "INVALID_ARGUMENT");
    long childMenu = extraPermission("t07-menu", Permission.Type.MENU, List.of());
    data(request("PATCH", PERMISSIONS + "/" + childMenu, token, Map.of("parentId", menu)));
    assertError(
        request("PATCH", PERMISSIONS + "/" + menu, token, Map.of("parentId", childMenu)),
        400,
        "INVALID_ARGUMENT");
    for (String path : List.of(mapping(999999999L), PERMISSIONS + "/999999999")) {
      assertError(
          request(
              path.endsWith("/apis") ? "GET" : "PATCH",
              path,
              token,
              path.endsWith("/apis") ? null : Map.of("enabled", false)),
          404,
          "RESOURCE_NOT_FOUND");
    }
    assertError(
        request("PATCH", APIS + "/999999999", token, Map.of("enabled", false)),
        404,
        "RESOURCE_NOT_FOUND");
    assertEquals(List.of(api), savedApiIds(target));
  }

  /** 保护逐人完整K，不能把只读人员及零散授权拼成完整维护者。 */
  @Test
  @DisplayName("T07 最后一名完整维护者的权限API及映射均不可破坏")
  void t07ProtectsCompleteMaintenanceCapabilities() throws Exception {
    String token = token("t07-admin");
    long target = permissionIds.get("rbac.resources.read");
    long api = apiIds.get(new RbacCatalog.Route("GET", SYSTEMS));
    assertError(
        request("PATCH", PERMISSIONS + "/" + target, token, Map.of("enabled", false)),
        409,
        "LAST_ADMIN_REQUIRED");
    assertError(
        request("PATCH", APIS + "/" + api, token, Map.of("enabled", false)),
        409,
        "LAST_ADMIN_REQUIRED");
    assertError(
        request("PUT", mapping(target), token, Map.of("ids", List.of())),
        409,
        "LAST_ADMIN_REQUIRED");
    assertEquals(4, savedApiIds(target).size());
    assertEquals(
        true,
        jdbc.queryForObject("SELECT enabled FROM permissions WHERE id=?", Boolean.class, target));
    assertEquals(
        true,
        jdbc.queryForObject("SELECT enabled FROM api_resources WHERE id=?", Boolean.class, api));
    data(
        request(
            "PATCH",
            PERMISSIONS + "/" + permissionIds.get("rbac.workspace"),
            token,
            Map.of("enabled", false)));
    assertEquals(200, request("GET", SYSTEMS, token, null).getStatusCode().value());
  }

  /** 数字枚举及空字符串的隐式转换应在API解析阶段拒绝。 */
  @Test
  @DisplayName("T07 数字枚举及空字符串不得转换为合法PATCH字段")
  void t07RejectsCoercedPatchTypes() throws Exception {
    String token = token("t07-admin");
    long id = permissionIds.get("rbac.logs.read");
    for (Object value :
        List.of(
            Map.of("type", 0), Map.of("parentId", ""), Map.of("enabled", 1), Map.of("type", ""))) {
      assertError(request("PATCH", PERMISSIONS + "/" + id, token, value), 400, "INVALID_ARGUMENT");
    }
    for (String path :
        List.of(
            APIS + "?page=-1",
            APIS + "?size=101",
            APIS + "?q=" + "x".repeat(101),
            TREE + "?systemId=0",
            mapping(0),
            mapping(9007199254740992L),
            PERMISSIONS + "/1.5/apis")) {
      assertError(request("GET", path, token, null), 400, "INVALID_ARGUMENT");
    }
    for (Object value :
        List.of(
            Map.of(),
            Map.of("enabled", "true"),
            Map.of("method", "DELETE"),
            Map.of("enabled", false, "path", "/new"))) {
      assertError(
          request(
              "PATCH", APIS + "/" + apiIds.get(new RbacCatalog.Route("GET", LOGS)), token, value),
          400,
          "INVALID_ARGUMENT");
    }
    assertError(
        request("PUT", mapping(999999999L), token, Map.of("ids", List.of())),
        404,
        "RESOURCE_NOT_FOUND");
    Map<String, Object> nullIds = new HashMap<>();
    nullIds.put("ids", null);
    assertError(request("PUT", mapping(id), token, nullIds), 400, "INVALID_ARGUMENT");
    assertError(
        request("PUT", mapping(id), token, Map.of("ids", Arrays.asList(1L, null))),
        400,
        "INVALID_ARGUMENT");
  }

  /** 拒绝顺序必须先校验可信身份、实际权限及全局范围，再解析业务输入。 */
  @Test
  @DisplayName("T07 无权及越界请求不能因非法输入先获得400")
  void t07ChecksAccessBeforeInvalidInput() throws Exception {
    long target = permissionIds.get("rbac.logs.read");
    assertError(
        request("PATCH", PERMISSIONS + "/" + target, null, Map.of()),
        401,
        "AUTHENTICATION_REQUIRED");
    account("t07-no-resources", rootId, List.of(permissionIds.get("rbac.users.read")));
    String noAccess = token("t07-no-resources");
    assertError(
        request("PUT", mapping(target), noAccess, Map.of("ids", List.of(1L, 1L))),
        403,
        "PERMISSION_DENIED");
    assertError(
        request("PATCH", PERMISSIONS + "/0", noAccess, Map.of("name", "readonly")),
        403,
        "PERMISSION_DENIED");
    account("t07-local-invalid", childId, List.of(permissionIds.get("rbac.resources.write")));
    assertError(
        request("PATCH", PERMISSIONS + "/" + target, token("t07-local-invalid"), Map.of()),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    long[] foreign =
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  ApplicationSystem system =
                      systems.save(new ApplicationSystem("t07-invalid-target", "外系统"));
                  Permission permission =
                      permissions.save(
                          new Permission(system, "foreign", "外系统权限", Permission.Type.MENU));
                  ApiResource api = apis.save(new ApiResource(system, "GET", USERS));
                  return new long[] {permission.getId(), api.getId()};
                });
    String admin = token("t07-admin");
    ResponseEntity<String> denied =
        request("PATCH", PERMISSIONS + "/" + foreign[0], admin, Map.of("name", "readonly"));
    assertError(denied, 403, "MANAGEMENT_SCOPE_DENIED");
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM authorization_logs WHERE request_id=?",
            Integer.class,
            denied.getHeaders().getFirst("X-Request-Id")));
    assertEquals(
        "AUTHORIZATION",
        jdbc.queryForObject(
            "SELECT event_type FROM authorization_logs WHERE request_id=?",
            String.class,
            denied.getHeaders().getFirst("X-Request-Id")));
    assertError(
        request("PATCH", APIS + "/" + foreign[1], admin, Map.of()), 403, "MANAGEMENT_SCOPE_DENIED");
  }

  /** 目录、直接目标和关联输入的系统边界使用不同的已评审错误语义。 */
  @Test
  @DisplayName("T07 外系统目录目标403、跨系统关联400且不改变映射")
  void t07IsolatesExternalSystems() throws Exception {
    long[] foreign =
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  ApplicationSystem system =
                      systems.save(new ApplicationSystem("t07-external", "另一个业务系统"));
                  ApiResource api = apis.save(new ApiResource(system, "GET", USERS));
                  Permission permission =
                      permissions.save(
                          new Permission(system, "t07-foreign", "外系统测试权限", Permission.Type.MENU));
                  permission.mapApi(api);
                  return new long[] {system.getId(), permission.getId(), api.getId()};
                });
    String token = token("t07-admin");
    assertEquals(1, data(request("GET", SYSTEMS, token, null)).size());
    for (String path :
        List.of(
            TREE + "?systemId=" + foreign[0],
            APIS + "?systemId=" + foreign[0],
            mapping(foreign[1]))) {
      assertError(request("GET", path, token, null), 403, "MANAGEMENT_SCOPE_DENIED");
    }
    assertError(
        request("PATCH", PERMISSIONS + "/" + foreign[1], token, Map.of("enabled", false)),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PATCH", APIS + "/" + foreign[2], token, Map.of("enabled", false)),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    long target = permissionIds.get("rbac.logs.read");
    List<Long> original = savedApiIds(target);
    assertError(
        request(
            "PUT", mapping(target), token, Map.of("ids", List.of(original.getFirst(), foreign[2]))),
        400,
        "INVALID_ARGUMENT");
    assertError(
        request("PATCH", PERMISSIONS + "/" + target, token, Map.of("parentId", foreign[1])),
        400,
        "INVALID_ARGUMENT");
    assertEquals(original, savedApiIds(target));
  }

  /** 强制中途约束故障，验证已删除的旧映射及已修改字段随同事务恢复。 */
  @Test
  @DisplayName("T07 保存边界翻译数据库冲突并整体回滚")
  void t07RollsBackDatabaseFailure() throws Exception {
    String token = token("t07-admin");
    long target = permissionIds.get("rbac.logs.read");
    List<Long> original = savedApiIds(target);
    long usersApi = apiIds.get(new RbacCatalog.Route("GET", USERS));
    jdbc.execute(
        "CREATE TRIGGER t07_fail_mapping BEFORE INSERT ON permission_apis FOR EACH ROW BEGIN IF"
            + " NEW.permission_id="
            + target
            + " AND NEW.api_id="
            + usersApi
            + " THEN SIGNAL SQLSTATE '23000' SET MYSQL_ERRNO=1062,MESSAGE_TEXT='T07 forced"
            + " failure'; END IF; END");
    try {
      assertError(
          request("PUT", mapping(target), token, Map.of("ids", List.of(usersApi))),
          409,
          "DATA_CONFLICT");
      assertEquals(original, savedApiIds(target));
    } finally {
      jdbc.execute("DROP TRIGGER t07_fail_mapping");
    }
    data(request("PUT", mapping(target), token, Map.of("ids", List.of(usersApi))));
    assertEquals(List.of(usersApi), savedApiIds(target));
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            new TransactionTemplate(transactions)
                .executeWithoutResult(
                    status ->
                        apis.saveAndFlush(
                            new ApiResource(
                                systems.findById(systemId).orElseThrow(), "GET", USERS))));
    assertEquals(20, apis.count());
  }

  /** 两名维护者同时失去各自关键能力时，共同根锁只允许一次不会清零的提交。 */
  @Test
  @DisplayName("T07 实际并发HTTP写入不能同时撤空两名完整维护者")
  void t07SerializesConcurrentAdminChanges() throws Exception {
    Map<String, Long> backup = new HashMap<>();
    for (Map.Entry<String, List<RbacCatalog.Route>> entry : RbacCatalog.definitions().entrySet()) {
      backup.put(
          entry.getKey(),
          extraPermission(
              "t07-backup-" + entry.getKey(),
              Permission.Type.BUTTON,
              entry.getValue().stream().map(apiIds::get).toList()));
    }
    account("t07-backup", rootId, new ArrayList<>(backup.values()));
    String first = token("t07-admin");
    String second = token("t07-backup");
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> a =
          executor.submit(
              () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return request(
                        "PATCH",
                        PERMISSIONS + "/" + permissionIds.get("rbac.resources.read"),
                        first,
                        Map.of("enabled", false))
                    .getStatusCode()
                    .value();
              });
      Future<Integer> c =
          executor.submit(
              () -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return request(
                        "PATCH",
                        PERMISSIONS + "/" + backup.get("rbac.resources.read"),
                        second,
                        Map.of("enabled", false))
                    .getStatusCode()
                    .value();
              });
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<Integer> statuses =
          new ArrayList<>(List.of(a.get(15, TimeUnit.SECONDS), c.get(15, TimeUnit.SECONDS)));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
      long complete =
          jdbc
              .queryForList(
                  "SELECT id FROM users WHERE enabled=1 AND department_id=?", Long.class, rootId)
              .stream()
              .filter(id -> effective.routes(id).containsAll(RbacCatalog.maintenanceRoutes()))
              .count();
      assertEquals(1, complete);
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  /** 请求通过AOP后被根锁阻塞，等待期间撤权，拿锁后必须重新读取并拒绝。 */
  @Test
  @DisplayName("T07 等待共同根锁期间已撤销的授权不能继续写入")
  void t07RechecksPermissionAfterWaitingForLock() throws Exception {
    List<Long> routes =
        RbacCatalog.definitions().get("rbac.resources.write").stream().map(apiIds::get).toList();
    long privateWrite = extraPermission("t07-waiting-write", Permission.Type.BUTTON, routes);
    account("t07-waiting", rootId, List.of(privateWrite));
    String token = token("t07-waiting");
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
                        () ->
                            request(
                                "PATCH",
                                PERMISSIONS + "/" + permissionIds.get("rbac.logs.read"),
                                token,
                                Map.of("enabled", false))));
                try {
                  assertTrue(entered.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(exception);
                }
                jdbc.update("DELETE FROM role_permissions WHERE permission_id=?", privateWrite);
              });
      assertError(waiting.get().get(15, TimeUnit.SECONDS), 403, "PERMISSION_DENIED");
      assertEquals(
          true,
          jdbc.queryForObject(
              "SELECT enabled FROM permissions WHERE id=?",
              Boolean.class,
              permissionIds.get("rbac.logs.read")));
    } finally {
      observedLock.entered.set(null);
      executor.shutdownNow();
    }
  }

  /** 少量测试权限仅用于隔离库，不登记到产品初始化清单。 */
  private long extraPermission(String code, Permission.Type type, List<Long> ids) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              Permission permission =
                  new Permission(systems.findById(systemId).orElseThrow(), code, code, type);
              for (Long id : ids) permission.mapApi(apis.findById(id).orElseThrow());
              return permissions.save(permission).getId();
            });
  }

  /** 以独立角色构造明确的当前有效授权，不用角色名称推断能力。 */
  private void account(String username, long departmentId, List<Long> ids) {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              Role role = roles.save(new Role(username, username));
              for (Long id : ids) role.grantPermission(permissions.findById(id).orElseThrow());
              User user =
                  new User(
                      username,
                      encoder.encode(PASSWORD),
                      "测试人员",
                      departments.findById(departmentId).orElseThrow(),
                      true);
              user.assignRole(role);
              users.save(user);
            });
  }

  /** 查询已提交的关系真值，不以当前候选页作为全部已选项。 */
  private List<Long> savedApiIds(long permissionId) {
    return jdbc.queryForList(
        "SELECT api_id FROM permission_apis WHERE permission_id=? ORDER BY api_id",
        Long.class,
        permissionId);
  }

  /** 使用服务端路径模板对应的实际对象地址。 */
  private String mapping(long permissionId) {
    return PERMISSIONS + "/" + permissionId + "/apis";
  }

  /** 每个失败保留标准外壳，不泄漏口令或SQL内部信息。 */
  private void assertError(ResponseEntity<String> response, int status, String code)
      throws Exception {
    assertEquals(status, response.getStatusCode().value(), () -> "实际响应=" + response.getBody());
    JsonNode body = json.readTree(response.getBody());
    assertEquals(code, body.get("code").asText());
    assertTrue(body.get("data").isNull());
    assertFalse(response.getBody().contains(PASSWORD));
    assertFalse(response.getBody().contains("SQL"));
  }

  /** 测试身份必须来自实际登录，不能直接注入用户ID绕过准入。 */
  private String token(String username) throws Exception {
    ResponseEntity<String> response =
        request("POST", LOGIN, null, Map.of("account", username, "password", PASSWORD));
    assertEquals(200, response.getStatusCode().value());
    return "Bearer " + data(response).get("token").asText();
  }

  /** 发送已编码URI，避免测试客户端再次编码查询条件。 */
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

  /** 成功外壳必须包含可解析数据，不以裸实体替代VO。 */
  private JsonNode data(ResponseEntity<String> response) throws Exception {
    assertEquals(200, response.getStatusCode().value());
    JsonNode body = json.readTree(response.getBody());
    assertEquals("OK", body.get("code").asText());
    return body.get("data");
  }
}
