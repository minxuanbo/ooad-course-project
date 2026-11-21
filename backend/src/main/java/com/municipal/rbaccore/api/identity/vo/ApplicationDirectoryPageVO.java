package com.municipal.rbaccore.api.identity.vo;

import java.util.List;

/** 外部目录分页响应直接返回字段，不沿用管理端data包装或零起页号。 */
public record ApplicationDirectoryPageVO<T>(List<T> items, long total, int page, int pageSize) {}
