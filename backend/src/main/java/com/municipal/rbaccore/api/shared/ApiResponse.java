package com.municipal.rbaccore.api.shared;

/** 统一HTTP响应信封；业务错误码与HTTP状态分别保留各自语义。 */
public record ApiResponse<T>(String code, String message, T data, String requestId) {
  /** 包装安全业务结果并保留调用方提供的请求追踪号。 */
  static <T> ApiResponse<T> success(T data, String requestId) {
    return new ApiResponse<>("OK", "成功", data, requestId);
  }
}
