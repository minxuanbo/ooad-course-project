package com.municipal.rbaccore.service.port.resource;

import lombok.Getter;
import lombok.Setter;

/** API启停命令，类型转换后的状态由受控业务入口更新。 */
@Getter
@Setter
public class UpdateApiCommand {
  private Boolean enabled;
}
