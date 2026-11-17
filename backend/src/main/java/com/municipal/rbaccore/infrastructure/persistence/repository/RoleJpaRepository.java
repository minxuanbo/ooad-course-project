package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 角色查询在数据库执行筛选及分页，编码判重沿实际列排序规则。 */
public interface RoleJpaRepository
    extends JpaRepository<RoleEntity, Long>, JpaSpecificationExecutor<RoleEntity> {
  /** 使用和唯一索引相同的列比较语义，避免Java大小写规则与MySQL分歧。 */
  boolean existsByCode(String code);
}
