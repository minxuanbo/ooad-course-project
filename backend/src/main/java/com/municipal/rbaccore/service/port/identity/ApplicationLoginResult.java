package com.municipal.rbaccore.service.port.identity;

/** 外部客户端要求的不透明访问令牌表示，不持久化或记录口令。 */
public record ApplicationLoginResult(String accessToken, String tokenType, long expiresIn) {}
