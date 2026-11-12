package com.municipal.rbaccore.service.port.role;

import lombok.Getter;
import lombok.Setter;

/** 请求校验后的分页、文字及状态条件。 */
@Getter
@Setter
public class RoleQuery {
  private String q;
  private Boolean enabled;
  private int page;
  private int size;
}
