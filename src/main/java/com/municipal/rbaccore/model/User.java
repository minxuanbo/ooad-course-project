package com.municipal.rbaccore.model;

import jakarta.persistence.*;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.util.Assert;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_employee_no", columnNames = "employee_no"),
    @UniqueConstraint(name = "uk_user_username", columnNames = "username")})
public class User {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "employee_no", nullable = false, length = 100)
  private String employeeNo;
  @Column(nullable = false, length = 100)
  private String username;
  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;
  @Column(nullable = false, length = 100)
  private String name;
  @Column(length = 32)
  private String phone;
  @Column(length = 191)
  private String position;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "department_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_department"))
  private Department department;
  @Column(nullable = false)
  private boolean enabled;
  @ManyToMany
  @JoinTable(name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_role_user")),
      inverseJoinColumns = @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_user_role_role")))
  private Set<Role> roles = new LinkedHashSet<>();

  protected User() {}

  public User(String employeeNo, String username, String passwordHash, String name,
      Department department, boolean enabled) {
    Assert.hasText(employeeNo, "工号不能为空");
    Assert.hasText(username, "用户名不能为空");
    Assert.hasText(passwordHash, "必须保留口令哈希");
    Assert.hasText(name, "姓名不能为空");
    Assert.notNull(department, "用户必须属于一个部门");
    this.employeeNo = employeeNo.trim();
    this.username = username.trim();
    this.passwordHash = passwordHash;
    this.name = name.trim();
    this.department = department;
    this.enabled = enabled;
  }

  public void moveTo(Department department) {
    Assert.notNull(department, "用户必须属于一个部门");
    this.department = department;
  }

  public void updateProfile(String phone, String position) {
    this.phone = phone;
    this.position = position;
  }

  public void assignRole(Role role) {
    Assert.notNull(role, "角色不能为空");
    roles.add(role);
  }

  public void removeRole(Role role) { roles.remove(role); }

  public Long getId() { return id; }
  public String getEmployeeNo() { return employeeNo; }
  public String getUsername() { return username; }
  public String getPasswordHash() { return passwordHash; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public String getPosition() { return position; }
  public Department getDepartment() { return department; }
  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public Set<Role> getRoles() { return Collections.unmodifiableSet(roles); }
}
