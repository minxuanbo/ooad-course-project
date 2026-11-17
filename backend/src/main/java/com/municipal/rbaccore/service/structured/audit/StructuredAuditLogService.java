package com.municipal.rbaccore.service.structured.audit;

import com.municipal.rbaccore.service.port.audit.AuditLogUseCase;
import com.municipal.rbaccore.service.port.audit.AuditQueryGateway;
import com.municipal.rbaccore.service.port.audit.LogPageResult;
import com.municipal.rbaccore.service.port.audit.LogQuery;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.structured.auth.StructuredAuthenticationService;
import com.municipal.rbaccore.service.structured.security.StructuredDepartmentScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 结构化日志查询保留实际操作门禁与可信根检查。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StructuredAuditLogService implements AuditLogUseCase {
  private final StructuredAuthenticationService auth;
  private final StructuredDepartmentScopeService scope;
  private final AuditQueryGateway queries;

  /** 每批核对可信根；快照细节由公共查询适配器提供。 */
  @Override
  public LogPageResult list(String header, LogQuery query) {
    CurrentIdentity actor = auth.require(header);
    if (!scope.isGlobal(actor)) throw BusinessException.scopeDenied();
    return queries.list(query);
  }
}
