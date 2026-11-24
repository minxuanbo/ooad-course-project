# 历次部署证据

各记录仅对应其原日期、源码和环境；保留原成绩及限制，不作为当前版本自动通过。现行状态见[测试与验收](../../testing-acceptance.md)。

<a id="application-management-release-20261124"></a>

## 应用管理与按钮置灰正式发布

2026-11-24晚，用户追加“部署吧，我先看下效果”授权后，从任务分支`codex/teacher-check-application-management`的已审核提交`25fdc214daa90c2c2b577217f66a274d64ea5dbc`发布。PR59仍为草稿，未合入main；本次部署授权不延伸为合入、删除分支或未来自动发布许可。

<a id="application-management-release-20261124-1-发布内容和精确来源"></a>

### 1. 发布内容和精确来源

本次上线应用管理SPA及分页、新增、资料/接入维护、逻辑移除四项操作；管理准入采用独立有效LOGIN，七个菜单显式映射读取API。页面内无权按钮保留并置灰，左侧菜单、部门范围、人员保护及后端鉴权仍按实际权限判断。课程前端保持老师原样。

本机从上述提交生成精确Git归档，在A/C核对同一归档SHA-256后解压到`/opt/ooad/releases/app59-25fdc21-20261124`。A在已有真实CloneFactory0.0.2构建基础镜像上串行构建、推送两个RBAC镜像；打包显式跳过测试，不将其称为新增回归成绩。A的main源码目录没有被改成未合入的任务提交。

| 服务 | 本次固定镜像摘要 |
| --- | --- |
| RBAC DDD后端 | `swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend@sha256:e71a72aef245b673c10a2f985e7b896f8026e6febcf1923cf1179df4bad34ae1` |
| RBAC Vue | `swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-frontend@sha256:e6c296c1d300712b725289d424b07707003aa7f7b06dc918c968462923becc90` |

后端运行容器的revision标签与源码提交一致，`/app/app.jar`的SHA-256为`403670965c6d278789c6e2dcd058d5c8662babf3e85635ccb8b4ace3f8495138`。北京时间20:27:13开始前端更新、20:27:53完成；后端20:28:24完成Spring启动，随后健康检查通过。采用固定摘要更新服务，没有重新部署整个Stack、重建课程或接入镜像，也未开放端口。

<a id="application-management-release-20261124-2-备份及旧库兼容"></a>

### 2. 备份及旧库兼容

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

<a id="application-management-release-20261124-3-正式环境验证"></a>

### 3. 正式环境验证

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

<a id="application-management-release-20261124-4-源码证据与访问"></a>

### 4. 源码证据与访问

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

源码回归及契约证据仍见[课堂自查](implementation-evidence.md#teacher-check-applications-20261124)：双策略各344项隔离真实MySQL、360份class一致、六项100%及原门槛，54项前端单元和17项可控HTTP构建渲染，63个CloneFactory重载核对。该批结果只对应业务源码；本次115项和11组才是新增的正式环境证据，没有为记录提交重复执行覆盖率或重新部署。

桌面PPT仅更新两页的发布状态和对应备注，44页结构、所有图形及其余42页原样保留，原生渲染目检通过；Markdown讲稿、三份教师问答及提交ZIP同步实际发布状态。详设PDF的设计文字没有待发布断言，PDF及JaCoCo报告未变；ZIP仍为294项，内部摘要和禁入文件检查通过，原桌面文件先核对摘要后另存私有备份。

Apifox9042431已在源码阶段完成1.7.0、41操作/33路径/77模型同步及语义回读差异0。本轮只发布已同步的相同契约，没有新增接口或重写环境设置。

入口保持RBAC `http://114.116.254.25:8088/`、课程`http://114.116.254.25/`；账号密码不变，需重新登录。管理员从左侧“应用管理”查看本次效果。监控9090/9100仍不公开。部署及记录提交不等于PR59已合入main。

<a id="login-prototype-constructor-mapping-20261124"></a>

## 原型登录与新版构造器映射核对（2026-11-24）

基线为65748fd；用户要求登录页与获批原型一致、去掉演示账号区域，核对Apifox及全部设计文字，整理最终桌面材料与JaCoCo，并授权合入双仓和部署。随后明确使用CloneFactory0.0.2的不可变构造器映射，撤销原record/final一律显式构造的项目例外；真实源码版本和分层边界保留。

<a id="login-prototype-constructor-mapping-20261124-修改及行为边界"></a>

### 修改及行为边界

- 登录页复用原型双栏、文案、组织SVG及同款局部图标；无演示账号、口令填充或原型标签。真实登录、路由逐次读身份、401清会话和原型外工作区不变；保留提交等待、密码显隐、键盘及窄屏操作。
- 五种Application协议Result→record VO以真实@CopyFrom生成规范构造器映射。Controller接收copy返回的新对象；21个构造参数全部来自source，不读取target，因此可使用带目标类型的null种子。权限查询布尔值、多来源分页/集合转换及领域工厂仍明确组装，不为映射让domain依赖JPA/port或共享port依赖DDD。
- AGENTS、贡献约定、后端/领域/测试/来源说明同步新版规则。修正已登记与已接入目录、部门角色读取/授撤、身份JWT及旧阶段性注释；不改变授权规则、HTTP字段、数据库或课程源码。
- OpenAPI YAML/JSON描述同步：维护目录可选择PENDING，未登记目标才403；运行准入仍要求CONNECTED。Apifox9042431导入无失败，最终回读37操作、31路径、73模型、K17及所有语义差异0；服务器环境不覆盖。
- ER权限层级为permissions.parent_id目录树，不产生父子授权继承；角色级别、部门范围及实际API映射分开解释。

<a id="login-prototype-constructor-mapping-20261124-本轮验证"></a>

### 本轮验证

| 对象 | 实测结果及限制 |
| --- | --- |
| RED | 原登录页缺少原型入口；不可变映射测试因生成copy方法不存在而失败，均为行为失败而非编译错误 |
| 前端 | 54项单元及构建通过；5项正式构建渲染通过，含原型页面、显隐/窄屏、首次登录/重登、延迟身份、统计失败、旧会话。HTTP夹具不能算正式授权验收 |
| 架构与映射 | 干净编译后20项现有/扩展检查通过。真实0.0.2与固定来源处理器class一致；58有效重载，210处可变赋值、201组同名同类型字段无遗漏；另5构造映射21参数全验证。注解扫描3候选均为@interface声明或字符串误报 |
| 双策略 | 源码0a09ee49fb7e61339cfa3f2b719df767bea58c90，独立MySQL8.4的127.0.0.1:3354实例，仅六测试专库。DDD/structured各331项，失败/错误/跳过均0；347份生产class摘要相同 |
| 最终覆盖率 | [最终报告](../../reports/jacoco/final-rbac/README.md)：INSTRUCTION24233、BRANCH1948、LINE4854、COMPLEXITY2026、METHOD1045、CLASS264，遗漏全0；原pom六项MISSEDCOUNT=0检查通过，无新增排除或降门槛 |
| 契约 | Apifox最终回读差异0。接口说明修正不等于新增路径/字段；两套用例回归均执行同一共享Controller |

覆盖率是隔离测试执行证据，不代表正式流量全部覆盖或性能保证。前轮330项及旧性能计时仅代表各自源码；按用户要求移除旧报告副本，历史事实及其Git版本入口保留。

<a id="login-prototype-constructor-mapping-20261124-外部材料与发布"></a>

### 外部材料与发布

桌面目录`2-9_详细设计提交与汇报_20261121`已安装129页详设PDF、逐页Markdown讲稿、43页图文PPT（25主讲、18评阅附录，致谢在附录前）、提交ZIP和唯一的最终JaCoCo报告目录，共五项。`RBAC_教师问答准备_20261121`内三份Markdown同步完成。旧Word、讲稿/演示PDF、旧版演示、旧交付目录和历史报告副本已清理，全部交付文件与最终暂存件SHA-256一致。

ZIP共280项，含PDF和同内容Markdown、编辑图源、OpenAPI及首次SQL，不含Word、完整工程、Java/class/JAR或PPTX。CRC、逐文件SHA-256和正文图片引用检查通过；最终ZIP SHA-256为`87c7ed68919252e503ffc28dfa5164f8f41950daf336c8548240838db8f56e14`。PDF/PPT联系图及变更页已目检，字段附录核对72字段；问答API章节按37操作、73模型重建，逐图讲解保留。

人员维护双策略及外部投影structured三张差异顺序图补齐当前部门角色范围参数，可编辑注册表、SVG/PNG和讲解同步；仍为82组图件，共同流程不画双份。后续注释与图件调整后再次编译，347份生产class与331项测试源码摘要一致；正式结果见下节，旧版线上验收不计作本次通过。

原始日志、凭据、报告执行文件、渲染图片和文档中间件留在忽略目录.local/login-prototype-20261124，不提交私有资料。课程原目录、原Excel、既有库/卷和业务日志不属于清理范围。

<a id="login-prototype-constructor-mapping-20261124-正式发布与验收"></a>

### 正式发布与验收

PR56已合入GitHub main并同步Gitee，发布源码为`a6881befdefa253aebf837ea53c10cc33126d86c`，A的Gitee工作副本先快进后构建。发布目录`/opt/ooad/releases/final-a6881be-20261124`，备份目录`/opt/ooad/backups/final-a6881be-20261124`。发布前保存八项服务Spec、任务ID、原env及两个RBAC服务限时8秒日志；不宣称有时限的日志读取是完整归档。

Swarm按原单副本stop-first配置更新：北京时间13:38:33完成后端，13:39:53完成前端。仅下列两个Image改变，其他Spec属性相同，另六项服务的Spec和运行任务ID完全相同；未重建Stack、改库结构、重跑初始化或更新课程源码/镜像。两RBAC容器healthy，revision均为发布源码，后端运行JAR SHA-256为`89cc4d7a2a26b61952b868b881c98153fbc833a12b2adfc220445aed62a4dde0`。

```text
rbac-backend@sha256:012c1c930730bebaf5f7a9f6deba72cd49dad4ea797e5e714c8d79eea07e431b
rbac-frontend@sha256:36bcd7d2e1cc3878be2076c8ca762edc1383cc81064d1e9aca7b229449fe3e8c
```

两镜像前缀均为`swr.cn-north-4.myhuaweicloud.com/javaee_xmu/`。七个Prometheus采集目标全部UP；监控端口仍仅内网。后端进程内会话索引已重建，发布前独立管理/课程JWT均实测401，新登录正常，用户需重新登录。

正式97项业务HTTP全部符合预期：20应用/18待接入及其运行拒绝、5内置/3应用预置角色、五类账号的工号/手机号准入、课程身份绑定、受众隔离、同一课程JWT的教师资格即时撤回/恢复、两入口八项身份协议、日志来源IP、部门及角色成员查询、课程原有列表和独立业务审计。临时教师角色在finally恢复完整原集合并再次核对；未重授其他人员或清理业务数据。

辅助清理曾把协议测试已退出的两个外部会话再次按204退出，导致两项清理误报；实际业务97项均通过。只读日志确认两条401均为SESSION_INVALID，另7项定点请求验证有效会话退出204、重复退出及读取身份401，符合当前外部退出契约。26个新建验收会话中24个正常清理、2个此前已撤销；脚本已在成功退出时移除清理集合，没有为此修改生产逻辑或重跑完整业务验收。原始误报与定点解决证据均保留。

正式Chrome由登录表单进入，不预置token：新版原型页面、管理员首次进入、六菜单及右上刷新、退出重登、受限领导获准首页共9组通过，30次API均200、渲染异常0；首次进入和重登无需刷新。正式登录页截图已目检，无演示账号区域。浏览器创建的独立会话均正常退出。

本次未追加菜单性能计时、课程浏览器、整机重启或节点故障测试。覆盖率仅对应独立六测试专库，正式验收仅对应上述请求；测试MySQL3354已在核实datadir后关闭。发布后仅文档记录提交不重新构建或部署相同业务源码。

<a id="access-extension-release-20261124"></a>

## 应用目录与部门角色正式发布（2026-11-24）

用户在步骤3/4完成后追加授权：合入main、正式部署并恢复JaCoCo100%。PR49包含18业务目录、部门角色、成员查询、来源IP及原型界面；PR50修正接入镜像对两脚本和权限表的精确构建白名单。两PR均已合入GitHub main并同步Gitee，构建机Gitee工作副本快进到同一源码。

<a id="access-extension-release-20261124-实际版本"></a>

### 实际版本

发布源码`632e808f0f2b31ca74ce130e3c4e4098bfcc8334`，目录`/opt/ooad/releases/r50-632e808-20261124`。继续双系统DDD、各单副本及stop-first；三个更新状态均completed，最后接入服务于北京时间03:10:44完成。当前8项服务定义：7项replicated各1/1，node-exporter为global4/4。

| 更新对象 | 固定镜像摘要（共同仓库前缀swr.cn-north-4.myhuaweicloud.com/javaee_xmu/） |
| --- | --- |
| RBAC后端 | rbac-backend@sha256:9dd216849daee5a348e6194d9927f6a54853d97f6eda7a2c34918e876c87dc9f |
| RBAC前端 | rbac-frontend@sha256:4a35701fc658bc7ba2fe1666c44a44c8079489e54fe4697e03a995be50a68660 |
| 身份/审计接入 | course-integration@sha256:fbf3ad828ed7a2ed286a45b7b44d9fbe1e95aef21c9e961ad177416d5f50b1fa |

构建镜像与正式容器中`/app/app.jar`的SHA-256同为`7c54fecca3ce6e1875b597e4626b2c2f3239b441cfb2f4c671b44560842088ad`，后端镜像源码标签为上述提交。构建显式跳过测试，引用发布前同一生产源码的独立回归证据，不冒充构建机再次执行测试。

只改变RBAC后端、RBAC前端及course-integration三项服务定义；其余五项Spec逐项比较相同。课程后端、前端因离线窗口停止后重新启动，镜像继续为前次的a6c53fd7…、15ce7e5a…，未修改老师Vue或课程业务。MySQL、Prometheus及node-exporter没有重部署；未执行整个Stack重建。

<a id="access-extension-release-20261124-数据保留与切换"></a>

### 数据保留与切换

旧库缺少client_ip、角色来源/归属、接入状态及回执来源字段，不能用CREATE IF NOT EXISTS冒充升级。先在B节点服务器内部建立全新结构副本，按首次SQL复制旧数据；所有个人数据和口令哈希均未导出本机。演练核对10001账号及旧角色/API关系，且以现有全部12表验证原子跨库换名和外键跟随；本机3353另以合成数据复验该机制。

正式窗口停止五项产品服务写入，完成三库一致性备份，再从停写原库重新复制到`rbac_r49_staged`。保留ID、口令、手机号、人员状态/归属、原角色关系、导入来源和3186条鉴权日志；不重新初始化万人资料或覆盖课程绑定。复制过程将五基础角色及三课程角色转换为显式来源分类，旧全局自定义不猜所属部门。

RBAC原捆绑操作权限拆到25项独立操作，沿每项原API及角色关联转移；核对原实际API权限没有减少，新增通行仅为三类基础领导的部门角色只读入口。原始关联和目录在归档库保留。随后独立business-catalog-v1配置追加18项PENDING、1271项MENU/BUTTON及原表五列矩阵，保留已有课程权限；无业务API造假。

12表以一个RENAME TABLE原子切换到原`rbac_core`名称，旧表完整保存在`rbac_before_r50_20261124`，原库/卷未删除。切换后核对原账号字段/密码差异0、原人员角色关联缺失0、原日志ID缺失0、活动外键指向其他库0。现有五基础角色及三应用预置角色不改变管理级别，部门自定义由管理员定义、领导按范围及完整子集授撤。

三库备份仅存B、E的`/opt/ooad/backups/r50-632e808-20261124`，两节点gzip及SHA-256通过。备份文件摘要为`d2f5943a6760bf39b7724f972b8a104a91291b76f693345a196ea2b25bae808c`；本轮没有宣称完成异机灾难恢复演练。服务原Spec及原始日志在C同名备份目录。一次Swarm旧任务日志读取阻塞后，停止该读取并使用有时限的补充归档，保留已取得原始日志，不因等待日志而清理旧任务或数据。

回滚不能只换旧镜像后继续使用新部门角色语义：应先停写并备份切换后的新增数据，再协调旧表、旧服务Spec和镜像；不得直接覆盖发布后的合法维护或课程数据。

<a id="access-extension-release-20261124-正式验证"></a>

### 正式验证

| 项目 | 结果与边界 |
| --- | --- |
| 公网HTTP | 最终97项均符合预期，含18个待接入应用拒绝登录、五类账号工号/手机号同主体、八项协议直连及经接入、受众隔离、课程绑定/课程列表/独立审计、成员查询与日志目录 |
| 实时授权 | 仅临时撤下一名本部普通教师的teacher关联，原课程JWT下一请求403；finally恢复完整原集合，同JWT立即200，核对恢复集合相同 |
| 会话 | 发布前管理与课程JWT均401，重新登录恢复；账号和密码不变 |
| 数据目录 | 20应用、18PENDING、5系统内置和3课程应用预置，待接入业务API为0；原表矩阵五角色权限数1271/940/983/488/196 |
| 来源IP | Nginx覆盖外来转发头，按实测最小/32可信对端配置；管理端与经接入登录记录同一真实公网来源，伪造203.0.113.9/8未写入日志；不在报告公开用户公网IP |
| 真实RBAC浏览器 | Chrome7组页面/交互及1组实际目录读取共8组通过；20下拉项/18待接入、3应用预置、部门角色表单、日志IP及实际待接入权限树可读，业务API列表为空；未提交新角色或改动业务数据 |
| 课程前端 | 原始首页HTTP200、静态HTML正常；Chrome导航ERR_BLOCKED_BY_CLIENT，未取得课程浏览器交互通过结果，不用HTTP替代该结论；五类课程身份和原课程业务读取已由HTTP单独通过 |
| 监控及端口 | 7个Prometheus目标UP，监控两服务Endpoint发布端口均为空；仍仅开放原课程80、RBAC8088，9090/9100不公开，1秒采集/2秒窗口及计算配置未改 |
| Apifox | 发布前再次导出9042431主分支，1.6.0、37操作/31路径/73模型，与仓库机器契约差异0，保留真实服务器环境 |

HTTP表中的401/403包括预期拒绝，并会正常留下鉴权日志；不删除测试产生的失败/匿名记录。浏览器初次定位遇到同名导航、复合标签及在途旧列表响应，修正测试选择器和响应绑定后通过，未因此修改正式前端。课程浏览器阻断单独保留为未验证范围。

本次可信连接对端受现有Swarm VIP转换影响，实测Nginx侧为10.0.1.130/32、接入侧为10.0.1.158/32；RBAC只信任这两项，接入只信任前者。不是信任整个overlay网段或公网地址。后续代理、节点网络或入口改变时须重新核实，不将地址当作永久拓扑事实。

<a id="access-extension-release-20261124-覆盖率与交付"></a>

### 覆盖率与交付

[双策略覆盖率补齐](implementation-evidence.md#access-extension-coverage-20261124)：两策略各328项失败/错误/跳过0，347份生产class一致；指令、分支、行、复杂度、方法、类全部100%，原pom六项零遗漏门槛通过。[脱敏完整报告](https://github.com/minxuanbo/ooad-course-project/blob/65748fdf00b733221358d8012cf439de2f99e833/docs/reports/jacoco/20261124-rbac/README.md)已随main同步两仓。当前发布的后端源码/pom与该实测源码一致，后续打包白名单及记录提交没有再次改变生产Java。

原始结果位于忽略目录`.local/r49-release-20261124`及SSH帮助器的r49/r50记录，含最终HTTP、浏览器、Apifox回读、镜像摘要、来源IP布尔结论及数据保留统计。数据库备份仅在服务器，不提交凭据、令牌、员工资料、运行日志或数据库。老师原目录、原Excel及旧卷保留；外部详设本轮未改，T14/T15仍不自动关闭。

本页之后仅补充发布记录的提交不重新发布同一应用源码。100%为测试执行计数，不代表线上流量覆盖、穷尽业务组合、完成压测或高可用。

<a id="jwt-coverage-release-20261123"></a>

## JWT修复发布与剩余事项核对（2026-11-23）

<a id="jwt-coverage-release-20261123-发布范围"></a>

### 发布范围

用户在PR #47合入并同步两仓后追加授权：如有生产改动就重新部署，并核对项目剩余工作。与此前线上RBAC相比，生产改动为JWT缺少iat时保留原始必填声明事实并拒绝、两处来源JSON技术编码去重及目录分页偏移保护的等价调整。报告归档本身没有再次修改生产代码。

本次仅更新`ooad_rbac-backend`，继续DDD、单副本、stop-first及失败回滚。课程后端、两个前端、身份/审计支撑、MySQL和监控共七项服务定义逐项比较未变。老师课程前端原样，未重新初始化、重置口令、覆盖授权或改变API/SQL结构。

<a id="jwt-coverage-release-20261123-实际版本与备份"></a>

### 实际版本与备份

| 对象 | 本次实际结果 |
| --- | --- |
| 源码 | `f52b693af98c4942cb0662004f8df1bf8f2c5601`；构建机Gitee副本已快进到该提交并保持干净 |
| 发布目录 | `/opt/ooad/releases/r47-f52b693-20261123` |
| RBAC镜像 | `swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend@sha256:86937ae70eb4575897cfa5ced0e8944688fed3217ab78a4724d610b0b82129a8` |
| 镜像源码标签 | `org.opencontainers.image.revision=f52b693af98c4942cb0662004f8df1bf8f2c5601` |
| 部署JAR SHA-256 | `c22613f5eac1485c25aa3af6f150129e980355474253b8feff2f15273a9e86a1`；A镜像内与E正式容器内一致 |
| 更新完成时间 | 2026-11-23 13:05:17，北京时间；Swarm状态completed，RBAC为1/1且healthy |
| 其他服务 | 其余六项replicated服务各1/1，node-exporter为global 4/4；总计八项服务定义 |
| 回滚镜像 | 上版RBAC摘要`98ae1e1c24faf6bef359185a54375744f0a4f9ccbae4185ec512fc22e661a95e`保留 |
| 三库备份 | `/opt/ooad/backups/r47-f52b693-20261123/before-update`，B、本机和E核对gzip/SHA-256；摘要`2c69af43da713490ce5104fe98484492eeed37233397cff35141db59da9234a5` |

采用`docker service update --with-registry-auth --image <固定摘要>`更新单个服务，没有重部署整个Stack。Docker同时登记linux/amd64平台元数据；除此之外，RBAC原环境、Secrets、Configs、主机约束、资源和更新策略均与更新前规格一致。新发布目录的swarm.env记录新镜像组合，但本次没有创建其中新RELEASE_ID对应的Stack配置；运行中的Config/Secret继续复用原对象。旧镜像、完整服务规格、原始RBAC日志、原库和旧卷均保留。

<a id="jwt-coverage-release-20261123-更新后验证"></a>

### 更新后验证

| 验证 | 实际结果 |
| --- | --- |
| 双系统与身份HTTP | 39次通过：两入口、健康、管理及课程新登录、八项协议直连及经支撑、受众隔离、当前教学列表和独立业务审计 |
| 五类账号与日志 | 43次通过：管理员、本部/分公司领导及普通员工，工号/手机号同主体；管理准入、课程原始工号登录、日志系统目录及权限查询真假留痕 |
| 实时授撤权 | 9次通过：只临时删除一名演示教师teacher关联；原JWT下一请求403，finally恢复完整原角色后同JWT200，并核对恢复集合相同 |
| 合计 | 更新后共91次HTTP检查均符合预期，包含预期401/403；更新前4次另记，不重复计入 |
| 重启会话 | 更新前管理JWT和课程JWT均在RBAC重启后失效，新登录恢复；需要在两系统重新登录 |
| 健康噪声 | 10次接入服务健康探测均200，匿名`/auth/me`401总数2669→2669；原历史日志保留 |
| 监控 | 七目标均UP，四节点CPU值有限且在0—100范围；node每1秒、Java每2秒采集，规则每2秒计算；9090/9100仍不公开 |
| 时间与应用 | RBAC、课程及接入支撑均healthy；两Java日志带+08:00，原UTC存储/JWT语义不变 |
| 浏览器 | CUA导航超时并重置，未获得可用页面观察或交互结果；本轮浏览器通过数0，不以HTTP替代 |

验证请求会正常生成允许、拒绝和权限查询记录，不删除这些记录来制造“无失败日志”。两处诊断探针最初对JWT合法aud形式、Docker自动平台元数据判断过严，已修正核对方式并保留原始记录；没有因此改动生产代码，最终成功结果按上表统计。

<a id="jwt-coverage-release-20261123-覆盖率与材料边界"></a>

### 覆盖率与材料边界

本次构建仅打包，显式跳过测试，不称为构建机重新执行304项回归。发布源码的生产目录和pom与[RBAC双策略100%实测](implementation-evidence.md#rbac-jacoco-complete)对应提交`91d1b56`无差异，继续引用当时两策略各304项、六项零遗漏的证据。上述91次公网检查是另一次发布验收，不是JaCoCo测试数量；正式进程没有附加JaCoCo agent。

[归档报告](https://github.com/minxuanbo/ooad-course-project/blob/65748fdf00b733221358d8012cf439de2f99e833/docs/reports/jacoco/20261123-rbac/README.md)仍固定原实测结果和ZIP摘要，不因发布重写。课程行90.60%、分支62.17%的成绩属于[前次课程测试集](release-evidence.md#release-20261123-部署后jacoco)，本次未补齐课程100%。外部Word/PDF/PPT和教师问答本轮未改写。

<a id="jwt-coverage-release-20261123-剩余事项"></a>

### 剩余事项

| 类别 | 当前状态与处理边界 |
| --- | --- |
| RBAC当前实现 | 未发现新增的核心功能未实现项；已知iat修复已发布，生产源码未发现TODO/FIXME/未实现占位；100%不等同于没有缺陷 |
| 小组评审 | GitHub [T14 #15](https://github.com/minxuanbo/ooad-course-project/issues/15)、[T15 #16](https://github.com/minxuanbo/ooad-course-project/issues/16)仍open；已有设计/联调证据，保留小组人工评审与验收，不自动关闭 |
| 浏览器验收 | 尚无本轮完整公网浏览器交互结果，仍需实际页面及课堂演示验收 |
| 老师课程来源 | 五项缺Controller、个人制批改、学生建组归属及普通教师日志范围仍见[课程接入限制](../../course-integration.md#6-老师业务的已知限制)；不自行补写整套课程业务 |
| 课程覆盖率 | 尚未100%；属于另一系统的独立测试工作，本轮只完成RBAC范围 |
| 可选运行增强 | HTTPS、强制首次改密、多实例共享会话/高可用、压测和恢复演练未作为当前交付完成项；需要另行明确目标后实施 |

原始证据位于忽略目录`.local/jwt-release-20261123`及SSH帮助器的`.local/production-20261122/jwt47-*`记录。包括源码与镜像、三库备份摘要、更新前后规格、HTTP结果、恢复前角色集合、运行核对、健康噪声和浏览器超时记录；不提交凭据、原始日志或数据库备份。

<a id="release-20261123"></a>

## 2026-11-23 发布核对与验证

用户明确授权本轮检查、更新桌面设计及问答、合入main、同步GitHub/Gitee、更新服务器源码并重新部署，部署后执行JaCoCo。保留原Excel、老师原目录、旧库/卷及原始日志；不重新初始化万人或重置运行授权，不关闭T14/T15。

<a id="release-20261123-发布前核对"></a>

### 发布前核对

- 代码候选`5091bd0`，基于PR44。OpenAPI1.5.1为37操作、31路径、71 Schema、K17；本轮回读Apifox9042431语义差异0，保留用户在线运行配置。
- 实际线上单人即时撤权/恢复9次HTTP通过：保存一名演示教师原角色集合，仅移除teacher关联，同一课程JWT下一请求403；finally恢复完整集合，同JWT恢复200，并核对角色集合一致。未改变共享角色权限。
- 线上匿名`/auth/me`401曾有2575条，主要由接入服务每30秒匿名健康探测产生；另有11条匿名`/api/me`。改为内网8082 Actuator健康状态及审计MySQL连通性，保留历史日志和真实未认证访问记录。
- 健康探测测试先RED（3次探针调用实际0），再GREEN；本轮Python39项全部通过，包括真实隔离MySQL审计并发/重启回读，失败、错误、跳过0。前端32项及构建通过。
- CloneFactory0.0.2真实处理器来源固定40583ff；普通依赖/处理器版本一致。53组注解与53生成重载对应（49 copy、4 copyNotNull），无空体、无可变同名同类型字段遗漏；record/final仍显式构造。本地实际构件与真实源构建的处理器class摘要相同。
- Chen ER目检与六实体、八联系及九核心表一致；三支撑表不进入核心ER。37操作及两类离线入口逐项追溯，保留16对类图及24对差异顺序图。修正包图实现箭头、适配器虚构字段、日志systems遗漏及包框端点；规范/需求双轴复核均无遗留问题。
- 设计正文去掉参考项目架构来源、“第一个纵向切片”和旧任务来源，技术依赖及独立课程来源证据保留。
- A的Gitee副本为`/root/ooad-course-project`，旧JaCoCo报告为未跟踪文件，更新时保留。Gitee/main是候选提交祖先，不需强推。

原始回读、测试、截图和受控备份位于忽略目录`.local/release-20261123`及既有私有服务器日志目录。以下记录实际发布和部署后验证，与发布前检查分开。

<a id="release-20261123-实际发布与来源"></a>

### 实际发布与来源

PR44合入`5b5fadb552ce4f18331846e28ecf2fe5bbc0dd66`后部署到`/opt/ooad/releases/r38-5b5fadb-20261123`；PR45合入`6465de5de175038fa3bb80d91dc621fab0875dbc`追加node-exporter主机名配置，Java源码未变。两系统均为DDD，RBAC和课程后端各单副本、stop-first。当前7项replicated服务各1/1，node-exporter global 4/4；B/C/D/E均Ready、Active。

重新获取老师Gitee远端，仅master，最新仍为`990cea2cc3686599ef5068945706a13e7e0afc5b`（2026-11-21 22:51:28 +08:00），没有后续修复。独立副本保留已确认后端补丁；课程web/src的95个文件与老师固定来源零差异。普通教师无管理权限时隐藏管理组是原始TeacherLayout及其测试的规定，不实施“菜单全部显示并置灰”改写。原目录不变，未切换structured，因为当前已修复DDD业务链通过。

| 镜像 | 实际SHA-256 |
| --- | --- |
| RBAC backend | `98ae1e1c24faf6bef359185a54375744f0a4f9ccbae4185ec512fc22e661a95e` |
| RBAC frontend | `c10144700c42f055925ae145aed70a7d425242a7bb9ec3289737f87a72f1c8c8` |
| course-integration | `276a6d62d45fa42cc7fe93a2b8e353753a25bd0caafc801e9ed553f14b9d9360` |
| course backend（复用） | `a6c53fd7f3fefb7710e83f1086c5ff242668e47e8a95cb0b38bfcd10679e9318` |
| course frontend（原始源码，复用） | `15ce7e5a9302f64cad74e4a43d21c97e288d1832c16a02cb3e222640f88f6e8e` |
| MySQL | `a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902` |
| Prometheus 3.7.3 | `6573196e5dbe834779ac4d34cb053113cc502a69e9e20ba3be96d37f33e81545` |
| node-exporter 1.10.2 | `7bcf2839f207d926b908cd3c566c9f1577efb72268062be0c96cd3b17a5cb283` |

更新前备份三库，B、本机和E均保存并核对gzip及SHA-256：`511fbab033f263ca5d5af1ee484b35352dff074eb88eca425a74b03224819460`。服务器备份位于`/opt/ooad/backups/r38-5b5fadb-20261123/before-update`。C保存服务规格，D/E归档应用原始日志；原库、旧卷和原日志保留，不重新执行万人导入或覆盖运行授权。

首次Stack更新中MySQL配置重建造成短暂DNS不可用，课程启动出现`UnknownHostException: mysql`并回滚；待数据库healthy后重新更新，课程服务最终completed。归档旧Docker service logs读取器曾挂起，停止该只读reader后改用节点docker logs完成归档，已捕获字节保留；不把第一次回滚或归档reader退出状态当作成功发布证据。

<a id="release-20261123-部署后线上验证"></a>

### 部署后线上验证

| 验证 | 本轮实际结果 |
| --- | --- |
| 公网业务HTTP | `http-verification.json`：56次通过，含八项协议直连/经支撑、课程DDD业务链和上传下载；复用已有演示对象 |
| 实时撤权/恢复 | `after-realtime.json`：9项通过，仅临时删除一名演示教师teacher关联，旧JWT下一请求403，finally恢复原集合后同JWT200，集合一致 |
| 五类账号及日志 | `account-verification.json`：43项通过，工号/手机号同主体、管理准入、课程原始工号登录、systems目录及PERMISSION_CHECK真假留痕 |
| 合计口径 | 上述部署后共108次HTTP检查，包含预期拒绝；发布前9次另记，不重复计数 |
| 健康噪声 | 10次正式`/healthz`均200，匿名`/auth/me`401计数2664→2664；保留历史拒绝及真实匿名请求日志 |
| 时间 | 两Java当前日志实际带+08:00；前端构建含Asia/Shanghai显示/筛选。Docker原始采集时间戳与UTC存储/JWT不变，Python接入技术日志仍UTC |
| 监控 | Prometheus自身、两Java、四节点共7目标全部UP；node每1秒、Java每2秒，两组规则每2秒，CPU回看2秒；四实例CPU为有限0—100值 |
| 主机名与端口 | 实测四个Swarm主机名，9090/9100仅overlay内网，不发布宿主机及公网；A不是Swarm节点 |
| 浏览器限制 | 真实Chrome与CUA内置浏览器均被客户端`net::ERR_BLOCKED_BY_CLIENT`拦截；本轮公网浏览器通过数为0，未绕过客户端限制，未借用旧版本浏览器成绩 |

课程原始Vue本轮56项测试通过（34 files）；RBAC Vue32项测试及构建通过，Python辅助39项通过。HTTP不能替代浏览器视觉/交互验收；本轮未追加整机重启、节点故障、高可用、负载或长期监控存储测试。来源已有业务缺口继续见course-integration.md，不宣称课程全功能完成。

<a id="release-20261123-部署后jacoco"></a>

### 部署后JaCoCo

构建机A首次在800MiB测试容器中被137终止，78项后未完成，该次不计通过或有效覆盖率。随后在本机隔离MySQL3353的六个RBAC测试专库及course_test完成运行，与正式业务库分离；原3349/3350及业务数据未动。

使用JDK21和JaCoCo0.8.14：RBAC分别设置`APP_BACKEND_STRATEGY=ddd/structured`运行`mvn -f backend/pom.xml clean test jacoco:report`；两策略各215项，失败/错误/跳过均0。339份class文件逐一SHA-256一致后，使用官方JaCoCo CLI merge并基于同一编译产物生成合并报告。每个单策略报告仍保留，分母包含未启用的另一套实现；合并报告也未排除另一策略或未执行生产类。

课程独立副本在不改pom的前提下以`-javaagent`附加同版JaCoCo，运行`clean test`及显式report：499项，失败/错误/跳过均0。测试副本与本轮准备副本501份生产文件内容一致；11处原字节差异均为LF/CRLF换行差异。该报告为课程既有测试集实际执行范围，不能说成499项公网DDD验收；部分课程DDD当前用户及HTTP客户端配置未覆盖。

| 报告 | 行覆盖/遗漏 | 行覆盖率 | 分支覆盖/遗漏 | 分支覆盖率 |
| --- | --- | --- | --- | --- |
| RBAC DDD单策略（包含全生产代码分母） | 3092 / 1342 | 69.73% | 968 / 708 | 57.76% |
| RBAC structured单策略（同分母） | 2740 / 1694 | 61.80% | 941 / 735 | 56.15% |
| RBAC同字节码合并 | 4265 / 169 | 96.19% | 1390 / 286 | 82.94% |
| 课程测试集 | 7136 / 740 | 90.60% | 1338 / 814 | 62.17% |

RBAC合并报告另有指令遗漏891、复杂度遗漏298、方法遗漏25、类遗漏3；未执行类为三个main/命令入口。插件计数口径为256个有可测指令的类，不能与339个编译文件或XML中含接口的节点数混称。

正确门槛检查时先备份target/jacoco.exec，将合并exec按摘要核对后置于插件实际读取位置，再离线执行`org.jacoco:jacoco-maven-plugin:0.8.14:check@coverage-check`，最后恢复原文件。结果退出1，六项MISSEDCOUNT=0门槛均未达到；不改阈值、不排除生产类、不把功能测试成功称为100%覆盖。先前`-Djacoco.dataFile`未覆盖check插件读取位置的一次结果仅作诊断，不作为合并门槛证据。

HTML/XML/exec、摘要及测试计数保存在私有目录并随桌面材料提供，不提交数据库、凭据、Surefire XML或完整调试日志。可复现步骤见[JaCoCo指南](../../../deploy/现场运行JaCoCo.md)。

<a id="release-20261123-外部设计与问答交付"></a>

### 外部设计与问答交付

用户指定桌面目录已同步同版详设PDF/Word及提交ZIP，详设119页；PPT43页（25页主讲、18页评阅附录）、逐页讲稿25页，封面无开场白、目录无讲解、致谢在附录前。保留蓝绿图文版式，页面可读正文约5249字符；20分钟为建议，未由汇报人完成真人计时。

源包保留82组当前包图/类图/差异顺序图、9组异常片段、前端及共享日志3组共同图、7张界面线框、Chen源图、首次SQL及同版OpenAPI。登录提示和日志系统下拉线框已同步，Word全部嵌入PNG均有对应源文件。字段表固定列宽并避免孤立表头；原生Word/PPT导出后渲染目检。ZIP515项通过CRC及manifest逐文件SHA-256核对，压缩包摘要`281286dec1497124fda0265a65c8f83c3b21b29b86aac5709e41da3356cefd14`。

教师问答保留原66问，补充用户11问及原始课程前端菜单说明；配套覆盖全部37项API、24对差异顺序图逐消息及32类图/2包图，项目链接核验无缺失。当前测试、覆盖率和浏览器限制已写入问答，不用旧成绩代替。详设正文不写Git分支/PR进度或参考Gitee架构来设计。三个问答Markdown仅放指定桌面目录，未纳入项目受控文件。

两个桌面目录共3396文件逐一摘要核对，原件已先完整备份；仅替换已备份且未被用户修改的旧文件，未知文件保留。JaCoCo完整报告另放桌面，不混入详设提交ZIP。本机本轮启动的隔离MySQL3353已恢复停止，数据保留，其他实例不动。

<a id="ddd-production-deployment"></a>

## R35 双DDD服务器部署（2026-11-22）

<a id="ddd-production-deployment-1-授权、来源与边界"></a>

### 1. 授权、来源与边界

用户明确授权正式部署两系统并清退现有服务，随后要求初始化口令便于输入。沿用R34已审核的部署适配，应用源码为496ed3e0b69d59e775f8c61b53d1c47e7fee0a67，分支codex/ddd-swarm-adaptation；老师课程源码固定990cea2cc3686599ef5068945706a13e7e0afc5b，使用独立副本及两份已交付补丁，老师原目录不改。详设Word/PDF/PPT保持原版，T14/T15不关闭。

本轮正式运行两套DDD，各单副本、stop-first；没有将structured部署到同一负载池。37项业务API未改变，未产生需要同步Apifox的契约差异。口令由Docker Secrets提供，真实值仅交付用户及保存在受控忽略目录，不构成项目默认密码。

<a id="ddd-production-deployment-2-旧环境保留与清退"></a>

### 2. 旧环境保留与清退

先保存五节点配置、旧容器日志和C的九项原服务Spec。B原MySQL全库一致性逻辑备份经gzip完整性和SHA核验；E原Mongo停止后冷备并核验。原MySQL/Mongo卷与其他旧卷全部保留，没有volume prune或drop旧库。

A原压测results/runtime压缩归档，约3.8GB，传至E并核对两端SHA256为68f0a72e5ee906117e4e45f82751f21c535b6e28dfbe82302971a70ff51fcba9，E解压完整性检查通过后，才清理A重复归档及原results/runtime。A的约14GB原data目录完整保留。传输临时密钥限定内网来源和固定接收命令，传输后已撤销并删除；用户私钥未上传。

随后清退原exp4/exp4mon九项Swarm服务和A独立node-exporter。保留现有B/C/D/E集群、唯一manager及javaee-proxy网络；A不加入运行集群。新数据卷ooad_mysql_v1与原exp4卷分离。各节点备份根为/opt/ooad/backups/r35-496ed3e-20261122，发布根为/opt/ooad/releases/r35-496ed3e-20261122。

<a id="ddd-production-deployment-3-真实构建与发布"></a>

### 3. 真实构建与发布

Docker Hub直连在服务器受限，运行时基础镜像经DaoCloud镜像源拉取并核对摘要。Java21基础仍固定老师SWR镜像；Maven3.9.9和老师真实CloneFactory0.0.2在A容器内构建，固定来源提交40583ff94764d2e758bcb13be17f5c05ea4d6352，库自身5项测试通过。

Linux实建发现两个问题并修复：CopyProcessor摘要按去CR后的固定LF字节校验，消除Windows检出换行差异；Maven下载从中途停滞的BusyBox wget改为带失败退出、超时及重试的curl。归一化只用于校验管道，不修改老师源码。修正通过规范轴和需求轴审核，各0项遗留。

五个应用镜像全部在A串行构建成功，两个前端完成正式生产构建，课程Vue同时通过类型检查。Java产品打包显式跳过测试，不将构建冒充后端完整回归；本轮未改变Java业务实现，原R34回归单独追溯。镜像推送现有SWR命名空间javaee_xmu后回读RepoDigest，C部署配置使用摘要固定版本。

| 镜像 | SHA256摘要 |
| --- | --- |
| rbac-backend | 1288c148bcc4b864829ada0a1a07f1e710554e4e7f59f019e288dd4a58102ea2 |
| rbac-frontend | 40d77f0608ae7948dad29cae224f745ba8f1aa12f7a882603178d7dab646b85f |
| course-backend（含字体运行依赖） | a6c53fd7f3fefb7710e83f1086c5ff242668e47e8a95cb0b38bfcd10679e9318 |
| course-frontend | 15ce7e5a9302f64cad74e4a43d21c97e288d1832c16a02cb3e222640f88f6e8e |
| course-integration | e6392ace29fc8f9a31ee6bc77fb65b1ef01a942ca1ec0102858f4084e9963db8 |
| MySQL8.4.13 | a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902 |
| Prometheus | 6927e0919a144aa7616fd0137d4816816d42f6b816de3af269ab065250859a62 |

Docker20的stack deploy没有--detach选项，本轮使用该版本支持的默认异步部署；一次性任务使用service create --detach并单独等待Complete及ExitCode=0。

<a id="ddd-production-deployment-4-新库及首次初始化"></a>

### 4. 新库及首次初始化

MySQL健康后另用真实SQL确认rbac_core的12张表及course_audit表就绪，五项产品服务保持0副本。老师原始dump摘要82d9f8f092d2fbac9193ba5573f407cf7f644bca8f52f39a48e3f5733057061b复核通过，只导入新的空course_project库；没有关闭Flyway校验或伪造baseline。

首次import计算全部账号口令后，出现Communications link failure/Connection reset，连接已空闲1118秒；MySQL wait_timeout为28800秒，users/import_sources查询均为0，整批未留下部分结果。保留原失败任务及完整私有日志，Java离线任务改为tasks.mysql直连唯一MySQL任务，避开VIP空闲连接；使用import2后缀的新任务重试，不降低BCrypt强度或改动Java事务。此修正经两轴增量复核，各0项遗留。

另用两个同节点、同镜像、同账号的只读SELECT 1连接做真实对照：均成功建立连接后空闲960秒；mysql虚拟IP再次查询触发OperationalError，tasks.mysql直连再次查询成功。两项探针符合预期，支持上述故障定位，不把重试本身当作修复证据。

重试import、base-preview、base-apply、course-preview、course-apply五项均Complete且ExitCode=0。首次导入得到110组织、10000员工加admin；五角色人数为1/64/106/72/9758。课程首发原位校正10000条工号绑定，新增admin教师绑定，保留两条额外演示对象。

逐人读库核对10001份课程角色及数字subject绑定全部正确；10001个BCrypt10哈希各不相同。有效教师191、学生9810、兼任课程管理员1，物理教师192/学生9811，正式应用2。没有向正式库导入32人虚构夹具。

<a id="ddd-production-deployment-5-公网与运行验收"></a>

### 5. 公网与运行验收

七服务按既定节点全部1/1：B数据库，C两个前端及Prometheus，D课程DDD，E RBAC DDD及独立接入服务。所有已配置容器健康检查通过，两个Prometheus目标均up、lastError为空。运行中实测MySQL约470MiB、课程约353MiB、RBAC约337MiB、接入约23MiB；各节点仍有约0.9GiB以上可用内存，未发生OOM或应用异常重启，不据此宣称性能容量或高可用验收。

公网RBAC为http://114.116.254.25:8088/，课程为http://114.116.254.25/。入口8项检查通过，匿名业务链均401；Java、数据库及监控未直接发布公网端口。

真实教学场景经现有生产API创建：新学期、开放的小组制课程、教学班、本部领导/普通员工任教、两名分公司人员选课、小组及作业里程碑。学生上传文件、教师批改已成功，之后完成锁拒绝再次提交409 MILESTONE_DONE_LOCKED，重跑验收读取原作业继续验证，不放宽业务锁。演示对象的私有ID与体验账号留在忽略目录。

首轮成绩导出返回500，实际日志为Fontconfig head is null。课程运行镜像补充fontconfig、DejaVu及文泉驿中文字体并构建字体缓存，保留非root10001运行；更新前后JAR的SHA256均为165021fcfc983f43a8956addd7ec3af8e95efd01ea3482da0ace2b854489e8ae。新镜像真实Excel导出200且内容为XLSX，未修改老师业务源码或POM。

最终直连真实HTTP共62项全部符合预期，覆盖同一admin双系统管理、四类员工代表按教师/学生登录、统一身份八项直连/接入层、无权限快照JWT、受众隔离、独立退出、CORS、当前学期反复切换、组长重复保存、教学班/里程碑对象权限、已有文件/成绩回读、Excel导出及原JWT即时撤权/恢复。首次失败及后续重跑报告保留，不把失败尝试合计为最终通过数量。

真实Chrome六个RBAC菜单均HTTP200，逐次对应容器Controller和DDD Service Logback。普通Chrome另实测admin登录RBAC总览及课程教师入口，显示课程/学期/教师/日志管理菜单。课程三角色Playwright尝试受本机客户端代理拦截，未计为浏览器通过；一次HTTP502未到达服务器Nginx，最终HTTP探针显式直连授权服务器后完成，不修改浏览器安全设置。

独立接入服务、课程DDD、RBAC DDD分别强制更新重启后，新增10项真实HTTP全部通过：接入重启仍接受原会话且审计总数不回退；课程重启后文件字节及成绩原样回读；RBAC重启后原管理/应用JWT均401，重新登录后的两系统功能正常。未将应用重启等同于服务器整体重启。

完成验收后对rbac_core、course_project、course_audit做single-transaction三库备份，约1.3MiB压缩包，SHA256为0259aabc240aab26078b35e2a6c5e0f7b5ed3d6bf496f655a2833b8d9509d877。B、本机私有目录及E均保留副本；E回读SHA和gzip完整性均通过。备份位于上述备份根的production-after-verification，目录700、文件600；未把完整恢复演练记作已通过。

从C回读实际非秘密swarm.env后，通过七服务、两DDD、单副本、固定镜像及公网Origin/配置引用校验；A/C发布目录的三项最终构建/初始化定义摘要一致。真实密码和体验账号只交付用户及私有access.private.md，不进入Git或PR。

<a id="ddd-production-deployment-6-保留限制"></a>

### 6. 保留限制

单manager、后端单副本及进程内JWT索引的边界不变，应用更新使用stop-first；本轮不宣称服务器整体重启、节点故障切换、负载性能或高可用通过。老师已有5项缺Controller、个人制批改409及学生建组归属等来源限制仍按[课程接入](../../course-integration.md)披露，不将部署成功等同于老师全部业务已实现。

<a id="beijing-time-and-service-cleanup"></a>

## R36 北京时间展示修正与临时服务清理（2026-11-22）

<a id="beijing-time-and-service-cleanup-1-授权与执行边界"></a>

### 1. 授权与执行边界

起点d3ca697e832914da4ac11e02781fccc6c7f49f8e。用户先要求定位网页UTC时间并暂停重新部署，随后明确授权清理不需要的0/1服务，并希望服务器应用日志也显示北京时间。此次只执行8个终态临时服务清理；时间修正完成源码及本机验证，等待用户继续反馈后明确统一发布。没有发布新镜像、更新线上Stack、重启正式应用或修改服务器时区/数据库数据。老师原目录、详设外部文件、T14/T15和草稿PR边界保持。

<a id="beijing-time-and-service-cleanup-2-原因及修正"></a>

### 2. 原因及修正

截图2026-11-22T04:28:10.754Z对应北京时间12:28:10.754。只读核对客户端、E主机/容器、B主机/数据库UTC时刻一致；E主机本地+0800，Java容器与MySQL为UTC。不是8小时时钟漂移。RBAC以Clock.systemUTC/Instant记事件，JPA/JDBC按UTC存储，Controller用Instant.toString导出带Z时间，原Vue直接显示该字符串。

网页列表、详情和总览活动现显式按Asia/Shanghai格式化为北京时间，保留毫秒和原UTC机器值/悬浮提示。筛选标签明确北京时间，使用显式+08:00转换UTC，不再依赖浏览器时区；非法日期和日期滚动拒绝。API的UTC Z格式、数据库、JWT时效、Clock及两策略业务实现不改，37项契约未变，不新增Apifox写入。

两个Java服务的Stack环境新增LOGGING_PATTERN_DATEFORMAT=yyyy-MM-dd'T'HH:mm:ss.SSSXXX,Asia/Shanghai。两系统都使用Boot默认Logback格式，课程XML亦引用默认格式；无需改老师源码或JVM全局时区。未来发布后应用消息时间应为2026-11-22T12:37:29.205+08:00。Docker --timestamps额外添加的采集时间仍为UTC，课堂查看命令改用不带该选项；归档可继续保留原始UTC采集时间。

<a id="beijing-time-and-service-cleanup-3-实际清理与证据"></a>

### 3. 实际清理与证据

清理目标固定为2项idle-probe和6项init服务，包括第一次失败import及成功import2。先核对服务名、restart-condition=none、所有任务终态，再保存完整Spec、ID/版本、任务列表/详情及日志；C与本机归档SHA256均为620f6af19fa8e861cdc6e3a2d963b57b44819111ddfeed3501d5e28430ed58d8，并验证8组完整记录。

随后重新核对ID/版本及终态，只删除这8个服务定义。清理前后正式七服务列表完全一致，全部1/1；没有删卷、Secrets、业务数据或初始化结果。归档保存在C的/opt/ooad/backups/r35-496ed3e-20261122/temporary-services-20261122T044112Z.tar.gz及本机忽略目录。旧失败证据仍可追溯，不能因服务列表变干净宣称原失败不存在。

<a id="beijing-time-and-service-cleanup-4-本机实际验证"></a>

### 4. 本机实际验证

- TDD RED：原查询在UTC与America/Los_Angeles进程中，将北京时间12:28误转为12:28Z/20:28Z；Asia/Shanghai为04:28Z。修正后3种时区均得到04:28Z，并覆盖跨日期边界。
- 前端31项完整测试通过，失败/跳过0；包括北京时间显示、年末跨日、空/坏值、非法日期和毫秒筛选。正式Vue构建通过。
- 原真实后端E2E脚本的时间标签选择器同步更新并通过语法检查；该完整E2E套件本轮未重跑，不把选择器维护等同于再次通过真实后端验收。
- 本机Chrome在UTC、Asia/Shanghai、America/Los_Angeles三个上下文完成列表/详情/筛选/总览共12项展示检查。网络使用明确的本地展示夹具，不算真实服务器授权或业务验收；截图与原始输出不提交。
- 两个实际JAR的Boot/Logback依赖中，通过真实LoggingSystemProperties环境映射及默认console-appender渲染固定事件，8项探针通过：旧配置在UTC下仍为04:37Z；新配置在3种JVM默认时区下均为12:37:29.205+08:00。

没有修改Java业务代码，未重复运行MySQL全量业务回归，也不借用R35的云端62/8/10项成绩充当本次时间修正已上线证据。正式发布后的真实浏览器与docker service logs回看待后续统一执行。

<a id="current-docs-and-monitoring"></a>

## 现行文档、监控与鉴权体验核对

日期：2026-11-22；源码起点`7fed862dca56844e02b8793d44527b9538e48dee`，任务分支`codex/docs-monitoring-current`。用户要求按现行需求和设计整理文档、删除无用历史，并指定老师`spring-fundamentals/4. SpringAOP`为监控参考。用户随后要求在同一PR44修正日志筛选、密码提示及Apifox真实测试环境，反馈起点为21b425b。此次没有部署、热加载或重启正式服务。

<a id="current-docs-and-monitoring-1-文档调整"></a>

### 1. 文档调整

README与docs导航只保留当前设计/运行入口；需求将原先按实施阶段追加的课程首次身份、身份八项、全JWT及部署约束合入业务主题，保留R01—R33稳定追溯编号。课程接入按系统职责、身份链、独立支撑、初始化恢复、来源补丁及业务限制组织；初始化和复现不再重复PR/阶段时间线。

后端、前端、领域、数据库、API说明及各组件README同步清除过时计划和重复状态；保留双策略设计、82组有效图件、K17、即时撤权、人员角色差集、初始化回执和老师来源缺口。AGENTS/CONTRIBUTING改为现行协作约束，不再累积互相覆盖的单次授权。

删除31个明确的tracked历史文件：旧阶段计划及整理说明、已被现行设计覆盖的两份接入决策、旧整改计划/任务/API稿、9份重复早期实施报告、14个被新图集替代的图源/图件。不再将这些内容移到更深层history；清理前本机归档位于`.local/docs-monitoring-20261122/docs-before.zip`，Git起点可恢复。老师纪要复核、原型确认、T13及重要UI/协议/初始化/部署证据保留。只修保留记录的引用，不改写当时测试成绩；原Excel、外部详设、原库/卷和原始日志不动。

<a id="current-docs-and-monitoring-2-服务与监控核对"></a>

### 2. 服务与监控核对

`course-integration`是独立Waitress身份投影及业务审计支撑：实时访问真实RBAC、核对粗粒度短码的必要路由全集、独立持久化course_audit。当前课程配置依赖它，不是mock或RBAC课程实体服务。

线上首发Prometheus使用官方镜像及30秒采集，未配置CPU规则；此前删除的为旧实验Stack监控。用户指定SpringAOP目录中的规则已为2秒CPU窗口，但规则组30秒显式间隔会覆盖全局2秒计算。此次待发布配置采用相同CPU公式，节点1秒采集、规则组2秒计算；Java示例job保留单独2秒采集，其他rate5m指标仍为5分钟窗口。

老师SWR镜像在A构建机只读检查：Prometheus3.7.3摘要`6573196e5dbe834779ac4d34cb053113cc502a69e9e20ba3be96d37f33e81545`，node-exporter1.10.2摘要`7bcf2839f207d926b908cd3c566c9f1577efb72268062be0c96cd3b17a5cb283`。Prometheus镜像内只含默认配置，公式来自挂载文件，不能仅凭镜像名称判断已更新。

Stack增加内网global node-exporter，拟采集B/C/D/E，A不在Swarm。只读host路径及有限采集器；不公开9090/9100。未来发布为8项服务定义，当前线上七服务未改变。来源快照、配置覆盖、采集语义及限制见[监控设计](../../../deploy/monitoring.md)。

<a id="current-docs-and-monitoring-3-实际验证"></a>

### 3. 实际验证

- 固定老师镜像中的promtool3.7.3：最终配置校验通过，11条规则校验通过。
- promtool合成序列5组、6次结果断言通过：双核/双实例隔离，空闲/全忙，计数器重置，漏采后暂缺及恢复，新实例单样本暂缺。
- Docker CLI27.5.1在断网、只读、无Docker socket的临时容器内执行`docker stack config`，8项服务定义解析通过。A原Docker20不含该子命令，未尝试用部署代替校验。
- Promtool在断网只读临时容器执行，测试临时TSDB使用/tmp内存文件系统；没有启动正式Prometheus或exporter。原始日志留在`.local/production-20261122/monitoring-promtool-offline-20261122f.log`。

全部41份剩余Markdown的465处本地链接/锚点校验无错误；非秘密配置校验输出8项服务定义。保留的11条规则表达式与用户指定老师文件逐项一致，显式组间隔改为2秒。R01—R33全部保留；OpenAPI YAML/JSON一致且仍37操作、31路径、71模型。上述为最初文档/监控提交的验证范围；随后鉴权体验修改的代码及新实测见第4节，不引用旧业务成绩代替本次通过。

双轴初审规范0项；需求轴发现overlay容器netdev不代表宿主网络的口径问题，已撤去该采集器及两条网络规则，明确本次不采宿主机网络，不引入host网络或公网端口。CPU、内存、文件系统、磁盘的宿主路径及Java指标保留；最终11条规则和Stack已重跑以上检查。独立新上下文双轴复核`7fed862...ec00f426`：Standards硬违反/需报告smell均0，Spec缺失/越界/实现错误最终均0；初审1项已修复。复核属于代理技术审核，不冒称老师或真人验收。

<a id="current-docs-and-monitoring-4-鉴权体验与apifox"></a>

### 4. 鉴权体验与Apifox

线上只读聚合核查时已存在33条course-project-management的LOGIN_SUCCESS及24条目录/查人AUTHORIZATION允许记录，不能把首页未显示理解为没有记录。此处LOGIN_SUCCESS证明RBAC凭据/应用准入成功，不证明课程教师/学生绑定或后续业务已经成功；老师后端主要通过/auth/me取权限后自行执行对象规则，RBAC没有收到每个功能的执行结果。

日志响应新增systems（id/code/name/enabled），由已授权日志Gateway提供，含停用应用，不随日志筛选缩小，不要求GET /api/systems资源目录权限。Vue改为名称/编码下拉、提交appCode，并说明快照刷新和业务结果边界。密码框补“请输入密码”，保留掩码及显示/隐藏。

两种身份用例分别在实际短码及operationId查询完成后登记PERMISSION_CHECK。过滤器每次查询记录一条真假结果；无权限仍HTTP200，reason=PERMISSION_NOT_GRANTED，不当成课程业务403或已执行结果。实际受控目录/查人继续用AUTHORIZATION。未扩写老师业务或混合两套审计。

新增两项真实MySQL/HTTP契约先RED（缺少目录及查询事件的断言失败），再实现；最初数据库未启动、沙箱Java附加停滞不算RED。最终structured与DDD各215项，失败/错误/跳过均0，原始日志为.local/pr44-feedback-20261122/rbac-structured-green.log及rbac-ddd-redgreen.log。前端32项及构建通过，Chrome9项显示检查通过（独立HTTP显示夹具，非服务器端到端）；截图与报告保存在同目录。真实CloneFactory53个生成重载非空，新增systems的集合元素类型转换显式映射。

OpenAPI1.5.1仍37操作、31路径、71模型、K17；YAML/JSON一致并通过OpenAPI3.1校验。Apifox9042431按本次授权同步两项Schema及版本，导出回读的操作/模型/鉴权/元信息语义差异0，失败计数0。环境50398304已改名“服务器验收（真实RBAC）”，默认及模块8887289 Base URL均http://114.116.254.25:8088，保留原参数，不新增重复环境；原11环境ID集合相同。经回读地址发起/api/me及/auth/me匿名请求均得到真实后端401、AUTHENTICATION_REQUIRED及请求号。个人客户端仍需选择该环境，旧Mock仅供显式示例，不写共享口令或JWT。

反馈修改e524ab2经独立新上下文双轴审核：Standards硬违反/需报告smell均0，Spec缺失/越界/实现错误均0。验证记录的Markdown标题排版已修正，引用检查仍为41份/465处零错误。审核属于代理技术复核，不冒称老师或真人验收。

<a id="current-docs-and-monitoring-5-尚未验证与发布边界"></a>

### 5. 尚未验证与发布边界

新增exporter的真实节点采集、overlay DNS发现、1秒采集的时序抖动与资源/存储占用须获统一发布授权后实测，离线合成序列不替代线上验收。2秒窗口只有少量样本，漏采可能短时无值，不能填成0%。应用仍单副本、单manager和进程内会话索引，不宣称高可用。

北京时间、监控及鉴权体验代码均待统一发布；未推送新应用镜像、更新线上Stack、重启正式服务或改外部详设，不关闭T14/T15。Apifox环境/契约已按本次授权更新，新增日志字段与查询事件尚需应用统一发布后在服务器验收，不能把指向真实服务器的环境当成新代码已上线。

