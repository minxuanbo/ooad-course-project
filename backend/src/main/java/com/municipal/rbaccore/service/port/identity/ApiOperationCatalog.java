package com.municipal.rbaccore.service.port.identity;

import java.util.Optional;
import java.util.Set;

/** operationId是接入元数据；应用是否登记启用及当前权限仍由数据库决定。 */
public interface ApiOperationCatalog {
  /** 在单个应用内定位操作，未知操作不猜测为公共接口。 */
  Optional<Operation> find(String system, String operationId);

  /** 未显式配置登录项时保留原接入语义。 */
  default Optional<String> loginPermission(String system) {
    return Optional.empty();
  }

  /** 无全局权限要求的操作只跳过全局门禁，对象权限仍由业务系统处理。 */
  record Operation(
      String method, String path, boolean requiresPermission, Set<String> permissionRoles) {
    /** 条件角色是接入元数据，不能由客户端改变。 */
    public Operation {
      permissionRoles = Set.copyOf(permissionRoles);
    }
  }
}
