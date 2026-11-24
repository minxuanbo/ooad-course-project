# 本地复现与核对

从main获取项目并记录实际提交，只复制源码，不复制他人的.local、数据库、node_modules、口令或预检CSV。当前发布范围与待发布项见[测试与验收](testing-acceptance.md)，线上状态不能从源码提交推断。

## 1. 获取正确版本及工具

```powershell
git clone --branch main --single-branch https://github.com/minxuanbo/ooad-course-project.git
Set-Location ooad-course-project
git branch --show-current
git rev-parse HEAD
java -version
mvn -version
node --version
```

要求JDK21、Maven、MySQL8.4、Node≥22.12及npm。使用`APP_BACKEND_STRATEGY`在产品、本机导入及首次授权三个入口选择`structured`（默认）或`ddd`。比较两种实现时为各策略使用单独实例和同名库。Maven实际使用的JDK也必须为21。先按[固定依赖安装](../deploy/README.md#本机依赖安装)从老师固定提交安装真实CloneFactory，不能以同名空包、BeanUtils或ObjectMapper代替。首次联网下载依赖需要访问Gitee、Maven及npm。

没有配置六个回归专库时，先只生成运行包：

```powershell
mvn -f backend/pom.xml -DskipTests clean package
```

这条命令不代表测试通过。完整回归必须按[后端测试配置](../backend/README.md#2-本地回归与隔离)准备对应六个可丢弃专库，再执行`mvn -f backend/pom.xml clean package`，不能把测试指向复现库或已有业务库。

## 2. 准备全新数据库

使用独立MySQL8.4实例和全新`rbac_core`，示例端口3307。库名被应用守卫固定；已有同名库时，换独立实例或端口，不删除旧库来腾位置。当前本机3306的其他实例不代表已为本项目准备好数据库。

在仓库根打开MySQL客户端，`-p`交由客户端提示输入，不在命令参数中写口令：

```powershell
mysql --host=127.0.0.1 --port=3307 --user=root --password --default-character-set=utf8mb4
```

在客户端内依次执行以下SQL。数据库口令占位值由复现者自行替换，只在自己的数据库控制台使用，不提交到Git：

```sql
CREATE DATABASE rbac_core CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
USE rbac_core;
SOURCE deploy/rbac-core-schema.sql;
SOURCE deploy/rbac-core-runtime.sql;
CREATE USER 'rbac_local'@'localhost' IDENTIFIED BY '<自行设置的数据库口令>';
GRANT SELECT, INSERT, UPDATE, DELETE ON rbac_core.* TO 'rbac_local'@'localhost';
SHOW TABLES;
```

应有12张表。两份SQL是新库首次结构，不是升级脚本；应用`ddl-auto=validate`、`sql.init.mode=never`，启动不会建表、导入或重授角色。其他操作系统可使用等价的环境变量和MySQL客户端命令。

## 3. 配置、导入及首次授权

回到仓库根的PowerShell，配置本机端口及账号。下列输入在内存中转换为进程环境变量，禁止输出环境变量值或把真实口令保存到脚本/聊天/日志。

```powershell
$env:APP_BACKEND_STRATEGY='structured'
$env:RBAC_CORE_DB_URL='jdbc:mysql://127.0.0.1:3307/rbac_core?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:RBAC_CORE_DB_USER='rbac_local'
$env:RBAC_CORE_DB_PASSWORD=[System.Net.NetworkCredential]::new('', (Read-Host '数据库口令' -AsSecureString)).Password
$env:RBAC_CORE_SOURCE_DIR=(Get-Location).Path
$env:RBAC_CORE_EMPLOYEE_PASSWORD=[System.Net.NetworkCredential]::new('', (Read-Host '员工统一初始口令' -AsSecureString)).Password
$env:RBAC_CORE_ADMIN_PASSWORD=[System.Net.NetworkCredential]::new('', (Read-Host '管理员独立初始口令' -AsSecureString)).Password
$env:RBAC_CORE_ADMIN_USERNAME='admin'
New-Item -ItemType Directory -Force .local | Out-Null
java '-Dloader.main=com.municipal.rbaccore.bootstrap.ImportCommand' -cp backend/target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

员工和管理员初始口令分别配置，非空且各不超过72个UTF-8字节。导入对10001个账号逐个BCrypt10加盐，耗时显著长于启动服务；等待命令成功退出，再继续，不并行重复导入。此时账号全部启用但尚无角色，不能据此登录管理端。

```powershell
java '-Dloader.main=com.municipal.rbaccore.bootstrap.BootstrapAuthorizationCommand' -cp backend/target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --rbac.core.preview-file=.local/initial-role-preview.csv
# 先核对预检文件；文件必须为新文件，已有文件不覆盖。
java '-Dloader.main=com.municipal.rbaccore.bootstrap.BootstrapAuthorizationCommand' -cp backend/target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

首次核对值：110有效组织、10000员工加独立admin、根直属11人；五角色人数1/64/106/72/9758，固定级别5/4/3/2/1；仅1个rbac系统及30个独立操作API资源。上述为RBAC核心阶段；另运行scripts/initialize_business_catalog.py预览/显式apply，登记18个PENDING业务系统和原表五角色矩阵，课程独立配置后共20项。普通员工默认无RBAC管理权限，拒绝登录属于正确结果。

## 4. 分别启动前后端

保留上面已配置环境变量的终端，启动后端：

```powershell
java -jar backend/target/rbac0-0.1.0.jar
```

默认`http://127.0.0.1:8081`，`PORT`环境变量可改端口。没有匿名首页或健康200；匿名`GET /api/me`应返回401及requestId。

另开终端进入仓库的`frontend`：

```powershell
npm ci
npm test
npm run build
$env:API_TARGET='http://127.0.0.1:8081'
npm run dev
```

浏览器访问`http://127.0.0.1:5173`。Vue开发服务仅监听127.0.0.1，`/api`代理到`API_TARGET`；后端改端口时同时修改代理。不要用8081代替前端页面，也不要把直接打开`prototype/index.html`的演示结果当真实接口结果。关闭运行终端/按Ctrl+C停止相应服务；项目没有默认后台守护或自动部署。

## 5. 建议组员依次检查

1. admin用独立配置口令登录，检查总览、部门、人员、角色、资源和日志；员工工号或手机号用员工口令定位同一账号，普通员工默认403管理准入。
2. 初始三管理角色只在本人子树查询和维护；同级领导、本人和上级只读。授下级为本级后，原管理者后续不能再改该目标，需更高级管理者处理。
3. 人员职位只读；角色停用与账号启用分别展示。角色/权限整体替换先读完整已保存集合，失败不能当空选覆盖；空选、非空共享配置都明确确认影响。
4. 部门按连续子树显示，直属/含下级人数不重计；新增类型受父级限制，只删无人无下级且非根节点，无层级调整。局部不能改删本人、上级或平级。
5. 另一个管理员撤权后，旧令牌的新请求立即按最新权限拒绝；前端403后更新实际能力并禁用失效提交，500/网络失败安全重试。
6. 日志改变筛选/批量需重新查询，同一游标快照total不变；日志查询本身的新记录可能被下一次新查询看到，不代表同快照自动增加。
7. 按[DDD隔离边界](backend-design.md#4-双实现选择方式)检查公共端口、技术适配器、结构化、DDD服务和互斥装配的区别；不要新增DDD占位类或用结构化服务套壳。

反馈请附实际提交、操作角色/部门、步骤、预期/实际、HTTP状态、业务code和requestId；不附口令、JWT或完整隐私数据。窄屏、键盘及写入组合也须按所改版本实测，历史通过不替代新验证。

## 6. 课程系统本地联调

完整边界见[课程接入](course-integration.md)。先安装Python辅助依赖、两项目前端依赖和固定CloneFactory0.0.2；使用独立新实例完成本文万人导入/授权及课程建表，再预览并显式执行课程首次身份脚本。服务必须串行初始化后开放，不向现有32人夹具追加万人身份。

建议隔离端口如下，启动前核对占用并自行配置；这不是现有进程仍在运行的声明：

| 组件 | 本机示例 | 关键配置 |
| --- | --- | --- |
| RBAC | 8081 | APP_BACKEND_STRATEGY=ddd或structured；APP_IDENTITY_ENABLED=true |
| 课程 | 8080 | APP_BACKEND_STRATEGY=ddd或structured；使用固定来源补丁副本 |
| 本机支撑 | 8085 | local_identity_gateway.py，SQLite及只读MySQL凭据放.local |
| RBAC Vue | 5173 | API_TARGET=http://127.0.0.1:8081 |
| 课程 Vue | 5174 | VITE_API_BASE_URL=http://127.0.0.1:8080；VITE_IDENTITY_BASE_URL=http://127.0.0.1:8085；VITE_MOCK_ENABLED=false |

RBAC启动前设置操作目录（缺失时operationId查询404，损坏/重复配置阻止启动）：

```powershell
$env:APP_IDENTITY_ENABLED='true'
$env:IDENTITY_OPERATIONS_FILE=([System.Uri]::new((Resolve-Path integrations/course/operations.json).Path)).AbsoluteUri
$env:IDENTITY_ALLOWED_ORIGINS='http://127.0.0.1:5174,http://localhost:5174'
```

课程后端按老师配置设置IDENTITY_BASE_URL指向8085；本机支撑命令示例：

```powershell
python integrations/course/local_identity_gateway.py --database .local/your-run/audit.sqlite3 --port 8085 --origin http://127.0.0.1:5174 --origin http://localhost:5174 --mysql-config <课程只读客户端配置绝对路径>
```

两个本机Origin还须在课程CORS_ALLOWED_ORIGINS显式允许，详见[跨域配置](course-integration.md#本机浏览器入口与跨域配置)。管理员用课程教师入口登录；初始admin在员工后创建，通常ID10001，人员按ID升序时搜索admin即可，不因排列位置改变权限。

两种登录都采用HS256身份JWT，管理aud=rbac、外部aud为登记应用，权限逐请求读取。IDENTITY_JWT_SECRET为至少256位Base64秘密，未配置则本进程随机；IDENTITY_JWT_ISSUER同时用于两类JWT，会话索引在内存，重启重新登录。勿把秘密写入参数、日志或Git。

`scripts/verify_course_initialization.py`及`verify_identity_contract.py`的参数见--help；前者会创建演示业务并临时授撤角色，只能运行于明确的本地验收库。新库按当前catalog初始化；旧回执重放需使用当时目录快照，不强行覆盖旧回执。原3349—3352验收库和日志保留，其版本证据见[验收](testing-acceptance.md)。

## 7. 服务器运行

两系统DDD的服务器部署按[部署指南](../deploy/README.md)执行，本页端口不套用于容器内网。线上入口为[课程](http://114.116.254.25/)及[RBAC](http://114.116.254.25:8088/)；口令由私有交付或Secrets获取，没有公开默认口令。现行18业务目录及部门角色已发布，原库和账号保留；实际版本及验证边界见验收入口，后续文档整理不自动触发重新部署。
