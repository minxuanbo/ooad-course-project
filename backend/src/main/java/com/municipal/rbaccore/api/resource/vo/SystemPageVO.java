package com.municipal.rbaccore.api.resource.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.resource.SystemPageResult;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 应用管理分页视图，元素类型转换由Controller显式完成。 */
@Getter
@Setter
@CopyFrom(SystemPageResult.class)
public class SystemPageVO {
  @CopyFrom.Exclude(SystemPageResult.class)
  private List<SystemVO> items;

  private long total;
  private int page;
  private int size;
}
