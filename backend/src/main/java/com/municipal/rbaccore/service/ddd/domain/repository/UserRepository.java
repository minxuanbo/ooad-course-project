package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.DomainPage;
import com.municipal.rbaccore.service.ddd.domain.model.OrganizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.SearchCriteria;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import java.util.List;
import java.util.Optional;

/** 人员聚合仓储，登录匹配保留候选而非返回已经判断的身份。 */
public interface UserRepository {
  /** 读取人员完整角色，用于管理保护和角色替换。 */
  Optional<User> find(User.Key key);

  /** 同时按工号和手机号返回实际命中的账号。 */
  List<User> match(User.LoginIdentity identity);

  /** 保存变更后的人员状态和直接角色集合。 */
  User save(User user);

  /** 按范围及筛选分页读取账号和当前角色摘要。 */
  DomainPage<User> search(SearchCriteria.Users criteria);

  /** 聚合范围内全部账号、启用账号以及每个直属部门的人数。 */
  OrganizationFacts.Statistics statistics(OrganizationFacts.Scope scope);
}
