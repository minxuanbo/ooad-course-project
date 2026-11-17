package com.municipal.rbaccore.api.audit.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.port.audit.LogPageResult;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 保持cursor/limit快照语义，增加同筛选快照total。 */
@Getter
@Setter
@NoArgsConstructor
@CopyFrom(LogPageResult.class)
public class LogPageVO {
  @CopyFrom.Exclude(LogPageResult.class)
  private List<LogEntryVO> items;

  private String nextCursor;
  private Boolean hasMore;
  private long snapshotId;
  private long total;
}
