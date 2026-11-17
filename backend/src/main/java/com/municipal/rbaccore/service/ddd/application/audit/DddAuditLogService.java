package com.municipal.rbaccore.service.ddd.application.audit;

import com.municipal.rbaccore.service.port.audit.AuditLogUseCase;
import com.municipal.rbaccore.service.port.audit.AuditQueryGateway;
import com.municipal.rbaccore.service.port.audit.LogPageResult;
import com.municipal.rbaccore.service.port.audit.LogQuery;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 日志查询每一页重新检查实际API和可信共同根。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DddAuditLogService implements AuditLogUseCase {
  private final ManagementAccessUseCase access;
  private final AuditQueryGateway queries;

  /** 游标只表示技术快照，不具备授权效力。 */
  @Override
  public LogPageResult list(String header, LogQuery query) {
    access.require(header);
    return queries.list(query);
  }
}
