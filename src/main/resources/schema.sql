-- MySQL 8.0.16+ / 8.4, InnoDB, UTF-8; all DATETIME values are UTC.
CREATE TABLE IF NOT EXISTS companies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(191) NOT NULL UNIQUE,
  enabled TINYINT NOT NULL DEFAULT 1,
  CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS departments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(191) NOT NULL,
  manager_user_id BIGINT,
  enabled TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_department_name (company_id,name),
  FOREIGN KEY (company_id) REFERENCES companies(id),
  CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_no VARCHAR(100) NOT NULL UNIQUE,
  username VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(32) UNIQUE,
  department_id BIGINT,
  position VARCHAR(191),
  password_hash VARCHAR(100) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (department_id) REFERENCES departments(id),
  CHECK (status IN ('ACTIVE','DISABLED')),
  INDEX idx_users_name (name),
  INDEX idx_users_dept (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS applications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(191) NOT NULL UNIQUE,
  name VARCHAR(191) NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS modules (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_id BIGINT NOT NULL,
  code VARCHAR(100) NOT NULL,
  name VARCHAR(191) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  FOREIGN KEY (app_id) REFERENCES applications(id),
  UNIQUE KEY uk_module_code (app_id,code),
  UNIQUE KEY uk_module_name (app_id,name),
  INDEX idx_module_sort (app_id,sort_order,id),
  CHECK (sort_order>=0), CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS features (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  module_id BIGINT NOT NULL,
  code VARCHAR(100) NOT NULL,
  name VARCHAR(191) NOT NULL,
  description TEXT,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  FOREIGN KEY (module_id) REFERENCES modules(id),
  UNIQUE KEY uk_feature_code (module_id,code),
  UNIQUE KEY uk_feature_name (module_id,name),
  INDEX idx_feature_sort (module_id,sort_order,id),
  CHECK (sort_order>=0), CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS actions (
  code VARCHAR(32) PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  CHECK (sort_order>=0), CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  feature_id BIGINT NOT NULL,
  action_code VARCHAR(32) NOT NULL,
  code VARCHAR(512) NOT NULL UNIQUE,
  enabled TINYINT NOT NULL DEFAULT 1,
  FOREIGN KEY (feature_id) REFERENCES features(id),
  FOREIGN KEY (action_code) REFERENCES actions(code),
  UNIQUE KEY uk_permission_function (feature_id,action_code),
  CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS roles (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL UNIQUE,
  description TEXT,
  enabled TINYINT NOT NULL DEFAULT 1,
  CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  granted_by BIGINT,
  granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id,role_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
  FOREIGN KEY (granted_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id,permission_id),
  FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
  FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS sessions (
  token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  user_id BIGINT NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  revoked TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (user_id) REFERENCES users(id),
  CHECK (revoked IN (0,1)),
  INDEX idx_sessions_user (user_id),
  INDEX idx_sessions_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS authorization_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  user_id BIGINT,
  username VARCHAR(100),
  app_code VARCHAR(191) NOT NULL,
  permission_code VARCHAR(512) NOT NULL,
  allowed TINYINT NOT NULL,
  request_id VARCHAR(191),
  reason TEXT,
  event_type VARCHAR(32) NOT NULL DEFAULT 'AUTHORIZATION',
  http_method VARCHAR(16) NOT NULL DEFAULT '',
  request_path VARCHAR(512) NOT NULL DEFAULT '',
  http_status INT,
  archived_at DATETIME(3),
  archived_by BIGINT,
  CHECK (allowed IN (0,1)),
  INDEX idx_auth_logs_time (occurred_at),
  INDEX idx_auth_logs_user (user_id),
  INDEX idx_log_user_id (user_id,id),
  INDEX idx_log_app_id (app_code,id),
  INDEX idx_log_archive_time (archived_at,occurred_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS authorization_changes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  operator_id BIGINT,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  detail TEXT,
  INDEX idx_changes_target (target_type,target_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS handovers (
  user_id BIGINT PRIMARY KEY,
  receiver_id BIGINT NOT NULL,
  owner_id BIGINT NOT NULL,
  expected_date DATE NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  oa_reference VARCHAR(200) NOT NULL DEFAULT '',
  state VARCHAR(16) NOT NULL,
  started_by BIGINT NOT NULL,
  started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_by BIGINT,
  completed_at DATETIME(3),
  completion_note VARCHAR(1000),
  FOREIGN KEY (user_id) REFERENCES users(id),
  FOREIGN KEY (receiver_id) REFERENCES users(id),
  FOREIGN KEY (owner_id) REFERENCES users(id),
  FOREIGN KEY (started_by) REFERENCES users(id),
  FOREIGN KEY (completed_by) REFERENCES users(id),
  CHECK (state IN ('HANDOVER','DEPARTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- Schema revision and role-bound management authorization.
CREATE TABLE IF NOT EXISTS management_migrations (
  version INT PRIMARY KEY,
  completed TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS role_management_permissions (
  role_id BIGINT NOT NULL,
  permission_code VARCHAR(64) NOT NULL,
  PRIMARY KEY(role_id,permission_code),
  FOREIGN KEY(role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS management_scopes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  scope_type VARCHAR(16) NOT NULL,
  company_id BIGINT,
  department_id BIGINT,
  FOREIGN KEY(user_id,role_id) REFERENCES user_roles(user_id,role_id) ON DELETE CASCADE,
  FOREIGN KEY(company_id) REFERENCES companies(id),
  FOREIGN KEY(department_id) REFERENCES departments(id),
  CHECK ((scope_type='GLOBAL' AND company_id IS NULL AND department_id IS NULL)
    OR (scope_type='COMPANY' AND company_id IS NOT NULL AND department_id IS NULL)
    OR (scope_type='DEPARTMENT' AND company_id IS NULL AND department_id IS NOT NULL)),
  INDEX idx_management_scope_user(user_id,role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
CREATE TABLE IF NOT EXISTS login_identifiers (
  identifier VARCHAR(100) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_login_identifier_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
