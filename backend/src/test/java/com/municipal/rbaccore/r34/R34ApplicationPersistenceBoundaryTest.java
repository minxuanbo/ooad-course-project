package com.municipal.rbaccore.r34;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.municipal.rbaccore.infrastructure.persistence.ddd.JpaApplicationSystemRepositoryAdapter;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import com.municipal.rbaccore.service.port.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessResourceFailureException;
import com.municipal.rbaccore.api.resource.dto.SystemQueryDTO;
import java.util.Optional;

/** R34持久化技术故障以端口边界注入，不替代真实MySQL业务事务回归。 */
class R34ApplicationPersistenceBoundaryTest {
  /** 数据库并发冲突必须与结构化操作同样返回稳定409，而不是泄露底层500。 */
  @Test
  @DisplayName("R34 应用保存并发冲突翻译为409且隐藏数据库内容")
  void R34_ConcurrentWriteIsConflict() {
    ApplicationSystemJpaRepository rows = mock(ApplicationSystemJpaRepository.class);
    when(rows.saveAndFlush(any())).thenThrow(new ConcurrencyFailureException("private-sql"));
    BusinessException error = assertThrows(BusinessException.class, () ->
        new JpaApplicationSystemRepositoryAdapter(rows).save(new ApplicationSystem(null, "third", "第三系统", true)));
    assertEquals(409, error.getStatus());
    assertEquals("DATA_CONFLICT", error.getCode());
    assertFalse(error.getMessage().contains("private-sql"));
  }

  /** 存储故障与并发冲突一致脱敏，不能把消失的既有编号当作新目录。 */
  @Test
  @DisplayName("R34 目录消失和存储故障均失败关闭而不隐式新增")
  void R34_MissingAndFailedStorageRejects() {
    ApplicationSystemJpaRepository rows = mock(ApplicationSystemJpaRepository.class);
    JpaApplicationSystemRepositoryAdapter adapter = new JpaApplicationSystemRepositoryAdapter(rows);
    when(rows.findById(999L)).thenReturn(Optional.empty());
    BusinessException missing = assertThrows(BusinessException.class, () ->
        adapter.save(new ApplicationSystem(999L, "missing", "已消失", true)));
    assertEquals(403, missing.getStatus());
    verify(rows, never()).saveAndFlush(any());
    when(rows.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("private-database"));
    BusinessException failure = assertThrows(BusinessException.class, () ->
        adapter.save(new ApplicationSystem(null, "third", "新目录", true)));
    assertEquals(409, failure.getStatus());
    assertEquals("DATA_CONFLICT", failure.getCode());
    assertFalse(failure.getMessage().contains("private-database"));
  }

  /** 缺省搜索不变成字符串，实际搜索才清理首尾空白。 */
  @Test
  @DisplayName("R34 应用可选搜索保留null并规范化非空值")
  void R34_OptionalSearchPreservesNull() {
    SystemQueryDTO query = new SystemQueryDTO();
    query.setQ(null);
    assertNull(query.getQ());
    query.setQ("  app  ");
    assertEquals("app", query.getQ());
  }
}
