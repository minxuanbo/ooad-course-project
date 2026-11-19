package com.municipal.rbaccore.service.port.security;

/** 不持久化的身份索引支撑，不保存业务权限快照。 */
public interface IdentityTokenStore {

  /** 创建身份索引。 */
  String issue(Long userId);

  /** 为明确应用签发独立受众的身份，不携带权限快照。 */
  String issueForSystem(Long userId, String system);

  /** 读取有效身份标识，失败关闭。 */
  Long require(String header);

  /** 读取与应用受众匹配的有效身份，其他系统的令牌不能混用。 */
  Long requireForSystem(String header, String system);

  /** 幂等撤销身份。 */
  void revoke(String header);

  /** 返回身份有效期秒数。 */
  long expiresIn();
}
