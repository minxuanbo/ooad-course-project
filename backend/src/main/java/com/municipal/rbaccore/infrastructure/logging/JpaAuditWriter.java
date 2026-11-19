package com.municipal.rbaccore.infrastructure.logging;

import com.municipal.rbaccore.infrastructure.persistence.entity.AuthorizationLogEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.AuthorizationLogJpaRepository;
import com.municipal.rbaccore.service.port.audit.AuditEvent;
import com.municipal.rbaccore.service.port.audit.AuditWriter;
import com.municipal.rbaccore.service.port.security.RbacCatalog;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/** 复用原过滤器记录端口，日志独立事务；记录失败不能改变已判定的授权结果。 */
@Service
@RequiredArgsConstructor
public class JpaAuditWriter implements AuditWriter {
  private final AuthorizationLogJpaRepository repository;
  private final ApplicationSystemJpaRepository systems;
  private final PlatformTransactionManager transactionManager;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  /** 单独提交允许与拒绝记录；日志故障只发安全运维事件，不泄漏SQL或让请求越权。 */
  @Override
  public void record(AuditEvent event) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    try {
      transaction.executeWithoutResult(
          status -> {
            // 取号前锁定固定行，锁由事务提交/回滚释放；后续追加不能抢先提交更大ID。
            systems
                .lockForAuditAppend(RbacCatalog.SYSTEM_CODE)
                .orElseThrow(() -> new DataRetrievalFailureException("RBAC日志追加协调行缺失"));
            repository.save(new AuthorizationLogEntity(event, clock.instant()));
          });
    } catch (DataAccessException | TransactionException exception) {
      events.publishEvent(new WriteFailure(exception.getClass().getSimpleName()));
    }
  }

  /** 运维事件不携带原异常、身份、参数或SQL，仅给集中AOP日志输出异常类型。 */
  public record WriteFailure(String exceptionType) {}
}
