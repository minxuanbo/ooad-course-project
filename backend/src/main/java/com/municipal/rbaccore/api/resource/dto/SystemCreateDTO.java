package com.municipal.rbaccore.api.resource.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.port.resource.CreateSystemCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 新系统只登记标识和名称，初始为待接入，不接受客户端预授权限。 */
@Getter
@Setter
@CopyTo(CreateSystemCommand.class)
public class SystemCreateDTO {
  @NotBlank
  @Size(max = 100)
  @Pattern(regexp = "[a-z0-9][a-z0-9_-]*")
  private String code;

  @NotBlank
  @Size(max = 191)
  private String name;
}
