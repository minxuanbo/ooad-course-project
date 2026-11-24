package com.municipal.rbaccore.api.resource.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import com.municipal.rbaccore.service.port.resource.UpdateSystemCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 名称、启停和接入状态独立修改，空修改及无效状态由API拒绝。 */
@Getter
@Setter
@CopyTo(UpdateSystemCommand.class)
public class SystemPatchDTO {
  @Size(max = 191)
  @Pattern(regexp = "(?s).*\\S.*")
  @JsonSetter(nulls = Nulls.FAIL)
  private String name;

  @JsonSetter(nulls = Nulls.FAIL)
  private Boolean enabled;
  @JsonSetter(nulls = Nulls.FAIL)
  private ConnectionStatus connectionStatus;

  /** 至少指定一项可修改内容，不把空对象当成成功写入。 */
  @AssertTrue(message = "至少提供一项应用修改")
  @com.fasterxml.jackson.annotation.JsonIgnore
  public boolean isChangePresent() {
    return name != null || enabled != null || connectionStatus != null;
  }
}
