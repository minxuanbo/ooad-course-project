package com.municipal.rbaccore.api.identity.vo;

/** 接入应用所需的最小身份投影，不输出手机号或口令。 */
public record ApplicationUserVO(String userId, String no, String name) {}
