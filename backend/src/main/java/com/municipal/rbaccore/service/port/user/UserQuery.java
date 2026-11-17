package com.municipal.rbaccore.service.port.user;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 查询命令与HTTP DTO分离，跨字段派生值由Controller转换。 */
@Getter
@Setter
@NoArgsConstructor
public class UserQuery {
  private String q;
  private Long departmentId;
  private Boolean enabled;
  private int page;
  private int size;
}
