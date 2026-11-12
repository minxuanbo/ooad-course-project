package com.municipal.rbaccore.api.user.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.role.support.RoleText;
import com.municipal.rbaccore.service.port.user.AssignableRoleQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** 候选角色搜索与分页在HTTP边界校验，不携带任何范围或授权字段。 */
@Getter
@Setter
@CopyTo(AssignableRoleQuery.class)
public class UserAssignableQueryDTO {
  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String q;

  @Min(0)
  private int page;

  @Min(1)
  @Max(100)
  private int size = 20;

  /** 先去首尾空白，再限制实际搜索词长度。 */
  public void setQ(String q) {
    this.q = RoleText.strip(q);
  }
}
