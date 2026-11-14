package com.municipal.rbaccore.api.user.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.user.UserResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员行不携带敏感字段或JPA关系。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(UserResult.class)
public class UserVO {
  private Long id;
  private String username;
  private String name;
  private String position;
  private Boolean writable;

  @CopyFrom.Exclude(UserResult.class)
  private java.util.List<com.municipal.rbaccore.api.role.vo.RoleVO> roles;

  private Long departmentId;
  private String departmentName;
  private boolean enabled;
}
