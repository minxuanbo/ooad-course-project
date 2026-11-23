package com.municipal.rbaccore.infrastructure.persistence.ddd;

import static org.junit.jupiter.api.Assertions.*;

import com.municipal.rbaccore.service.port.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** R19单元测试只注入适配器保存回调的故障；真实数据库回滚由MySQL契约另行验证。 */
class R19WriteErrorTranslationTest {
  /** 旧接口要求所有保存冲突为409，其余接口区分未知故障500；响应不能带SQL。 */
  @Test
  @DisplayName("R19 JPA写边界按操作契约翻译未知数据库故障且隐藏内部消息")
  void R19_unknownDatabaseFailureUsesDeclaredContract() {
    for (boolean allConflicts : new boolean[] {true, false}) {
      BusinessException failure = assertThrows(BusinessException.class,
          () -> JpaWriteErrors.persist("权限", allConflicts,
              () -> { throw new DataAccessResourceFailureException("secret-sql-and-password"); }));
      assertEquals(allConflicts ? 409 : 500, failure.getStatus());
      assertEquals(allConflicts ? "DATA_CONFLICT" : "INTERNAL_ERROR", failure.getCode());
      assertFalse(failure.getMessage().contains("secret"));
    }
  }
}
