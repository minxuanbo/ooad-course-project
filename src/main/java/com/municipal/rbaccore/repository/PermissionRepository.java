package com.municipal.rbaccore.repository;

import com.municipal.rbaccore.model.Permission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 本人有效权限只沿角色及同系统有效API计算，不补入导航祖先。 */
public interface PermissionRepository extends JpaRepository<Permission, Long> {

  /** 每项返回权限至少关联一个同系统有效API；父菜单不隐式授予子按钮。 */
  @Query(
      "select distinct p from User u join u.roles r join r.permissions p join p.apis a where"
          + " u.id=:userId and u.enabled=true and r.enabled=true and p.enabled=true and"
          + " a.enabled=true and p.system.id=a.system.id and p.system.enabled=true and"
          + " p.system.code=:system order by p.id")
  List<Permission> effectivePermissions(Long userId, String system);
}
