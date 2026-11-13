package com.municipal.rbaccore.architecture;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.shared.TestProjectPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T12以纯源码边界核对旧模型与旧功能清除，不依赖数据库或网络。 */
class R19LegacyRemovalBoundaryTest {
  private static final Path ROOT = TestProjectPaths.repository();
  private static final Set<String> KEPT_RBAC_SKELETON =
      Set.of(
          "BusinessException.java",
          "ApiExceptionHandler.java",
          "ApiResponse.java",
          "ApiResponseAdvice.java",
          "AuditEvent.java",
          "AuditWriter.java",
          "ExcelXmlReader.java",
          "RequestAuditFilter.java");

  /** 主源码不再出现独立公司模型、统一标识、会话、交接、归档扩展及旧JDBC调用。 */
  @Test
  @DisplayName("R19/R21 主源码与前端不再引用已清除旧物")
  void R19_LegacyIdentifiersAreRemovedFromMainSources() throws IOException {
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
            ROOT.resolve("backend/src/main/java"),
            ROOT.resolve("backend/src/main/resources"),
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

  /** 共享支撑已归入各层，旧包不再作为运行时依赖。 */
  @Test
  @DisplayName("R19 旧包已移除且共享支撑只保留一份")
  void R19_oldPackageIsRemoved() {
    assertFalse(Files.exists(ROOT.resolve("backend/src/main/java/com/municipal/rbac")));
    assertTrue(
        Files.exists(
            ROOT.resolve(
                "backend/src/main/java/com/municipal/rbaccore/api/shared/ApiResponse.java")));
  }

  /** 旧建表脚本、旧默认配置与触发器安装脚本删除，新链路产物在位。 */
  @Test
  @DisplayName("R19/R21 旧脚本与配置删除且新链路产物在位")
  void R19_LegacyArtifactsAreAbsentAndCoreArtifactsPresent() {
    for (String absent :
        List.of(
            "backend/src/main/resources/schema.sql",
            "backend/src/main/resources/application.yml",
            "deploy/install-login-constraints.sql")) {
      assertFalse(Files.exists(ROOT.resolve(absent)), absent + " 应已删除");
    }
    for (String present :
        List.of(
            "backend/src/main/resources/application-core-runtime.yml",
            "backend/src/main/resources/application-core-import.yml",
            "deploy/rbac-core-schema.sql",
            "deploy/rbac-core-runtime.sql",
            "backend/src/main/java/com/municipal/rbaccore/bootstrap/RbacApplication.java",
            "backend/src/main/java/com/municipal/rbaccore/bootstrap/BootstrapAuthorizationCommand.java")) {
      assertTrue(Files.exists(ROOT.resolve(present)), present + " 必须保留");
    }
  }

  /** 默认打包入口为整改HTTP命令，构建不再依赖旧JDBC starter或丢失MySQL驱动。 */
  @Test
  @DisplayName("R19/R21 构建与部署入口切换到整改链路")
  void R19_BuildAndDeploymentSwitchToCoreRuntime() throws IOException {
    String pom = Files.readString(ROOT.resolve("backend/pom.xml"), StandardCharsets.UTF_8);
    assertTrue(
        pom.contains("<mainClass>com.municipal.rbaccore.bootstrap.RbacApplication</mainClass>"));
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
  @DisplayName("R19/R21 正式前端保留整改入口并排除旧演示业务入口")
  void R19_FrontendExcludesLegacyBusinessWorkspaces() {
    for (String absent :
        List.of(
            "frontend/src/pages/Portal.vue",
            "frontend/src/pages/System.vue",
            "frontend/src/pages/Organizations.vue",
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
  @DisplayName("R19/R21 登录双入口与手机号登录保留")
  void R19_LoginDualEntryIsPreserved() throws IOException {
    String loginPage =
        Files.readString(ROOT.resolve("frontend/src/pages/Login.vue"), StandardCharsets.UTF_8);
    assertTrue(loginPage.contains("工号或手机号"));
    String importConfig =
        Files.readString(
            ROOT.resolve("backend/src/main/resources/application-core-import.yml"),
            StandardCharsets.UTF_8);
    assertFalse(importConfig.contains("employee_no"));
    // 新用户模型只保存一份工号(username)，User实体不声明第二工号字段。
    String userModel =
        Files.readString(
            ROOT.resolve(
                "backend/src/main/java/com/municipal/rbaccore/infrastructure/persistence/entity/UserEntity.java"),
            StandardCharsets.UTF_8);
    assertFalse(Arrays.stream(userModel.split("\n")).anyMatch(line -> line.contains("employeeNo")));
  }
}
