# RBAC项目协作

## 当前状态与工作边界

六步可视化接入向导及两系统范围已由PR60合入main并发布。用户已授权本轮docs/Apifox/桌面材料、部署、双仓推送与分支清理，不延伸为未来任务授权。保留动态新增第三个系统；PENDING用于未来新应用的配置阶段，不预置其余18项模拟目录。正式清理已先演练、三库双机备份并逐行核对，保留人员/角色及两系统授权、日志、原Excel和旧卷。

本轮最终源码双策略各365项隔离真实MySQL回归，失败/错误/跳过0，418份生产class一致；六项100%及原Maven零遗漏门槛通过。前端54单元、22构建渲染及最终46项Python通过；CloneFactory73个有效重载、15项构造映射62参数及214组可变同名同类型字段无遗漏。Apifox9042431的1.8.0契约为45操作/37路径/94模型，回读语义差异0。2026-11-24至25日已发布两个RBAC及接入服务镜像：104项正式HTTP、12组浏览器、39次API均200，七监控目标UP；原4136日志和人员密码/角色关系保留。具体摘要与边界见[本轮发布](docs/history/verification/release-evidence.md#application-onboarding-release-20261125)，不把旧性能算作重测。

上轮25fdc21应用管理已发布，见[原发布](docs/history/verification/release-evidence.md#application-management-release-20261124)；现有两个自定义验证角色须保留。课前一次发布授权不延伸为未来任务自动发布；源码或记录提交本身不自动重启正式服务。课程前端维持原样，不擅自扩写课程业务。T14/T15不自动关闭；Apifox仅在明确授权范围内写入并回读。文档按现行职责合并，版本证据集中归档；桌面仅保留最终PDF/PPT/Markdown/提交包/JaCoCo，原件私有备份保留。

用户2026-11-25另授权的当前系统已选权限摘要已仅发布RBAC前端，来源a297868；54项单元/25项构建渲染、正式9项只读HTTP及10项行为检查通过，19次浏览器API均200，七监控目标UP，其余七项服务Spec和任务未变，旧JWT仍有效，见[摘要发布](docs/history/verification/release-evidence.md#permission-selection-summary-release-20261125)。全部系统授权草稿与保存语义保留。仅项目docs归类同步，详设及课程身份不修改；本轮双仓合入及旧分支清理已获授权，不延伸为未来任务授权。

## 业务与架构

- 当前业务依据为[需求](docs/requirements.md)，字段主规范为api/openapi.yaml，JSON同源；业务语义见docs/api-contract.md。当前45操作、37路径、94 Schema，完整维护能力K17。
- 六核心实体、九核心表加五支撑表；管理员是普通User的有效基础级别/可信根/实际权限资格，不新增管理员字段。非管理员保护本人/同级/上级，管理员可相互管理，写后至少一名同一启用根用户具备有效五级、有效LOGIN及完整K。
- 自定义角色不提升级别或冻结人员。锁后读取现有角色、校验实际增删；不可改项锁定保留，全局自定义及应用预置项仅可信根直属有效五级管理员增删；部门角色另受所属部门子树、领导范围及完整权限子集约束，管理员定义，领导只分配/撤回。
- 两策略独立实现业务，共享API/Vue/SQL。structured在service/structured；service/port只放接口、模型及协议值，不依赖具体实现或承担组织策略。DDD application依赖domain.model/repository及port，不能依赖JPA/Entity/structured；领域仓储仅领域模型和JDK容器，复杂读模型用Gateway。共享基础设施不能调用两套具体应用服务。
- 管理端与外部应用统一HS256身份JWT，通过Bearer携带，aud隔离；无角色权限快照，逐请求读库、单会话即时撤销、RBAC重启失效。管理端要求有效RBAC登录权限；只授登录可进空工作台，业务读取与操作另行授权。
- Application按数据库登记事实读取，不维护应用代码白名单、教师标记或课程实体。默认万人初始化仅RBAC；课程独立登记CONNECTED，首次完成后共两应用。其余原表18项不进入正式初始化，相关批量初始化入口已移除。未来通过应用管理与接入向导登记第三个系统，不添加应用代码白名单。运行通行另查接入状态。角色可跨应用选权，Permission—ApiResource必须同应用。
- 课程teacher/student/course-admin由独立接入配置表达，Teacher/Student及user_id绑定只在课程库。本部（含职能部门/项目，排除分公司子树）员工为教师，其余为学生；合格RBAC管理员同账号兼课程管理员并有教师绑定。独立首次脚本显式执行，重复回执不重置后续维护，不宣称两库分布式原子事务。

## 来源与实现规范

代码skill：.local/rbac-issues/skills-20261117/projectmgt-code-conventions/SKILL.md；审核skill：.local/rbac-issues/skills/code-review/SKILL.md。遵守TDD、中文Javadoc、注解独行、Service事务与锁后重鉴权；本RBAC保留本地口令，管理分页page/size，外部目录沿page/pageSize，全新库首次SQL，不交付升级脚本。

真实CloneFactory0.0.2固定spring-fundamentals提交40583ff94764d2e758bcb13be17f5c05ea4d6352，普通依赖和处理器统一版本。同名同类型的直接映射优先真实处理器，包含record/final目标的构造器映射；不可变copy返回新对象，必须接收返回值并核对全部构造参数。仅全部参数均来自source时允许带目标类型的null种子，否则提供有效目标或显式组装。业务工厂、派生计算、多来源/关联/类型转换及会破坏分层依赖的映射仍显式处理，不为使用处理器引入跨层依赖或无意义包装；不以空包或改名旧jar替代。

老师课程来源固定990cea2，2026-11-23重新获取远端后未发现更新。prepare_course_source.py导出独立副本，应用structured三补丁及已确认DDD/学期/安全日志补丁；不改原目录、pom、Flyway或Vue业务源码。课程前端全部按老师原样，菜单行为不另行改写；当前DDD已验证，若有实际问题再评估structured。5项缺Controller、个人制批改、学生建组归属及普通教师日志范围等限制见docs/course-integration.md，不擅自补写整套课程业务。

类图/差异顺序图使用用户提供20261117新版UML skill；包图遵守UML包语义。当前88组图件（2包图、34类图、52差异顺序图）保留，45操作和两类离线入口逐项追溯；共同流程不画双份。

## 部署与证据

正式入口C的80（课程）、8088（RBAC），数据库/Java/Actuator/监控留内网。当前会话索引在进程内，后端各单副本、stop-first，不宣称高可用。course-integration负责实时身份转接、完整短码校验及独立MySQL业务审计，不混入RBAC日志。

监控参考老师spring-fundamentals/4. SpringAOP，CPU采用1秒采集、2秒窗口和2秒规则计算；配置覆盖、镜像摘要及内网node-exporter设计见deploy/monitoring.md。当前8项服务定义：7项单副本和node-exporter全局4任务；7个Prometheus采集目标已实测UP，9090/9100不公开。

文档描述现行职责，版本、环境、测试数量、源提交和未验证范围分别记于验收证据。历史成绩只对应当时版本，不算未来修改通过；业务测试、静态审核和老师验收不混称。旧阶段计划、重复说明和被替代图件已按用户要求清理，Git保留原版本；有用来源及验证记录见docs/history/README.md。
