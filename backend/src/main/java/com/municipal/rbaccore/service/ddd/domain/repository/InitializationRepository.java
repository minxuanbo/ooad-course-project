package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.BootstrapPlan;
import com.municipal.rbaccore.service.ddd.domain.model.ImportBatch;
import com.municipal.rbaccore.service.ddd.domain.model.InitializationState;

/** 提供原始来源与数据库事实，持久化已经核实的批量计划。 */
public interface InitializationRepository {
  /** 核对快照，包含墓碑及所有来源目标。 */
  InitializationState inspect();

  /** 保存组织、人员和来源的完整新建批次。 */
  ImportBatch.Receipt save(ImportBatch batch);

  /** 读取授权目录是否为空及可能存在的初始化回执摘要。 */
  BootstrapPlan.Catalog catalog();

  /** 保存系统、角色、显式关联及授权回执。 */
  void publish(BootstrapPlan.Publication publication);
}
