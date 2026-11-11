package com.municipal.rbaccore.api.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.municipal.rbaccore.api.CoreApiLimits;
import com.municipal.rbaccore.service.RoleModels;
import jakarta.validation.constraints.*;
import java.util.HashSet;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 角色权限完整集合必须显式传入，空数组表示撤销全部直接授权。 */
@Getter
@Setter
@CopyTo(RoleModels.PermissionsCommand.class)
public class RolePermissionsDTO {
  @NotNull private List<@NotNull @Positive @Max(CoreApiLimits.MAX_ID) Long> ids;

  /** 不静默去重，避免前端错误被当作一次成功的整体保存。 */
  @JsonIgnore
  @AssertTrue(message = "权限ID不能重复")
  public boolean isUniqueIds() {
    return ids == null || new HashSet<>(ids).size() == ids.size();
  }
}
