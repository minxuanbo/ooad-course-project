package com.municipal.rbaccore.api.user.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.user.DepartmentResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 客户端按parentId构树，可见根不暴露范围外父节点。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(DepartmentResult.class)
public class DepartmentVO {
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
