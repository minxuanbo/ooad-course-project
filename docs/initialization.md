# 原始资料与首次初始化设计

本文说明新项目数据如何建立与核对，两种策略分别执行下述规则，使用同一来源、表结构和回执格式。本地命令见[后端说明](../backend/README.md#3-新项目本地初始化与运行)。初始化采用全新库首次SQL，原Excel保留原件；历史执行结果另记于验证记录。

## 1. 输入与来源

| 输入 | 唯一用途 | 不作为来源的内容 |
| --- | --- | --- |
| 员工表10000行/六列 | 姓名、工号、组织路径、岗位、手机号及行号 | 口令、启用状态、角色分配不是原表数据 |
| 部门表111节点 | 根、父子关系与组织来源 | 负责人姓名不直接授予角色或管理权限 |
| 权限清单 | 五类角色的业务参考、系统/模块/功能原资料 | 不含HTTP模板，不把其他业务“系统管理后台”矩阵当作本RBAC管理端 |
| 部署秘密配置 | 员工统一初始口令、独立admin口令，可选账号名 | 不从旧库复用哈希/状态，不写入Git或日志 |
| 已确认的首次授权规则 | 将核实工号分到五角色 | 后续运行不再按岗位动态授权 |

原件摘要、真实行数、A1声明范围及合并单元格注意事项见[需求来源](requirements/sources.md)。读取必须按实际单元格和合并区域恢复数据，不能依据声明A1截断。姓名不唯一，按工号和核实部门定位人员。

## 2. 来源到目标

唯一根为总公司。仅将原“公司领导”节点并入总公司，其10人直接归根；其他节点及员工路径保持原意。原件不改：111组织来源→110目标部门；10000员工+独立admin→10001账号；根直属10领导+admin共11人。111来源节点可以映射同一目标根，不因此伪造另一个部门。

users.id只负责新库关联；username来自“用户名”即工号，phone来自“电话号码”，均按字符串保存。工号必填唯一、手机号可空且非空值唯一；导入另校验跨用户工号/手机号碰撞，不能只依赖两列各自唯一索引。原表无手机号的未来场景仍可用工号登录。

## 3. 阶段与事务

| 阶段 | 写入 | 核对/失败行为 |
| --- | --- | --- |
| 显式建库/结构 | DBA选择rbac_core，运行两份正式SQL | 新库隔离、utf8mb4_0900_bin；JPA validate拒绝错结构，应用不自动建旧表 |
| 资料/账号导入 | 110部门、10001启用账号、111组织/10000员工/admin来源记录 | 原件摘要、行号、六列、唯一性、树无环连通及全量归属；任何失败整批回滚 |
| 授权预检 | 仅在忽略目录输出新的CSV清单 | 预览不写角色/关联；不得覆盖已有清单或提交万人资料 |
| 实际首次授权 | 本系统目录、五角色、10001首次关联、执行回执 | 整批Service事务与根锁、完整K；失败不留下半套目录/回执 |
| 产品启动 | 不导入、不重授、不建旧表 | 默认RbacApplication，读取现有资料和最新授权 |

初始员工全部启用，不等于可登录管理端。每个账号独立BCrypt盐，员工和admin的初始口令分别配置；初始配置须非空且不超过BCrypt支持的72个UTF-8字节。admin默认账号名可配置，身份仍是普通User，不增加管理员类或字段。

## 4. 五角色首次清单

| 角色 | 已确认分类 | 人数 | 初始RBAC能力 |
| --- | --- | ---: | --- |
| 系统管理员 | 独立admin | 1 | 本系统全部维护及日志 |
| 公司领导 | 总公司/分公司直属领导 | 64 | 范围总览、人员查询/角色分配/停用/调动及部门增删改名，均受本人子树与级别保护 |
| 部门经理 | 部长、副部长 | 106 | 同上 |
| 项目经理 | 项目经理、项目副经理 | 72 | 同上 |
| 普通员工 | 其余核实人员 | 9758 | 不含RBAC管理操作，拒绝管理端登录 |

三管理角色不含角色定义写入、资源配置或日志查询，增加角色及已选权限读取；局部仅可见其子树部门角色。总公司领导因归根具有全组织人员范围，不因此变成完整维护者。首次分类按已确认来源/规则生成可复核工号清单；未明确的岗位不猜测提权，后续调动不自动按岗位换角色。

初次五角色type=SYSTEM_BUILTIN、management_level=5/4/3/2/1。组织及本RBAC首次授权与课程接入分开；不批量导入其余18个业务目录或原表业务矩阵。首次SQL直接含组织type/deleted及同级名称约束；全新部署不执行升级脚本。

## 5. 重复执行与恢复

已有有效来源及回执时按稳定编码/来源及原父ID核对，不重置口令、启用状态、部门改名、新增/删除节点、角色或资源调整；初始人数是首次核对值，不是后续强制恢复目标。原件变化、来源缺失/损坏、已有无来源数据或admin来源不一致时停止，不能自动拼接旧数据。

重复导入还要求现存账号 ID 集合与 `EMPLOYEE`、`BOOTSTRAP` 来源所关联的账号 ID 集合完全相等。仅核对来源条数或逐条原员工均存在不足以证明来源完整；额外的无来源账号返回 `SOURCE_MAPPING_INCOMPLETE`，不删除、认领或重置账号。工号/手机号碰撞仍先返回既有 `IDENTIFIER_CONFLICT`。此检查针对账号，不禁止运行期合法新增部门，也不恢复已删除部门的墓碑。

导入失败的整批事务可核对后重试；数据库结构冲突须备份并明确迁移，CREATE TABLE IF NOT EXISTS不代表升级能力。旧业务库、历史日志及原Excel保留，不复用旧user_id，也不要求把旧日志整迁新库。验收回放使用私有本机实例，生产部署使用显式已备份目标，二者不混用。

## 6. 显式课程首次接入

默认万人导入及五角色首次授权只登记RBAC。课程由独立catalog/initialization配置及脚本接入，产品启动或身份开关不自动批量授予教师、学生或管理员角色。

完成本章步骤后，总公司本部领导、职能部门/项目员工首次为teacher，分公司及下属为student；合格根直属有效五级管理员兼course-admin并建立课程教师绑定。当前原件核对191教师、9810学生及1名兼任管理员；不按首次人数恢复运行期授权。

本机使用initialize_course_integration.py，新服务器首发使用initialize_deployment.py；两库提交顺序、预览、回执及中断恢复统一见[课程首次接入](course-integration.md#4-首次身份分配与恢复)。旧32人夹具仅用于隔离回归，不覆盖原库；老师Flyway原样使用，不交付升级SQL。

## 7. 权限中文展示名称

name用于人员理解，code保持稳定，不能按中文名称鉴权。两套首次授权共用RbacCatalog.displayNames；课程名称来自独立接入目录，不在Vue建立翻译白名单。

| 稳定编码 | 中文名称 |
| --- | --- |
| rbac.login | 登录RBAC |
| rbac.systems.create | 新增受管应用 |
| rbac.systems.page.read | 查询受管应用 |
| rbac.systems.write | 维护应用资料与接入 |
| rbac.systems.remove | 移除受管应用 |
| rbac.systems.onboarding.read | 查看接入向导 |
| rbac.systems.onboarding.import | 导入接入接口 |
| rbac.systems.onboarding.check | 检查接入配置 |
| rbac.systems.onboarding.complete | 完成应用接入 |
| rbac.overview.read | 查看总览 |
| rbac.departments.read | 查看部门目录 |
| rbac.departments.write | 修改部门名称 |
| rbac.departments.create | 新增下级部门 |
| rbac.departments.delete | 删除空部门 |
| rbac.departments.tree.read | 查看部门树 |
| rbac.users.read | 查询人员 |
| rbac.users.roles.read | 查看人员角色 |
| rbac.users.roles.candidates.read | 查询可授予角色 |
| rbac.users.roles.write | 分配人员角色 |
| rbac.users.disable | 停用人员 |
| rbac.users.department.write | 调动部门归属 |
| rbac.systems.read | 查看系统目录 |
| rbac.resources.read | 查看权限树 |
| rbac.apis.read | 查询API资源 |
| rbac.resources.apis.read | 查看API映射 |
| rbac.resources.write | 维护权限属性 |
| rbac.apis.write | 启停API资源 |
| rbac.resources.apis.write | 配置API映射 |
| rbac.roles.read | 查询角色 |
| rbac.roles.permissions.read | 查看角色权限 |
| rbac.roles.create | 新建角色 |
| rbac.roles.write | 修改角色 |
| rbac.roles.permissions.write | 配置角色权限 |
| rbac.logs.read | 查询鉴权日志 |

默认34个BUTTON分别映射实际API，7个MENU显式映射本页读取API；rbac.login独立无父项。管理员初始化具备全部项，三种人员管理角色增加登录资格，普通员工仍无RBAC登录权限。已有来源回执不会因重跑自动补授新目录；既有环境的兼容处理须在另行授权发布时备份并核对，不由产品启动偷偷重授。

## 8. 业务目录与角色来源

正式部署仅初始化RBAC与课程；其他原表系统不进入正式目录或授权集合。原权限表保留为来源和离线原型，不创建虚构API。新增应用由可视化向导显式配置。


课程首次配置将3角色标为APPLICATION_PRESET并关联来源ApplicationSystem，无管理级别；五基础角色SYSTEM_BUILTIN无所属部门/应用。新建DEPARTMENT_CUSTOM必须有效ownerDepartmentId，GLOBAL_CUSTOM保留旧全局角色语义，不能根据名称猜分类。定义、选权与启停由管理员维护；领导只分配/撤回，不在初始化时任意认领既有角色。

部署离线首次阶段在RBAC核心导入后，只显式执行deploy/initialize_deployment.py配置真实课程；镜像不携带其他业务目录初始化脚本或权限Excel。交付全新库首次SQL，既有环境先备份和演练后单独兼容，不重跑首次授权，不把CREATE IF NOT EXISTS当成旧库升级。

<a id="reproduction"></a>

## 本地复现与核对

从main获取项目并记录实际提交，只复制源码，不复制他人的.local、数据库、node_modules、口令或预检CSV。当前发布范围与待发布项见[测试与验收](testing-acceptance.md)，线上状态不能从源码提交推断。

<a id="reproduction-1-获取正确版本及工具"></a>

### 1. 获取正确版本及工具

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

<a id="reproduction-2-准备全新数据库"></a>

### 2. 准备全新数据库

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

应有14张表。两份SQL是新库首次结构，不是升级脚本；应用`ddl-auto=validate`、`sql.init.mode=never`，启动不会建表、导入或重授角色。其他操作系统可使用等价的环境变量和MySQL客户端命令。

<a id="reproduction-3-配置、导入及首次授权"></a>

### 3. 配置、导入及首次授权

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

首次核对值：110有效组织、10000员工加独立admin、根直属11人；五角色人数1/64/106/72/9758，固定级别5/4/3/2/1；核心阶段仅1个rbac系统及34个独立操作API资源；课程独立配置后共2项，不导入其他业务目录。普通员工默认无RBAC管理权限，拒绝登录属于正确结果。

<a id="reproduction-4-分别启动前后端"></a>

### 4. 分别启动前后端

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

<a id="reproduction-5-建议组员依次检查"></a>

### 5. 建议组员依次检查

1. admin用独立配置口令登录，检查总览、部门、人员、角色、资源和日志；员工工号或手机号用员工口令定位同一账号，普通员工默认403管理准入。
2. 初始三管理角色只在本人子树查询和维护；同级领导、本人和上级只读。授下级为本级后，原管理者后续不能再改该目标，需更高级管理者处理。
3. 人员职位只读；角色停用与账号启用分别展示。角色/权限整体替换先读完整已保存集合，失败不能当空选覆盖；空选、非空共享配置都明确确认影响。
4. 部门按连续子树显示，直属/含下级人数不重计；新增类型受父级限制，只删无人无下级且非根节点，无层级调整。局部不能改删本人、上级或平级。
5. 另一个管理员撤权后，旧令牌的新请求立即按最新权限拒绝；前端403后更新实际能力并禁用失效提交，500/网络失败安全重试。
6. 日志改变筛选/批量需重新查询，同一游标快照total不变；日志查询本身的新记录可能被下一次新查询看到，不代表同快照自动增加。
7. 按[DDD隔离边界](backend-design.md#4-双实现选择方式)检查公共端口、技术适配器、结构化、DDD服务和互斥装配的区别；不要新增DDD占位类或用结构化服务套壳。

反馈请附实际提交、操作角色/部门、步骤、预期/实际、HTTP状态、业务code和requestId；不附口令、JWT或完整隐私数据。窄屏、键盘及写入组合也须按所改版本实测，历史通过不替代新验证。

<a id="reproduction-6-课程系统本地联调"></a>

### 6. 课程系统本地联调

完整边界见[课程接入](course-integration.md)。先安装Python辅助依赖、两项目前端依赖和固定CloneFactory0.0.2；使用独立新实例完成本文万人导入/授权及课程建表，再预览并显式执行课程首次身份脚本。服务必须串行初始化后开放，不向现有32人夹具追加万人身份。

建议隔离端口如下，启动前核对占用并自行配置；这不是现有进程仍在运行的声明：

| 组件 | 本机示例 | 关键配置 |
| --- | --- | --- |
| RBAC | 8081 | APP_BACKEND_STRATEGY=ddd或structured；APP_IDENTITY_ENABLED=true |
| 课程 | 8080 | APP_BACKEND_STRATEGY=ddd或structured；使用固定来源补丁副本 |
| 本机支撑 | 8085 | local_identity_gateway.py，SQLite及只读MySQL凭据放.local |
| RBAC Vue | 5173 | API_TARGET=http://127.0.0.1:8081 |
| 课程 Vue | 5174 | VITE_API_BASE_URL=http://127.0.0.1:8080；VITE_IDENTITY_BASE_URL=http://127.0.0.1:8085；VITE_MOCK_ENABLED=false |

RBAC启动前开启外部身份入口；操作目录由独立首次接入脚本显式导入api_operations，后续由向导维护，启动不重授或重导入：

```powershell
$env:APP_IDENTITY_ENABLED='true'
$env:IDENTITY_ALLOWED_ORIGINS='http://127.0.0.1:5174,http://localhost:5174'
```

课程后端按老师配置设置IDENTITY_BASE_URL指向8085；本机支撑命令示例：

```powershell
python integrations/course/local_identity_gateway.py --database .local/your-run/audit.sqlite3 --port 8085 --origin http://127.0.0.1:5174 --origin http://localhost:5174 --mysql-config <课程只读客户端配置绝对路径>
```

两个本机Origin还须在课程CORS_ALLOWED_ORIGINS显式允许，详见[跨域配置](course-integration.md#本机浏览器入口与跨域配置)。管理员用课程教师入口登录；初始admin在员工后创建，通常ID10001，人员按ID升序时搜索admin即可，不因排列位置改变权限。

两种登录都采用HS256身份JWT，管理aud=rbac、外部aud为登记应用，权限逐请求读取。IDENTITY_JWT_SECRET为至少256位Base64秘密，未配置则本进程随机；IDENTITY_JWT_ISSUER同时用于两类JWT，会话索引在内存，重启重新登录。勿把秘密写入参数、日志或Git。

`scripts/verify_course_initialization.py`及`verify_identity_contract.py`的参数见--help；前者会创建演示业务并临时授撤角色，只能运行于明确的本地验收库。新库按当前catalog初始化；旧回执重放需使用当时目录快照，不强行覆盖旧回执。原3349—3352验收库和日志保留，其版本证据见[验收](testing-acceptance.md)。

<a id="reproduction-7-服务器运行"></a>

### 7. 服务器运行

两系统DDD的服务器部署按[部署指南](../deploy/README.md)执行，本页端口不套用于容器内网。线上入口为[课程](http://114.116.254.25/)及[RBAC](http://114.116.254.25:8088/)；口令由私有交付或Secrets获取，没有公开默认口令。本轮按两系统范围调整目录，部门角色、人员及口令保留；实际版本及验证边界见验收入口，后续文档整理不自动触发重新部署。


## 应用后续接入

首次课程配置显式读取`integrations/course/operations.json`并写入application_profiles/api_operations，清单纳入首次回执摘要。相同回执直接跳过，不覆盖后续界面维护；改变首次输入须明确处理来源差异，不能当作运行更新命令。新应用可由“应用管理→配置接入”六步向导完成；业务后端仍须真实部署并实现对象权限，RBAC不会自动生成业务系统。
