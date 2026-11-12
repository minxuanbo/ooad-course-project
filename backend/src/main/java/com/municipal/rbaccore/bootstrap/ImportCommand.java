package com.municipal.rbaccore.bootstrap;

import com.municipal.rbaccore.config.BootstrapCredentialsFactory;
import com.municipal.rbaccore.config.ImportConfiguration;
import com.municipal.rbaccore.service.port.importing.BootstrapCredentials;
import com.municipal.rbaccore.service.port.importing.ImportReport;
import com.municipal.rbaccore.service.port.importing.ImportUseCase;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/** 在原Maven工程内执行一次离线导入；不替代产品启动入口。 */
public final class ImportCommand {
  private ImportCommand() {}

  /** 读取部署配置并关闭导入上下文，仅输出无个人明细及秘密的结果。 */
  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(ImportConfiguration.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setDefaultProperties(
        Map.of(
            "spring.config.name", "application-core-import",
            "spring.main.banner-mode", "off"));
    try (ConfigurableApplicationContext context = application.run(args)) {
      BootstrapCredentials credentials = BootstrapCredentialsFactory.from(context.getEnvironment());
      Path sourceDirectory =
          Path.of(context.getEnvironment().getProperty("rbac.core.import.source-dir", "."));
      ImportReport report =
          context.getBean(ImportUseCase.class).importDirectory(sourceDirectory, credentials);
      System.out.println(report);
      System.out.println("T03账号及资料导入核对完成；角色和权限由T05批量初始化。");
    }
  }
}
