package com.municipal.rbaccore.service.port.resource;

import lombok.Getter;
import lombok.Setter;

/** 通过API格式校验后的目录查询命令。 */
@Getter
@Setter
public class ApiQuery {
  private Long systemId;
  private String q;
  private int page;
  private int size;
}
