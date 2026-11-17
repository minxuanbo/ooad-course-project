package com.municipal.rbaccore.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.municipal.rbaccore.config.ImportConfiguration;
import com.municipal.rbaccore.config.RuntimeConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** 检查产品、导入和授权入口使用的真实配置，在连接数据库前记录选中的业务定义。 */
class R19BackendStrategyTest {
  /** 默认装配结构化，显式DDD时所有用例和门禁一起替换。 */
  @Test
  @DisplayName("R19 产品和首次授权配置按策略互斥注册全部业务组件")
  void runtimeSelectsOneStrategy() {
    List<String> structured = businessDefinitions(RuntimeConfiguration.class, null);
    List<String> ddd = businessDefinitions(RuntimeConfiguration.class, "ddd");
    assertThat(structured).anyMatch(name -> name.endsWith("StructuredAuthenticationService"));
    assertThat(structured).noneMatch(name -> name.contains(".ddd."));
    assertThat(ddd).noneMatch(name -> name.contains(".structured."));
    assertThat(ddd.stream().map(name -> name.substring(name.lastIndexOf('.') + 1)))
        .contains(
            "DddAuthenticationService",
            "DddUserQueryService",
            "DddUserManagementService",
            "DddRoleService",
            "DddResourceService",
            "DddDepartmentService",
            "DddOverviewService",
            "DddAuditLogService",
            "DddAuthorizationService",
            "DddManagementAccess",
            "DddMaintenanceGuard",
            "DddInitialAuthorizationService");
  }

  /** 离线导入按同一配置选择实现，保持独立于HTTP组件。 */
  @Test
  @DisplayName("R19 离线导入只装配选中策略的导入服务")
  void importingSelectsOneStrategy() {
    assertThat(businessDefinitions(ImportConfiguration.class, "structured"))
        .singleElement()
        .asString()
        .endsWith("StructuredImportService");
    assertThat(businessDefinitions(ImportConfiguration.class, "ddd"))
        .singleElement()
        .asString()
        .endsWith("DddImportService");
  }

  /** 配错时两种入口都应在接触数据源前失败。 */
  @Test
  @DisplayName("R19 产品和离线导入拒绝未知后端策略")
  void unknownStrategyFailsBeforeDatabase() {
    for (Class<?> entry : List.of(RuntimeConfiguration.class, ImportConfiguration.class)) {
      assertThatThrownBy(() -> businessDefinitions(entry, "unknown"))
          .hasStackTraceContaining("app.backend-strategy")
          .hasStackTraceContaining("unknown");
    }
  }

  /** 记录由真实配置扫描或导入的类名，中止其余启动阶段以避免访问任何库。 */
  private List<String> businessDefinitions(Class<?> entry, String strategy) {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context
          .getEnvironment()
          .getPropertySources()
          .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
      context
          .getEnvironment()
          .getPropertySources()
          .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
      if (strategy != null) {
        context
            .getEnvironment()
            .getPropertySources()
            .addFirst(
                new MapPropertySource(
                    "explicit-strategy", Map.of("app.backend-strategy", strategy)));
      }
      context.register(entry);
      context.addBeanFactoryPostProcessor(
          factory -> {
            List<String> definitions =
                Arrays.stream(factory.getBeanDefinitionNames())
                    .map(name -> factory.getBeanDefinition(name).getBeanClassName())
                    .filter(
                        name ->
                            name != null
                                && (name.contains(".service.structured.")
                                    || name.contains(".service.ddd.application.")))
                    .toList();
            throw new DefinitionsRecorded(definitions);
          });
      try {
        context.refresh();
        throw new AssertionError("记录定义后应立即停止创建数据源");
      } catch (DefinitionsRecorded captured) {
        return captured.definitions;
      }
    }
  }

  /** 仅供测试中止启动并携带记录结果。 */
  private static final class DefinitionsRecorded extends RuntimeException {
    private final List<String> definitions;

    private DefinitionsRecorded(List<String> definitions) {
      this.definitions = definitions;
    }
  }
}
