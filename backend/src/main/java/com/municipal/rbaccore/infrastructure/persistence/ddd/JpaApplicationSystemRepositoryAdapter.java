package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import com.municipal.rbaccore.service.ddd.domain.repository.ApplicationSystemRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 避免铺开外部系统，仅使用当前产品的稳定系统编码。 */
@Repository
@RequiredArgsConstructor
public class JpaApplicationSystemRepositoryAdapter implements ApplicationSystemRepository {
  private final ApplicationSystemJpaRepository rows;

  /** 查询已接入系统并映射领域值，不调用任何应用服务。 */
  @Override
  public java.util.List<ApplicationSystem> catalog(java.util.Set<String> codes) {
    return rows.findByCodeInOrderByIdAsc(codes).stream().map(JpaDomainMapper::system).toList();
  }

  /** 从真实目录定位系统而非固定主键。 */
  @Override
  public Optional<ApplicationSystem> findRbac() {
    return rows.findByCode(ApplicationSystem.CODE_RBAC).map(JpaDomainMapper::system);
  }
}
