package com.municipal.rbaccore.model;

import jakarta.persistence.*;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.util.Assert;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(name = "uk_role_code", columnNames = "code"))
public class Role {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, length = 100)
  private String code;
  @Column(nullable = false, length = 100)
  private String name;
  @Column(nullable = false)
  private boolean enabled = true;
  @ManyToMany
  @JoinTable(name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_role_permission_role")),
      inverseJoinColumns = @JoinColumn(name = "permission_id", foreignKey = @ForeignKey(name = "fk_role_permission_permission")))
  private Set<Permission> permissions = new LinkedHashSet<>();

  protected Role() {}

  public Role(String code, String name) {
    Assert.hasText(code, "角色编码不能为空");
    Assert.hasText(name, "角色名称不能为空");
    this.code = code.trim();
    this.name = name.trim();
  }

  public void grantPermission(Permission permission) {
    Assert.notNull(permission, "权限不能为空");
    permissions.add(permission);
  }

  public void revokePermission(Permission permission) { permissions.remove(permission); }

  public Long getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public Set<Permission> getPermissions() { return Collections.unmodifiableSet(permissions); }
}
