# 双系统DDD部署与首次初始化

2026-11-22已按用户授权完成双DDD正式发布：RBAC和课程后端均为DDD、各单副本，两个正式Vue前端由Swarm内Nginx提供。配置入口为[stack.yml](stack.yml)。旧服务在备份核验后清退，原库/卷/资料保留；五个应用镜像已构建推送并按摘要部署。云端实际结果与限制见[R35记录](../docs/history/verification/ddd-production-deployment.md)，部署前本机证据另见[R34记录](../docs/history/verification/ddd-deployment-adaptation.md)。

R36用户要求集中反馈后再发布：8项已结束的初始化/排障服务已归档清理，7项正式服务不变；北京时间网页与Java日志修正只完成源码和本机验证，尚未构建发布新镜像、更新线上Stack或重启应用，不能将当前源码配置当作线上已生效。见[R36记录](../docs/history/verification/beijing-time-and-service-cleanup.md)。

## 1. 五节点及访问入口

| 节点 | 当前硬件 | 本轮部署职责 |
| --- | --- | --- |
| A course-build | 2核、约1.8GiB可见内存、40GB | 串行构建与JMeter；首发已将原结果异机归档并释放空间，原data保留 |
| B course-worker-mysql | 同上 | MySQL8.4，rbac_core/course_project/course_audit三库、独立应用账号；限1GiB |
| C course-manager | 同上 | Swarm manager、两个64MiB Nginx、320MiB Prometheus |
| D course-worker-app | 同上 | 课程DDD后端，限896MiB |
| E course-worker-log | 同上 | RBAC DDD限640MiB，独立Waitress接入/审计服务限128MiB |

仅C使用host模式发布TCP80和8088：课程http://114.116.254.25/等同于http://114.116.254.25:80/；RBAC为http://114.116.254.25:8088/。华为云安全组允许这两个入站端口，保留SSH22。MySQL3306、两个Java业务端口、Actuator及Prometheus9090都不公开。Swarm2377/tcp、7946/tcp+udp、4789/udp只允许节点内网互通，已有共享overlay为javaee-proxy。

课程Nginx将/api去前缀转发course-backend:8080；RBAC的/api与通用/auth保持前缀转发rbac-backend:8081，继续服务数据库登记的任意应用；/course-identity去挂载前缀转发course-integration:8085。课程前端编译时VITE_IDENTITY_BASE_URL使用http://114.116.254.25:8088/course-identity；课程后端运行时APP_IDENTITY_BASE_URL使用接入服务内网名。精确Origin使用不带尾斜杠和:80的课程入口，不填容器DNS给浏览器。长期使用可在两个入口接域名及HTTPS，须同步重新构建课程前端和更新Origin/issuer。

当前JWT会话索引在RBAC进程内，不缓存权限；重启后重新登录。APP_REPLICAS只允许0或1，更新采用stop-first。两策略共用SQL结构，部署只运行DDD；不将structured和DDD随机混入同一负载池。

## 2. 来源和部署补丁

老师来源固定990cea2cc3686599ef5068945706a13e7e0afc5b，使用[prepare_course_source.py](../scripts/prepare_course_source.py)导出新副本，再依次应用[原三处structured补丁](../integrations/course/course-local-fixes.patch)和[DDD及安全日志补丁](../integrations/course/course-deployment-fixes.patch)。新补丁修复DDD组长差集保存、有效已选课学生读取开放里程碑、已有CV/CTM管理员读取教学班、当前学期唯一标记释放顺序；移除两套登录结果日志、异常消息和外部HTTP查询串。相关回归一并保存在补丁中，不修改老师原目录、pom、Flyway及Vue业务代码。

[deployment_gateway.py](../integrations/course/deployment_gateway.py)由Waitress运行，身份八项实时访问真实RBAC、校验配置短码所需路由全集、不缓存授权。业务审计两项独立写course_audit；course_context账号只读课程归属。审计不是RBAC八项身份协议的一部分，也不写RBAC鉴权日志。读取仍要求AV全局权限，未扩展普通教师的对象日志范围。PyMySQL包含RSA依赖以支持MySQL8.4首次caching_sha2_password认证；不依赖认证缓存。

课程文件实际存于file_object.content的MySQL LONGBLOB中，随数据库备份，不虚设未被代码使用的上传目录。默认不新建MongoDB、Redis或Elasticsearch。

## 3. 准备发布材料（本机）

本机使用Python3.12；check-config另需PyYAML。服务器A的构建使用容器化Java21/Maven3.9.9/Node24，辅助命令不要求升级服务器系统Python。先在本机准备：

```powershell
python scripts/prepare_course_source.py --source C:/Users/21051/Desktop/course-project-mgt --target .local/course-release-r34
Copy-Item deploy/swarm.env.example .local/swarm.env
python deploy/create-secrets.py --directory .local/swarm-secrets-v1
python deploy/check-config.py --env .local/swarm.env
```

编辑.local/swarm.env中的SWR命名空间、固定镜像标签及RELEASE_ID，不能保留CHANGE_ME。没有提供或假定老师SWR仓库的写权限。生成的8项秘密分别为MySQL root、四个业务/只读账号、Base64 JWT密钥、员工初始密码、管理员初始密码；可先通过RBAC_CORE_EMPLOYEE_PASSWORD、RBAC_CORE_ADMIN_PASSWORD指定后两项。无写死默认密码，不打印值。文件留在忽略目录，安全传输后在C以Docker Secrets导入；不得放入VITE变量、镜像层、Git或普通env文件。

A先归档旧实验数据和压测结果，再清理已核对的旧镜像缓存和轮转日志；不使用volume prune，不把/opt/javaee-exp4/data当缓存。原数据库、历史日志保留。拷贝源码发布材料及已生成的课程副本到A，不携带凭据。导入所需三份原Excel另外只读放到E的/opt/ooad/sources；两目录归UID/GID10001、权限700，原Excel文件600。/opt/ooad/private-preview由UID10001写入，预检CSV不得提交。

## 4. 构建及发布前检查（A）

```bash
set -a
. .local/swarm.env
set +a
export COURSE_SOURCE="$PWD/.local/course-release-r34"
bash deploy/build-images.sh
```

构建脚本串行生成两个Java、两个前端、一个接入服务镜像；不会推送或发布。镜像打包显式跳过测试，必须另有当前源码的完整回归。A可用deploy/test-backend.sh配合ooad-java-build:clonefactory-0.0.2执行隔离MySQL测试，默认DDD；该脚本执行test及JaCoCo报告，既有100%覆盖率门槛不是本次发布条件。不要同时构建和压测。

登录有写权限的SWR后分别推送五个镜像，将生产env改成回读得到的RepoDigest；将deploy、integrations/course/operations.json和非秘密env传到C同一发布根。数据库/监控镜像也记录实际摘要。每次变更使用新RELEASE_ID，保留上一版配置及镜像摘要用于回滚。

## 5. 离线首次安装与身份绑定

以下为全新环境首发步骤，R35已实际执行；不要对已运行的正式库重复建库/导入。旧服务备份核对后才清退，不覆盖原有exp4数据卷。Stack统一命名ooad，新MySQL卷ooad_mysql_v1与旧卷分离，固定B节点。首次导入期间五个产品服务副本均保持0。

在C同样登录具有镜像读取权限的SWR，再加载非秘密env、安全导入秘密文件并启动数据库阶段。Stack和新建初始化任务均传递registry auth；不能假定A的登录凭据自动出现在C：

```bash
set -a
. .local/swarm.env
set +a
# private目录来自安全传输，目录700、文件600；只输出Docker secret ID。
for name in mysql_root_password rbac_db_password course_db_password audit_db_password course_read_password jwt_secret employee_password admin_password; do
  docker secret create "${SECRET_PREFIX}_$name" ".local/swarm-secrets-v1/$name"
done
export APP_REPLICAS=0
docker stack deploy --with-registry-auth -c deploy/stack.yml ooad
```

当前服务器Docker20的stack deploy不支持--detach选项，以上命令默认异步返回；不得把命令返回当作全部服务已健康。课程Java运行镜像另含Fontconfig、DejaVu及文泉驿中文字体，供POI计算Excel列宽；非root运行也需要这些依赖。

MySQL新卷首启通过mysql-init.sh建立三库及账号、RBAC两份首次SQL与独立审计首次SQL。产品不自动导入万人或重授角色。MySQL健康后，在B把老师原始dump安全放到新输入目录，核对摘要，执行COURSE_DUMP=/绝对路径/原始dump.sql bash deploy/load-course-dump.sh；脚本拒绝非空course_project库，保留原SQL不改写。dump已含Flyway历史，课程随后正常validate/migrate；不伪造baseline或关闭校验。

在C逐项运行，每项必须等任务Complete、核对退出码0和保留日志后再执行下一项：

```bash
bash deploy/initialize-jobs.sh import
bash deploy/initialize-jobs.sh base-preview
bash deploy/initialize-jobs.sh base-apply
bash deploy/initialize-jobs.sh course-preview
bash deploy/initialize-jobs.sh course-apply
```

import建立10000员工+独立admin，各账号独立BCrypt盐；base-preview只读生成私有CSV，base-apply执行五角色首次授权。course-preview按真实来源核对工号、所属范围、当前绑定和账号状态；course-apply才在新部署库将旧工号user_id改成RBAC数字subject，保留老师teacher/student主键与业务关联，补建admin教师绑定后发布配置角色。任一冲突失败停止，不猜测合并人员，不重置运行期授权。

Java离线任务使用tasks.mysql直达唯一MySQL任务，避免万人BCrypt计算期间超过Swarm VIP的空闲连接时限；不能通过降低BCrypt强度或放弃整批事务来加快首次导入。任务失败先核对日志、退出码和数据库事务结果，保留原任务；确需重试同一阶段时，可仅对该次命令指定新名称，例如`RELEASE_ID="${RELEASE_ID}-import2" bash deploy/initialize-jobs.sh import`。后续阶段仍使用原发布配置，不重新建库或删除失败记录。

有效身份应为190名本部员工+admin共191名教师、9810名学生、同一admin兼1名课程管理员。老师dump另有1教师和1学生演示记录，保留但不自动授RBAC角色；因此物理表行为192教师、9811学生，不能用物理人数冒充有效授权人数。相同回执重跑保留后续维护；课程绑定先成功但RBAC提交中断时可安全重试，两个数据库不伪装成分布式原子事务。

确认预览和apply结果后，在C设置APP_REPLICAS=1重新stack deploy。数据库首次脚本只对全新卷执行，不把此流程作为旧库升级工具。

Docker20以restart-condition=none的普通Swarm服务承载一次性任务，完成后仍显示0/1；须查看任务Complete/Failed与退出码区分结果。验收后先归档服务Spec、任务详情、日志并核验，再按明确名称删除这些终态临时服务，正式七服务保留。R36清理了6项初始化任务（含失败首试及成功重试）和2项空闲探针，归档位于/opt/ooad/backups/r35-496ed3e-20261122/temporary-services-20261122T044112Z.tar.gz，另有本机私有副本；不删除数据库卷、秘密或初始化结果。

## 6. 老师验收与日志

```bash
python deploy/verify-deployment.py
# C：另开一个终端，显示实际Java Logback日志。
docker service logs --follow --since 2m ooad_rbac-backend
```

待统一发布的Stack为两个Java服务显式设置LOGGING_PATTERN_DATEFORMAT，时间格式包含Asia/Shanghai及+08:00，例如2026-11-22T12:37:29.205+08:00；不依赖JVM或容器默认时区，不改变数据库/JWT的UTC语义。docker service logs --timestamps额外添加的Docker原始采集时间戳仍为UTC，不能通过Logback修改；普通课堂查看使用以上不带--timestamps的命令，取证归档可继续保留UTC采集时间。当前线上尚未应用此配置。

在RBAC浏览器依次切换工作总览、部门管理、人员管理、角色与权限、资源与API、鉴权日志。核对每次实际/api请求成功，以及对应Controller/Service的开始、完成、耗时日志；不能以Nginx访问日志或数据库鉴权日志代替。无需在前端新增“日志控制台”，页面正常加载及后端日志同步出现即可。Stack明确配置三层DEBUG、根INFO；日志不含密码、JWT、Authorization、完整参数或响应。Docker每容器10MB×5轮转，退出/更新前将验收时间段导出到受控归档目录。

还需在公网验证：admin同时登录两系统；本部领导/普通员工教师、分公司领导/员工学生；原JWT即时授撤权和应用隔离；DDD学期切换、组长重复保存、管理员教学班、有效学生里程碑；上传下载及重启后业务数据保留。Prometheus通过内网抓rbac-backend:8082和course-backend:8081；必要时用SSH隧道查看，不公开9090。服务器重启和单节点故障验证单独记录，当前单manager方案不宣称高可用。

## 7. 备份与回滚

MySQL三库统一进行受控逻辑备份（包含file_object文件内容及course_audit），归档到另一节点或本机并核对可恢复；保留原始dump、原Excel、旧库、旧实验数据及历史验收日志。镜像更新失败由stop-first/rollback恢复上个Spec；涉及数据初始化的失败不靠回滚镜像恢复数据库，使用已验证备份或另建新卷。不得用docker system prune --volumes、删除原卷或drop旧库来处理故障。恢复后重新登录，验证旧JWT失效及业务数据回读。

## 8. 本机依赖与原复现入口

## 本机依赖安装

使用JDK21、Maven及Git。2026-11-21按用户提供的老师本地spring-fundamentals核对并升级为真实`cn.edu.xmu:clonefactory:0.0.2`；源码固定提交`40583ff94764d2e758bcb13be17f5c05ea4d6352`，不是将旧构件改名。来源为[老师spring-fundamentals](https://gitee.com/mingqcn/spring-fundamentals/tree/40583ff94764d2e758bcb13be17f5c05ea4d6352/clonefactory)，源码工作树已核对干净。CopyProcessor.java在原Windows CRLF检出下的SHA-256为`161253932c623301e3f90726548e8821c87d9928ee518a47ee7b6f51ec6e3fd8`，Linux LF及去CR后的统一摘要为`60ee8327a248a96e1a9541cba4b9005f7f411d03c4f6f3af370c7df44285e831`；容器按后者校验，不改写源码。来源仓库许可为Apache-2.0，源码与jar只放忽略目录及本机Maven仓库。

以下PowerShell命令在仓库根执行，使用新的本地忽略目录；目录已存在时先核对所属及提交，不覆盖。编译器proc=full使老师处理器源码可在Java21正确编译，不改老师源码，也不连带升级本项目Boot、JDK或Lombok。镜像构建使用java-build.Dockerfile安装同一固定版本；原老师目录不修改。

```powershell
git clone --no-checkout https://gitee.com/mingqcn/spring-fundamentals.git .local/teacher-clonefactory-build
git -C .local/teacher-clonefactory-build checkout --detach 40583ff94764d2e758bcb13be17f5c05ea4d6352
git -C .local/teacher-clonefactory-build rev-parse HEAD
Push-Location .local/teacher-clonefactory-build/clonefactory
mvn -B -ntp '-Dmaven.compiler.proc=full' clean install
Pop-Location
```

确认安装BUILD SUCCESS且提交完全一致，再构建本工程。完整功能回归需先准备对应隔离MySQL专库，见[T13复现](../docs/history/verification/T13.md#2-可复现步骤)；只打包时可以显式跳过测试，但不得把该构建结果表述为回归通过。首次依赖下载需要Gitee/Maven网络。密码、token、生成报告和老师源码留在忽略目录，不提交Git。


本机组织账号导入、五角色预检/首次授权及默认JAR仍按[后端说明](../backend/README.md#3-新项目本地初始化与运行)和[复现指南](../docs/reproduction.md)执行。默认初始化仅登记RBAC；课程配置独立显式接入，不由产品启动自动触发。[旧现场JaCoCo说明](现场运行JaCoCo.md)仅为历史材料。详设Word/PDF/PPT及老师原目录保持原版。
