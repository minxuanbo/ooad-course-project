package com.municipal.rbaccore.service.port.audit;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** total始终是同一快照和筛选条件的总数，不随游标翻页缩小。 */
@Getter
@Setter
@AllArgsConstructor
public class LogPageResult {
  private List<LogEntryResult> items;
  private String nextCursor;
  private Boolean hasMore;
  private long snapshotId;
  private long total;
}
