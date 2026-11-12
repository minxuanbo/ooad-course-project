package com.municipal.rbaccore.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.model.*;
import com.municipal.rbaccore.repository.*;
import com.municipal.rbaccore.security.RbacCatalog;
import com.municipal.rbaccore.service.EffectivePermissionService;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** T11通过真实MySQL和随机端口HTTP核对范围内角色分配、停用、调动与越权拒绝。 */
@SpringBootTest(
    classes = CoreRuntimeConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.config.name=application-core-user-test")
@ActiveProfiles("core-user-test")
class CoreUserAdminT11Test {
  private static final String LOGIN = "/api/auth/login";
  private static final String USERS = "/api/users";
  private static final String PASSWORD = "t11-isolated-fixture-password";
  private static final String ROOT = "t11-root";
  private static final String ROOT2 = "t11-root2";
  private static final String DEPT = "t11-dept";
  private static final String CHILD_ADMIN = "t11-child-admin";
  private static final String EMPLOYEE = "t11-employee";
  private static final String SIBLING = "t11-sibling";
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
  private long rootId;
  private long childId;
  private long grandchildId;
  private long siblingId;
  private long rootUserId;
  private long root2UserId;
  private long deptUserId;
  private long childAdminUserId;
  private long employeeUserId;
  private long siblingUserId;
  private long fullRoleId;
  private long personnelRoleId;
  private final Map<String, Long> permissionIds = new LinkedHashMap<>();

  /** 每例从可丢弃专库重建小样本组织与真实权限链，不使用生产初始化数据。 */
  @BeforeEach
  void t11Prepare() {
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
    permissionIds.clear();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              Department root = departments.save(new Department("t11-root", "核实根部门"));
              Department child = new Department("t11-child", "工程部");
              child.moveTo(root);
              departments.save(child);
              Department grandchild = new Department("t11-grandchild", "道路组");
              grandchild.moveTo(child);
              departments.save(grandchild);
              Department sibling = new Department("t11-sibling", "财务部");
              sibling.moveTo(root);
              departments.save(sibling);
              rootId = root.getId();
              childId = child.getId();
              grandchildId = grandchild.getId();
              siblingId = sibling.getId();
              ApplicationSystem system = systems.save(new ApplicationSystem("rbac", "权限管理系统"));
              for (Map.Entry<String, List<RbacCatalog.Route>> entry :
                  RbacCatalog.definitions().entrySet()) {
                Permission permission =
                    new Permission(system, entry.getKey(), entry.getKey(), Permission.Type.BUTTON);
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
    fullRoleId = role("t11-full", new ArrayList<>(permissionIds.values()), true);
    personnelRoleId =
        role(
            "t11-personnel",
            List.of(
                permissionIds.get("rbac.users.read"),
                permissionIds.get("rbac.users.roles.write"),
                permissionIds.get("rbac.users.disable"),
                permissionIds.get("rbac.users.department.write")),
            true);
    rootUserId = account(ROOT, rootId, List.of(fullRoleId));
    root2UserId = account(ROOT2, rootId, List.of(fullRoleId));
    deptUserId = account(DEPT, childId, List.of(personnelRoleId));
    childAdminUserId =
        account(
            CHILD_ADMIN,
            childId,
            List.of(
                role(
                    "t11-child-k",
                    List.of(
                        permissionIds.get("rbac.users.read"),
                        permissionIds.get("rbac.users.roles.write"),
                        permissionIds.get("rbac.resources.read"),
                        permissionIds.get("rbac.resources.write"),
                        permissionIds.get("rbac.roles.read"),
                        permissionIds.get("rbac.roles.write"),
                        permissionIds.get("rbac.roles.permissions.write")),
                    true)));
    employeeUserId = account(EMPLOYEE, grandchildId, List.of());
    siblingUserId = account(SIBLING, siblingId, List.of());
  }

  /** 当前角色读取含停用项，越界与未知目标按契约区分403/404。 */
  @Test
  @DisplayName("T11 范围内读取当前角色并拒绝同级部门")
  void t11ReadsRolesInScope() throws Exception {
    String root = token(ROOT);
    String dept = token(DEPT);
    JsonNode empty = success(request("GET", userRoles(employeeUserId), root, null), 200);
    assertEquals(employeeUserId, empty.get("user").get("id").asLong());
    assertEquals(6, empty.get("user").size());
    assertEquals(grandchildId, empty.get("user").get("departmentId").asLong());
    assertTrue(empty.get("user").get("enabled").asBoolean());
    assertEquals(0, empty.get("roles").size());

    success(request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(fullRoleId))), 200);
    JsonNode assigned = success(request("GET", userRoles(employeeUserId), dept, null), 200);
    assertEquals(1, assigned.get("roles").size());
    assertEquals("t11-full", assigned.get("roles").get(0).get("code").asText());
    assertEquals(4, assigned.get("roles").get(0).size());
    assertTrue(assigned.get("roles").get(0).get("enabled").asBoolean());

    assertError(request("GET", userRoles(siblingUserId), dept, null), 403, "MANAGEMENT_SCOPE_DENIED");
    assertError(request("GET", userRoles(900000001L), root, null), 404, "RESOURCE_NOT_FOUND");
    assertError(request("GET", userRoles(900000001L), dept, null), 403, "MANAGEMENT_SCOPE_DENIED");
  }

  /** 候选只返回启用且有效权限为本人子集的角色，搜索与分页同谓词。 */
  @Test
  @DisplayName("T11 候选角色按本人有效权限子集计算")
  void t11AssignableRolesAreSubsetOfActor() throws Exception {
    role("t11-logs", List.of(permissionIds.get("rbac.logs.read")), true);
    role("t11-roles-only", List.of(permissionIds.get("rbac.roles.read")), true);
    role("t11-disabled-hr", List.of(permissionIds.get("rbac.users.read")), false);
    String dept = token(DEPT);
    JsonNode page =
        success(request("GET", assignable(employeeUserId) + "?size=100", dept, null), 200);
    List<String> codes = new ArrayList<>();
    page.get("items").forEach(item -> codes.add(item.get("code").asText()));
    assertTrue(codes.contains("t11-personnel"), codes.toString());
    assertFalse(codes.contains("t11-full"), codes.toString());
    assertFalse(codes.contains("t11-child-k"), codes.toString());
    assertFalse(codes.contains("t11-logs"), codes.toString());
    assertFalse(codes.contains("t11-disabled-hr"), codes.toString());
    assertTrue(page.get("total").asInt() >= 1);
    assertEquals(page.get("total").asInt(), codes.size());
    page.get("items").forEach(item -> assertTrue(item.get("enabled").asBoolean()));

    JsonNode searched =
        success(request("GET", assignable(employeeUserId) + "?q=PERSON", dept, null), 200);
    assertEquals(1, searched.get("total").asInt());
    assertEquals("t11-personnel", searched.get("items").get(0).get("code").asText());
    assertEquals(
        0, success(request("GET", assignable(employeeUserId) + "?q=absent", dept, null), 200)
            .get("total").asInt());
    JsonNode paged =
        success(request("GET", assignable(employeeUserId) + "?size=1&page=0", dept, null), 200);
    assertEquals(1, paged.get("items").size());
    assertEquals(searched.get("total").asInt(), paged.get("total").asInt());

    String root = token(ROOT);
    JsonNode rootCandidates = success(request("GET", assignable(employeeUserId) + "?size=100", root, null), 200);
    assertTrue(rootCandidates.get("total").asInt() >= 5);
    assertError(request("GET", assignable(siblingUserId), dept, null), 403, "MANAGEMENT_SCOPE_DENIED");
  }

  /** 整体替换、空选清空以及非法/超权/停用角色全部失败且不改变已保存状态。 */
  @Test
  @DisplayName("T11 角色整体替换、幂等与整组校验")
  void t11ReplacesRolesAtomically() throws Exception {
    String root = token(ROOT);
    JsonNode assigned =
        success(request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))), 200);
    assertEquals(List.of(personnelRoleId), longs(assigned.get("roleIds")));
    assertTrue(assigned.get("enabled").asBoolean());
    assertEquals(6, assigned.size());
    success(request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))), 200);
    assertEquals(List.of(personnelRoleId), savedRoleIds(employeeUserId));

    long logs = role("t11-logs", List.of(permissionIds.get("rbac.logs.read")), true);
    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(logs, personnelRoleId))),
        200);
    assertEquals(List.of(personnelRoleId, logs), savedRoleIds(employeeUserId));

    success(request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of())), 200);
    assertTrue(savedRoleIds(employeeUserId).isEmpty());

    long disabled = role("t11-disabled", List.of(permissionIds.get("rbac.users.read")), false);
    assertError(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(disabled))),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request(
            "PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId, personnelRoleId))),
        400,
        "INVALID_ARGUMENT");
    assertError(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(900000001L))),
        400,
        "INVALID_ARGUMENT");
    assertError(request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of("x"))), 400, "INVALID_ARGUMENT");

    String dept = token(DEPT);
    assertError(
        request("PUT", userRoles(employeeUserId), dept, Map.of("ids", List.of(fullRoleId))),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    success(
        request("PUT", userRoles(employeeUserId), dept, Map.of("ids", List.of(personnelRoleId))), 200);
    assertError(
        request("PUT", userRoles(siblingUserId), dept, Map.of("ids", List.of())),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertEquals(List.of(personnelRoleId), savedRoleIds(employeeUserId));
    assertTrue(savedRoleIds(siblingUserId).isEmpty());
  }

  /** 撤权后旧令牌失去具体操作权限，再次管理登录按T04口径拒绝。 */
  @Test
  @DisplayName("T11 撤权即时影响后续请求与再次登录")
  void t11RevocationAffectsRequestsAndLogin() throws Exception {
    String root = token(ROOT);
    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))), 200);
    String employee = token(EMPLOYEE);
    success(request("GET", USERS, employee, null), 200);

    success(request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of())), 200);
    assertError(request("GET", USERS, employee, null), 403, "PERMISSION_DENIED");
    assertError(login(EMPLOYEE), 403, "MANAGEMENT_ACCESS_DENIED");
    assertTrue(savedRoleIds(employeeUserId).isEmpty());
    assertTrue(
        jdbc.queryForObject(
            "SELECT enabled FROM users WHERE id=?", Boolean.class, employeeUserId));
  }

  /** 停用保留角色与部门，旧令牌401、再次登录401且不接受恢复指令。 */
  @Test
  @DisplayName("T11 停用保留关联并使会话与登录失效")
  void t11DisableEndsSessionAndLogin() throws Exception {
    String root = token(ROOT);
    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))), 200);
    String employee = token(EMPLOYEE);
    JsonNode disabled =
        success(request("PATCH", status(employeeUserId), root, Map.of("enabled", false)), 200);
    assertFalse(disabled.get("enabled").asBoolean());
    assertEquals(grandchildId, disabled.get("departmentId").asLong());
    assertEquals(List.of(personnelRoleId), longs(disabled.get("roleIds")));

    assertError(request("GET", USERS, employee, null), 401, "SESSION_INVALID");
    assertError(login(EMPLOYEE), 401, "LOGIN_FAILED");
    assertError(
        request("PATCH", status(employeeUserId), root, Map.of("enabled", true)),
        400,
        "INVALID_ARGUMENT");
    JsonNode repeated =
        success(request("PATCH", status(employeeUserId), root, Map.of("enabled", false)), 200);
    assertFalse(repeated.get("enabled").asBoolean());
    assertEquals(List.of(personnelRoleId), savedRoleIds(employeeUserId));
    assertEquals(
        grandchildId,
        jdbc.queryForObject("SELECT department_id FROM users WHERE id=?", Long.class, employeeUserId));
  }

  /** 原部门与目标部门都要在范围内，失败保留原归属、状态与角色。 */
  @Test
  @DisplayName("T11 跨部门调动校验双范围并保留角色")
  void t11MovesWithinDoubleScope() throws Exception {
    String root = token(ROOT);
    String dept = token(DEPT);
    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))), 200);
    JsonNode moved =
        success(
            request("PUT", department(employeeUserId), dept, Map.of("departmentId", childId)), 200);
    assertEquals(childId, moved.get("departmentId").asLong());
    assertEquals(List.of(personnelRoleId), longs(moved.get("roleIds")));

    success(
        request("PUT", department(employeeUserId), dept, Map.of("departmentId", grandchildId)), 200);
    assertEquals(
        grandchildId,
        jdbc.queryForObject("SELECT department_id FROM users WHERE id=?", Long.class, employeeUserId));

    assertError(
        request("PUT", department(employeeUserId), dept, Map.of("departmentId", siblingId)),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PUT", department(employeeUserId), dept, Map.of("departmentId", 900000001L)),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PUT", department(employeeUserId), root, Map.of("departmentId", 900000001L)),
        404,
        "RESOURCE_NOT_FOUND");
    // 反向拉入外部人员同样失败。
    assertError(
        request("PUT", department(siblingUserId), dept, Map.of("departmentId", childId)),
        403,
        "MANAGEMENT_SCOPE_DENIED");

    assertEquals(
        grandchildId,
        jdbc.queryForObject("SELECT department_id FROM users WHERE id=?", Long.class, employeeUserId));
    assertEquals(List.of(personnelRoleId), savedRoleIds(employeeUserId));
    assertEquals(
        siblingId,
        jdbc.queryForObject("SELECT department_id FROM users WHERE id=?", Long.class, siblingUserId));
  }

  /** 部门管理员不能改自己或有效全局管理员，共同根自改仍受完整K约束。 */
  @Test
  @DisplayName("T11 自保护与全局管理员保护")
  void t11RejectsSelfAndGlobalAdministratorChanges() throws Exception {
    String dept = token(DEPT);
    assertError(
        request("PUT", userRoles(deptUserId), dept, Map.of("ids", List.of())),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PUT", department(deptUserId), dept, Map.of("departmentId", grandchildId)),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PUT", userRoles(childAdminUserId), dept, Map.of("ids", List.of())),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PATCH", status(childAdminUserId), dept, Map.of("enabled", false)),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertEquals(List.of(personnelRoleId), savedRoleIds(deptUserId));

    // 共同根可以修改自己，且第二名完整管理者存在时允许撤权。
    String root = token(ROOT);
    success(request("PUT", userRoles(rootUserId), root, Map.of("ids", List.of())), 200);
    assertEquals(1, completeAdministrators());
  }

  /** 最后一名完整K管理者不能被撤权、停用或移出共同根。 */
  @Test
  @DisplayName("T11 最后管理员保护覆盖撤权、停用与移出根")
  void t11ProtectsLastAdministrator() throws Exception {
    jdbc.update("DELETE FROM user_roles WHERE user_id=?", root2UserId);
    String root = token(ROOT);
    assertEquals(1, completeAdministrators());
    assertError(request("PUT", userRoles(rootUserId), root, Map.of("ids", List.of())), 409, "LAST_ADMIN_REQUIRED");
    assertError(request("PATCH", status(rootUserId), root, Map.of("enabled", false)), 409, "LAST_ADMIN_REQUIRED");
    assertError(
        request("PUT", department(rootUserId), root, Map.of("departmentId", childId)),
        409,
        "LAST_ADMIN_REQUIRED");
    assertEquals(1, completeAdministrators());
    assertEquals(List.of(fullRoleId), savedRoleIds(rootUserId));
    assertTrue(jdbc.queryForObject("SELECT enabled FROM users WHERE id=?", Boolean.class, rootUserId));

    jdbc.update(
        "INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", root2UserId, fullRoleId);
    assertEquals(2, completeAdministrators());
    success(request("PUT", userRoles(rootUserId), root, Map.of("ids", List.of())), 200);
    assertEquals(1, completeAdministrators());
  }

  /** 两名完整管理者并发撤权时只允许一个提交，不能把完整K清零。 */
  @Test
  @DisplayName("T11 并发撤权至少保留一名完整管理员")
  void t11ConcurrentRevocationsKeepOneAdministrator() throws Exception {
    String first = token(ROOT);
    String second = token(ROOT2);
    assertEquals(2, completeAdministrators());
    List<Integer> statuses =
        concurrently(
            () -> request("PUT", userRoles(root2UserId), first, Map.of("ids", List.of())),
            () -> request("PUT", userRoles(rootUserId), second, Map.of("ids", List.of())));
    assertEquals(1, statuses.stream().filter(status -> status == 200).count(), statuses::toString);
    assertEquals(1, statuses.stream().filter(status -> status != 200).count(), statuses::toString);
    assertEquals(1, completeAdministrators());
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
      Collections.sort(statuses);
      return statuses;
    } finally {
      executor.shutdownNow();
    }
  }

  /** 完整K只统计共同根下启用且具备全部维护能力的用户。 */
  private long completeAdministrators() {
    return jdbc
        .queryForList("SELECT id FROM users WHERE enabled=1 AND department_id=?", Long.class, rootId)
        .stream()
        .filter(id -> effective.routes(id).containsAll(RbacCatalog.maintenanceRoutes()))
        .count();
  }

  /** 角色以真实JPA权限关联建立，名称本身不产生授权。 */
  private long role(String code, List<Long> ids, boolean enabled) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              Role role = new Role(code, code);
              for (Long id : ids) role.grantPermission(permissions.findById(id).orElseThrow());
              role.setEnabled(enabled);
              return roles.save(role).getId();
            });
  }

  /** 测试账号沿真实JPA关系建立，部门决定管理范围。 */
  private long account(String username, long departmentId, List<Long> roleIds) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              User user =
                  new User(
                      username,
                      encoder.encode(PASSWORD),
                      username,
                      departments.findById(departmentId).orElseThrow(),
                      true);
              for (Long roleId : roleIds) user.assignRole(roles.findById(roleId).orElseThrow());
              return users.save(user).getId();
            });
  }

  /** 从已提交数据库核对整体替换结果，不依靠内存对象。 */
  private List<Long> savedRoleIds(long userId) {
    return jdbc.queryForList(
        "SELECT role_id FROM user_roles WHERE user_id=? ORDER BY role_id", Long.class, userId);
  }

  /** JSON数组转换为可比较的ID列表。 */
  private List<Long> longs(JsonNode array) {
    List<Long> values = new ArrayList<>();
    array.forEach(node -> values.add(node.asLong()));
    return values;
  }

  /** 路径构造集中，保持实际URL与已评审模板一致。 */
  private String userRoles(long id) {
    return USERS + "/" + id + "/roles";
  }

  private String assignable(long id) {
    return USERS + "/" + id + "/assignable-roles";
  }

  private String status(long id) {
    return USERS + "/" + id + "/status";
  }

  private String department(long id) {
    return USERS + "/" + id + "/department";
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
