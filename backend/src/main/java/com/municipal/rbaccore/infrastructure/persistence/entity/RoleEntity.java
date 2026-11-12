package com.municipal.rbaccore.infrastructure.persistence.entity;


import jakarta.persistence.*;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.Assert;

/** 全局共享的角色定义，权限只通过显式关联授予，不按名称产生管理能力。 */
@Entity
@Table(
    name = "roles",
    uniqueConstraints = @UniqueConstraint(name = "uk_role_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@cn.edu.xmu.clonefactory.CopyTo({com.municipal.rbaccore.service.port.role.RoleResult.class})
@cn.edu.xmu.clonefactory.CopyNotNullFrom(
    com.municipal.rbaccore.service.port.role.UpdateRoleCommand.class)
public class RoleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String code;

  @Column(nullable = false, length = 100)
  @lombok.Setter
  private String name;

  @Column(nullable = false)
  @Setter
  @cn.edu.xmu.clonefactory.CopyTo.Exclude({
    com.municipal.rbaccore.service.port.role.RoleResult.class
  })
  private boolean enabled = true;

  @ManyToMany
  @JoinTable(
      name = "role_permissions",
      joinColumns =
          @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_role_permission_role")),
      inverseJoinColumns =
          @JoinColumn(
              name = "permission_id",
              foreignKey = @ForeignKey(name = "fk_role_permission_permission")))
  @Getter(AccessLevel.NONE)
  private Set<PermissionEntity> permissions = new LinkedHashSet<>();

  /** 创建必要定义，编码在创建后不再提供修改入口。 */
  public RoleEntity(String code, String name) {
    Assert.hasText(code, "角色编码不能为空");
    Assert.hasText(name, "角色名称不能为空");
    this.code = code.trim();
    this.name = name.trim();
  }

  /** 更名不改变权限、编码或人员管理范围。 */
  public void rename(String name) {
    Assert.hasText(name, "角色名称不能为空");
    this.name = name.trim();
  }

  /** 授予一个显式权限，菜单父子关系不隐含其他授权。 */
  public void grantPermission(PermissionEntity permission) {
    Assert.notNull(permission, "权限不能为空");
    permissions.add(permission);
  }

  /** 移除直接授权，不删除权限定义和其他角色的引用。 */
  public void revokePermission(PermissionEntity permission) {
    permissions.remove(permission);
  }

  /** 集合只读，修改必须经过Service及持久化数据约束入口和调用方事务。 */
  public Set<PermissionEntity> getPermissions() {
    return Collections.unmodifiableSet(permissions);
  }
}
