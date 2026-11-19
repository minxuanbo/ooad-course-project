package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.entity.PermissionEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.entity.UserEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.UserJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.AuthorizationRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** 读取原始授权事实和执行已知条件的分页，实际授权仍由领域策略判定。 */
@Repository
@RequiredArgsConstructor
public class JpaAuthorizationRepositoryAdapter implements AuthorizationRepository {
  private final UserJpaRepository users;
  private final RoleJpaRepository roles;

  /** 不调用结构化的有效权限查询，映射全部直接链。 */
  @Override
  public Optional<AuthorizationFacts> facts(User.Key key) {
    return users.findById(key.value()).map(this::map);
  }

  /** 保存后限定到根直属有效人员，交给领域逐人核实独立覆盖。 */
  @Override
  public List<AuthorizationFacts> rootAccounts(Department.Key root) {
    return users.findByDepartmentIdAndEnabledTrue(root.value()).stream().map(this::map).toList();
  }

  /** 无论外系统权限是否停用，候选SQL都排除受污染角色。 */
  @Override
  public DomainPage<Role> candidates(AuthorizationFacts.AssignableRoleCriteria criteria) {
    String pattern = JpaDomainQueries.pattern(criteria.q());
    Specification<RoleEntity> filter =
        (root, query, builder) -> {
          Subquery<Long> excluded = query.subquery(Long.class);
          Join<RoleEntity, PermissionEntity> permission =
              excluded.correlate(root).join("permissions");
          Predicate outOfSubset =
              criteria.allowedPermissionIds().isEmpty()
                  ? builder.conjunction()
                  : builder.not(permission.get("id").in(criteria.allowedPermissionIds()));
          excluded
              .select(permission.get("id"))
              .where(
                  builder.or(
                      builder.not(permission.get("system").get("code").in(criteria.systemCodes())),
                      builder.and(
                          builder.isTrue(permission.get("enabled")),
                          builder.isTrue(permission.get("system").get("enabled")),
                          outOfSubset)));
          Predicate level = builder.le(root.get("managementLevel"), criteria.level());
          if (criteria.customPermitted()) {
            level = builder.or(level, builder.isNull(root.get("managementLevel")));
          }
          return builder.and(
              builder.isTrue(root.get("enabled")),
              level,
              builder.not(builder.exists(excluded)),
              builder.or(
                  builder.like(builder.lower(root.get("name")), pattern, '\\'),
                  builder.like(builder.lower(root.get("code")), pattern, '\\')));
        };
    return JpaDomainQueries.read(
        roles,
        filter,
        criteria.page(),
        criteria.size(),
        page -> page.stream().map(JpaDomainMapper::roleSummary).toList());
  }

  /** 将JPA集合快照映射为真正不可变的领域值。 */
  private AuthorizationFacts map(UserEntity row) {
    return new AuthorizationFacts(
        JpaDomainMapper.user(
            row,
            row.getRoles().stream()
                .map(JpaDomainMapper::role)
                .collect(Collectors.toUnmodifiableSet())));
  }
}
