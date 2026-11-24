package com.municipal.rbaccore.bootstrap;

import com.municipal.rbaccore.config.RuntimeConfiguration;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** 产品HTTP入口，按配置互斥装配structured或DDD业务实现。 */
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
