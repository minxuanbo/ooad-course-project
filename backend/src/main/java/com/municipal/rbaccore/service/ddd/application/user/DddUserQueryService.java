package com.municipal.rbaccore.service.ddd.application.user;

import com.municipal.rbaccore.service.ddd.application.support.*;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.model.policy.OrganizationPolicy;
import com.municipal.rbaccore.service.ddd.domain.model.policy.RoleAssignmentPolicy;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import com.municipal.rbaccore.service.port.auth.AuthenticationUseCase;
import com.municipal.rbaccore.service.port.error.BusinessException;
import com.municipal.rbaccore.service.port.user.*;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 先应用人员组织范围，再分页返回职位、状态和当前角色。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DddUserQueryService implements UserQueryUseCase {
  private final AuthenticationUseCase authentication;
  private final UserRepository users;
  private final DddScope scope;
  private final DddDepartmentView departments;

  /** 人员筛选树沿用相同可见范围和排序。 */
  @Override
  public List<DepartmentResult> tree(String header) {
    User actor = actor(header);
    return departments.build(actor, scope.facts()).tree();
  }

  /** 相同数据库快照内计算条目和总数，目标保护使用同一主体。 */
  @Override
  public UserPageResult list(String header, UserQuery query) {
    User actor = actor(header);
    OrganizationFacts facts = scope.facts();
    OrganizationFacts.Scope visible =
        scope.filter(facts, actor.department().id(), query.getDepartmentId());
    OrganizationFacts.Scope selected = UserQuery.DIRECT.equals(query.getDepartmentScope())
        ? new OrganizationFacts.Scope(Set.of(query.getDepartmentId())) : visible;
    DomainPage<User> page =
        users.search(
            new SearchCriteria.Users(
                selected, query.getQ(), query.getEnabled(), query.getRoleId(), query.getPage(), query.getSize()));
    boolean global = OrganizationPolicy.global(facts, actor.department().id());
    return new UserPageResult(
        page.items().stream()
            .map(
                user ->
                    DddResults.person(user, RoleAssignmentPolicy.canManage(actor, global, user)))
            .toList(),
        page.total(),
        page.page(),
        page.size());
  }

  /** 最新有效身份只在本次查询中复用。 */
  private User actor(String header) {
    return users
        .find(new User.Key(authentication.require(header).id()))
        .orElseThrow(BusinessException::unauthorized);
  }
}
