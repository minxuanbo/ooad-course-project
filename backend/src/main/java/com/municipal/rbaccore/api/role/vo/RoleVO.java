package com.municipal.rbaccore.api.role.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.role.RoleResult;
import com.municipal.rbaccore.service.port.role.RoleType;
import lombok.Getter;
import lombok.Setter;

/** 角色定义与来源归属摘要，不增加角色继承或管理员标记。 */
@Getter
@Setter
@CopyFrom(RoleResult.class)
public class RoleVO {
  private Long id;
  private String code;
  private String name;
  private Integer managementLevel;
  private Boolean enabled;
  private RoleType type;
  private Long ownerDepartmentId;
  private String ownerDepartmentName;
  private Long applicationId;
  private String applicationName;
}
