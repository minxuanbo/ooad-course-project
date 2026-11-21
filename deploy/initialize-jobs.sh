#!/usr/bin/env bash
# 在C执行显式的一次性初始化任务；不启动产品副本，不重置数据。
set -eu
: "${RELEASE_ID:?}" "${SECRET_PREFIX:?}" "${RBAC_BACKEND_IMAGE:?}" "${INTEGRATION_IMAGE:?}"
phase=${1:?Choose import, base-preview, base-apply, course-preview or course-apply}
case "$phase" in import|base-preview|base-apply|course-preview|course-apply) ;; *) exit 2;; esac
for service in rbac-backend course-backend course-integration rbac-frontend course-frontend; do
  replicas=$(docker service inspect "ooad_$service" --format '{{.Spec.Mode.Replicated.Replicas}}')
  test "$replicas" = 0 || { echo 'Product services must remain at zero replicas during first initialization' >&2; exit 1; }
done
name="ooad-init-$phase-$RELEASE_ID"
if docker service inspect "$name" >/dev/null 2>&1; then
  echo 'Preserve existing job and logs. Inspect its result before choosing a new RELEASE_ID.' >&2
  exit 1
fi
common=(--name "$name" --detach --with-registry-auth --replicas 1 --restart-condition none --constraint node.hostname==course-worker-log --network javaee-proxy --limit-memory 896M --log-driver json-file --log-opt max-size=10m --log-opt max-file=5)
if [[ "$phase" == course-* ]]; then
  args=()
  test "$phase" != course-apply || args+=(--apply)
  docker service create "${common[@]}" \
    --env DB_HOST=mysql --env INITIALIZATION_PHASE=offline-first-install \
    --secret "source=${SECRET_PREFIX}_rbac_db_password,target=rbac_db_password" \
    --secret "source=${SECRET_PREFIX}_course_db_password,target=course_db_password" \
    "$INTEGRATION_IMAGE" python /app/deploy/initialize_deployment.py "${args[@]}"
else
  main=com.municipal.rbaccore.bootstrap.BootstrapAuthorizationCommand
  args=()
  if test "$phase" = import; then
    main=com.municipal.rbaccore.bootstrap.ImportCommand
  elif test "$phase" = base-preview; then
    args+=("--rbac.core.preview-file=/app/.local/base-role-preview-$RELEASE_ID.csv")
  fi
  docker service create "${common[@]}" \
    --env APP_BACKEND_STRATEGY=ddd \
    --env 'RBAC_CORE_DB_URL=jdbc:mysql://mysql:3306/rbac_core?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false' \
    --env RBAC_CORE_DB_USER=rbac --env RBAC_CORE_SOURCE_DIR=/sources \
    --env SPRING_CONFIG_IMPORT=configtree:/run/secrets/ \
    --env 'JAVA_TOOL_OPTIONS=-Xms64m -Xmx384m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+ExitOnOutOfMemoryError' \
    --secret "source=${SECRET_PREFIX}_rbac_db_password,target=spring.datasource.password" \
    --secret "source=${SECRET_PREFIX}_employee_password,target=rbac.core.import.employee-password" \
    --secret "source=${SECRET_PREFIX}_admin_password,target=rbac.core.import.admin-password" \
    --mount type=bind,src=/opt/ooad/sources,dst=/sources,readonly \
    --mount type=bind,src=/opt/ooad/private-preview,dst=/app/.local \
    "$RBAC_BACKEND_IMAGE" "-Dloader.main=$main" -cp /app/app.jar org.springframework.boot.loader.launch.PropertiesLauncher "${args[@]}"
fi
echo "Job created: $name. Wait for Complete, inspect its exit code and retain logs before the next phase."
echo "docker service ps --no-trunc $name"
echo "docker service logs $name"
