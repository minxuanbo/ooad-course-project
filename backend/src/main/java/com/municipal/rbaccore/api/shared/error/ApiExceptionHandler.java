package com.municipal.rbaccore.api.shared.error;

import com.municipal.rbaccore.api.shared.ApiResponse;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 新旧接口共享稳定错误响应，不输出异常、SQL或请求参数。 */
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {
  private final RequestContext requests;

  /** 业务层已翻译的异常保留其状态与错误码。 */
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiResponse<Object>> api(BusinessException e) {
    return error(HttpStatus.valueOf(e.getStatus()), e.getCode(), e.getMessage());
  }

  /** 请求解析或Bean Validation失败统一返回输入错误。 */
  @ExceptionHandler({
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.validation.BindException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class
  })
  ResponseEntity<ApiResponse<Object>> invalid(Exception e) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请求参数或 JSON 格式错误");
  }

  /** 缺少必要身份头不暴露业务数据。 */
  @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
  ResponseEntity<ApiResponse<Object>> missingHeader(Exception e) {
    return error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先登录");
  }

  /** 缺少处理器与静态资源沿同一端点不存在契约响应。 */
  @ExceptionHandler({
    org.springframework.web.servlet.NoHandlerFoundException.class,
    org.springframework.web.servlet.resource.NoResourceFoundException.class
  })
  ResponseEntity<ApiResponse<Object>> notFound(Exception e) {
    return error(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "接口不存在");
  }

  /** 路径存在但HTTP方法未提供时返回405。 */
  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Object>> method(Exception e) {
    return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不支持");
  }

  /** 兼容旧切片的兜底；新写操作仍在Service保存边界翻译约束错误。 */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiResponse<Object>> conflict(Exception e) {
    return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "数据违反唯一性或关联约束");
  }

  /** 未预期故障隐藏内部消息，只返回可追踪的requestId。 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> unexpected(Exception e) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误");
  }

  /** 错误码交共享过滤器记录，响应外形与成功包装保持一致。 */
  private ResponseEntity<ApiResponse<Object>> error(
      HttpStatus status, String code, String message) {
    requests.error(code);
    return ResponseEntity.status(status)
        .body(new ApiResponse<>(code, message, null, requests.requestId()));
  }
}
