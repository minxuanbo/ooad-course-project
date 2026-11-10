package com.municipal.rbaccore.bootstrap;

import org.springframework.data.jpa.repository.JpaRepository;

/** 回执保存与批量授权共用Service事务。 */
public interface BootstrapReceiptRepository extends JpaRepository<BootstrapReceipt, String> {}
