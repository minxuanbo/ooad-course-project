package com.municipal.rbaccore.infrastructure.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 资源、角色及人员管理请求共用提前鉴权，执行期仍进入原Controller AOP。 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class ManagementWebConfiguration implements WebMvcConfigurer {
  private final ManagementPreflight preflight;

  /** preHandle早于请求体解析和Bean Validation，目标再由处理器类型精确限定。 */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(preflight)
        .addPathPatterns(
            "/api/departments",
            "/api/departments/*",
            "/api/systems",
            "/api/permissions/**",
            "/api/api-resources/**",
            "/api/roles",
            "/api/roles/**",
            "/api/users/*/roles",
            "/api/users/*/assignable-roles",
            "/api/users/*/status",
            "/api/users/*/department");
  }
}
