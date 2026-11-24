package com.municipal.rbaccore.api.role.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.api.role.support.RoleText;
import com.municipal.rbaccore.api.shared.ApiLimits;
import com.municipal.rbaccore.service.port.role.CreateRoleCommand;
import com.municipal.rbaccore.service.port.role.RoleType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** 新建全局或部门角色，校验编码、名称、类型及所属部门，首尾空白在请求边界归一。 */
@Getter
@Setter
@CopyTo(CreateRoleCommand.class)
public class RoleCreateDTO {
  @NotBlank
  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String code;

  @NotBlank
  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String name;

  @NotNull

  private RoleType type = RoleType.GLOBAL_CUSTOM;

  @Positive
  @Max(ApiLimits.MAX_ID)
  private Long ownerDepartmentId;

  /** 内置和应用预置只由独立初始化登记；部门角色必须明确所属部门。 */
  @com.fasterxml.jackson.annotation.JsonIgnore
  @AssertTrue(message = "仅可创建全局或部门自定义角色，部门角色必须指定所属部门")
  public boolean isScopeValid() {
    return type == RoleType.DEPARTMENT_CUSTOM
        ? ownerDepartmentId != null
        : type == RoleType.GLOBAL_CUSTOM && ownerDepartmentId == null;
  }

  /** 归一后的编码同时用于数据库判重和保存，不另做大小写转换。 */
  public void setCode(String code) {
    this.code = RoleText.strip(code);
  }

  /** 角色名称去首尾空白，不接受空白名称。 */
  public void setName(String name) {
    this.name = RoleText.strip(name);
  }
}
