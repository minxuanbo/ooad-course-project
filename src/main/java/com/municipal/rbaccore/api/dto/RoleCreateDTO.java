package com.municipal.rbaccore.api.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.municipal.rbaccore.service.RoleModels;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** 新建角色只接收编码和名称，首尾空白在请求边界归一后校验。 */
@Getter
@Setter
@CopyTo(RoleModels.CreateCommand.class)
public class RoleCreateDTO {
  @NotBlank
  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String code;

  @NotBlank
  @Size(max = 100)
  @Setter(AccessLevel.NONE)
  private String name;

  /** 归一后的编码同时用于数据库判重和保存，不另做大小写转换。 */
  public void setCode(String code) {
    this.code = code == null ? null : code.trim();
  }

  /** 角色名称去首尾空白，不接受空白名称。 */
  public void setName(String name) {
    this.name = name == null ? null : name.trim();
  }
}
