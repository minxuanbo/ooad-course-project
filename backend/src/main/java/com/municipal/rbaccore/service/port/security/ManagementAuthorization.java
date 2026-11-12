package com.municipal.rbaccore.service.port.security;

/** 实际API授权查询支撑，调用方不按角色名称放行。 */
public interface ManagementAuthorization {
  /** 返回当前用户同系统的实际有效API集合，不包含实体。 */
  java.util.Set<RbacCatalog.Route> routes(Long userId);

  /** 判断本系统方法和服务端模板是否被有效授权覆盖。 */
  boolean allows(Long userId, String method, String template);

  /** 判断是否存在已登记管理API以允许进入管理端。 */
  boolean canEnter(Long userId);
}
