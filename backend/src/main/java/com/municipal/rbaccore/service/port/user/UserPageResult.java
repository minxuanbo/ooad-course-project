package com.municipal.rbaccore.service.port.user;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 分页总数来自与查询一致的数据库谓词。 */
@Getter
@Setter
@AllArgsConstructor
public class UserPageResult {
  private List<UserResult> items;
  private long total;
  private int page;
  private int size;
}
