package com.municipal.rbaccore.api.resource.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import com.municipal.rbaccore.service.port.resource.ResourcePermissionResult;
import lombok.Getter;
import lombok.Setter;

/** 单节点PATCH返回平面字段，树查询再组合children。 */
@Getter
@Setter
@CopyFrom(ResourcePermissionResult.class)
public class ResourcePermissionVO {
  private Long id;
  private Long systemId;
  private Long parentId;
  private PermissionType type;
  private String code;
  private String name;
  private Boolean enabled;
}
