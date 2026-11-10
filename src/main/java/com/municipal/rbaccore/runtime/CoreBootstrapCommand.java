package com.municipal.rbaccore.runtime;

import com.municipal.rbaccore.bootstrap.InitialAuthorizationService;
import com.municipal.rbaccore.bootstrap.InitialRole;
import com.municipal.rbaccore.bootstrap.RoleBootstrapPlan;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/** 首次授权的离线预检/执行入口，不增加产品授权接口。 */
public final class CoreBootstrapCommand {
  private CoreBootstrapCommand() {}

  /** 指定preview-file时仅输出可复核CSV，否则按已确认规则事务初始化。 */
  public static void main(String[] args) throws Exception {
    SpringApplication application = new SpringApplication(CoreRuntimeConfiguration.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setDefaultProperties(Map.of("spring.config.name", "application-core-runtime"));
    try (ConfigurableApplicationContext context = application.run(args)) {
      InitialAuthorizationService service = context.getBean(InitialAuthorizationService.class);
      Path directory = Path.of(context.getEnvironment().getProperty("rbac.core.source-dir", "."));
      String preview = context.getEnvironment().getProperty("rbac.core.preview-file");
      if (preview != null) {
        RoleBootstrapPlan plan = service.preview(directory);
        Path file = Path.of(preview).toAbsolutePath().normalize();
        if (!file.getFileName().toString().endsWith(".csv")) {
          throw new IllegalArgumentException("预检清单必须为新的CSV文件");
        }
        try (java.io.BufferedWriter writer =
            Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
          writer.write("username,role,sourceRow\n");
          for (RoleBootstrapPlan.Assignment assignment : plan.assignments()) {
            writer.write(
                csv(assignment.username())
                    + ","
                    + csv(assignment.role().getName())
                    + ","
                    + assignment.sourceRow()
                    + "\n");
          }
          writer.write(
              csv(service.initialAdminUsername())
                  + ","
                  + csv(InitialRole.SYSTEM_ADMIN.getName())
                  + ",\n");
        }
        System.out.println(
            "预检完成，未写入授权。角色人数=" + plan.plannedCounts() + "，清单摘要=" + plan.manifestSha256());
      } else {
        System.out.println(service.initialize(directory));
      }
    }
  }

  /** 每个字符串按CSV双引号转义，支持配置账号名中的逗号或引号。 */
  private static String csv(String value) {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
