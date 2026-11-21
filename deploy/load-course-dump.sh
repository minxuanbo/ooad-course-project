#!/usr/bin/env bash
# 仅在B对新Stack的空course_project库导入一次，禁止把老师DROP TABLE脚本用于已有业务库。
set -eu
: "${COURSE_DUMP:?Set the original SQL dump path}"
test -f "$COURSE_DUMP"
container=$(docker ps --filter label=com.docker.swarm.service.name=ooad_mysql --format '{{.ID}}')
test -n "$container"
test "$(printf '%s\n' "$container" | wc -l)" -eq 1
count=$(docker exec "$container" sh -c '
  export MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
  exec mysql -uroot -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=0x636f757273655f70726f6a656374"
')
test "$count" = 0 || { echo 'Refusing to import into a nonempty course database' >&2; exit 1; }
docker exec -i "$container" sh -c '
  export MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"
  exec mysql -uroot --binary-mode --default-character-set=utf8mb4 course_project
' < "$COURSE_DUMP"
echo 'Original course dump imported into the new empty database; identity reconciliation is still required.'
