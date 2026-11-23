package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.entity.RoleEntity;
import com.municipal.rbaccore.infrastructure.persistence.repository.ApplicationSystemJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.PermissionJpaRepository;
import com.municipal.rbaccore.infrastructure.persistence.repository.RoleJpaRepository;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import com.municipal.rbaccore.service.ddd.domain.repository.RoleRepository;
import com.municipal.rbaccore.service.port.role.RoleType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** 在数据库分页共享角色并存储完整直接权限关联。 */
@Repository
@RequiredArgsConstructor
public class JpaRoleRepositoryAdapter implements RoleRepository {
  private final RoleJpaRepository rows;
  private final PermissionJpaRepository permissions;
  private final DepartmentJpaRepository departments;
  private final ApplicationSystemJpaRepository systems;

  /** 按编号加载完整角色，包含停用与外系统关联。 */
  @Override
  public Optional<Role> find(Role.Key key) {
    return rows.findById(key.value()).map(JpaDomainMapper::role);
  }

  /** 批量解析真实存在的选项，不替应用层忽略不存在的ID。 */
  @Override
  public Set<Role> select(Role.Selection selection) {
    return rows.findAllById(selection.ids()).stream()
        .map(JpaDomainMapper::role)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** 同一谓词提供当前页和总数。 */
  @Override
  public DomainPage<Role> search(SearchCriteria.Roles criteria) {
    String pattern = JpaDomainQueries.pattern(criteria.q());
    Specification<RoleEntity> specification =
        (root, query, builder) ->
            builder.and(
                criteria.type() == null
                    ? builder.conjunction()
                    : builder.equal(root.get("type"), RoleType.valueOf(criteria.type().name())),
                criteria.ownerDepartmentId() == null
                    ? builder.conjunction()
                    : builder.equal(
                        root.get("ownerDepartment").get("id"), criteria.ownerDepartmentId()),
                criteria.visibleOwners() == null
                    ? builder.conjunction()
                    : builder.and(
                        builder.equal(root.get("type"), RoleType.DEPARTMENT_CUSTOM),
                        root.get("ownerDepartment").get("id").in(criteria.visibleOwners())),
                builder.or(
                    builder.like(builder.lower(root.get("name")), pattern, '\\'),
                    builder.like(builder.lower(root.get("code")), pattern, '\\')),
                criteria.enabled() == null
                    ? builder.conjunction()
                    : builder.equal(root.get("enabled"), criteria.enabled()));
    return JpaDomainQueries.read(
        rows,
        specification,
        criteria.page(),
        criteria.size(),
        page -> page.stream().map(JpaDomainMapper::roleSummary).toList());
  }

  /** 对已有连接相同的项不重复插入，约束错误在此边界翻译。 */
  @Override
  public Role save(Role value) {
    return JpaWriteErrors.persist(
        "角色",
        false,
        () -> {
          RoleEntity row =
              value.id() == null
                  ? new RoleEntity(value.code(), value.name())
                  : rows.findById(value.id()).orElseThrow();
          row.rename(value.name());
          row.setEnabled(value.enabled());
          if (value.type() == Role.Type.DEPARTMENT_CUSTOM)
            row.belongTo(departments.getReferenceById(value.ownerDepartment().id()));
          else if (value.type() == Role.Type.APPLICATION_PRESET)
            row.presetFor(systems.getReferenceById(value.application().id()));
          else row.setManagementLevel(value.managementLevel());
          Set<Long> ids =
              value.permissions().stream().map(Permission::id).collect(Collectors.toSet());
          List.copyOf(row.getPermissions()).stream()
              .filter(item -> !ids.contains(item.getId()))
              .forEach(row::revokePermission);
          permissions.findAllById(ids).forEach(row::grantPermission);
          return JpaDomainMapper.role(rows.saveAndFlush(row));
        });
  }
}
