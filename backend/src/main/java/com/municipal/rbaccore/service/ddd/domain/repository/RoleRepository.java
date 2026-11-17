package com.municipal.rbaccore.service.ddd.domain.repository;

import com.municipal.rbaccore.service.ddd.domain.model.DomainPage;
import com.municipal.rbaccore.service.ddd.domain.model.Role;
import com.municipal.rbaccore.service.ddd.domain.model.SearchCriteria;
import java.util.Optional;
import java.util.Set;

/** 共享角色仓储保存定义与直接关联，不在此判断授予范围。 */
public interface RoleRepository {
  /** 按ID读取一个角色及完整直接权限。 */
  Optional<Role> find(Role.Key key);

  /** 解析所有存在的目标角色。 */
  Set<Role> select(Role.Selection selection);

  /** 列表仅装配当前页的角色摘要。 */
  DomainPage<Role> search(SearchCriteria.Roles criteria);

  /** 保存角色的全部允许字段及直接关联。 */
  Role save(Role role);
}
