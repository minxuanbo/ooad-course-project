package com.municipal.rbaccore.infrastructure.persistence.entity;

import com.municipal.rbaccore.service.port.role.RoleResult;
import com.municipal.rbaccore.service.port.role.RoleType;
import com.municipal.rbaccore.service.port.role.UpdateRoleCommand;
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
    uniqueConstraints =
        @UniqueConstraint(name = "uk_role_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@cn.edu.xmu.clonefactory.CopyTo({RoleResult.class})
@cn.edu.xmu.clonefactory.CopyNotNullFrom(UpdateRoleCommand.class)
public class RoleEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)

  private String code;

  @Column(nullable = false, length = 100)
  @lombok.Setter
  private String name;

  /** 固定基础角色管理级别；自定义角色为null，不能提高持有人级别。 */
  @Column(name = "management_level")
  private Integer managementLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "role_type", nullable = false, length = 24)
  private RoleType type = RoleType.GLOBAL_CUSTOM;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(
      name = "owner_department_id",
      foreignKey =
          @ForeignKey(name = "fk_role_owner_department"))
  private DepartmentEntity ownerDepartment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "application_id", foreignKey =
      @ForeignKey(name = "fk_role_application"))
  private ApplicationSystemEntity application;

  @Column(nullable = false)
  @Setter
  @cn.edu.xmu.clonefactory.CopyTo.Exclude({RoleResult.class})
  private boolean enabled = true;

  /** 同事务内批量装载角色权限，不保存跨请求授权快照。 */
  @org.hibernate.annotations.BatchSize(size = 32)
  @ManyToMany
  @JoinTable(
      name = "role_permissions",
      joinColumns =
          @JoinColumn(name = "role_id", foreignKey =
                  @ForeignKey(name = "fk_role_permission_role")),
      inverseJoinColumns =
          @JoinColumn(
              name = "permission_id",
              foreignKey =
                      @ForeignKey(name = "fk_role_permission_permission")))
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

  /** 固定级别角色由初始化显式设置；自定义及应用角色不能附带管理级别。 */
  public void setManagementLevel(Integer level) {
    this.managementLevel = level;
    this.type = level == null ? RoleType.GLOBAL_CUSTOM : RoleType.SYSTEM_BUILTIN;
    this.ownerDepartment = null;
    this.application = null;
  }

  /** 创建时绑定部门，后续HTTP维护不提供类型或归属变更入口。 */
  public void belongTo(DepartmentEntity department) {
    Assert.notNull(department, "部门角色必须指定所属部门");
    this.type = RoleType.DEPARTMENT_CUSTOM;
    this.ownerDepartment = department;
    this.application = null;
    this.managementLevel = null;
  }

  /** 通用应用预置来源来自显式接入配置，不把业务身份加入RBAC内置判断。 */
  public void presetFor(ApplicationSystemEntity system) {
    Assert.notNull(system, "应用预置角色必须指定来源应用");
    this.type = RoleType.APPLICATION_PRESET;
    this.application = system;
    this.ownerDepartment = null;
    this.managementLevel = null;
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
