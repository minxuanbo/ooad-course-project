package com.municipal.rbaccore.service.port.resource;

import lombok.Getter;
import lombok.Setter;

/** 应用维护列表的数据库分页条件，与下拉目录契约分开。 */
@Getter
@Setter
public class SystemQuery {
  private String q;
  private Boolean enabled;
  private int page;
  private int size = 20;
}
