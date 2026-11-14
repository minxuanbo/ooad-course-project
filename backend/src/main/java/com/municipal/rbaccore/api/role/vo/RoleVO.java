package com.municipal.rbaccore.api.role.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.role.RoleResult;
import lombok.Getter;
import lombok.Setter;

/** 必要角色定义，不增加部门、继承或管理员标记。 */
@Getter
@Setter
@CopyFrom(RoleResult.class)
public class RoleVO {
  private Long id;
  private String code;
  private String name;
  private Integer managementLevel;
  private Boolean enabled;
}
