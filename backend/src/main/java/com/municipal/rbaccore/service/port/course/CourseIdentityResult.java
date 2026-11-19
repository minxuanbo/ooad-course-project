package com.municipal.rbaccore.service.port.course;

import java.util.List;

/** 老师协议中的主体、领域身份和当前短权限码，不作为授权缓存。 */
public record CourseIdentityResult(String subject, List<String> roles, List<String> permissions) {}
