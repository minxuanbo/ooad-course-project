package com.municipal.rbaccore.api.course.vo;

/** 老师身份客户端要求的原始登录响应，不包含权限快照。 */
public record CourseLoginVO(String accessToken, String tokenType, long expiresIn) {}
