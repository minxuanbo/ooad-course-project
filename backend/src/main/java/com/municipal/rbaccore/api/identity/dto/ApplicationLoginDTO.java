package com.municipal.rbaccore.api.identity.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.port.auth.LoginCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 接收单个应用的通用身份登录请求，不接受客户端自报角色。 */
@Getter
@Setter
@NoArgsConstructor
@CopyTo(LoginCommand.class)
public class ApplicationLoginDTO {
  @NotBlank
  @Size(max = 100)
  private String username;

  @NotNull
  @Size(min = 1, max = 200)
  private String password;

  @NotNull
  @Size(min = 1, max = 1)
  private List<
      @NotBlank
      @Size(max = 100)
      String> systems;
}
