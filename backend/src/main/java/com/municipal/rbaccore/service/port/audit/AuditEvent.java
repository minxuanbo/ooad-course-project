package com.municipal.rbaccore.service.port.audit;

/** 最小鉴权记录的安全字段集合，不包含口令、令牌、请求体或查询串。 */
public record AuditEvent(
    Long userId,
    String username,
    String eventType,
    boolean allowed,
    String requestId,
    String reason,
    String httpMethod,
    String requestPath,
    int httpStatus,
    String appCode) {
  /** 保持RBAC原调用语义，课程兼容入口必须显式给出归属。 */
  public AuditEvent(
      Long userId,
      String username,
      String eventType,
      boolean allowed,
      String requestId,
      String reason,
      String httpMethod,
      String requestPath,
      int httpStatus) {
    this(
        userId,
        username,
        eventType,
        allowed,
        requestId,
        reason,
        httpMethod,
        requestPath,
        httpStatus,
        com.municipal.rbaccore.service.port.security.RbacCatalog.SYSTEM_CODE);
  }
}
