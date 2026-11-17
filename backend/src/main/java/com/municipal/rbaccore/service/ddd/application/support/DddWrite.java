package com.municipal.rbaccore.service.ddd.application.support;

import com.municipal.rbaccore.service.ddd.domain.model.DomainRuleViolation;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.security.CurrentIdentity;
import com.municipal.rbaccore.service.port.security.MaintenanceProtection;
import com.municipal.rbaccore.service.port.security.ManagementAccessUseCase;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 在调用方事务内统一根锁、锁后鉴权、领域更新以及保底检查的顺序。 */
@Component
@RequiredArgsConstructor
public class DddWrite {
  private final MaintenanceProtection maintenance;
  private final DddRequestAccess requests;
  private final ManagementAccessUseCase global;

  /** 局部写入由具体用例继续核查范围和目标等级。 */
  public <T> T scoped(String header, Function<CurrentIdentity, T> change) {
    return execute(() -> change.apply(requests.require(header)));
  }

  /** 全局配置变更还需要本人直接归属可信共同根。 */
  public <T> T global(String header, Supplier<T> change) {
    return execute(
        () -> {
          global.require(header);
          return change.get();
        });
  }

  /** 规则异常成为既有语义，事务由抛出的业务异常整体回滚。 */
  private <T> T execute(Supplier<T> change) {
    maintenance.lock();
    try {
      T result = change.get();
      maintenance.retainOne();
      return result;
    } catch (DomainRuleViolation violation) {
      throw switch (violation.rule()) {
        case ROLE_NOT_ASSIGNABLE -> BusinessException.scopeDenied();
        case INVALID_CHILD ->
            BusinessException.bad("DEPARTMENT_TYPE_INVALID", violation.getMessage());
        case DEPARTMENT_ROOT_REQUIRED, DEPARTMENT_NOT_EMPTY, DEPARTMENT_NAME_CONFLICT ->
            new BusinessException(409, violation.rule().name(), violation.getMessage());
        default -> BusinessException.bad("INVALID_ARGUMENT", violation.getMessage());
      };
    }
  }
}
