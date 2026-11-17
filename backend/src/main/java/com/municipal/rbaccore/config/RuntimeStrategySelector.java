package com.municipal.rbaccore.config;

import com.municipal.rbaccore.service.structured.importing.StructuredImportService;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/** 产品和首次授权入口注册整套用例及其支持端口。 */
final class RuntimeStrategySelector implements ImportSelector, EnvironmentAware {
  private Environment environment;

  /** 接收Spring已经合并的属性来源。 */
  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  /** 返回唯一配置，避免另一策略的业务Bean被发现。 */
  @Override
  public String[] selectImports(AnnotationMetadata metadata) {
    return new String[] {
      switch (BackendStrategy.from(environment)) {
        case STRUCTURED -> Structured.class.getName();
        case DDD -> DomainDriven.class.getName();
      }
    };
  }

  /** 结构化部署使用原有事务脚本。 */
  @Configuration(proxyBeanMethods = false)
  @ComponentScan("com.municipal.rbaccore.service.structured")
  @Import(StructuredImportService.class)
  static class Structured {}

  /** DDD部署发现应用服务和专用持久化适配器。 */
  @Configuration(proxyBeanMethods = false)
  @ComponentScan({
    "com.municipal.rbaccore.service.ddd.application",
    "com.municipal.rbaccore.infrastructure.persistence.ddd"
  })
  static class DomainDriven {}
}
