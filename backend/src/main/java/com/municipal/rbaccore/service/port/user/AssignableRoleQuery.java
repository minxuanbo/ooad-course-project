package com.municipal.rbaccore.service.port.user;

import lombok.Getter;
import lombok.Setter;

/** 候选角色的名称/编码搜索与分页条件。 */
@Getter
@Setter
public class AssignableRoleQuery {
  private String q;
  private int page;
  private int size;
}
