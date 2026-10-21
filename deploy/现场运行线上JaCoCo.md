# 对已部署后端现场采集 JaCoCo

此流程直接调用 D 上正在运行的 `rbac_backend`，连接其现有 MySQL，
现场覆盖率以该后端 JVM 在演示期间的执行数据为准。

## 已准备好的环境

- 后端公网 API：`http://120.46.86.147:8081/api`。
- JaCoCo 0.8.14 agent 附加在后端 JVM，只监听**容器内部** `127.0.0.1:6300`。
- 不需要开放安全组 6300，不要将它发布到宿主机或公网。
- D 的工具目录：`/opt/course-rbac/tools/`。
- D 的导出目录：`/opt/course-rbac/reports/live-jacoco/`。
- 报告 class 文件从线上同一个 `app.jar` 提取，源码与该版本核对一致。
- 当前基线业务 JAR SHA256：`267b7c0102b476e32ac845a8b785ecfa5596af23a123bfc1ef259833c93df6c3`。
- 现场开始与导出都不需要重启服务。未来升级后端代码时，必须重新准备匹配的采集镜像。

## 现场操作：准备 → 开始 → 演示 → 导出

### 0. 自己电脑的 PowerShell：每轮创建新演示数据

需要 Python 3、OpenSSH，以及本机已经配置的 SSH 私钥和 known_hosts（不能从 Gitee 获取这些私密文件）。
先进入项目；首次安装依赖需要联网，以后无需重复安装：

```powershell
cd C:\Users\21051\Desktop\ooad-course-project
python -m pip install --target .local/demo-deps bcrypt==5.0.0
```

每轮演示开始前运行：

```powershell
python deploy/live-demo-prepare.py
```

该命令通过 SSH 连接 B，仅在 rbac 数据库事务中 INSERT 三个全新账号及一个空角色。
现有后端无新增账号/角色接口，因此准备阶段使用 SQL；业务验证阶段全部走已部署后端 API。
准备 SQL 不属于 JVM 覆盖率。随机编号避免覆盖既有数据；不会重置数据库或已有员工。

| 名称 | 用途及最终状态 |
|---|---|
| demo_<随机编号>_worker | 被授权和办理离职的演示员工；结束后 DISABLED，角色清空、会话撤销 |
| demo_<随机编号>_receiver | 接收人；保持 ACTIVE，无角色权限 |
| demo_<随机编号>_owner | 交接负责人；保持 ACTIVE，无角色权限 |
| demo_<随机编号>_role | 专用普通角色；中途授予一个已有业务权限，最后清空权限 |

三个账号使用随机生成的密码，仅存于本机 `.local/live-demo/<编号>.json` 和 `latest.json`，已被 Git 忽略。
不要上传这些文件。管理员仍用现有 admin，通过隐藏输入读取密码；不会创建额外管理员。
演示人员无真实姓名和手机号。每轮会保留演示记录和真实审计记录，因此列表人数会增加 3。

### 1. D 的 Termius 终端：开始采集

```bash
sh /opt/course-rbac/tools/live-jacoco.sh start
```

看到 `Coverage counters reset` 再继续。这里只重置 JVM 覆盖率，不重置业务数据。

### 2. 自己电脑的 PowerShell：运行完整演示

```powershell
python deploy/live-demo-full.py
```

提示 `Admin password:` 时输入当前管理员密码（课程初始值 admin123）；输入不回显。
脚本自动读取刚生成的演示账号密码，不打印令牌或人员响应内容。
请使用普通 `python`，不要加 `-O`，否则会关闭 Python 的断言检查。

脚本依次验证：

1. 清单与线上账号/角色 ID 一致，演示员工尚未离职，角色未分配给其他人。
2. 登录成功/失败、未认证 401、普通员工管理接口 403。
3. 未授权时拒绝 → 角色获得权限并分配给员工 → 允许鉴权、进入系统及查看目录。
4. 清空角色权限后立即拒绝；恢复后再撤销用户角色也立即拒绝；重新授权恢复访问。
5. 参数缺失、超长、无效日期、不存在的对象及重复交接等异常场景。
6. 发起交接后仍可鉴权 → 确认完成 → 员工停用、角色清空、旧令牌 401、重新登录 401。
7. 重复完成交接保持幂等，离职后不能重新授权，最后清空演示角色权限并注销会话。

每次请求检查预期 HTTP 状态，关键业务步骤额外检查 allowed、状态和角色等响应内容。
出现 `PASS` 且后续两次注销成功才表示完整运行结束。预期 400/401/403/404 是场景通过，不是脚本故障。
演示会真实写入授权、交接和审计数据，只变更本轮专用员工和角色；不执行真实业务系统的业务操作。

**复跑或中途失败：重新执行步骤 0 创建新的一组，再按 1 → 2 → 3。**
已离职账号不可复用，脚本会拒绝；不要手工把员工恢复 ACTIVE 或删除交接历史。
失败会保留已成功步骤的演示数据，不自动回滚整轮；失败报告不能声称全部场景通过。
不要同时运行两轮，否则 latest.json 和覆盖率采集窗口会冲突。

### 3. D 的 Termius 终端：导出

```bash
sh /opt/course-rbac/tools/live-jacoco.sh report
```

成功输出 HTML report 和 Download archive：

```text
/opt/course-rbac/reports/live-jacoco/<时间>-report-<编号>/html/index.html
/opt/course-rbac/reports/live-jacoco/<时间>-report-<编号>.tar.gz
```

用 Termius SFTP 下载打印出的 `.tar.gz`，完整解压并打开 `html/index.html`。
也可在本机使用：

```powershell
scp -F NUL -i "$env:USERPROFILE/.ssh/course_deploy_20261020" -o "UserKnownHostsFile=$env:USERPROFILE/.ssh/course_deploy_known_hosts" -o StrictHostKeyChecking=yes root@120.46.86.147:/opt/course-rbac/reports/live-jacoco/替换为实际文件名.tar.gz .
```

不要在演示后重新 start，否则会清空刚才的计数。容器重启会导致本轮失效，需用新账号重做。
包内保留 HTML、XML、CSV、coverage.exec 以及镜像/JAR/容器元数据。
旧 `live-api-demo.py` 是只读简版，完整口试使用上述 `live-demo-full.py`。

## 如何向老师解释结果

- “先清零线上后端 JVM 的覆盖率计数，再通过公网调用真实接口，最后导出这一时间段的执行数据。”
- “报告来自实际服务的 JVM，包含本轮请求执行的路径。”
- 清零后没有重新初始化数据库，初始化逻辑通常显示未覆盖；启动入口也可能显示未覆盖。
- 健康检查和同一时段其他用户的请求也会计入，不能声称是严格单用户隔离数据。
- 总覆盖率包含启动、Excel 导入与异常路径；本轮没有清库、重启或重新导入 Excel，不承诺 100%。
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
agent 不可用时先核对后端镜像、参数和容器状态；不要重启 Docker 或修改其他 lab 服务排查。
