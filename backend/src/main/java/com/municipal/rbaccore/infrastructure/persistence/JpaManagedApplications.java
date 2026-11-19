package com.municipal.rbaccore.infrastructure.persistence;

import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.service.port.security.ManagedApplications;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 通用目录适配器只提供登记事实，不缓存系统状态或调用业务应用服务。 */
@Component
@RequiredArgsConstructor
public class JpaManagedApplications implements ManagedApplications {
  private final ApplicationSystemJpaRepository systems;

  /** 每次从当前事务读取目录，新增应用无须修改生产代码。 */
  @Override
  public Set<String> codes() {
    return systems.findAll().stream()
        .map(system -> system.getCode())
        .collect(Collectors.toUnmodifiableSet());
  }
}
