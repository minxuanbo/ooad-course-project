package com.municipal.rbac;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** 沿用统一JSON响应外形，供旧入口与整改HTTP上下文复用。 */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
  /** 所有Controller响应均经过同一包装入口。 */
  public boolean supports(
      MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
    return true;
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
    return body instanceof ApiResponse<?>
        ? body
        : ApiResponse.success(body, RequestAuditFilter.requestId());
  }
}
