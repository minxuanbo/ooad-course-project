package com.municipal.rbaccore.service.port.audit;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 日志结果是纯值，不把JPA对象交给HTTP层。 */
@Getter
@Setter
@NoArgsConstructor
public class LogEntryResult {
  private Long id;
  private Instant occurredAt;
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
