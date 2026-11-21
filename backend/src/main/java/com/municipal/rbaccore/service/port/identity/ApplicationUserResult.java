package com.municipal.rbaccore.service.port.identity;

/** 接入应用所需的最小账号投影，不含手机号、口令或管理范围。 */
public record ApplicationUserResult(String userId, String no, String name) {}
