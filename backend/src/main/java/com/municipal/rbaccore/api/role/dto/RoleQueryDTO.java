package com.municipal.rbaccore.api.role.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.role.support.RoleText;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.role.RoleQuery;
import com.municipal.rbaccore.service.port.role.RoleType;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** 角色分页及搜索格式在HTTP边界校验，状态不接受宽松布尔别名。 */
@Getter
@Setter
@CopyTo(RoleQuery.class)
public class RoleQueryDTO {
  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String q;

  @Pattern(regexp = "true|false")
  @CopyTo.Exclude(RoleQuery.class)
  private String enabled;

  private RoleType type;

  @Positive
  @Max(ApiLimits.MAX_ID)
  private Long ownerDepartmentId;

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
