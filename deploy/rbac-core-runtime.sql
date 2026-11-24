-- 在T03独立整改库上追加运行支撑，不修改九张核心表，不建立会话/凭证/资格实体。
CREATE TABLE IF NOT EXISTS authorization_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  occurred_at DATETIME(3) NOT NULL,
  user_id BIGINT,
  username VARCHAR(100),
  app_code VARCHAR(191) NOT NULL,
  permission_code VARCHAR(191),
  allowed BIT NOT NULL,
  request_id VARCHAR(64) NOT NULL,
  reason VARCHAR(64) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  http_method VARCHAR(16) NOT NULL,
  request_path VARCHAR(512) NOT NULL,
  http_status INT NOT NULL,
  client_ip VARCHAR(45),
  PRIMARY KEY (id),
  INDEX ix_authorization_log_user (user_id, id),
  INDEX ix_authorization_log_time (occurred_at, id),
  CONSTRAINT fk_authorization_log_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- 首次批量授权的执行回执；不参与运行时鉴权，不记录角色版本或管理员资格。
CREATE TABLE IF NOT EXISTS bootstrap_receipts (
  seed_key VARCHAR(64) NOT NULL,
  manifest_sha256 VARCHAR(64) NOT NULL,
  completed_at DATETIME(3) NOT NULL,
  source_json LONGTEXT,
  PRIMARY KEY (seed_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- 显式接入元数据；只在首次安装建立，产品启动不导入或重授。
CREATE TABLE IF NOT EXISTS application_profiles (
 system_id BIGINT NOT NULL,
 entry_url VARCHAR(512),
 login_permission_code VARCHAR(100),
 PRIMARY KEY(system_id),
 CONSTRAINT fk_profile_system FOREIGN KEY(system_id) REFERENCES systems(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS api_operations (
 system_id BIGINT NOT NULL,
 operation_id VARCHAR(128) NOT NULL,
 http_method VARCHAR(16) NOT NULL,
 path_template VARCHAR(512) NOT NULL,
 requires_permission BIT NOT NULL,
 permission_roles LONGTEXT NOT NULL,
 PRIMARY KEY(system_id,operation_id),
 CONSTRAINT fk_operation_system FOREIGN KEY(system_id) REFERENCES systems(id),
 CONSTRAINT ck_operation_method CHECK(http_method IN ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS')),
 CONSTRAINT ck_operation_roles_json CHECK(JSON_VALID(permission_roles))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
