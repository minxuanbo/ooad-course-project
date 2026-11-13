package com.municipal.rbaccore.infrastructure.persistence.entity;

import com.municipal.rbaccore.service.port.resource.PermissionType;
import jakarta.persistence.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.util.Assert;

/** 菜单/按钮持久化记录及关联约束，不作为DDD聚合使用。 */
@Entity
@Table(
    name = "permissions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_permission_system_code",
            columnNames = {"system_id", "code"}))
@cn.edu.xmu.clonefactory.CopyTo({
  com.municipal.rbaccore.service.port.auth.EffectivePermissionResult.class,
  com.municipal.rbaccore.service.port.resource.ResourcePermissionResult.class
})
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PermissionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "system_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_permission_system"))
  private ApplicationSystemEntity system;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_permission_parent"))
  private PermissionEntity parent;

  @OneToMany(mappedBy = "parent")
  @cn.edu.xmu.clonefactory.CopyTo.Exclude(
      com.municipal.rbaccore.service.port.resource.ResourcePermissionResult.class)
  private Set<PermissionEntity> children = new LinkedHashSet<>();

  @Column(nullable = false, length = 100)
  private String code;

  @Column(nullable = false, length = 191)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 6)
  private PermissionType type;

  @Column(nullable = false)
  @cn.edu.xmu.clonefactory.CopyTo.Exclude({
    com.municipal.rbaccore.service.port.auth.EffectivePermissionResult.class,
    com.municipal.rbaccore.service.port.resource.ResourcePermissionResult.class
  })
  @lombok.Setter
  private boolean enabled = true;

  @ManyToMany
  @JoinTable(
      name = "permission_apis",
      joinColumns =
          @JoinColumn(
              name = "permission_id",
              foreignKey = @ForeignKey(name = "fk_permission_api_permission")),
      inverseJoinColumns =
          @JoinColumn(name = "api_id", foreignKey = @ForeignKey(name = "fk_permission_api_api")))
  private Set<ApiResourceEntity> apis = new LinkedHashSet<>();

  /** 创建带必要归属和稳定编码的持久化记录，输入缺失时拒绝。 */
  public PermissionEntity(
      ApplicationSystemEntity system, String code, String name, PermissionType type) {
    Assert.notNull(system, "权限必须属于一个系统");
    Assert.notNull(type, "权限类型不能为空");
    Assert.hasText(code, "权限编码不能为空");
    Assert.hasText(name, "权限名称不能为空");
    this.system = system;
    this.code = code.trim();
    this.name = name.trim();
    this.type = type;
  }

  /** 变更直接上级前检查自指和循环，失败时保留原有树关系。 */
  public void moveTo(PermissionEntity parent) {
    if (parent != null) {
      Assert.isTrue(system.sameIdentity(parent.getSystem()), "权限父子必须属于同一系统");
      Assert.isTrue(parent.getType() == PermissionType.MENU, "按钮不能有子权限");
    }
    Set<Object> visited = new HashSet<>();
    for (PermissionEntity node = parent; node != null; node = node.getParent()) {
      Object key = node.getId() == null ? node : node.getId();
      Assert.isTrue(
          node != this && (id == null || !id.equals(node.getId())) && visited.add(key),
          "权限不能自指或形成循环");
    }
    if (this.parent != null) this.parent.removeChild(this);
    this.parent = parent;
    if (parent != null) parent.addChild(this);
  }

  /** 类型变化不能使已有子节点挂在按钮之下。 */
  public void changeType(PermissionType type) {
    Assert.notNull(type, "权限类型不能为空");
    Assert.isTrue(type != PermissionType.BUTTON || children.isEmpty(), "有子权限的菜单不能改为按钮");
    this.type = type;
  }

  /** 只建立同系统的显式API关联，失败前不改变原集合。 */
  public void mapApi(ApiResourceEntity api) {
    Assert.notNull(api, "API 不能为空");
    Assert.isTrue(system.sameIdentity(api.getSystem()), "权限只能映射同系统 API");
    apis.add(api);
  }

  /** 仅移除本条显式映射，不删除共享API记录。 */
  public void unmapApi(ApiResourceEntity api) {
    apis.remove(api);
  }

  // 维护反向集合，使同一事务内移动子节点后再改类型也能检查真实子节点。
  /** 同步维护反向集合，使当前事务中的树约束读取最新子节点。 */
  protected void addChild(PermissionEntity child) {
    children.add(child);
  }

  /** 移除旧反向集合中的引用，防止移动后的类型判断使用旧树。 */
  protected void removeChild(PermissionEntity child) {
    children.remove(child);
  }

  /** 返回只读子节点集合，调用者不能绕过树约束直接增删。 */
  public Set<PermissionEntity> getChildren() {
    return Collections.unmodifiableSet(children);
  }

  /** 返回只读API集合，修改须经过同系统映射检查。 */
  public Set<ApiResourceEntity> getApis() {
    return Collections.unmodifiableSet(apis);
  }
}
