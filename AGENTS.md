# RBAC项目协作

2026-11-22 R36：用户要求集中反馈后再重新部署，最新限制覆盖R35即时发布授权；仅授权8项终态临时服务清理，归档Spec/任务/日志到C及本机并核验SHA后已删除，正式7服务前后列表不变、均1/1，数据卷/Secrets保留。源码网页列表/详情/总览统一Asia/Shanghai，筛选按北京时间显式转UTC；两个Java服务Stack设日志日期格式Asia/Shanghai/+08:00，Docker可选--timestamps采集前缀仍UTC。前端31测试/构建、三浏览器时区12项本地夹具展示、两JAR真实Boot日志8项探针通过；详见docs/history/verification/beijing-time-and-service-cleanup.md。没有新镜像/线上Stack更新/应用重启，时间修正仍待统一发布；API/数据库/Instant/JWT/两策略业务不改，不新增Apifox写入，原老师目录/详设/T14/T15保持。

2026-11-22 R35：用户明确授权正式部署两DDD及清退旧服务，并要求易输入初始口令。旧MySQL/Mongo/服务Spec/历史日志与A压测结果先备份核验，原卷、Excel和A原data保留；新七服务已发布C的80（课程）/8088（RBAC），各单副本stop-first。应用源码496ed3e，实际摘要见docs/history/verification/ddd-production-deployment.md；首次导入因VIP长空闲连接失败且整批0残留，改Java离线任务tasks.mysql直连后五阶段均退出0，另960秒对照证实VIP失败/直连成功。逐人核对10001角色及绑定、独立BCrypt10盐、191教师/9810学生/1管理员；原演示对象保留。容器补Fontconfig/中英文字体修复Excel500，JAR摘要不变；Linux校验去CR、curl下载修正。最终公网HTTP62、入口8、三服务重启10通过，Chrome六菜单逐项对应DDD日志、普通Chrome同admin双系统登录通过；课程三角色Playwright受本机代理拦截不计通过。三库新备份异机SHA/gzip通过，不宣称整机重启/故障切换/压测或恢复演练。口令仅Secrets及私有交付，不提交；37API未变、不新增Apifox写入，PR43保持草稿，不合main/不关T14/T15/不改详设或老师原目录。

2026-11-22 R34：用户提供老师部署说明并要求两系统均使用DDD，完成部署前适配。七服务Swarm配置、Dockerfile/真实CloneFactory0.0.2、Secrets/离线首发工具及独立Waitress/MySQL业务审计已准备；仅C公开80/8088，后端各单副本、stop-first。老师来源990cea2独立副本保留原structured三补丁，并修复DDD学期标记释放、组长差集、学生里程碑、管理员教学班及安全日志；原目录不改。RBAC各213、课程副本499、双DDD HTTP133及内部运维/重启8项、Python38、六菜单Chrome日志对应及两前端构建通过，详见docs/history/verification/ddd-deployment-adaptation.md；云端旧服务清退、镜像发布/部署尚未执行，不以本地证据替代。详设外部文件/T14/T15边界保持。

2026-11-22 R33当前规则：管理端与外部应用统一HS256身份JWT，JwtIdentityStore/JwtIdentityCodec替代原随机管理令牌；仍按aud隔离管理准入与各应用，不缓存权限，保留单会话即时撤销及重启失效。后端UML为16对类图、24对差异顺序图，37操作/两类离线入口逐项追溯；共同流程不得重复画。当前docs导航与验收已精简，旧阶段计划、设计说明、验收及重复图归history，历史数量按当时版本理解。规范仍用用户提供的20261117代码/UML skill，双轴审核；详设外部文件、老师原工程、服务器与T14/T15边界保持。以下R32/R31为阶段记录，不覆盖本条全JWT现行规则。

2026-11-21 R32：按老师共享契约补齐统一身份鉴权8项，外部应用采用无角色权限快照的HS256 JWT，管理端原Bearer保留；新增部门/直属人员分页及operationId权限查询，两策略独立决策、即时撤权，统一身份审计2项依用户澄清不纳入。当前OpenAPI1.4.0为37操作、31路径、71 Schema、22错误、K17。各208项真实MySQL回归与独立JAR身份61次/课程43次HTTP通过，Python25项、RBAC前端27项及两Vue构建通过，浏览器范围见docs/history/verification/identity-eight-contract.md。用户明确授权本轮同步Apifox9042431并将有效分支合入main，覆盖旧“本轮不合main/不写Apifox”限制；不删除分支、不关闭T14/T15、不部署服务器，详设Word/PDF/PPT不改。

2026-11-21 R31实测：两套新本机万人库逐人核对191教师（190员工+admin）、9810学生、1名同账号课程管理员。真实CloneFactory0.0.2下两策略各195项回归，最终HTTP各43项；第一组正式Vue24项交互、RBAC前端27项、课程构建通过。Python含MySQL配置覆盖/失败停止修复共24项通过，生成53个非空重载及185组字段无遗漏，双轴修复复核0项。见docs/history/verification/course-initialization-r31.md；详设Word/PDF/PPT依用户最新要求不改，服务器部署搁置。

2026-11-21新增R31：用户确认总公司本部（含本部职能部门/项目，排除分公司子树）领导和普通员工均为课程teacher，其余为student；启用、可信根直属、有效五级RBAC管理员兼course-admin且有课程教师绑定，同一账号管理两系统。规则由integrations/course显式初始化配置与独立脚本执行，不进入RBAC业务服务，不由身份开关自动执行。原32人夹具、原库及老师原目录保留；重复初始化不重置后续授权。部署仍等待老师说明。

2026-11-21用户提供spring-fundamentals干净提交40583ff94764d2e758bcb13be17f5c05ea4d6352：CloneFactory真实版本0.0.2，普通依赖与处理器统一属性。新版具备record/final构造器映射能力，不再沿用“库不支持final”的旧能力结论；本项目领域record/final仍按已确认规范显式构造。老师原目录保持不动，从忽略目录源码副本构建验证。

2026-11-20更新：课程本地接入按用户澄清改为通用应用目录和身份协议，不维护应用代码白名单、教师标记或课程专用RBAC服务。应用角色由配置数据表达；RBAC管理端准入保持原规则。自定义角色不冻结人员，角色差集锁后校验且不可改现有项可原样保留；自定义项仍仅可信根直属有效五级管理员可增删，不增加委托机制。老师原目录保留；副本仅修复structured课程组长重复保存、学生里程碑读取和管理员教学班读取，旧教师名单安全补丁撤除。外部业务审计由integrations/course独立本地支撑持久保存，不混入RBAC鉴权日志；来源限制和实测见docs/course-integration.md及本轮验证记录。未经要求不合main、不关闭T14/T15、不部署服务器。

2026-11-19阶段6 UI复验：structured通过PR #36交付窄屏日志计数、Modal键盘焦点及CollectionEditor失权事件修复，详见docs/history/verification/structured-ui-acceptance.md。ddd在包含PR #36的main提交292a8a4上，用相同用例和夹具在独立库复验。两种策略各通过13项浏览器用例、26项前端测试及生产构建；ddd的S6-01～S6-09补充用例及重启回读也通过，详见docs/history/verification/stage6-ddd.md。

截至2026-11-18，阶段5已完成双轴审核、修复及CloneFactory专项核对，用户批准PR #34合入main，合并提交e521f97，文件树与验收提交83dbf17一致。structured和ddd各178项后端测试、71次真实HTTP通过，前端26项测试/构建及运行器5项单测通过；两套空库各完成10001个BCrypt10账号导入及首次授权。桌面浏览器已复验登录、六个管理页面和关键写入；阶段6仍待窄屏、完整键盘及其他写入组合复验。组员从main复现并记录实际提交，见docs/reproduction.md及docs/testing-acceptance.md。T14/T15、课程接入及服务器部署仍按开发计划。

main核对后修复代码2fd8d40：日志追加独立事务先锁稳定RBAC系统目录行再取号、提交，查询继续使用同一快照；两策略独立校验员工/admin来源覆盖全部账号；生产与测试Java注解独行。两套真实MySQL完整回归各180项、前端26项及构建、运行器5项通过，CloneFactory51个生成重载及183组同名同类型字段已核对。新增两项共享回归在两策略均先RED后GREEN；规范/需求复核均0项，实际记录见测试说明，不以本次成绩替代此前独立JAR或浏览器证据。

RBAC本体业务依据为老师说明、原始资料和用户确认形成的docs/requirements.md；老师Gitee提供本体组织/框架参考，并在R29提供课程真实协议与实现来源。默认万人初始化仅登记RBAC；正式目录按数据库登记事实读取，课程接入配置独立角色/权限；18业务系统及矩阵仍仅在离线原型演示，不进入正式目录/初始化/授权子集。采用全新库首次SQL，不交付升级脚本。历史原型讨论以其时间语境保留在docs/history/design-decisions/prototype.md及requirements/sources.md，不覆盖最新范围。

HTTP字段主规范为api/openapi.yaml，JSON同源；业务语义见docs/api-contract.md。结构化实现归service/structured；service/port是共享用例/模型/协议取值，不放具体实现依赖或组织层级策略。共享基础设施不调用结构化或DDD应用服务，DDD持久化适配器实现领域仓储并映射领域模型。产品、导入与首次授权共用互斥装配，见docs/backend-design.md第4章。

后续生产代码与测试按用户2026-11-17提供新版projectmgt-code-conventions执行；本RBAC保留本地口令，分页沿page/size。领域仓储只输入输出domain.model及JDK容器，DomainPage装领域对象，复杂读模型用service.port Gateway；application不依赖JPA/Entity/结构化。final/record不可依赖CloneFactory写入，须显式构造；可变同名同类型字段仍优先真实处理器并核对生成赋值。注解独立换行、中文Javadoc、TDD、Service事务及锁后重鉴权沿新规范。2026-11-18用户授权修复日志快照、重复导入来源覆盖和注解格式，验证及审核通过后直接推送main；该明确授权优先于默认草稿PR流程。

两套策略分别保留业务实现，共用正式接口和数据模型。完整K保留原17项并要求有效基础级别5及可信根；人员目标/授予级别和部门规则见R23—R27。文档写目标职责，实际测试和限制独立记验收；不以历史成绩替代新版本通过。

按CONTRIBUTING.md协作，保留原Excel、用户未跟踪DOCX/Markdown、原库及历史日志；临时凭据/CSV/数据库/日志/产物不提交。按文件提交并以main为草稿PR目标。Apifox写入须明确要求。类图/顺序图用新版UML skill，图件应与代码实际类和方法对应；包图使用UML包语义，不宣称该skill适用于包图。顺序图只画两套差异，共同部分一律不画。

2026-11-20本轮交付：通用身份查人日志、安全ID与多角色Schema经双轴审核修复复核为0项；两RBAC各195项完整回归，真实HTTP structured141/ddd142、各27项浏览器通过，前端27项与构建、脚本8项通过。CloneFactory53个非空重载及185组同名同类型核对。R30中文权限名在两策略初始化、夹具、本地数据和文档一致；稳定编码不变。详见docs/history/verification/course-integration-generic.md，不以此覆盖老师原项目来源缺口。
