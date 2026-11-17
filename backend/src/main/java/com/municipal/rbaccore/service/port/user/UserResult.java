package com.municipal.rbaccore.service.port.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员列表包含只读职位和角色摘要，不包含手机号、口令哈希或完整原始资料。 */
@Getter
@Setter
@NoArgsConstructor
public class UserResult {
  private Long id;
  private String username;
  private String name;
  private String position;
  private Boolean writable;
  private java.util.List<com.municipal.rbaccore.service.port.role.RoleResult> roles;
  private Long departmentId;
  private String departmentName;
  private Boolean enabled;
}
