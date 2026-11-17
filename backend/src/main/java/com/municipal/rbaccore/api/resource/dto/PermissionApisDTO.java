package com.municipal.rbaccore.api.resource.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.resource.ReplacePermissionApisCommand;
import jakarta.validation.constraints.*;
import java.util.HashSet;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 映射整体替换必须明确传数组，重复项拒绝而不是静默去重。 */
@Getter
@Setter
@CopyTo(ReplacePermissionApisCommand.class)
public class PermissionApisDTO {
  @NotNull private List<@NotNull @Positive @Max(ApiLimits.MAX_ID) Long> ids;

  /** null由NotNull报告，非空集合中的重复ID由API直接拒绝。 */
  @AssertTrue(message = "API ID不能重复")
  public boolean isUniqueIds() {
    return ids == null || new HashSet<>(ids).size() == ids.size();
  }
}
