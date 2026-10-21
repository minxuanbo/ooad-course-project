# 对已部署后端现场采集 JaCoCo

此流程直接调用 D 上正在运行的 `rbac_backend`，连接其现有 MySQL，
不是 `mvn test` 或临时数据库测试。独立测试报告继续保留在 `reports/jacoco/`。

## 已准备好的环境

- 后端公网 API：`http://120.46.86.147:8081/api`。
- JaCoCo 0.8.14 agent 附加在后端 JVM，只监听**容器内部** `127.0.0.1:6300`。
- 不需要开放安全组 6300，不要将它发布到宿主机或公网。
- D 的工具目录：`/opt/course-rbac/tools/`。
- D 的导出目录：`/opt/course-rbac/reports/live-jacoco/`。
- 报告 class 文件从线上同一个 `app.jar` 提取，源码与该版本核对一致。
- 当前基线业务 JAR SHA256：`267b7c0102b476e32ac845a8b785ecfa5596af23a123bfc1ef259833c93df6c3`。
- 现场开始与导出都不需要重启服务。未来升级后端代码时，必须重新准备匹配的采集镜像。

## 现场操作：按这三步进行

### 1. 在 D 的 Termius 终端开始一轮采集

```bash
sh /opt/course-rbac/tools/live-jacoco.sh start
```

看到 `Coverage counters reset` 后，再执行第二步。
它保存一份开始前的原始计数，再清零覆盖率计数；不会重置数据库、账号、角色或日志。
不要在演示结束后再次执行 start，否则会清掉刚才演示产生的覆盖率。

### 2. 在自己的电脑上运行公网 API 演示

本机需要 Python 3。在项目目录打开 PowerShell：

```powershell
cd C:\Users\21051\Desktop\ooad-course-project
python deploy/live-api-demo.py --account admin
```

提示 `Password (not printed)` 时输入该账号密码（课程默认 `admin123`），输入不回显。
脚本逐行打印请求方法、路径和 HTTP 状态，不打印令牌和人员信息。
看到 `Live API demonstration passed` 和注销成功后，可继续普通员工演示：

```powershell
python deploy/live-api-demo.py --account SG010000
```

输入该员工密码（课程默认 `123456`）。管理员接口应出现 `GET /dashboard -> 403`。
账号/密码若已变更，请用实际凭据；脚本会检查结果，不会把失败当成功。

没有本机 Python 时，也可以在 D 运行：

```bash
python3 /opt/course-rbac/tools/live-api-demo.py --account admin
python3 /opt/course-rbac/tools/live-api-demo.py --account SG010000
```

也可以用 Apifox/Postman 手动执行相同请求；开始和导出步骤不变。
登录必须用 POST JSON，不能通过浏览器地址栏 GET 测试。

脚本覆盖：未登录拒绝、登录/注销、本人权限/应用、系统进入、模块/功能目录、
允许与拒绝的操作鉴权、管理员统计/查询、普通员工管理接口拒绝。
**不改角色授权、不办理离职、不修改人员记录**；会创建并注销会话，
并新增系统进入、操作鉴权日志。这些真实审计记录保留，不自动删除。
如果需要展示授权修改或离职处置，应另行准备专用演示账号，不能对真实人员随意操作。

### 3. 回到 D 导出本轮报告

```bash
sh /opt/course-rbac/tools/live-jacoco.sh report
```

成功会打印 `HTML report` 和 `Download archive`，例如：

```text
/opt/course-rbac/reports/live-jacoco/<时间>-report-<编号>/html/index.html
/opt/course-rbac/reports/live-jacoco/<时间>-report-<编号>.tar.gz
```

用 Termius SFTP 从 D 下载打印出的 `.tar.gz` 文件，在本机完整解压，
打开 `html/index.html` 向老师展示。也可通过本机 SSH/SCP 下载：

```powershell
scp -i "$env:USERPROFILE/.ssh/course_deploy_20261020" -o "UserKnownHostsFile=$env:USERPROFILE/.ssh/course_deploy_known_hosts" root@120.46.86.147:/opt/course-rbac/reports/live-jacoco/替换为实际文件名.tar.gz .
```

包内包含 HTML 页面及资源、XML、CSV、原始 `coverage.exec`、容器 ID/镜像 ID/JAR 摘要。
这些信息用于说明报告对应的是哪一个正在运行的后端。

## 如何向老师解释结果

- “先清零线上后端 JVM 的覆盖率计数，再通过公网调用真实接口，最后导出这一时间段的执行数据。”
- “这份是线上操作覆盖率；另一份是 20 项自动化测试的覆盖率，二者没有合并。”
- 清零后没有重新初始化数据库，初始化逻辑通常显示未覆盖；启动入口也可能显示未覆盖。
- 健康检查和同一时段其他用户的请求也会计入，不能声称是严格单用户隔离数据。
- 普通读写演示覆盖率通常低于完整测试套件，不应追求通过修改正式数据补齐所有分支。
- 如果容器在 start/report 之间重启，脚本拒绝导出本轮报告；重新 start 并重做演示。
- 覆盖率保存在 JVM 内存，容器崩溃前未导出的计数可能丢失；导出文件保存在 D 宿主机。

## 维护与回滚（不属于现场必做步骤）

本次镜像：
`swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend@sha256:5b4a9585ff77368514394822375b9dc1fb879f3c7946016b182906743d1be805`。

`deploy/Dockerfile.live-jacoco` 固定了当前业务镜像，因此保持原 JAR 不变；
未来代码变化后，先构建新的正常业务镜像，再更新该文件的 application 基础镜像，
核对源码后重建采集镜像，不能继续用旧 class 文件生成新代码报告。

重新发布时，在 C 设置正确的镜像变量，并同时使用两个配置文件：

```bash
cd ~/ooad-course-project
. /root/.config/course-rbac/images.env
docker stack deploy --with-registry-auth -c deploy/stack.yml -c deploy/stack.live-jacoco.yml rbac
```

不要只用基础 stack.yml 发布，否则会移除 agent 参数。
初次启用前的后端服务配置和镜像变量已保存在 C 的
`/root/.config/course-rbac/backend-before-live-jacoco.json` 与
`images-before-live-jacoco.env`。
如需紧急撤销本次更新，可在下一次服务更新前使用 `docker service rollback rbac_backend`；
后续若已多次更新，rollback 只回退最近一次变更，应核对目标镜像和 JAVA_TOOL_OPTIONS。
agent 不可用不会影响独立测试库的 JaCoCo 流程；不要重启 Docker 或修改其他 lab 服务排查。
