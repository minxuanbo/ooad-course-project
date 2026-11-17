package com.municipal.rbaccore.api.department.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.port.department.RenameDepartmentCommand;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** 部门改名禁止携带父级、类型等未支持字段。 */
@Getter
@Setter
@CopyTo(RenameDepartmentCommand.class)
public class DepartmentRenameDTO {
  @NotBlank
  @Size(max = 100)
  private String name;

  /** 输入先去首尾空白，再执行长度验证及课程映射。 */
  public void setName(String name) {
    this.name = name == null ? null : name.trim();
  }
}
