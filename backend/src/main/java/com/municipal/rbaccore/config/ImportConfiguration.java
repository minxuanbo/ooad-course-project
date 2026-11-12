package com.municipal.rbaccore.config;

import com.municipal.rbaccore.infrastructure.excel.WorkbookImportPlanner;
import com.municipal.rbaccore.infrastructure.persistence.entity.DepartmentEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.ImportSourceEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.structured.importing.StructuredImportService;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 离线导入使用的独立上下文，不扫描旧业务组件，不开放HTTP服务。 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackageClasses = {DepartmentEntity.class, ImportSourceEntity.class})
@EnableJpaRepositories(basePackageClasses = DepartmentJpaRepository.class)
@Import({
  StructuredImportService.class,
  com.municipal.rbaccore.infrastructure.security.BCryptPasswordHashing.class
})
public class ImportConfiguration {

  /** 在数据源实例化前检查明确的整改库和初始化隔离配置。 */
  @Bean
  public static BeanFactoryPostProcessor databaseGuard(Environment environment) {
    /** 仅允许本机明确的模型专库，防止create-drop落到其他库。 */
    requireIsolatedDatabase(
        environment.getProperty("spring.datasource.url"),
        environment.getProperty("spring.sql.init.mode"),
        environment.getProperty("spring.jpa.hibernate.ddl-auto"),
        environment.getProperty("rbac.initialize"));
    BootstrapCredentialsFactory.from(environment);
    return beanFactory -> {};
  }

  /** 只接显式指定的rbac_core库，DDL仅校验，不自动建旧表或重建数据。 */
  public static void requireIsolatedDatabase(
      String url, String sqlMode, String ddlMode, String oldInitialize) {
    if (url == null
        || !url.matches("jdbc:mysql://[^/?#\\s]+/rbac_core(?:\\?[^#]*)?")
        || !"never".equals(sqlMode)
        || !"validate".equals(ddlMode)
        || !"false".equals(oldInitialize)) {
      throw new ImportException(
          "IMPORT_DATABASE_UNSAFE",
          "离线导入只允许显式MySQL整改库rbac_core，sql.init=never、ddl=validate且关闭旧初始化");
    }
  }

  /** 生产口令采用BCrypt默认强度，每个账号独立生成盐。 */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** 只读取原始组织人员工作簿，不读取旧业务权限目录。 */
  @Bean
  WorkbookImportPlanner planner() {
    return new WorkbookImportPlanner();
  }
}
