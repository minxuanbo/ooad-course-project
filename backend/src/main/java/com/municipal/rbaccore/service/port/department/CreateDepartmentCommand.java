package com.municipal.rbaccore.service.port.department;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 新增部门的独立用例输入，不接收编码或权限结论。 */
@Getter
@Setter
@NoArgsConstructor
public class CreateDepartmentCommand {
  private Long parentId;
  private String name;
  private DepartmentType type;
}
