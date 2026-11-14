package com.municipal.rbaccore.service.port.department;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 改名用例只接受名称，不接受父级和组织类型。 */
@Getter
@Setter
@NoArgsConstructor
public class RenameDepartmentCommand {
  private String name;
}
