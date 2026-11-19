package com.municipal.rbaccore.api.course.vo;

import java.util.List;

/** 课程客户端当前身份，只返回实时计算后的协议短码。 */
public record CourseIdentityVO(String subject, List<String> roles, List<String> permissions) {}
