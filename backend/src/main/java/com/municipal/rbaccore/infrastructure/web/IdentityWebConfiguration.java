package com.municipal.rbaccore.infrastructure.web;

import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationProfileJpaRepository;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;

/** 显式接入入口可动态开放身份路径，CORS不代替应用状态及JWT鉴权。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.identity.enabled", havingValue = "true")
public class IdentityWebConfiguration {
  private final ApplicationProfileJpaRepository profiles;
  private final List<String> configured;

  /** 固定部署来源仍兼容保留，新增入口从当前数据库读取。 */
  public IdentityWebConfiguration(
      ApplicationProfileJpaRepository profiles,
      @Value("${app.identity.allowed-origins:}")
      String[] origins) {
    this.profiles = profiles;
    this.configured = Arrays.stream(origins).filter(value -> !value.isBlank()).toList();
  }

  /** 过滤器只映射身份路径，不扩展管理端跨域，也不允许Cookie凭据。 */
  @Bean
  public FilterRegistrationBean<CorsFilter> identityCors() {
    CorsFilter filter =
        new CorsFilter(
            request -> {
              if (request.getHeader("Origin") == null) return null;
              List<String> allowed = new ArrayList<>(configured);
              if (!configured.contains(request.getHeader("Origin")))
                for (String entry : profiles.activeEntryUrls())
                  origin(entry).ifPresent(allowed::add);
              CorsConfiguration config = new CorsConfiguration();
              config.setAllowedOrigins(allowed);
              config.setAllowedMethods(List.of("GET", "POST"));
              config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
              config.setExposedHeaders(List.of("X-Request-Id"));
              config.setMaxAge(300L);
              return config;
            });
    FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(filter);
    registration.addUrlPatterns("/auth/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }

  /** 仅从合法HTTP入口提取精确Origin，不开放任意来源或通配符。 */
  public static Optional<String> origin(String entry) {
    try {
      URI value = URI.create(entry);
      if (!("http".equalsIgnoreCase(value.getScheme())
              || "https".equalsIgnoreCase(value.getScheme()))
          || value.getHost() == null
          || value.getUserInfo() != null
          || value.getFragment() != null) return Optional.empty();
      int port = value.getPort();
      String host = value.getHost().toLowerCase(Locale.ROOT);
      String suffix =
          port < 0
                  || value.getScheme().equalsIgnoreCase("http") && port == 80
                  || value.getScheme().equalsIgnoreCase("https") && port == 443
              ? ""
              : ":" + port;
      return Optional.of(value.getScheme().toLowerCase(Locale.ROOT) + "://" + host + suffix);
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
