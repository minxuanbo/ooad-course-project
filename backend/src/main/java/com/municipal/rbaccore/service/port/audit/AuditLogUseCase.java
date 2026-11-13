package com.municipal.rbaccore.service.port.audit;

/** 最小鉴权日志查询用例，不包含归档或汇总报表。 */
public interface AuditLogUseCase {

  /** 返回同筛选快照中的一批日志及总数。 */
  LogPageResult list(String header, LogQuery query);
}
