package com.municipal.rbaccore.api.audit.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.audit.LogEntryResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 不返回archivedAt、archivedBy、请求体或登录输入。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(LogEntryResult.class)
public class LogEntryVO {
  private Long id;

  @CopyFrom.Exclude(LogEntryResult.class)
  private String occurredAt;

  private Long userId;
  private String username;
  private String appCode;
  private String permissionCode;
  private Boolean allowed;
  private String requestId;
  private String reason;
  private String eventType;
  private String httpMethod;
  private String requestPath;
  private int httpStatus;
}
