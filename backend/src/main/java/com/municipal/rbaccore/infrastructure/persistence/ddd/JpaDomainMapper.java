package com.municipal.rbaccore.infrastructure.persistence.ddd;

import com.municipal.rbaccore.infrastructure.persistence.entity.*;
import com.municipal.rbaccore.service.ddd.domain.model.*;
import java.util.Set;
import java.util.stream.Collectors;

/** 在当前事务内显式构造不可变领域值，避免JPA代理跨越领域仓储。 */
final class JpaDomainMapper {
  private JpaDomainMapper() {}

  /** 保留系统当前启用状态以便独立计算有效链。 */
  static ApplicationSystem system(ApplicationSystemEntity row) {
    return new ApplicationSystem(
        row.getId(),
        row.getCode(),
        row.getName(),
        row.isEnabled(),
        ApplicationSystem.Connection.valueOf(row.getConnectionStatus().name()));
  }

  /** 方法和模板属于同一个路由值。 */
  static ApiResource api(ApiResourceEntity row) {
    return new ApiResource(
        row.getId(),
        system(row.getSystem()),
        new ApiResource.Route(row.getHttpMethod(), row.getPathTemplate()),
        row.isEnabled());
  }

  /** 投影全部直接API，不过滤无效或跨系统边。 */
  static Permission permission(PermissionEntity row) {
    return new Permission(
        row.getId(),
        system(row.getSystem()),
        row.getParent() == null ? null : row.getParent().getId(),
        row.getCode(),
        row.getName(),
        Permission.Type.valueOf(row.getType().name()),
        row.isEnabled(),
        row.getApis().stream().map(JpaDomainMapper::api).collect(Collectors.toUnmodifiableSet()));
  }

  /** 人员分页只取角色状态和基础级别，不递归读取权限图。 */
  static Role roleSummary(RoleEntity row) {
    return new Role(
        row.getId(),
        row.getCode(),
        row.getName(),
        row.getManagementLevel(),
        row.isEnabled(),
        Set.of(),
        Role.Type.valueOf(row.getType().name()),
        row.getOwnerDepartment() == null ? null : department(row.getOwnerDepartment()),
        row.getApplication() == null ? null : system(row.getApplication()));
  }

  /** 角色写入和授权策略使用角色的完整直接关联。 */
  static Role role(RoleEntity row) {
    return roleSummary(row)
        .replacePermissions(
            row.getPermissions().stream()
                .map(JpaDomainMapper::permission)
                .collect(Collectors.toUnmodifiableSet()));
  }

  /** 保留组织身份和墓碑，在领域内判断节点可见性。 */
  static Department department(DepartmentEntity row) {
    return new Department(
        row.getId(),
        row.getCode(),
        row.getName(),
        row.getParent() == null ? null : row.getParent().getId(),
        Department.Type.valueOf(row.getType().name()),
        row.isDeleted());
  }

  /** 人员基本模型可按场景装配摘要或全部授权事实。 */
  static User user(UserEntity row, Set<Role> roles) {
    return new User(
        row.getId(),
        row.getUsername(),
        row.getPasswordHash(),
        row.getName(),
        row.getPhone(),
        row.getPosition(),
        department(row.getDepartment()),
        row.isEnabled(),
        roles);
  }

  /** 管理目标和当前页只需要角色摘要。 */
  static User person(UserEntity row) {
    return user(
        row,
        row.getRoles().stream()
            .map(JpaDomainMapper::roleSummary)
            .collect(Collectors.toUnmodifiableSet()));
  }
}
