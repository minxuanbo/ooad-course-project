package com.municipal.rbaccore.api.resource.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.resource.SystemResult;
import lombok.Getter;
import lombok.Setter;

/** 仅当前RBAC系统，不暴露未评审的其他系统目录。 */
@Getter
@Setter
@CopyFrom(SystemResult.class)
public class SystemVO {
  private Long id;
  private String code;
  private String name;
  private Boolean enabled;
}
