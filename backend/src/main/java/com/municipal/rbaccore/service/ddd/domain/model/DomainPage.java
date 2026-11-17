package com.municipal.rbaccore.service.ddd.domain.model;

import java.util.List;

/** 表示同一读取快照的领域对象分页，先按条件过滤再分页。 */
public record DomainPage<T>(int page, int size, long total, List<T> items) {
  /** 结果保持只读，避免应用层改动仓储返回的集合。 */
  public DomainPage {
    items = List.copyOf(items);
  }
}
