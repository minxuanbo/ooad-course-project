package com.municipal.rbaccore.api.resource.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.port.resource.SystemQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** 应用分页使用page/size；搜索先去首尾空白，状态仅接受true/false。 */
@Getter
@Setter
@CopyTo(SystemQuery.class)
public class SystemQueryDTO {
  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String q;

  @Pattern(regexp = "true|false")
  @CopyTo.Exclude(SystemQuery.class)
  private String enabled;

  @Min(0)
  private int page;

  @Min(1)
  @Max(100)
  private int size = 20;

  /** 规范化查询文本，百分号和下划线在持久化条件中继续按文字转义。 */
  public void setQ(String q) {
    this.q = q == null ? null : q.trim();
  }
}
