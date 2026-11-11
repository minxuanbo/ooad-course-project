package com.municipal.rbaccore.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.importing.ImportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** T09测试配置只能连接其对应的本机可丢弃数据库。 */
class CoreRoleBoundaryT09Test {
  /** 不允许测试profile使用部署库、其他票测试库或外部数据库主机。 */
  @Test
  @DisplayName("T09 测试profile和本机专库严格匹配")
  void t09RequiresIsolatedDatabase() {
    for (String address :
        new String[] {
          "127.0.0.1:3319/rbac_core",
          "127.0.0.1:3319/rbac_core_t07_test",
          "external:3319/rbac_core_t09_test"
        }) {
      assertThrows(
          ImportException.class,
          () -> CoreRuntimeConfiguration.databaseGuard(environment(address)));
    }
    assertNotNull(
        CoreRuntimeConfiguration.databaseGuard(environment("127.0.0.1:3319/rbac_core_t09_test")));
  }

  /** 降低测试口令计算成本的profile不得绕过validate及禁止旧初始化的要求。 */
  @Test
  @DisplayName("T09 不允许测试上下文启用旧初始化或任意DDL")
  void t09RequiresSafeInitialization() {
    MockEnvironment environment = environment("127.0.0.1:3319/rbac_core_t09_test");
    environment.withProperty("rbac.initialize", "true");
    assertThrows(ImportException.class, () -> CoreRuntimeConfiguration.databaseGuard(environment));
    environment
        .withProperty("rbac.initialize", "false")
        .withProperty("spring.jpa.hibernate.ddl-auto", "create-drop");
    assertThrows(ImportException.class, () -> CoreRuntimeConfiguration.databaseGuard(environment));
  }

  /** 仅构造环境对象，拒绝案例不应实际建立数据库连接。 */
  private MockEnvironment environment(String address) {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:mysql://" + address)
            .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
            .withProperty("spring.sql.init.mode", "always")
            .withProperty("rbac.initialize", "false");
    environment.setActiveProfiles("core-role-test");
    return environment;
  }
}
