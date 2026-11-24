package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.ApplicationSystemSpecifications;
import com.municipal.rbaccore.infrastructure.persistence.entity.ApplicationSystemEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import com.municipal.rbaccore.service.ddd.domain.model.DomainPage;
import com.municipal.rbaccore.service.ddd.domain.model.SearchCriteria;
import com.municipal.rbaccore.service.ddd.domain.repository.ApplicationSystemRepository;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** 映射已登记应用事实，稳定RBAC编码只用于本系统锚点。 */
@Repository
@RequiredArgsConstructor
public class JpaApplicationSystemRepositoryAdapter implements ApplicationSystemRepository {
  private final ApplicationSystemJpaRepository rows;

  /** 真实数据库同时计数和分页，超大越末页不调用JPA整数偏移。 */
  @Override
  public DomainPage<ApplicationSystem> search(SearchCriteria.Systems criteria) {
    Specification<ApplicationSystemEntity> filter =
        ApplicationSystemSpecifications.matching(criteria.q(), criteria.enabled());
    long total = rows.count(filter);
    java.util.List<ApplicationSystem> items =
        (long) criteria.page() * criteria.size() >= total
            ? java.util.List.of()
            : rows
                .findAll(filter, PageRequest.of(criteria.page(), criteria.size(), Sort.by("id")))
                .getContent()
                .stream()
                .map(JpaDomainMapper::system)
                .toList();
    return new DomainPage<>(criteria.page(), criteria.size(), total, items);
  }

  /** 唯一性在实际保存边界兜底，未找到既有编号时不改成隐式新增。 */
  @Override
  public ApplicationSystem save(ApplicationSystem system) {
    ApplicationSystemEntity row =
        system.id() == null
            ? new ApplicationSystemEntity(system.code(), system.name())
            : rows.findById(system.id()).orElseThrow(BusinessException::scopeDenied);
    row.setName(system.name());
    row.setEnabled(system.enabled());
    row.setConnectionStatus(ConnectionStatus.valueOf(system.connectionStatus().name()));
    return JpaDomainMapper.system(JpaWriteErrors.persist("应用", true, () -> saveRow(row)));
  }

  /** 唯一编码冲突单独标识，其余数据库故障交统一写边界翻译。 */
  private ApplicationSystemEntity saveRow(ApplicationSystemEntity row) {
    try {
      return rows.saveAndFlush(row);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(409, "APPLICATION_CODE_CONFLICT", "应用编码已经登记");
    }
  }

  /** 查询全部已登记系统并映射领域值，不调用任何应用服务。 */
  @Override
  public java.util.List<ApplicationSystem> catalog() {
    return rows.findAll(Sort.by("id")).stream().map(JpaDomainMapper::system).toList();
  }

  /** 从真实目录定位系统而非固定主键。 */
  @Override
  public Optional<ApplicationSystem> findRbac() {
    return rows.findByCode(ApplicationSystem.CODE_RBAC).map(JpaDomainMapper::system);
  }
}
