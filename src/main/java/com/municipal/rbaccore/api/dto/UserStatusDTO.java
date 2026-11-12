package com.municipal.rbaccore.api.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.UserAdminModels;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 停用指令只接受布尔状态，本票范围仅允许false。 */
@Getter
@Setter
@CopyTo(UserAdminModels.DisableCommand.class)
public class UserStatusDTO {
  @NotNull private Boolean enabled;
}
