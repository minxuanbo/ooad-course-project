package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.service.port.error.BusinessException;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/** 在JPA写操作处翻译异常，业务层不引用数据库异常类型。 */
final class JpaWriteErrors {
  private JpaWriteErrors() {}

  /** 普通约束和并发冲突返回409，其他数据库故障按对应既有操作契约处理。 */
  static <T> T persist(String operation, boolean allConflicts, Supplier<T> save) {
    try {
      return save.get();
    } catch (DataIntegrityViolationException | ConcurrencyFailureException exception) {
      throw new BusinessException(409, "DATA_CONFLICT", operation + "保存冲突，请刷新后重试");
    } catch (DataAccessException exception) {
      if (allConflicts) {
        throw new BusinessException(409, "DATA_CONFLICT", operation + "保存冲突，请刷新后重试");
      }
      throw new BusinessException(500, "INTERNAL_ERROR", operation + "保存失败，请重试");
    }
  }
}
