package com.municipal.rbaccore.importing;

import com.municipal.rbaccore.model.Department;
import com.municipal.rbaccore.model.Role;
import com.municipal.rbaccore.model.User;
import com.municipal.rbaccore.repository.DepartmentRepository;
import com.municipal.rbaccore.repository.UserRepository;
import com.municipal.rbaccore.repository.RoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import static org.junit.jupiter.api.Assertions.*;

/** T03 使用本机专用MySQL库验证真实全量导入，绝不连接既有业务库。 */
@SpringBootTest(classes = CoreImportJpaT03Test.ImportTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.config.name=application-core-import-test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CoreImportJpaT03Test {
  /** 显式选择新模型及导入支撑，不扫描旧产品初始化器、触发器或Controller。 */
  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = {Department.class, ImportSource.class})
  @EnableJpaRepositories(basePackageClasses = {DepartmentRepository.class, ImportSourceRepository.class})
  @Import(CoreImportService.class)
  static class ImportTestConfiguration {
    /** 建表脚本只允许T03专用本机测试库，先于数据源创建执行。 */
    @Bean
    static BeanFactoryPostProcessor databaseGuard(Environment environment) {
      Assert.isTrue(environment.getRequiredProperty("spring.datasource.url").matches(
          "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}/rbac_core_t03_test(?:\\?.*)?"),
          "导入测试只允许本机独立库rbac_core_t03_test");
      return beanFactory -> { };
    }

    /** 测试降低计算成本，生产配置保持BCrypt默认强度。 */
    @Bean
    PasswordEncoder passwordEncoder() {
      return new BCryptPasswordEncoder(4);
    }

    /** 原表解析与生产使用同一个实现。 */
    @Bean
    WorkbookImportPlanner planner() {
      return new WorkbookImportPlanner();
    }
  }

  @Autowired CoreImportService service;
  @Autowired UserRepository users;
  @Autowired DepartmentRepository departments;
  @Autowired RoleRepository roles;
  @Autowired ImportSourceRepository sources;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired PasswordEncoder encoder;
  @PersistenceContext EntityManager entityManager;

  private static final BootstrapCredentials CREDENTIALS = new BootstrapCredentials(
      "test-employee-password", "test-admin-password", "admin");

  /** 仅清理本测试守卫允许的专库，清理顺序遵守外键，不关闭约束。 */
  @BeforeEach
  void t03ResetIsolatedTestDatabase() {
    for (String table : List.of("import_sources", "user_roles", "role_permissions", "permission_apis",
        "users", "roles", "permissions", "api_resources", "systems")) {
      jdbc.update("DELETE FROM " + table);
    }
    jdbc.update("UPDATE departments SET parent_id=NULL");
    jdbc.update("DELETE FROM departments");
  }

  /** JPA结构校验不检查排序规则，导入前必须另行验证实际登录字段比较语义。 */
  @Test
  @DisplayName("T03 拒绝实际数据库中不一致的登录字段排序规则")
  void t03RejectsUnexpectedLoginColumnCollation() {
    jdbc.execute("ALTER TABLE users MODIFY username VARCHAR(100) COLLATE utf8mb4_0900_ai_ci NOT NULL");
    try {
      assertEquals("IMPORT_DATABASE_UNSAFE", assertThrows(ImportException.class,
          () -> service.importDirectory(Path.of("."), CREDENTIALS)).getCode());
      assertEquals(0, users.count());
      assertEquals(0, departments.count());
    } finally {
      jdbc.execute("ALTER TABLE users MODIFY username VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL");
    }
  }

  /** 不能把没有原始来源映射的已有数据自动认领成原始员工。 */
  @Test
  @DisplayName("T03 首次初始化拒绝混入无来源的已有目标数据")
  void t03RejectsUnmappedExistingData() {
    departments.save(new Department("unmapped", "无来源部门"));
    assertEquals("TARGET_NOT_EMPTY", assertThrows(ImportException.class,
        () -> service.importDirectory(Path.of("."), CREDENTIALS)).getCode());
    assertEquals(1, departments.count());
    assertEquals(0, users.count());
    assertEquals(0, sources.count());
  }

  /** 非唯一性数据库错误同样在Service处翻译并回滚已写入的部门。 */
  @Test
  @DisplayName("T03 非唯一约束数据库失败也整体回滚并返回稳定错误码")
  void t03TranslatesDatabaseFailureAndRollsBack() {
    jdbc.execute("CREATE TRIGGER t03_unavailable BEFORE INSERT ON users FOR EACH ROW "
        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='T03 test database failure'");
    try {
      assertEquals("IMPORT_DATABASE_ERROR", assertThrows(ImportException.class,
          () -> service.importDirectory(Path.of("."), CREDENTIALS)).getCode());
      assertEquals(0, users.count());
      assertEquals(0, departments.count());
      assertEquals(0, sources.count());
    } finally {
      jdbc.execute("DROP TRIGGER t03_unavailable");
    }
  }

  /** 数据库中应建立110个目标部门和10001个无角色账号。 */
  @Test
  @DisplayName("T03 MySQL全量导入110个目标部门、10000名员工和独立管理员")
  void t03ImportsCompletePopulationIntoMySql() throws Exception {
    jdbc.execute("CREATE TRIGGER t03_fail_import BEFORE INSERT ON users FOR EACH ROW "
        + "BEGIN IF NEW.username='SG000005' THEN SIGNAL SQLSTATE '23000' "
        + "SET MYSQL_ERRNO=1062,MESSAGE_TEXT='T03 forced constraint failure'; END IF; END");
    try {
      ImportException failure = assertThrows(ImportException.class,
          () -> service.importDirectory(Path.of("."), CREDENTIALS));
      assertEquals("DATA_CONFLICT", failure.getCode());
      assertEquals(0, users.count());
      assertEquals(0, departments.count());
      assertEquals(0, sources.count());
    } finally {
      jdbc.execute("DROP TRIGGER t03_fail_import");
    }

    ImportReport report = service.importDirectory(Path.of("."), CREDENTIALS);
    assertEquals(111, report.sourceOrganizations());
    assertEquals(110, report.targetOrganizations());
    assertEquals(10000, report.employees());
    assertEquals(10001, report.createdUsers());
    assertNotNull(report.adminId());
    assertEquals(110, departments.count());
    assertEquals(10001, users.count());
    assertEquals(10112, sources.count());
    assertEquals(0, roles.count());
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM departments WHERE parent_id IS NULL", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM departments WHERE name='公司领导'", Integer.class));
    assertEquals(11, jdbc.queryForObject("SELECT count(*) FROM users u JOIN departments d ON d.id=u.department_id "
        + "WHERE d.name='总公司'", Integer.class));
    assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
        + "AND table_name='users' AND column_name='employee_no'", Integer.class));
    verifyAllSourceMappings();

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      User user = users.findByUsernameOrPhone("SG000211", "SG000211").getFirst();
      Role role = roles.save(new Role("t03-manual-role", "后续人工授权样例"));
      user.assignRole(role);
      user.setEnabled(false);
      user.updateProfile("00000000001", "后续调整岗位");
      user.moveTo(users.findById(report.adminId()).orElseThrow().getDepartment());
    });
    String unchangedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username='SG000211'", String.class);
    ImportReport rerun = service.importDirectory(Path.of("."),
        new BootstrapCredentials("changed-employee-config", "changed-admin-config", "admin"));
    assertEquals(0, rerun.createdUsers());
    assertEquals(10001, rerun.existingUsers());
    assertEquals(10112, sources.count());
    assertEquals(unchangedHash, jdbc.queryForObject("SELECT password_hash FROM users WHERE username='SG000211'", String.class));
    assertEquals("00000000001", jdbc.queryForObject("SELECT phone FROM users WHERE username='SG000211'", String.class));
    assertEquals(false, jdbc.queryForObject("SELECT enabled FROM users WHERE username='SG000211'", Boolean.class));
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class));
    assertEquals("总公司", jdbc.queryForObject("SELECT d.name FROM departments d JOIN users u ON u.department_id=d.id "
        + "WHERE u.username='SG000211'", String.class));

    assertRejectedMutation("UPDATE import_sources SET source_json='[]' WHERE source_type='EMPLOYEE' "
        + "AND source_key='SG000001'", "SOURCE_CHANGED");
    assertRejectedMutation("DELETE FROM import_sources WHERE source_type='EMPLOYEE' "
        + "AND source_key='SG000001'", "SOURCE_MAPPING_INCOMPLETE");
    assertRejectedMutation("UPDATE departments SET code='t03-changed-code' WHERE name='总公司'", "TARGET_TREE_CHANGED");
    assertEquals("SOURCE_MAPPING_INCOMPLETE", assertThrows(ImportException.class,
        () -> service.importDirectory(Path.of("."),
            new BootstrapCredentials("test-employee-password", "test-admin-password", "different-admin"))).getCode());

    // 跨用户标识冲突不受单列唯一索引覆盖，重复导入必须再次拒绝。
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      User conflicting = new User("t03-conflicting-user", encoder.encode("test-only"), "冲突夹具",
          users.findById(report.adminId()).orElseThrow().getDepartment(), true);
      conflicting.updateProfile("SG000001", "测试岗位");
      users.save(conflicting);
    });
    assertEquals("IDENTIFIER_CONFLICT", assertThrows(ImportException.class,
        () -> service.importDirectory(Path.of("."), CREDENTIALS)).getCode());
    assertEquals(10002, users.count());
    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_roles", Integer.class));
  }

  /** 模拟损坏后调用真实导入Service，检查拒绝并回滚样例损坏，保持后续断言独立。 */
  private void assertRejectedMutation(String sql, String expectedCode) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      jdbc.update(sql);
      assertEquals(expectedCode, assertThrows(ImportException.class,
          () -> service.importDirectory(Path.of("."), CREDENTIALS)).getCode());
      status.setRollbackOnly();
    });
  }

  /** 对全部人员、组织来源逐条读回，而非只信任导入返回的计数。 */
  private void verifyAllSourceMappings() throws Exception {
    ImportPlan plan = new WorkbookImportPlanner().read(Path.of("."));
    Map<String, ImportPlan.Employee> expected = plan.employees().stream()
        .collect(Collectors.toMap(person -> person.username().trim(), Function.identity()));
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      entityManager.clear();
      Map<Long, User> storedUsers = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
      Map<String, Long> organizationIds = new HashMap<>();
      int checked = 0;
      for (ImportSource source : sources.findAll()) {
        if (source.getKind() == ImportSource.Kind.ORGANIZATION) {
          organizationIds.put(source.getSourceKey(), source.getDepartment().getId());
        } else if (source.getKind() == ImportSource.Kind.EMPLOYEE) {
          ImportPlan.Employee original = expected.get(source.getSourceKey());
          User user = storedUsers.get(source.getUser().getId());
          assertEquals(original.username().trim(), user.getUsername());
          assertEquals(original.name().trim(), user.getName());
          assertEquals(original.phone().trim(), user.getPhone());
          assertEquals(original.position(), user.getPosition());
          assertEquals(original.targetDepartment(), user.getDepartment().getName());
          assertTrue(user.isEnabled());
          assertTrue(user.getRoles().isEmpty());
          assertEquals(original.row(), source.getSourceRow());
          try {
            assertEquals(List.of(original.name(), original.username(), original.company(), original.department(),
                original.position(), original.phone()), new ObjectMapper().readValue(source.getSourceJson(), List.class));
          } catch (Exception exception) {
            throw new AssertionError("T03来源六列不能读回", exception);
          }
          checked++;
        }
      }
      assertEquals(10000, checked);
      assertEquals(111, organizationIds.size());
      assertEquals(organizationIds.get("总公司"), organizationIds.get("公司领导"));
      User first = users.findByUsernameOrPhone("SG000001", "SG000001").getFirst();
      User second = users.findByUsernameOrPhone("SG000002", "SG000002").getFirst();
      User admin = users.findByUsernameOrPhone("admin", "admin").getFirst();
      assertTrue(encoder.matches(CREDENTIALS.getEmployeePassword(), first.getPasswordHash()));
      assertTrue(encoder.matches(CREDENTIALS.getAdminPassword(), admin.getPasswordHash()));
      assertFalse(encoder.matches(CREDENTIALS.getEmployeePassword(), admin.getPasswordHash()));
      assertNotEquals(first.getPasswordHash(), second.getPasswordHash());
      assertNull(admin.getPhone());
      assertTrue(admin.getRoles().isEmpty());
    });
  }
}
