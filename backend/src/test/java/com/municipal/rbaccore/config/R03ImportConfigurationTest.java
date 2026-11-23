package com.municipal.rbaccore.config;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.service.port.error.ImportException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

/** R03部署装配边界：建连接前拒绝不安全配置，生产口令使用独立BCrypt盐。 */
class R03ImportConfigurationTest {
  /** 每一个隔离条件分别拒绝，防止仅依赖组合失败掩盖遗漏。 */
  @Test
  @DisplayName("R03 导入装配逐项拒绝空URL、非目标库、自动建表和旧初始化")
  void R03_guardsEachUnsafeConfiguration() {
    String safe = "jdbc:mysql://127.0.0.1:3353/rbac_core?serverTimezone=UTC";
    assertDoesNotThrow(() -> ImportConfiguration.requireIsolatedDatabase(safe, "never", "validate", "false"));
    for (String[] input : List.of(
        new String[] {null, "never", "validate", "false"},
        new String[] {"jdbc:mysql://localhost/other", "never", "validate", "false"},
        new String[] {safe, "always", "validate", "false"},
        new String[] {safe, "never", "create-drop", "false"},
        new String[] {safe, "never", "validate", "true"})) {
      assertEquals("IMPORT_DATABASE_UNSAFE", assertThrows(ImportException.class,
          () -> ImportConfiguration.requireIsolatedDatabase(input[0], input[1], input[2], input[3])).getCode());
    }
    MockEnvironment environment = new MockEnvironment()
        .withProperty("spring.datasource.url", safe)
        .withProperty("spring.sql.init.mode", "never")
        .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
        .withProperty("rbac.initialize", "false")
        .withProperty("rbac.core.import.employee-password", "employee-test")
        .withProperty("rbac.core.import.admin-password", "admin-test");
    assertDoesNotThrow(() -> ImportConfiguration.databaseGuard(environment)
        .postProcessBeanFactory(new DefaultListableBeanFactory()));
    environment.setProperty("rbac.core.import.admin-password", "");
    assertEquals("BOOTSTRAP_CONFIG_INVALID", assertThrows(ImportException.class,
        () -> ImportConfiguration.databaseGuard(environment)).getCode());
  }

  /** Bean工厂返回真实读取器和BCrypt，不能用固定摘要替换逐账号独立盐。 */
  @Test
  @DisplayName("R03 生产密码Bean使用独立BCrypt盐且验证原始口令")
  void R03_productionBeansUseRealHashing() {
    ImportConfiguration configuration = new ImportConfiguration();
    assertNotNull(configuration.planner());
    PasswordEncoder encoder = configuration.passwordEncoder();
    String first = encoder.encode("test-password");
    String second = encoder.encode("test-password");
    assertNotEquals(first, second);
    assertTrue(encoder.matches("test-password", first));
    assertFalse(encoder.matches("wrong-password", first));
  }
}
