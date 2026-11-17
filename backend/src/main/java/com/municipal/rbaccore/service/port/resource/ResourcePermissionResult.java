package com.municipal.rbaccore.service.port.resource;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 树的父引用与children为展示投影，不携带JPA关联。 */
@Getter
@Setter
public class ResourcePermissionResult {
  private Long id;
  private Long systemId;
  private Long parentId;
  private PermissionType type;
  private String code;
  private String name;
  private Boolean enabled;
  private List<ResourcePermissionResult> children;
}
