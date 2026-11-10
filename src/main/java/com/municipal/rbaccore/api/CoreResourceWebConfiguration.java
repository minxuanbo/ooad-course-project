package com.municipal.rbaccore.api;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 提前鉴权只注册到资源请求，其他切片的Controller/AOP入口保持原有规则。 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CoreResourceWebConfiguration implements WebMvcConfigurer {
  private final CoreResourcePreflight preflight;

  /** preHandle早于请求体解析和Bean Validation，目标再由处理器类型精确限定。 */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(preflight)
        .addPathPatterns("/api/systems", "/api/permissions/**", "/api/api-resources/**");
  }
}
