package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.BootstrapReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** 回执保存与批量授权共用Service事务。 */
public interface BootstrapReceiptJpaRepository
    extends JpaRepository<BootstrapReceiptEntity, String> {}
