package com.municipal.rbaccore.service.port.audit;

/** 提供已授权日志的脱敏快照分页，不承担权限判断。 */
public interface AuditQueryGateway {
  /** 使用查询条件和游标生成对应快照页。 */
  LogPageResult list(LogQuery query);
}
