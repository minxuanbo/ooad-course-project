package com.municipal.rbac;

/** The sole HTTP response envelope. HTTP status remains meaningful. */
public record ApiResponse<T>(String code, String message, T data, String requestId) {
  static <T> ApiResponse<T> success(T data,String requestId){return new ApiResponse<>("OK","成功",data,requestId);}
}
