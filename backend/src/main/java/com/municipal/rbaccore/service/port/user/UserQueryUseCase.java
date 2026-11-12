package com.municipal.rbaccore.service.port.user;

/** 部门树及人员分页查询的共享用例。 */
public interface UserQueryUseCase {

  /** 查询当前可见范围树。 */
  java.util.List<DepartmentResult> tree(String header);

  /** 数据库先范围和筛选，再计数分页。 */
  UserPageResult list(String header, UserQuery query);
}
