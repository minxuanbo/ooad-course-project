package com.municipal.rbaccore.infrastructure.persistence.repository;

import com.municipal.rbaccore.infrastructure.persistence.entity.ApiResourceEntity;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** 沿完整有效关联计算真实API，不依赖角色名称或客户端权限参数。 */
public interface ApiResourceJpaRepository
    extends JpaRepository<ApiResourceEntity, Long>, JpaSpecificationExecutor<ApiResourceEntity> {
  /** 目录范围、搜索和分页在数据库完成，输出关系在事务内加载。 */
  @Override
  @EntityGraph(attributePaths = "system")
  Page<ApiResourceEntity> findAll(
      Specification<ApiResourceEntity> specification, Pageable pageable);

  /** 权限和API均须属于同一个启用系统，多角色多权限按OR汇总去重。 */
  @Query(
      "select distinct a from UserEntity u join u.roles r join r.permissions p join p.apis a where"
          + " u.id=:userId and u.enabled=true and r.enabled=true and p.enabled=true and"
          + " a.enabled=true and p.system.id=a.system.id and a.system.enabled=true and"
          + " a.system.code=:system")
  List<ApiResourceEntity> effectiveApis(Long userId, String system);
}
