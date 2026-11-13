package com.municipal.rbaccore.api.resource.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.resource.ApiResult;
import lombok.Getter;
import lombok.Setter;

/** API只读方法模板，enabled允许必要启停。 */
@Getter
@Setter
@CopyFrom(ApiResult.class)
public class ApiVO {
  private Long id;
  private Long systemId;
  private String method;
  private String path;
  private Boolean enabled;
}
