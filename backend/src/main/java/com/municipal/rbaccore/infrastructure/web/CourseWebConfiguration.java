package com.municipal.rbaccore.infrastructure.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 课程生产前端直连身份服务，仅对显式配置的来源开放身份路径。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.course-integration.enabled", havingValue = "true")
public class CourseWebConfiguration implements WebMvcConfigurer {
  @Value("${app.course-integration.allowed-origins:http://127.0.0.1:5174}")
  private String[] allowedOrigins;

  /** 不扩展管理端跨域规则，也不使用任意来源与凭据组合。 */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/auth/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST")
        .allowedHeaders("Authorization", "Content-Type")
        .exposedHeaders("X-Request-Id")
        .maxAge(300);
  }
}
