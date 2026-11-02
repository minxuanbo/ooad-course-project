package com.municipal.rbaccore.model;

import jakarta.persistence.*;
import org.springframework.util.Assert;

@Entity
@Table(name = "systems", uniqueConstraints = @UniqueConstraint(name = "uk_system_code", columnNames = "code"))
public class ApplicationSystem {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, length = 100)
  private String code;
  @Column(nullable = false, length = 191)
  private String name;
  @Column(nullable = false)
  private boolean enabled = true;

  protected ApplicationSystem() {}

  public ApplicationSystem(String code, String name) {
    Assert.hasText(code, "系统编码不能为空");
    Assert.hasText(name, "系统名称不能为空");
    this.code = code.trim();
    this.name = name.trim();
  }

  boolean sameIdentity(ApplicationSystem other) {
    return other != null && (this == other || (getId() != null && getId().equals(other.getId())));
  }

  public Long getId() { return id; }
  public String getCode() { return code; }
  public String getName() { return name; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
