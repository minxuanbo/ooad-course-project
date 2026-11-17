package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.ApplicationSystem;
import com.municipal.rbaccore.service.ddd.domain.model.Permission;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 权限仓储保留树及直接API关联，包括停用项目。 */
public interface PermissionRepository {
  /** 读取单个权限及直接关联。 */
  Optional<Permission> find(Permission.Key key);

  /** 读取同系统节点，域策略判断父子关系。 */
  List<Permission> tree(ApplicationSystem system);

  /** 读取明确请求的权限集合，不偷偷过滤停用或外系统数据。 */
  Set<Permission> select(Permission.Selection selection);

  /** 持久化经过领域规则验证的树节点和映射。 */
  Permission save(Permission permission);
}
