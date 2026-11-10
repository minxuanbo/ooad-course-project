package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.ApiResource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 沿完整有效关联计算真实API，不依赖角色名称或客户端权限参数。 */
public interface ApiResourceRepository extends JpaRepository<ApiResource, Long> {

  /** 权限和API均须属于同一个启用系统，多角色多权限按OR汇总去重。 */
  @Query(
      "select distinct a from User u join u.roles r join r.permissions p join p.apis a where"
          + " u.id=:userId and u.enabled=true and r.enabled=true and p.enabled=true and"
          + " a.enabled=true and p.system.id=a.system.id and a.system.enabled=true and"
          + " a.system.code=:system")
  List<ApiResource> effectiveApis(Long userId, String system);
}
