package com.municipal.rbaccore.config;

import com.municipal.rbaccore.infrastructure.persistence.ddd.JpaInitializationRepositoryAdapter;
import com.municipal.rbaccore.service.ddd.application.importing.DddImportService;
import com.municipal.rbaccore.service.structured.importing.StructuredImportService;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/** 离线进程只加载选定策略的导入服务和必要适配器。 */
public final class ImportStrategySelector implements ImportSelector, EnvironmentAware {
  private Environment environment;

  /** 接收与产品入口一致的策略属性。 */
  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  /** 导入不装配HTTP、令牌或首次授权用例。 */
  @Override
  public String[] selectImports(AnnotationMetadata metadata) {
    return switch (BackendStrategy.from(environment)) {
      case STRUCTURED -> new String[] {StructuredImportService.class.getName()};
      case DDD ->
          new String[] {
            DddImportService.class.getName(), JpaInitializationRepositoryAdapter.class.getName()
          };
    };
  }
}
