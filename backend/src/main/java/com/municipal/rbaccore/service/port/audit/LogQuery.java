package com.municipal.rbaccore.service.port.audit;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** API边界验证后的查询参数，UTC时间转换在Controller完成。 */
@Getter
@Setter
@NoArgsConstructor
public class LogQuery {
  private Long userId;
  private String username;
  private String appCode;
  private Boolean allowed;
  private Instant from;
  private Instant to;
  private String event;
  private String cursor;
  private int limit;
}
