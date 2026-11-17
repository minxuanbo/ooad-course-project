package com.municipal.rbaccore.service.port.importing;

/** 不含个人明细或秘密的导入结果；账号建立不代表T05的授权初始化已经完成。 */
public record ImportReport(
    int sourceOrganizations,
    int targetOrganizations,
    int employees,
    int createdUsers,
    int existingUsers,
    Long adminId,
    String organizationSha256,
    String employeeSha256) {}
