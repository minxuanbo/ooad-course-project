package com.municipal.rbaccore.api.shared;

import com.municipal.rbaccore.service.port.security.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 仅业务Controller统一JSON包装；内部Actuator保留原始运维协议。 */
@RestControllerAdvice(basePackages = "com.municipal.rbaccore.api")
@RequiredArgsConstructor
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
  private final RequestContext requests;

  /** 管理端使用统一包装，外部既有身份协议保留原成功结构。 */
  public boolean supports(
      MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
    return !method.getContainingClass().isAnnotationPresent(RawResponse.class);
  }

  /** 已是标准响应时保留；普通VO附带本请求的追踪标识。 */
  public Object beforeBodyWrite(
      Object body,
      MethodParameter method,
      MediaType type,
      Class<? extends HttpMessageConverter<?>> converter,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return body instanceof ApiResponse<?> ? body : ApiResponse.success(body, requests.requestId());
  }
}
