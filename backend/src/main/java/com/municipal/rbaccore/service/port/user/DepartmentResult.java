package com.municipal.rbaccore.service.port.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 部门导航的可见根由服务投影为null，实体父引用不变。 */
@Getter
@Setter
@NoArgsConstructor
public class DepartmentResult {
  private Long id;
  private Long parentId;
  private String name;
  private com.municipal.rbaccore.service.port.department.DepartmentType type;
  private long directUsers;
  private long totalUsers;
  private Boolean canCreate;
  private Boolean canEdit;
  private Boolean canDelete;
}
