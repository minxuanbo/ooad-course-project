package com.municipal.rbaccore.api.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.municipal.rbaccore.service.RoleModels;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** 角色PATCH仅允许名称及启用状态，缺省保留、显式null拒绝。 */
@Getter
@Setter
@CopyTo(RoleModels.PatchCommand.class)
public class RolePatchDTO {
  @Size(min = 1, max = 100)
  @Setter(AccessLevel.NONE)
  private String name;

  @JsonSetter(nulls = Nulls.FAIL)
  private Boolean enabled;

  /** Jackson先拒绝显式null，再按归一后的名称长度校验。 */
  @JsonSetter(nulls = Nulls.FAIL)
  public void setName(String name) {
    this.name = RoleText.strip(name);
  }

  /** 至少包含一项可更新字段；校验结果不是可接受的JSON属性。 */
  @JsonIgnore
  @AssertTrue(message = "至少提供一个可更新字段")
  public boolean isNonEmptyPatch() {
    return name != null || enabled != null;
  }
}
