package com.municipal.rbaccore.service.port.auth;

import com.municipal.rbaccore.service.port.resource.PermissionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 本人实际有效权限，不扩展父级导航的授权含义。 */
@Getter
@Setter
@NoArgsConstructor
public class EffectivePermissionResult {
  private Long id;
  private Long systemId;
  private Long parentId;
  private PermissionType type;
  private String code;
  private String name;
}
