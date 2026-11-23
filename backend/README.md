# RBAC后端

Java21、Spring Boot3.5.6、Spring Data JPA、MySQL8.4及老师真实CloneFactory。同一工程内提供structured和DDD两套业务实现，分别通过事务脚本和领域仓储编排。完整[后端设计](../docs/backend-design.md)说明共享API、用例、基础设施边界及代码与测试归类。

CloneFactory当前固定0.0.2，源码提交40583ff94764d2e758bcb13be17f5c05ea4d6352；按[本机依赖安装](../deploy/README.md#本机依赖安装)构建老师真实源码，依赖及处理器版本由同一个Maven属性维护。

组员从[完整本地复现](../docs/reproduction.md)开始，记录实际检出分支及提交。共享/结构化边界见[DDD隔离说明](../docs/backend-design.md#4-双实现选择方式)。

## 1. 目录与构建

```text
backend/
├── pom.xml
├── src/main/java/com/municipal/rbaccore/
│   ├── api/{auth,user,department,role,resource,audit,identity,shared}
│   ├── service/port/{auth,user,department,role,resource,audit,identity,security,importing,bootstrap,error}
│   ├── service/structured/{auth,user,department,role,resource,audit,identity,security,importing,bootstrap}
│   ├── service/ddd/application/{auth,user,department,role,resource,audit,identity,security,importing,bootstrap,support}
│   ├── service/ddd/domain/{model,model/policy,repository}
│   ├── infrastructure/{persistence,security,web,logging,excel}
│   ├── config
│   └── bootstrap
├── src/test/java/com/municipal/rbaccore/{api,architecture,bootstrap,config,infrastructure,r01,r03,r04,r06,r07,r11,r13,r14,r18,r22,r23,r29,shared}
└── scripts/{accept-runtime.py,jacoco-evidence.py,tests,test-backend.ps1}
```

DDD 持久化适配器位于 `infrastructure/persistence/ddd`。`r23` 同时包含共享 HTTP 契约 `R23PrototypeContractTest` 和纯领域测试 `R24DepartmentPolicyTest`；后者检查可信根保护、空节点删除与同父名称唯一规则，不连接数据库。部门规则由 `OrganizationPolicy` 执行，五个基础管理级别的领域常量统一定义在 `Role`，应用服务负责事务与错误翻译。

先按[固定依赖安装](../deploy/README.md#本机依赖安装)准备老师CloneFactory。以下命令在仓库根执行：

```powershell
mvn -f backend/pom.xml clean test-compile
mvn -f backend/pom.xml clean package
python -B -m unittest discover -s backend/scripts/tests -v
```

完整package包含真实MySQL测试，必须先提供对应本机专库及测试账号；不能指向业务库。测试变量/库映射见下节；双策略覆盖率按第7节合并并执行原六项零遗漏门槛，单策略报告不代表整个工程的合并覆盖率。打包结果为backend/target/rbac0-0.1.0.jar，默认入口com.municipal.rbaccore.bootstrap.RbacApplication。

## 2. 本地回归与隔离

| 环境变量 | 必须匹配的本机专库 |
| --- | --- |
| RBAC_CORE_TEST_DB_URL | rbac_core_t02_test |
| RBAC_CORE_IMPORT_TEST_DB_URL | rbac_core_t03_test |
| RBAC_CORE_HTTP_TEST_DB_URL | rbac_core_t05_test |
| RBAC_CORE_RESOURCE_TEST_DB_URL | rbac_core_t07_test |
| RBAC_CORE_ROLE_TEST_DB_URL | rbac_core_t09_test |
| RBAC_CORE_USER_TEST_DB_URL | rbac_core_t11_test |

另提供共用RBAC_CORE_TEST_DB_USER/PASSWORD。URL只允许127.0.0.1或localhost及明确端口/专库，建议UTC参数；原profile/库名为兼容既有安全守卫而保留，不再决定测试源码目录。原Excel保留仓库根，可用RBAC_TEST_EXCEL_DIR明确指定原件目录。所有数据源安全守卫先于创建连接执行，不应为跑通测试放宽。

分别设置`APP_BACKEND_STRATEGY=structured`或`APP_BACKEND_STRATEGY=ddd`并用对应隔离实例的六个同名专库运行相同测试。测试需要创建故障注入触发器；MySQL 8.4开启binlog时仅在私有测试实例允许`log_bin_trust_function_creators=ON`。

Windows可使用[scripts/test-backend.ps1](scripts/test-backend.ps1)，其只检查前置并运行Maven，不创建/清空实例。跨平台直接运行上述Maven命令。模型测试只在其专库create-drop；HTTP/导入测试使用正式SQL及事务/清理夹具，不能在业务库执行。

## 3. 新项目本地初始化与运行

先显式准备独立rbac_core及专用账号，依次执行deploy/rbac-core-schema.sql、deploy/rbac-core-runtime.sql；应用启动只validate，不建表。完整来源/五角色/幂等规则见[初始化设计](../docs/initialization.md)。通过秘密环境配置提供RBAC_CORE_DB_URL/USER/PASSWORD、RBAC_CORE_SOURCE_DIR（含三份原Excel的仓库根）、RBAC_CORE_EMPLOYEE_PASSWORD和RBAC_CORE_ADMIN_PASSWORD；可选RBAC_CORE_ADMIN_USERNAME默认admin。不在命令参数、Git或报告填写秘密。

在仓库根按顺序执行。将`APP_BACKEND_STRATEGY`设置为`ddd`可让导入、首次授权和产品使用DDD，默认是`structured`，未知值使启动失败。

```powershell
$env:APP_BACKEND_STRATEGY='structured'
# 导入组织和账号，尚不授角色。
java '-Dloader.main=com.municipal.rbaccore.bootstrap.ImportCommand' -cp backend/target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher
# .local目录须存在，预检CSV必须为新文件；生成后先核对清单。
java '-Dloader.main=com.municipal.rbaccore.bootstrap.BootstrapAuthorizationCommand' -cp backend/target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --rbac.core.preview-file=.local/initial-role-preview.csv
# 不带preview-file才执行首次批量授权。
java '-Dloader.main=com.municipal.rbaccore.bootstrap.BootstrapAuthorizationCommand' -cp backend/target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher
# 默认产品入口启动；再次启动不导入或重授角色。业务目录另按初始化设计显式运行initialize_business_catalog.py，课程仍用独立首次配置。
java -jar backend/target/rbac0-0.1.0.jar
```

HTTP默认8081，前端代理/api不变。后端29项管理操作与8项可选通用应用身份操作的机器契约见[OpenAPI](../api/openapi.yaml)。需要新空库端到端复现时，使用[accept-runtime.py](scripts/accept-runtime.py)并按[验收说明](../docs/testing-acceptance.md)提供实际私有datadir、MySQL客户端及JDK21。每次为所选 `APP_BACKEND_STRATEGY` 提供独立空库，报告会记录策略。探针拒绝非空库和额外Spring/JVM覆盖，不提供清库开关。若 Windows 上 JDK 报本机套接字 `Invalid argument: connect`，可在当前终端将 `TEMP` 和 `TMP` 指向实际存在、可写的短路径目录，再启动验收。

## 4. 当前部署边界

两个后端在服务器均采用DDD，两个Nginx公开80/8088；Java、MySQL与监控仅内网。RBAC管理端口8082提供Actuator健康与Prometheus。根Dockerfile及构建脚本使用backend目录、固定真实CloneFactory0.0.2。配置与执行见[部署指南](../deploy/README.md)，实际发布和待发布项见[验收](../docs/testing-acceptance.md)，不从源码推断线上状态。

## 5. 可选通用应用身份服务

`APP_IDENTITY_ENABLED`默认false；显式开启后提供8项老师要求的完整身份协议，资源目录来自所有已登记应用；teacher/student/课程管理员由配置角色表达，API类型要求非空有效映射。课程业务仍只在老师工程。配置、独立空库夹具及双系统验证脚本见[课程接入](../docs/course-integration.md)，不得向真实员工库导入虚构验收SQL。

课程接入需在原万人导入、五角色授权后，使用独立`scripts/initialize_course_integration.py`预览及apply，为本部员工授teacher、其余授student、合格管理员兼course-admin，并建立课程本地绑定。此工具不进入后端业务装配，不由身份开关自动执行；课程角色与RBAC管理准入仍独立。

操作查询须配置IDENTITY_OPERATIONS_FILE=file:///绝对路径/integrations/course/operations.json；JWT秘密/签发者、两种本机Origin及分页差异见[完整身份设计](../docs/backend-design.md#18-通用应用身份与课程本地接入)。身份协议由RBAC实现，课程业务仍在老师工程。

## 6. 管理与外部应用统一JWT

两类登录通过JwtIdentityStore/JwtIdentityCodec统一签发及校验HS256 JWT；管理aud为rbac，外部aud为单一登记应用，使用Bearer请求头，继续实时读取权限。IDENTITY_JWT_SECRET/IDENTITY_JWT_ISSUER同时用于两类JWT，APP_IDENTITY_ENABLED仅控制外部8项端点。未配秘密时每进程随机；无论密钥是否固定，当前进程内会话索引在重启后丢失，须重新登录。当前测试与证据从[验收入口](../docs/testing-acceptance.md)阅读。

## 7. 双策略JaCoCo复现

已完成的[完整HTML/XML报告包](../docs/reports/jacoco/20261124-performance-rbac/README.md)可直接下载查看；其来源及边界见[本轮验证记录](../docs/history/verification/authorization-loading-performance-20261124.md)。下面的方法用于重新测试和生成新的报告。

使用独立的真实MySQL测试实例及第2节六个专库，测试会建立和清理夹具、注入故障，不能使用业务库。纯领域、端口边界、文件和密码学测试与真实MySQL契约测试互补，不能将全部测试称为数据库测试。两套策略串行运行，避免覆盖同一个target或同时清理测试库。

先安装官方JaCoCo CLI 0.8.14的nodeps JAR，例如通过Maven的`dependency:get`获取`org.jacoco:org.jacoco.cli:0.8.14:jar:nodeps`。下列命令在仓库根执行，Maven使用JDK21，测试数据库环境变量已配置；Maven镜像按本机情况选择。`.local/coverage-run`必须是本次新证据目录。

```powershell
$env:APP_BACKEND_STRATEGY='ddd'
mvn -f backend/pom.xml clean package
if ($LASTEXITCODE -ne 0) { throw 'DDD回归失败' }
python backend/scripts/jacoco-evidence.py snapshot --target backend/target --strategy ddd --output .local/coverage-run/ddd
if ($LASTEXITCODE -ne 0) { throw 'DDD快照失败' }
$env:APP_BACKEND_STRATEGY='structured'
mvn -f backend/pom.xml clean package
if ($LASTEXITCODE -ne 0) { throw 'structured回归失败' }
python backend/scripts/jacoco-evidence.py snapshot --target backend/target --strategy structured --output .local/coverage-run/structured
if ($LASTEXITCODE -ne 0) { throw 'structured快照失败' }
$coverageCli="$env:USERPROFILE/.m2/repository/org/jacoco/org.jacoco.cli/0.8.14/org.jacoco.cli-0.8.14-nodeps.jar"
python backend/scripts/jacoco-evidence.py merge --ddd .local/coverage-run/ddd --structured .local/coverage-run/structured --classes backend/target/classes --sources backend/src/main/java --cli $coverageCli --java java --output .local/coverage-run/combined
if ($LASTEXITCODE -ne 0) { throw '覆盖率合并或零遗漏检查失败' }
```

工具保存测试计数、全部生产class的SHA-256及执行数据摘要；不复制可能含环境凭据的Surefire XML，不覆盖旧证据。合并时必须两份快照和当前class全部一致，使用官方CLI输出HTML/XML，再检查INSTRUCTION、BRANCH、LINE、COMPLEXITY、METHOD、CLASS全部missed=0；没有包排除或降门槛开关。

最后将同一合并exec交给pom中原有`coverage-check`，不能用单策略exec替代，也不要再执行clean或重编译。以下操作保留并恢复structured原exec：

```powershell
Copy-Item backend/target/jacoco.exec .local/coverage-run/structured-original.exec -ErrorAction Stop
try {
  Copy-Item .local/coverage-run/combined/jacoco.exec backend/target/jacoco.exec
  mvn -f backend/pom.xml org.jacoco:jacoco-maven-plugin:0.8.14:check@coverage-check
  if ($LASTEXITCODE -ne 0) { throw 'Maven六项门槛未通过' }
} finally {
  Copy-Item .local/coverage-run/structured-original.exec backend/target/jacoco.exec
}
```

100%表示当前测试执行覆盖全部JaCoCo可测计数，不证明不存在缺陷、完成所有业务场景、已通过压测或线上已发布。每次生产代码变更后须重新取得同字节码的完整双策略证据；历史报告不能直接沿用。
