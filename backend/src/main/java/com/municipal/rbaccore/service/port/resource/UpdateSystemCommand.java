package com.municipal.rbaccore.service.port.resource;

import lombok.Getter;
import lombok.Setter;

/** 应用生命周期修改，省略字段保持原状态，稳定编码不可改。 */
@Getter
@Setter
public class UpdateSystemCommand {
  private String name;
  private Boolean enabled;
  private ConnectionStatus connectionStatus;
}
