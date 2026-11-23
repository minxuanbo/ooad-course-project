package com.municipal.rbaccore.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.AuthorizationLogJpaRepository;
import com.municipal.rbaccore.service.port.audit.AuditEvent;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/** R16日志写入端口的故障协议，事务与取号顺序另由真实MySQL测试覆盖。 */
class R16AuditWriterBoundaryTest {
  /** 缺协调行不能无锁追加，运维事件不携带身份或底层异常。 */
  @Test
  @DisplayName("R16 日志协调行缺失时不写日志且只报告安全故障类型")
  void R16_missingAppendLockPublishesSafeFailure() {
    AuthorizationLogJpaRepository logs = mock(AuthorizationLogJpaRepository.class);
    ApplicationSystemJpaRepository systems = mock(ApplicationSystemJpaRepository.class);
    when(systems.lockForAuditAppend("rbac")).thenReturn(Optional.empty());
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    assertDoesNotThrow(() -> new JpaAuditWriter(logs, systems, transactions, Clock.systemUTC(), events)
        .record(mock(AuditEvent.class)));
    verifyNoInteractions(logs);
    ArgumentCaptor<JpaAuditWriter.WriteFailure> failure = ArgumentCaptor.forClass(JpaAuditWriter.WriteFailure.class);
    verify(events).publishEvent(failure.capture());
    assertEquals("DataRetrievalFailureException", failure.getValue().exceptionType());
    verify(transactions).rollback(any());
  }
}
