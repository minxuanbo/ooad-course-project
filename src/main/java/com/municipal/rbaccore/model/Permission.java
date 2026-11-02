package com.municipal.rbaccore.model;

import jakarta.persistence.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.util.Assert;

@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(
    name = "uk_permission_system_code", columnNames = {"system_id", "code"}))
public class Permission {
  public enum Type { MENU, BUTTON }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "system_id", nullable = false, foreignKey = @ForeignKey(name = "fk_permission_system"))
  private ApplicationSystem system;
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_permission_parent"))
  private Permission parent;
  @OneToMany(mappedBy = "parent")
  private Set<Permission> children = new LinkedHashSet<>();
  @Column(nullable = false, length = 100)
  private String code;
  @Column(nullable = false, length = 191)
  private String name;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 6)
  private Type type;
  @Column(nullable = false)
  private boolean enabled = true;
  @ManyToMany
  @JoinTable(name = "permission_apis",
      joinColumns = @JoinColumn(name = "permission_id", foreignKey = @ForeignKey(name = "fk_permission_api_permission")),
      inverseJoinColumns = @JoinColumn(name = "api_id", foreignKey = @ForeignKey(name = "fk_permission_api_api")))
  private Set<ApiResource> apis = new LinkedHashSet<>();

  protected Permission() {}

  public Permission(ApplicationSystem system, String code, String name, Type type) {
    Assert.notNull(system, "权限必须属于一个系统");
    Assert.notNull(type, "权限类型不能为空");
    Assert.hasText(code, "权限编码不能为空");
    Assert.hasText(name, "权限名称不能为空");
    this.system = system;
    this.code = code.trim();
    this.name = name.trim();
    this.type = type;
  }

  public void moveTo(Permission parent) {
    if (parent != null) {
      Assert.isTrue(system.sameIdentity(parent.getSystem()), "权限父子必须属于同一系统");
      Assert.isTrue(parent.getType() == Type.MENU, "按钮不能有子权限");
    }
    Set<Object> visited = new HashSet<>();
    for (Permission node = parent; node != null; node = node.getParent()) {
      Object key = node.getId() == null ? node : node.getId();
      Assert.isTrue(node != this && (id == null || !id.equals(node.getId())) && visited.add(key),
          "权限不能自指或形成循环");
    }
    if (this.parent != null) this.parent.removeChild(this);
    this.parent = parent;
    if (parent != null) parent.addChild(this);
  }

  public void changeType(Type type) {
    Assert.notNull(type, "权限类型不能为空");
    Assert.isTrue(type != Type.BUTTON || children.isEmpty(), "有子权限的菜单不能改为按钮");
    this.type = type;
  }

  public void mapApi(ApiResource api) {
    Assert.notNull(api, "API 不能为空");
    Assert.isTrue(system.sameIdentity(api.getSystem()), "权限只能映射同系统 API");
    apis.add(api);
  }

  public void unmapApi(ApiResource api) { apis.remove(api); }

  // 维护反向集合，使同一事务内移动子节点后再改类型也能检查真实子节点。
  protected void addChild(Permission child) { children.add(child); }
  protected void removeChild(Permission child) { children.remove(child); }

  public Long getId() { return id; }
  public ApplicationSystem getSystem() { return system; }
  public Permission getParent() { return parent; }
  public Set<Permission> getChildren() { return Collections.unmodifiableSet(children); }
  public String getCode() { return code; }
  public String getName() { return name; }
  public Type getType() { return type; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public Set<ApiResource> getApis() { return Collections.unmodifiableSet(apis); }
}
