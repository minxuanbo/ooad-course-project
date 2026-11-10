package com.municipal.rbaccore.bootstrap;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 首次授权事务回执，只用于防重入，不参与管理员资格或运行时权限判断。 */
@Entity
@Table(name = "bootstrap_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BootstrapReceipt {
  @Id
  @Column(name = "seed_key", length = 64)
  private String seedKey;

  @Column(name = "manifest_sha256", nullable = false, length = 64)
  private String manifestSha256;

  @Column(name = "completed_at", nullable = false, columnDefinition = "datetime(3)")
  private Instant completedAt;

  /** 回执与整批角色关联同一事务提交，失败不留下完成标记。 */
  BootstrapReceipt(String manifestSha256, Instant completedAt) {
    this.seedKey = "rbac-initial-roles";
    this.manifestSha256 = manifestSha256;
    this.completedAt = completedAt;
  }
}
