#!/usr/bin/env bash
# 在A串行构建；本脚本不推送镜像、不调用stack deploy、不停止任何服务。
set -eu
cd "$(dirname "$0")/.."
: "${COURSE_SOURCE:?Set the independently prepared course source directory}"
: "${RBAC_BACKEND_IMAGE:?}" "${RBAC_FRONTEND_IMAGE:?}"
: "${COURSE_BACKEND_IMAGE:?}" "${COURSE_FRONTEND_IMAGE:?}" "${INTEGRATION_IMAGE:?}"
: "${RBAC_PUBLIC_ORIGIN:?}"
available_kb=$(df -Pk . | awk 'NR==2 {print $4}')
test "$available_kb" -ge 12582912 || { echo 'At least 12 GiB free disk required before building' >&2; exit 1; }
test -f "$COURSE_SOURCE/backend/pom.xml"
test -f "$COURSE_SOURCE/.dockerignore"
docker build -f deploy/java-build.Dockerfile -t ooad-java-build:clonefactory-0.0.2 .
docker build -f Dockerfile -t "$RBAC_BACKEND_IMAGE" .
docker build -f deploy/rbac-frontend.Dockerfile -t "$RBAC_FRONTEND_IMAGE" .
docker build -f deploy/course-backend.Dockerfile -t "$COURSE_BACKEND_IMAGE" "$COURSE_SOURCE"
docker build -f deploy/course-frontend.Dockerfile --build-arg "IDENTITY_PUBLIC_URL=$RBAC_PUBLIC_ORIGIN/course-identity" -t "$COURSE_FRONTEND_IMAGE" "$COURSE_SOURCE"
docker build -f deploy/integration.Dockerfile -t "$INTEGRATION_IMAGE" .
echo 'Five application images built locally. Push and deployment are separate steps.'
