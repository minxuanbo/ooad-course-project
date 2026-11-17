package com.municipal.rbaccore.bootstrap;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** 同一工程的整改HTTP入口，产品默认启动的最终切换由T12完成。 */
public final class RbacApplication {
  private RbacApplication() {}

  /** 显式加载整改配置，不扫描旧Controller、JDBC初始化或数据库会话。 */
  public static void main(String[] args) {
    SpringApplication application = new SpringApplication(RuntimeConfiguration.class);
    application.setWebApplicationType(WebApplicationType.SERVLET);
    application.setDefaultProperties(Map.of("spring.config.name", "application-core-runtime"));
    application.run(args);
  }
}
