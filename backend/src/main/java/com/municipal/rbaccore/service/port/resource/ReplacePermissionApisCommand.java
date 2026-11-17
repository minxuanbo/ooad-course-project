package com.municipal.rbaccore.service.port.resource;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 全组映射替换，不包含前端权限或可信身份字段。 */
@Getter
@Setter
public class ReplacePermissionApisCommand {
  private List<Long> ids;
}
