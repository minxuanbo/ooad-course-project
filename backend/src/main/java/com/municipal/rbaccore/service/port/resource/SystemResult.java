package com.municipal.rbaccore.service.port.resource;

import lombok.Getter;
import lombok.Setter;

/** 当前系统的安全目录表示。 */
@Getter
@Setter
public class SystemResult {
  private Long id;
  private String code;
  private String name;
  private Boolean enabled;
}
