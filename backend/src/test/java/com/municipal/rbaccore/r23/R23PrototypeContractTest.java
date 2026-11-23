package com.municipal.rbaccore.r23;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.municipal.rbaccore.config.RuntimeConfiguration;
import com.municipal.rbaccore.infrastructure.persistence.ddd.JpaAuthorizationRepositoryAdapter;
import com.municipal.rbaccore.infrastructure.persistence.ddd.JpaUserRepositoryAdapter;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.OrganizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.Role;
import com.municipal.rbaccore.service.ddd.domain.model.SearchCriteria;
import com.municipal.rbaccore.service.port.department.DepartmentUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.identity.IdentityDirectoryGateway;
import com.municipal.rbaccore.service.port.role.RoleResult;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import com.municipal.rbaccore.service.port.security.RequestContext;
import com.municipal.rbaccore.service.port.user.AssignableRoleQuery;
import com.municipal.rbaccore.service.port.user.UserQuery;
import com.municipal.rbaccore.service.port.user.UserQueryUseCase;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.security.StructuredDepartmentScopeService;
import com.municipal.rbaccore.service.structured.security.StructuredEffectivePermissionService;
import com.municipal.rbaccore.service.structured.security.StructuredMaintenanceGuard;
import com.municipal.rbaccore.service.structured.security.StructuredPersonnelAuthority;
import com.municipal.rbaccore.service.structured.user.StructuredUserManagementService;
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
  private static final String OVERVIEW = "/api/overview";
  private static final String ME = "/api/me";
  private static final String MY_PERMISSIONS = "/api/me/permissions";
  private static final String LOGIN = "/api/auth/login";
  private static final String DEPARTMENTS = "/api/departments";
  private static final String USERS = "/api/users";
  private static final String PERMISSION_TREE = "/api/permissions/tree?systemId=1";
  private static final String PASSWORD = "isolated-stage4-fixture";
  @Autowired
  TestRestTemplate http;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  PasswordEncoder encoder;
  @Autowired
  DepartmentUseCase departmentService;
  @Autowired
  UserQueryUseCase userQueries;
  @Autowired
  UserJpaRepository userRows;
  @Autowired
  RoleJpaRepository roleRows;
  @Autowired
  DepartmentJpaRepository departmentRows;
  @Autowired
  IdentityDirectoryGateway directory;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  /** 本人权限投影保留真实父ID，不凭导航祖先补发授权。 */
  @Test
  @DisplayName("R08 本人已授权子权限保留父ID但不额外授予祖先")
  void R08_SelectedChildRetainsItsParentId() {
    Long child =
        jdbc.queryForObject(
            "SELECT MIN(id) FROM permissions WHERE system_id=1 AND id<>1", Long.class);
    jdbc.update("UPDATE permissions SET parent_id=1 WHERE id=?", child);
    try {
      JsonNode data = call(HttpMethod.GET, MY_PERMISSIONS, token(1), null).getBody().path("data");
      JsonNode selected =
          java.util.stream.StreamSupport.stream(data.spliterator(), false)
              .filter(item -> item.path("id").asLong() == child)
              .findFirst()
              .orElseThrow();
      assertEquals(1, selected.path("parentId").asInt());
    } finally {
      jdbc.update("UPDATE permissions SET parent_id=NULL WHERE id=?", child);
    }
  }

  /** 空有效权限集合应由真实SQL排除非空授权角色，不退回全量候选。 */
  @Test
  @DisplayName("R24 结构化候选真实JPA对空权限集合只保留空授权角色")
  void R24_StructuredCandidateQueryWithNoEffectivePermissions() {
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              StructuredAuthenticationService auth =
                  org.mockito.Mockito.mock(StructuredAuthenticationService.class);
              CurrentIdentity actor = new CurrentIdentity(1L, "stage4-1", 1L);
              org.mockito.Mockito.when(auth.require("Bearer boundary")).thenReturn(actor);
              StructuredDepartmentScopeService scope =
                  org.mockito.Mockito.mock(StructuredDepartmentScopeService.class);
              org.mockito.Mockito.when(scope.allowedIds(actor)).thenReturn(List.of(1L, 2L, 3L));
              StructuredPersonnelAuthority authority =
                  org.mockito.Mockito.mock(StructuredPersonnelAuthority.class);
              org.mockito.Mockito.when(authority.snapshot(actor))
                  .thenReturn(new StructuredPersonnelAuthority.AuthoritySnapshot(1L, 5, true));
              org.mockito.Mockito.when(
                      authority.canManage(
                          org.mockito.ArgumentMatchers.any(
                              StructuredPersonnelAuthority.AuthoritySnapshot.class),
                          org.mockito.ArgumentMatchers.any()))
                  .thenReturn(true);
              StructuredUserManagementService service =
                  new StructuredUserManagementService(
                      () -> java.util.Set.of("rbac", "business"),
                      auth,
                      org.mockito.Mockito.mock(RequestContext.class),
                      org.mockito.Mockito.mock(StructuredEffectivePermissionService.class),
                      scope,
                      org.mockito.Mockito.mock(StructuredMaintenanceGuard.class),
                      userRows,
                      authority,
                      roleRows,
                      departmentRows);
              AssignableRoleQuery query = new AssignableRoleQuery();
              query.setSize(20);
              assertEquals(
                  List.of("custom"),
                  service.assignableRoles("Bearer boundary", 4L, query).getItems().stream()
                      .map(RoleResult::getCode)
                      .toList());
              status.setRollbackOnly();
            });
  }

  /** 物理外键仍允许历史跨系统父引用，查询不能丢弃损坏节点而伪造完整树。 */
  @Test
  @DisplayName("R14 跨应用父引用损坏时权限树查询明确409且不泄漏外应用节点")
  void R14_PermissionTreeRejectsCrossApplicationParent() {
    String admin = token(1);
    jdbc.update("UPDATE permissions SET parent_id=999 WHERE id=1");
    try {
      ResponseEntity<JsonNode> response = call(HttpMethod.GET, PERMISSION_TREE, admin, null);
      assertEquals(409, response.getStatusCode().value());
      assertEquals("DATA_CONFLICT", response.getBody().path("code").asText());
      assertTrue(response.getBody().path("data").isNull());
    } finally {
      jdbc.update("UPDATE permissions SET parent_id=NULL WHERE id=1");
    }
  }

  /** 数据库先过滤状态和范围，空范围及极大页码不能退回全量数据。 */
  @Test
  @DisplayName("R10/R29 真实JPA查询处理空范围、停用筛选、越末页及墓碑")
  void R10_ReadAdaptersKeepEmptyScopesAndTypedFilters() {
    String admin = token(1);
    jdbc.update("UPDATE users SET enabled=0 WHERE id=4");
    JsonNode filtered =
        call(HttpMethod.GET, USERS + "?enabled=false", admin, null).getBody().path("data");
    assertEquals(1, filtered.path("total").asInt());
    assertEquals(4, filtered.path("items").get(0).path("id").asInt());
    UserQuery query = new UserQuery();
    query.setSize(20);
    query.setEnabled(false);
    query.setDepartmentId(2L);
    assertEquals(1, userQueries.list("Bearer " + admin, query).getTotal());
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              JpaUserRepositoryAdapter adapter =
                  new JpaUserRepositoryAdapter(userRows, roleRows, departmentRows);
              OrganizationFacts.Scope empty = new OrganizationFacts.Scope(java.util.Set.of());
              assertEquals(
                  0,
                  adapter.search(new SearchCriteria.Users(empty, null, null, null, 0, 20)).total());
              assertEquals(0, adapter.statistics(empty).total());
              JpaAuthorizationRepositoryAdapter authorization =
                  new JpaAuthorizationRepositoryAdapter(userRows, roleRows, departmentRows);
              assertEquals(
                  List.of("custom"),
                  authorization
                      .candidates(
                          new AuthorizationFacts.AssignableRoleCriteria(
                              5, true, java.util.Set.of(), null, 0, 20))
                      .items()
                      .stream()
                      .map(Role::code)
                      .toList());
              assertNull(directory.department(1L).orElseThrow().parentId());
              assertEquals(1L, directory.department(2L).orElseThrow().parentId());
              assertTrue(directory.users(1L, null, 2, 20).items().isEmpty());
              assertTrue(directory.users(1L, null, Integer.MAX_VALUE, 100).items().isEmpty());
              assertTrue(
                  directory
                      .departments(1L, 1L, java.util.Set.of(1L, 2L, 3L), null, 2, 20)
                      .items()
                      .isEmpty());
              assertTrue(
                  directory
                      .departments(
                          1L, 1L, java.util.Set.of(1L, 2L, 3L), null, Integer.MAX_VALUE, 100)
                      .items()
                      .isEmpty());
              jdbc.update("UPDATE departments SET deleted=1 WHERE id=3");
              assertTrue(directory.department(3L).isEmpty());
              status.setRollbackOnly();
            });
  }

  /** 所有父子类型逐项通过真实HTTP核对，不只验证一种非法组合。 */
  @Test
  @DisplayName("R24 部门父子类型矩阵和可信根改名通过真实MySQL验证")
  void R24_DepartmentTypeMatrixAndRootRename() {
    jdbc.update(
        "INSERT INTO departments(id,code,name,parent_id,type)"
            + " VALUES(10,'company','样例分公司',1,'COMPANY')");
    String admin = token(1);
    Map<Long, List<String>> permitted =
        Map.of(
            1L,
            List.of("COMPANY", "DEPARTMENT", "PROJECT"),
            10L,
            List.of("DEPARTMENT", "PROJECT"),
            2L,
            List.of("PROJECT"),
            3L,
            List.of());
    for (long parent : List.of(1L, 10L, 2L, 3L)) {
      for (String type : List.of("HEADQUARTERS", "COMPANY", "DEPARTMENT", "PROJECT")) {
        ResponseEntity<JsonNode> response =
            call(
                HttpMethod.POST,
                DEPARTMENTS,
                admin,
                Map.of("parentId", parent, "name", "组合-" + parent + "-" + type, "type", type));
        boolean allowed = permitted.get(parent).contains(type);
        assertEquals(
            allowed ? 200 : 400,
            response.getStatusCode().value(),
            parent + "/" + type + ":" + response.getBody());
        if (allowed)
          assertEquals(parent, response.getBody().path("data").path("parentId").asLong());
        else assertEquals("DEPARTMENT_TYPE_INVALID", response.getBody().path("code").asText());
      }
    }
    assertEquals(
        200,
        call(HttpMethod.PATCH, DEPARTMENTS + "/1", admin, Map.of("name", "总公司新名称"))
            .getStatusCode()
            .value());
    assertEquals(
        "总公司",
        jdbc.queryForObject(
            "SELECT source_key FROM import_sources WHERE department_id=1", String.class));
    assertEquals(
        "总公司新名称", jdbc.queryForObject("SELECT name FROM departments WHERE id=1", String.class));
    assertEquals(
        "DEPARTMENT_NOT_EMPTY",
        call(HttpMethod.DELETE, DEPARTMENTS + "/10", admin, null).getBody().path("code").asText());
  }

  /** 拥有实际API仍不能跳过人员级别和直属根条件。 */
  @Test
  @DisplayName("R24 普通员工获部门API仍不能写，非根五级人员仍保护本人部门")
  void R24_ApiPermissionDoesNotReplaceManagerQualification() {
    jdbc.update(
        "INSERT IGNORE INTO role_permissions(role_id,permission_id) SELECT 3,id FROM permissions"
            + " WHERE system_id=1");
    String employee = token(4);
    JsonNode tree = call(HttpMethod.GET, DEPARTMENTS, employee, null).getBody().path("data");
    assertFalse(tree.isEmpty());
    for (JsonNode node : tree) {
      assertFalse(node.path("canCreate").asBoolean());
      assertFalse(node.path("canEdit").asBoolean());
      assertFalse(node.path("canDelete").asBoolean());
    }
    assertEquals(
        403,
        call(
                HttpMethod.POST,
                DEPARTMENTS,
                employee,
                Map.of("parentId", 2, "name", "员工建项目", "type", "PROJECT"))
            .getStatusCode()
            .value());
    jdbc.update("DELETE FROM user_roles WHERE user_id=2");
    jdbc.update("INSERT INTO user_roles VALUES(2,1)");
    assertEquals(
        403,
        call(HttpMethod.PATCH, DEPARTMENTS + "/2", token(2), Map.of("name", "非根管理员本人部门"))
            .getStatusCode()
            .value());
    String administrator = token(1);
    int missingContext =
        assertThrows(
                BusinessException.class,
                () -> departmentService.checkTarget("Bearer " + administrator, 2L, true))
            .getStatus();
    assertTrue(List.of(401, 403).contains(missingContext));
  }

  /** 故障注入只在专库建短期触发器，保存失败后的数据库必须完整回滚。 */
  @Test
  @DisplayName("R24 部门保存约束失败翻译409且没有部分新增")
  void R24_DatabaseFailureRollsBackDepartmentCreation() {
    String admin = token(1);
    jdbc.execute(
        "CREATE TRIGGER r24_reject_new_department BEFORE INSERT ON departments FOR EACH ROW SIGNAL"
            + " SQLSTATE '23000' SET MYSQL_ERRNO=1062, MESSAGE_TEXT='isolated department constraint"
            + " failure'");
    try {
      ResponseEntity<JsonNode> response =
          call(
              HttpMethod.POST,
              DEPARTMENTS,
              admin,
              Map.of("parentId", 2, "name", "失败项目", "type", "PROJECT"));
      assertEquals(409, response.getStatusCode().value(), String.valueOf(response.getBody()));
      assertEquals("DATA_CONFLICT", response.getBody().path("code").asText());
      assertEquals(
          0,
          jdbc.queryForObject("SELECT COUNT(*) FROM departments WHERE name='失败项目'", Integer.class));
    } finally {
      jdbc.execute("DROP TRIGGER r24_reject_new_department");
    }
  }

  /** 专库内构造独立根管理员、部门经理和跨系统无API权限，不触及业务库。 */
  @BeforeEach
  void prepare() {
    assertEquals("rbac_core_t11_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
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
        "UPDATE roles SET role_type=CASE WHEN id<=3 THEN 'SYSTEM_BUILTIN' ELSE 'GLOBAL_CUSTOM' END,"
            + " management_level=CASE id WHEN 1 THEN 5 WHEN 2 THEN 3 WHEN 3 THEN 1 ELSE NULL END");
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
    JsonNode overview = call(HttpMethod.GET, OVERVIEW, token, null).getBody().path("data");
    assertEquals(4, overview.path("totalUsers").asInt());
    assertEquals(3, overview.path("totalDepartments").asInt());
  }

  /** 改名后回传真实人数与删除资格，调用方不必重新读整棵目录才能得到正确状态。 */
  @Test
  @DisplayName("R24 非空部门改名返回真实人数且不可删除")
  void R24_RenamedDepartmentReportsCurrentUsage() {
    String admin = token(1);
    ResponseEntity<JsonNode> renamed =
        call(HttpMethod.PATCH, DEPARTMENTS + "/2", admin, Map.of("name", "新部门名称"));
    assertEquals(200, renamed.getStatusCode().value());
    JsonNode node = renamed.getBody().path("data");
    assertEquals(3, node.path("directUsers").asInt());
    assertEquals(3, node.path("totalUsers").asInt());
    assertTrue(node.path("canDelete").isBoolean());
    assertFalse(node.path("canDelete").asBoolean());
  }

  /** 已删除部门是范围外筛选，不能作为当前人员目录的过滤根。 */
  @Test
  @DisplayName("R10/R24 人员筛选拒绝已删除部门并保留未知目标错误")
  void R24_UserSearchRejectsDepartmentTombstone() {
    String admin = token(1);
    assertEquals(
        200, call(HttpMethod.DELETE, DEPARTMENTS + "/3", admin, null).getStatusCode().value());
    ResponseEntity<JsonNode> deleted = call(HttpMethod.GET, USERS + "?departmentId=3", admin, null);
    assertEquals(403, deleted.getStatusCode().value());
    assertEquals("MANAGEMENT_SCOPE_DENIED", deleted.getBody().path("code").asText());
    ResponseEntity<JsonNode> unknown =
        call(HttpMethod.GET, USERS + "?departmentId=99999", admin, null);
    assertEquals(404, unknown.getStatusCode().value());
    assertEquals("RESOURCE_NOT_FOUND", unknown.getBody().path("code").asText());
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
    JsonNode identity = call(HttpMethod.GET, ME, admin, null).getBody().path("data").path("user");
    assertEquals(5, identity.path("managementLevel").asInt());
    assertTrue(identity.path("globalScope").asBoolean());
    assertEquals(25, identity.path("routes").size());
  }

  /** 级别与权限子集叠加；自定义角色不冻结人员，但仍只能由管理员增删。 */
  @Test
  @DisplayName("R23/R27 自定义角色不冻结人员且不可由局部管理员撤回")
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
        200,
        call(HttpMethod.PATCH, USERS + "/4/status", manager, Map.of("enabled", false))
            .getStatusCode()
            .value());
    assertEquals(
        200,
        call(HttpMethod.PUT, USERS + "/4/roles", token(1), Map.of("ids", List.of()))
            .getStatusCode()
            .value());
  }

  /** 原样保留项与增删项分别处理；伪造保留和非法删除必须整次回滚。 */
  @Test
  @DisplayName("R27 锁后按角色差集校验，保留自定义角色时可调整普通角色")
  void R27_PreservedCustomRoleDoesNotBlockOtherChanges() {
    String manager = token(2);
    jdbc.update("DELETE FROM role_permissions WHERE role_id=3");
    jdbc.update("INSERT INTO user_roles VALUES(4,4)");
    JsonNode current =
        call(HttpMethod.GET, USERS + "/4/roles", manager, null).getBody().path("data");
    assertEquals("[4]", current.path("lockedRoleIds").toString());
    assertEquals(
        200,
        call(HttpMethod.PUT, USERS + "/4/roles", manager, Map.of("ids", List.of(4)))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        call(HttpMethod.PUT, USERS + "/4/roles", manager, Map.of("ids", List.of(3)))
            .getStatusCode()
            .value());
    assertEquals(
        List.of(4L),
        jdbc.queryForList(
            "SELECT role_id FROM user_roles WHERE user_id=4 ORDER BY role_id", Long.class));
    assertEquals(
        200,
        call(HttpMethod.PUT, USERS + "/4/department", manager, Map.of("departmentId", 3))
            .getStatusCode()
            .value());
    assertEquals(
        200,
        call(HttpMethod.PUT, USERS + "/4/roles", token(1), Map.of("ids", List.of(3)))
            .getStatusCode()
            .value());
    assertEquals(
        403,
        call(HttpMethod.PUT, USERS + "/4/roles", manager, Map.of("ids", List.of(3, 4)))
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
