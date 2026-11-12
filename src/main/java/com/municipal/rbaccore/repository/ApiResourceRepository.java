package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.ApiResource;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** 沿完整有效关联计算真实API，不依赖角色名称或客户端权限参数。 */
public interface ApiResourceRepository
    extends JpaRepository<ApiResource, Long>, JpaSpecificationExecutor<ApiResource> {
  /** 目录范围、搜索和分页在数据库完成，输出关系在事务内加载。 */
  @Override
  @EntityGraph(attributePaths = "system")
  Page<ApiResource> findAll(Specification<ApiResource> specification, Pageable pageable);

  /** 权限和API均须属于同一个启用系统，多角色多权限按OR汇总去重。 */
  @Query(
      "select distinct a from User u join u.roles r join r.permissions p join p.apis a where"
          + " u.id=:userId and u.enabled=true and r.enabled=true and p.enabled=true and"
          + " a.enabled=true and p.system.id=a.system.id and a.system.enabled=true and"
          + " a.system.code=:system")
  List<ApiResource> effectiveApis(Long userId, String system);

  /** 单个角色对外贡献的有效API，供候选角色与本人有效权限做子集比较。 */
  @Query(
      "select distinct a from Role r join r.permissions p join p.apis a where"
          + " r.id=:roleId and r.enabled=true and p.enabled=true and"
          + " a.enabled=true and p.system.id=a.system.id and a.system.enabled=true and"
          + " a.system.code=:system")
  List<ApiResource> roleEffectiveApis(Long roleId, String system);
}
