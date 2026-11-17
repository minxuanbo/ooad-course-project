package com.municipal.rbaccore.service.ddd.domain.model;

/** API身份由系统、HTTP方法及服务端模板组成，写入只改变启用状态。 */
public record ApiResource(Long id, ApplicationSystem system, Route route, boolean enabled) {
  /** HTTP方法和服务端模板共同识别授权对象。 */
  public record Route(String method, String path) {}

  /** API更新保留身份和路由。 */
  public ApiResource changeAvailability(Boolean value) {
    return value == null ? this : new ApiResource(id, system, route, value);
  }

  /** API记录的数据库身份。 */
  public record Key(Long value) {}
}
