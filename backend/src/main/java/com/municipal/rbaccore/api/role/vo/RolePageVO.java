package com.municipal.rbaccore.api.role.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.role.RolePageResult;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 分页元信息由课程映射器复制，元素类型显式转为VO。 */
@Getter
@Setter
@CopyFrom(RolePageResult.class)
public class RolePageVO {
  @CopyFrom.Exclude(RolePageResult.class)
  private List<RoleVO> items;

  private long total;
  private int page;
  private int size;
}
