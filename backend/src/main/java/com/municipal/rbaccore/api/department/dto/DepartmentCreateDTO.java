package com.municipal.rbaccore.api.department.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.department.CreateDepartmentCommand;
import com.municipal.rbaccore.service.port.department.DepartmentType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** 新增输入仅含父级、类型和名称，去空白后的长度在API层校验。 */
@Getter
@Setter
@CopyTo(CreateDepartmentCommand.class)
public class DepartmentCreateDTO {
  @NotNull
  @Positive
  @Max(ApiLimits.MAX_ID)
  private Long parentId;

  @NotNull private DepartmentType type;

  @NotBlank
  @Size(max = 100)
  private String name;

  /** 输入先去首尾空白，再执行长度验证及课程映射。 */
  public void setName(String name) {
    this.name = name == null ? null : name.trim();
  }
}
