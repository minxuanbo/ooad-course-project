package com.municipal.rbaccore.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 单一账号输入接收工号或手机号，口令不去首尾空白。 */
@Getter
@Setter
@NoArgsConstructor
public class LoginDTO {
  @NotBlank
  @Size(max = 100)
  private String account;

  @NotNull
  @Size(min = 1, max = 200)
  private String password;
}
