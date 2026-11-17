package com.municipal.rbaccore.service.port.resource;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 页码、大小和total不随当前页内容变化。 */
@Getter
@Setter
@AllArgsConstructor
public class ApiPageResult {
  private List<ApiResult> items;
  private long total;
  private int page;
  private int size;
}
