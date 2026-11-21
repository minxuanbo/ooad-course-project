package com.municipal.rbaccore.api.identity.vo;

/** API全局门禁查询只返回操作标识及判断结果，不泄漏所需权限码。 */
public record ApplicationApiAuthorityVO(String operationId, boolean allowed) {}
