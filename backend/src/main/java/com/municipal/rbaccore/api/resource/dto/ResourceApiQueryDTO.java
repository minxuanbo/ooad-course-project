package com.municipal.rbaccore.api.resource.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.resource.ApiQuery;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** API目录的分页及方法/路径搜索格式在请求边界验证。 */
@Getter
@Setter
@CopyTo(ApiQuery.class)
public class ResourceApiQueryDTO {
  @Positive
  @Max(ApiLimits.MAX_ID)
  private Long systemId;

  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String q;

  @Min(0)
  private int page;

  @Min(1)
  @Max(100)
  private int size = 20;

  /** 按契约先去首尾空格，再由Bean Validation检查有效搜索词长度。 */
  public void setQ(String q) {
    this.q = q == null ? null : q.trim();
  }
}
