package com.municipal.rbaccore.service.port.course;

/** 添加课程教师所需的最小账号投影，不含手机号、口令或管理范围。 */
public record CourseUserResult(String userId, String no, String name) {}
