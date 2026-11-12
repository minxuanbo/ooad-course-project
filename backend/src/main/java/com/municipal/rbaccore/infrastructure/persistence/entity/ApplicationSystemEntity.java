package com.municipal.rbaccore.infrastructure.persistence.entity;


import jakarta.persistence.*;
import org.springframework.util.Assert;

/** 应用系统持久化记录，只维护稳定标识和启用状态，不承担授权决策。 */
@Entity
@Table(
    name = "systems",
    uniqueConstraints = @UniqueConstraint(name = "uk_system_code", columnNames = "code"))
@cn.edu.xmu.clonefactory.CopyTo({com.municipal.rbaccore.service.port.resource.SystemResult.class})
@lombok.Getter
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ApplicationSystemEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String code;

  @Column(nullable = false, length = 191)
  private String name;

  @Column(nullable = false)
  @cn.edu.xmu.clonefactory.CopyTo.Exclude({
    com.municipal.rbaccore.service.port.resource.SystemResult.class
  })
  @lombok.Setter
  private boolean enabled = true;

  /** 创建带必要归属和稳定编码的持久化记录，输入缺失时拒绝。 */
  public ApplicationSystemEntity(String code, String name) {
    Assert.hasText(code, "系统编码不能为空");
    Assert.hasText(name, "系统名称不能为空");
    this.code = code.trim();
    this.name = name.trim();
  }

  /** 已持久化记录按ID比较，未持久化记录只承认同一对象引用。 */
  boolean sameIdentity(ApplicationSystemEntity other) {
    return other != null && (this == other || (getId() != null && getId().equals(other.getId())));
  }
}
