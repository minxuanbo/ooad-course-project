-- 仅用于新项目独立整改库；由调用方显式选择 rbac_core 数据库。
-- 九张核心表不变，import_sources 为导入来源支撑，不进入核心 ER。
-- 不删除、清空或覆盖已有表；已有结构不符时由 JPA validate 拒绝启动。
CREATE TABLE IF NOT EXISTS departments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL,
  name VARCHAR(191) NOT NULL,
  parent_id BIGINT,
  type VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT',
  deleted BIT NOT NULL DEFAULT 0,
  active_name VARCHAR(191) GENERATED ALWAYS AS (IF(deleted=0,TRIM(name),NULL)) STORED,
  PRIMARY KEY (id),
  CONSTRAINT uk_department_code UNIQUE (code),
  CONSTRAINT uk_department_sibling_name UNIQUE (parent_id, active_name),
  CONSTRAINT ck_department_type CHECK (type IN ('HEADQUARTERS','COMPANY','DEPARTMENT','PROJECT')),
  CONSTRAINT ck_department_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 100),
  CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES departments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS systems (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL,
  name VARCHAR(191) NOT NULL,
  enabled BIT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_system_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS roles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  code VARCHAR(100) NOT NULL,
  name VARCHAR(100) NOT NULL,
  enabled BIT NOT NULL,
  management_level INT,
  PRIMARY KEY (id),
  CONSTRAINT uk_role_code UNIQUE (code)
  ,CONSTRAINT ck_role_level CHECK (management_level BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(100) COLLATE utf8mb4_0900_bin NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  name VARCHAR(100) NOT NULL,
  phone VARCHAR(32) COLLATE utf8mb4_0900_bin,
  position VARCHAR(191),
  department_id BIGINT NOT NULL,
  enabled BIT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_user_username UNIQUE (username),
  CONSTRAINT uk_user_phone UNIQUE (phone),
  CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS api_resources (
  id BIGINT NOT NULL AUTO_INCREMENT,
  system_id BIGINT NOT NULL,
  http_method VARCHAR(16) NOT NULL,
  path_template VARCHAR(512) NOT NULL,
  enabled BIT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_api_system_method_path UNIQUE (system_id, http_method, path_template),
  CONSTRAINT fk_api_system FOREIGN KEY (system_id) REFERENCES systems(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  system_id BIGINT NOT NULL,
  parent_id BIGINT,
  code VARCHAR(100) NOT NULL,
  name VARCHAR(191) NOT NULL,
  type ENUM('MENU','BUTTON') NOT NULL,
  enabled BIT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_permission_system_code UNIQUE (system_id, code),
  CONSTRAINT fk_permission_system FOREIGN KEY (system_id) REFERENCES systems(id),
  CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS role_permissions (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES roles(id),
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS permission_apis (
  permission_id BIGINT NOT NULL,
  api_id BIGINT NOT NULL,
  PRIMARY KEY (permission_id, api_id),
  CONSTRAINT fk_permission_api_permission FOREIGN KEY (permission_id) REFERENCES permissions(id),
  CONSTRAINT fk_permission_api_api FOREIGN KEY (api_id) REFERENCES api_resources(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE IF NOT EXISTS import_sources (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_type ENUM('ORGANIZATION','EMPLOYEE','BOOTSTRAP') NOT NULL,
  source_key VARCHAR(191) COLLATE utf8mb4_0900_bin NOT NULL,
  file_sha256 VARCHAR(64),
  source_row INT,
  source_json LONGTEXT NOT NULL,
  department_id BIGINT,
  user_id BIGINT,
  PRIMARY KEY (id),
  CONSTRAINT uk_import_source_key UNIQUE (source_type, source_key),
  CONSTRAINT uk_import_source_user UNIQUE (user_id),
  CONSTRAINT fk_import_department FOREIGN KEY (department_id) REFERENCES departments(id),
  CONSTRAINT fk_import_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT ck_import_source_target CHECK (
    (source_type='ORGANIZATION' AND department_id IS NOT NULL AND user_id IS NULL)
    OR (source_type IN ('EMPLOYEE','BOOTSTRAP') AND user_id IS NOT NULL AND department_id IS NULL)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
