package com.municipal.rbaccore.r13;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApiResourceJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.PermissionJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.infrastructure.web.RequestAuditFilter;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.security.ManagementAuthorization;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** T09通过真实MySQL和随机端口HTTP核对角色维护、即时授权和原子保护。 */
abstract class R13RoleContractSupport {
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
  @Autowired DepartmentJpaRepository departments;
  @Autowired UserJpaRepository users;
  @Autowired RoleJpaRepository roles;
  @Autowired PermissionJpaRepository permissions;
  @Autowired ApiResourceJpaRepository apis;
  @Autowired ApplicationSystemJpaRepository systems;
  @Autowired PlatformTransactionManager transactions;
  @Autowired ManagementAuthorization effective;
  @Autowired LockObservation observedLock;
  @Autowired RoleCountObservation observedCount;
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

    /** 提供计数后、查询页之前的确定同步点。 */
    @Bean
    RoleCountObservation roleCountObservation() {
      return new RoleCountObservation();
    }
  }

  /** 锁观察器避免测试依靠固定睡眠碰运气。 */
  @Aspect
  static class LockObservation {
    private final AtomicReference<CountDownLatch> entered = new AtomicReference<>();

    /** 通知测试线程后继续执行实际根锁。 */
    @Before(
        "execution(*"
            + " com.municipal.rbaccore.service.structured.security.StructuredMaintenanceGuard.lock(..))")
    public void beforeLock() {
      CountDownLatch latch = entered.get();
      if (latch != null && RequestAuditFilter.current() != null) latch.countDown();
    }
  }

  /** 计数完成后暂停读请求，让写请求在两次查询之间提交。 */
  @Aspect
  static class RoleCountObservation {
    private final AtomicReference<PagePause> pause = new AtomicReference<>();

    /** 只暂停由测试标记的角色分页计数。 */
    @AfterReturning(
        "execution(* org.springframework.data.jpa.repository.JpaSpecificationExecutor+.count(..))"
            + " && this(com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository)")
    public void afterCount() throws InterruptedException {
      PagePause current = pause.getAndSet(null);
      if (current != null) {
        current.counted().countDown();
        assertTrue(current.resume().await(10, TimeUnit.SECONDS));
      }
    }
  }

  /** 读线程通知计数完成，写入提交后再放行读线程。 */
  record PagePause(CountDownLatch counted, CountDownLatch resume) {}

  /** 每例从可丢弃专库重建少量真实关联，不使用生产初始化或旧业务数据。 */
  @BeforeEach
  void R13_Prepare() {
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
              DepartmentEntity root = departments.save(new DepartmentEntity("t09-root", "核实根部门"));
              DepartmentEntity child = new DepartmentEntity("t09-child", "测试子部门");
              child.moveTo(root);
              departments.save(child);
              rootId = root.getId();
              childId = child.getId();
              ApplicationSystemEntity system =
                  systems.save(new ApplicationSystemEntity("rbac", "权限管理系统"));
              systemId = system.getId();
              PermissionEntity menu =
                  permissions.save(
                      new PermissionEntity(system, "rbac.workspace", "父菜单", PermissionType.MENU));
              permissionIds.put("rbac.workspace", menu.getId());
              for (Map.Entry<String, List<RbacCatalog.Route>> entry :
                  RbacCatalog.definitions().entrySet()) {
                PermissionEntity permission =
                    new PermissionEntity(
                        system, entry.getKey(), entry.getKey(), PermissionType.BUTTON);
                permission.moveTo(menu);
                for (RbacCatalog.Route route : entry.getValue()) {
                  permission.mapApi(
                      apis.save(new ApiResourceEntity(system, route.method(), route.path())));
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
  @DisplayName("R13/R15 新建空角色、改名启停和权限保存读回")
  void R13_RoleLifecycle() throws Exception {
    String admin = token("t09-admin");
    JsonNode created =
        success(request("POST", ROLES, admin, Map.of("code", " new-role ", "name", " 新角色 ")), 201);
    long id = created.get("id").asLong();
    assertEquals("new-role", created.get("code").asText());
    assertEquals("新角色", created.get("name").asText());
    assertTrue(created.get("enabled").asBoolean());
    assertEquals(5, created.size());
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
  @DisplayName("R13/R15 角色分页、AND筛选、大小写搜索和极大越末页")
  void R13_SearchAndPages() throws Exception {
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

  /** 计数后提交新角色，已有分页请求仍看到一致快照。 */
  @Test
  @DisplayName("R13/R15 并发创建时角色页和总数保持同一快照")
  void R13_KeepsPageSnapshot() throws Exception {
    String admin = token("t09-admin");
    create(admin, "snapshot-first", "快照角色一");
    PagePause pause = new PagePause(new CountDownLatch(1), new CountDownLatch(1));
    observedCount.pause.set(pause);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<ResponseEntity<String>> read =
          executor.submit(() -> request("GET", ROLES + "?q=snapshot-", admin, null));
      assertTrue(pause.counted().await(10, TimeUnit.SECONDS));
      create(admin, "snapshot-second", "快照角色二");
      pause.resume().countDown();
      JsonNode page = success(read.get(10, TimeUnit.SECONDS), 200);
      assertEquals(1, page.get("total").asInt());
      assertEquals(1, page.get("items").size());
      JsonNode next = success(request("GET", ROLES + "?q=snapshot-", admin, null), 200);
      assertEquals(2, next.get("total").asInt());
      assertEquals(2, next.get("items").size());
    } finally {
      pause.resume().countDown();
      observedCount.pause.set(null);
      executor.shutdownNow();
    }
  }

  /** 实际MySQL二进制排序规则和唯一索引共同决定编码相等。 */
  @Test
  @DisplayName("R13/R15 编码trim判重与MySQL大小写重音语义一致")
  void R13_CodeUniqueness() throws Exception {
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
  @DisplayName("R13/R15 拒绝非法定义、未知字段及隐式类型转换")
  void R13_RejectsInvalidDefinitions() throws Exception {
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

  /** 角色文本的全角及不换行空白与原型的首尾清理一致。 */
  @Test
  @DisplayName("R13/R15 角色编码、名称及搜索统一清理首尾Unicode空白")
  void R13_NormalizesRoleText() throws Exception {
    String admin = token("t09-admin");
    long target = create(admin, "unicode-target", "初始名称");
    assertError(
        request("PATCH", role(target), admin, Map.of("name", "\u3000")), 400, "INVALID_ARGUMENT");
    assertError(
        request("POST", ROLES, admin, Map.of("code", "\u00a0", "name", "空白编码")),
        400,
        "INVALID_ARGUMENT");
    JsonNode created =
        success(
            request(
                "POST",
                ROLES,
                admin,
                Map.of("code", "\u3000unicode-role\u00a0", "name", "\u00a0旧名称\u3000")),
            201);
    assertEquals("unicode-role", created.get("code").asText());
    assertEquals("旧名称", created.get("name").asText());
    JsonNode renamed =
        success(request("PATCH", role(target), admin, Map.of("name", "\u3000新名称\u00a0")), 200);
    assertEquals("新名称", renamed.get("name").asText());
    assertEquals(
        1,
        success(request("GET", ROLES + "?q=%E3%80%80unicode-role%C2%A0", admin, null), 200)
            .get("total")
            .asInt());
  }

  /** 格式错误的路径不可写入宽松转换后的目标。 */
  @Test
  @DisplayName("R13/R15 拒绝路径ID和分页整数中的空白及其他进制")
  void R13_RejectsMalformedDecimalParameters() throws Exception {
    String admin = token("t09-admin");
    long target = create(admin, "decimal-target", "原角色");
    String malformedRole = ROLES + "/0%20" + target;
    assertError(
        request("PATCH", malformedRole, admin, Map.of("name", "错误目标")), 400, "INVALID_ARGUMENT");
    assertError(
        request("GET", malformedRole + "/permissions", admin, null), 400, "INVALID_ARGUMENT");
    assertError(
        request("PUT", malformedRole + "/permissions", admin, Map.of("ids", List.of())),
        400,
        "INVALID_ARGUMENT");
    for (String query : List.of("page=0%201", "size=1%202", "page=0x0")) {
      assertError(request("GET", ROLES + "?" + query, admin, null), 400, "INVALID_ARGUMENT");
    }
    assertEquals(
        "原角色", jdbc.queryForObject("SELECT name FROM roles WHERE id=?", String.class, target));
  }

  /** 一个请求体只接受一个合法JSON值，失败保留角色及授权。 */
  @Test
  @DisplayName("R13/R15 拒绝JSON对象后的多余内容且保留原授权")
  void R13_RejectsTrailingJson() throws Exception {
    String admin = token("t09-admin");
    long permissionId = permissionIds.get("rbac.roles.read");
    long target = account("t09-trailing", rootId, List.of(permissionId));
    assertError(
        rawRequest("PUT", selected(target), admin, "{\"ids\":[]} {\"ids\":[12]}"),
        400,
        "INVALID_ARGUMENT");
    assertError(
        rawRequest("PATCH", role(target), admin, "{\"enabled\":false} trailing"),
        400,
        "INVALID_ARGUMENT");
    assertEquals(List.of(permissionId), savedIds(target));
    assertTrue(jdbc.queryForObject("SELECT enabled FROM roles WHERE id=?", Boolean.class, target));
  }

  /** 关联数组先完整验证，已停用项可读可移除但不能重新授予。 */
  @Test
  @DisplayName("R13/R15 非法权限数组及停用权限不产生部分替换")
  void R13_RejectsInvalidPermissionSets() throws Exception {
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
  @DisplayName("R13/R15 跨系统选权拒绝且保留目标已有外系统关系")
  void R13_PreservesForeignAssignments() throws Exception {
    long foreign =
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  ApplicationSystemEntity system =
                      systems.save(new ApplicationSystemEntity("other", "外系统测试夹具"));
                  return permissions
                      .save(
                          new PermissionEntity(system, "other.read", "外系统权限", PermissionType.MENU))
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
  @DisplayName("R13/R15 授权撤销立即影响HTTP与再次登录，多角色共享权限")
  void R13_ChangesActualAuthorization() throws Exception {
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
  @DisplayName("R13/R15 角色启停和停用期间配权均使用最新状态")
  void R13_RoleEnablement() throws Exception {
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
  @DisplayName("R13/R15 最后完整管理员的角色状态和权限受同一保护")
  void R13_ProtectsLastAdministrator() throws Exception {
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
  @DisplayName("R13/R15 无权及部门账号不能通过非法输入或角色名称绕过拒绝")
  void R13_AccessPrecedesValidation() throws Exception {
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
  @DisplayName("R13/R15 角色创建最小日志记录实际201状态且不含请求体")
  void R13_LogsActualCreationStatus() throws Exception {
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
  @DisplayName("R13/R15 最后管理员逐用户计算完整K并支持本人多角色汇总")
  void R13_CountsCompleteUsersRatherThanRoles() throws Exception {
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
                  RoleEntity original = roles.findById(adminRoleId).orElseThrow();
                  PermissionEntity permission = permissions.findById(critical).orElseThrow();
                  original.revokePermission(permission);
                  RoleEntity second = roles.save(new RoleEntity("t09-split", "同一人另一角色"));
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
  @DisplayName("R13/R15 任一权限映射实际API即可放行，完整K采用同一OR语义")
  void R13_HonorsAlternativeApiMappings() throws Exception {
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

  /** A08-4：同组接口中只授予列表路由，详情仍单独拒绝。 */
  @Test
  @DisplayName("R13/R15 单端点授权不会放行同组角色权限详情")
  void R13_RequiresPermissionForEachEndpoint() throws Exception {
    long listOnly =
        alternativePermission(
            "t09-role-list-only",
            "rbac.roles.read",
            api -> "GET".equals(api.getHttpMethod()) && ROLES.equals(api.getPathTemplate()));
    account("t09-list-reader", rootId, List.of(listOnly));
    String reader = token("t09-list-reader");

    success(request("GET", ROLES, reader, null), 200);
    assertError(request("GET", selected(adminRoleId), reader, null), 403, "PERMISSION_DENIED");
  }

  /** 父节点启停不级联直接按钮授权，其他启用角色仍可提供相同API。 */
  @Test
  @DisplayName("R13/R15 父菜单不隐含授权、多角色OR及无角色准入")
  void R13_KeepsDirectSelectionSemantics() throws Exception {
    String admin = token("t09-admin");
    long read = permissionIds.get("rbac.users.read");
    long first = account("t09-multi", childId, List.of(read));
    long second =
        new TransactionTemplate(transactions)
            .execute(
                status -> {
                  RoleEntity role = roles.save(new RoleEntity("t09-second", "另一个角色"));
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
  @DisplayName("R13/R15 关联写入约束冲突409、存储故障500均原子回滚")
  void R13_RollsBackStorageFailures() throws Exception {
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
  @DisplayName("R13/R15 两名管理员同时停用角色及撤空权限只能成功一次")
  void R13_SerializesRoleWriters() throws Exception {
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
  @DisplayName("R13/R15 与T07同时撤销不同维护者关键能力仍保留完整K")
  void R13_SerializesWithResourceChanges() throws Exception {
    long privateRead = alternativePermission("t09-private-users-read", "rbac.users.read");
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              RoleEntity admin = roles.findById(adminRoleId).orElseThrow();
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
  @DisplayName("R13/R15 并发创建同编码返回一次201与一次409")
  void R13_SerializesDuplicateCreation() throws Exception {
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
  @DisplayName("R13/R15 等待共同根锁后重新检查授权并拒绝过期请求")
  void R13_RechecksAfterWaiting() throws Exception {
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
  @DisplayName("R13/R15 不可用系统拒绝角色写入和再次管理登录")
  void R13_RejectsDisabledSystem() throws Exception {
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
    return alternativePermission(code, sourceCode, api -> true);
  }

  /** 选取具体API，允许构造只拥有同组一个端点的账号。 */
  private long alternativePermission(
      String code, String sourceCode, Predicate<ApiResourceEntity> allowed) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              PermissionEntity source =
                  permissions.findById(permissionIds.get(sourceCode)).orElseThrow();
              PermissionEntity alternative =
                  new PermissionEntity(
                      systems.findById(systemId).orElseThrow(), code, code, PermissionType.BUTTON);
              source.getApis().stream().filter(allowed).forEach(alternative::mapApi);
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
              RoleEntity role = roles.save(new RoleEntity(username, username));
              role.setManagementLevel(5);
              for (Long id : ids) role.grantPermission(permissions.findById(id).orElseThrow());
              UserEntity user =
                  new UserEntity(
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
    return rawRequest(
        method, path, token, payload == null ? null : json.writeValueAsString(payload));
  }

  /** 原样发送请求体以覆盖服务端的格式校验。 */
  private ResponseEntity<String> rawRequest(String method, String path, String token, String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) headers.set("Authorization", token);
    return http.exchange(
        URI.create(http.getRootUri() + path),
        HttpMethod.valueOf(method),
        new HttpEntity<>(body, headers),
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
