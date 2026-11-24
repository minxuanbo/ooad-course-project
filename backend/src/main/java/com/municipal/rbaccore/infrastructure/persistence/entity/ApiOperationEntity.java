package com.municipal.rbaccore.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.*;

/** 操作标识映射独立于API资源，多标识可指向同一路由并保留对象门禁例外。 */
@Entity
@Table(name = "api_operations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiOperationEntity {
  @EmbeddedId
  private Key id;

  @MapsId("systemId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "system_id", foreignKey =
      @ForeignKey(name = "fk_operation_system"))
  private ApplicationSystemEntity system;

  @Column(name = "http_method", nullable = false, length = 16)
  private String httpMethod;

  @Column(name = "path_template", nullable = false, length = 512)
  private String pathTemplate;

  @Column(name = "requires_permission", nullable = false)
  private boolean requiresPermission;

  @Column(name = "permission_roles", nullable = false, columnDefinition = "LONGTEXT")
  private String permissionRoles;

  /** 维护显式元数据，不保存账号授权快照。 */
  public ApiOperationEntity(
      ApplicationSystemEntity system,
      String operationId,
      String method,
      String path,
      boolean required,
      String roles) {
    this.id = new Key(system.getId(), operationId);
    this.system = system;
    this.httpMethod = method;
    this.pathTemplate = path;
    this.requiresPermission = required;
    this.permissionRoles = roles;
  }

  /** 同应用操作编号是稳定自然键，跨应用同名互不影响。 */
  @Embeddable
  @Getter
  @EqualsAndHashCode
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  public static class Key implements Serializable {
    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "operation_id", nullable = false, length = 128)
    private String operationId;
  }
}
