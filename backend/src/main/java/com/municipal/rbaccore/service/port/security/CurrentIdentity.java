package com.municipal.rbaccore.service.port.security;

/** 每次从有效账号重新读取的可信身份，不接受客户端userId或管理员标记。 */
public record CurrentIdentity(Long id, String username, Long departmentId) {}
