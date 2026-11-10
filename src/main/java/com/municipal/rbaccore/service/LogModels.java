package com.municipal.rbaccore.service;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.logging.AuthorizationLog;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 最小日志查询命令与返回模型，不暴露归档、导出或审计扩展。 */
public interface LogModels {
  /** API边界验证后的查询参数，UTC时间转换在Controller完成。 */
  @Getter
  @Setter
  @NoArgsConstructor
  class Search {
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

  /** 日志结果是纯值，不把JPA对象交给HTTP层。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(AuthorizationLog.class)
  class EntryResult {
    private Long id;
    private Instant occurredAt;
    private Long userId;
    private String username;
    private String appCode;
    private String permissionCode;

    @CopyFrom.Exclude(AuthorizationLog.class)
    private Boolean allowed;

    private String requestId;
    private String reason;
    private String eventType;
    private String httpMethod;
    private String requestPath;
    private int httpStatus;
  }

  /** total始终是同一快照和筛选条件的总数，不随游标翻页缩小。 */
  @Getter
  @Setter
  @AllArgsConstructor
  class PageResult {
    private List<EntryResult> items;
    private String nextCursor;
    private Boolean hasMore;
    private long snapshotId;
    private long total;
  }
}
