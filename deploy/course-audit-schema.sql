-- 全新独立course_audit库首次SQL；不修改RBAC鉴权日志表。
CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    app_code VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
    request_id VARCHAR(255) COLLATE utf8mb4_0900_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    payload MEDIUMTEXT NOT NULL,
    response MEDIUMTEXT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    target VARCHAR(1000) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_audit_request (app_code, request_id),
    KEY ix_audit_system_time (app_code, occurred_at, id),
    KEY ix_audit_system_actor (app_code, actor_id, id),
    CONSTRAINT chk_audit_payload CHECK (JSON_VALID(payload)),
    CONSTRAINT chk_audit_response CHECK (JSON_VALID(response))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
