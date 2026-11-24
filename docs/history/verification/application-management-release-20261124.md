# 应用管理与按钮置灰正式发布

2026-11-24晚，用户追加“部署吧，我先看下效果”授权后，从任务分支`codex/teacher-check-application-management`的已审核提交`25fdc214daa90c2c2b577217f66a274d64ea5dbc`发布。PR59仍为草稿，未合入main；本次部署授权不延伸为合入、删除分支或未来自动发布许可。

## 1. 发布内容和精确来源

本次上线应用管理SPA及分页、新增、资料/接入维护、逻辑移除四项操作；管理准入采用独立有效LOGIN，七个菜单显式映射读取API。页面内无权按钮保留并置灰，左侧菜单、部门范围、人员保护及后端鉴权仍按实际权限判断。课程前端保持老师原样。

本机从上述提交生成精确Git归档，在A/C核对同一归档SHA-256后解压到`/opt/ooad/releases/app59-25fdc21-20261124`。A在已有真实CloneFactory0.0.2构建基础镜像上串行构建、推送两个RBAC镜像；打包显式跳过测试，不将其称为新增回归成绩。A的main源码目录没有被改成未合入的任务提交。

| 服务 | 本次固定镜像摘要 |
| --- | --- |
| RBAC DDD后端 | `swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend@sha256:e71a72aef245b673c10a2f985e7b896f8026e6febcf1923cf1179df4bad34ae1` |
| RBAC Vue | `swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-frontend@sha256:e6c296c1d300712b725289d424b07707003aa7f7b06dc918c968462923becc90` |

后端运行容器的revision标签与源码提交一致，`/app/app.jar`的SHA-256为`403670965c6d278789c6e2dcd058d5c8662babf3e85635ccb8b4ace3f8495138`。北京时间20:27:13开始前端更新、20:27:53完成；后端20:28:24完成Spring启动，随后健康检查通过。采用固定摘要更新服务，没有重新部署整个Stack、重建课程或接入镜像，也未开放端口。

## 2. 备份及旧库兼容

旧库缺少独立LOGIN及应用管理目录，不能直接换镜像后让全部管理账号403。先只读预览，再在B的独立`rbac_app59_rehearsal`副本演练本轮追加和保留断言。正式窗口仅将RBAC后端临时缩为0，确认停止写入后，使用单一致性快照备份`rbac_core`、`course_project`、`course_audit`，包含文件内容、触发器和历史日志；B/E各保存一份并通过gzip及SHA-256校验。

备份位置为B/E的`/opt/ooad/backups/app59-25fdc21-20261124/three-databases.sql.gz`，SHA-256为`8148550e8cc37b81ad1754017d6d72d6aa47959d181c2bc5ea15c43c0af7c221`。C同名受控目录保存8项服务Spec、运行任务、上一版env及限时日志。备份及秘密未进入Git，也未将业务库下载到本机。

兼容脚本仅放本轮私有目录和服务器受控发布目录，不交付升级SQL，不改永久表结构、不重跑万人或课程初始化。单事务追加并在COMMIT前逐行核对所有旧行及预期数量；任一断言失败即回滚。重复执行因目录已存在而拒绝，不能借重跑补回人工撤权。

| 表 | 发布前 | 发布后 | 实际变化 |
| --- | ---: | ---: | --- |
| users | 10001 | 10001 | 账号、口令、部门及状态逐行保留 |
| departments / systems / roles | 110 / 20 / 8 | 110 / 20 / 8 | 原行及状态保留 |
| permissions | 1336 | 1343 | 新增5个BUTTON、2个MENU；仅将两项原默认父级移至新菜单 |
| api_resources | 47 | 52 | 新增登录和四项应用操作，原ID/字段保留 |
| permission_apis | 49 | 63 | 新增5个操作映射及9个菜单读取映射，原关系保留 |
| role_permissions | 4027 | 4037 | 新增10个关系，原关系保留 |
| user_roles | 20003 | 20003 | 未改人员角色 |
| import_sources / bootstrap_receipts | 10112 / 3 | 10112 / 3 | 原来源及首次回执保留 |
| authorization_logs | 3866 | 保留并正常增长 | 原3866行在提交前逐行核对；未删除预期拒绝或匿名日志 |

LOGIN根据旧25项有效管理API链确定需要兼容的启用角色，实际为`system-admin`、`company-leader`、`department-manager`、`project-manager`，未按角色名代替有效链计算；普通员工和课程角色未自动取得管理登录资格。应用管理四操作及两个新菜单仅补到现有五级基础角色。已存在的撤权、权限/API启停状态不重置。

## 3. 正式环境验证

完成健康检查后，115项真实公网HTTP全部符合预期，包括成功、400/401/403/409拒绝和回滚；本轮独立创建的24个验收会话均已退出清理。

- 发布前管理及课程JWT均401；管理/外部受众隔离、退出即时失效仍成立。
- 20项目录和18项PENDING业务系统保留，待接入应用登录拒绝；五类工号/手机号身份、课程teacher/student绑定及三类课程角色保留。
- 应用分页、启用筛选、搜索空结果、匿名拒绝、分页/状态边界、重复编码409、三个PATCH字段显式null400及空PATCH400符合契约；尝试停用/移除RBAC因维护者保护409，事务回滚后20项目录原样。未为验收新增或移除正式业务系统。
- 暂撤一名验收教师的teacher角色，同一课程JWT下一请求立即403；finally恢复完整原角色，同JWT立即恢复，并再次核对原集合。没有缓存JWT权限快照。
- 八项统一身份协议通过RBAC直连和课程接入路径；部门及角色成员、来源IP、原课程列表和独立业务审计查询成功。

接入自动重启后另做6次来源IP定点HTTP：管理登录与经接入登录记录同一实际公网来源，伪造的X-Forwarded-For/X-Real-IP未进入可信来源；两会话已退出。该组独立于上述115项，原始IP不进入交付报告。接入短码以实时身份投影核查，不把转接后的本人查询日志误称为上游短码路由日志。

真实Chrome以登录表单验证11组交互：管理员首次登录/退出重登、七个菜单、应用20行及新增/编辑表单、受限领导登录及无权操作置灰。34次浏览器API均200，页面异常及Vue渲染异常0；未预置JWT，没有刷新页面掩盖初次渲染问题。新增应用表单只打开后取消，未提交测试目录。截图与原始请求报告留在私有验收目录。

八项服务最终恢复7项1/1及node-exporter全局4/4，7个Prometheus采集目标均UP。服务Spec核对只有两个RBAC Image改变，其余六项Spec完全一致。身份接入服务在发布窗口因RBAC依赖健康检查失败而自动重启一次，任务记录为`unhealthy container`；其镜像/配置未变，恢复后课程链路验证通过。其余五项未修改服务的运行任务ID未变，不能把本次描述为六项任务均未重启或零停机。

启动期间曾在健康完成前探测到RBAC502、接入恢复前课程登录503，原失败报告保留，未计入115项完成后的业务验收。没有整机重启、节点故障、全量课程浏览器或新一轮性能/压测结果。

## 4. 源码证据与访问

本机仓库根实际调用入口如下，脚本及私有原始报告保存在忽略目录`.local/application-release-20261124`，SSH摘要归档在`.local/production-20261122/app59-*.log`。这是本次已执行命令的记录，不是可对正式库重复执行的初始化或升级方案；兼容脚本只允许一次追加。

```powershell
node .local/application-release-20261124/deploy.mjs source
node .local/application-release-20261124/deploy.mjs build
node .local/application-release-20261124/deploy.mjs catalog-upload
node .local/application-release-20261124/deploy.mjs catalog-preview
node .local/application-release-20261124/deploy.mjs catalog-rehearse
python .local/application-release-20261124/verify-http.py before
node .local/application-release-20261124/deploy.mjs backup-spec
node .local/application-release-20261124/deploy.mjs freeze
node .local/application-release-20261124/deploy.mjs backup-db
node .local/application-release-20261124/deploy.mjs catalog-apply
node .local/application-release-20261124/deploy.mjs backend
node .local/application-release-20261124/deploy.mjs frontend
node .local/application-release-20261124/deploy.mjs verify
node .local/application-release-20261124/deploy.mjs catalog-verify
node .local/application-release-20261124/browser-verify.mjs
python .local/application-release-20261124/verify-http.py after
python .local/application-release-20261124/verify-ip.py
```

最终HTTP、浏览器和运行核对分别对应`after-http.json`、`browser-verification.json`、`runtime-verification.json`。启动未就绪和接入重启期间的两份失败探测另存，不覆盖成成功证据。`catalog-verify`在本轮业务验收期间回读数量；旧行完整保留的断言在写事务提交前执行，不用运行后自然增长的日志数代替。

源码回归及契约证据仍见[课堂自查](teacher-check-applications-20261124.md)：双策略各344项隔离真实MySQL、360份class一致、六项100%及原门槛，54项前端单元和17项可控HTTP构建渲染，63个CloneFactory重载核对。该批结果只对应业务源码；本次115项和11组才是新增的正式环境证据，没有为记录提交重复执行覆盖率或重新部署。

桌面PPT仅更新两页的发布状态和对应备注，44页结构、所有图形及其余42页原样保留，原生渲染目检通过；Markdown讲稿、三份教师问答及提交ZIP同步实际发布状态。详设PDF的设计文字没有待发布断言，PDF及JaCoCo报告未变；ZIP仍为294项，内部摘要和禁入文件检查通过，原桌面文件先核对摘要后另存私有备份。

Apifox9042431已在源码阶段完成1.7.0、41操作/33路径/77模型同步及语义回读差异0。本轮只发布已同步的相同契约，没有新增接口或重写环境设置。

入口保持RBAC `http://114.116.254.25:8088/`、课程`http://114.116.254.25/`；账号密码不变，需重新登录。管理员从左侧“应用管理”查看本次效果。监控9090/9100仍不公开。部署及记录提交不等于PR59已合入main。
