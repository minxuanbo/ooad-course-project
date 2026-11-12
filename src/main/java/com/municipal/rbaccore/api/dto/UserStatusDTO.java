package com.municipal.rbaccore.api.dto;

import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 停用指令只接受布尔状态，本票范围仅允许false。 */
@Getter
@Setter
public class UserStatusDTO {
  @NotNull
  @AssertFalse(message = "本票只支持停用人员")
  private Boolean enabled;
}
