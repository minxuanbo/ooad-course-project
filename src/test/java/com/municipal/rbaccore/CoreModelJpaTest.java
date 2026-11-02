package com.municipal.rbaccore;

import com.municipal.rbaccore.model.*;
import com.municipal.rbaccore.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RestController;

import static com.municipal.rbaccore.model.Permission.Type.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CoreModelJpaTest.ModelConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.config.name=application-core-model-test")
@Transactional
class CoreModelJpaTest {
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = Department.class)
  @EnableJpaRepositories(basePackageClasses = DepartmentRepository.class)
  static class ModelConfiguration {
    @Bean
    static BeanFactoryPostProcessor databaseGuard(Environment environment) {
      requireIsolatedDatabase(environment.getRequiredProperty("spring.datasource.url"));
      return beanFactory -> {};
    }

    static void requireIsolatedDatabase(String url) {
      // create-drop 只能接专用测试库，校验先于 DataSource/EntityManagerFactory 实例化。
      Assert.isTrue(url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}/rbac_core_t02_test(?:\\?.*)?"),
          "模型测试只允许本机独立 MySQL 库 rbac_core_t02_test");
    }
  }

  @Autowired DepartmentRepository departments;
  @Autowired ApplicationSystemRepository systems;
  @Autowired ApiResourceRepository apis;
  @Autowired PermissionRepository permissions;
  @Autowired RoleRepository roles;
  @Autowired UserRepository users;
  @Autowired ApplicationContext context;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;
  @PersistenceContext EntityManager em;

  record Sample(ApplicationSystem system, Department root, Department child,
      Permission menu, Permission read, Permission other, ApiResource api,
      Role firstRole, Role secondRole, User user, User noRole) {}

  Sample sample() {
    var system = systems.saveAndFlush(new ApplicationSystem("rbac-core", "模型样例"));
    var root = departments.saveAndFlush(new Department("root", "根部门"));
    var child = new Department("child", "子部门");
    child.moveTo(root);
    departments.saveAndFlush(child);
    var api = apis.saveAndFlush(new ApiResource(system, "GET", "/__t02__/people/{id}"));
    var menu = permissions.saveAndFlush(new Permission(system, "menu", "样例菜单", MENU));
    var read = new Permission(system, "read", "样例按钮", BUTTON);
    read.moveTo(menu);
    read.mapApi(api);
    permissions.saveAndFlush(read);
    var other = new Permission(system, "other", "另一个菜单", MENU);
    other.mapApi(api);
    permissions.saveAndFlush(other);
    var firstRole = new Role("role-a", "角色甲");
    firstRole.grantPermission(menu);
    firstRole.grantPermission(read);
    roles.saveAndFlush(firstRole);
    var secondRole = new Role("role-b", "角色乙");
    secondRole.grantPermission(other);
    roles.saveAndFlush(secondRole);
    roles.saveAndFlush(new Role("empty-role", "暂未授予权限"));
    var user = new User("employee-1", "user-1", "unchanged-hash", "样例人员", child, true);
    user.updateProfile("100000001", "样例岗位");
    user.assignRole(firstRole);
    user.assignRole(secondRole);
    users.saveAndFlush(user);
    var noRole = users.saveAndFlush(new User(
        "employee-2", "user-2", "other-hash", "无角色人员", root, false));
    return new Sample(system, root, child, menu, read, other, api, firstRole, secondRole, user, noRole);
  }

  @Test void savesAndReloadsWholeChainWithMultipleRolesNoRolesAndSharedApi() {
    var sample = sample();
    em.clear();
    var user = users.findById(sample.user().getId()).orElseThrow();
    assertEquals("employee-1", user.getEmployeeNo());
    assertEquals("user-1", user.getUsername());
    assertEquals("unchanged-hash", user.getPasswordHash());
    assertEquals("样例人员", user.getName());
    assertEquals("100000001", user.getPhone());
    assertEquals("样例岗位", user.getPosition());
    assertTrue(user.isEnabled());
    assertEquals("child", user.getDepartment().getCode());
    assertEquals("root", user.getDepartment().getParent().getCode());
    assertEquals(2, user.getRoles().size());
    var mappedApiIds = user.getRoles().stream().flatMap(role -> role.getPermissions().stream())
        .flatMap(permission -> permission.getApis().stream()).map(ApiResource::getId).toList();
    assertEquals(List.of(sample.api().getId(), sample.api().getId()), mappedApiIds);
    var read = permissions.findById(sample.read().getId()).orElseThrow();
    assertEquals(BUTTON, read.getType());
    assertEquals("menu", read.getParent().getCode());
    assertEquals("rbac-core", read.getSystem().getCode());
    var noRole = users.findById(sample.noRole().getId()).orElseThrow();
    assertTrue(noRole.getRoles().isEmpty());
    assertFalse(noRole.isEnabled());
    assertTrue(roles.findAll().stream().filter(role -> role.getCode().equals("empty-role"))
        .findFirst().orElseThrow().getPermissions().isEmpty());
    assertTrue(permissions.findById(sample.menu().getId()).orElseThrow().getApis().isEmpty());
    assertEquals("GET", apis.findById(sample.api().getId()).orElseThrow().getHttpMethod());
    assertEquals("/__t02__/people/{id}", apis.findById(sample.api().getId()).orElseThrow().getPathTemplate());
  }

  @Test void mysqlHasExactlyNineTablesCompositePrimaryKeysForeignKeysAndNoLegacyComponents() throws Exception {
    try (var connection = dataSource.getConnection()) {
      assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
      assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 8);
      assertEquals("rbac_core_t02_test", connection.getCatalog());
    }
    assertEquals(List.of("api_resources", "departments", "permission_apis", "permissions",
        "role_permissions", "roles", "systems", "user_roles", "users"), sqlList(
        "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name"));
    for (var entry : Map.of("user_roles", List.of("user_id", "role_id"),
        "role_permissions", List.of("role_id", "permission_id"),
        "permission_apis", List.of("permission_id", "api_id")).entrySet()) {
      assertEquals(new HashSet<>(entry.getValue()), new HashSet<>(sqlList(
          "SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE()"
              + " AND table_name='" + entry.getKey() + "' AND constraint_name='PRIMARY'")));
    }
    assertEquals(11, ((Number) em.createNativeQuery(
        "SELECT count(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE()"
            + " AND constraint_type='FOREIGN KEY'").getSingleResult()).intValue());
    assertEquals(0, ((Number) em.createNativeQuery(
        "SELECT count(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()")
        .getSingleResult()).intValue());
    assertTrue(context.getBeansOfType(ApplicationRunner.class).isEmpty());
    assertTrue(context.getBeansWithAnnotation(RestController.class).isEmpty());
    assertFalse(context.containsBean("dataInitializer"));
    assertFalse(context.containsBean("databaseConstraints"));
    assertNull(context.getEnvironment().getProperty("rbac.session-hours"));
    assertEquals("never", context.getEnvironment().getProperty("spring.sql.init.mode"));
  }

  List<?> sqlList(String sql) {
    return em.createNativeQuery(sql).getResultList();
  }

  void sqlError(int expectedCode, String sql) {
    var exception = assertThrows(PersistenceException.class, () -> em.createNativeQuery(sql).executeUpdate());
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException) {
        assertEquals(expectedCode, sqlException.getErrorCode());
        return;
      }
    }
    fail("Expected MySQL constraint error", exception);
  }

  @ParameterizedTest
  @ValueSource(strings = {"user_roles", "role_permissions", "permission_apis"})
  void duplicateAssociationIsRejectedByMySqlPrimaryKey(String table) {
    sample();
    sqlError(1062, "INSERT INTO " + table + " SELECT * FROM " + table + " LIMIT 1");
  }

  static Stream<String> duplicateRows() {
    String system = "(SELECT id FROM systems WHERE code='rbac-core')";
    String department = "(SELECT id FROM departments WHERE code='root')";
    return Stream.of(
        "INSERT INTO departments(code,name) VALUES('root','重复编码')",
        "INSERT INTO roles(code,name,enabled) VALUES('role-a','重复编码',1)",
        "INSERT INTO systems(code,name,enabled) VALUES('rbac-core','重复编码',1)",
        "INSERT INTO users(employee_no,username,password_hash,name,department_id,enabled)"
            + " VALUES('employee-1','different-user','hash','重复工号'," + department + ",1)",
        "INSERT INTO users(employee_no,username,password_hash,name,department_id,enabled)"
            + " VALUES('different-employee','user-1','hash','重复用户名'," + department + ",1)",
        "INSERT INTO permissions(system_id,code,name,type,enabled)"
            + " VALUES(" + system + ",'menu','重复系统权限','MENU',1)",
        "INSERT INTO api_resources(system_id,http_method,path_template,enabled)"
            + " VALUES(" + system + ",'GET','/__t02__/people/{id}',1)");
  }

  @ParameterizedTest
  @MethodSource("duplicateRows")
  void requiredUniquenessIsEnforcedByMySql(String sql) {
    sample();
    sqlError(1062, sql);
  }

  static Stream<String> missingReferences() {
    String system = "(SELECT id FROM systems WHERE code='rbac-core')";
    String role = "(SELECT id FROM roles WHERE code='role-a')";
    String user = "(SELECT id FROM users WHERE username='user-1')";
    String permission = "(SELECT id FROM permissions WHERE code='menu')";
    String api = "(SELECT id FROM api_resources LIMIT 1)";
    return Stream.of(
        "INSERT INTO departments(code,name,parent_id) VALUES('bad-parent','无效上级',-1)",
        "INSERT INTO users(employee_no,username,password_hash,name,department_id,enabled)"
            + " VALUES('bad-dept','bad-dept','hash','无效部门',-1,1)",
        "INSERT INTO permissions(system_id,code,name,type,enabled) VALUES(-1,'bad-system','无效系统','MENU',1)",
        "INSERT INTO permissions(system_id,parent_id,code,name,type,enabled)"
            + " VALUES(" + system + ",-1,'bad-parent','无效上级','MENU',1)",
        "INSERT INTO api_resources(system_id,http_method,path_template,enabled) VALUES(-1,'GET','/bad-system',1)",
        "INSERT INTO user_roles(user_id,role_id) VALUES(-1," + role + ")",
        "INSERT INTO user_roles(user_id,role_id) VALUES(" + user + ",-1)",
        "INSERT INTO role_permissions(role_id,permission_id) VALUES(-1," + permission + ")",
        "INSERT INTO role_permissions(role_id,permission_id) VALUES(" + role + ",-1)",
        "INSERT INTO permission_apis(permission_id,api_id) VALUES(-1," + api + ")",
        "INSERT INTO permission_apis(permission_id,api_id) VALUES(" + permission + ",-1)");
  }

  @ParameterizedTest
  @MethodSource("missingReferences")
  void necessaryForeignKeysRejectMissingTargets(String sql) {
    sample();
    sqlError(1452, sql);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "INSERT INTO users(employee_no,username,password_hash,name,department_id,enabled) VALUES('missing','missing','hash','无部门',NULL,1)",
      "INSERT INTO permissions(system_id,code,name,type,enabled) VALUES(NULL,'missing','无系统','MENU',1)",
      "INSERT INTO api_resources(system_id,http_method,path_template,enabled) VALUES(NULL,'GET','/missing',1)"})
  void mandatoryDepartmentAndSystemCannotBeNull(String sql) {
    sqlError(1048, sql);
  }

  @Test void sameCodesAndApiTemplatesAreAllowedAcrossDifferentSystems() {
    var sample = sample();
    var second = systems.saveAndFlush(new ApplicationSystem("second-system", "第二系统样例"));
    permissions.saveAndFlush(new Permission(second, "menu", "相同编码不同系统", MENU));
    apis.saveAndFlush(new ApiResource(second, "GET", sample.api().getPathTemplate()));
    em.clear();
    assertEquals(2, systems.count());
    assertEquals(4, permissions.count());
    assertEquals(2, apis.count());
  }

  @Test void loadedTreesAndMappingsRejectInvalidMutationsAndRemainConsistentAfterMoves() {
    var sample = sample();
    var second = systems.saveAndFlush(new ApplicationSystem("other-system", "其他系统"));
    var external = apis.saveAndFlush(new ApiResource(second, "GET", "/__t02__/other"));
    em.clear();
    var menu = permissions.findById(sample.menu().getId()).orElseThrow();
    var read = permissions.findById(sample.read().getId()).orElseThrow();
    var other = permissions.findById(sample.other().getId()).orElseThrow();
    var root = departments.findById(sample.root().getId()).orElseThrow();
    var child = departments.findById(sample.child().getId()).orElseThrow();
    assertThrows(IllegalArgumentException.class, () -> root.moveTo(child));
    assertThrows(IllegalArgumentException.class, () -> menu.changeType(BUTTON));
    assertThrows(IllegalArgumentException.class, () -> menu.moveTo(menu));
    read.mapApi(apis.getReferenceById(sample.api().getId()));
    assertThrows(IllegalArgumentException.class, () -> read.mapApi(apis.getReferenceById(external.getId())));
    read.moveTo(other);
    menu.changeType(BUTTON);
    assertThrows(IllegalArgumentException.class, () -> other.changeType(BUTTON));
    permissions.flush();
    em.clear();
    assertNull(departments.findById(root.getId()).orElseThrow().getParent());
    assertEquals(root.getId(), departments.findById(child.getId()).orElseThrow().getParent().getId());
    var reloaded = permissions.findById(read.getId()).orElseThrow();
    assertEquals(other.getId(), reloaded.getParent().getId());
    assertEquals(1, reloaded.getApis().size());
    assertEquals(BUTTON, permissions.findById(menu.getId()).orElseThrow().getType());
    assertTrue(permissions.findById(menu.getId()).orElseThrow().getChildren().isEmpty());
    assertEquals(1, permissions.findById(other.getId()).orElseThrow().getChildren().size());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void domainFailureRollsBackEarlierJpaWrites() {
    var transaction = new TransactionTemplate(transactionManager);
    long before = departments.count();
    assertThrows(IllegalArgumentException.class, () -> transaction.executeWithoutResult(status -> {
      var root = departments.saveAndFlush(new Department("rollback-root", "回滚根"));
      var child = new Department("rollback-child", "回滚子");
      child.moveTo(root);
      departments.saveAndFlush(child);
      root.moveTo(child);
    }));
    assertEquals(before, departments.count());
  }

  @Test void systemsRolesApisAndUsersKeepTheirOwnEnabledState() {
    var sample = sample();
    sample.system().setEnabled(false);
    sample.firstRole().setEnabled(false);
    sample.read().setEnabled(false);
    sample.api().setEnabled(false);
    sample.user().setEnabled(false);
    sample.user().moveTo(sample.root());
    em.flush();
    em.clear();
    assertFalse(systems.findById(sample.system().getId()).orElseThrow().isEnabled());
    assertFalse(roles.findById(sample.firstRole().getId()).orElseThrow().isEnabled());
    assertFalse(permissions.findById(sample.read().getId()).orElseThrow().isEnabled());
    assertFalse(apis.findById(sample.api().getId()).orElseThrow().isEnabled());
    var user = users.findById(sample.user().getId()).orElseThrow();
    assertFalse(user.isEnabled());
    assertEquals(sample.root().getId(), user.getDepartment().getId());
  }
}
