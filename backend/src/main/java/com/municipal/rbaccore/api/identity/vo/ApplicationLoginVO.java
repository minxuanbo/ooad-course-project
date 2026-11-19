package com.municipal.rbaccore.api.identity.vo;

/** 老师身份客户端要求的原始登录响应，不包含权限快照。 */
public record ApplicationLoginVO(String accessToken, String tokenType, long expiresIn) {}
