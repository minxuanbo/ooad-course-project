# RBAC项目协作

## 当前状态与工作边界

structured和DDD均已实现；服务器两系统使用DDD。2026-11-24按用户确认合入PR54，10:11:53仅发布RBAC首次登录渲染修复，其他七项服务Spec和任务均未变，既有后端会话仍有效。当前前端镜像及9组正式表单/菜单验证见[本次发布](docs/history/verification/login-render-and-anonymous-audit-20261124.md)；后端仍为PR52的[性能版本](docs/history/verification/authorization-loading-performance-20261124.md)。现有18业务目录、部门角色、原型界面及日志来源IP保留，原库见[目录发布](docs/history/verification/access-extension-release-20261124.md)。以后不因源码提交、合入或文档整理自动更新线上Stack或重启正式应用。

当前未改动的RBAC后端源码已有双策略各330项及六项100%合并覆盖率，属于前轮性能验证；当时公网97项HTTP及三轮18次菜单读取通过，资源页中位数约2.15秒，其余约0.69—1.18秒。本次仅前端Teleport延迟解析，54项单元、4项正式构建渲染通过；线上9组、30次API均200、渲染异常0，未重测后端覆盖率或性能。逐请求读库、所有门禁与锁后重鉴权不变；课程原镜像保留，本次未追加课程浏览器验证。测试覆盖率不等同于正式流量覆盖率或性能结论；源码相同的后续记录提交不再次部署。

PR44的日志系统下拉、密码提示、实际权限查询留痕和Apifox服务器环境已完成，目标9042431；本轮已另获合入、双仓同步和重新部署授权。用户已要求按现行需求和设计职责整理全部docs，允许删除无当前价值的历史文件，保留有用来源及证据。外部详设及教师问答仅在用户指定桌面目录同步，原件已备份；原Excel、老师原目录、旧库/卷及原始日志仍保留。T14/T15不自动关闭；Apifox写入需明确授权，不由文档修改触发。新任务按[CONTRIBUTING](CONTRIBUTING.md)从main建立codex/分支、审核后推送草稿PR，单次合入和发布授权不延续至新任务。

用户2026-11-24已授权实施18业务系统及部门角色并同步Apifox9042431，随后追加授权PR49合入main、部署及恢复JaCoCo六项100%。本轮沿现有任务分支完成；18业务系统按原表矩阵独立首次配置，另加RBAC和课程共20项。发布保留现有账号、口令、授权及历史日志，原库另存，实际结果见验收入口；此授权不延续为未来任务自动发布许可。

## 业务与架构

- 当前业务依据为[需求](docs/requirements.md)，字段主规范为api/openapi.yaml，JSON同源；业务语义见docs/api-contract.md。当前37操作、31路径、73 Schema，完整维护能力K17。
- 六核心实体、九核心表加三支撑表；管理员是普通User的有效基础级别/可信根/实际权限资格，不新增管理员字段。非管理员保护本人/同级/上级，管理员可相互管理，写后至少一名同一启用根用户具备有效五级及完整K。
- 自定义角色不提升级别或冻结人员。锁后读取现有角色、校验实际增删；不可改项锁定保留，全局自定义及应用预置项仅可信根直属有效五级管理员增删；部门角色另受所属部门子树、领导范围及完整权限子集约束，管理员定义，领导只分配/撤回。
- 两策略独立实现业务，共享API/Vue/SQL。structured在service/structured；service/port只放接口、模型及协议值，不依赖具体实现或承担组织策略。DDD application依赖domain.model/repository及port，不能依赖JPA/Entity/structured；领域仓储仅领域模型和JDK容器，复杂读模型用Gateway。共享基础设施不能调用两套具体应用服务。
- 管理端与外部应用统一HS256身份JWT，通过Bearer携带，aud隔离；无角色权限快照，逐请求读库、单会话即时撤销、RBAC重启失效。管理端仍拒绝无实际管理API的员工。
- Application按数据库登记事实读取，不维护应用代码白名单、教师标记或课程实体。默认万人初始化仅RBAC；独立business-catalog-v1首次配置登记18项PENDING业务系统及原表五角色矩阵，课程独立登记CONNECTED。运行通行另查接入状态。角色可跨应用选权，Permission—ApiResource必须同应用。
- 课程teacher/student/course-admin由独立接入配置表达，Teacher/Student及user_id绑定只在课程库。本部（含职能部门/项目，排除分公司子树）员工为教师，其余为学生；合格RBAC管理员同账号兼课程管理员并有教师绑定。独立首次脚本显式执行，重复回执不重置后续维护，不宣称两库分布式原子事务。

## 来源与实现规范

代码skill：.local/rbac-issues/skills-20261117/projectmgt-code-conventions/SKILL.md；审核skill：.local/rbac-issues/skills/code-review/SKILL.md。遵守TDD、中文Javadoc、注解独行、Service事务与锁后重鉴权；本RBAC保留本地口令，管理分页page/size，外部目录沿page/pageSize，全新库首次SQL，不交付升级脚本。

真实CloneFactory0.0.2固定spring-fundamentals提交40583ff94764d2e758bcb13be17f5c05ea4d6352，普通依赖和处理器统一版本。同名同类型的直接映射优先真实处理器，包含record/final目标的构造器映射；不可变copy返回新对象，必须接收返回值并核对全部构造参数。仅全部参数均来自source时允许带目标类型的null种子，否则提供有效目标或显式组装。业务工厂、派生计算、多来源/关联/类型转换及会破坏分层依赖的映射仍显式处理，不为使用处理器引入跨层依赖或无意义包装；不以空包或改名旧jar替代。

老师课程来源固定990cea2，2026-11-23重新获取远端后未发现更新。prepare_course_source.py导出独立副本，应用structured三补丁及已确认DDD/学期/安全日志补丁；不改原目录、pom、Flyway或Vue业务源码。课程前端全部按老师原样，菜单行为不另行改写；当前DDD已验证，若有实际问题再评估structured。5项缺Controller、个人制批改、学生建组归属及普通教师日志范围等限制见docs/course-integration.md，不擅自补写整套课程业务。

类图/差异顺序图使用用户提供20261117新版UML skill；包图遵守UML包语义。当前82组图件（2包图、32类图、48差异顺序图）保留，37操作和两类离线入口逐项追溯；共同流程不画双份。

## 部署与证据

正式入口C的80（课程）、8088（RBAC），数据库/Java/Actuator/监控留内网。当前会话索引在进程内，后端各单副本、stop-first，不宣称高可用。course-integration负责实时身份转接、完整短码校验及独立MySQL业务审计，不混入RBAC日志。

监控参考老师spring-fundamentals/4. SpringAOP，CPU采用1秒采集、2秒窗口和2秒规则计算；配置覆盖、镜像摘要及内网node-exporter设计见deploy/monitoring.md。当前8项服务定义：7项单副本和node-exporter全局4任务；7个Prometheus采集目标已实测UP，9090/9100不公开。

文档描述现行职责，版本、环境、测试数量、源提交和未验证范围分别记于验收证据。历史成绩只对应当时版本，不算未来修改通过；业务测试、静态审核和老师验收不混称。旧阶段计划、重复说明和被替代图件已按用户要求清理，Git保留原版本；有用来源及验证记录见docs/history/README.md。
