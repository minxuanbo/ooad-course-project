package com.municipal.rbac;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
  public boolean supports(MethodParameter method,Class<? extends HttpMessageConverter<?>> converter){return true;}
  public Object beforeBodyWrite(Object body,MethodParameter method,MediaType type,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return body instanceof ApiResponse<?>?body:ApiResponse.success(body,RequestAuditFilter.requestId());
  }
}
