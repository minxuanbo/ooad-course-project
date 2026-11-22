#!/usr/bin/env bash
# 仅由官方MySQL入口在全新数据卷首次执行；旧卷不会重建、重置或导入。
(
# 官方入口会source非可执行脚本；其函数允许省略$1，不能启用nounset。
set -e
rbac_password=$(cat /run/secrets/rbac_db_password)
course_password=$(cat /run/secrets/course_db_password)
audit_password=$(cat /run/secrets/audit_db_password)
context_password=$(cat /run/secrets/course_read_password)
for password in "$rbac_password" "$course_password" "$audit_password" "$context_password"; do
  case "$password" in *[!0-9a-f]*|'') echo 'Database secrets must be generated hexadecimal values' >&2; exit 1;; esac
done
docker_process_sql <<SQL
CREATE DATABASE rbac_core CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
CREATE DATABASE course_project CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE course_audit CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'rbac'@'%' IDENTIFIED BY '${rbac_password}';
CREATE USER 'course_app'@'%' IDENTIFIED BY '${course_password}';
CREATE USER 'course_audit'@'%' IDENTIFIED BY '${audit_password}';
CREATE USER 'course_context'@'%' IDENTIFIED BY '${context_password}';
GRANT SELECT,INSERT,UPDATE,DELETE ON rbac_core.* TO 'rbac'@'%';
GRANT ALL PRIVILEGES ON course_project.* TO 'course_app'@'%';
GRANT SELECT,INSERT,UPDATE ON course_audit.* TO 'course_audit'@'%';
GRANT SELECT ON course_project.* TO 'course_context'@'%';
SQL
docker_process_sql --database=rbac_core < /bootstrap/rbac-core-schema.sql
docker_process_sql --database=rbac_core < /bootstrap/rbac-core-runtime.sql
docker_process_sql --database=course_audit < /bootstrap/course-audit-schema.sql
unset rbac_password course_password audit_password context_password password
)
