#!/bin/sh
# Run on build host A. No published ports or existing business databases.
set -eu
: "${RBAC_TEST_MYSQL_IMAGE:?Set RBAC_TEST_MYSQL_IMAGE to the verified MySQL image}"
: "${RBAC_BUILD_IMAGE:?Set RBAC_BUILD_IMAGE to the Dockerfile build stage image}"
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_name="rbac-test-$(date +%s)-$$"
test_dir=$(mktemp -d)
report_dir="$project_dir/reports/jacoco/$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$report_dir"
: "${RBAC_TEST_EXCEL_DIR:=$project_dir}"
cleanup() {
  docker rm -fv "$test_name" >/dev/null 2>&1 || true
  docker network rm "$test_name" >/dev/null 2>&1 || true
  rm -f "$test_dir/mysql.env" "$test_dir/test.env"
  rmdir "$test_dir"
}
trap cleanup EXIT
umask 077
password=$(openssl rand -hex 24)
{
  printf 'MYSQL_ROOT_PASSWORD=%s\n' "$password"
  printf 'MYSQL_DATABASE=rbac_core_t02_test\nMYSQL_USER=rbac_test\nMYSQL_PASSWORD=%s\n' "$password"
} > "$test_dir/mysql.env"
{
  for pair in 'RBAC_CORE_TEST_DB_URL:rbac_core_t02_test' 'RBAC_CORE_IMPORT_TEST_DB_URL:rbac_core_t03_test' 'RBAC_CORE_HTTP_TEST_DB_URL:rbac_core_t05_test' 'RBAC_CORE_RESOURCE_TEST_DB_URL:rbac_core_t07_test' 'RBAC_CORE_ROLE_TEST_DB_URL:rbac_core_t09_test' 'RBAC_CORE_USER_TEST_DB_URL:rbac_core_t11_test'; do
    printf '%s=jdbc:mysql://127.0.0.1:3306/%s?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false\n' "${pair%%:*}" "${pair#*:}"
  done
  printf 'RBAC_CORE_TEST_DB_USER=rbac_test\nRBAC_CORE_TEST_DB_PASSWORD=%s\n' "$password"
  printf 'MAVEN_OPTS=-Xmx192m\n'
} > "$test_dir/test.env"
unset password
docker network create "$test_name" >/dev/null
docker run -d --name "$test_name" --network "$test_name" --memory 512m \
  --env-file "$test_dir/mysql.env" "$RBAC_TEST_MYSQL_IMAGE" \
  --innodb-buffer-pool-size=128M --performance-schema=OFF --mysqlx=OFF --skip-log-bin >/dev/null
ready=false
for attempt in $(seq 1 90); do
  if docker exec "$test_name" sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h 127.0.0.1 -u rbac_test rbac_core_t02_test -e "SELECT 1"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
if [ "$ready" != true ]; then
  docker logs --tail 30 "$test_name"
  exit 1
fi
# Only this disposable instance grants test-only schema/trigger privileges.
docker exec "$test_name" sh -c '
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  for db in rbac_core_t02_test rbac_core_t03_test rbac_core_t05_test rbac_core_t07_test rbac_core_t09_test rbac_core_t11_test; do
    printf "CREATE DATABASE IF NOT EXISTS %s CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin; GRANT ALL ON %s.* TO '\''rbac_test'\''@'\''%%'\'';\n" "$db" "$db"
  done | mysql -u root
'
# Share only this temporary MySQL network namespace: test guards require loopback URLs.
docker run --rm --network "container:$test_name" --memory 800m \
  --env-file "$test_dir/test.env" -v "$RBAC_TEST_EXCEL_DIR:/source:ro" -v "$project_dir:/project:ro" \
  -v "$report_dir:/reports" "$RBAC_BUILD_IMAGE" \
  sh -c '
    mkdir /tmp/test-project && cd /tmp/test-project || exit 1
    cp /project/pom.xml . && cp -R /project/src /project/deploy . && cp /source/*.xlsx . || exit 1
    mkdir frontend && cp -R /project/frontend/src /project/frontend/tests frontend/ || exit 1
    /opt/apache-maven-3.9.9/bin/mvn -B -ntp -s /opt/maven-settings.xml clean verify
    result=$?
    if [ -s target/site/jacoco/index.html ]; then
      test -s target/site/jacoco/index.html && test -s target/jacoco.exec || exit 1
      cp -R target/site/jacoco/. /reports/ || exit 1
      cp target/jacoco.exec /reports/ || exit 1
      for f in target/surefire-reports/*.txt; do
        sed -n "/Tests run:/p" "$f"
      done > /reports/test-summary.txt
      find target/classes/com/municipal -name '*.class' | sort | xargs sha256sum > /reports/class-sha256.txt
      find src/main/java src/test/java -name '*.java' | sort | xargs sha256sum > /reports/source-sha256.txt
    fi
    exit "$result"
  '
# Surefire XML may contain datasource credentials; never export it with public reports.
printf 'JaCoCo report: %s/index.html\n' "$report_dir"
