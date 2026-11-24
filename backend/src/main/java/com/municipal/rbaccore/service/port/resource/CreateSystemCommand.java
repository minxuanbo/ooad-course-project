package com.municipal.rbaccore.service.port.resource;

import lombok.Getter;
import lombok.Setter;

/** 登记应用所需的稳定编码及展示名称，不携带业务身份或授权快照。 */
@Getter
@Setter
public class CreateSystemCommand {
  private String code;
  private String name;
}
