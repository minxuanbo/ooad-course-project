package com.municipal.rbaccore.r22;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.config.BootstrapCredentialsFactory;
import com.municipal.rbaccore.config.ImportConfiguration;
import com.municipal.rbaccore.infrastructure.excel.WorkbookImportPlanner;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.importing.BootstrapCredentials;
import com.municipal.rbaccore.service.structured.importing.LoginIdentifiers;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

/** T03 登录标识、部署秘密及独立库守卫的业务边界。 */
class R22ImportDatabaseBoundaryTest {
  @TempDir Path directory;

  /** 文件缺失时不能返回空的可执行计划。 */
  @Test
  @DisplayName("R22 源文件缺失报告稳定读取错误，不返回部分导入计划")
  void R22_RejectsMissingSourceFiles() {
    assertEquals(
        "SOURCE_READ_FAILED",
        assertThrows(ImportException.class, () -> new WorkbookImportPlanner().read(directory))
            .getCode());
  }

  /** 两列各自唯一仍可能跨列碰撞，必须按同一账号集合检查。 */
  @Test
  @DisplayName("R22 重复工号、重复手机号及跨用户跨字段冲突均拒绝")
  void R22_RejectsEveryCrossUserIdentifierCollision() {
    for (List<LoginIdentifiers.Identity> identities :
        List.of(
            List.of(identity("0001", "0101"), identity("0001", "0102")),
            List.of(identity("0001", "0101"), identity("0002", "0101")),
            List.of(identity("0001", "0101"), identity("0101", "0102")),
            List.of(identity("0001", "0101"), identity("0002", "0001")))) {
      assertEquals(
          "IDENTIFIER_CONFLICT",
          assertThrows(ImportException.class, () -> LoginIdentifiers.validate(identities))
              .getCode());
    }
    assertDoesNotThrow(
        () ->
            LoginIdentifiers.validate(
                List.of(
                    identity("0001", "0001"), identity("SG0001", null), identity("sg0001", " "))));
  }

  /** 配置缺项不得退回旧统一密码，也不允许BCrypt静默截断长口令。 */
  @Test
  @DisplayName("R22 两类初始口令必填且不泄露或截断")
  void R22_RequiresSeparateExplicitPasswordConfiguration() {
    assertEquals(
        "BOOTSTRAP_CONFIG_INVALID",
        assertThrows(
                ImportException.class,
                () -> BootstrapCredentialsFactory.from(new MockEnvironment()))
            .getCode());
    assertEquals(
        "BOOTSTRAP_CONFIG_INVALID",
        assertThrows(
                ImportException.class,
                () -> new BootstrapCredentials("employee", "中".repeat(25), "admin"))
            .getCode());
    BootstrapCredentials configured =
        BootstrapCredentialsFactory.from(
            new MockEnvironment()
                .withProperty("rbac.core.import.employee-password", " employee password ")
                .withProperty("rbac.core.import.admin-password", "admin password"));
    assertEquals(" employee password ", configured.getEmployeePassword());
    assertEquals("admin", configured.getAdminUsername());
    assertFalse(configured.toString().contains("password"));
  }

  /** 不允许旧库、无显式库名或危险DDL配置进入导入上下文。 */
  @Test
  @DisplayName("R22 导入命令拒绝旧库和自动重建结构")
  void R22_RejectsUnsafeDatabaseAndInitializationSettings() {
    for (String url :
        List.of(
            "",
            "jdbc:mysql://127.0.0.1:3317/rbac",
            "jdbc:mysql://127.0.0.1:3317/rbac_test",
            "jdbc:h2:mem:rbac_core",
            "jdbc:mysql://127.0.0.1:3317/rbac_core_backup")) {
      assertEquals(
          "IMPORT_DATABASE_UNSAFE",
          assertThrows(
                  ImportException.class,
                  () ->
                      ImportConfiguration.requireIsolatedDatabase(
                          url, "never", "validate", "false"))
              .getCode());
    }
    String valid = "jdbc:mysql://127.0.0.1:3317/rbac_core?connectionTimeZone=UTC";
    assertDoesNotThrow(
        () -> ImportConfiguration.requireIsolatedDatabase(valid, "never", "validate", "false"));
    assertThrows(
        ImportException.class,
        () -> ImportConfiguration.requireIsolatedDatabase(valid, "always", "validate", "false"));
    assertThrows(
        ImportException.class,
        () -> ImportConfiguration.requireIsolatedDatabase(valid, "never", "create-drop", "false"));
    assertThrows(
        ImportException.class,
        () -> ImportConfiguration.requireIsolatedDatabase(valid, "never", "validate", "true"));
  }

  /** 创建只含标识的样例，不把口令混入身份比较。 */
  private static LoginIdentifiers.Identity identity(String username, String phone) {
    return new LoginIdentifiers.Identity(username, phone);
  }
}
