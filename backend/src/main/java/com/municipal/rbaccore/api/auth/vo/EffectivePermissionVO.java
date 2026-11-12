package com.municipal.rbaccore.api.auth.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.auth.EffectivePermissionResult;
import com.municipal.rbaccore.service.port.resource.PermissionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 本系统有效权限，不混入Excel其他系统的条目。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(EffectivePermissionResult.class)
public class EffectivePermissionVO {
  private Long id;
  private Long systemId;
  private Long parentId;
  private PermissionType type;
  private String code;
  private String name;
}
