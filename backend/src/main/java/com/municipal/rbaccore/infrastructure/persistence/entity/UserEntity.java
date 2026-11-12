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

/** RBAC 用户；username 即工号，手机号只是同一账号的另一登录入口。 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_user_username", columnNames = "username"),
      @UniqueConstraint(name = "uk_user_phone", columnNames = "phone")
    })
@cn.edu.xmu.clonefactory.CopyTo({
  com.municipal.rbaccore.service.port.auth.IdentityResult.class,
  com.municipal.rbaccore.service.port.user.UserResult.class,
  com.municipal.rbaccore.service.port.user.UserWithRolesResult.class
})
public class UserEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(
      nullable = false,
      length = 100,
      columnDefinition = "varchar(100) COLLATE utf8mb4_0900_bin")
  private String username;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 32, columnDefinition = "varchar(32) COLLATE utf8mb4_0900_bin")
  private String phone;

  @Column(length = 191)
  private String position;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "department_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_user_department"))
  private DepartmentEntity department;

  @Setter
  @Column(nullable = false)
  @cn.edu.xmu.clonefactory.CopyTo.Exclude({
    com.municipal.rbaccore.service.port.auth.IdentityResult.class,
    com.municipal.rbaccore.service.port.user.UserResult.class,
    com.municipal.rbaccore.service.port.user.UserWithRolesResult.class
  })
  private boolean enabled;

  @Getter(AccessLevel.NONE)
  @ManyToMany
  @JoinTable(
      name = "user_roles",
      joinColumns =
          @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_role_user")),
      inverseJoinColumns =
          @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_user_role_role")))
  private Set<RoleEntity> roles = new LinkedHashSet<>();

  /** 创建账号时明确提供哈希、所属部门和状态，不生成另一份工号。 */
  public UserEntity(
      String username,
      String passwordHash,
      String name,
      DepartmentEntity department,
      boolean enabled) {
    Assert.hasText(username, "工号不能为空");
    Assert.isTrue(username.trim().length() <= 100, "工号不能超过100字符");
    Assert.hasText(passwordHash, "必须提供口令哈希");
    Assert.isTrue(passwordHash.length() <= 100, "口令哈希不能超过100字符");
    Assert.hasText(name, "姓名不能为空");
    Assert.isTrue(name.trim().length() <= 100, "姓名不能超过100字符");
    Assert.notNull(department, "用户必须属于一个部门");
    this.username = username.trim();
    this.passwordHash = passwordHash;
    this.name = name.trim();
    this.department = department;
    this.enabled = enabled;
  }

  /** 改变人员归属；权限及范围校验由调用方的业务事务完成。 */
  public void moveTo(DepartmentEntity department) {
    Assert.notNull(department, "用户必须属于一个部门");
    this.department = department;
  }

  /** 保留手机号前导零，空白手机号不参与登录匹配与唯一性检查。 */
  public void updateProfile(String phone, String position) {
    String normalizedPhone = phone == null || phone.isBlank() ? null : phone.trim();
    Assert.isTrue(normalizedPhone == null || normalizedPhone.length() <= 32, "手机号不能超过32字符");
    Assert.isTrue(position == null || position.length() <= 191, "岗位不能超过191字符");
    this.phone = normalizedPhone;
    this.position = position;
  }

  /** 添加角色关联，不按岗位或角色名称推导权限。 */
  public void assignRole(RoleEntity role) {
    Assert.notNull(role, "角色不能为空");
    roles.add(role);
  }

  /** 解除角色关联，业务入口另行检查最后管理员保护。 */
  public void removeRole(RoleEntity role) {
    roles.remove(role);
  }

  /** 返回只读关联，避免绕过受控的角色变更入口。 */
  public Set<RoleEntity> getRoles() {
    return Collections.unmodifiableSet(roles);
  }
}
