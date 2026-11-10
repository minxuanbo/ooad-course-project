package com.municipal.rbaccore.api.vo;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.municipal.rbaccore.service.LogModels;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 日志HTTP响应只保留经评审的最小字段和快照分页信息。 */
public interface LogViews {
  /** 不返回archivedAt、archivedBy、请求体或登录输入。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(LogModels.EntryResult.class)
  class EntryVO {
    private Long id;

    @CopyFrom.Exclude(LogModels.EntryResult.class)
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

  /** 保持cursor/limit快照语义，增加同筛选快照total。 */
  @Getter
  @Setter
  @NoArgsConstructor
  @CopyFrom(LogModels.PageResult.class)
  class PageVO {
    @CopyFrom.Exclude(LogModels.PageResult.class)
    private List<EntryVO> items;

    private String nextCursor;
    private Boolean hasMore;
    private long snapshotId;
    private long total;
  }
}
