package com.municipal.rbaccore.service.port.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员列表输出不包含手机号、口令哈希或岗位原始资料。 */
@Getter
@Setter
@NoArgsConstructor
public class UserResult {
  private Long id;
  private String username;
  private String name;
  private Long departmentId;
  private String departmentName;
  private Boolean enabled;
}
