package com.municipal.rbaccore.service.port.course;

import com.municipal.rbaccore.service.port.auth.LoginCommand;

/** 老师已有统一身份协议的用例边界，不承载课程业务操作。 */
public interface CourseIdentityUseCase {
  /** 凭据通过且当前具备显式教师准入后签发课程受众令牌。 */
  CourseLoginResult login(LoginCommand command);

  /** 每次按当前账号、角色、系统和映射重新导出协议权限。 */
  CourseIdentityResult current(String header);

  /** 查询当前菜单或按钮权限，未知协议码返回false。 */
  boolean allowed(String header, String code);

  /** 具有课程UL权限者才能查询老师添加教师所需的最小身份信息。 */
  CourseUserResult user(String header, Long id);
}
