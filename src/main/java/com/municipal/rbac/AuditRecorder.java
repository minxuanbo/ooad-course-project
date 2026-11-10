package com.municipal.rbac;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 旧链路的JDBC记录适配器，保留至T12清理旧产品入口。 */
@Service
@RequiredArgsConstructor
class AuditRecorder implements AuditWriter {
  private final AuditRepository repository;

  /** 独立提交旧登录与拒绝事件，统一入口接收安全值对象。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditEvent event) {
    repository.record(
        event.userId(),
        event.username(),
        event.eventType(),
        event.allowed(),
        event.requestId(),
        event.reason(),
        event.httpMethod(),
        event.requestPath(),
        event.httpStatus());
  }
}
