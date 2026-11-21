package com.municipal.rbaccore.service.port.identity;

import java.util.List;

/** 外部身份目录使用从1开始的页号，不改变RBAC管理端分页协议。 */
public record ApplicationDirectoryPage<T>(List<T> items, long total, int page, int pageSize) {
  /** 目录结果保持不可变，调用者不能修改已完成的查询快照。 */
  public ApplicationDirectoryPage {
    items = List.copyOf(items);
  }
}
