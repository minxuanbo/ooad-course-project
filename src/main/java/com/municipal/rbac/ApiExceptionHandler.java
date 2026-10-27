package com.municipal.rbac;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.*;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiResponse<Object>> api(ApiException e){return error(e.status,e.code,e.getMessage());}
  @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.validation.BindException.class})
  ResponseEntity<ApiResponse<Object>> invalid(Exception e){return error(HttpStatus.BAD_REQUEST,"INVALID_ARGUMENT","请求参数或 JSON 格式错误");}
  @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
  ResponseEntity<ApiResponse<Object>> missingHeader(Exception e){return error(HttpStatus.UNAUTHORIZED,"AUTHENTICATION_REQUIRED","请先登录");}
  @ExceptionHandler({org.springframework.web.servlet.NoHandlerFoundException.class,org.springframework.web.servlet.resource.NoResourceFoundException.class})
  ResponseEntity<ApiResponse<Object>> notFound(Exception e){return error(HttpStatus.NOT_FOUND,"ENDPOINT_NOT_FOUND","接口不存在");}
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Object>> method(Exception e){return error(HttpStatus.METHOD_NOT_ALLOWED,"METHOD_NOT_ALLOWED","请求方法不支持");}
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiResponse<Object>> conflict(Exception e){return error(HttpStatus.CONFLICT,"DATA_CONFLICT","数据违反唯一性或关联约束");}
  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Object>> unexpected(Exception e){return error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","服务器内部错误");}
  private ResponseEntity<ApiResponse<Object>> error(HttpStatus status,String code,String message){
    RequestAuditFilter.error(code);
    return ResponseEntity.status(status).body(new ApiResponse<>(code,message,null,RequestAuditFilter.requestId()));
  }
}
class ApiException extends RuntimeException {
  final HttpStatus status;final String code;
  ApiException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}
  static ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
  static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ARGUMENT",message);}
  static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"PERMISSION_DENIED","没有执行该操作的权限");}
  static ApiException scopeDenied(){return new ApiException(HttpStatus.FORBIDDEN,"MANAGEMENT_SCOPE_DENIED","目标不在允许的管理范围内");}
  static ApiException unauthorized(){return new ApiException(HttpStatus.UNAUTHORIZED,"SESSION_INVALID","登录已失效");}
}
