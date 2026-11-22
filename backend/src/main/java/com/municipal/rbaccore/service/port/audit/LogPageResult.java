package com.municipal.rbaccore.service.port.audit;

import com.municipal.rbaccore.service.port.resource.SystemResult;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 日志快照页附带登记系统筛选目录；total不随游标翻页缩小。 */
@Getter
@Setter
@AllArgsConstructor
public class LogPageResult {
  private List<LogEntryResult> items;
  private String nextCursor;
  private Boolean hasMore;
  private long snapshotId;
  private long total;
  private List<SystemResult> systems;
}
