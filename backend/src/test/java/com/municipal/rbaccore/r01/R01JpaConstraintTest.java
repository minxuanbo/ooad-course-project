package com.municipal.rbaccore.r01;

import static com.municipal.rbaccore.service.port.resource.PermissionType.*;
import static org.junit.jupiter.api.Assertions.*;

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

@SpringBootTest(
    classes = R01JpaConstraintTest.ModelConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.config.name=application-core-model-test")
/** R01真实MySQL验证九张核心表、支撑表及数据库约束，测试数据在独立库中回滚。 */
@Transactional
class R01JpaConstraintTest {
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = DepartmentEntity.class)
  @EnableJpaRepositories(basePackageClasses = DepartmentJpaRepository.class)
  static class ModelConfiguration {
    /** 在数据源创建前校验本机专库，禁止破坏性DDL连接业务库。 */
    @Bean
    static BeanFactoryPostProcessor databaseGuard(Environment environment) {
      /** 仅允许本机明确的模型专库，防止create-drop落到其他库。 */
      requireIsolatedDatabase(environment.getRequiredProperty("spring.datasource.url"));
      return beanFactory -> {};
    }

    /** 仅允许本机明确的模型专库，防止create-drop落到其他库。 */
    static void requireIsolatedDatabase(String url) {
      // create-drop 只能接专用测试库，校验先于 DataSource/EntityManagerFactory 实例化。
      Assert.isTrue(
          url.matches(
              "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}/rbac_core_t02_test(?:\\?.*)?"),
          "模型测试只允许本机独立 MySQL 库 rbac_core_t02_test");
    }
  }

  /** 关联当前测试场景的持久化夹具，保持原始引用便于回读比较。 */
  @Autowired DepartmentJpaRepository departments;

  @Autowired ApplicationSystemJpaRepository systems;
  @Autowired ApiResourceJpaRepository apis;
  @Autowired PermissionJpaRepository permissions;
  @Autowired RoleJpaRepository roles;
  @Autowired UserJpaRepository users;
  @Autowired ApplicationContext context;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;
  @PersistenceContext EntityManager em;

  record Sample(
      ApplicationSystemEntity system,
      DepartmentEntity root,
      DepartmentEntity child,
      PermissionEntity menu,
      PermissionEntity read,
      PermissionEntity other,
      ApiResourceEntity api,
      RoleEntity firstRole,
      RoleEntity secondRole,
      UserEntity user,
      UserEntity noRole) {}

  /** 构造多角色、无角色和共享API夹具，供持久化与约束场景复用。 */
  Sample sample() {
    ApplicationSystemEntity system =
        systems.saveAndFlush(new ApplicationSystemEntity("rbac-core", "模型样例"));
    DepartmentEntity root = departments.saveAndFlush(new DepartmentEntity("root", "根部门"));
    DepartmentEntity child = new DepartmentEntity("child", "子部门");
    child.moveTo(root);
    departments.saveAndFlush(child);
    ApiResourceEntity api =
        apis.saveAndFlush(new ApiResourceEntity(system, "GET", "/__t02__/people/{id}"));
    PermissionEntity menu =
        permissions.saveAndFlush(new PermissionEntity(system, "menu", "样例菜单", MENU));
    PermissionEntity read = new PermissionEntity(system, "read", "样例按钮", BUTTON);
    read.moveTo(menu);
    read.mapApi(api);
    permissions.saveAndFlush(read);
    PermissionEntity other = new PermissionEntity(system, "other", "另一个菜单", MENU);
    other.mapApi(api);
    permissions.saveAndFlush(other);
    RoleEntity firstRole = new RoleEntity("role-a", "角色甲");
    firstRole.grantPermission(menu);
    firstRole.grantPermission(read);
    roles.saveAndFlush(firstRole);
    RoleEntity secondRole = new RoleEntity("role-b", "角色乙");
    secondRole.grantPermission(other);
    roles.saveAndFlush(secondRole);
    roles.saveAndFlush(new RoleEntity("empty-role", "暂未授予权限"));
    UserEntity user = new UserEntity("user-1", "unchanged-hash", "样例人员", child, true);
    user.updateProfile("100000001", "样例岗位");
    user.assignRole(firstRole);
    user.assignRole(secondRole);
    users.saveAndFlush(user);
    UserEntity noRole =
        users.saveAndFlush(new UserEntity("user-2", "other-hash", "无角色人员", root, false));
    /** 关联当前测试场景的持久化夹具，保持原始引用便于回读比较。 */
    return new Sample(
        system, root, child, menu, read, other, api, firstRole, secondRole, user, noRole);
  }

  /** 持久化并重读整条授权关联，核对多角色、无角色和共享API。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 持久化并重读整条授权关联，核对多角色、无角色和共享API。")
  void savesAndReloadsWholeChainWithMultipleRolesNoRolesAndSharedApi() {
    Sample sample = sample();
    em.clear();
    UserEntity user = users.findById(sample.user().getId()).orElseThrow();
    assertEquals("user-1", user.getUsername());
    assertEquals("unchanged-hash", user.getPasswordHash());
    assertEquals("样例人员", user.getName());
    assertEquals("100000001", user.getPhone());
    assertEquals("样例岗位", user.getPosition());
    assertTrue(user.isEnabled());
    assertEquals("child", user.getDepartment().getCode());
    assertEquals("root", user.getDepartment().getParent().getCode());
    assertEquals(2, user.getRoles().size());
    List<Long> mappedApiIds =
        user.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .flatMap(permission -> permission.getApis().stream())
            .map(ApiResourceEntity::getId)
            .toList();
    assertEquals(List.of(sample.api().getId(), sample.api().getId()), mappedApiIds);
    PermissionEntity read = permissions.findById(sample.read().getId()).orElseThrow();
    assertEquals(BUTTON, read.getType());
    assertEquals("menu", read.getParent().getCode());
    assertEquals("rbac-core", read.getSystem().getCode());
    UserEntity noRole = users.findById(sample.noRole().getId()).orElseThrow();
    assertTrue(noRole.getRoles().isEmpty());
    assertFalse(noRole.isEnabled());
    assertTrue(
        roles.findAll().stream()
            .filter(role -> role.getCode().equals("empty-role"))
            .findFirst()
            .orElseThrow()
            .getPermissions()
            .isEmpty());
    assertTrue(permissions.findById(sample.menu().getId()).orElseThrow().getApis().isEmpty());
    assertEquals("GET", apis.findById(sample.api().getId()).orElseThrow().getHttpMethod());
    assertEquals(
        "/__t02__/people/{id}",
        apis.findById(sample.api().getId()).orElseThrow().getPathTemplate());
  }

  /** 核对十二张精确表名、核心十一外键及关联联合主键，拒绝旧模型混入。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 核对十二张精确表名、核心十一外键及关联联合主键，拒绝旧模型混入。")
  void R01_mysqlHasNineCoreAndThreeSupportTablesWithConstraints() throws Exception {
    try (java.sql.Connection connection = dataSource.getConnection()) {
      assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
      assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 8);
      assertEquals("rbac_core_t02_test", connection.getCatalog());
    }
    assertEquals(
        List.of(
            "api_resources",
            "authorization_logs",
            "bootstrap_receipts",
            "departments",
            "import_sources",
            "permission_apis",
            "permissions",
            "role_permissions",
            "roles",
            "systems",
            "user_roles",
            "users"),
        /** 读取当前隔离库的元数据或断言结果，不访问业务库。 */
        sqlList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER"
                + " BY table_name"));
    for (Map.Entry<String, List<String>> entry :
        Map.of(
                "user_roles",
                List.of("user_id", "role_id"),
                "role_permissions",
                List.of("role_id", "permission_id"),
                "permission_apis",
                List.of("permission_id", "api_id"))
            .entrySet()) {
      assertEquals(
          new HashSet<>(entry.getValue()),
          new HashSet<>(
              /** 读取当前隔离库的元数据或断言结果，不访问业务库。 */
              sqlList(
                  "SELECT column_name FROM information_schema.key_column_usage WHERE"
                      + " table_schema=DATABASE() AND table_name='"
                      + entry.getKey()
                      + "' AND constraint_name='PRIMARY'")));
    }
    assertEquals(
        11,
        ((Number)
                em.createNativeQuery(
                        "SELECT count(*) FROM information_schema.table_constraints WHERE"
                            + " table_schema=DATABASE() AND constraint_type='FOREIGN KEY' AND"
                            + " table_name IN ('departments','systems','roles','users',"
                            + " 'permissions','api_resources','user_roles','role_permissions','permission_apis')")
                    .getSingleResult())
            .intValue());
    assertEquals(
        0,
        ((Number)
                em.createNativeQuery(
                        "SELECT count(*) FROM information_schema.triggers WHERE"
                            + " trigger_schema=DATABASE()")
                    .getSingleResult())
            .intValue());
    assertTrue(context.getBeansOfType(ApplicationRunner.class).isEmpty());
    assertTrue(context.getBeansWithAnnotation(RestController.class).isEmpty());
    assertFalse(context.containsBean("dataInitializer"));
    assertFalse(context.containsBean("databaseConstraints"));
    assertNull(context.getEnvironment().getProperty("rbac.session-hours"));
    assertEquals("never", context.getEnvironment().getProperty("spring.sql.init.mode"));
  }

  /** 读取当前隔离库的元数据或断言结果，不访问业务库。 */
  List<?> sqlList(String sql) {
    return em.createNativeQuery(sql).getResultList();
  }

  /** 在独立事务中触发数据库约束失败，核对SQL状态及回滚效果。 */
  void sqlError(int expectedCode, String sql) {
    PersistenceException exception =
        assertThrows(PersistenceException.class, () -> em.createNativeQuery(sql).executeUpdate());
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException) {
        assertEquals(expectedCode, sqlException.getErrorCode());
        return;
      }
    }
    fail("Expected MySQL constraint error", exception);
  }

  /** 重复关联必须被联合主键拒绝，不能只依靠集合去重。 */
  @ParameterizedTest
  @ValueSource(strings = {"user_roles", "role_permissions", "permission_apis"})
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 重复关联必须被联合主键拒绝，不能只依靠集合去重。")
  void duplicateAssociationIsRejectedByMySqlPrimaryKey(String table) {
    /** 构造多角色、无角色和共享API夹具，供持久化与约束场景复用。 */
    sample();
    /** 在独立事务中触发数据库约束失败，核对SQL状态及回滚效果。 */
    sqlError(1062, "INSERT INTO " + table + " SELECT * FROM " + table + " LIMIT 1");
  }

  /** 提供核心字段重复的非法记录，验证数据库唯一约束。 */
  static Stream<String> duplicateRows() {
    String system = "(SELECT id FROM systems WHERE code='rbac-core')";
    String department = "(SELECT id FROM departments WHERE code='root')";
    return Stream.of(
        "INSERT INTO departments(code,name) VALUES('root','重复编码')",
        "INSERT INTO roles(code,name,enabled) VALUES('role-a','重复编码',1)",
        "INSERT INTO systems(code,name,enabled) VALUES('rbac-core','重复编码',1)",
        "INSERT INTO users(username,password_hash,name,department_id,enabled)"
            + " VALUES('user-1','hash','重复工号',"
            + department
            + ",1)",
        "INSERT INTO users(username,phone,password_hash,name,department_id,enabled)"
            + " VALUES('different-user','100000001','hash','重复手机号',"
            + department
            + ",1)",
        "INSERT INTO permissions(system_id,code,name,type,enabled)"
            + " VALUES("
            + system
            + ",'menu','重复系统权限','MENU',1)",
        "INSERT INTO api_resources(system_id,http_method,path_template,enabled)"
            + " VALUES("
            + system
            + ",'GET','/__t02__/people/{id}',1)");
  }

  /** 工号、非空手机号、角色和系统内资源编码由数据库兜底唯一性。 */
  @ParameterizedTest
  @MethodSource("duplicateRows")
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 工号、非空手机号、角色和系统内资源编码由数据库兜底唯一性。")
  void requiredUniquenessIsEnforcedByMySql(String sql) {
    /** 构造多角色、无角色和共享API夹具，供持久化与约束场景复用。 */
    sample();
    /** 在独立事务中触发数据库约束失败，核对SQL状态及回滚效果。 */
    sqlError(1062, sql);
  }

  /** 提供引用不存在目标的记录，验证数据库外键约束。 */
  static Stream<String> missingReferences() {
    String system = "(SELECT id FROM systems WHERE code='rbac-core')";
    String role = "(SELECT id FROM roles WHERE code='role-a')";
    String user = "(SELECT id FROM users WHERE username='user-1')";
    String permission = "(SELECT id FROM permissions WHERE code='menu')";
    String api = "(SELECT id FROM api_resources LIMIT 1)";
    return Stream.of(
        "INSERT INTO departments(code,name,parent_id) VALUES('bad-parent','无效上级',-1)",
        "INSERT INTO users(username,password_hash,name,department_id,enabled)"
            + " VALUES('bad-dept','hash','无效部门',-1,1)",
        "INSERT INTO permissions(system_id,code,name,type,enabled)"
            + " VALUES(-1,'bad-system','无效系统','MENU',1)",
        "INSERT INTO permissions(system_id,parent_id,code,name,type,enabled)"
            + " VALUES("
            + system
            + ",-1,'bad-parent','无效上级','MENU',1)",
        "INSERT INTO api_resources(system_id,http_method,path_template,enabled)"
            + " VALUES(-1,'GET','/bad-system',1)",
        "INSERT INTO user_roles(user_id,role_id) VALUES(-1," + role + ")",
        "INSERT INTO user_roles(user_id,role_id) VALUES(" + user + ",-1)",
        "INSERT INTO role_permissions(role_id,permission_id) VALUES(-1," + permission + ")",
        "INSERT INTO role_permissions(role_id,permission_id) VALUES(" + role + ",-1)",
        "INSERT INTO permission_apis(permission_id,api_id) VALUES(-1," + api + ")",
        "INSERT INTO permission_apis(permission_id,api_id) VALUES(" + permission + ",-1)");
  }

  /** 核心关系引用不存在目标时由MySQL拒绝并回滚。 */
  @ParameterizedTest
  @MethodSource("missingReferences")
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 核心关系引用不存在目标时由MySQL拒绝并回滚。")
  void necessaryForeignKeysRejectMissingTargets(String sql) {
    /** 构造多角色、无角色和共享API夹具，供持久化与约束场景复用。 */
    sample();
    /** 在独立事务中触发数据库约束失败，核对SQL状态及回滚效果。 */
    sqlError(1452, sql);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "INSERT INTO users(username,password_hash,name,department_id,enabled)"
            + " VALUES('missing','hash','无部门',NULL,1)",
        "INSERT INTO permissions(system_id,code,name,type,enabled)"
            + " VALUES(NULL,'missing','无系统','MENU',1)",
        "INSERT INTO api_resources(system_id,http_method,path_template,enabled)"
            + " VALUES(NULL,'GET','/missing',1)"
      })
  /** 人员部门及资源系统归属不可空，验证实际数据库约束。 */
  void mandatoryDepartmentAndSystemCannotBeNull(String sql) {
    /** 在独立事务中触发数据库约束失败，核对SQL状态及回滚效果。 */
    sqlError(1048, sql);
  }

  /** 系统内唯一不应错误扩大为跨系统唯一。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 系统内唯一不应错误扩大为跨系统唯一。")
  void sameCodesAndApiTemplatesAreAllowedAcrossDifferentSystems() {
    Sample sample = sample();
    ApplicationSystemEntity second =
        systems.saveAndFlush(new ApplicationSystemEntity("second-system", "第二系统样例"));
    permissions.saveAndFlush(new PermissionEntity(second, "menu", "相同编码不同系统", MENU));
    apis.saveAndFlush(new ApiResourceEntity(second, "GET", sample.api().getPathTemplate()));
    em.clear();
    assertEquals(2, systems.count());
    assertEquals(4, permissions.count());
    assertEquals(2, apis.count());
  }

  /** 重载后的树和映射仍执行局部约束，移动后正反向关联保持一致。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 重载后的树和映射仍执行局部约束，移动后正反向关联保持一致。")
  void loadedTreesAndMappingsRejectInvalidMutationsAndRemainConsistentAfterMoves() {
    Sample sample = sample();
    ApplicationSystemEntity second =
        systems.saveAndFlush(new ApplicationSystemEntity("other-system", "其他系统"));
    ApiResourceEntity external =
        apis.saveAndFlush(new ApiResourceEntity(second, "GET", "/__t02__/other"));
    em.clear();
    PermissionEntity menu = permissions.findById(sample.menu().getId()).orElseThrow();
    PermissionEntity read = permissions.findById(sample.read().getId()).orElseThrow();
    PermissionEntity other = permissions.findById(sample.other().getId()).orElseThrow();
    DepartmentEntity root = departments.findById(sample.root().getId()).orElseThrow();
    DepartmentEntity child = departments.findById(sample.child().getId()).orElseThrow();
    assertThrows(IllegalArgumentException.class, () -> root.moveTo(child));
    assertThrows(IllegalArgumentException.class, () -> menu.changeType(BUTTON));
    assertThrows(IllegalArgumentException.class, () -> menu.moveTo(menu));
    read.mapApi(apis.getReferenceById(sample.api().getId()));
    assertThrows(
        IllegalArgumentException.class, () -> read.mapApi(apis.getReferenceById(external.getId())));
    read.moveTo(other);
    menu.changeType(BUTTON);
    assertThrows(IllegalArgumentException.class, () -> other.changeType(BUTTON));
    permissions.flush();
    em.clear();
    assertNull(departments.findById(root.getId()).orElseThrow().getParent());
    assertEquals(
        root.getId(), departments.findById(child.getId()).orElseThrow().getParent().getId());
    PermissionEntity reloaded = permissions.findById(read.getId()).orElseThrow();
    assertEquals(other.getId(), reloaded.getParent().getId());
    assertEquals(1, reloaded.getApis().size());
    assertEquals(BUTTON, permissions.findById(menu.getId()).orElseThrow().getType());
    assertTrue(permissions.findById(menu.getId()).orElseThrow().getChildren().isEmpty());
    assertEquals(1, permissions.findById(other.getId()).orElseThrow().getChildren().size());
  }

  /** 持久化数据约束失败必须回滚事务内此前已经执行的写入。 */
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 持久化数据约束失败必须回滚事务内此前已经执行的写入。")
  void domainFailureRollsBackEarlierJpaWrites() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    long before = departments.count();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            transaction.executeWithoutResult(
                status -> {
                  DepartmentEntity root =
                      departments.saveAndFlush(new DepartmentEntity("rollback-root", "回滚根"));
                  DepartmentEntity child = new DepartmentEntity("rollback-child", "回滚子");
                  child.moveTo(root);
                  departments.saveAndFlush(child);
                  root.moveTo(child);
                }));
    assertEquals(before, departments.count());
  }

  /** 分别保存各对象状态，确认启停不隐式级联改变其他对象。 */
  @Test
  @org.junit.jupiter.api.DisplayName("R01/R02/R09/R14 分别保存各对象状态，确认启停不隐式级联改变其他对象。")
  void systemsRolesApisAndUsersKeepTheirOwnEnabledState() {
    Sample sample = sample();
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
    UserEntity user = users.findById(sample.user().getId()).orElseThrow();
    assertFalse(user.isEnabled());
    assertEquals(sample.root().getId(), user.getDepartment().getId());
  }
}
