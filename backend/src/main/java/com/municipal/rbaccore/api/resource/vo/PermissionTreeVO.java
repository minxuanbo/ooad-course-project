package com.municipal.rbaccore.api.resource.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.resource.ResourcePermissionResult;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 树响应的子元素也使用VO，避免结果与实体形成反向循环。 */
@Getter
@Setter
@CopyFrom(ResourcePermissionResult.class)
public class PermissionTreeVO {
  private Long id;
  private Long systemId;
  private Long parentId;
  private PermissionType type;
  private String code;
  private String name;
  private Boolean enabled;

  @CopyFrom.Exclude(ResourcePermissionResult.class)
  private List<PermissionTreeVO> children;
}
