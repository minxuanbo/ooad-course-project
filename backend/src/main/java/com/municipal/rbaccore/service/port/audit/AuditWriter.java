package com.municipal.rbaccore.service.port.audit;

/** 最小鉴权记录端口：旧过滤器复用同一调用，新链路由JPA实现。 */
public interface AuditWriter {

  /** 记录安全字段；调用方不得传入令牌、口令、请求体或查询串。 */
  void record(AuditEvent event);
}
