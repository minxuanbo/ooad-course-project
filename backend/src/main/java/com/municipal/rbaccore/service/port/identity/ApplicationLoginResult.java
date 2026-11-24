package com.municipal.rbaccore.service.port.identity;

/** 外部客户端通过Bearer携带HS256身份JWT，不持久化或记录口令。 */
public record ApplicationLoginResult(String accessToken, String tokenType, long expiresIn) {}
