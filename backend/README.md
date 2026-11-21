# RBAC后端

Java21、Spring Boot3.5.6、Spring Data JPA、MySQL8.4及老师真实CloneFactory。同一工程内提供structured和DDD两套业务实现，分别通过事务脚本和领域仓储编排。完整[后端设计](../docs/backend-design.md)说明共享API、用例、基础设施边界及代码与测试归类。

CloneFactory当前固定0.0.2，源码提交40583ff94764d2e758bcb13be17f5c05ea4d6352；按[本机依赖安装](../deploy/README.md#本机依赖安装)构建老师真实源码，依赖及处理器版本由同一个Maven属性维护。

组员从[完整本地复现](../docs/reproduction.md)开始，记录实际检出分支及提交。共享/结构化边界见[DDD隔离说明](../docs/backend-design.md#4-双实现选择方式)。

## 1. 目录与构建

```text
backend/
├── pom.xml
├── src/main/java/com/municipal/rbaccore/
│   ├── api/{auth,user,department,role,resource,audit,course,shared}
│   ├── service/port/{auth,user,department,role,resource,audit,course,security,importing,bootstrap,error}
│   ├── service/structured/{auth,user,department,role,resource,audit,course,security,importing,bootstrap}
│   ├── service/ddd/application/{auth,user,department,role,resource,audit,course,security,importing,bootstrap,support}
│   ├── service/ddd/domain/{model,model/policy,repository}
│   ├── infrastructure/{persistence,security,web,logging,excel}
│   ├── config
│   └── bootstrap
├── src/test/java/com/municipal/rbaccore/{r01,r03,r04,r06,r07,r11,r13,r14,r18,r22,r23,architecture,infrastructure,shared}
└── scripts/{accept-runtime.py,tests,test-backend.ps1}
```

DDD 持久化适配器位于 `infrastructure/persistence/ddd`。`r23` 同时包含共享 HTTP 契约 `R23PrototypeContractTest` 和纯领域测试 `R24DepartmentPolicyTest`；后者检查可信根保护、空节点删除与同父名称唯一规则，不连接数据库。部门规则由 `OrganizationPolicy` 执行，五个基础管理级别的领域常量统一定义在 `Role`，应用服务负责事务与错误翻译。

先按[固定依赖安装](../deploy/README.md#本机依赖安装)准备老师CloneFactory。以下命令在仓库根执行：

```powershell
mvn -f backend/pom.xml clean test-compile
mvn -f backend/pom.xml clean package
python -B -m unittest discover -s backend/scripts/tests -v
```

完整package包含真实MySQL测试，必须先提供对应本机专库及测试账号；不能指向业务库。测试变量/库映射见下节，JaCoCo仍单独报告，当前不作为阻塞。打包结果为backend/target/rbac0-0.1.0.jar，默认入口com.municipal.rbaccore.bootstrap.RbacApplication。

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
# 默认产品入口启动；再次启动不导入或重授角色。
java -jar backend/target/rbac0-0.1.0.jar
```

HTTP默认8081，前端代理/api不变。后端29项管理操作与4项可选通用应用身份操作的机器契约见[OpenAPI](../api/openapi.yaml)。需要新空库端到端复现时，使用[accept-runtime.py](scripts/accept-runtime.py)并按[验收说明](../docs/testing-acceptance.md)提供实际私有datadir、MySQL客户端及JDK21。每次为所选 `APP_BACKEND_STRATEGY` 提供独立空库，报告会记录策略。探针拒绝非空库和额外Spring/JVM覆盖，不提供清库开关。若 Windows 上 JDK 报本机套接字 `Invalid argument: connect`，可在当前终端将 `TEMP` 和 `TMP` 指向实际存在、可写的短路径目录，再启动验收。

## 4. 当前部署边界

用户确认后续会增加服务器，云拓扑/镜像/Stack发布留待新方案。现有根Dockerfile、.dockerignore及deploy中旧容器脚本未适配本轮backend目录，不能据此宣称当前镜像或线上可用。本阶段只验证本地构建、私有MySQL和默认JAR，不操作服务器。

## 5. 可选通用应用身份服务

`APP_IDENTITY_ENABLED`默认false；显式开启后提供4项老师已有身份协议，资源目录来自所有已登记应用；teacher/student/课程管理员由配置角色表达，API类型要求非空有效映射。课程业务仍只在老师工程。配置、独立空库夹具及双系统验证脚本见[课程接入](../docs/course-integration.md)，不得向真实员工库导入虚构验收SQL。

R31需在原万人导入、五角色授权后，使用独立`scripts/initialize_course_integration.py`预览及apply，为本部员工授teacher、其余授student、合格管理员兼course-admin，并建立课程本地绑定。此工具不进入后端业务装配，不由身份开关自动执行；课程角色与RBAC管理准入仍独立。
