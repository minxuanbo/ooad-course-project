package com.municipal.rbaccore.api.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.municipal.rbaccore.api.CoreApiLimits;
import com.municipal.rbaccore.model.Permission;
import com.municipal.rbaccore.service.ResourceModels;
import jakarta.validation.constraints.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** 区分parentId缺省与显式null，其他字段不能用null清空。 */
@Getter
@Setter
@CopyTo(ResourceModels.PermissionPatch.class)
public class ResourcePermissionPatchDTO {
  @Positive
  @Max(CoreApiLimits.MAX_ID)
  @Setter(AccessLevel.NONE)
  private Long parentId;

  @JsonIgnore
  @Setter(AccessLevel.NONE)
  private Boolean parentIdPresent = false;

  @JsonSetter(nulls = Nulls.FAIL)
  private Permission.Type type;

  @JsonSetter(nulls = Nulls.FAIL)
  private Boolean enabled;

  /** 只有请求实际提供字段时才变更父级，null明确表示移动为根。 */
  @JsonSetter
  public void setParentId(Long parentId) {
    this.parentId = parentId;
    this.parentIdPresent = true;
  }

  /** PATCH至少包含一个支持字段，保留缺省字段的既有值。 */
  @AssertTrue(message = "至少提供一个可更新字段")
  public boolean isNonEmptyPatch() {
    return parentIdPresent || type != null || enabled != null;
  }
}
