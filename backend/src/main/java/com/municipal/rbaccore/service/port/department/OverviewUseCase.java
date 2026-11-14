package com.municipal.rbaccore.service.port.department;

/** 总览聚合共享边界，不依赖浏览器统计或具体持久化技术。 */
public interface OverviewUseCase {
  /** 只统计本人部门及后代，停用账号仍计入人员总数。 */
  OverviewResult overview(String header);
}
