# RBAC DDD概要与详细设计

版本1.0，2026-11-17，待用户审核。本文定义下一阶段“如何实现”，不表示DDD代码已经完成。结构化基线为PR #32合入后的4822114；main通过PR #1同步为a4275be，两者文件树相同。本轮只修改设计、图件及文档生成工具，审核通过后另行开始生产代码和测试实现。

## 1. 范围、依据与不变契约

业务仍以[需求](requirements.md)为准。正式系统只管理RBAC自身，课程接入、18个原型演示系统、登录限速和新服务器部署不在本轮。共用Vue、29操作OpenAPI、18项HTTP错误、12表首次SQL、工号/手机号同账号、即时鉴权、固定五级角色及17项K；不复制Controller、数据库或前端，不重授已初始化数据。

参考老师course-project-mgt的ca90f1c提交中backend/service/ddd、基础设施适配器及docs/backend-design.md第17章：沿用共享用例、领域仓储、互斥装配和按模块拆图的方式，不移植课程业务、Boot4、外部统一身份或Redis。参考位置及两份新版skill摘要见[设计评审记录](acceptance/ddd-design.md)。老师仓库代码与文档有阶段差异时，实际源码用于核实参考做法，本RBAC用户确认及新版skill优先。

本RBAC是一个有界上下文。身份、组织、人员、授权目录及初始化是模块划分，不据此新增微服务或把每张表当成独立子域。六个核心实体不变；日志、来源和回执是运行支撑，不增加全局管理员实体。

## 2. 目标包与装配

```text
com.municipal.rbaccore
├── api                         现有Controller、DTO/VO、输入校验与HTTP适配
├── service.port                现有用例、Command/Query/Result及技术支撑抽象
├── service.structured          保留事务脚本与直接JPA访问
├── service.ddd                 以下均为拟新增包
│   ├── application
│   │   ├── auth/user/role/resource/department/audit
│   │   ├── importing/bootstrap/security
│   │   └── support             结果装配、领域异常到既有业务错误的翻译
│   └── domain
│       ├── model               聚合、值、事实、查询条件、DomainPage、领域异常
│       │   └── policy          不依赖框架的跨聚合判断
│       └── repository          仅领域模型输入输出的仓储接口
├── infrastructure
│   ├── persistence/{entity,repository}  现有技术模型和JPA仓储
│   ├── persistence/{ddd,assembler}     拟新增领域适配器及映射
│   └── security/web/logging/excel      共用技术支撑，通过port调用所选业务实现
└── config/bootstrap            唯一装配根与产品/导入/授权三个启动入口
```

跨层依赖：application→domain.model/domain.repository/service.port；domain仅依赖领域模型及JDK；基础设施适配器→领域仓储/模型和JPA；API→service.port。application内部的结果Mapper、异常翻译器属于同层协作，不装入公共包，不承载授权策略；Spring事务/组件注解可用于application，领域不引Spring/JPA。禁止application→infrastructure、JpaRepository、Entity、Servlet或structured，也禁止infrastructure→DDD application。策略规则放domain.model.policy，避免应用服务引用不在新版skill允许模型树内的旁支包。

拟提供app.backend-strategy=structured|ddd，缺省structured，未知值启动失败。RuntimeConfiguration拆分公共技术配置和两个互斥策略配置；各策略的全部用例及ManagementAuthorization、ManagementAccessUseCase、MaintenanceProtection一起装配，不能只切换HTTP主用例而漏掉AOP/预检支撑。产品、ImportCommand、BootstrapAuthorizationCommand均沿同一策略选择；技术Bean只注册一次，不扫描两套无条件Service。当前还没有这个开关，不能用参数宣称DDD已可运行。

## 3. 用例职责与图件覆盖

所有Ddd前缀类型均为目标名称。方法签名沿对应现有接口，不新增业务HTTP接口。图件索引及逐图范围见[DDD图集](design/README.md)。

| 现有公共契约 | 拟实现类 | 领域/支撑及主要职责 | 类图 |
| --- | --- | --- | --- |
| AuthenticationUseCase | DddAuthenticationService | User、AuthenticationPolicy、授权事实；双入口唯一定位、口令/状态、准入、身份装配；Bearer仍用技术端口 | identity |
| UserManagementUseCase | DddUserManagementService | User、RoleAssignmentPolicy；选权候选/保存、严格目标保护、停用和调动 | personnel |
| UserQueryUseCase | DddUserQueryService | UserCriteria、DomainPage<User>、OrganizationFacts；先范围/过滤/计数再分页，当前页批量角色摘要 | query |
| RoleUseCase | DddRoleService | Role、PermissionSelectionPolicy；新建空角色、改名启停、直接权限整体替换 | role |
| ResourceUseCase | DddResourceService | Permission、ApiResource、ApplicationSystem、PermissionTreePolicy；树、API启停、系统只读目录及完整映射 | resource / api-resource / systems |
| DepartmentUseCase | DddDepartmentService | Department、OrganizationPolicy；类型、范围、同级重名、根和空节点保护、墓碑 | department |
| OverviewUseCase | DddOverviewService | OrganizationFacts、OrganizationStatistics；同快照聚合，不依赖另一具体DDD Service | overview |
| AuditLogUseCase | DddAuditLogService | 通过ManagementAccessUseCase和AuditQueryGateway；共同根+实际API、技术游标快照、脱敏结果 | audit |
| ImportUseCase | DddImportService | ImportManifest、User/Department；源数据规划转换、整批新库导入、来源/幂等 | importing |
| AuthorizationBootstrapUseCase | DddInitialAuthorizationService | BootstrapPlan、Receipt；预览只读、五角色完整集合、首次回执、重复不重置 | bootstrap |
| ManagementAuthorization、ManagementAccessUseCase、MaintenanceProtection | DddAuthorizationService、DddManagementAccess、DddMaintenanceGuard | 有效链、范围与完整维护者分别编排；注入公共接口，禁止跨调具体应用服务 | authorization / management-access / maintenance |

## 4. 聚合、值与不变量

| 类型 | 自身状态与行为 | 跨聚合所需事实 |
| --- | --- | --- |
| User | 内部ID、工号/手机号、哈希、姓名、只读职位、部门ID、启用、完整roleIds；replaceRoles/disable/transfer | 操作者/目标级别与范围、角色有效权限、K |
| Role | code稳定、name、enabled、managementLevel、直接permissionIds；createEmpty/rename/changeEnabled/replacePermissions | 权限存在/启用/本系统、编码唯一、K |
| Permission | systemId、parentId、type、enabled、直接apiIds；changeType/moveTo/changeEnabled/replaceApis | 同系统树无环、BUTTON无子、映射同系统、K |
| ApiResource | 系统、方法/模板只读、enabled；changeEnabled | 系统状态和K |
| Department | 稳定code、parentId、type、name、deleted；createChild/rename/deleteEmpty | 可信根与范围、同父名称、人员/下级存在性 |
| ApplicationSystem | RBAC系统标识与启用事实，只读 | 有效授权链同系统边界，不新增系统CRUD |

聚合以标识引用，不嵌入JPA对象图。关系集合返回只读副本，替换行为明确增删，不通过公开setter任意修改权限关系。来源导入通过受控reconstitute或factory建立完整快照，不调用运行时HTTP补造数据。User不会包含所有Role对象或全公司员工，Role不会持有所有用户；需要的关联事实批量取得。

领域状态/属性常量由对应对象以public static final声明：Role归口五基础级别和自定义级别语义，Department归口四种类型，Permission归口MENU/BUTTON，ApplicationSystem归口RBAC系统编码。应用/领域不引用公共协议枚举作为自身状态，也不按名称/职位猜级别；应用Mapper在领域取值与现有DepartmentType等协议值间显式转换。布尔启用状态保持其既有语义，不虚构额外生命周期。

UserKey、RoleKey、PermissionKey、ApiKey、DepartmentKey等不可变标识只用于领域/仓储边界的类型安全；HTTP仍为正安全整数，工号仍为字符串。PageWindow、DepartmentScope、ApiOperation、UserCriteria、RoleCriteria、AuthorizationFacts、OrganizationFacts、DepartmentUsage、MaintenanceFacts等值/事实都归domain.model；不可变值和record使用显式构造，不交CloneFactory写final字段。

跨聚合策略在model.policy：AuthorizationPolicy逐链计算有效Permission/API及OR，不把父菜单当继承；RoleAssignmentPolicy同时检查目标保护、授予上限及完整有效Permission子集，污染角色拒绝，停用自定义角色也保留目标保护；OrganizationPolicy核实来源根并计算有效子树/合法父子；MaintenancePolicy判断一个启用、可信根、级别5的用户独自覆盖K，不把多人权限拼接。策略接受明确事实，不访问仓储、HTTP、Spring或全局缓存。

## 5. 领域仓储与复杂查询边界

下表是拟议领域签名，不是已经生成的Java。所有参数、返回值元素均来自domain.model，Optional/List/Set仅为JDK容器；无参方法可读取事实。保存返回已持久化的领域对象，不用应用Command/Result作为仓储协议。标识和分页条件也用领域值，避免对新版skill“仅领域对象输入输出”作宽松例外。

| 仓储 | 代表签名 | 适配器及说明 |
| --- | --- | --- |
| UserRepository | find(UserKey): Optional<User>；match(LoginIdentity): List<User>；save(User): User；search(UserCriteria): DomainPage<User>；statistics(DepartmentScope): OrganizationStatistics | JpaUserRepositoryAdapter；查询只取当前页，再批量装配角色ID/摘要；总览在同一读事务内聚合，不加载万人对象图 |
| RoleRepository | find(RoleKey): Optional<Role>；findAll(RoleSelection): List<Role>；search(RoleCriteria): DomainPage<Role>；save(Role): Role | JpaRoleRepositoryAdapter；code唯一由查询和数据库兜底 |
| PermissionRepository | find(PermissionKey): Optional<Permission>；findAll(PermissionSelection): List<Permission>；save(Permission): Permission | JpaPermissionRepositoryAdapter；直接关联保留停用项，持久化不决定是否可授 |
| ApiResourceRepository | find(ApiKey): Optional<ApiResource>；search(ApiCriteria): DomainPage<ApiResource>；save(ApiResource): ApiResource | JpaApiResourceRepositoryAdapter；方法/模板不可由PATCH覆盖 |
| ApplicationSystemRepository | listRbac(): List<ApplicationSystem> | JpaApplicationSystemRepositoryAdapter；仅按ApplicationSystem.CODE_RBAC读取有限目录；测试库有外系统行仍只返回RBAC，不因无关行拒绝整个查询，不增加系统CRUD |
| OrganizationRepository | load(): OrganizationFacts；lock(DepartmentKey): Department；usage(DepartmentKey): DepartmentUsage；save(Department): Department | JpaOrganizationRepositoryAdapter；事实包括来源根证据与有效节点，lock使用同一根行FOR UPDATE |
| AuthorizationRepository | facts(UserKey): AuthorizationFacts；candidates(AssignableRoleCriteria): DomainPage<Role>；keepers(MaintenanceCriteria): DomainPage<MaintenanceFacts> | JpaAuthorizationRepositoryAdapter；范围/权限条件由领域策略形成明确查询条件，SQL只执行谓词，不按角色名或SQL存在性代替领域判定 |
| InitializationRepository | inspect(ImportManifest): InitializationState；save(ImportBatch): ImportReceipt；publish(BootstrapPlan): BootstrapReceipt | JpaInitializationRepositoryAdapter；整批来源/对象/回执在应用事务内提交，不接收ImportReport或BootstrapReport |

DomainPage<T>定义page、size、total、items，T必须为领域模型，应用服务转换为现有PageResult。query/count在同一快照、同一谓词；不能先分页候选后在Java过滤，导致total或合法候选错误。可授候选查询将级别/完整权限集合/污染系统条件转为NOT EXISTS等SQL，保存时重新加载完整目标和候选事实，由领域策略独立复验；不以查询SQL替代写业务规则。

AuthorizationFacts应带完整相关链及每个对象的启用/同系统事实，再由AuthorizationPolicy计算；不能只返回SQL已判好的API列表、在领域层原样转发，冒称领域授权实现。事实查询可按当前用户和相关ID缩小数据，不全量读取万人授权图；字段仅供当前请求，禁止跨请求缓存有效结论。

日志是复杂技术读模型，拟新增service.port.audit.AuditQueryGateway及infrastructure.logging.JpaAuditQueryGateway；它可使用既有LogQuery/LogPageResult，封装游标编解码/指纹/快照与JPA投影。它不是领域Repository，不扩充核心实体。DddAuditLogService只经ManagementAccessUseCase完成本次日志API及共同根校验后调用Gateway。现有结构化日志服务本阶段保持，DDD实施时若抽取技术查询共用实现，必须先证明两套同契约，不能把结构化鉴权一并共享。

Repository禁止导入service.port、infrastructure、jakarta.persistence、Spring Data Page/JpaRepository；返回Map<String,Object>、Entity或把应用Result改名为领域对象均不接受。映射和数据库异常翻译在基础设施适配器就地进行；领域决策在模型/策略，事务在application。

## 6. 写事务、即时生效与K

所有相关写仍为同一MySQL事务，READ_COMMITTED；需要分页计数一致性的查询采用现有REPEATABLE_READ契约。装配完成后，两套策略均使用同一根行锁，不能锁不同资源造成跨入口竞态。

写步骤：开始应用事务→DddMaintenanceGuard通过领域仓储锁已由来源证据核实的根→重新读取身份/启用状态、当前实际API、范围和必要级别→加载目标及所需领域事实→调用模型/策略→适配器保存→按修改后的状态重算K→提交。发生拒绝、唯一/FK冲突、死锁或保存故障，全事务回滚。取锁前的MVC预检不替代锁后检查。

K校验必须看见本事务的待提交变更。适配器保存时同步完整关联；只有确实需要在写边界捕获约束或让后续K查询看见写入，才使用saveAndFlush，不额外裸flush。不能在未同步的JPA旧实体图上或独立事务中检查K。检索候选维护者可按页批量取事实，一人完整成立即可；不缓存“曾经有管理员”。最后保留者必须启用、直属可信根、有效基础级别5，且其一个人的有效API覆盖原17项K。

读/写不把授权存入Bearer或跨请求缓存；撤权提交后的新请求用最新事实，已有另一有效链则按OR保留。等待根锁的旧写在锁后失权必须拒绝。在途已执行操作不追溯取消。日志继续独立REQUIRES_NEW及脱敏/去重，业务日志写失败不能改变授权结论。

## 7. 认证、错误与映射

认证技术共用IdentityTokenStore、PasswordHashing、RequestContext，业务链独立：UserRepository按同一输入精确匹配工号/手机号→AuthenticationPolicy要求唯一、启用、口令符合→AuthorizationPolicy要求本RBAC登记管理API→发令牌。未知/歧义/停用/错口令仍401 LOGIN_FAILED，正确口令但无管理准入仍403；不新增会话实体或外部统一身份。

领域异常DomainViolation只包含领域原因和可公开提示，不携HTTP、SQL或JPA对象；application.support的DddFailureTranslator翻译为既有BusinessException和18项HTTP错误，API Advice保持共用。基础设施在保存发生处把数据库冲突翻译为既有错误，不在全局Advice按表名判断。框架路由404/405仍沿共用Advice；离线初始化错误保持既有CLI语义。

映射分三段：HTTP DTO→公共Command、公共Result→VO沿现有真实CloneFactory；Entity↔可变领域对象的共同字段可用真实复制后执行受控reconstitute/factory及关系映射；不可变领域对象、record、final属性必须逐字段显式构造，不能用copy调用冒充已复制。应用Query→领域Criteria、DomainPage→应用PageResult、领域ID/枚举/关系集合转换显式处理。人员查询时应用层把所选部门ID转换为DepartmentKey，由OrganizationPolicy在操作者范围内解析所选子树，再提取q、enabled及构造PageWindow；领域UserCriteria.of只接收DepartmentScope、String、Boolean、PageWindow，不接收service.port.UserQuery或任何DTO。空的部门筛选沿操作者完整范围；未知/越界仍沿现有404/403语义。不可为了映射方便开放权限关系或稳定编码setter。

每条映射在后续编码时列入审计表：源/目标、同名同类型字段、显式派生/转换、排除原因、生成重载和往返断言。改注解后clean检查真实CloneFactory.java，测试必须断言工号、姓名、部门、职位、级别及授权ID往返，不只断言对象非null。新版Java注解独立换行适用于后续新增/修改源码及测试；本轮不顺带重排已批准结构化代码。

## 8. 初始化与运行支撑

不共享整个StructuredImportService/StructuredInitialAuthorizationService。ExcelXmlReader/WorkbookImportPlanner作为来源技术读取与预检可共用，ImportPlan由DDD应用转换为领域ImportManifest；组织合并、账号创建和批量授角色由领域factory/policy表达，再由仓储保存。哈希端口共用，每账号独立盐；公共BootstrapCredentials及明文配置止于应用层和哈希端口，领域只接收含已编码账号种子的AccountInitialization值，不直接依赖公共credentials类型。ImportBatch的种子按稳定来源部门code引用，适配器在同一事务先保存组织，再用新ID映射解析FK，不负责领导合并、状态或角色分类决策。来源摘要/稳定键/原父ID及墓碑核对保持。已有回执不重置后续状态、口令、角色、改名或新删节点。

DddImportService负责一次整批导入事务；DddInitialAuthorizationService预览只读，initialize在根锁下整批发布五角色、关联与回执，失败全回滚。ImportManifest/BootstrapPlan为领域模型，既有ImportReport/RoleBootstrapPlan/BootstrapReport仅在应用边界装配。全部员工启用不等于管理准入，首次人数和分类沿原确认，不把原型演示系统导入正式数据。

## 9. 顺序图只画差异的切分规则

共同HTTP绑定/校验、Controller、DTO/VO、Bearer解析、AOP/预检、通用日志、根锁取得/锁后重新鉴权、最终K检查与事务提交，不放进新顺序图。它们作为文字前置/后置约束在本节和第6章说明，不能被误解为删掉这些行为。即使具体实现类不同，只要外部协作语义相同，也不为凑图重复画公共链路。

六组差异片段：有效授权计算、人员角色替换、角色权限替换、权限树修改、部门新增、人员分页查询。每组各一张Structured现状和DDD目标，共12张；每张仅从“所需身份/范围/锁后事实已准备”的差异切入点到“变更/读模型准备好”的切出点。调用者统一obj: 调用者，表示调用该差异片段的外层流程，不冒称新增公共方法。DDD图中方法为本设计拟议名称；结构化图逐项关联当前真实方法。异常只在该差异片段发生时从实际抛出对象直接回复obj，不重复画技术错误响应。

查询、总览、日志和初始化其余流程采用图集覆盖矩阵解释复用关系；不另外绘制相同的HTTP/令牌/日志时序。原完整require顺序图退出当前设计图集，源码历史仍可追溯。类图和包图用于展示静态边界，可出现共用接口，不能据此把共用运行流程再画进顺序图。

## 10. 编码与验收关口（本轮不执行实现）

1. 用户先审核本文、仓储契约、包图/类图及差异切分，再开始DDD代码。
2. 先建立DDD架构守卫：领域仅JDK；领域仓储仅模型容器；应用不碰JPA/结构化/HTTP；两套策略所有用例及支撑端口互斥。把原“无DDD目录”阶段断言替换为真正的装配与单向依赖检查。
3. 以共享r07/r11/r13/r14契约分别启动DDD专库；r23增量提取公共断言，补DDD启动类，不复制业务断言或共享同一测试库。不能让旧结构化启动类假冒DDD通过。
4. 按认证/查询→人员写→角色资源/部门→日志/初始化切片TDD；每片先真实行为RED，后领域与适配实现，再完整回归。
5. 必测普通员工拒绝、双入口、即时撤权OR、同级/本人/上级/自定义角色保护、越级/子集/污染候选、整体替换、部门类型/重名/空删、K不拼人、锁后撤权、交叉写并发及故障回滚。对比HTTP及最终数据库状态，不只比状态码。
6. 不可变映射和51个既有复制重载相关功能分别检查实际生成与往返字段；测试数据/数据库/日志隔离。前端仍同一套，完整浏览器/窄屏、两策略真实HTTP及新项目两轮初始化各自记证据。

结构化167项/前端26项等已有成绩仅是合并基线历史证据，不作为本轮DDD通过结果。当前未创建service/ddd源码、策略开关或新SQL；本设计PR审核前不得编码DDD或自动合并设计分支。
