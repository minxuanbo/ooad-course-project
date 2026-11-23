package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** 角色查询在数据库执行筛选及分页，编码判重沿实际列排序规则。 */
public interface RoleJpaRepository
    extends JpaRepository<RoleEntity, Long>, JpaSpecificationExecutor<RoleEntity> {
  /** 当前页连同两种来源名称一次获取，避免每个角色额外读取所属部门或应用。 */
  @Override
  @org.springframework.data.jpa.repository.EntityGraph(
      attributePaths = {"ownerDepartment", "application"})
  org.springframework.data.domain.Page<RoleEntity> findAll(
      org.springframework.data.jpa.domain.Specification<RoleEntity> specification,
      org.springframework.data.domain.Pageable pageable);

  /** 软删除部门也必须检查所属角色，包含停用定义。 */
  boolean existsByOwnerDepartmentId(Long departmentId);

  /** 一次读取角色归属占用事实，包含停用定义，不装配人员或授权集合。 */
  @org.springframework.data.jpa.repository.Query(
      "select distinct r.ownerDepartment.id from RoleEntity r where r.ownerDepartment is not null")
  java.util.Set<Long> ownerDepartmentIds();

  /** 使用和唯一索引相同的列比较语义，避免Java大小写规则与MySQL分歧。 */
  boolean existsByCode(String code);
}
