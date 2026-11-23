package com.municipal.rbaccore.service.port.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 查询命令与HTTP DTO分离，跨字段派生值由Controller转换。 */
@Getter
@Setter
@NoArgsConstructor
public class UserQuery {
  public static final String DIRECT = "DIRECT";
  public static final String SUBTREE = "SUBTREE";
  private String q;
  private Long departmentId;
  private String departmentScope = SUBTREE;
  private Long roleId;
  private Boolean enabled;
  private int page;
  private int size;
}
