package com.municipal.rbaccore.service.port.bootstrap;

import java.util.Map;

/** 首次授权结果只包含数量和清单摘要，不包含个人名单。 */
public record BootstrapReport(
    Map<String, Long> plannedCounts,
    int createdAssignments,
    boolean alreadyInitialized,
    String manifestSha256) {}
