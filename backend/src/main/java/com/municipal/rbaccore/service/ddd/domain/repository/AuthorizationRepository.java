package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.AuthorizationFacts;
import com.municipal.rbaccore.service.ddd.domain.model.Department;
import com.municipal.rbaccore.service.ddd.domain.model.DomainPage;
import com.municipal.rbaccore.service.ddd.domain.model.Role;
import com.municipal.rbaccore.service.ddd.domain.model.User;
import java.util.List;
import java.util.Optional;

/** 在当前事务内读取授权链与候选事实，不保存已授权的业务结论。 */
public interface AuthorizationRepository {
  /** 返回当前用户的全部角色、权限及直接API事实。 */
  Optional<AuthorizationFacts> facts(User.Key key);

  /** 按领域给出的筛选条件先计算范围、再返回候选页。 */
  DomainPage<Role> candidates(AuthorizationFacts.AssignableRoleCriteria criteria);

  /** 取可信根启用人员的事实，维护者资格由领域独立计算。 */
  List<AuthorizationFacts> rootAccounts(Department.Key root);
}
