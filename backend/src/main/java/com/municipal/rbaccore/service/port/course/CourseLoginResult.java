package com.municipal.rbaccore.service.port.course;

/** 课程已有客户端要求的不透明访问令牌表示，不持久化或记录口令。 */
public record CourseLoginResult(String accessToken, String tokenType, long expiresIn) {}
