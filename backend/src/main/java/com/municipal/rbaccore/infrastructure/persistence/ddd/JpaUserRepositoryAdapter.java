package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** 人员范围谓词在分页前应用；角色摘要以当前页批量加载。 */
@Repository
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepository {
  private final UserJpaRepository rows;
  private final RoleJpaRepository roles;
  private final DepartmentJpaRepository departments;

  /** 读取账号及目标保护需要的角色摘要。 */
  @Override
  public Optional<User> find(User.Key key) {
    return rows.findById(key.value()).map(JpaDomainMapper::person);
  }

  /** 保留双入口命中的全部人员，由领域策略决定歧义。 */
  @Override
  public List<User> match(User.LoginIdentity identity) {
    return rows.findByUsernameOrPhone(identity.value(), identity.value()).stream()
        .map(row -> JpaDomainMapper.user(row, Set.of()))
        .toList();
  }

  /** 保存角色集合、归属及状态，同步约束和后续完整维护者查询。 */
  @Override
  public User save(User user) {
    return JpaWriteErrors.persist(
        "人员",
        false,
        () -> {
          UserEntity row = rows.findById(user.id()).orElseThrow();
          Set<Long> selected = user.roles().stream().map(Role::id).collect(Collectors.toSet());
          List.copyOf(row.getRoles()).stream()
              .filter(role -> !selected.contains(role.getId()))
              .forEach(row::removeRole);
          roles.findAllById(selected).forEach(row::assignRole);
          row.moveTo(departments.getReferenceById(user.department().id()));
          row.setEnabled(user.enabled());
          return JpaDomainMapper.person(rows.saveAndFlush(row));
        });
  }

  /** 先按领域给定部门集合过滤，再应用搜索、状态和分页。 */
  @Override
  public DomainPage<User> search(SearchCriteria.Users criteria) {
    if (criteria.scope().departmentIds().isEmpty()) {
      return new DomainPage<>(criteria.page(), criteria.size(), 0, List.of());
    }
    String text = JpaDomainQueries.pattern(criteria.q());
    Specification<UserEntity> specification =
        (root, query, builder) -> {
          List<Predicate> clauses = new ArrayList<>();
          clauses.add(root.get("department").get("id").in(criteria.scope().departmentIds()));
          clauses.add(
              builder.or(
                  builder.like(builder.lower(root.get("name")), text, '\\'),
                  builder.like(builder.lower(root.get("username")), text, '\\')));
          if (criteria.enabled() != null)
            clauses.add(builder.equal(root.get("enabled"), criteria.enabled()));
          return builder.and(clauses.toArray(Predicate[]::new));
        };
    return JpaDomainQueries.read(
        rows, specification, criteria.page(), criteria.size(), this::loadPageRoles);
  }

  /** 批量读取同一页的角色，再依查询窗口原有顺序映射。 */
  private List<User> loadPageRoles(List<UserEntity> page) {
    Map<Long, UserEntity> indexed =
        rows.withRoles(page.stream().map(UserEntity::getId).toList()).stream()
            .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    return page.stream().map(row -> JpaDomainMapper.person(indexed.get(row.getId()))).toList();
  }

  /** 数据库按直接归属聚合人数，不读取人员明细。 */
  @Override
  public OrganizationFacts.Statistics statistics(OrganizationFacts.Scope scope) {
    if (scope.departmentIds().isEmpty()) return new OrganizationFacts.Statistics(0, 0, List.of());
    List<Long> ids = List.copyOf(scope.departmentIds());
    List<OrganizationFacts.Count> counts =
        rows.departmentCounts(ids).stream()
            .map(row -> new OrganizationFacts.Count((Long) row[0], (Long) row[1]))
            .toList();
    return new OrganizationFacts.Statistics(
        counts.stream().mapToLong(OrganizationFacts.Count::directUsers).sum(),
        rows.countByDepartmentIdInAndEnabledTrue(ids),
        counts);
  }
}
