package com.municipal.rbaccore.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T12以纯源码边界核对旧模型与旧功能清除，不依赖数据库或网络。 */
class T12LegacyRemovalBoundaryTest {
  private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
  private static final Set<String> KEPT_RBAC_SKELETON =
      Set.of(
          "ApiException.java",
          "ApiExceptionHandler.java",
          "ApiResponse.java",
          "ApiResponseAdvice.java",
          "AuditEvent.java",
          "AuditWriter.java",
          "ExcelXmlReader.java",
          "RequestAuditFilter.java");

  /** 主源码不再出现独立公司模型、统一标识、会话、交接、归档扩展及旧JDBC调用。 */
  @Test
  @DisplayName("T12 主源码与前端不再引用已清除旧物")
  void t12LegacyIdentifiersAreRemovedFromMainSources() throws IOException {
    List<String> forbidden =
        List.of(
            "employee_no",
            "employeeNo",
            "login_identifiers",
            "Handover",
            "handover",
            "authorization_changes",
            "role_management_permissions",
            "management_scopes",
            "JdbcTemplate",
            "companies",
            "companyId",
            "session-hours",
            "RBAC_DB_",
            "RBAC_INITIALIZE");
    List<Path> scopes =
        List.of(
            ROOT.resolve("src/main/java"),
            ROOT.resolve("src/main/resources"),
            ROOT.resolve("frontend/src"));
    for (Path scope : scopes) {
      try (Stream<Path> files = Files.walk(scope)) {
        for (Path file : files.filter(Files::isRegularFile).toList()) {
          String text = Files.readString(file, StandardCharsets.UTF_8);
          for (String word : forbidden) {
            assertFalse(text.contains(word), file + " 仍引用已清除旧物: " + word);
          }
        }
      }
    }
  }

  /** 共享骨架类保留在原包，其余旧包类全部删除。 */
  @Test
  @DisplayName("T12 旧包仅保留共享骨架类")
  void t12RbacPackageKeepsOnlySharedSkeleton() throws IOException {
    List<String> remaining;
    try (Stream<Path> files = Files.list(ROOT.resolve("src/main/java/com/municipal/rbac"))) {
      remaining = files.map(Path::getFileName).map(Path::toString).sorted().toList();
    }
    assertEquals(
        List.copyOf(KEPT_RBAC_SKELETON).stream().sorted().collect(Collectors.toList()), remaining);
  }

  /** 旧建表脚本、旧默认配置与触发器安装脚本删除，新链路产物在位。 */
  @Test
  @DisplayName("T12 旧脚本与配置删除且新链路产物在位")
  void t12LegacyArtifactsAreAbsentAndCoreArtifactsPresent() {
    for (String absent :
        List.of(
            "src/main/resources/schema.sql",
            "src/main/resources/application.yml",
            "deploy/install-login-constraints.sql")) {
      assertFalse(Files.exists(ROOT.resolve(absent)), absent + " 应已删除");
    }
    for (String present :
        List.of(
            "src/main/resources/application-core-runtime.yml",
            "src/main/resources/application-core-import.yml",
            "deploy/rbac-core-schema.sql",
            "deploy/rbac-core-runtime.sql",
            "src/main/java/com/municipal/rbaccore/runtime/CoreRuntimeCommand.java",
            "src/main/java/com/municipal/rbaccore/runtime/CoreBootstrapCommand.java")) {
      assertTrue(Files.exists(ROOT.resolve(present)), present + " 必须保留");
    }
  }

  /** 默认打包入口为整改HTTP命令，构建不再依赖旧JDBC starter或丢失MySQL驱动。 */
  @Test
  @DisplayName("T12 构建与部署入口切换到整改链路")
  void t12BuildAndDeploymentSwitchToCoreRuntime() throws IOException {
    String pom = Files.readString(ROOT.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertTrue(
        pom.contains("<mainClass>com.municipal.rbaccore.runtime.CoreRuntimeCommand</mainClass>"));
    assertFalse(pom.contains("spring-boot-starter-jdbc"));
    assertTrue(pom.contains("com.mysql"));
    assertTrue(pom.contains("clonefactory"));

    String stack = Files.readString(ROOT.resolve("deploy/stack.yml"), StandardCharsets.UTF_8);
    assertFalse(stack.contains("RBAC_DB_URL"));
    assertFalse(stack.contains("RBAC_INITIALIZE"));
    assertTrue(stack.contains("RBAC_CORE_DB_URL"));
    assertTrue(stack.contains("rbac_core"));
    assertTrue(stack.contains("/api/me"));

    String script =
        Files.readString(ROOT.resolve("deploy/test-backend.sh"), StandardCharsets.UTF_8);
    assertFalse(script.contains("log-bin-trust-function-creators"));
    assertFalse(script.contains("com/municipal/rbac/*.java"));
    assertTrue(script.contains("src/main/java"));
  }

  /** 旧前端失效入口删除，归档按钮与登录后提示假设不再出现。 */
  @Test
  @DisplayName("T12 前端仅保留登录与日志查询兼容入口")
  void t12FrontendKeepsOnlyLoginAndLogQuery() {
    for (String absent :
        List.of(
            "frontend/src/pages/Portal.vue",
            "frontend/src/pages/System.vue",
            "frontend/src/pages/Organizations.vue",
            "frontend/src/pages/Users.vue",
            "frontend/src/pages/Management.vue",
            "frontend/src/components/Handover.vue",
            "frontend/src/components/ManagementGrants.vue",
            "frontend/src/components/RoleManagementEditor.vue",
            "frontend/src/components/RoleEditor.vue")) {
      assertFalse(Files.exists(ROOT.resolve(absent)), absent + " 应已删除");
    }
    assertTrue(Files.exists(ROOT.resolve("frontend/src/pages/Login.vue")));
    assertTrue(Files.exists(ROOT.resolve("frontend/src/pages/AuditLogs.vue")));
    assertTrue(Files.exists(ROOT.resolve("frontend/tests/prototype.test.mjs")));
  }

  /** 工号/手机号双入口保留，不能为清理旧注册表取消手机号登录。 */
  @Test
  @DisplayName("T12 登录双入口与手机号登录保留")
  void t12LoginDualEntryIsPreserved() throws IOException {
    String loginPage =
        Files.readString(ROOT.resolve("frontend/src/pages/Login.vue"), StandardCharsets.UTF_8);
    assertTrue(loginPage.contains("工号或手机号"));
    String importConfig =
        Files.readString(
            ROOT.resolve("src/main/resources/application-core-import.yml"), StandardCharsets.UTF_8);
    assertFalse(importConfig.contains("employee_no"));
    // 新用户模型只保存一份工号(username)，User实体不声明第二工号字段。
    String userModel =
        Files.readString(
            ROOT.resolve("src/main/java/com/municipal/rbaccore/model/User.java"),
            StandardCharsets.UTF_8);
    assertFalse(Arrays.stream(userModel.split("\n")).anyMatch(line -> line.contains("employeeNo")));
  }
}
