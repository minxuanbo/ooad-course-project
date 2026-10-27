package com.municipal.rbac;

import java.util.List;
public interface AuditDtos {

  public record Query(Long userId,String username,String appCode,Boolean allowed,String from,String to,String event,String storage,String cursor,Integer limit){}
  public record Entry(long id,String occurredAt,Long userId,String username,String appCode,String permissionCode,boolean allowed,String requestId,String reason,String eventType,String httpMethod,String requestPath,Integer httpStatus,String archivedAt,Long archivedBy){}
  public record Page(List<Entry> items,String nextCursor,boolean hasMore,long snapshotId){}
  public record Archive(String before,Integer batchSize){}
  public record ArchiveResult(int archived,boolean hasMore,long snapshotId){}
}
