package com.municipal.rbac;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<Map<String,Object>> api(ApiException e) { return ResponseEntity.status(e.status).body(Map.of("error",e.getMessage())); }
  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String,Object>> unexpected(Exception e) { return ResponseEntity.status(500).body(Map.of("error","服务器内部错误")); }
}
class ApiException extends RuntimeException {
  final HttpStatus status;
  ApiException(HttpStatus status,String message){super(message);this.status=status;}
  static ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
  static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"没有执行该操作的权限");}
  static ApiException unauthorized(){return new ApiException(HttpStatus.UNAUTHORIZED,"登录已失效");}
}
