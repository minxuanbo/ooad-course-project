package com.municipal.rbaccore.api.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.CoreApiLimits;
import com.municipal.rbaccore.service.UserAdminModels;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 调动只接受范围内单一目标部门，不携带角色或状态字段。 */
@Getter
@Setter
@CopyTo(UserAdminModels.MoveCommand.class)
public class UserDepartmentDTO {
  @NotNull
  @Positive
  @Max(CoreApiLimits.MAX_ID)
  private Long departmentId;
}
