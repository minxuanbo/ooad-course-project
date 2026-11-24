package com.municipal.rbaccore.api.resource.dto;

import com.municipal.rbaccore.api.shared.ApiLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 权限树允许选择已登记应用目录，实际API及范围判定由Service完成。 */
@Getter
@Setter
public class ResourceSystemQueryDTO {
  @Positive
  @Max(ApiLimits.MAX_ID)
  private Long systemId;
}
