package com.municipal.rbaccore.api.user.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.user.ReplaceUserRolesCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.HashSet;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 人员角色完整集合必须显式传入，空数组表示撤销全部关联。 */
@Getter
@Setter
@CopyTo(ReplaceUserRolesCommand.class)
public class UserRolesDTO {
  @NotNull private List<@NotNull @Positive @Max(ApiLimits.MAX_ID) Long> ids;

  /** 不静默去重，避免前端错误被当作一次成功的整体替换。 */
  @JsonIgnore
  @AssertTrue(message = "角色ID不能重复")
  public boolean isUniqueIds() {
    return ids == null || new HashSet<>(ids).size() == ids.size();
  }
}
