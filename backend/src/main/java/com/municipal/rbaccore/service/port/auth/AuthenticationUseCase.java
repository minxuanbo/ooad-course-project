package com.municipal.rbaccore.service.port.auth;


/** 共享认证用例；结构化实现与后续DDD共用输入输出。 */
public interface AuthenticationUseCase {

  /** 双入口定位唯一账号并校验管理准入。 */
  LoginResult login(LoginCommand command);

  /** 读取当前可信身份，状态每次重新确认。 */
  com.municipal.rbaccore.service.port.security.CurrentIdentity require(String header);

  /** 查询本人安全信息。 */
  IdentityResult me(String header);

  /** 查询本人当前有效权限。 */
  java.util.List<EffectivePermissionResult> myPermissions(String header);

  /** 幂等撤销所带身份。 */
  void logout(String header);
}
