#!/bin/sh
# Run on D as root. Only loopback inside the existing backend container is used.
set -eu
mode=${1:-}
case "$mode" in start|report) ;; *) echo 'Usage: sh deploy/live-jacoco.sh start|report' >&2; exit 2;; esac
base=/opt/course-rbac/reports/live-jacoco
umask 077
mkdir -p "$base"
ids=$(docker ps -q --filter label=com.docker.swarm.service.name=rbac_backend)
test "$(printf '%s\n' "$ids" | grep -c .)" -eq 1 || { echo 'Expected one running rbac_backend container' >&2; exit 1; }
cid=$(docker inspect --format '{{.Id}}' "$ids")
image=$(docker inspect --format '{{.Image}}' "$cid")
if [ "$mode" = report ]; then
  test -f "$base/active-container" || { echo 'Run start before the API demonstration' >&2; exit 1; }
  test "$(cat "$base/active-container")" = "$cid" || { echo 'Backend restarted: start a new session and repeat the API demonstration' >&2; exit 1; }
fi
name="$(date -u +%Y%m%dT%H%M%SZ)-$mode-$$"
out="$base/$name"
mkdir "$out"
reset=
if [ "$mode" = start ]; then reset=--reset; fi
# Empty JAVA_TOOL_OPTIONS prevents a second agent from attaching to the CLI process.
docker exec -e JAVA_TOOL_OPTIONS= "$cid" sh -c 'mkdir -p "$1"; java -Xmx128m -jar /opt/jacoco/cli.jar dump --address 127.0.0.1 --port 6300 --retry 0 --destfile "$1/coverage.exec" $2' sh "/tmp/$name" "$reset"
if [ "$mode" = report ]; then
  docker exec -e JAVA_TOOL_OPTIONS= "$cid" java -Xmx128m -jar /opt/jacoco/cli.jar report "/tmp/$name/coverage.exec" \
    --classfiles /opt/jacoco/classes --sourcefiles /opt/jacoco/source \
    --name 'Deployed RBAC backend - live API demonstration' \
    --html "/tmp/$name/html" --xml "/tmp/$name/jacoco.xml" --csv "/tmp/$name/jacoco.csv"
fi
test "$(docker inspect --format '{{.State.Running}}' "$cid")" = true
docker cp "$cid:/tmp/$name/." "$out/"
{
  printf 'mode=%s\ncollected_utc=%s\ncontainer_id=%s\nimage_id=%s\n' "$mode" "$(date -u +%FT%TZ)" "$cid" "$image"
  docker inspect --format 'image_reference={{.Config.Image}}' "$cid"
  docker exec -e JAVA_TOOL_OPTIONS= "$cid" sha256sum /app/app.jar
} > "$out/metadata.txt"
if [ "$mode" = start ]; then
  printf '%s\n' "$cid" > "$base/active-container"
  echo 'Coverage counters reset. Run the API demonstration now (business data unchanged by reset).'
else
  tar -czf "$out.tar.gz" -C "$out" .
  echo "HTML report: $out/html/index.html"
  echo "Download archive: $out.tar.gz"
fi
echo "Saved: $out"
