package com.municipal.rbaccore.logging;

import com.municipal.rbac.AuditEvent;
import com.municipal.rbaccore.security.RbacCatalog;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 迁入整改库的最小鉴权运行日志，不进入核心ER，不恢复归档或完整审计。 */
@Entity
@Table(name = "authorization_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthorizationLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "occurred_at", nullable = false, columnDefinition = "datetime(3)")
  private Instant occurredAt;

  @Column(name = "user_id")
  private Long userId;

  @Column(length = 100)
  private String username;

  @Column(name = "app_code", nullable = false, length = 191)
  private String appCode;

  @Column(name = "permission_code", length = 191)
  private String permissionCode;

  @Column(nullable = false)
  private boolean allowed;

  @Column(name = "request_id", nullable = false, length = 64)
  private String requestId;

  @Column(nullable = false, length = 64)
  private String reason;

  @Column(name = "event_type", nullable = false, length = 32)
  private String eventType;

  @Column(name = "http_method", nullable = false, length = 16)
  private String httpMethod;

  @Column(name = "request_path", nullable = false, length = 512)
  private String requestPath;

  @Column(name = "http_status", nullable = false)
  private int httpStatus;

  /** 仅接收过滤器/AOP的安全事件；系统归属固定为rbac而非业务后台。 */
  AuthorizationLog(AuditEvent event, Instant now) {
    this.occurredAt = now;
    this.userId = event.userId();
    this.username = event.username();
    this.appCode = RbacCatalog.SYSTEM_CODE;
    this.allowed = event.allowed();
    this.requestId = event.requestId();
    this.reason = event.reason();
    this.eventType = event.eventType();
    this.httpMethod = event.httpMethod();
    this.requestPath =
        event.requestPath().substring(0, Math.min(event.requestPath().length(), 512));
    this.httpStatus = event.httpStatus();
  }
}
