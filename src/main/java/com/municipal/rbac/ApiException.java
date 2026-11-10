package com.municipal.rbac;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 新旧业务共用的安全业务异常，HTTP状态与错误码保持既有响应契约。 */
@Getter
public class ApiException extends RuntimeException {
  final HttpStatus status;
  final String code;

  /** 消息只包含可公开的业务说明，不传入SQL、口令或令牌。 */
  public ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  /** 输入格式不满足契约时使用稳定的400错误。 */
  public static ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }

  /** 保留旧调用方的默认输入错误。 */
  public static ApiException bad(String message) {
    return bad("INVALID_ARGUMENT", message);
  }

  /** 缺少实际API操作授权，不依据角色名称判断。 */
  public static ApiException forbidden() {
    return new ApiException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "没有执行该操作的权限");
  }

  /** 范围外对象不暴露是否存在。 */
  public static ApiException scopeDenied() {
    return new ApiException(HttpStatus.FORBIDDEN, "MANAGEMENT_SCOPE_DENIED", "目标不在允许的管理范围内");
  }

  /** 已提交的可信身份失效，沿既有会话错误码返回。 */
  public static ApiException unauthorized() {
    return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "登录已失效");
  }
}
