package com.municipal.rbaccore.api.resource.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.resource.PermissionApisResult;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 完整已保存映射不受候选列表的搜索或分页截断。 */
@Getter
@Setter
@CopyFrom(PermissionApisResult.class)
public class PermissionApisVO {
  private List<Long> ids;

  @CopyFrom.Exclude(PermissionApisResult.class)
  private List<ApiVO> items;
}
