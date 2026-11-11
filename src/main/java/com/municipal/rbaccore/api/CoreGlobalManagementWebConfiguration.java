package com.municipal.rbaccore.api;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 全局资源与角色请求共用提前鉴权，执行期仍进入原Controller AOP。 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CoreGlobalManagementWebConfiguration implements WebMvcConfigurer {
  private final CoreGlobalManagementPreflight preflight;

  /** preHandle早于请求体解析和Bean Validation，目标再由处理器类型精确限定。 */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(preflight)
        .addPathPatterns(
            "/api/systems",
            "/api/permissions/**",
            "/api/api-resources/**",
            "/api/roles",
            "/api/roles/**");
  }
}
