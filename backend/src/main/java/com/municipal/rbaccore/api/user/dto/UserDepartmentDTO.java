package com.municipal.rbaccore.api.user.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.user.TransferUserCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 调动只接受范围内单一目标部门，不携带角色或状态字段。 */
@Getter
@Setter
@CopyTo(TransferUserCommand.class)
public class UserDepartmentDTO {
  @NotNull
  @Positive
  @Max(ApiLimits.MAX_ID)
  private Long departmentId;
}
