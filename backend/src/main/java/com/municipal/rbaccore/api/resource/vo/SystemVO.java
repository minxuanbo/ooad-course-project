package com.municipal.rbaccore.api.resource.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.resource.ConnectionStatus;
import com.municipal.rbaccore.service.port.resource.SystemResult;
import lombok.Getter;
import lombok.Setter;

/** 数据库已登记应用的安全目录，不枚举离线原型的演示系统。 */
@Getter
@Setter
@CopyFrom(SystemResult.class)
public class SystemVO {
  private Long id;
  private String code;
  private String name;
  private Boolean enabled;
  private ConnectionStatus connectionStatus;
}
