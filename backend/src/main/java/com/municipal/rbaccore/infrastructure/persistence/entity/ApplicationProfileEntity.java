package com.municipal.rbaccore.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

/** 应用接入元数据支撑记录，不承担课程身份或人员级别。 */
@Entity
@Table(name = "application_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationProfileEntity {
  @Id
  private Long systemId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "system_id", foreignKey =
      @ForeignKey(name = "fk_profile_system"))
  private ApplicationSystemEntity system;

  @Column(name = "entry_url", length = 512)
  private String entryUrl;

  @Column(name = "login_permission_code", length = 100)
  private String loginPermissionCode;

  /** 更新同一支撑记录，不用带既有主键的新对象触发错误merge。 */
  public void revise(String entryUrl, String loginPermissionCode) {
    this.entryUrl = entryUrl;
    this.loginPermissionCode = loginPermissionCode;
  }

  /** 只有明确发布接入配置时创建或更新，默认启动不会产生记录。 */
  public ApplicationProfileEntity(
      ApplicationSystemEntity system, String entryUrl, String loginPermissionCode) {
    this.system = system;
    this.entryUrl = entryUrl;
    this.loginPermissionCode = loginPermissionCode;
  }
}
