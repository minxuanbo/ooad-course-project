package com.municipal.rbaccore.api.resource.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.port.resource.UpdateApiCommand;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** API仅支持明确布尔启停，不接受路径、方法、系统或null更新。 */
@Getter
@Setter
@CopyTo(UpdateApiCommand.class)
public class ResourceApiPatchDTO {
  @NotNull private Boolean enabled;
}
