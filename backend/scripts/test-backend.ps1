param([string[]]$MavenArguments = @('clean', 'test'))
$ErrorActionPreference = 'Stop'
# 运行器不建立或销毁数据库，所有URL仍须通过测试上下文自身的严格守卫。
$requiredVariables = @(
  'RBAC_CORE_TEST_DB_URL', 'RBAC_CORE_IMPORT_TEST_DB_URL', 'RBAC_CORE_HTTP_TEST_DB_URL',
  'RBAC_CORE_RESOURCE_TEST_DB_URL', 'RBAC_CORE_ROLE_TEST_DB_URL', 'RBAC_CORE_USER_TEST_DB_URL',
  'RBAC_CORE_TEST_DB_USER', 'RBAC_CORE_TEST_DB_PASSWORD'
)
foreach ($variableName in $requiredVariables) {
  if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variableName))) {
    throw "缺少必要测试配置：$variableName；见backend/README.md，不输出配置值"
  }
}
$backendDirectory = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
Push-Location -LiteralPath $backendDirectory
try {
  & mvn @MavenArguments
  $testExitCode = $LASTEXITCODE
} finally {
  Pop-Location
}
exit $testExitCode
