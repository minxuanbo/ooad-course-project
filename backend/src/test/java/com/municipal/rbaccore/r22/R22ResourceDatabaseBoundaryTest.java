package com.municipal.rbaccore.r22;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import com.municipal.rbaccore.service.port.error.ImportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** T07测试profile不能把降低口令计算成本或建表能力带入部署库。 */
class R22ResourceDatabaseBoundaryTest {
  /** 明确的部署库只能在非测试profile且禁止自动建表时使用。 */
  @Test
  @DisplayName("R22 测试profile拒绝连接生产命名的整改库")
  void R22_RejectsTestProfilesOnDeploymentDatabase() {
    for (String profile : new String[] {"core-resource-test", "core-http-test"}) {
      MockEnvironment environment = environment("rbac_core");
      environment.setActiveProfiles(profile);
      assertThrows(ImportException.class, () -> RuntimeConfiguration.databaseGuard(environment));
    }
  }

  /** 每种测试profile仅接其对应的本机隔离库，错配及缺省均拒绝。 */
  @Test
  @DisplayName("R22 测试profile和隔离库必须匹配")
  void R22_RequiresMatchingIsolatedDatabase() {
    MockEnvironment environment = environment("rbac_core_t07_test");
    assertThrows(ImportException.class, () -> RuntimeConfiguration.databaseGuard(environment));
    environment.setActiveProfiles("core-http-test");
    assertThrows(ImportException.class, () -> RuntimeConfiguration.databaseGuard(environment));
    environment.setActiveProfiles("core-resource-test");
    assertNotNull(RuntimeConfiguration.databaseGuard(environment));
  }

  /** 生产配置保留显式库名和validate，不允许开启旧初始化或自动DDL。 */
  @Test
  @DisplayName("R22 部署上下文要求明确库名及关闭旧初始化")
  void R22_RetainsDeploymentGuard() {
    MockEnvironment environment = environment("rbac_core");
    assertNotNull(RuntimeConfiguration.databaseGuard(environment));
    environment.withProperty("rbac.initialize", "true");
    assertThrows(ImportException.class, () -> RuntimeConfiguration.databaseGuard(environment));
  }

  /** 配置夹具仅创建属性对象，不实例化数据库连接或启动应用。 */
  private MockEnvironment environment(String database) {
    return new MockEnvironment()
        .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:3317/" + database)
        .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
        .withProperty("spring.sql.init.mode", "never")
        .withProperty("rbac.initialize", "false");
  }
}
