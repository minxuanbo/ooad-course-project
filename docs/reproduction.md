# 组员本地复现与核对

当前基线导航（2026-11-20）：main为717345f，课程接入在codex/course-local-integration的614a0c4（草稿PR #38，尚未合入main）。课程场景请按本文末节及接入说明检出对应分支。以下万人库复现保留阶段5来源：[PR #34](https://github.com/minxuanbo/ooad-course-project/pull/34)已合入main（e521f97），当时main已包含structured/ddd双实现，该阶段文件树与验收提交83dbf17一致。组员统一从main获取并记录实际提交号；需要固定本轮验收版本时检出e521f97。只通过Git获取项目，不复制其他人的`.local`、数据库文件、node_modules、口令或预检CSV。

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

首次核对值：110有效组织、10000员工加独立admin、根直属11人；五角色人数1/64/106/72/9758，固定级别5/4/3/2/1；仅1个rbac系统及25个受控API。18业务系统仅在离线原型展示，不进入初始化。普通员工默认无RBAC管理权限，拒绝登录属于正确结果。

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

反馈请附Git提交号、操作角色/部门、操作步骤、预期/实际、HTTP状态/业务code和requestId；不附口令、Bearer、万人CSV或完整隐私数据。[阶段5交接复核](testing-acceptance.md#阶段5-交接复核2026-11-17)已记录两策略桌面浏览器及关键操作结果；阶段6约定的窄屏、键盘及写入组合已按两策略复验并合入PR #37（717345f）。本轮课程联调另见[接入复现](course-integration.md)，使用独立空库和虚构夹具，不替换本页万人初始化。

## 6. 课程系统本地联调

本轮从main 717345f检出codex/course-local-integration。按[课程接入说明](course-integration.md)配置两个数据库、两后端及两前端，老师原目录不动，使用独立源码副本和虚构账号。实际覆盖与限制见[本轮联调证据](history/verification/course-integration-generic.md)，不以原阶段6成绩替代新增接口验证。

课程联调当前由PR #38交付，复现前检出origin/codex/course-local-integration并记录实际提交；main尚不包含此次修改。先完成两项目npm ci，并安装Chrome与Python依赖`python -m pip install -r scripts/requirements-course-integration.txt`；初始化和端口按课程接入说明，不向真实员工库导入虚构夹具。

### 2026-11-21新增的R31万人接入

上面PR #38与32人夹具保留原时间语境。本轮R31在`codex/course-identity-initialization`分支，复现时获取该分支并记录实际提交；不要向32人夹具直接追加万人身份。先按[依赖安装](../deploy/README.md#本机依赖安装)安装老师干净提交40583ff的真实CloneFactory0.0.2；普通依赖和注解处理器版本均由backend/pom.xml的clonefactory.version管理。

在独立全新实例执行本文第2—3节的万人导入和五角色授权、老师课程原Flyway，再按[课程接入第8节](course-integration.md#8-本部教师与分公司学生首次接入r31)预览并显式apply。维护窗口中串行执行，完成后开放服务。当前原件的首次核对值为191教师（190员工及admin）、9810学生、1名兼任管理员；运行期不强行恢复这些人数。

本机本轮structured/RBAC8081、课程8080、支撑8085共用新实例3351；DDD/RBAC8083、第二课程8084、第二支撑8086共用新实例3352。前端5173/5174当前展示第一组。原3349/3350实例和历史日志保留。两策略用独立库，不同时写同一库；两组课程后端均为老师structured。

验证脚本`scripts/verify_course_initialization.py`会经生产HTTP创建演示学期、课程、教学班及里程碑，并临时撤回后恢复学生资格；只能指向明确的本地验收环境。参数见`--help`。本轮成绩及限制见[独立验证记录](history/verification/course-initialization-r31.md)。口令仅由本地私有说明和环境提供，不存在正式默认口令。
