package com.municipal.rbaccore.api.user.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.user.UserQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 人员查询参数的格式、范围和分页上限在API边界验证。 */
@Getter
@Setter
@NoArgsConstructor
@CopyTo(UserQuery.class)
public class UserQueryDTO {
  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String q = "";

  @Positive
  @Max(ApiLimits.MAX_ID)
  private Long departmentId;

  @Pattern(regexp = "true|false")
  @CopyTo.Exclude(UserQuery.class)
  private String enabled;

  @Min(0)
  private int page;

  @Min(1)
  @Max(100)
  private int size = 20;

  /** 搜索词先去首尾空白，再由请求校验器检查有效长度。 */
  public void setQ(String q) {
    this.q = q == null ? null : q.trim();
  }
}
