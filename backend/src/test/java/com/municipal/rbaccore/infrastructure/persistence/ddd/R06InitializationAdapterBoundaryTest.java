package com.municipal.rbaccore.infrastructure.persistence.ddd;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipal.rbaccore.infrastructure.excel.SourceCellsJson;
import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.infrastructure.persistence.repository.*;
import com.municipal.rbaccore.service.ddd.application.bootstrap.DddInitialAuthorizationService;
import com.municipal.rbaccore.service.ddd.domain.model.BootstrapPlan;
import com.municipal.rbaccore.service.ddd.domain.repository.InitializationRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.error.ImportException;
import com.municipal.rbaccore.service.port.importing.SourceWorkbookReader;
import com.municipal.rbaccore.service.port.security.MaintenanceProtection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R06仓储读取协议的单元边界，不用模拟结果宣称SQL和事务已通过。 */
class R06InitializationAdapterBoundaryTest {
  /** 每个目录都独立影响判空，不靠数据库当前外键关系省掉安全检查。 */
  @Test
  @DisplayName("R06 DDD目录任一计数非零均非空，账号漂移不写完成回执")
  void R06_catalogCountsAndPublicationDrift() {
    for (int occupied = -1; occupied < 4; occupied++) {
      Fixture fixture = new Fixture();
      if (occupied == 0) when(fixture.roles.count()).thenReturn(1L);
      if (occupied == 1) when(fixture.systems.count()).thenReturn(1L);
      if (occupied == 2) when(fixture.permissions.count()).thenReturn(1L);
      if (occupied == 3) when(fixture.apis.count()).thenReturn(1L);
      assertEquals(occupied == -1, fixture.adapter.catalog().empty());
    }
    Fixture changed = new Fixture();
    when(changed.systems.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(changed.permissions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(changed.users.findAllForBootstrap()).thenReturn(List.of(
        new UserEntity("unplanned", "encoded", "未列入计划", new DepartmentEntity("root", "根"), true)));
    BusinessException failure = assertThrows(BusinessException.class, () -> changed.adapter.publish(
        new BootstrapPlan.Publication(List.of(), List.of(), new java.util.HashMap<>(), "hash", Instant.EPOCH)));
    assertEquals("BOOTSTRAP_CONFLICT", failure.getCode());
    verify(changed.receipts, never()).saveAndFlush(any());
  }

  /** 首次授权来源读取故障必须转换为冲突，不暴露持久化异常细节。 */
  @Test
  @DisplayName("R06 DDD首次授权转换来源读取故障")
  void R06_bootstrapReadFailureIsConflict() {
    InitializationRepository repository = mock(InitializationRepository.class);
    when(repository.inspect()).thenThrow(new ImportException("IMPORT_DATABASE_ERROR", "private-sql"));
    DddInitialAuthorizationService service = new DddInitialAuthorizationService(mock(SourceWorkbookReader.class),
        repository, mock(MaintenanceProtection.class), Clock.systemUTC());
    BusinessException failure = assertThrows(BusinessException.class, service::initialAdminUsername);
    assertEquals("BOOTSTRAP_CONFLICT", failure.getCode());
    assertFalse(failure.getMessage().contains("private"));
  }

  /** 仅控制仓储计数和快照，整批回滚由真实MySQL契约负责。 */
  private static final class Fixture {
    private final UserJpaRepository users = mock(UserJpaRepository.class);
    private final ApplicationSystemJpaRepository systems = mock(ApplicationSystemJpaRepository.class);
    private final ApiResourceJpaRepository apis = mock(ApiResourceJpaRepository.class);
    private final PermissionJpaRepository permissions = mock(PermissionJpaRepository.class);
    private final RoleJpaRepository roles = mock(RoleJpaRepository.class);
    private final BootstrapReceiptJpaRepository receipts = mock(BootstrapReceiptJpaRepository.class);
    private final JpaInitializationRepositoryAdapter adapter = new JpaInitializationRepositoryAdapter(
        new SourceCellsJson(new ObjectMapper()), mock(DepartmentJpaRepository.class), users,
        mock(ImportSourceJpaRepository.class), systems, apis, permissions, roles, receipts);
  }
}
