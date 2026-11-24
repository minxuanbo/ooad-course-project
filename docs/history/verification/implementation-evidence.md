# 后端与集成验证证据

各记录仅对应其原日期、源码和环境；保留原成绩及限制，不作为当前版本自动通过。现行状态见[测试与验收](../../testing-acceptance.md)。

<a id="teacher-check-applications-20261124"></a>

## 课堂检查与应用管理验证

2026-11-24，任务分支`codex/teacher-check-application-management`，从main建立并保留PR58两项按钮置灰修改。本文记录发布前源码及离线验证，当时不部署、不改正式库。用户随后另行授权正式发布，两个RBAC镜像及旧库兼容已完成，实测见[发布记录](release-evidence.md#application-management-release-20261124)；PR59仍为草稿、未合main，课程前端及原始日志保留。

<a id="teacher-check-applications-20261124-1-对照老师检查项"></a>

### 1. 对照老师检查项

| 检查项 | 实际结论与处理 | 可核对位置 |
| --- | --- | --- |
| 应用管理菜单 | 原来仅能枚举已登记目录，缺运行期新增/移除界面。本轮新增SPA应用管理，数据库分页、新增、名称/接入状态修改、逻辑移除与恢复；无代码白名单 | R34、ApplicationController、Applications.vue |
| 正常异常与边界 | 新增应用生命周期、权限拒绝、编码冲突、字段长度、分页边界、维护者保护、存储故障直接用例；每条稳定需求放对应方法链接，复用真实共享契约，不补空r02目录 | requirements.md逐条链接、r34契约 |
| 本人权限排除停用项 | 原有有效链计算已过滤；补直接读取本人权限响应，逐个断开权限/API/角色链，验证OR替代、未授父项及父停用不级联。系统停用使RBAC准入拒绝 | R09_ExcludeInactivePermissionChains |
| 登录与单菜单 | 原准入要求任意管理API。本轮独立LOGIN：只授登录可进入空工作台；授单菜单显式读取API后才有该页能力；撤LOGIN后旧JWT管理本人查询也403 | R08_LoginOnlyAndSingleMenu、RbacCatalog |
| MENU/BUTTON与API | Permission及ApiResource仍分表、多对多显式映射。七个初始MENU明确映射本页读取API，各按钮单独映射真实操作；父子不继承，不隐含其他写按钮 | RbacCatalog.menuRoutes、R06目录检查 |
| 中文名称 | 补真实初始化响应逐项核对30个中文操作名，以及未开放名称编辑时400且不改库；编码、ID及关系不因中文展示改变 | R30_ChineseCatalogNamesAndImmutableMetadata |
| 角色关联批删重建 | 未发现。两策略已有Set差量保存；锁后读取现有集合，仅新增/删除逐项重验，锁定项可保留 | R11/R13/R27共享契约、User行为与JPA Adapter |
| 当前状态及两策略 | 逐请求读库、无JWT权限快照、共根锁后重鉴权保留。应用分页两策略均同一RR快照；新增应用预检在MVC解析前，数据库并发错误统一409，PATCH混合显式null先RED后拒绝且不改库 | Application契约、ManagementPreflight、JpaWriteErrors |
| JPA写法 | 生产链无旧JDBC业务查询。原外部身份目录技术Gateway直接使用EntityManager，本轮改为Spring Data专用投影及计数，领域仓储不泄漏技术类型 | JpaIdentityDirectoryGateway、Department/UserJpaRepository |
| 选择器装配 | 互斥策略没有重复业务执行。StructuredImportService无组件注解，必须保留显式Import，不能因外层扫描就误删 | R19BackendStrategyTest、R07完整契约 |
| 分层、类图、差异图 | 应用生命周期类图同步，新增一组真实差异；structured部门删除图补所属角色占用。84组图=2包+32类+50差异序列；41操作和两类离线入口可追溯 | backend-design第17章、ddd-diagrams.json |
| SPA与课程边界 | 正式Vue只有index.html入口，Router切换同一应用；离线原型只是旧交互参考，明确列出与新源码差异。课程对象权限和原始Vue行为未改 | frontend/src/main.js、prototype/README.md、course-integration.md |

<a id="teacher-check-applications-20261124-2-测试及覆盖率"></a>

### 2. 测试及覆盖率

隔离MySQL8.4在本轮私有3355实例建立六个测试库，未用正式业务库或旧联调库。新应用、独立LOGIN、中文名称及拒绝顺序采用真实嵌入式HTTP；持久化故障另按端口注入，不将其称为真实数据库故障实验。

显式null拒绝修复后，DDD与structured均重新clean完整运行344项。两策略失败、错误、跳过均0；360份生产class逐文件SHA-256一致后合并。原Maven六项MISSEDCOUNT=0检查通过，未排除业务类或降低门槛。

| 指标 | covered | missed |
| --- | ---: | ---: |
| INSTRUCTION | 25173 | 0 |
| BRANCH | 2000 | 0 |
| LINE | 5013 | 0 |
| COMPLEXITY | 2099 | 0 |
| METHOD | 1092 | 0 |
| CLASS | 276 | 0 |

[最终报告](../../reports/jacoco/final-rbac/README.md)只维护当前版本，原报告留在Git历史；100%是本轮隔离执行覆盖，不代表业务无缺陷、线上流量覆盖或性能结论。前端54项单元与17项构建渲染通过；浏览器为真实Vue配可控HTTP，不能冒充真实业务后端或公网验收。

真实CloneFactory0.0.2检查：63个有效生成重载、0空实现；224处可变setter赋值，214组可变同名同类型字段无遗漏；5个不可变构造映射、21参数完整且不读取null目标。业务工厂、跨层/多来源/派生转换仍明确组装。注解独行扫描排除注解类型声明及字符串后无违规。

<a id="teacher-check-applications-20261124-3-外部契约与材料"></a>

### 3. 外部契约与材料

Apifox9042431主分支导入新增4操作及4模型，失败0；再基于完整模块读取更新版本及说明，保留环境设置。最终回读为1.7.0、41操作、33路径、77模型，规范化比较语义差异0；原17项K不变，完整维护者另要求有效LOGIN。已纠正旧x-initial-permission-code中仍指向分组授权的元数据，实际鉴权继续允许任一有效Permission映射覆盖。

桌面详设PDF、同版PPT、Markdown讲稿、三份教师问答和提交ZIP按用户要求同步。详设133页；PPT25页主讲含封面、目录及致谢，附录19页共44页，新增应用管理放技术附录。PPT经原生渲染核对，保留原目录图的自定义裁切；生成工具导入曾丢失该图形几何，交付前已恢复。Word与演示评审PDF仅为忽略目录中间件，不交付。ZIP提供PDF、Markdown、可编辑图源、OpenAPI及首次SQL，不含Word、完整工程、凭据或数据库。

操作演练见[自定义角色验证方案](../../testing-acceptance.md#role-verification-guide)。方案没有在正式库创建账号、角色或测试数据。新增人员仍属人力来源，本轮不增加注册/创建人员功能。

<a id="teacher-check-applications-20261124-4-未验证和发布边界"></a>

### 4. 未验证和发布边界

本文源码验证阶段未做公网发布验收，不沿用PR56/前轮结果证明新版本通过。后续已按用户追加授权完成备份、目录/权限兼容与两个镜像发布，115项公网HTTP及11组浏览器实测单独记于[发布记录](release-evidence.md#application-management-release-20261124)。没有重跑初始化、重置原授权或追加新性能结论。

<a id="access-extension-coverage-20261124"></a>

## 应用目录及部门角色覆盖率补齐（2026-11-24）

用户追加授权恢复JaCoCo六项100%、合入main及部署。此页记录发布前源码验证；服务器实际版本、数据切换及公网结果另记，不用覆盖率冒充部署验收。

实测源码`7fd1b60e1e76d0d74d3fa5db7c535c404c2a9c47`，相对前次扩展仅增加边界测试及注解独行格式修正，未降低pom、证据工具或统计范围。新增用例检查部门角色的缺失/删除/越界归属、当前祖先适用性、低级别及读取元数据缺失、管理员定义资格、锁定旧关联、部门删除能力、应用预置真实持久化往返、所属过滤及两端各自PENDING门禁。

<a id="access-extension-coverage-20261124-实测结果"></a>

### 实测结果

| 检查 | 结果与边界 |
| --- | --- |
| DDD完整回归 | JDK21、真实MySQL8.4隔离3353，clean test jacoco:report；328项，失败/错误/跳过0 |
| structured完整回归 | 同环境独立clean；328项，失败/错误/跳过0 |
| 字节码一致性 | 两份快照及当前347份生产class逐项SHA-256完全相同 |
| 官方合并 | JaCoCo CLI0.8.14合并exec，分析264可测类；指令24208、分支1948、行4862、复杂度2021、方法1040、类264，六项未覆盖全部0 |
| 原Maven门槛 | 合并exec交原coverage-check，All coverage checks have been met；检查后恢复structured原exec |
| 双轴静态审核 | Spec未发现明确需求缺陷；Standards硬违反0、未解决有影响异味0；PENDING测试已分别隔离权限端/API端失效，避免双端同时失效掩盖遗漏 |
| 前端及接入 | 业务源码与前次53项、8组浏览器、43项Python的验证版本相同；不虚报本轮重新执行 |

该成绩替代[前次新增代码未满覆盖](implementation-evidence.md#access-extension-20261124-4-本轮jacoco结果与限制)作为当前源码覆盖率依据，旧记录保留原始结果。两种策略使用独立业务实现；单策略覆盖率不要求单独覆盖另一策略，合并仅在生产字节码完全相同时允许。

<a id="access-extension-coverage-20261124-报告与复现"></a>

### 报告与复现

[完整报告归档](https://github.com/minxuanbo/ooad-course-project/blob/65748fdf00b733221358d8012cf439de2f99e833/docs/reports/jacoco/20261124-rbac/README.md)包含脱敏HTML、XML、计数及逐文件摘要，未包含凭据、Surefire环境XML、exec、class或数据库。使用[现行复现命令](../../../backend/README.md#7-双策略jacoco复现)，最终完整测试日志及快照位于忽略目录`.local/member-queries-20261123`的release49-final-ddd-20261124、release49-final-structured-20261124；合并及原门槛记录位于`.local/r49-release-20261124`。

100%只表示测试命中了JaCoCo可测代码计数，不证明没有缺陷、穷尽业务组合、通过性能压测或生产流量全覆盖。服务器发布和数据兼容必须单独核验。

<a id="access-extension-20261124"></a>

## 18业务系统与部门角色验证（2026-11-24）

本记录对应草稿PR49中1.6.0源码，沿成员查询、日志IP及原型界面改动继续实施用户已确认的步骤3/4。实施前HEAD为888a1ccbe6cfa381db8dce389c6c15c8dff5522d，目标main为9ff74bea9cd88f840adb45f8330348fb830fe91e。测试产物、数据库及凭据在本机忽略目录；本记录不代表已经发布或通过老师验收。

<a id="access-extension-20261124-1-目录与授权结果"></a>

### 1. 目录与授权结果

原件[应用软件功能权限清单](../../../市政公司应用软件功能权限清单.xlsx)SHA-256为13c18de30710cc2867588aa99843c6c388c0a8ee85029f3420f7b4c4c9fad655。只读提取18系统、115模块、359功能点；独立首次配置生成1271项MENU/BUTTON权限，其中模块115项、功能359项、动作797项，不生成虚构业务API。APP_01—APP_18均为PENDING；加上CONNECTED的RBAC与课程共20项目录。

| 原表角色列 | 初次计划权限数（含显式展示菜单） |
| --- | ---: |
| 系统管理员 | 1271 |
| 公司领导 | 940 |
| 部门经理 | 983 |
| 项目经理 | 488 |
| 普通员工 | 196 |

逐行逐动作与五列原矩阵比较；全部只展开同行动作并集，空白不授权，本人限定及view规范化留痕。矩阵不是逐级包含关系，不因管理级别更高而自动补授原表未给的权限。脚本追加到现存五基础角色，保留其他应用关系；相同business-catalog-v1回执跳过，人工撤权和停用不恢复。

四类来源为SYSTEM_BUILTIN、APPLICATION_PRESET、DEPARTMENT_CUSTOM、GLOBAL_CUSTOM。五基础角色保持管理级别，课程三角色通过接入配置登记为应用预置，无管理级别。管理员定义部门角色及选权，领导仅在人员/所属部门子树、目标级别和完整计划权限子集内授撤；待接入权限也参加子集判断。锁后验证实际角色差集，调动同事务撤下不适用项，实时鉴权再次过滤陈旧归属。有所属角色的部门不能删除。

<a id="access-extension-20261124-2-后端与独立初始化"></a>

### 2. 后端与独立初始化

环境为JDK21、MySQL8.4本机3353隔离实例、真实CloneFactory0.0.2，未连接现有产品业务库或云端服务。双策略完整执行test及jacoco:report，测试包含真实Spring/HTTP、数据库与架构边界。

| 最终执行 | 测试数 | 失败 / 错误 / 跳过 | 完成时间（北京时间） |
| --- | ---: | --- | --- |
| DDD | 317 | 0 / 0 / 0 | 2026-11-24 02:12:31 |
| structured | 317 | 0 / 0 / 0 | 2026-11-24 02:15:07 |

TDD首轮部门角色创建用例返回400而预期201，27项中1失败；实现后该场景通过，最终以上述两次clean完整结果为准。新增用例检查部门角色创建/分类、领导受限读取与授予、禁止全局授予及本人修改、调动撤权、陈旧归属失效、删除保护、PENDING新旧身份及短码拒绝、计划选权保存。真实锁等待及分页快照专项48项另验通过，最终完整回归已包含相应修复。

独立目录实测只重建3353的rbac_catalog_extension_test：首次SQL、五基础角色及两个CONNECTED应用后执行业务目录配置，核对20系统/18PENDING/0虚构API，五角色保存的全部code与计划逐项相等。人工清空admin业务权并停用APP_01后重跑，确认不恢复人工维护。此测试库保留验证后的撤权状态，不作完整演示库。

本机原始证据：.local/member-queries-20261123/extension-accepted-{ddd,structured}-20261124.log及对应reports目录；RED记录extension-role-red-20261124.log，目录核对器.local/verify_business_mysql.py。复现入口见[后端](../../../backend/README.md)及[首次配置](../../initialization.md#8-业务目录与角色来源)。

<a id="access-extension-20261124-3-前端、脚本与映射"></a>

### 3. 前端、脚本与映射

- Vue53项测试及生产构建通过；8组隔离Chrome检查通过，含20目录/18待接入、部门角色创建及现有原型布局。浏览器运行真实Vue，HTTP由虚构夹具提供，不能当作浏览器连接真实后端的双系统端到端验收。
- scripts/tests统一发现43项：41通过、2跳过、0失败/错误；跳过为需要另外启用审计MySQL配置的既有Gateway用例。执行时使用本机已安装依赖，并允许本机临时HTTP端口。默认Python环境首次尝试因缺少依赖及沙箱socket限制失败，配置正确环境后重跑通过，不改业务代码规避。
- CloneFactory53个生成重载均非空，210处生成赋值；201组同名同类型可变字段检查无遗漏，record/final仍显式构造。注解扫描3个候选是注解类型声明或字符串内文本，实际独行违规0。
- YAML/JSON同源；1.6.0为37操作、31路径、73Schema、K17。字段增加角色分类/归属和接入状态，无新增业务HTTP操作。
- 六实体Chen总图增加角色所属部门、应用预置来源两类可选联系，核心外键11→13；82组双策略图件保留，调动及角色创建差异顺序图已重新生成并目检，不画共同流程双份。

<a id="access-extension-20261124-4-本轮jacoco结果与限制"></a>

### 4. 本轮JaCoCo结果与限制

两策略快照和当前347份生产class逐项SHA-256完全一致，官方JaCoCo CLI0.8.14合并两份exec，分析264个可测类，不排除新增业务代码。

| 计数 | 已覆盖 | 未覆盖 | 比例 |
| --- | ---: | ---: | ---: |
| 指令 | 24106 | 102 | 99.58% |
| 分支 | 1902 | 46 | 97.64% |
| 行 | 4843 | 19 | 99.61% |
| 圈复杂度 | 1974 | 47 | 97.67% |
| 方法 | 1038 | 2 | 99.81% |
| 类 | 264 | 0 | 100% |

本轮六项零遗漏门槛未通过，未降低pom及证据工具的门槛。此前成员查询阶段及11月23日发布版本的六项100%仅属于各自旧版本，不能作为新增代码后的成绩。合并HTML/XML/摘要保存在.local/access-extension-20261124/jacoco-merged；本次已完成行为回归，后续若继续要求新增版本六项100%，须补足未覆盖场景后重新取得相同字节码双策略证据。

<a id="access-extension-20261124-5-apifox回读"></a>

### 5. Apifox回读

用户明确授权写入9042431，模块8887289、主分支8854997。导入前完整导出备份，使用官方Node fetch导入37操作及73模型，更新模块版本为1.6.0，保留服务器环境。首次更新9个接口、新增2个模型、更新4个模型，失败0；最终再次导出并比较字段、路径、响应、Schema与认证语义，差异0。未把共享令牌、密码或JWT写入环境。

原始导入/回读位于.local/apifox/access-extension-*-20261124.json，比对结论在.local/pr44-feedback-20261122/apifox-comparison.json。公开入口为[Apifox](https://lmbnuppe75.apifox.cn)。线上仍为旧部署版本，新字段须待统一发布后才可在真实服务器响应中看到。

<a id="access-extension-20261124-6-双轴审核与发布边界"></a>

### 6. 双轴审核与发布边界

Standards：最终静态复核未解决硬性违反0、有实际影响异味0；归属名称N+1、循环重复查库及无用预读已修复。Spec：最终未发现剩余已确认缺陷，核对18系统矩阵、计划/实际授权分离、四分类、领导授撤、锁后差集、调动及删除保护。两轴为代码静态审查，不替代运行测试。

本轮提交并推送现有任务分支，保留草稿PR49；未合main、未部署、未执行云端初始化或重置产品数据、未关闭T14/T15。首次SQL含新字段和约束，不交付升级SQL；既有部署库需在后续统一发布前明确兼容数据方案。原Excel、老师源码与前端、旧库/卷和日志保留；外部详设材料本轮未改。

<a id="member-queries-audit-ip-20261123"></a>

## 部门员工、角色成员与来源IP验证

日期：2026-11-23。基线main为9ff74bea9cd88f840adb45f8330348fb830fe91e；本轮功能分支codex/member-queries-audit-ip。仅实施用户确认的步骤1、2，不部署、不合main、不写Apifox，不导入18系统或启用部门角色分配。

<a id="member-queries-audit-ip-20261123-1-实现及设计边界"></a>

### 1. 实现及设计边界

- 复用GET /api/users，增加departmentScope=DIRECT/SUBTREE及roleId；默认子树兼容原接口，直属必须指定departmentId。先范围再过滤/计数/分页；角色停用保留关联，未知角色为空页。
- 部门“查看员工”、角色“查看成员”共用只读分页组件，支持搜索、账号状态及直属/子树切换；关闭、筛选和身份变化丢弃晚响应。原角色目录共同根门禁及所有分配保护保持。
- 日志clientIp贯穿过滤器、执行期AOP、MVC预检、持久化、真实映射及详情；首次SQL加可空client_ip。仅显式可信代理的单值来源有效，默认不信任转发头；Nginx覆盖外来头，正式接入服务以请求上下文隔离后转交来源。服务间地址不冒称最终用户IP。
- 角色来源、部门分配和待接入状态见[待确认设计](../../domain-model.md#access-extension-plan)。后续按Excel完整18系统准备，加现有RBAC和课程共20目录项；本轮不落库、不启用。

核心六对象和Chen ER关系未变；37操作、31路径、71 Schema及K17不变。OpenAPI YAML/JSON同源。查询类图增加roleId，DDD差异顺序图更新实际查询条件构造参数；不重复绘制共同HTTP/IP采集顺序。

<a id="member-queries-audit-ip-20261123-2-red与green"></a>

### 2. RED与GREEN

真实MySQL的新增HTTP测试先确认三个缺口：DIRECT返回10001而非根直属11；roleId=0返回200而非400；client_ip列及日志采集不存在。最初一次执行被沙箱拒绝本机套接字，不算行为RED；恢复项目私有测试实例并用许可运行后获得上述实际失败。

实现后新增三项HTTP测试及四项IP边界测试。覆盖直属/子树、角色与部门交集、未知角色空页、停用角色关联、状态组合、越界/不存在部门、安全ID/枚举/关联校验，以及登录、匿名拒绝、执行期AOP、已识别MVC预检的来源记录。IP边界覆盖IPv4/IPv6、未信任代理、非法/重复/多地址头、CIDR错误及过滤器传递。

环境：Windows、JDK21、真实CloneFactory0.0.2、私有MySQL8.4.8的127.0.0.1:3353，六个既有可丢弃专库。为这些测试专库对齐client_ip列后执行；未改线上、万人业务库、旧实例、原Excel及原始日志。

| 验证 | 本轮结果 |
| --- | --- |
| DDD完整回归 | 311项，失败/错误/跳过均0 |
| structured完整回归 | 311项，失败/错误/跳过均0 |
| 双策略生产class核对 | 342份SHA-256完全相同，才合并exec |
| 合并JaCoCo | 指令22216、分支1730、行4498、复杂度1867、方法995、类259全部覆盖；六项遗漏均0 |
| 原pom门槛 | 将本轮合并exec交原coverage-check，六项零遗漏通过；未降低门槛或新增排除 |
| Vue | 35项通过，生产构建通过；含新成员组件的筛选、失败、晚响应和身份切换测试 |
| Python | 41项通过、无跳过，含请求来源隔离、正式Waitress中间件和已有真实MySQL业务审计回归 |
| CloneFactory | 53个生成重载均非空，198次赋值；189组可变同名同类型字段核对无遗漏 |
| UML | 生成器通过；受影响PNG已目检，参数可读、无裁切 |

后端按backend/README.md的六专库环境分别设置APP_BACKEND_STRATEGY后运行clean test jacoco:report；同字节码合并与原门槛执行方式沿该文第7节。Vue运行npm test、npm run build；Python显式提供本机隔离数据库配置后执行python -B -m unittest discover -s scripts/tests -v。原始日志、HTML/XML/exec及摘要留.local/member-queries-20261123，不覆盖之前归档的304项报告。

<a id="member-queries-audit-ip-20261123-3-未发布与限制"></a>

### 3. 未发布与限制

服务器仍为此前发布版本，不会立即出现按钮或IP字段。尚未对新版本取得真实浏览器端到端、正式代理链来源和云端运行结果；组件测试、构建及本机HTTP不冒称这些结果。正式发布前必须另外核对现有数据兼容和可信代理范围；只执行CREATE TABLE IF NOT EXISTS不会升级旧表，不能以自动DDL或重跑初始化覆盖现库。

Apifox仍对应已发布契约，本轮仅修改仓库契约；写入需用户明确授权。18系统导入、应用预置角色分类与部门角色运行规则，以及后续集中验收/发布，等待用户确认。角色成员查询不提前开放部门领导的全局角色维护能力。

<a id="member-queries-audit-ip-20261123-standards"></a>

### Standards

规范轴首次发现1项P2：新增OpenAPI参数重复及required数组重复，严格校验拒绝。4b79028已去重并从YAML同源生成JSON，两份OpenAPI及LogEntryVO JSON Schema严格校验均通过。独立审核复核未解决项0；其余分层、真实CloneFactory、隐私及编码规范未发现阻断问题。

<a id="member-queries-audit-ip-20261123-spec"></a>

### Spec

需求轴首次发现2项P2：上述契约重复，以及正式Waitress默认提前清除来源头。后者已在Docker入口保留原始头、保持trusted_proxy=None，仍由Gateway唯一核查可信连接方与单值IP；新增实际中间件测试先RED（默认清头），修复后GREEN。Python最终41项无失败/错误/跳过。独立复核两项均消除，未解决项0；未发现人员范围扩张或提前实施待确认阶段。

审核基线为9ff74be；Java完整回归与342份class对应9aa49db的源码，后续4b79028及记录提交没有修改Java。原pom合并门槛成功日志为.local/member-queries-20261123/coverage-gate-merged.log；原始HTML/exec、测试输出、映射扫描及图件目检资料留在本轮忽略目录，不覆盖此前已发布版本的归档报告。本轮未取得新的真实浏览器端到端证据，Vue脚本测试/构建不冒充浏览器交互验收。

审核结论：Standards首次1项、Spec首次2项；修复复核后两轴未解决项均0。

<a id="rbac-jacoco-complete"></a>

## RBAC双策略JaCoCo补齐（2026-11-23）

<a id="rbac-jacoco-complete-范围与环境"></a>

### 范围与环境

本轮只补齐RBAC后端structured与DDD的合并覆盖率，基线为`5743a13`，任务分支为`codex/jacoco-complete`。结果对应包含本记录的源码，不能作为旧发布镜像或后续修改版本的测试成绩。

使用JDK21.0.13、Maven3.8.5、MySQL8.4.8及JaCoCo0.8.14。CloneFactory普通依赖与处理器均为真实0.0.2。两策略串行使用本轮隔离MySQL实例3353及后端说明列出的六个`rbac_core_*_test`专库，保留各上下文安全守卫；这些是真实MySQL业务代码集成测试库，不是线上万人业务库。原Excel、旧库、卷和原始日志保留。

完成后已核对3353端口与私有datadir，正常停止本轮启动的实例并保留数据，恢复测试前的停止状态；其他MySQL实例未操作。

测试包括纯领域规则、技术端口故障单元测试、真实XLSX/XML、真实JWT签名及密码学、真实MySQL和HTTP契约。端口替身用于控制异常或不一致读取，不能据此宣称验证了SQL约束、事务回滚或分布式原子性。SQL约束、故障触发器、回滚、授权链及HTTP行为继续由真实MySQL测试验证。

<a id="rbac-jacoco-complete-最终结果"></a>

### 最终结果

| 验证 | 结果 |
| --- | --- |
| DDD完整回归 | 304项，失败0、错误0、跳过0；12:37:25完成 |
| structured完整回归 | 304项，失败0、错误0、跳过0；12:39:41完成 |
| 审核后路由常量定向复测 | DDD下13项，失败0、错误0、跳过0；structured全量已包含相同修改 |
| Python后端辅助工具 | 9项通过，含4项新增覆盖率证据保护测试 |
| 生产字节码 | 两次全量与合并时当前全部340份class的SHA-256一致 |
| CloneFactory | 53个生成重载均非空，194处生成赋值；185组可变同名同类型字段无遗漏 |
| 原Maven覆盖门槛 | 六项MISSEDCOUNT=0全部通过，退出0；12:40:32完成 |

官方JaCoCo CLI合并两份完整执行数据，不增加生产类排除；原pom未修改。按JaCoCo默认过滤规则得到257个可测类，全部六项计数如下：

| 计数 | 已覆盖 | 遗漏 | 覆盖率 |
| --- | ---: | ---: | ---: |
| INSTRUCTION | 21860 | 0 | 100% |
| BRANCH | 1678 | 0 | 100% |
| LINE | 4440 | 0 | 100% |
| COMPLEXITY | 1831 | 0 | 100% |
| METHOD | 985 | 0 | 100% |
| CLASS | 257 | 0 | 100% |

100%是整个RBAC工程同字节码下两策略执行数据的合并结果，不把一个策略的单次报告称为全工程100%。它证明这些可测计数已执行，不代表没有缺陷、所有业务组合或性能场景均已穷尽，也不是老师验收或线上发布结果。

<a id="rbac-jacoco-complete-变更与有效断言"></a>

### 变更与有效断言

新增和扩充启动入口、隔离配置、来源与初始化回执、组织与人员范围、候选SQL、权限导出、审计、游标、文件/XML读取及JWT边界测试。断言包含稳定错误码、实际投影字段、无副作用、失败回滚及拒绝后的保留状态，不仅要求“没有抛异常”。真实数据库故障注入会产生预期SQL错误日志，以最终Surefire结果区分预期拒绝与测试失败。

生产行为修复为JWT必填`iat`校验：真实HS256签名且缺少`iat`的新增测试发现Spring声明转换器会补时间，使原必填检查未拒绝。先取得行为RED，再保留原始载荷是否含`iat`的事实，移除框架补值后由既有校验拒绝；相同测试GREEN。原有签名、进程会话索引和逐请求读库一直存在，不能将该发现表述成原先可任意伪造在线身份。

另外统一两处重复来源JSON编码为共享技术组件`SourceCellsJson`，保留独立默认JSON设置、来源错误契约及两策略各自业务判断；目录分页先检查整数偏移上限，再判断是否越末页，返回行为等价。HTTP契约、数据库结构、角色与课程业务均未改变。

中途暴露的测试装配缺项、断言错误和父权限引用恢复问题均已修正，原始失败日志保留；最终报告只取上述两次完整成功回归。没有通过删除生产防御、修改阈值、排除包、空断言或跳过测试取得100%。

<a id="rbac-jacoco-complete-复现与证据"></a>

### 复现与证据

完整命令及专库配置见[后端说明第7节](../../../backend/README.md#7-双策略jacoco复现)。`backend/scripts/jacoco-evidence.py`在每次clean完整测试后保存计数、全部class摘要及exec摘要，不复制可能含环境凭据的Surefire XML。合并时核对两策略和当前class一致，用官方CLI生成HTML/XML并检查六项零遗漏。

门槛检查前备份target/jacoco.exec，将按摘要核对的合并exec置于插件实际读取路径，执行`org.jacoco:jacoco-maven-plugin:0.8.14:check@coverage-check`。本轮输出`All coverage checks have been met.`，随后恢复原exec并再次核对摘要；不依赖无效的dataFile参数覆盖。

原始材料留在忽略目录`.local/jacoco-complete-20261123`：`full-ddd-6.log`、`full-structured-6.log`、`evidence-6/{ddd,structured}/snapshot.json`及exec、`evidence-6/combined/{html,jacoco.xml,summary.json,jacoco.exec}`、`final-maven-gate.log`、`review-route-constants-ddd.log`和`mapping-audit.log`。`analysis-6/missed.json`为空，独立分析所得六项计数与正式合并工具一致。

用户随后明确要求将报告归档并推送两仓，完整可浏览HTML、XML和摘要按授权收录于[报告目录](https://github.com/minxuanbo/ooad-course-project/blob/65748fdf00b733221358d8012cf439de2f99e833/docs/reports/jacoco/20261123-rbac/README.md)。ZIP及每个文件的摘要见同目录manifest；仅将两处机器生成的会话名称改为策略标签，时间和覆盖率计数不变。凭据、数据库、原exec、class/JAR、Surefire环境XML及原始日志仍不提交。归档及导航修改不改变生产代码，沿用上述同字节码实测结果。

<a id="rbac-jacoco-complete-standards"></a>

### Standards

按指定审核skill独立审核规范轴。发现的1类测试固定路由常量问题已修正并复核，硬性发现0；保留1项非阻断Middle Man建议：两处原私有JSON包装方法目前直接委托共享编码器。为保持本轮生产改动最小，暂不额外整理。

<a id="rbac-jacoco-complete-spec"></a>

### Spec

独立需求轴未发现缺项、范围扩张或错误实现，发现0。审查确认六项原门槛不变、无生产排除、测试隔离及同字节码合并；实际通过结果由上面的运行证据确认。两轴分别报告，不互相抵消。

<a id="rbac-jacoco-complete-发布边界"></a>

### 发布边界

覆盖率和报告归档阶段没有重启正式应用，先交付PR #47，按用户后续授权合入GitHub main、同步Gitee并清理闲置分支。此后用户又明确要求发布生产修复，RBAC后端已于2026-11-23 13:05更新，实际镜像及91次HTTP验证见[JWT修复发布记录](release-evidence.md#jwt-coverage-release-20261123)。课程系统覆盖率仍保留前次测试成绩，原始报告不因发布改写；老师课程代码、Apifox及桌面材料本轮不变。

<a id="jwt-design-cleanup"></a>

## R33全JWT及双策略文档/UML整理验证

日期：2026-11-21—22。源码起点`62907464ba76d035c758f4867ed22219a3484271`，任务分支`codex/jwt-design-cleanup`。用户要求管理和外部应用全部JWT、参考老师docs补齐structured类图、核实所有实现差异且只绘差异、整理文档，并指出Apifox“应用身份”空目录。需求见[R33](../../requirements.md#71-身份协议与令牌)。老师本机docs仅作只读参考，不采用其中旧实施状态或其他框架版本作为本RBAC要求。

<a id="jwt-design-cleanup-1-最终改动"></a>

### 1. 最终改动

管理端与外部应用统一通过`JwtIdentityStore`/`JwtIdentityCodec`签发和验证HS256身份JWT；删除旧管理opaque分支，原身份端口及token/accessToken响应字段不变。只含sub/aud/iss/iat/exp/jti，不含角色权限快照。管理aud=rbac，外部aud为单个已登记应用；原管理准入、权限实时读取、单会话退出、进程重启失效及12小时有效期保持。无新表、刷新令牌或委托授权机制。

UML原来偏重DDD的15个模块，现在为各模块补对应structured类图，加上通用身份形成16对。37项HTTP、2类离线入口和横切支撑逐项核对，按24组独立实现差异生成48张顺序图；共同JWT/HTTP/日志/锁/提交及相同Gateway读取不画两份，同一分页/集合模式在覆盖表明确复用。每个消息接收方法核实存在，JPA继承方法及Lombok生成setter按真实来源校验。覆盖表与图件使用同一注册表并对照OpenAPI和用例端口生成。

当前docs根保留12份职责明确的文档，旧阶段计划、UI验收、长验收快照及两份重复设计说明归history；14份旧局部/重复图资源也归档。共19个文件搬迁，原资料和历史成绩不删，受影响链接同步。逐文件对照起点Git内容，14份图源/图件内容相同，5份Markdown除导航重定位与历史标识外正文保留。移除已被完整structured图替代的旧生成脚本，防止生成过时图重新混入当前目录。详设Word/PDF/PPT及讲稿保持原版。

全量Python回归暴露旧32人空库夹具生成器写死R29目录数量，导致R32新增IDR/IDU后失效。新增断言先RED，修复为当前配置结构校验；测试独立核18个API权限、24映射、22唯一课程API，并确认IDR/IDU只初授系统/课程管理员角色。未向现有库执行该夹具，也未改旧夹具数据。

<a id="jwt-design-cleanup-2-测试与本机联调"></a>

### 2. 测试与本机联调

Windows、JDK21.0.13、Maven3.8.5、MySQL8.4.8、真实CloneFactory0.0.2。沿用R31的独立3351/3352实例及各6个专用回归库；原3349/3350、万人库、角色及课程绑定保留。两策略分别`mvn -B -ntp -o -f backend/pom.xml clean package`，不把R32的208项成绩作为本轮通过。

| 检查 | 实际结果 |
| --- | --- |
| JWT TDD | 管理JWT格式新增单测先因旧随机令牌只有1段而RED（1失败、0错误）；实现后3项身份存储及既有4项JOSE单测均GREEN，另增加共享真实管理HTTP JWT/退出契约 |
| structured真实MySQL完整回归 | 212项，失败/错误/跳过均0，clean package成功 |
| DDD真实MySQL完整回归 | 212项，失败/错误/跳过均0，clean package成功 |
| 独立JAR身份HTTP | 两策略各61次通过，新增管理JWT/aud/无权限快照断言，保持8项直连与转接、受众隔离、退出及真实API撤权恢复 |
| 独立JAR课程HTTP | 两策略各43次通过，逐一核对10001角色与绑定、191教师、9810学生、1同账号管理员、10001独立密码哈希；原令牌授撤权及重复初始化保护通过 |
| Python | 26项通过；包含当前夹具目录及真实HTTP stub。第一次沙箱执行的套接字10013和旧目录断言失败不计通过，最终在允许本机套接字环境重跑 |
| 第一组真实Vue | 25项通过，新增管理页面JWT受众及无权限快照断言；课程部门选人另在127.0.0.1与localhost两Origin均通过。未宣称第二组完整浏览器复验 |
| RBAC前端 | 27项测试及生产构建通过 |

原始日志、Surefire、JAR、HTTP JSON与截图放`.local/jwt-design-20261121`，口令仍在原私有目录，不提交或回显。RBAC后端切至本轮已验证JAR：structured8081、DDD8083；课程8080/8084、支撑8085/8086、Vue5173/5174及数据库保持原进程。后端重启后两端均须重新登录，原演示口令不变。

<a id="jwt-design-cleanup-3-契约、图件与归档核对"></a>

### 3. 契约、图件与归档核对

当前OpenAPI1.5.0仍为37操作、31路径、71模型、22错误、K17。管理BearerAuth和applicationJwt均标JWT，两个登录字段均有JWT三段格式约束；Bearer仍是携带方式，受众相互隔离。本轮OpenAPI3.1校验、YAML=JSON、两策略28个真实响应Schema均通过；CloneFactory两策略生成摘要相同，53个非空重载、185组可变同名同类型字段无遗漏，Java注解独行检查0项。449个变更Markdown本地目标/锚点无缺失。旧JaCoCo指南在提交核对中已从起点main精确恢复并核对内容相同，最终不删除该原文件；历史快照中的对应引用固定到当时Git版本。

当前图源：`docs/design/ddd-diagrams.json`、`scripts/build-ddd-design.mjs`、`scripts/build-structured-design.mjs`、`scripts/build-identity-design.py`。当前82组包括包2、类32、差异顺序48；接收方法、共同流程禁入、几何及循环/条件边界由生成器核对，视觉检查页在本轮`visual-review/manifest.json`。旧图保存在`docs/history/design-assets/through-r32`，不参与当前生成。

已打开22页检查图覆盖82幅PNG，并复看身份/人员类图及权限树、导入、维护保护等原图。审核发现的权限修改分支遗漏、opt条件文字重叠和structured成员选取偏题均已修正：四项字段opt与DDD两条alt对应源码，条件和首消息留足行距，类成员按模块明确选择并核实声明，不再按源码顺序截取。最终视觉及源码复核通过。

<a id="jwt-design-cleanup-4-apifox同步"></a>

### 4. Apifox同步

目标项目9042431、模块8887289、主分支8854997。写前导出备份；本轮更新管理登录说明、2模型JWT字段及管理JWT组件，接口创建/删除均0、失败均0。平台保留的模块info经完整读取、官方CLI Schema校验后更新，最终官方导出1.5.0、37操作、71模型，与本地操作/模型/鉴权/info比较差异0。

“应用身份”原目录100273904是R32分组移动遗留，已逐项核实全部37接口、子目录及项目Markdown文档均不在其下，再删除该唯一空目录并回读目录树。“统一身份鉴权”8项及其他管理接口保留。证据见本轮`apifox-before.json`、`apifox-import.json`、`apifox-final.json`、`apifox-comparison.json`，空目录核对/删除结果在`.local/apifox/r33-*`，不写老师共享项目。

<a id="jwt-design-cleanup-5-审核与边界"></a>

### 5. 审核与边界

按用户代码/UML skill及code-review skill进行fresh-context规范/需求双轴审核，修复后最终规范轴0项、需求轴0项。审核是静态检查，不冒充独立回归。原老师工程工作树干净、当前提交990cea2，副本仅原3个structured生产补丁；不关闭T14/T15，不部署服务器，不修改详设外部文件。沿本会话用户将有效分支合入main的指示，在验证和审核通过后交付；实际PR及提交状态以GitHub为准。

<a id="identity-eight-contract"></a>

## R32完整8项统一身份鉴权验证

日期：2026-11-21。任务起点`0f1848ebd796ac9b48190c05eee976874545811e`，业务实现提交`29ab1d20de85252c19b0eedbfe34a15c72a658eb`，任务分支`codex/identity-eight-contract`。用户随后明确授权有效分支合入main，PR #38/#39/#40已按依赖顺序合入，并由`68ace41`将远端main纳入本轮验证分支。后续提交只补契约错误枚举、操作别名、文档/图件及验收探针；不以之前195项成绩代替本轮结果。

<a id="identity-eight-contract-1-交付范围与边界"></a>

### 1. 交付范围与边界

依据[需求R32](../../requirements.md#71-身份协议与令牌)及[完整身份设计](../../backend-design.md#18-通用应用身份与课程本地接入)，交付登录、退出、本人、短码判断、部门目录、部门直属用户、operationId权限判断、按统一身份查人共8项。新增的是退出、部门、部门直属用户及operationId权限判断4项，RBAC总操作数由33增至37；原29项管理操作和完整K17保持。

外部应用使用标准HS256 JWT，JOSE/Nimbus校验签名、issuer、单受众、主体与严格到期边界；只含身份和会话字段，不含角色权限快照。管理端仍独立随机Bearer，双方不能混用。进程内会话索引实现单令牌即时退出和重启失效；业务授权实时读数据库。目录按当前人员部门子树控制，逐层返回直接子部门，直属用户只给最小5字段；page从1、pageSize默认20最多100。查询和总数在同一数据库快照中计算。

operationId由显式`IDENTITY_OPERATIONS_FILE`登记，不推断应用或URL。课程目录包含真实课程操作及老师/本项目身份operationId别名，共91条；混合入口的配置角色按permissionRoles判断全局门禁，对象关系仍由课程Service校验。未知操作404，重复/损坏配置阻止启动。统一身份审计另2项依用户澄清不纳入，独立本地支撑继续保存原课程业务审计。

不新增Teacher/Student实体、应用白名单、数据库表或升级SQL，不扩大自定义角色委托能力，不补老师已记录的5个Controller缺口、个人制批改等业务。详设Word/PDF/PPT及讲稿保持原版，不部署服务器、不关闭T14/T15。

<a id="identity-eight-contract-2-环境与执行"></a>

### 2. 环境与执行

Windows、JDK21.0.13、Maven3.8.5、MySQL8.4.8，真实CloneFactory0.0.2。沿用R31本机独立实例：structured为3351、DDD为3352，各有6个回归专库及独立`rbac_core`。原3349/3350、32人夹具、万人库和历史日志保留。

完整回归命令为`mvn -B -ntp -o -f backend/pom.xml clean package`，显式设置各策略和6专库环境变量。默认JAR身份探针用`scripts/verify_identity_contract.py`，课程场景用`scripts/verify_course_initialization.py`；后者对R31旧库用`--initialization-catalog`指向原目录快照，以保留原回执，不用新增目录覆盖旧完成记录。

本轮只在两验收库显式登记IDR/IDU对应2权限、2API及管理员角色关联，无重导入、无重置原有角色集合。逐一复核10001人的角色、课程绑定及独立密码哈希，仍为191教师（190员工与admin）、9810学生、1名同账号兼任管理员。

<a id="identity-eight-contract-3-本轮结果"></a>

### 3. 本轮结果

| 检查 | 结果与证据边界 |
| --- | --- |
| TDD | 新增身份行为8项先RED后GREEN；JWT严格到期单测先RED后GREEN；gateway真实HTTP stub先404 RED后修复GREEN |
| structured完整MySQL回归 | 208项，失败/错误/跳过均0，clean package成功 |
| DDD完整MySQL回归 | 208项，失败/错误/跳过均0，clean package成功；采用`full-regression-resumed.log`，暂停中断的旧日志不作通过证据 |
| 独立JAR身份HTTP | 两策略各61次通过，覆盖8项直连/转接、签名篡改、受众隔离、目录范围与最小字段、分页、别名、退出即时失效、管理API停用/恢复后原JWT即时生效 |
| 独立JAR课程HTTP | 两策略各43次通过，逐人核10001绑定及口令哈希；真实管理员、总部员工/领导、分公司员工/领导和授撤权场景 |
| Python | 25项通过，包括gateway真实HTTP stub转接和204空响应 |
| 浏览器 | 第一组正式Vue24项通过；课程“添加教师”部门树、直属人员搜索选人另在127.0.0.1及localhost均通过，未提交创建教师。未将第二组HTTP结果称为第二组完整浏览器复验 |
| RBAC前端 | 27项测试及生产构建通过 |
| 老师Vue副本 | vue-tsc类型检查与生产构建通过，无Vue源码修改 |
| CloneFactory | 两策略生成文件SHA256相同；53个非空重载、185组可变同名同类型字段无遗漏；final/record继续显式构造 |
| Java注解 | 生产/测试词法独行检查0项；旧简易正则的TYPE_USE候选不作违规结论 |
| 机器契约 | OpenAPI3.1校验通过，YAML=JSON；37操作、31路径、71 Schema、22错误、K17；15个POST/PUT/PATCH中13个有body，另2个为退出操作，空对象body为0 |
| 真实响应Schema | 两策略共22个实际HTTP响应校验通过，覆盖全部8项、204空响应及3种新增404错误；不输出或保存JWT |
| 文档链接 | 本轮变更Markdown的285个本地目标/标题锚点均存在，git diff --check通过 |
| UML | 更新DDD身份类图，新增structured身份类图与两份目录差异顺序图，同源puml/svg/png；核实际类/方法并视觉检查，共享HTTP/JWT/SQL不在两份差异图重复绘制 |
| 老师目录 | 原目录工作树干净，副本相对于原接入来源仍仅3个structured Java补丁；原目录后来独立更新，未用旧来源摘要错误宣称其全部文件未变 |

原始报告在忽略目录`.local/identity-eight-20261121`：各策略`full-regression*.log`、Surefire、JAR、`identity-http.json`、`course-http.json`；公共`python-tests.log`、`browser-result.json`、`directory-browser.json`、`mapping-check.log`和`artifact-check.json`。凭据、数据库、完整日志与构建产物不提交。

<a id="identity-eight-contract-4-apifox同步与回读"></a>

### 4. Apifox同步与回读

用户明确授权同步项目9042431，模块8887289，主分支8854997。写前导出备份；用Node fetch官方导入，新增4接口、更新7接口、新增5模型、更新2模型及新增JWT组件；endpoint/schema/securityScheme失败均0，未删除其他资源。平台保留旧鉴权及模块版本元数据后，经CLI完整读取、Schema校验，再显式修正7项JWT入口及模块信息。

最终官方导出回读为1.4.0、37操作、71模型，全部8项在“统一身份鉴权”目录，匿名login、JWT鉴权和204空退出响应均一致。比较器展开公共参数/响应引用并忽略平台装饰，操作、模型、鉴权组件及info的语义差异为0。证据为`apifox-before.json`、`apifox-import.json`、`apifox-final.json`及`apifox-comparison.json`。公共文档入口为[本项目Apifox](https://lmbnuppe75.apifox.cn)，不写入老师共享项目。

<a id="identity-eight-contract-5-审核与合入"></a>

### 5. 审核与合入

按code-review skill进行fresh-context双轴静态审核：规范初审0项；需求初审2项P2（公共Error缺3个新404编码、现行文档与图件未完整同步），均修复。收尾复核继续修正旧接口总述/README、HTTP错误状态说明、R32链接及类图标签位置，最终规范轴0项、需求轴0项。审核不冒充独立测试执行。用户已授权PR #38/#39/#40及本轮有效分支合入main，旧UI分支用祖先关系核对，分支暂不删除；GitHub合并状态以实际PR为准。

<a id="course-initialization-r31"></a>

## R31课程首次身份与CloneFactory0.0.2验证

日期：2026-11-21。任务起点为f70ec359f04949c2fe33d3bf562867d0a86ca52f，工作分支codex/course-identity-initialization。本记录只报告本轮实测；不合main、不关闭T14/T15、不推Apifox、不部署服务器。

<a id="course-initialization-r31-1-变更及来源"></a>

### 1. 变更及来源

用户明确本部含职能部门和项目，排除分公司子树；本部员工包括领导与普通员工均teacher，其余student，RBAC管理员兼course-admin。规则由独立Python接入入口及配置执行，不加入RBAC核心实体/应用服务。默认导入和五角色授权仍只登记RBAC。

用户提供的spring-fundamentals工作树干净，提交40583ff94764d2e758bcb13be17f5c05ea4d6352，CloneFactory实际版本0.0.2。从git archive源码副本执行clean install，老师5项测试通过；普通provided依赖与annotationProcessorPaths统一引用0.0.2属性。新版支持record/final构造器映射；本项目现有不可变领域映射继续显式构造。

课程运行副本仍固定原19fbd1a及既有3项structured修复，没有升级老师整套业务。核对时老师原目录已由外部更新到e9a6dff（部署说明），工作树干净；因此不能声称原目录仍与旧19fbd1a逐文件相同，本轮不采用该部署说明开展部署。

<a id="course-initialization-r31-2-独立环境与首次数据"></a>

### 2. 独立环境与首次数据

| 组合 | MySQL | RBAC | 课程structured | 身份及业务审计支撑 |
| --- | ---: | ---: | ---: | ---: |
| structured | 3351 | 8081 | 8080 | 8085 |
| DDD | 3352 | 8083 | 8084 | 8086 |

两套全新本机实例均有独立rbac_core/course_project及六个回归专库。原3349/3350、原32人夹具及历史日志保留。正式Vue5173/老师Vue5174当前连接第一组，mock关闭。

两套均实际完成原Java万人导入、五角色授权、老师Flyway及新课程接入，逐人核对10001账号的角色与user_id绑定：190名本部员工加admin共191教师、9810学生、1名兼任课程管理员。10001个BCrypt10哈希各不相同。首次万人BCrypt导入在CloneFactory升级前使用既有已验JAR完成；升级后新JAR实际完成两库10001人重复导入及首次授权回执核对，不将其误记为新版全新万人导入。

<a id="course-initialization-r31-3-本轮实测"></a>

### 3. 本轮实测

| 检查 | 实际结果 |
| --- | --- |
| 新初始化TDD | policy-red.log及safety-red.log保存行为RED，随后新增14项GREEN |
| Python课程辅助测试 | 初始新14项加原8项为22项；审核补2项安全回归，最终24项通过 |
| structured真实MySQL完整回归 | clean package，195项，失败/错误/跳过均0 |
| DDD真实MySQL完整回归 | clean package，195项，失败/错误/跳过均0 |
| 新0.0.2独立JAR HTTP | structured43次、DDD51次，全部符合预期；含预期401/403/404 |
| 客户端安全修复后最终HTTP | structured43次、DDD43次，分别复验全量绑定、教学场景、撤权及重复初始化 |
| Chrome真实生产Vue | 第一组24项检查通过，18张页面截图；第二组由真实HTTP覆盖，未宣称双组浏览器复验 |
| RBAC前端 | 27项测试及生产构建通过 |
| 老师Vue | vue-tsc及生产构建通过 |
| CloneFactory生成专项 | 两策略生成文件SHA-256相同；53个重载均非空，194处赋值，185组可变同名同类型字段无遗漏 |
| 注解扫描 | 3个候选均为误报（2处@interface声明、1处Column字符串），实际同行注解0项 |
| API | OpenAPI YAML/JSON同源，仍33操作、27路径、66 Schema，17项完整K；本轮无新增HTTP接口 |

HTTP覆盖同一admin双系统登录、本部领导与普通员工教师、分公司领导与普通员工学生、错误身份入口拒绝、普通教师不能读全局目录、跨受众令牌拒绝。通过课程生产API建立演示学期、开放分组课程、教学班、两名任课教师、两名选课学生及作业里程碑。重复保存组长保持关联，教师读名册，学生读开放里程碑，管理员读教学班和独立持久业务日志。

真实RBAC撤回学生角色LOGIN后，原课程token下一请求403；此时重跑初始化跳过，仍403；恢复原完整权限后原token200。重复初始化不覆盖运行期授权。structured本轮复用了升级前已建的演示课程，故少8次创建请求，覆盖场景相同。

新JAR SHA-256：

- structured：`7f51e313290808b6222cff0fedfbea53936bece83bbda0808754d5ad9741e93a`
- DDD：`09684fb36ba7313802431a9346423264e633a352d6a469e84cb153f111dc346b`
- 两策略生成CloneFactory：`d34cb572a58e5c0cf3408b45813dba7acbdb167eec1d98837b1e33b7c53490e6`

<a id="course-initialization-r31-4-证据与限制"></a>

### 4. 证据与限制

原始日志、报告、JAR、截图与秘密均在忽略目录`.local/course-initialization-20261121`。主要文件：python-tests.log、clonefactory-build.log、mapping-scan.log、browser/result.json，以及各策略的clonefactory-002-full.log、course-preview/applied/repeated.json、http-clonefactory-002.json、repeat-002-ImportCommand.log和repeat-002-BootstrapAuthorizationCommand.log。含凭据的配置不提交或在聊天输出。

双轴初审：需求0项；规范发现1项MySQL默认配置可能覆盖本机目标/继承force。已以真实客户端复现RED，修复为指定defaults-file、no-login-paths、显式loopback/端口/TCP及skip-force。真实GREEN确认配置覆盖不能改变目标，SQL失败后后续语句不执行；新增2项测试先RED后GREEN，最终24项通过，两组最终http-final.json各43项通过。日志为mysql-options-red.log、mysql-options-real-red/green.log。详设Word/PDF及提交包依用户最新要求不更新。

e2e4b8b后两轴独立复核：Standards硬规范0项、判断性问题0项，Spec0项。249个受影响Markdown本地文件目标存在；课程副本对固定原始快照仍仅上述3个Java文件有差异。桌面体验说明只在本地保存，不进入Git；包含两系统入口、代表账号和临时口令。

首次接入在开放业务前维护窗口串行执行，课程及RBAC分别提交，不是分布式事务；运行期调动、后续管理员变更不自动重推课程身份。旧32人夹具不是升级目标。老师5项Controller缺失、个人制批改、学生建组归属和DDD当前学期等限制继续见[课程接入第7节](../../course-integration.md#6-老师业务的已知限制)。本轮没有扩写这些业务，也未测得吞吐量或服务器部署结论。

<a id="course-initialization-r31-5-本机入口问题复现与修复"></a>

### 5. 本机入口问题复现与修复

用户反馈admin似乎不在人员名单且课程登录显示“请求失败，请稍后重试”。真实Chrome确认：搜索admin可以看到系统管理员、课程教师资格和课程管理员；未筛选时按ID升序，10001条数据每页20条，admin在第501页（该页1条）。记录为admin-pagination-check.json及browser/admin-search-recheck.png，没有修改人员排序或账号数据。

课程页从127.0.0.1:5174登录200，但localhost:5174可复现浏览器CORS拒绝，页面显示同样的通用错误。原配置只显式允许127.0.0.1，两个域名不是同一Origin。仅调整第一组课程CORS_ALLOWED_ORIGINS及本地支撑的两个--origin，重启课程和支撑，未重启RBAC或数据库，不改变口令、授权和绑定。修复后真实Chrome从两个入口均完成POST /login与GET /me，各返回200且无CORS错误，admin人员搜索仍正常；用户随后确认可以登录。

证据位于.local/course-initialization-20261121的admin-login-diagnostic.json、diagnose-admin-login.mjs、course/gateway-access日志和restart-course-access.ps1。此项是本地配置修复，不修改老师原工程、课程生产补丁或OpenAPI，不把此前195项完整回归算成新的执行成绩。

<a id="ddd-deployment-adaptation"></a>

## R34 双DDD部署前适配（2026-11-22）

<a id="ddd-deployment-adaptation-1-授权与基线"></a>

### 1. 授权与基线

用户提供老师deploy/README、五台SSH服务器，先要求方案，随后明确两个后端均使用DDD、其余完成适配；补充询问菜单对应Logback是否要改前端。此次完成部署材料与本机隔离验证，不将部署方案当作服务器已上线。起点main bdfb3e0294ebfbaeb7340f9a9d516eb2f81a4a33；任务分支codex/ddd-swarm-adaptation。详设Word/PDF/PPT不改，T14/T15不关闭。

老师原目录干净提交990cea2cc3686599ef5068945706a13e7e0afc5b，原目录不写入。与历史19fbd1a相比只有4个Java生产文件变化及1个新增，仍重新验证，不复用旧成绩。副本先保留原3处structured补丁，再增加DDD相同行为修复、学期标记释放、安全日志及相关回归。产品37项HTTP契约未变；Actuator属于内部运维入口，不并入业务OpenAPI或Apifox目录。

<a id="ddd-deployment-adaptation-2-实测环境与配置"></a>

### 2. 实测环境与配置

五台均2vCPU、可见内存约1800MiB、40GB、Ubuntu18.04.6、Docker20.10.21，无swap。A剩余约1.9GB；B/C/D/E是现有Swarm节点，C为唯一manager，A不加入运行集群。本轮SSH仅调查；尝试临时目录中的配置语法核对后即清理，没有创建/更新/删除Swarm服务，没有清理镜像、数据卷或旧实验资料。

配置将MySQL放B，两个Nginx和Prometheus放C，课程DDD放D，RBAC DDD及独立Waitress服务放E。仅C发布host模式TCP80/8088，Java、MySQL和监控留overlay。正式HTTP入口、Secrets、单副本、stop-first、轮转、第一次导入/预览/绑定/授权步骤见[部署说明](../../../deploy/README.md)。当前会话索引仍在RBAC内存中，重启需重新登录。

公网/auth直接访问通用RBAC八项协议，课程前端的专用投影经/course-identity挂载独立接入服务；课程后端仍使用Swarm内部地址。这样不把课程单应用准入约束施加给其他数据库登记的应用，也不新增RBAC课程Controller。课程Vue以此最终baseURL重新类型检查及生产构建。

Dockerfile已适配backend目录及真实CloneFactory0.0.2固定提交；两个前端正式构建、两个Java镜像、独立接入服务分别构建。Java镜像构建显式跳过测试，不能算回归。服务器自带Docker CLI没有stack config子命令；本轮使用临时官方27.5.1客户端解析完整Stack，并用服务器bash检查五个脚本语法，临时文件随后清理；另有PyYAML和引用文件核对。没有安装替换系统Docker，也不伪报镜像构建或Stack已经上线。

<a id="ddd-deployment-adaptation-3-red与修复"></a>

### 3. RED与修复

1. 学期测试采用已提交的两个学期及独立Service事务；原DDD触发term.uk_term_current_marker并返回TERM_CURRENT_CONFLICT。新仓储批量SQL先释放标记、清理上下文，再由Service重读并保存目标；不把未带身份的认证失败计作RED。
2. 老师新登录服务直接打印含accessToken的LoginResult；统一日志规范RED。部署补丁移除两套登录直接日志，由统一切面记录开始/完成和异常类型；外部HTTP日志集中到切面，删去异常消息、完整URI查询串、Header/Body。
3. 原绑定工具遇到老师dump工号user_id时拒绝。新的离线首发工具只在新库按工号校正，冲突失败停止，原业务主键保留。另两项演示身份保留且不自动授权；原本机初始化工具的严格边界不放宽。
4. 新网关最初把过期会话退出转成401；契约RED后恢复上游204幂等空体。真实HTTP首连MySQL8.4发现RSA依赖缺失，补齐PyMySQL[rsa]/cryptography并重新运行；不通过降低认证方式绕过。
5. 实测发现全局ApiResponseAdvice将Health包进业务JSON、使Prometheus返回500。真实HTTP回归RED后，将Advice限定业务API包，保留Actuator原始JSON/文本及身份RawResponse；当前两策略各213项包含此回归。
6. 全量测试的旧部署健康检查预期改为真实Actuator；老师测试默认令牌含全部管理权限，教学班负例显式改用无CV/CTM的教师身份，并同页验证真实全局CV可读。日志测试验证统一切面及Header/Body/查询串均不泄露，不删除负例或放宽预期。

审核另修复官方MySQL入口source脚本时的nounset不兼容、初始化短任务等待/私有仓库认证、测试摘要旧路径。两轴修复复核各0遗留项。

<a id="ddd-deployment-adaptation-4-当前源码实际结果"></a>

### 4. 当前源码实际结果

| 范围 | 本轮结果 |
| --- | --- |
| RBAC真实MySQL全量 | DDD213、structured213，失败/错误/跳过均0 |
| 老师部署副本真实MySQL全量 | 两策略及架构等499项，失败/错误/跳过均0；含新增独立事务学期回归 |
| 两DDD独立JAR+正式Waitress/MySQL接入 | 最终133次真实HTTP，全部符合预期，含预期拒绝与老师已有缺口 |
| 内部监控与接入服务重启 | 8次真实HTTP，两个DDD健康和Prometheus原始响应正确；Waitress重启后原JWT有效、MySQL业务审计原样回读 |
| Python辅助脚本 | 38项通过，含真实MySQL并发幂等、重建存储回读、应用分区、数据库分页和首发绑定 |
| RBAC前端 | 27项测试、生产构建通过；真实Chrome六个菜单均HTTP200且出现对应Controller与DDD Service日志 |
| 老师Vue | 新副本关闭mock、/api及公网统一身份地址，类型检查与生产构建通过；Vue业务源码未改 |
| 原dump绑定 | 原SQL导入3353新course_dump库；10000条原工号原位校正、补建1名admin；逐人核对10001绑定、原业务ID不变、再次计划0变更 |
| 有效与物理人数 | 191有效教师、9810有效学生、1同账号管理员；保留演示记录后物理teacher192/student9811 |

首次HTTP曾通过136次，最终JAR复验为133次；已有虚构场景的条件创建请求不重复，不以旧次数覆盖本轮结果。

六个菜单验证使用正式构建的本机预览及真实DDD JAR、无网络mock。课堂现场还需在C实际Nginx入口及docker service logs中重做，不拿本机预览冒充Swarm运行证据。源副本和第一次失败报告均保留在.local/deployment-adaptation-20261122，不提交凭据、个人明细、运行产物或原始日志。

<a id="ddd-deployment-adaptation-5-未执行范围"></a>

### 5. 未执行范围

未清退旧服务、未清理A磁盘、未创建云端Secrets/新库、未构建推送服务器镜像或发布Stack、未执行公网浏览器/服务器重启/节点故障验收。镜像仓库使用操作者有写权限的SWR命名空间，模板不假设老师仓库可写。部署操作和云端结果另行记录。

课程缺Controller的5个入口、个人制批改409、学生建组负责班级等既有业务限制继续按[课程接入](../../course-integration.md)披露；本轮只修复已确认接入阻塞，不声称老师全部业务完成。独立业务审计仍是已认证调用者上报事件，不伪装成课程事务提交证明。

<a id="course-integration-generic"></a>

## 通用应用接入与课程本地验证

日期：2026-11-20；分支：codex/course-local-integration；main基线717345f。业务实现与审核修复快照为09c2093、ee629a9，后续仅补浏览器定位和验收记录。现行设计见[课程接入](../../course-integration.md)，此前教师最小试验只作为历史。

<a id="course-integration-generic-最终验证"></a>

### 最终验证

| 检查 | structured | ddd |
| --- | --- | --- |
| RBAC真实MySQL完整clean package | 195项，失败/错误/跳过0 | 195项，失败/错误/跳过0 |
| 独立JAR对接老师structured的真实HTTP | 141次全部符合预期 | 142次全部符合预期 |
| 两个正式Vue的真实Chrome交互 | 27项通过 | 27项通过 |

HTTP计数包含实际允许和预期401/403/404/409，其中个人制批改列表的409是老师来源缺口，不代表该业务已经完成。两策略场景相同；次数相差1是新增教师时遇到此前软删除的虚构候选，按老师规则409后换下一候选，未清空历史来制造通过。

RBAC前端27项Node测试及生产构建通过；课程原始Vue的TypeScript检查/构建通过。辅助脚本8项通过，覆盖持久审计幂等/隔离、chunked请求体、多路由权限部分撤回失败关闭、临时授权故障恢复及夹具目录。

真实浏览器覆盖学生课程/个人提交/组队/口试/成绩，教师课程/教学班/名册/小组/里程碑与批改工作区，课程管理员课程/学期/教师/日志/团队。另在RBAC页面撤回菜单和按钮并观察原课程令牌后续结果，恢复后原登录继续可用。新增检查核实RBAC及课程中文权限名称、稳定编码和局部管理员锁定角色，不开启网络mock、不录制密码或令牌。

<a id="course-integration-generic-关键修复与clonefactory"></a>

### 关键修复与CloneFactory

- 自定义角色不冻结人员。写事务锁后重读数据库集合，对角色新增/删除逐项验权，锁定项可原样保留；局部管理者仍受人员级别及部门范围约束，自定义项仍仅管理员可增删。真实HTTP与页面均验证。
- 目录从已登记Application读取；RBAC不写课程身份、业务模型或路由白名单。老师角色与粗粒度路由需求仅在接入配置中表达。
- /auth/me提供通用permissionApis事实；课程适配器核实同一短码的全部必要路由。部分撤回TCM映射先复现204错误放行，修复后真实DELETE/PUT均403，恢复映射后原令牌可用。
- 新身份查人端点补正安全整数上限；角色数组去除旧maxItems=1。统一过滤器记录实际查人授权允许/拒绝，门禁通过但目标404仍为allowed=true；不把元数据查询伪装成课程业务执行。
- R30中文权限名由两套首次授权共用目录提供，当前虚构库13项RBAC及33项课程名称均已同步，编码/ID/关联不变；前端不按英文码硬翻译。

clean生成的真实CloneFactory共53个重载，全部有字段赋值，空映射0；185组可变同名同类型字段核对无缺失。新增ApplicationLoginDTO.password及UserRolesResult.lockedRoleIds生成赋值已明确核实。record/final按字段显式构造。注解独行扫描0项。OpenAPI共33操作、17项K，YAML/JSON同源且3.1校验通过。

<a id="course-integration-generic-standards"></a>

### Standards

两个fresh-context子代理按指定code-review skill独立审查。首次规范轴发现1项：身份查人缺安全整数上限；已补API层@Max、同源Schema及行为回归，复核剩余0项，判断项0项。浏览器精确文本定位的额外风险已改为按编码定位行，并由真实27项交互确认。

<a id="course-integration-generic-spec"></a>

### Spec

首次需求轴发现2项：身份角色数组旧单项上限、查人鉴权允许日志遗漏。均已修复并复核为0项；新增安全边界与审计回归先观察到真实行为RED，再14项GREEN。固定scenario路径的闲置副作用已移除，报告父目录支持新建。没有新增委托机制、擅自课程数据权限或另一套RBAC。

最终：Standards硬违规0、判断项0；Spec剩余0。

<a id="course-integration-generic-环境、原件与必要改动"></a>

### 环境、原件与必要改动

原3349实例及历史数据全部保留；本轮新建3350实例，含rbac_core/course_project和六个隔离回归库。初始32虚构RBAC账号、4本地教师、3本地学生、2课程、2教学班、4里程碑。真实写入后保留虚构申请课程、作业、成绩及审计，不通过重导入覆盖这些状态。口试预约上限100是已有课程配置，便于重复体验；未修改老师默认值或清理预约历史。

老师原目录提交19fbd1a的943个来源文件SHA-256不变；副本仅改StructuredCourseService、StructuredMilestoneService、StructuredTeachingClassService三个生产文件。修复重复保留课程组长、已选课学生读取里程碑、持CV/CTM管理员读取教学班；[补丁及清单](../../../integrations/course/README.md)可相对原件git apply --check。未改老师pom、Flyway或Vue；旧额外教师名单安全补丁撤除。

本地外部支撑8085实时转接RBAC身份并持久保存业务审计，归属查询使用独立SELECT账号；不混入RBAC鉴权日志，不作为服务器部署方案。当前运行RBAC DDD8081、课程structured8080、RBAC Vue5173、课程Vue5174。运行JAR与最终测试产物SHA-256一致。凭据、SQL、JAR、数据库、原始日志和截图均在.local/course-integration/full，不提交。

可核对产物：rbac-structured-full-final.log、rbac-ddd-full-final.log、structured-http.json、ddd-http.json、browser-structured/result.json、browser-ddd/result.json及对应PNG、mapping-audit.txt、artifact-check.json。原始账号口令在私有配置，不在报告中复制。

<a id="course-integration-generic-已知来源限制与评审关口"></a>

### 已知来源限制与评审关口

老师生产Controller缺少课程重置申请、单独description更新、小组GET详情、个人口试成绩、录音摘要5个文档路径；个人制教师批改列表明确409 COURSE_NOT_GROUP_BASED，其详情/批改链未完成。学生建组尚缺负责教学班归属，口试完整链使用老师已有教师建组入口。普通教师对象范围的外部审计读取未另行实现，本轮日志演示为持AV课程管理员。老师DDD当前学期切换409的旧问题未改；不宣称老师DDD整套验收通过。

这些来源缺口不解释为RBAC授权不足，不通过提权或在RBAC补课程业务掩盖。T14 #15负责真实场景/身份/原型与契约，T15 #16负责两系统授撤权及隔离证据，仍open供小组评审。本分支不合main、不关闭票、不部署服务器，也不推送Apifox。

<a id="t13"></a>

## T13 当前RBAC后端集成验收

> 历史记录：本页对应下文固定版本和当时目录。2026-11-13后端归类后，当前运行入口与阶段结果见[代码归类](../../backend-design.md#3-包结构)及[测试说明](../../testing-acceptance.md)；本页原成绩不覆盖后续改动。

<a id="t13-1-对象与边界"></a>

### 1. 对象与边界

需求/接口依据为[需求说明](../../requirements.md)、[正式契约](../../api-contract.md)和[验收矩阵](../../testing-acceptance.md)。共同基线32f0361包含文档PR #27及T12；当前生产实现仍为结构化Service，本票不移动源码、不实现DDD、不启动T14/T15。

验收包括实际JDK21、真实MySQL空库/HTTP、初始化来源、当前授权及范围、三类写入口保护/回滚/并发、默认jar与再次启动。既有业务测试本轮重新运行，不拿T12结果代替。全量正式前端、DDD、课程双业务系统及云端部署分别记录为未验收。

<a id="t13-2-可复现步骤"></a>

### 2. 可复现步骤

先按[部署说明](../../../deploy/README.md)安装老师固定CloneFactory依赖。准备工作区共同根.local内的全新MySQL8.4私有datadir，实例只绑定127.0.0.1的非3306端口；创建空rbac_core与仅本轮实例可用的账号，不使用业务库或已有数据。本票运行器要求实际返回的@@datadir与指定路径完全一致，并拒绝非空库，不提供清空/覆盖开关。

回归使用六个独立空库：rbac_core_t02_test、rbac_core_t03_test、rbac_core_t05_test、rbac_core_t07_test、rbac_core_t09_test、rbac_core_t11_test，均在该私有实例。分别设置RBAC_CORE_TEST_DB_URL、RBAC_CORE_IMPORT_TEST_DB_URL、RBAC_CORE_HTTP_TEST_DB_URL、RBAC_CORE_RESOURCE_TEST_DB_URL、RBAC_CORE_ROLE_TEST_DB_URL、RBAC_CORE_USER_TEST_DB_URL，另设置共用RBAC_CORE_TEST_DB_USER/PASSWORD。URL采用本机实际端口及UTC参数，严格匹配profile安全守卫。私有实例可关闭binlog，使有效故障注入触发器可运行；不恢复产品旧登录触发器。

在实际JDK21的JAVA_HOME下执行：

```powershell
mvn clean package '-DtestJvmArgs=-Xmx512m -Duser.timezone=UTC'
python -B -m unittest discover -s scripts/tests -v
```

完整package包含142项现有后端测试，不加skipTests；其事务/并发测试使用真实JPA/MySQL。Python安全单测只检查验收工具在接库前的环境保护，不模拟业务数据库。

默认JAR探针使用[scripts/accept-core-runtime.py](https://github.com/minxuanbo/ooad-course-project/blob/1149c24c83c2ff40108e59af73cf00916c326bf8/scripts/accept-core-runtime.py)。通过环境/秘密配置分别提供RBAC_CORE_DB_URL、RBAC_CORE_DB_USER、RBAC_CORE_DB_PASSWORD、RBAC_CORE_EMPLOYEE_PASSWORD、RBAC_CORE_ADMIN_PASSWORD；可选RBAC_CORE_ADMIN_USERNAME默认admin。指定RBAC_ACCEPTANCE_DATADIR为实际私有datadir绝对路径，MYSQL_CLIENT为mysql可执行文件，JAVA_HOME为实际JDK21。三份原始Excel留在仓库根，脚本核对已确认摘要，不接受任意替代来源。

清除遗留SPRING_*、Spring点号配置、loader及JVM配置注入变量后执行：

```powershell
python -B scripts/accept-core-runtime.py
```

脚本仅向已核实空库执行正式SQL，再调用正式导入、只读预览和实际首次授权；以生产BCrypt10逐账号盐生成，不使用测试profile。随后默认java -jar两轮真实HTTP验证。账号字段边界由该隔离实例SQL临时布置并恢复；产品没有因此新增人员资料编辑接口。人员授权、停用及调动均调用真实API。失败不自动清库，须保留证据并另建全新私有实例/空库复现。

Java子进程只接收白名单OS变量及精确核实的RBAC配置；额外Spring/JVM/loader覆盖在首个SQL之前拒绝，避免CLI检查私有库而Java误接其他库。脚本使用运行时require，不因python -O跳过安全检查。日志、CSV、环境摘要与结果留在共同工作区被忽略的.local/rbac-issues/t13-runtime-*，不得提交原始日志、秘密或万人清单。

<a id="t13-3-本轮记录"></a>

### 3. 本轮记录

2026-11-12本轮验收通过。回归与打包生产源码基线为32f0361；最终空库运行器固定在1149c24c83c2ff40108e59af73cf00916c326bf8，包含环境隔离修复及正确的权限拒绝断言。之后仅回写文档，不改变该运行器或生产源码。实际环境为Microsoft OpenJDK21.0.13.0.2+1-LTS、MySQL8.4.8、Maven3.8.5；便携JDK来自微软官方并核对官方SHA-256，不修改系统默认Java。

| 本轮检查 | 结果与证据入口 |
| --- | --- |
| 六个全新私有专库clean package | 142项，Failures/Errors/Skipped均0，BUILD SUCCESS；包含T02/T03真实JPA和T05/T07/T09/T11真实HTTP |
| 运行器安全回归 | 3项通过，覆盖12类Spring/JVM/loader额外配置在接库前拒绝、正常核实路径及Java环境白名单 |
| 空rbac_core正式SQL与生产初始化 | 111个组织来源、110个目标部门、10000员工+独立admin、初始全部启用；根直属11人；导入阶段无角色 |
| 预览与首次授权 | 预览不写授权，五角色初始人数1/64/106/72/9758 |
| 默认JAR独立端到端探针 | 70次真实HTTP响应，完整流程PASS；包含两轮默认启动，无测试profile或降低BCrypt强度 |
| 身份、权限、范围与查询 | 工号/手机号同id、权限及部门；普通员工拒绝、错口令/未知/歧义、无手机号/同值/前导零；部门树、筛选/分页/total/越界与404、退出撤销均通过 |
| 管理写与日志 | 角色创建/改名/选权、资源与API映射、人员授予/撤销/停用/双范围调动、最后管理员409回滚、日志快照/total/游标/手机号输入不入日志、部门日志403均通过 |
| 重复初始化与重启 | 保留已停用、撤空角色、调动及更换后的口令；重新登录管理员和查询日志正常 |
| 模型与资料 | 物理表恰为9核心+3支撑共12表，无旧运行模型；原Excel/ER SHA-256不变，旧实例/业务库未连接 |
| 最终产物 | 默认Start-Class为CoreRuntimeCommand；jar含真实CloneFactory依赖，无旧RbacApplication及schema.sql |

本轮jar SHA-256：`77d5c2ace20e272948a6b850b54fd373f5a78a4893890b43b8a649fced164f17`，用于核对本轮证据对应的实际产物，不要求未来重新构建具有同一时间戳/摘要。

本地原始证据位于共同工作区.local/rbac-issues：t13-java21-regression.log、t13-safety-green.log、t13-runtime-final.log、t13-evidence-summary.json；最终运行明细目录t13-runtime-1794475364218478700含environment.json、summary.json、正式离线/两轮默认启动日志。原始文件可能含环境/个人资料，不纳入Git；本节与测试源码提供公开可复核摘要。

审核发现运行器继承环境可能绕过指定数据源，已用前置拒绝/白名单修复并以3项安全单测验证；初次探针对部门无角色查询权限的错误码预期写成范围错误，已按“先权限后范围”修正。失败/诊断运行不作为最终通过证据，结论依据上述固定1149c24的新空库完整运行。

<a id="t13-4-需求到本轮证据的映射"></a>

### 4. 需求到本轮证据的映射

| 需求/验收组 | 重新执行的用例或探针阶段 |
| --- | --- |
| 原件、字段唯一、来源、初始化/重试 | CoreImportJpaT03Test、WorkbookImportPlannerT03Test及探针prepare/main；错误库/profile由T03/T07/T09/T11边界测试及运行器安全单测覆盖 |
| 双入口、准入、有效链OR、本人 | CoreRuntimeHttpT05Test的DualLoginAndManagementAdmission、IdentifierBoundaries、UsesCurrentPermissionsRatherThanRoleNames、RecomputesEnabledChainsAndOrMappings；探针verify_login_and_queries |
| 组合筛选、范围、分页total、日志脱敏 | T05的ScopesTreeAndPaginationInDatabase、RequiresGlobalScopeAndLogPermission、PaginatesLogsWithStableSnapshot及RequestAuditBoundaryTest；探针查询/日志阶段 |
| 人员权限ID子集、候选分页、调动/停用 | CoreUserAdminT11Test的AssignableRolesAreSubsetOfActor、RejectsDifferentPermissionForSameApi、RejectsExternalRoleGrants、LoadsOnlyRequestedCandidatePage、MovesWithinDoubleScope、DisableEndsSessionAndLogin；探针verify_management |
| 同用户完整K与资源/角色交叉并发 | T07 ProtectsCompleteMaintenanceCapabilities、SerializesConcurrentAdminChanges；T09 CountsCompleteUsersRatherThanRoles、SerializesWithResourceChanges、SerializesRoleWriters |
| 人员/角色交叉并发、锁后最新状态 | T11 SharesProtectionWithRoleMaintenance、ConcurrentRevocationsKeepOneAdministrator、RechecksTargetScopeAfterLockWait；T07 RechecksPermissionAfterWaitingForLock；T09 RechecksAfterWaiting |
| 三类写故障全回滚 | T07 RollsBackDatabaseFailure、T09 RollsBackStorageFailures、T11 RollsBackStorageFailures，均真实MySQL注入失败并比较持久化状态 |
| 契约/框架/旧物 | 四组真实HTTP测试的输入/范围/错误/响应映射、各边界测试及T12LegacyRemovalBoundaryTest；默认jar70响应、12表/主类/依赖检查；生产源码JPA、AOP、只读/写事务及真实CloneFactory静态核对 |

上述短方法名省略对应t05/t07/t09/t11前缀，实际测试源码位于src/test/java/com/municipal/rbaccore/runtime。它们在本轮实际JDK21/新MySQL库中重新执行；不是引用旧报告，也不是用模拟原型证明并发。两个独立新上下文代理分别审核Standards/Spec并回读最终日志、版本与产物摘要，最终剩余各0，确认Issue #14六条验收证据充分。这属于代理技术复核，不冒称老师/真人验收；用户已授权按复核无问题的流程合入，实际状态见[PR #28](https://github.com/minxuanbo/ooad-course-project/pull/28)和[Issue #14](https://github.com/minxuanbo/ooad-course-project/issues/14)。

<a id="t13-5-限制"></a>

### 5. 限制

功能回归与JaCoCo分别报告：本次不运行verify作为关卡，保留原覆盖率配置，不宣称100%覆盖。没有本地Docker引擎，镜像/Swarm和现场容器脚本未实测；本票默认jar及本机MySQL的结果不冒称线上结果。前端设计完整，但本票不宣称正式全量界面、DDD或课程系统已完成。T13完成后按用户要求停止，不启动其他任务。


<a id="application-onboarding-wizard-20261124"></a>

## 可视化接入与两系统初始范围（2026-11-24）

用户授权六步向导，随后提供第二次检查记录并确认仅保留RBAC及课程，同时要求同步材料、Apifox、部署、双仓推送和合入。老师00:06:05明确要求新增第三个系统，00:06:28明确清除其他目录。因此取消18项模拟目录的部署首次初始化，保留通用应用管理及PENDING配置阶段，不增加应用代码白名单。

本轮RED为新增向导端点/入口缺失的真实行为失败；另有已发布后身份刷新失败跳过复检的渲染RED。最终两策略各365项真实MySQL3356隔离回归，无失败/错误/跳过；两次clean的418份生产class逐文件SHA一致。合并覆盖率为指令30486、分支2534、行6019、复杂度2528、方法1254、类326，六项遗漏0，原Maven零遗漏检查通过。未降门槛或排除业务；当前唯一实体报告见最终JaCoCo，覆盖率不代表正式流量或性能。

新增R35共13项HTTP契约及解析/协议/编排边界：只读导入、完整指纹、关联/长度/来源边界、真实MySQL写故障全回滚、角色子集额外API、独立LOGIN撤回、元数据差量移除、动态精确Origin；专用HTTP桩覆盖协议故障，实际本机HTTP覆盖6项协议检查。两类技术故障与业务数据库证据分别理解。

前端54项单元、22项构建后真实Vue浏览器测试通过，其中发布后本人刷新失败仍自动复检并清空账号口令有直接用例。脚本初轮45项通过，正式向导检查又暴露原课程createClasses缺少真实API映射；新增直接用例先RED精确指出该操作，补齐CTM的POST /courses/{courseId}/classes后，最终Python46项全通过，包括任务私有course_audit的真实并发幂等/重建持久化两项，无跳过。目录为23唯一API、25映射，CTM四路由。Java/Vue未因此改变，不重复计算原回归成绩。73个真实CloneFactory重载均非空，15个record构造映射62参数逐项顺序核对，214组可变同名同类型字段无遗漏；Java注解独行扫描0项。

OpenAPI1.8.0为45操作、37路径、94模型，K17保持；Apifox9042431新增4向导操作及17模型，导入失败0，官方模块元数据更新后回读版本正确，规范化逐字段语义差异0，保留原真实服务器环境。JSON/YAML同源且OpenAPI3.1校验通过。

文档按职责归并接口对照、复现、物理字典和角色验证；旧验证归为三份主题档案，保留日期和版本限制。合并时误插代码围栏的锚点已修复，29份仓库Markdown链接检查0断链，97处需求测试方法定位刷新。现行88组图件为2包/34类/52差异顺序图，全部45项HTTP和两类离线入口有追溯，共同流程不画双份。桌面详设正文旧图另归并为同源图集，实际页数和发布结果由交付记录说明。

最终双轴审核发现的页面清理路径、现行18目录文字、14表计数、围栏锚点及R14资源页/向导名称职责冲突均已修复。此源码记录不声称本轮正式部署已经完成；正式镜像与数据保留另见部署档案。
