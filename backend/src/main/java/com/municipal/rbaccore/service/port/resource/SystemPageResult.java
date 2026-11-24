package com.municipal.rbaccore.service.port.resource;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 应用列表使用同条件计数和分页，不把当前页条数当总数。 */
@Getter
@Setter
@AllArgsConstructor
public class SystemPageResult {
  private List<SystemResult> items;
  private long total;
  private int page;
  private int size;
}
