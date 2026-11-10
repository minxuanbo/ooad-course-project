package com.municipal.rbaccore.api.dto;

import com.municipal.rbaccore.api.CoreApiLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 权限树仅允许选择当前RBAC系统，范围判定由Service完成。 */
@Getter
@Setter
public class ResourceSystemQueryDTO {
  @Positive
  @Max(CoreApiLimits.MAX_ID)
  private Long systemId;
}
