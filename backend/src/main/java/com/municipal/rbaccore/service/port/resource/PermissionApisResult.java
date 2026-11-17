package com.municipal.rbaccore.service.port.resource;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 映射真值ids包含所有已保存项，含尚未解除的停用API。 */
@Getter
@Setter
@AllArgsConstructor
public class PermissionApisResult {
  private List<Long> ids;
  private List<ApiResult> items;
}
