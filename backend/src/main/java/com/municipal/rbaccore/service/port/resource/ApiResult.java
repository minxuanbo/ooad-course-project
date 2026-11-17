package com.municipal.rbaccore.service.port.resource;

import lombok.Getter;
import lombok.Setter;

/** method/path按契约命名，和实体httpMethod/pathTemplate显式转换。 */
@Getter
@Setter
public class ApiResult {
  private Long id;
  private Long systemId;
  private String method;
  private String path;
  private Boolean enabled;
}
