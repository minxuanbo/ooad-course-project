package com.municipal.rbaccore.api.course.vo;

/** 添加课程教师所需的最小身份投影，不输出手机号或口令。 */
public record CourseUserVO(String userId, String no, String name) {}
