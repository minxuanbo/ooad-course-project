package com.municipal.rbaccore.service.port.auth;

/** 共享认证用例，结构化和DDD使用同一输入输出及应用受众协议。 */
public interface AuthenticationUseCase {

  /** 双入口定位唯一账号并校验管理准入。 */
  LoginResult login(LoginCommand command);

  /** 只验证唯一账号、口令和状态，应用准入由各自用例检查后才能签发身份。 */
  com.municipal.rbaccore.service.port.security.CurrentIdentity verify(LoginCommand command);

  /** 按明确应用受众重新验证账号状态。 */
  com.municipal.rbaccore.service.port.security.CurrentIdentity requireForSystem(
      String header, String system);

  /** 读取当前可信身份，状态每次重新确认。 */
  com.municipal.rbaccore.service.port.security.CurrentIdentity require(String header);

  /** 查询本人安全信息。 */
  IdentityResult me(String header);

  /** 查询本人当前有效权限。 */
  java.util.List<EffectivePermissionResult> myPermissions(String header);

  /** 幂等撤销所带身份。 */
  void logout(String header);
}
