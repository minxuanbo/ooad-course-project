package com.municipal.rbaccore.runtime;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.municipal.rbac.ApiExceptionHandler;
import com.municipal.rbac.ApiResponseAdvice;
import com.municipal.rbac.RequestAuditFilter;
import com.municipal.rbaccore.importing.ImportException;
import java.time.Clock;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** T05实际HTTP上下文，仅扫描整改链路，不加载旧初始化、会话表及产品Controller。 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackages = "com.municipal.rbaccore")
@EnableJpaRepositories(
    basePackages = {
      "com.municipal.rbaccore.repository",
      "com.municipal.rbaccore.importing",
      "com.municipal.rbaccore.logging",
      "com.municipal.rbaccore.bootstrap"
    })
@ComponentScan(
    basePackages = {
      "com.municipal.rbaccore.api",
      "com.municipal.rbaccore.service",
      "com.municipal.rbaccore.security",
      "com.municipal.rbaccore.logging",
      "com.municipal.rbaccore.bootstrap"
    })
@Import({ApiResponseAdvice.class, ApiExceptionHandler.class, RequestAuditFilter.class})
public class CoreRuntimeConfiguration {

  /** 生产只接明确的整改库；测试建表仅允许对应profile下的本机专用测试库。 */
  @Bean
  static BeanFactoryPostProcessor databaseGuard(Environment environment) {
    String url = environment.getProperty("spring.datasource.url", "");
    boolean isolatedTest =
        environment.acceptsProfiles(Profiles.of("core-http-test"))
            && url.matches(
                "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}/rbac_core_t05_test(?:\\?.*)?");
    isolatedTest =
        isolatedTest
            || (environment.acceptsProfiles(Profiles.of("core-resource-test"))
                && url.matches(
                    "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}/rbac_core_t07_test(?:\\?.*)?"));
    boolean production =
        !environment.acceptsProfiles(Profiles.of("core-http-test", "core-resource-test"))
            && url.matches("jdbc:mysql://[^/?#\\s]+/rbac_core(?:\\?[^#]*)?")
            && "never".equals(environment.getProperty("spring.sql.init.mode"));
    if ((!isolatedTest && !production)
        || !"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
        || !"false".equals(environment.getProperty("rbac.initialize"))) {
      throw new ImportException("IMPORT_DATABASE_UNSAFE", "只允许显式整改库及匹配的隔离配置，不加载旧初始化");
    }
    return beanFactory -> {};
  }

  /** 日志和原Bearer生命周期均使用UTC时钟。 */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** 生产沿用BCrypt默认强度，专用测试profile仅降低夹具计算成本。 */
  @Bean
  PasswordEncoder passwordEncoder(Environment environment) {
    return new BCryptPasswordEncoder(
        environment.acceptsProfiles(Profiles.of("core-http-test", "core-resource-test")) ? 4 : 10);
  }

  /** JSON遵守契约字段类型，拒绝账号、ID、布尔值和枚举的隐式类型转换。 */
  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictJsonInput() {
    return builder ->
        builder.postConfigurer(
            mapper -> {
              mapper.enable(
                  com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
              mapper.enable(
                  com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
              mapper.enable(
                  com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
              mapper
                  .coercionConfigFor(LogicalType.Textual)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
              mapper
                  .coercionConfigFor(LogicalType.Integer)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
              mapper
                  .coercionConfigFor(LogicalType.Boolean)
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
              mapper
                  .coercionConfigFor(LogicalType.Enum)
                  .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
            });
  }
}
