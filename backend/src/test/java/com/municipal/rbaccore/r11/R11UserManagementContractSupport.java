package com.municipal.rbaccore.r11;

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
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.SessionFactory;
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

/** T11通过真实MySQL和随机端口HTTP核对范围内角色分配、停用、调动与越权拒绝。 */
abstract class R11UserManagementContractSupport {
  private static final String LOGIN = "/api/auth/login";
  private static final String USERS = "/api/users";
  private static final String ROLES = "/api/roles";
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
  @Autowired DepartmentJpaRepository departments;
  @Autowired UserJpaRepository users;
  @Autowired RoleJpaRepository roles;
  @Autowired PermissionJpaRepository permissions;
  @Autowired ApiResourceJpaRepository apis;
  @Autowired ApplicationSystemJpaRepository systems;
  @Autowired PlatformTransactionManager transactions;
  @Autowired ManagementAuthorization effective;
  @Autowired EntityManagerFactory entityManagerFactory;
  @Autowired LockObservation observedLock;
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

  /** 仅观察实际HTTP请求到达共同根锁的时刻，不替换仓储或鉴权实现。 */
  @TestConfiguration(proxyBeanMethods = false)
  static class LockFixtures {
    /** 为范围变更竞争提供确定的同步点，不使用固定睡眠。 */
    @Bean
    LockObservation lockObservation() {
      return new LockObservation();
    }
  }

  /** 同步持锁事务与已经完成AOP检查的请求，实际锁仍由MySQL执行。 */
  @Aspect
  static class LockObservation {
    private final AtomicReference<CountDownLatch> entered = new AtomicReference<>();

    /** 请求将获取数据库锁时通知夹具，之后继续执行原方法。 */
    @Before(
        "execution(*"
            + " com.municipal.rbaccore.service.structured.security.StructuredMaintenanceGuard.lock(..))")
    public void beforeLock() {
      CountDownLatch gate = entered.get();
      if (gate != null && RequestAuditFilter.current() != null) gate.countDown();
    }
  }

  /** 每例从可丢弃专库重建小样本组织与真实权限链，不使用生产初始化数据。 */
  @BeforeEach
  void R11_Prepare() {
    assertEquals("rbac_core_t11_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    jdbc.execute("DROP TRIGGER IF EXISTS t11_fail_roles");
    jdbc.execute("DROP TRIGGER IF EXISTS t11_fail_move");
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
              DepartmentEntity root = departments.save(new DepartmentEntity("t11-root", "核实根部门"));
              DepartmentEntity child = new DepartmentEntity("t11-child", "工程部");
              child.moveTo(root);
              departments.save(child);
              DepartmentEntity grandchild = new DepartmentEntity("t11-grandchild", "道路组");
              grandchild.moveTo(child);
              departments.save(grandchild);
              DepartmentEntity sibling = new DepartmentEntity("t11-sibling", "财务部");
              sibling.moveTo(root);
              departments.save(sibling);
              rootId = root.getId();
              childId = child.getId();
              grandchildId = grandchild.getId();
              siblingId = sibling.getId();
              ApplicationSystemEntity system =
                  systems.save(new ApplicationSystemEntity("rbac", "权限管理系统"));
              for (Map.Entry<String, List<RbacCatalog.Route>> entry :
                  RbacCatalog.definitions().entrySet()) {
                PermissionEntity permission =
                    new PermissionEntity(
                        system, entry.getKey(), entry.getKey(), PermissionType.BUTTON);
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
  @DisplayName("R11/R12/R15 范围内读取当前角色并拒绝同级部门")
  void R11_ReadsRolesInScope() throws Exception {
    String root = token(ROOT);
    String dept = token(DEPT);
    JsonNode empty = success(request("GET", userRoles(employeeUserId), root, null), 200);
    assertEquals(employeeUserId, empty.get("user").get("id").asLong());
    assertEquals(6, empty.get("user").size());
    assertEquals(grandchildId, empty.get("user").get("departmentId").asLong());
    assertTrue(empty.get("user").get("enabled").asBoolean());
    assertEquals(0, empty.get("roles").size());

    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(fullRoleId))), 200);
    JsonNode assigned = success(request("GET", userRoles(employeeUserId), dept, null), 200);
    assertEquals(1, assigned.get("roles").size());
    assertEquals("t11-full", assigned.get("roles").get(0).get("code").asText());
    assertEquals(5, assigned.get("roles").get(0).size());
    assertTrue(assigned.get("roles").get(0).get("enabled").asBoolean());
    long disabledRole =
        role("t11-current-disabled", List.of(permissionIds.get("rbac.users.read")), false);
    jdbc.update(
        "INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", employeeUserId, disabledRole);
    JsonNode retained = success(request("GET", userRoles(employeeUserId), dept, null), 200);
    assertEquals(2, retained.get("roles").size());
    assertFalse(retained.get("roles").get(1).get("enabled").asBoolean());

    assertError(
        request("GET", userRoles(siblingUserId), dept, null), 403, "MANAGEMENT_SCOPE_DENIED");
    assertError(request("GET", userRoles(900000001L), root, null), 404, "RESOURCE_NOT_FOUND");
    assertError(request("GET", userRoles(900000001L), dept, null), 403, "MANAGEMENT_SCOPE_DENIED");
  }

  /** 候选只返回启用且有效权限为本人子集的角色，搜索与分页同谓词。 */
  @Test
  @DisplayName("R11/R12/R15 候选角色按本人有效权限子集计算")
  void R11_AssignableRolesAreSubsetOfActor() throws Exception {
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
        0,
        success(request("GET", assignable(employeeUserId) + "?q=absent", dept, null), 200)
            .get("total")
            .asInt());
    JsonNode paged =
        success(request("GET", assignable(employeeUserId) + "?size=1&page=0", dept, null), 200);
    assertEquals(1, paged.get("items").size());
    assertEquals(searched.get("total").asInt(), paged.get("total").asInt());

    String root = token(ROOT);
    JsonNode rootCandidates =
        success(request("GET", assignable(employeeUserId) + "?size=100", root, null), 200);
    assertTrue(rootCandidates.get("total").asInt() >= 5);
    assertError(
        request("GET", assignable(siblingUserId), dept, null), 403, "MANAGEMENT_SCOPE_DENIED");
  }

  /** 整体替换、空选清空以及非法/超权/停用角色全部失败且不改变已保存状态。 */
  @Test
  @DisplayName("R11/R12/R15 角色整体替换、幂等与整组校验")
  void R11_ReplacesRolesAtomically() throws Exception {
    String root = token(ROOT);
    JsonNode assigned =
        success(
            request(
                "PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))),
            200);
    assertEquals(List.of(personnelRoleId), longs(assigned.get("roleIds")));
    assertTrue(assigned.get("enabled").asBoolean());
    assertEquals(6, assigned.size());

    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))),
        200);
    assertEquals(List.of(personnelRoleId), savedRoleIds(employeeUserId));

    long logs = role("t11-logs", List.of(permissionIds.get("rbac.logs.read")), true);

    success(
        request(
            "PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(logs, personnelRoleId))),
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
            "PUT",
            userRoles(employeeUserId),
            root,
            Map.of("ids", List.of(personnelRoleId, personnelRoleId))),
        400,
        "INVALID_ARGUMENT");
    assertError(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(900000001L))),
        400,
        "INVALID_ARGUMENT");
    assertError(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of("x"))),
        400,
        "INVALID_ARGUMENT");

    String dept = token(DEPT);
    assertError(
        request("PUT", userRoles(employeeUserId), dept, Map.of("ids", List.of(fullRoleId))),
        403,
        "MANAGEMENT_SCOPE_DENIED");

    success(
        request("PUT", userRoles(employeeUserId), dept, Map.of("ids", List.of(personnelRoleId))),
        200);
    assertError(
        request("PUT", userRoles(siblingUserId), dept, Map.of("ids", List.of())),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertEquals(List.of(personnelRoleId), savedRoleIds(employeeUserId));
    assertTrue(savedRoleIds(siblingUserId).isEmpty());
  }

  /** 撤权后旧令牌失去具体操作权限，再次管理登录按T04口径拒绝。 */
  @Test
  @DisplayName("R11/R12/R15 撤权即时影响后续请求与再次登录")
  void R11_RevocationAffectsRequestsAndLogin() throws Exception {
    String root = token(ROOT);

    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))),
        200);
    String employee = token(EMPLOYEE);

    success(request("GET", USERS, employee, null), 200);

    success(request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of())), 200);
    assertError(request("GET", USERS, employee, null), 403, "PERMISSION_DENIED");
    assertError(login(EMPLOYEE), 403, "MANAGEMENT_ACCESS_DENIED");
    assertTrue(savedRoleIds(employeeUserId).isEmpty());
    assertTrue(
        jdbc.queryForObject("SELECT enabled FROM users WHERE id=?", Boolean.class, employeeUserId));
  }

  /** 停用保留角色与部门，旧令牌401、再次登录401且不接受恢复指令。 */
  @Test
  @DisplayName("R11/R12/R15 停用保留关联并使会话与登录失效")
  void R11_DisableEndsSessionAndLogin() throws Exception {
    String root = token(ROOT);

    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))),
        200);
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
        jdbc.queryForObject(
            "SELECT department_id FROM users WHERE id=?", Long.class, employeeUserId));
    String dept = token(DEPT);

    assertError(
        request("PATCH", status(deptUserId), dept, Map.of("enabled", false)),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    success(request("PATCH", status(deptUserId), root, Map.of("enabled", false)), 200);
    assertError(request("GET", USERS, dept, null), 401, "SESSION_INVALID");
    assertError(login(DEPT), 401, "LOGIN_FAILED");
  }

  /** 原部门与目标部门都要在范围内，失败保留原归属、状态与角色。 */
  @Test
  @DisplayName("R11/R12/R15 跨部门调动校验双范围并保留角色")
  void R11_MovesWithinDoubleScope() throws Exception {
    String root = token(ROOT);
    String dept = token(DEPT);

    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))),
        200);
    String employee = token(EMPLOYEE);
    assertError(
        request("PUT", department(employeeUserId), dept, Map.of("departmentId", childId)),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    // 保留低级管理者的双范围移动场景，原同级人员不再允许局部管理者修改。
    jdbc.update("UPDATE roles SET management_level=2 WHERE id=?", personnelRoleId);
    jdbc.update(
        "UPDATE roles SET management_level=3 WHERE id=?",
        role(
            "t11-dept-higher",
            List.of(
                permissionIds.get("rbac.users.read"),
                permissionIds.get("rbac.users.department.write")),
            true));
    jdbc.update(
        "INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE"
            + " code='t11-dept-higher'",
        deptUserId);
    assertEquals(1, success(request("GET", USERS, employee, null), 200).get("total").asInt());
    JsonNode moved =
        success(
            request("PUT", department(employeeUserId), dept, Map.of("departmentId", childId)), 200);
    assertEquals(childId, moved.get("departmentId").asLong());
    assertEquals(List.of(personnelRoleId), longs(moved.get("roleIds")));
    assertEquals(3, success(request("GET", USERS, employee, null), 200).get("total").asInt());

    success(
        request("PUT", department(employeeUserId), dept, Map.of("departmentId", grandchildId)),
        200);
    assertEquals(1, success(request("GET", USERS, employee, null), 200).get("total").asInt());
    assertEquals(
        grandchildId,
        jdbc.queryForObject(
            "SELECT department_id FROM users WHERE id=?", Long.class, employeeUserId));

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
        jdbc.queryForObject(
            "SELECT department_id FROM users WHERE id=?", Long.class, employeeUserId));
    assertEquals(List.of(personnelRoleId), savedRoleIds(employeeUserId));
    assertEquals(
        siblingId,
        jdbc.queryForObject(
            "SELECT department_id FROM users WHERE id=?", Long.class, siblingUserId));
  }

  /** 部门管理员不能改自己或有效全局管理员，共同根自改仍受完整K约束。 */
  @Test
  @DisplayName("R11/R12/R15 自保护与全局管理员保护")
  void R11_RejectsSelfAndGlobalAdministratorChanges() throws Exception {
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
        request("PUT", userRoles(rootUserId), dept, Map.of("ids", List.of())),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PATCH", status(rootUserId), dept, Map.of("enabled", false)),
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
  @DisplayName("R11/R12/R15 最后管理员保护覆盖撤权、停用与移出根")
  void R11_ProtectsLastAdministrator() throws Exception {
    jdbc.update("DELETE FROM user_roles WHERE user_id=?", root2UserId);
    String root = token(ROOT);
    assertEquals(1, completeAdministrators());
    assertError(
        request("PUT", userRoles(rootUserId), root, Map.of("ids", List.of())),
        409,
        "LAST_ADMIN_REQUIRED");
    assertError(
        request("PATCH", status(rootUserId), root, Map.of("enabled", false)),
        409,
        "LAST_ADMIN_REQUIRED");
    assertError(
        request("PUT", department(rootUserId), root, Map.of("departmentId", childId)),
        409,
        "LAST_ADMIN_REQUIRED");
    assertEquals(1, completeAdministrators());
    assertEquals(List.of(fullRoleId), savedRoleIds(rootUserId));
    assertTrue(
        jdbc.queryForObject("SELECT enabled FROM users WHERE id=?", Boolean.class, rootUserId));

    jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", root2UserId, fullRoleId);
    assertEquals(2, completeAdministrators());

    success(request("PUT", userRoles(rootUserId), root, Map.of("ids", List.of())), 200);
    assertEquals(1, completeAdministrators());
  }

  /** 两名完整管理者并发撤权时只允许一个提交，不能把完整K清零。 */
  @Test
  @DisplayName("R11/R12/R15 并发撤权至少保留一名完整管理员")
  void R11_ConcurrentRevocationsKeepOneAdministrator() throws Exception {
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

  /** 同路径的不同权限仍是不同授权，候选和保存都必须满足实际权限子集。 */
  @Test
  @DisplayName("R11/R12/R15 相同API不代表可以授出本人未拥有的有效权限")
  void R11_RejectsDifferentPermissionForSameApi() throws Exception {
    long alias = permission("t11-alias", false);
    long aliasRole = role("t11-alias-role", List.of(alias), true);
    String dept = token(DEPT);
    JsonNode page =
        success(request("GET", assignable(employeeUserId) + "?size=100", dept, null), 200);
    for (JsonNode item : page.get("items")) assertNotEquals(aliasRole, item.get("id").asLong());
    assertError(
        request("PUT", userRoles(employeeUserId), dept, Map.of("ids", List.of(aliasRole))),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertTrue(savedRoleIds(employeeUserId).isEmpty());
    long stale = role("t11-stale-candidate", List.of(permissionIds.get("rbac.users.read")), true);
    assertEquals(
        1,
        success(
                request("GET", assignable(employeeUserId) + "?q=t11-stale-candidate", dept, null),
                200)
            .get("total")
            .asInt());

    success(
        request(
            "PUT",
            ROLES + "/" + stale + "/permissions",
            token(ROOT),
            Map.of("ids", List.of(permissionIds.get("rbac.users.read"), alias))),
        200);
    assertEquals(
        0,
        success(
                request("GET", assignable(employeeUserId) + "?q=t11-stale-candidate", dept, null),
                200)
            .get("total")
            .asInt());
    assertError(
        request("PUT", userRoles(employeeUserId), dept, Map.of("ids", List.of(stale))),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertTrue(savedRoleIds(employeeUserId).isEmpty());
  }

  /** 外系统及混合角色的有效权限不能被系统过滤丢弃后伪装成合法空集。 */
  @Test
  @DisplayName("R11/R12/R15 外系统及混合角色不出现在候选且不能保存")
  void R11_RejectsExternalRoleGrants() throws Exception {
    long foreign = permission("t11-foreign", true);
    long foreignRole = role("t11-foreign-role", List.of(foreign), true);
    long mixedRole =
        role("t11-mixed-role", List.of(foreign, permissionIds.get("rbac.users.read")), true);
    String root = token(ROOT);
    JsonNode page =
        success(request("GET", assignable(employeeUserId) + "?size=100", root, null), 200);
    for (JsonNode item : page.get("items")) {
      assertNotEquals(foreignRole, item.get("id").asLong());
      assertNotEquals(mixedRole, item.get("id").asLong());
    }
    for (Long id : List.of(foreignRole, mixedRole)) {
      assertError(
          request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(id))),
          403,
          "MANAGEMENT_SCOPE_DENIED");
    }
    assertTrue(savedRoleIds(employeeUserId).isEmpty());
  }

  /** 完整K必须同时满足根归属，不能把范围内的非根人员当成全局维护者。 */
  @Test
  @DisplayName("R11/R12/R15 非根完整K人员仍可按正常范围规则撤权停用与调动")
  void R11_DoesNotTreatNonRootKUserAsGlobalAdministrator() throws Exception {
    String dept = token(DEPT);

    success(request("PUT", userRoles(childAdminUserId), dept, Map.of("ids", List.of())), 200);

    success(request("PATCH", status(childAdminUserId), dept, Map.of("enabled", false)), 200);

    success(
        request("PUT", department(childAdminUserId), dept, Map.of("departmentId", grandchildId)),
        200);
    assertEquals(2, completeAdministrators());
  }

  /** 当身份、权限或目标范围不满足时，不能先用非法输入返回400。 */
  @Test
  @DisplayName("R11/R12/R15 人员接口先验证身份权限及目标范围再解析请求")
  void R11_ChecksAccessBeforeInvalidInput() throws Exception {
    assertError(
        request("PATCH", status(employeeUserId), null, Map.of()), 401, "AUTHENTICATION_REQUIRED");
    long readerRole = role("t11-reader", List.of(permissionIds.get("rbac.users.read")), true);
    account("t11-reader", childId, List.of(readerRole));
    assertError(
        request(
            "PUT", userRoles(employeeUserId), token("t11-reader"), Map.of("ids", List.of(1L, 1L))),
        403,
        "PERMISSION_DENIED");
    String dept = token(DEPT);
    assertError(
        request("PUT", userRoles(siblingUserId), dept, Map.of("ids", List.of(1L, 1L))),
        403,
        "MANAGEMENT_SCOPE_DENIED");
    assertError(
        request("PUT", department(deptUserId), dept, Map.of()), 403, "MANAGEMENT_SCOPE_DENIED");
  }

  /** 页大小约束实际角色实体读取量，不能读取全部候选后在内存裁剪。 */
  @Test
  @DisplayName("R11/R12/R15 候选过滤计数分页在数据库完成且避免逐角色查询")
  void R11_LoadsOnlyRequestedCandidatePage() throws Exception {
    for (int index = 0; index < 50; index++) role("t11-empty-" + index, List.of(), true);
    String dept = token(DEPT);
    SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
    factory.getStatistics().clear();
    JsonNode page =
        success(request("GET", assignable(employeeUserId) + "?size=2", dept, null), 200);
    assertEquals(51, page.get("total").asLong());
    assertEquals(2, page.get("items").size());
    long loaded =
        factory.getStatistics().getEntityStatistics(RoleEntity.class.getName()).getLoadCount();
    assertTrue(loaded <= 8, "角色实体实际加载数=" + loaded);
  }

  /** 两种保存故障分别核对稳定错误码及已提交数据不被部分覆盖。 */
  @Test
  @DisplayName("R11/R12/R15 数据库保存故障回滚角色及调动并返回稳定错误")
  void R11_RollsBackStorageFailures() throws Exception {
    String root = token(ROOT);
    long previousRole = role("t11-previous", List.of(permissionIds.get("rbac.users.read")), true);

    success(
        request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(previousRole))), 200);
    jdbc.execute(
        "CREATE TRIGGER t11_fail_roles BEFORE INSERT ON user_roles FOR EACH ROW BEGIN IF"
            + " NEW.user_id="
            + employeeUserId
            + " AND NEW.role_id="
            + personnelRoleId
            + " THEN SIGNAL SQLSTATE '23000' SET MYSQL_ERRNO=1062,MESSAGE_TEXT='T11 forced"
            + " conflict'; END IF; END");
    try {
      assertError(
          request("PUT", userRoles(employeeUserId), root, Map.of("ids", List.of(personnelRoleId))),
          409,
          "DATA_CONFLICT");
      assertEquals(List.of(previousRole), savedRoleIds(employeeUserId));
    } finally {
      jdbc.execute("DROP TRIGGER t11_fail_roles");
    }
    jdbc.execute(
        "CREATE TRIGGER t11_fail_move BEFORE UPDATE ON users FOR EACH ROW BEGIN IF NEW.id="
            + employeeUserId
            + " AND NEW.department_id="
            + childId
            + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T11 forced storage failure'; END IF;"
            + " END");
    try {
      assertError(
          request("PUT", department(employeeUserId), root, Map.of("departmentId", childId)),
          500,
          "INTERNAL_ERROR");
      assertEquals(
          grandchildId,
          jdbc.queryForObject(
              "SELECT department_id FROM users WHERE id=?", Long.class, employeeUserId));
      assertEquals(List.of(previousRole), savedRoleIds(employeeUserId));
    } finally {
      jdbc.execute("DROP TRIGGER t11_fail_move");
    }

    success(request("PUT", department(employeeUserId), root, Map.of("departmentId", childId)), 200);
  }

  /** 跨角色维护与人员停用入口也必须按同一个根锁保护同一完整K计数。 */
  @Test
  @DisplayName("R11/R12/R15 与T09并发变更不能同时清空两名完整维护者")
  void R11_SharesProtectionWithRoleMaintenance() throws Exception {
    long backup = role("t11-backup", new ArrayList<>(permissionIds.values()), true);
    jdbc.update("DELETE FROM user_roles WHERE user_id=?", root2UserId);
    jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", root2UserId, backup);
    long partial =
        role(
            "t11-partial-operator",
            List.of(permissionIds.get("rbac.roles.write"), permissionIds.get("rbac.users.disable")),
            true);
    account("t11-partial-operator", rootId, List.of(partial));
    String operator = token("t11-partial-operator");
    List<Integer> statuses =
        concurrently(
            () -> request("PATCH", ROLES + "/" + backup, operator, Map.of("enabled", false)),
            () -> request("PATCH", status(rootUserId), operator, Map.of("enabled", false)));
    assertEquals(List.of(200, 409), statuses);
    assertEquals(1, completeAdministrators());
  }

  /** 目标在请求等待共同根锁期间调出范围，写事务拿锁后必须读取新归属。 */
  @Test
  @DisplayName("R11/R12/R15 锁等待期间目标调出范围后拒绝旧请求")
  void R11_RechecksTargetScopeAfterLockWait() throws Exception {
    String dept = token(DEPT);
    CountDownLatch entered = new CountDownLatch(1);
    observedLock.entered.set(entered);
    AtomicReference<Future<ResponseEntity<String>>> future = new AtomicReference<>();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              transaction -> {
                departments.lockById(rootId).orElseThrow();
                future.set(
                    executor.submit(
                        () ->
                            request(
                                "PATCH", status(employeeUserId), dept, Map.of("enabled", false))));
                try {
                  assertTrue(entered.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(exception);
                }
                jdbc.update(
                    "UPDATE users SET department_id=? WHERE id=?", siblingId, employeeUserId);
              });
      assertError(future.get().get(15, TimeUnit.SECONDS), 403, "MANAGEMENT_SCOPE_DENIED");
      assertTrue(
          jdbc.queryForObject(
              "SELECT enabled FROM users WHERE id=?", Boolean.class, employeeUserId));
      assertEquals(
          siblingId,
          jdbc.queryForObject(
              "SELECT department_id FROM users WHERE id=?", Long.class, employeeUserId));
    } finally {
      observedLock.entered.set(null);
      executor.shutdownNow();
    }
  }

  /** 为安全边界构造不同权限共享同路径的真实关联，不作为生产初始化。 */
  private long permission(String code, boolean foreign) {
    return new TransactionTemplate(transactions)
        .execute(
            transaction -> {
              ApplicationSystemEntity system =
                  foreign
                      ? systems.save(new ApplicationSystemEntity(code, "测试外系统"))
                      : systems.findAll().getFirst();
              PermissionEntity permission =
                  new PermissionEntity(system, code, code, PermissionType.BUTTON);
              if (foreign)
                permission.mapApi(apis.save(new ApiResourceEntity(system, "GET", USERS)));
              else
                permission.mapApi(
                    apis.findAll().stream()
                        .filter(
                            api ->
                                api.getHttpMethod().equals("GET")
                                    && api.getPathTemplate().equals(USERS))
                        .findFirst()
                        .orElseThrow());
              return permissions.save(permission).getId();
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
      Collections.sort(statuses);
      return statuses;
    } finally {
      executor.shutdownNow();
    }
  }

  /** 完整K只统计共同根下启用且具备全部维护能力的用户。 */
  private long completeAdministrators() {
    return jdbc
        .queryForList(
            "SELECT id FROM users WHERE enabled=1 AND department_id=?", Long.class, rootId)
        .stream()
        .filter(id -> effective.routes(id).containsAll(RbacCatalog.maintenanceRoutes()))
        .count();
  }

  /** 角色以真实JPA权限关联建立，名称本身不产生授权。 */
  private long role(String code, List<Long> ids, boolean enabled) {
    return new TransactionTemplate(transactions)
        .execute(
            status -> {
              RoleEntity role = new RoleEntity(code, code);
              role.setManagementLevel(
                  code.equals("t11-full")
                          || code.equals("t11-backup")
                          || code.equals("t11-partial-operator")
                      ? 5
                      : code.equals("t11-personnel") ? 3 : 2);
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
              UserEntity user =
                  new UserEntity(
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

  /** 候选路径只携带目标人员ID，不提交前端范围。 */
  private String assignable(long id) {
    return USERS + "/" + id + "/assignable-roles";
  }

  /** 停用使用人员状态资源，不提供恢复路由。 */
  private String status(long id) {
    return USERS + "/" + id + "/status";
  }

  /** 调动使用单一部门归属替换接口。 */
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
