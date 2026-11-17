package com.municipal.rbaccore.api.resource.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.resource.ApiPageResult;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 有增长可能的API目录保持分页响应。 */
@Getter
@Setter
@CopyFrom(ApiPageResult.class)
public class ApiPageVO {
  @CopyFrom.Exclude(ApiPageResult.class)
  private List<ApiVO> items;

  private long total;
  private int page;
  private int size;
}
