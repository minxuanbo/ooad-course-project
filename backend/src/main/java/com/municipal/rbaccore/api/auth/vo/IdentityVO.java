package com.municipal.rbaccore.api.auth.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.auth.IdentityResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 工号即用户名，不返回重复工号、手机号或哈希。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(IdentityResult.class)
public class IdentityVO {
  private Long id;
  private String username;
  private String name;
  private String departmentName;
  private Integer managementLevel;
  private Boolean globalScope;
  private java.util.List<com.municipal.rbaccore.service.port.security.RbacCatalog.Route> routes;
  private Long departmentId;
  private boolean enabled;
}
