package com.municipal.rbaccore.config;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.importing.BootstrapCredentials;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** R03装配守卫只验证配置，不连接名称相似的正式业务库。 */
class R03RuntimeConfigurationBoundaryTest {
  /** 生产URL正确也不能开启自动SQL初始化，口令强度不能沿用测试profile。 */
  @Test
  @DisplayName("R03 生产装配拒绝自动初始化且使用BCrypt默认强度")
  void R03_productionGuardAndPasswordStrengthRemainIndependent() {
    MockEnvironment production = new MockEnvironment()
        .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3353/rbac_core")
        .withProperty("spring.sql.init.mode", "always")
        .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
        .withProperty("rbac.initialize", "false");
    assertEquals("IMPORT_DATABASE_UNSAFE", assertThrows(ImportException.class,
        () -> RuntimeConfiguration.databaseGuard(production)).getCode());
    production.setProperty("spring.sql.init.mode", "never");
    assertDoesNotThrow(() -> RuntimeConfiguration.databaseGuard(production));
    String hash = new RuntimeConfiguration().passwordEncoder(production).encode("test-only-password");
    assertEquals("10", hash.substring(4, 6));
  }

  /** 管理员名称以字符数限制并清理边界，口令不被同样trim或输出。 */
  @Test
  @DisplayName("R03 管理员名称缺失空白或超长均拒绝，最大长度允许")
  void R03_administratorNameHasExplicitBoundaries() {
    for (String name : java.util.Arrays.asList(null, " ", "a".repeat(101))) {
      assertEquals("BOOTSTRAP_CONFIG_INVALID", assertThrows(ImportException.class,
          () -> new BootstrapCredentials("employee-test", "admin-test", name)).getCode());
    }
    assertEquals("a".repeat(100), new BootstrapCredentials("employee-test", "admin-test", " " + "a".repeat(100) + " ").getAdminUsername());
    BusinessException compatibility = BusinessException.bad("公开输入说明");
    assertEquals(400, compatibility.getStatus());
    assertEquals("INVALID_ARGUMENT", compatibility.getCode());
    assertEquals("公开输入说明", compatibility.getMessage());
  }
}
