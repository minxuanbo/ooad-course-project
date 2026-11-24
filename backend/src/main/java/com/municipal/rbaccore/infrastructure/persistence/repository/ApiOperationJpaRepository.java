package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApiOperationEntity;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

/** 接入操作技术目录，与角色权限及会话分开。 */
public interface ApiOperationJpaRepository
    extends JpaRepository<ApiOperationEntity, ApiOperationEntity.Key> {
  /** 操作编号的唯一性限定在当前应用。 */
  Optional<ApiOperationEntity> findBySystemCodeAndIdOperationId(String system, String operationId);

  /** 编辑快照包含该应用全部操作，顺序稳定用于并发比较。 */
  List<ApiOperationEntity> findBySystemIdOrderByIdOperationIdAsc(Long systemId);
}
