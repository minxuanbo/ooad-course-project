package com.municipal.rbaccore.service.port.identity;

import com.municipal.rbaccore.service.port.auth.LoginCommand;

/** 老师已有统一身份协议的用例边界，不承载课程业务操作。 */
public interface ApplicationIdentityUseCase {
  /** 凭据通过且当前具备该应用有效授权后签发应用受众令牌。 */
  ApplicationLoginResult login(LoginCommand command, String system);

  /** 每次按当前账号、角色、系统和映射重新导出协议权限。 */
  ApplicationIdentityResult current(String header);

  /** 查询当前菜单或按钮权限，未知协议码返回false。 */
  boolean allowed(String header, String code);

  /** 具有本应用身份查询API权限者才能查询最小身份信息。 */
  ApplicationUserResult user(String header, Long id);
}
