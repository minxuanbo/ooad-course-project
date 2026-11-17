package com.municipal.rbaccore.service.port.resource;

import lombok.Getter;
import lombok.Setter;

/** 权限PATCH的字段存在性单独表达，不能用null推断缺省。 */
@Getter
@Setter
public class UpdatePermissionCommand {
  private Long parentId;
  private Boolean parentIdPresent;
  private PermissionType type;
  private Boolean enabled;
}
