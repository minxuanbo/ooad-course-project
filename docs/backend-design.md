# 市政公司统一权限管理系统 · 后端设计方案

业务依据为[需求说明](requirements.md)，领域语义见[领域模型](domain-model.md)，存储见[数据库设计](database-design.md)，HTTP契约见[OpenAPI](../api/openapi.yaml)。文档按老师[后端设计](https://gitee.com/mingqcn/course-project-mgt/blob/ca90f1cb7faa7d73b71b93a125460513f46b730e/docs/backend-design.md)的章节职责组织，全部设计对象均为本RBAC项目。

同一后端工程提供 structured 和 DDD 两套实现，共用 HTTP 契约、Vue 和 MySQL。实际验证范围见[测试与验收](testing-acceptance.md)。

## 1. 设计目标

单仓库、单后端应用，提供structured/ddd两种业务实现，共用HTTP/数据库契约、前端与初始化来源。两种实现应在身份、授权、范围、错误、持久化状态、回滚和并发下保持一致。Java21、Boot3.5.6、JPA、MySQL8.4、AOP及老师真实CloneFactory沿当前技术基线，不因参考老师新版本而升级框架。

本RBAC自己保存口令并认证，不照搬课程项目的外部统一身份。课程按通用身份协议显式接入；Redis、语音、存储和其他示例集成不纳入RBAC范围。

默认首次授权管理RBAC自身权限，通用应用目录按登记事实读取，课程独立配置接入，共用Vue、29项管理HTTP操作与8项可选通用应用身份操作、22项HTTP错误、12表首次SQL、固定五级角色及17项完整维护能力集合K。18个外系统仅保留离线原型演示，不进入正式目录、授权或初始化。课程身份适配职责见[课程接入](course-integration.md)，两策略各自计算权限，不将业务实现放入共享port。

参考老师项目的共享用例、领域仓储、互斥装配与按模块绘图方式；本项目保留本地账号认证，不移植课程业务、外部统一身份、Boot4或Redis。

## 2. 总体结构

### 2.1 分层职责

| 实际模块                                             | 当前责任                                                                                    |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| api.auth/user/department/role/resource/audit及shared | 共享HTTP、DTO/VO、输入校验、CloneFactory映射及响应/错误适配                                 |
| service.port                                         | 独立用例接口、Command/Query/Result与支撑抽象，无Entity/MVC/Spring依赖                       |
| service.structured                                   | 事务脚本、范围/有效授权/可授予/根锁/K及初始化；直接使用JPA仓储                              |
| infrastructure.persistence.entity/repository         | Entity/JpaRepository，保留表结构、关系与查询，不作为DDD聚合                                 |
| infrastructure.security/web/logging/excel            | BCrypt与身份索引、MVC预检/过滤器、最小日志、游标与来源读取                                  |
| config / bootstrap                                   | 扫描/数据源守卫/秘密装配，以及RbacApplication、ImportCommand、BootstrapAuthorizationCommand |

API与Vue只依赖共享用例/HTTP契约，两种业务实现相互独立；持久化模型和技术支撑可以复用，业务判断不放入公共技术类。数据库不因实现策略而复制两套表。

## 3. 包结构

### 3.1 包职责说明

```text
com.municipal.rbaccore
├── api                         现有Controller、DTO/VO、输入校验与HTTP适配
├── service.port                现有用例、Command/Query/Result及技术支撑抽象
├── service.structured          保留事务脚本与直接JPA访问
├── service.ddd                 独立领域实现
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
│   ├── persistence/ddd                 领域仓储适配器及JpaDomainMapper
│   └── security/web/logging/excel      共用技术支撑，通过port调用所选业务实现
└── config/bootstrap            唯一装配根与产品/导入/授权三个启动入口
```

后端为backend下的单一Maven工程，frontend为生产Vue，prototype为离线交互参考，docs维护设计；不复制两套后端工程。api.department提供部门/总览HTTP适配，service.port.department定义共享用例，service.structured.department承担结构化实现。

### 3.2 依赖方向

application→domain.model/domain.repository/service.port；domain仅依赖领域模型及JDK；基础设施适配器→领域仓储/模型和JPA；API→service.port。application内部的结果Mapper、异常翻译器属于同层协作，不装入公共包，不承载授权策略；Spring事务/组件注解可用于application，领域不引Spring/JPA。禁止application→infrastructure、JpaRepository、Entity、Servlet或structured，也禁止infrastructure→DDD application。策略规则放domain.model.policy，避免应用服务引用不在新版skill允许模型树内的旁支包。

### 3.3 一个请求的包路径

身份及requestId过滤→操作预检→DTO校验/Controller→AOP实际门禁→选定用例实现的事务→仓储/适配器→MySQL。请求追踪通过RequestContext传递，认证技术经IdentityTokenStore和PasswordHashing隔离；预检不替代实际门禁与写锁后重验。

## 4. 双实现选择方式

配置 `app.backend-strategy=structured|ddd` 选择业务实现，亦可使用环境变量 `APP_BACKEND_STRATEGY`。默认 structured，未知值在创建数据源前失败。RuntimeStrategySelector 负责产品和首次授权，ImportStrategySelector 负责离线导入。两者共用 BackendStrategy 解析规则。运行时同时切换所有用例与 ManagementAuthorization、ManagementAccessUseCase、MaintenanceProtection；命令进程只装配当前入口需要的业务。

### 4.1 两套实现的边界

| 区域                                | 当前内容                                                                     | DDD边界                                                                                                                                   |
| ----------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| api、frontend、OpenAPI              | HTTP DTO/VO、校验、错误/信封、Vue和37操作契约（29管理、8通用身份）                                | 复用同一接口/页面，Controller只调service.port，不写实现分支                                                                                   |
| service.port                        | 用例/支撑接口、Command/Query/Result、协议枚举、错误码、固定角色/方法路径目录 | 不导入Entity、Spring、Servlet、结构化或DDD类型；目录是协议/首次数据元信息，不执行动态权限、范围或组织层级策略                                 |
| infrastructure.security/web/logging | BCrypt、Bearer承载的JWT、请求/游标/技术日志、MVC预检及门禁                             | 通过AuthenticationUseCase、ManagementAuthorization、ManagementAccessUseCase等端口调用当次装配实现，不能直接调用StructuredService或DDD应用服务 |
| infrastructure.excel                | Excel解析、原件/源字段校验、LoginIdentifiers离线标识碰撞检查                 | 复用来源读取端口与技术预检；不依赖结构化服务，DDD业务仍独立保证身份不变量                                                                     |
| infrastructure.persistence          | JPA Entity/Repository及技术查询                                              | 结构化服务可直接使用；DDD需独立适配器实现领域仓储，映射领域模型，不让DDD应用直接使用JPA实体/仓储                                              |
| service.structured                  | 有效授权、人员范围/级别、部门类型规则、根锁/K、事务脚本和当前初始化编排      | 当前全部为结构化代码；DDD领域策略/行为独立实现同一规则，不继承/转调这些服务或把其搬入common                                                   |
| config/bootstrap                    | 当前扫描、数据源守卫、生产/导入/首次授权启动                                 | 显式选择策略，互斥注册对应业务和领域适配器                                                                   |

DepartmentType只声明HEADQUARTERS/COMPANY/DEPARTMENT/PROJECT；结构化层级判断位于StructuredDepartmentService。源数据标识校验归infrastructure.excel，不再出现共享WorkbookImportPlanner导入service.structured的反向依赖。实体中的局部持久化保护不代表DDD领域对象，不能直接改名充当领域实现。

当前RbacCatalog保存方法/路径、初始权限映射和固定K目录，两套实现须遵守同一契约；有效授权计算、人员保护及完整维护者计算不在目录类中。CurrentIdentity与请求能力快照不跨请求缓存授权，DDD仍须锁后读取最新事实。

两种策略分别使用隔离数据库运行同一组契约。启动命令及专库条件见[后端说明](../backend/README.md)。

## 5. 共享用例契约

Command/Query/Result与HTTP DTO/VO分离，Controller及预检/AOP注入service.port接口，不注入具体实现。

| 用例/支撑                   | 实际接口                                        | 当前双策略实现/共用适配                                                     |
| --------------------------- | ----------------------------------------------- | ----------------------------------------------------------------- |
| 登录、本人                  | AuthenticationUseCase                           | StructuredAuthenticationService / DddAuthenticationService                                   |
| 范围树、人员列表            | UserQueryUseCase                                | StructuredUserQueryService / DddUserQueryService                                        |
| 人员角色/停用/调动          | UserManagementUseCase                           | StructuredUserManagementService / DddUserManagementService                                   |
| 角色定义、选权              | RoleUseCase                                     | StructuredRoleService / DddRoleService                                             |
| 资源与API映射               | ResourceUseCase                                 | StructuredResourceService / DddResourceService                                         |
| 部门维护 / 范围总览         | DepartmentUseCase / OverviewUseCase             | StructuredDepartmentService / DddDepartmentService / StructuredOverviewService / DddOverviewService           |
| 最小日志查询                | AuditLogUseCase                                 | StructuredAuditLogService / DddAuditLogService                                         |
| 全局范围与操作预检          | ManagementAccessUseCase                         | StructuredGlobalManagementAccess / DddManagementAccess                                  |
| 新项目账号导入 / 首次五角色 | ImportUseCase / AuthorizationBootstrapUseCase   | StructuredImportService / DddImportService / StructuredInitialAuthorizationService / DddInitialAuthorizationService   |
| 最新API集合 / 根锁及K       | ManagementAuthorization / MaintenanceProtection | StructuredEffectivePermissionService / DddAuthorizationService / StructuredMaintenanceGuard / DddMaintenanceGuard |
| 口令 / 身份索引             | PasswordHashing / IdentityTokenStore            | BCryptPasswordHashing / JwtIdentityStore                       |
| 请求操作与追踪 / 事件记录   | RequestContext / AuditWriter                    | HttpRequestContext / JpaAuditWriter                               |
| 原始资料读取                | SourceWorkbookReader                            | WorkbookImportPlanner（复用ExcelXmlReader）                       |

### 5.1 用例输入输出与约束

| 用例端口组   | 输入/输出                                 | 关键约束                                  |
| ------------ | ----------------------------------------- | ----------------------------------------- |
| 认证与本人   | account/password或可信身份→身份及有效权限 | 双入口唯一、有效管理API准入、退出幂等     |
| 组织人员查询 | 可选部门/q/状态/page/size→可见树/分页     | 范围先于筛选与计数，不返回范围外父节点    |
| 人员管理     | userId及roleIds/停用/目标部门→最新结果    | 子集、本人/管理员限制、调动双范围、根锁/K |
| 角色与选权   | 角色查询/空权限新建/部分更新/整体选权     | 共同根与实际API、编码唯一、显式权限       |
| 资源与映射   | 目录读取/允许字段更新/完整apiIds          | 同系统、树规则、映射OR、根锁/K            |
| 最小日志     | 筛选/UTC范围/cursor/limit→快照分页        | 日志API+共同根、每批重验、脱敏            |
| 初始化       | 原件、秘密、来源、预检清单→来源/回执      | 新项目、只读预览、重复不重置              |

日志存储、身份索引、Excel读取等由小型端口隔离；有真实外部HTTP需求才引入@HttpExchange客户端，业务层不得拼HTTP请求。当前不预建未确认外部系统客户端。

## 6. 关键模块与用例

### 6.1 结构化业务组织

按人员、角色、资源、认证、日志、初始化划分用例服务，Service负责业务判断和事务，Repository负责明确查询/持久化。候选Permission子集、范围过滤、total和分页在数据库完成，不先全量加载再截页。纯规则可提取有明确输入的辅助对象，不把角色名硬编码成权限判断。

StructuredPersonnelAuthority集中角色级别与目标保护；StructuredDepartmentService集中组织类型、合法下级及删除规则；StructuredOverviewService按范围聚合。它们仍是结构化事务脚本及辅助规则，不属于DDD领域模型。

### 6.2 DDD应用与领域职责

下表列出当前DDD类，方法遵守第5章对应接口，不增加业务HTTP接口。领域行为与策略见[领域模型](domain-model.md#3-ddd聚合与依赖)，静态关系见第17章。

| 现有公共契约                                                            | 实现类                                                          | 领域/支撑及主要职责                                                                                   | 类图模块                                        |
| ----------------------------------------------------------------------- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| AuthenticationUseCase                                                   | DddAuthenticationService                                          | User、AuthenticationPolicy、授权事实；双入口唯一定位、口令/状态、准入、身份装配；JWT签发与校验使用技术端口   | identity                                        |
| UserManagementUseCase                                                   | DddUserManagementService                                          | User、RoleAssignmentPolicy；选权候选/保存、严格目标保护、停用和调动                                   | personnel                                       |
| UserQueryUseCase                                                        | DddUserQueryService                                               | UserCriteria、DomainPage<User>、OrganizationFacts；先范围/过滤/计数再分页，当前页批量角色摘要         | query                                           |
| RoleUseCase                                                             | DddRoleService                                                    | Role、PermissionSelectionPolicy；新建空角色、改名启停、直接权限整体替换                               | role                                            |
| ResourceUseCase                                                         | DddResourceService                                                | Permission、ApiResource、ApplicationSystem、PermissionTreePolicy；树、API启停、系统只读目录及完整映射 | resource / api-resource / systems               |
| DepartmentUseCase                                                       | DddDepartmentService                                              | Department、OrganizationPolicy；类型、范围、同级重名、根和空节点保护、墓碑                            | department                                      |
| OverviewUseCase                                                         | DddOverviewService                                                | OrganizationFacts、OrganizationStatistics；同快照聚合，不依赖另一具体DDD Service                      | overview                                        |
| AuditLogUseCase                                                         | DddAuditLogService                                                | 通过ManagementAccessUseCase和AuditQueryGateway；共同根+实际API、技术游标快照、脱敏结果                | audit                                           |
| ImportUseCase                                                           | DddImportService                                                  | ImportManifest、User/Department；源数据规划转换、整批新库导入、来源/幂等                              | importing                                       |
| AuthorizationBootstrapUseCase                                           | DddInitialAuthorizationService                                    | BootstrapPlan、Receipt；预览只读、五角色完整集合、首次回执、重复不重置                                | bootstrap                                       |
| ManagementAuthorization、ManagementAccessUseCase、MaintenanceProtection | DddAuthorizationService、DddManagementAccess、DddMaintenanceGuard | 有效链、范围与完整维护者分别编排；注入公共接口，禁止跨调具体应用服务                                  | authorization / management-access / maintenance |

部门新增和改名通过 `OrganizationPolicy.requireUnique` 检查同父名称唯一；删除通过 `requireNonRoot` 保护可信根，通过 `requireEmpty` 检查人员及有效下级占用。`DddDepartmentService` 负责加载事实，保持范围检查、可信根保护、编辑权限、占用检查的删除顺序，规则通过后才调用 `Department.tombstone`。领域规则失败由 `DddWrite` 翻译为既有 `DEPARTMENT_ROOT_REQUIRED`、`DEPARTMENT_NOT_EMPTY`、`DEPARTMENT_NAME_CONFLICT` 三种409错误，在原写事务中回滚。

角色基础级别在 `Role` 中以 `public static final` 归口：`DEFAULT_LEVEL`、`PROJECT_MANAGER_LEVEL`、`DEPARTMENT_MANAGER_LEVEL`、`COMPANY_LEADER_LEVEL`、`ADMIN_LEVEL`。`FIRST_MANAGER_LEVEL` 引用项目经理级别，`InitialAuthorizationPolicy` 使用这些领域常量完成首次分类，不再单独定义级别值。

### 6.3 领域仓储与复杂查询边界

领域仓储输入输出使用领域对象，容器使用 JDK 集合、Optional 或 DomainPage。仓储协议独立于 HTTP 及 JPA。

| 领域仓储 | 代表签名 | 适配器 |
| --- | --- | --- |
| UserRepository | find(User.Key)、match(User.LoginIdentity)、save(User)、search(SearchCriteria.Users): DomainPage<User>、statistics(OrganizationFacts.Scope) | JpaUserRepositoryAdapter |
| RoleRepository | find(Role.Key)、select(Role.Selection)、search(SearchCriteria.Roles)、save(Role) | JpaRoleRepositoryAdapter |
| PermissionRepository | find(Permission.Key)、select(Permission.Selection)、tree(ApplicationSystem)、save(Permission) | JpaPermissionRepositoryAdapter |
| ApiResourceRepository | find(ApiResource.Key)、select(Set<ApiResource.Key>)、search(SearchCriteria.Apis)、save(ApiResource) | JpaApiResourceRepositoryAdapter |
| ApplicationSystemRepository | findRbac(): Optional<ApplicationSystem> | JpaApplicationSystemRepositoryAdapter |
| OrganizationRepository | trustedRoot()、load(): OrganizationFacts、lock(Department.Key)、usage(Department.Key)、save(Department) | JpaOrganizationRepositoryAdapter |
| AuthorizationRepository | facts(User.Key)、candidates(AssignableRoleCriteria): DomainPage<Role>、rootAccounts(Department.Key) | JpaAuthorizationRepositoryAdapter |
| InitializationRepository | inspect(): InitializationState、save(ImportBatch): ImportBatch.Receipt、catalog()、publish(BootstrapPlan.Publication) | JpaInitializationRepositoryAdapter |

DomainPage<T>定义page、size、total、items，T必须为领域模型，应用服务转换为现有PageResult。query/count在同一快照、同一谓词；不能先分页候选后在Java过滤，导致total或合法候选错误。可授候选查询将级别/完整权限集合/污染系统条件转为NOT EXISTS等SQL，保存时重新加载完整目标和候选事实，由领域策略独立复验；不以查询SQL替代写业务规则。

AuthorizationFacts应带完整相关链及每个对象的启用/同系统事实，再由AuthorizationPolicy计算；不能只返回SQL已判好的API列表、在领域层原样转发，冒称领域授权实现。事实查询可按当前用户和相关ID缩小数据，不全量读取万人授权图；字段仅供当前请求，禁止跨请求缓存有效结论。

日志的技术查询由共享 AuditQueryGateway 和 JpaAuditQueryGateway 提供，封装游标、指纹、快照及 JPA 投影。两套应用独立执行实际 API 和共同根的门禁，每一批重新授权。

Repository禁止导入service.port、infrastructure、jakarta.persistence、Spring Data Page/JpaRepository；返回Map<String,Object>、Entity或把应用Result改名为领域对象均不接受。映射和数据库异常翻译在基础设施适配器就地进行；领域决策在模型/策略，事务在application。

### 6.4 首次导入与授权

不共享整个StructuredImportService/StructuredInitialAuthorizationService。ExcelXmlReader/WorkbookImportPlanner作为来源技术读取与预检可共用，ImportPlan由DDD应用转换为领域ImportManifest；组织合并、账号创建和批量授角色由领域factory/policy表达，再由仓储保存。哈希端口共用，每账号独立盐；公共BootstrapCredentials及明文配置止于应用层和哈希端口，领域只接收含已编码账号种子的AccountInitialization值，不直接依赖公共credentials类型。ImportBatch的种子按稳定来源部门code引用，适配器在同一事务先保存组织，再用新ID映射解析FK，不负责领导合并、状态或角色分类决策。来源摘要/稳定键/原父ID及墓碑核对保持。已有回执不重置后续状态、口令、角色、改名或新删节点。

DddImportService负责一次整批导入事务；DddInitialAuthorizationService预览只读，initialize在根锁下整批发布五角色、关联与回执，失败全回滚。ImportManifest/BootstrapPlan为领域模型，既有ImportReport/RoleBootstrapPlan/BootstrapReport仅在应用边界装配。全部员工启用不等于管理准入，首次人数和分类沿原确认，不把原型演示系统导入正式数据。

原件转换、110部门/10001账号、五角色人数与恢复规则统一见[首次初始化设计](initialization.md)，命令统一见[后端说明](../backend/README.md)。

## 7. 第一个纵向切片：人员角色分配

### 7.1 范围

选用整体替换人员角色，验证共同HTTP契约、独立业务判断、持久化关联、即时生效及完整维护者保护。

### 7.2 结构化实现

StructuredUserManagementService在事务内组织根锁、锁后重验、人员范围及级别保护，经StructuredPersonnelAuthority核查全部角色的有效Permission子集，直接使用JPA仓储整体替换关联；写后检查K，不满足则回滚。

### 7.3 DDD实现

1. API校验目标id和唯一roleIds，转Command；操作者只来自可信身份。
2. 应用服务开启事务、取得可信根锁，重读操作者有效API、归属及目标。
3. 范围策略检查目标、自身/完整管理员限制；角色候选规则检查全部有效Permission ID子集。
4. User行为整体替换roleIds，通过仓储保存，维护者策略验证同一根用户完整K仍存在。
5. 成功提交并转Result/VO；未知关联、越界、最后管理员或数据库故障均回滚。

查询候选与保存分别重验，前端旧草稿不构成授权证据。应用服务通过领域仓储取得事实，由RoleAssignmentPolicy和User.replaceRoles表达规则与变更，不调用结构化服务。

### 7.4 两套实现共同验收

同一请求得到一致的HTTP结果与最终数据库状态。覆盖合法替换、清空、未知角色、范围外目标、本人/同级/上级保护、越级、Permission子集、污染角色、最后维护者、锁后撤权及故障回滚；两套实现使用不同专库执行共享断言。差异片段见第17章，不重画共同链路。

## 8. MySQL物理设计约定

### 8.1 基础规则

沿Java21/JPA/MySQL8.4基线，显式rbac_core、utf8mb4_0900_bin、JPA validate、sql.init.mode=never。仅提供全新库首次SQL，不提供旧库升级；类型、空值及索引以[物理模型](database/physical-model.md)为准。

### 8.2 唯一与关联规则

工号唯一，非空手机号唯一；跨工号/手机号冲突由导入与认证业务额外保证。角色编码、API方法/模板、有效同级部门名及关系去重由数据库约束兜底，树无环、人员范围、角色子集及K由业务事务保证。部门删除保留来源墓碑，重复导入不复原维护结果。

### 8.3 表分组

九张核心表表达六实体及三组多对多关系，另外三张表支撑鉴权日志、来源和首次授权回执。两策略共用这些表，不新增管理员实体、DDD专属表或课程业务表。概念联系及表映射见[数据库设计](database-design.md)。

## 9. 事务与一致性

### 9.1 事务边界

| 场景         | 事务与保护                                                      |
| ------------ | --------------------------------------------------------------- |
| 普通查询     | Service只读事务，数据库范围/筛选/计数/分页                      |
| 相关管理写   | 同一Service事务、同一可信根悲观锁、锁后身份/API/范围重验、写后K |
| 多表整体替换 | 关联删除与新增及状态共同提交；拒绝/故障恢复全部原值             |
| 初始化       | 资料导入与首次授权各自整批事务；预览只读；回执与授权同时提交    |
| 最小日志记录 | 独立事务，失败不改变原允许/拒绝结论；日志查询失败安全报错       |

日志追加统一经 `JpaAuditWriter`。每个 `REQUIRES_NEW` 事务先对 `systems` 中稳定的 `rbac` 目录行加悲观写锁，再保存日志，锁随事务提交或回滚释放。目录行仅作为技术协调点，不根据其启用状态作授权，不取得部门维护根锁。这样所有应用实例的日志取号、提交顺序一致，已提交最大 ID 之前不会再出现晚提交记录；回滚造成的 ID 空洞不影响快照。该临界区只包含短日志写入，日志查询不取此锁，也不将整份日志装入内存。协调行缺失或日志存储失败仍沿既有安全运维事件降级，不改变业务鉴权结果。

### 9.2 根锁、即时生效与完整维护者

所有相关写仍为同一MySQL事务，READ_COMMITTED；需要分页计数一致性的查询采用现有REPEATABLE_READ契约。装配完成后，两套策略均使用同一根行锁，不能锁不同资源造成跨入口竞态。

写步骤：开始应用事务→DDD由DddMaintenanceGuard通过领域仓储、structured由StructuredMaintenanceGuard通过JPA仓储，锁定同一来源证据核实的根→重新读取身份/启用状态、当前实际API、范围和必要级别→加载目标及所需领域事实→调用模型/策略→适配器保存→按修改后的状态重算K→提交。发生拒绝、唯一/FK冲突、死锁或保存故障，全事务回滚。取锁前的MVC预检不替代锁后检查。

K校验必须看见本事务的待提交变更。适配器保存时同步完整关联；只有确实需要在写边界捕获约束或让后续K查询看见写入，才使用saveAndFlush，不额外裸flush。不能在未同步的JPA旧实体图上或独立事务中检查K。检索候选维护者可按页批量取事实，一人完整成立即可；不缓存“曾经有管理员”。最后保留者必须启用、直属可信根、有效基础级别5，且其一个人的有效API覆盖原17项K。

读/写不把授权存入Bearer或跨请求缓存；撤权提交后的新请求用最新事实，已有另一有效链则按OR保留。等待根锁的旧写在锁后失权必须拒绝。在途已执行操作不追溯取消。日志继续独立REQUIRES_NEW及脱敏/去重，业务日志写失败不能改变授权结论。

可信根由来源映射及真实组织覆盖核实，不能按名称或任意null父节点挑选。两套实现跨所有相关写入口使用同一个锁点，防止两个事务各将对方视为最后维护者。

## 10. API、安全与错误

OpenAPI定义37个操作（29项管理操作，含部门及总览5项扩展；另有8项可选通用应用身份操作）；[业务约束](api-contract.md)解释实际API OR、同系统、范围、可授予子集和17项K。API门禁按服务端方法/模板，不依据固定权限code、角色名或客户端admin标识。本人查询与退出沿独立兼容契约。

账号唯一性和口令校验后还需管理端准入。Bearer索引保留在进程内，重启失效；每次管理请求重读最新状态/授权/部门，不把token内旧权限当真。用户停用后已有身份不能管理。统一{code,message,data,requestId}，401/403/404/409各按规范，500不泄露栈、SQL或输入。

最小鉴权日志记录允许/拒绝，技术日志统一AOP只记方法、耗时和异常类型。不得输出口令、token、Authorization、原始手机号登录输入、DTO/VO或完整响应。存储失败只记安全运维事件，禁止把原异常和敏感参数带出。

### 10.1 认证与错误翻译

认证技术共用IdentityTokenStore、PasswordHashing、RequestContext，业务链独立：UserRepository按同一输入精确匹配工号/手机号→AuthenticationPolicy要求唯一、启用、口令符合→AuthorizationPolicy要求本RBAC登记管理API→发令牌。未知/歧义/停用/错口令仍401 LOGIN_FAILED，正确口令但无管理准入仍403；不新增会话实体，也不依赖另一个统一身份提供方。

领域异常DomainViolation只包含领域原因和可公开提示，不携HTTP、SQL或JPA对象；application.support的DddFailureTranslator翻译为既有BusinessException及现行HTTP错误；通用应用准入错误为APPLICATION_ACCESS_DENIED，API Advice保持共用。基础设施在保存发生处把数据库冲突翻译为既有错误，不在全局Advice按表名判断。框架路由404/405仍沿共用Advice；离线初始化错误保持既有CLI语义。

## 11. 测试方案

遵循行为RED→最小实现→回归；两策略共用用例/HTTP契约，分别真实MySQL验证状态、范围、事务回滚、根锁并发与数据库约束。领域单元测试覆盖自身规则，不能用mock数据库代替集成证据。

重点覆盖双入口/歧义、无管理准入、权限OR、外系统、子集陷阱、树循环、候选跨页、撤权/停用即时生效、共同根、K不能拼人及交叉写入口并发。DTO类型/未知字段/缺失id及每个稳定业务错误均需契约测试。各策略实际执行版本及覆盖范围见[测试与验收](testing-acceptance.md)。

### 11.1 测试命名与目录

| 需求组/目录    | 测试类/契约                                                                                                                                      | 旧任务来源                                      |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------- |
| r01            | R01ModelRulesTest、R01JpaConstraintTest                                                                                                          | T02核心模型/真实约束                            |
| r03、r04、r06  | R03ImportJpaContractTest、R03WorkbookImportPlannerTest、R03ExcelSourceTest、R03ExcelImportTest、R04UserIdentifierTest、R06InitialRoleRulesTest | T03/T05原件、账号与首次分类                     |
| r07            | R07AuthenticationContractSupport + R07AuthenticationContractTest                                                                       | T05双入口/准入/查询/日志，覆盖R04/R07—R10/R16等 |
| r11            | R11UserManagementContractSupport + R11UserManagementContractTest                                                                       | T11人员管理、子集/调动/并发保护                 |
| r13            | R13RoleContractSupport + R13RoleContractTest                                                                                           | T09角色、选权/共享维护者                        |
| r14            | R14ResourceContractSupport + R14ResourceContractTest                                                                                   | T07资源树/API映射/并发                          |
| r23            | R23PrototypeContractTest、R24DepartmentPolicyTest                                                                                                  | 共享HTTP级别/目标/部门契约；纯领域根保护、空节点及同父唯一规则 |
| r18            | R18UserQueryValidationTest                                                                                                                       | DTO清洗顺序及安全ID回归                         |
| r22            | R22Import/Resource/Role/UserDatabaseBoundaryTest                                                                                                 | 各专库/profile守卫                              |
| infrastructure | R03ExcelReaderBoundaryTest、R16RequestAuditBoundaryTest、R16ManagedAuditTest                                                                     | 格式/隐私/日志切面职责                          |
| architecture   | R19R21StructuredBoundaryTest、R19CloneFactoryMappingTest、R19LegacyRemovalBoundaryTest                                                           | 分层、真实映射、旧物禁用                        |
| shared         | TestProjectPaths                                                                                                                                 | 统一定位仓库根、后端和原Excel                   |

同一组 HTTP、导入、故障注入与并发测试根据策略配置分别启动两次。锁等待探针观察公共 MaintenanceProtection 接口。使用独立私有 MySQL 实例中的对应专库，保持数据互不干扰。

R19R21StructuredBoundaryTest使用仅测试依赖ArchUnit1.4.1导入生产字节码，检查API、公共端口、基础设施及结构化层依赖；Controller还检查真实非静态字段必须为service.port下用例接口，注释中的UseCase不参与判定。R18ErrorSchemaTest归r18，路由404/405的真实HTTP断言归共享r07契约；前端增量归tests/r17-navigation.test.mjs及r17-workspaces.test.mjs，共享客户端夹具归tests/helpers/api-client.mjs，不放入生产源码或复制另一套页面。

本地运行验收及5项运行器单测位于backend/scripts，覆盖环境隔离和策略传递；测试辅助代码不混入生产src/main。SQL位于deploy，首次结构包含角色级别、组织类型和墓碑，不承担旧数据库升级。实际验证入口见[测试与验收](testing-acceptance.md)。

### 11.2 DDD架构及双实现验证

领域仅依赖JDK与自身模型，领域仓储签名仅模型及JDK容器；应用不依赖JPA/结构化/HTTP。R19BackendStrategyTest检查同一配置入口的互斥装配和未知值，同时保留公共端口和基础设施的单向依赖检查。基础设施依赖DDD领域模型/仓储是合法适配，不得调用DDD应用服务。

R03、R07、R11、R13、R14、R23 等共享契约在两个策略实例分别运行。生成的 CloneFactory 和显式领域映射使用字段断言验证。后端契约、独立JAR、浏览器与部署分别验证，不以共用前端或历史成绩代替另一策略的业务通过。当前执行入口与证据见[测试与验收](testing-acceptance.md)。

## 12. 设计变更与验证原则

业务规则在两套实现分别落实；共享契约测试验证相同输入、授权、错误和持久化结果，架构测试验证依赖方向。变更先取得真实行为RED，再完成最小实现并执行受影响回归。初始化、事务及锁须使用真实MySQL；映射验证生成源码及字段往返，浏览器验证真实HTTP与可操作状态。

新增用例需同步需求、OpenAPI、端口、两套业务实现和操作追溯矩阵。只改文档或部署配置时执行对应链接、配置及工具检查，不把打包或静态审核记为业务回归通过。

## 13. 关键设计决策

| 决策                                       | 依据及影响                                         |
| ------------------------------------------ | -------------------------------------------------- |
| 单应用、共享契约、独立业务实现             | 保证可比较性，避免重复Controller、前端或两套数据库 |
| 六核心对象、领域行为及事实策略             | 聚合不加载全公司对象图，跨聚合不变量不塞入单个实体 |
| 同一可信根锁与本地事务                     | 保证锁后重验、单人完整K及所有相关写失败回滚        |
| 领域仓储与复杂查询Gateway分开              | 不向领域泄漏Command/Result/JPA或技术游标           |
| 本地账号、工号/手机号同身份                | 保留本RBAC认证，技术端口可复用，业务准入各自判断   |
| 可变映射用真实CloneFactory，不可变显式构造 | 保留明确领域构造与不变量；0.0.2的构造器能力不改变本项目约定 |
| 顺序图仅含差异                             | 共同门禁、锁、K与提交由正文约定，不绘制重复执行链  |

上述为本项目设计取舍；后续业务范围变化须同步需求、设计、API及验证，不以参考项目课程业务代替本系统规则。

## 14. CloneFactory对象拷贝方案

采用老师spring-fundamentals的真实CloneFactory0.0.2，固定用户提供源码提交40583ff94764d2e758bcb13be17f5c05ea4d6352；普通依赖和编译期处理器共用pom中的clonefactory.version属性。可复现安装唯一维护在[部署说明](../deploy/README.md#本机依赖安装)，不以ObjectMapper/BeanUtils替代，也不把旧构件换版本号冒充新源码。

DTO→Command、Result→VO、同名同类型toResult优先CloneFactory.copy；PATCH共同非空字段优先copyNotNull。当前Entity→Result由Entity端@CopyTo声明，使纯用例模型不反向依赖持久化类型；角色和API状态PATCH用Entity端@CopyNotNullFrom。派生字段、多来源、集合元素变化、时间类型转换及id/系统归属/授权等受保护字段必须排除并显式处理。

不能用copyNotNull表达parentId“缺省保留/显式null清父”的三态，应保留字段存在标记并显式调用业务行为；不能把Command整包复制进聚合绕过校验。修改注解后clean重编译并验证生成映射行为，不提交生成源码。

### 14.1 领域及不可变对象映射

映射分三段：HTTP DTO→公共Command、公共Result→VO沿现有真实CloneFactory；Entity↔可变领域对象的共同字段可用真实复制后执行受控reconstitute/factory及关系映射。0.0.2真实处理器已支持record及final目标通过构造器返回新对象；本项目仍按既定领域规范对不可变领域对象、record、final属性逐字段显式构造，不能忽略copy返回值或绕过领域不变量。应用Query→领域Criteria、DomainPage→应用PageResult、领域ID/枚举/关系集合转换显式处理。人员查询时 DddScope 校验所选部门，再由 OrganizationPolicy 在可见组织事实内计算子树。应用层显式构造 SearchCriteria.Users，携带领域 Scope、搜索条件、状态及分页。空的部门筛选沿操作者完整范围；未知/越界仍沿现有404/403语义。不可为了映射方便开放权限关系或稳定编码setter。

JpaDomainMapper 显式构造领域记录；DddResults 等装配公开字段，关系和派生值逐项设置。改注解后clean检查真实CloneFactory.java，测试必须断言工号、姓名、部门、职位、级别及授权ID往返，不只断言对象非null。新版Java注解独立换行适用于后续新增/修改源码及测试；映射测试覆盖现有结构化链与DDD链。

### 14.2 结构化映射与边界

- Command/Query/Result/DTO/VO分别使用独立文件并以对应后缀命名。PermissionType为纯用例取值，API/端口不依赖Entity内嵌枚举。
- Entity→Result的CloneFactory声明由Entity端CopyTo维护，避免结果模型反向导入持久化类；同名字段仍用真实处理器。primitive的is前缀、关系ID、集合元素及派生值保留显式转换。
- 角色PATCH及API启停使用copyNotNull；角色编码不在PATCH输入内，parentId缺省/显式null仍显式处理，不绕过树约束。getter/setter/空构造样板交Lombok，保留只读集合和必要数据校验。
- 人员查询q先在DTO trim后验长，部门筛选ID补安全整数上限；Service保留范围及数据库筛选职责。安全整数与空值规则遵守OpenAPI。
- 鉴权日志allowed与后续400/404/409/500业务结果分开；401/403和范围拒绝仍记拒绝。统一层日志覆盖API/Service/Infrastructure，Servlet过滤器生命周期从方法AOP排除，避免代理破坏容器初始化；其安全事件仍由原过滤器采集。

## 15. HTTP DTO与VO规范

api.<业务>.dto下的类以DTO结尾，api.<业务>.vo下的类以VO结尾；请求@Valid，长度、范围、枚举、唯一数组、时间先后和JSON类型在API边界。Service负责授权、范围、状态、树及跨对象一致性，不承担请求格式清洗。

缺省与null、整数安全范围、page/size和日志cursor/limit沿OpenAPI，不照搬老师pageSize。VO只包含契约允许字段，不返回Entity、口令哈希、手机号或导入原文。Controller负责映射，不承担查询/持久化；持久化约束错误在Adapter/Service写边界转稳定业务错误，Advice只统一HTTP响应。

Java新增或修改类/接口/记录及关键方法写中文Javadoc；明确局部类型，不用var；每个注解独占一行，源码使用真实换行。领域状态/属性常量定义在对应领域对象，公共协议取值经应用Mapper转换。

## 16. 运行与部署设计

产品RbacApplication、离线ImportCommand和首次BootstrapAuthorizationCommand相互分离。显式rbac_core、ddl-auto=validate、sql.init.mode=never、旧初始化关闭；错库/测试profile组合失败关闭，不在启动时恢复旧表或重授角色。口令/数据库秘密通过环境或configtree提供。

本地构建、测试及初始化见[后端说明](../backend/README.md)，服务器执行见[部署指南](../deploy/README.md)。Swarm按节点职责运行两个Nginx、两个单副本DDD后端、MySQL、课程接入支撑及Prometheus；待统一发布的监控配置增加全局node-exporter，采集节点CPU等宿主机指标，见[监控设计](../deploy/monitoring.md)。公网仅C的80/8088，业务、Actuator、数据库及监控均留内网。

独立课程接入支撑负责实时身份转接、配置短码核查和独立业务审计，不进入RBAC领域/应用。两个Java服务的层日志使用北京时间，存储/接口Instant及JWT继续UTC。会话索引在进程内，后端单副本、stop-first，重启须重新登录；两策略不创建第二套业务表。数据变更不能仅靠回滚镜像恢复。当前线上版本与待发布修改见[验收入口](testing-acceptance.md)。

## 17. 结构化与DDD UML

以当前源码为准，两套实现的类图按相同15个模块成对展示，通用应用身份另有1对；不再只展示DDD。共享技术和两个策略的静态依赖可以出现在类图，顺序图只画实际实现差异。

### 17.1 结构化与DDD包图

![structured包图](design/package-structured-current.png)

[SVG](design/package-structured-current.svg)

![DDD包图](design/package-ddd-design.png)

[SVG](design/package-ddd-design.svg)

### 17.2 模块类图

每张以实际Service为中心，字段关联标role；Repository/Entity与领域仓储/领域模型分别展示。列出关键成员，省略无关getter/setter。两套技术存储和HTTP契约共用，并不表示业务实现只有一套。

#### 身份与准入（identity）

structured：`StructuredAuthenticationService`，直接依赖`UserJpaRepository`及`StructuredEffectivePermissionService`。

![structured 身份与准入](design/class-structured-identity.png)

[structured SVG](design/class-structured-identity.svg)

DDD：`DddAuthenticationService`，领域仓储/模型及策略边界如下。

![DDD 身份与准入](design/class-ddd-identity.png)

[DDD SVG](design/class-ddd-identity.svg)

#### 人员角色、停用与调动（personnel）

structured：`StructuredUserManagementService`，直接依赖`UserJpaRepository`及`StructuredPersonnelAuthority`。

![structured 人员角色、停用与调动](design/class-structured-personnel.png)

[structured SVG](design/class-structured-personnel.svg)

DDD：`DddUserManagementService`，领域仓储/模型及策略边界如下。

![DDD 人员角色、停用与调动](design/class-ddd-personnel.png)

[DDD SVG](design/class-ddd-personnel.svg)

#### 范围内人员查询（query）

structured：`StructuredUserQueryService`，直接依赖`UserJpaRepository`及`StructuredDepartmentScopeService`。

![structured 范围内人员查询](design/class-structured-query.png)

[structured SVG](design/class-structured-query.svg)

DDD：`DddUserQueryService`，领域仓储/模型及策略边界如下。

![DDD 范围内人员查询](design/class-ddd-query.png)

[DDD SVG](design/class-ddd-query.svg)

#### 共享角色及直接权限（role）

structured：`StructuredRoleService`，直接依赖`RoleJpaRepository`及`StructuredGlobalManagementAccess`。

![structured 共享角色及直接权限](design/class-structured-role.png)

[structured SVG](design/class-structured-role.svg)

DDD：`DddRoleService`，领域仓储/模型及策略边界如下。

![DDD 共享角色及直接权限](design/class-ddd-role.png)

[DDD SVG](design/class-ddd-role.svg)

#### 权限树及API映射（resource）

structured：`StructuredResourceService`，直接依赖`PermissionJpaRepository`及`StructuredGlobalManagementAccess`。

![structured 权限树及API映射](design/class-structured-resource.png)

[structured SVG](design/class-structured-resource.svg)

DDD：`DddResourceService`，领域仓储/模型及策略边界如下。

![DDD 权限树及API映射](design/class-ddd-resource.png)

[DDD SVG](design/class-ddd-resource.svg)

#### 部门维护（department）

structured：`StructuredDepartmentService`，直接依赖`DepartmentJpaRepository`及`StructuredDepartmentScopeService`。

![structured 部门维护](design/class-structured-department.png)

[structured SVG](design/class-structured-department.svg)

DDD：`DddDepartmentService`，领域仓储/模型及策略边界如下。

![DDD 部门维护](design/class-ddd-department.png)

[DDD SVG](design/class-ddd-department.svg)

#### 范围总览（overview）

structured：`StructuredOverviewService`，直接依赖`UserJpaRepository`及`StructuredDepartmentService`。

![structured 范围总览](design/class-structured-overview.png)

[structured SVG](design/class-structured-overview.svg)

DDD：`DddOverviewService`，领域仓储/模型及策略边界如下。

![DDD 范围总览](design/class-ddd-overview.png)

[DDD SVG](design/class-ddd-overview.svg)

#### 最小鉴权日志查询（audit）

structured：`StructuredAuditLogService`，直接依赖`AuditQueryGateway`及`StructuredDepartmentScopeService`。

![structured 最小鉴权日志查询](design/class-structured-audit.png)

[structured SVG](design/class-structured-audit.svg)

DDD：`DddAuditLogService`，领域仓储/模型及策略边界如下。

![DDD 最小鉴权日志查询](design/class-ddd-audit.png)

[DDD SVG](design/class-ddd-audit.svg)

#### 新项目组织账号导入（importing）

structured：`StructuredImportService`，直接依赖`ImportSourceJpaRepository`及`SourceWorkbookReader`。

![structured 新项目组织账号导入](design/class-structured-importing.png)

[structured SVG](design/class-structured-importing.svg)

DDD：`DddImportService`，领域仓储/模型及策略边界如下。

![DDD 新项目组织账号导入](design/class-ddd-importing.png)

[DDD SVG](design/class-ddd-importing.svg)

#### 首次批量授权（bootstrap）

structured：`StructuredInitialAuthorizationService`，直接依赖`RoleJpaRepository`及`RoleBootstrapPlanner`。

![structured 首次批量授权](design/class-structured-bootstrap.png)

[structured SVG](design/class-structured-bootstrap.svg)

DDD：`DddInitialAuthorizationService`，领域仓储/模型及策略边界如下。

![DDD 首次批量授权](design/class-ddd-bootstrap.png)

[DDD SVG](design/class-ddd-bootstrap.svg)

#### 完整维护者保护（maintenance）

structured：`StructuredMaintenanceGuard`，直接依赖`UserJpaRepository`及`StructuredEffectivePermissionService`。

![structured 完整维护者保护](design/class-structured-maintenance.png)

[structured SVG](design/class-structured-maintenance.svg)

DDD：`DddMaintenanceGuard`，领域仓储/模型及策略边界如下。

![DDD 完整维护者保护](design/class-ddd-maintenance.png)

[DDD SVG](design/class-ddd-maintenance.svg)

#### API资源启停（api-resource）

structured：`StructuredResourceService`，直接依赖`ApiResourceJpaRepository`及`StructuredGlobalManagementAccess`。

![structured API资源启停](design/class-structured-api-resource.png)

[structured SVG](design/class-structured-api-resource.svg)

DDD：`DddResourceService`，领域仓储/模型及策略边界如下。

![DDD API资源启停](design/class-ddd-api-resource.png)

[DDD SVG](design/class-ddd-api-resource.svg)

#### 已登记应用目录（systems）

structured：`StructuredResourceService`，直接依赖`ApplicationSystemJpaRepository`及`ManagedApplications`。

![structured 已登记应用目录](design/class-structured-systems.png)

[structured SVG](design/class-structured-systems.svg)

DDD：`DddResourceService`，领域仓储/模型及策略边界如下。

![DDD 已登记应用目录](design/class-ddd-systems.png)

[DDD SVG](design/class-ddd-systems.svg)

#### 逐请求有效授权（authorization）

structured：`StructuredEffectivePermissionService`，直接依赖`ApiResourceJpaRepository`及`ManagedApplications`。

![structured 逐请求有效授权](design/class-structured-authorization.png)

[structured SVG](design/class-structured-authorization.svg)

DDD：`DddAuthorizationService`，领域仓储/模型及策略边界如下。

![DDD 逐请求有效授权](design/class-ddd-authorization.png)

[DDD SVG](design/class-ddd-authorization.svg)

#### 实际API与共同根门禁（management-access）

structured：`StructuredGlobalManagementAccess`，直接依赖`StructuredEffectivePermissionService`及`StructuredDepartmentScopeService`。

![structured 实际API与共同根门禁](design/class-structured-management-access.png)

[structured SVG](design/class-structured-management-access.svg)

DDD：`DddManagementAccess`，领域仓储/模型及策略边界如下。

![DDD 实际API与共同根门禁](design/class-ddd-management-access.png)

[DDD SVG](design/class-ddd-management-access.svg)

#### 通用应用身份（application-identity）

![structured通用身份](design/class-structured-application-identity.png)

[SVG](design/class-structured-application-identity.svg) · [PlantUML](design/class-structured-application-identity.puml)

![DDD通用身份](design/class-ddd-application-identity.png)

[SVG](design/class-ddd-application-identity.svg) · [PlantUML](design/class-ddd-application-identity.puml)

### 17.3 差异顺序图

逐项核对37个HTTP操作、2个离线入口及横切支撑后，按24组独立差异编排，每组structured/DDD各1张，共48张。重复使用同一实现模式的接口只引用同一组图，具体对应见17.4；不是将每个接口复制两遍。

共同HTTP绑定/校验、Controller、DTO/VO映射、JWT签发/解析、AOP、技术日志、根锁取得、锁后重鉴权、最终保护调用、保存后的提交/回滚不画。普通保存本身不再作为两套差异重复展示；ImportBatch/Publication是领域批次边界，属于真实接口差异。`maintenance`组只画完整维护者资格算法，不重复最终保护/锁的共同编排。

每图从所列实际方法的差异片段切入；片段名称不是新增方法。同步调用编号，赋值消息不重复画普通回复；每次调用各有短执行条，静态类标MetaClass。循环与条件框只包含实际分支。共用拒绝码和事务失败路径在第9—10章、API契约规定，不因成功片段省略而消失。

#### 有效API计算（authorization）

structured直接查询有效API链；DDD读取AuthorizationFacts并调用AuthorizationPolicy。

源码：`service/structured/security/StructuredEffectivePermissionService.java`的`routes`；DDD：`DddAuthorizationService.routes`。

![structured 有效API计算差异](design/sequence-authorization-structured.png)

[structured SVG](design/sequence-authorization-structured.svg)

![DDD 有效API计算差异](design/sequence-authorization-ddd.png)

[DDD SVG](design/sequence-authorization-ddd.svg)

#### 人员角色整体替换（personnel）

锁后仅校验角色差集：structured在Service操作Entity关联，DDD以RoleAssignmentPolicy校验并返回新的User。

源码：`service/structured/user/StructuredUserManagementService.java`的`replaceRoles`；DDD：`DddUserManagementService.replaceRoles`。

![structured 人员角色整体替换差异](design/sequence-personnel-structured.png)

[structured SVG](design/sequence-personnel-structured.svg)

![DDD 人员角色整体替换差异](design/sequence-personnel-ddd.png)

[DDD SVG](design/sequence-personnel-ddd.svg)

#### 角色权限整体替换（role-permissions）

structured校验集合后增删Entity关联；DDD由PermissionSelectionPolicy一次校验并替换不可变Role。

源码：`service/structured/role/StructuredRoleService.java`的`replacePermissions`；DDD：`DddRoleService.replacePermissions`。

![structured 角色权限整体替换差异](design/sequence-role-permissions-structured.png)

[structured SVG](design/sequence-role-permissions-structured.svg)

![DDD 角色权限整体替换差异](design/sequence-role-permissions-ddd.png)

[DDD SVG](design/sequence-role-permissions-ddd.svg)

#### 权限树修改（permission-tree）

structured调用Entity局部树保护；DDD读取完整树后由PermissionTreePolicy生成新Permission。

源码：`service/structured/resource/StructuredResourceService.java`的`patchPermission`；DDD：`DddResourceService.patchPermission`。

![structured 权限树修改差异](design/sequence-permission-tree-structured.png)

[structured SVG](design/sequence-permission-tree-structured.svg)

![DDD 权限树修改差异](design/sequence-permission-tree-ddd.png)

[DDD SVG](design/sequence-permission-tree-ddd.svg)

#### 新增合法下级部门（department-create）

structured的Service校验类型/重名并移动Entity；DDD调用OrganizationPolicy与Department.child。

源码：`service/structured/department/StructuredDepartmentService.java`的`create`；DDD：`DddDepartmentService.create`。

![structured 新增合法下级部门差异](design/sequence-department-create-structured.png)

[structured SVG](design/sequence-department-create-structured.svg)

![DDD 新增合法下级部门差异](design/sequence-department-create-ddd.png)

[DDD SVG](design/sequence-department-create-ddd.svg)

#### 范围内人员分页（user-query）

structured直接count/findAll；DDD构造Users条件，由领域仓储返回DomainPage。角色/API分页复用此访问边界模式，类型在覆盖矩阵列明。

源码：`service/structured/user/StructuredUserQueryService.java`的`list`；DDD：`DddUserQueryService.list`。

![structured 范围内人员分页差异](design/sequence-user-query-structured.png)

[structured SVG](design/sequence-user-query-structured.svg)

![DDD 范围内人员分页差异](design/sequence-user-query-ddd.png)

[DDD SVG](design/sequence-user-query-ddd.svg)

#### 凭据匹配与账号有效性（credentials）

structured的Service检查匹配数和enabled；DDD由AuthenticationPolicy筛选领域User。共同口令校验与JWT签发不画。

源码：`service/structured/auth/StructuredAuthenticationService.java`的`verify`；DDD：`DddAuthenticationService.verify`。

![structured 凭据匹配与账号有效性差异](design/sequence-credentials-structured.png)

[structured SVG](design/sequence-credentials-structured.svg)

![DDD 凭据匹配与账号有效性差异](design/sequence-credentials-ddd.png)

[DDD SVG](design/sequence-credentials-ddd.svg)

#### 组织子树范围（organization-scope）

structured由递归SQL取得子树；DDD用OrganizationPolicy从组织事实计算范围。外部目录复用同一差异模式。

源码：`service/structured/security/StructuredDepartmentScopeService.java`的`allowedIds`；DDD：`DddScope.filter`。

![structured 组织子树范围差异](design/sequence-organization-scope-structured.png)

[structured SVG](design/sequence-organization-scope-structured.svg)

![DDD 组织子树范围差异](design/sequence-organization-scope-ddd.png)

[DDD SVG](design/sequence-organization-scope-ddd.svg)

#### 可信共同根判断（global-scope）

structured比较来源根、父级及SQL覆盖数；DDD由OrganizationPolicy.global判断可信共同根。实际API门禁沿authorization复用。

源码：`service/structured/security/StructuredDepartmentScopeService.java`的`isGlobal`；DDD：`DddManagementAccess.require`。

![structured 可信共同根判断差异](design/sequence-global-scope-structured.png)

[structured SVG](design/sequence-global-scope-structured.svg)

![DDD 可信共同根判断差异](design/sequence-global-scope-ddd.png)

[DDD SVG](design/sequence-global-scope-ddd.svg)

#### 候选角色的数据库筛选（assignable-roles）

structured构造JPA Specification并分别计数/分页；DDD将完整AssignableRoleCriteria交给AuthorizationRepository。

源码：`service/structured/user/StructuredUserManagementService.java`的`assignableRoles`；DDD：`DddUserManagementService.assignableRoles`。

![structured 候选角色的数据库筛选差异](design/sequence-assignable-roles-structured.png)

[structured SVG](design/sequence-assignable-roles-structured.svg)

![DDD 候选角色的数据库筛选差异](design/sequence-assignable-roles-ddd.png)

[DDD SVG](design/sequence-assignable-roles-ddd.svg)

#### 人员停用的状态修改（user-disable）

structured修改受管Entity.enabled；DDD调用User.disable返回新领域对象。目标保护分别位于辅助Service和领域策略。

源码：`service/structured/user/StructuredUserManagementService.java`的`disable`；DDD：`DddUserManagementService.disable`。

![structured 人员停用的状态修改差异](design/sequence-user-disable-structured.png)

[structured SVG](design/sequence-user-disable-structured.svg)

![DDD 人员停用的状态修改差异](design/sequence-user-disable-ddd.png)

[DDD SVG](design/sequence-user-disable-ddd.svg)

#### 人员调动的双范围与归属（user-transfer）

structured校验范围后调用Entity.moveTo；DDD用领域范围和User.transfer形成新User。共同写事务及保存省略。

源码：`service/structured/user/StructuredUserManagementService.java`的`move`；DDD：`DddUserManagementService.move`。

![structured 人员调动的双范围与归属差异](design/sequence-user-transfer-structured.png)

[structured SVG](design/sequence-user-transfer-structured.svg)

![DDD 人员调动的双范围与归属差异](design/sequence-user-transfer-ddd.png)

[DDD SVG](design/sequence-user-transfer-ddd.svg)

#### 空权限角色创建（role-create）

structured检查编码后创建空权限RoleEntity；DDD调用Role.custom，持久化适配器兜底编码唯一性。

源码：`service/structured/role/StructuredRoleService.java`的`create`；DDD：`DddRoleService.create`。

![structured 空权限角色创建差异](design/sequence-role-create-structured.png)

[structured SVG](design/sequence-role-create-structured.svg)

![DDD 空权限角色创建差异](design/sequence-role-create-ddd.png)

[DDD SVG](design/sequence-role-create-ddd.svg)

#### 角色名称与状态修改（role-update）

structured通过CloneFactory原地更新Entity；DDD调用Role.revise。说明框标明本地赋值，不展开共用映射器技术链。

源码：`service/structured/role/StructuredRoleService.java`的`patch`；DDD：`DddRoleService.patch`。

![structured 角色名称与状态修改差异](design/sequence-role-update-structured.png)

[structured SVG](design/sequence-role-update-structured.svg)

![DDD 角色名称与状态修改差异](design/sequence-role-update-ddd.png)

[DDD SVG](design/sequence-role-update-ddd.svg)

#### API启停的状态修改（api-availability）

structured原地更新ApiResourceEntity；DDD调用ApiResource.changeAvailability生成新值。

源码：`service/structured/resource/StructuredResourceService.java`的`patchApi`；DDD：`DddResourceService.patchApi`。

![structured API启停的状态修改差异](design/sequence-api-availability-structured.png)

[structured SVG](design/sequence-api-availability-structured.svg)

![DDD API启停的状态修改差异](design/sequence-api-availability-ddd.png)

[DDD SVG](design/sequence-api-availability-ddd.svg)

#### 权限与API的同系统映射（permission-mapping）

structured逐项更改Entity关系差集；DDD用PermissionTreePolicy.map核实完整同系统集合并返回新Permission。

源码：`service/structured/resource/StructuredResourceService.java`的`replaceMapping`；DDD：`DddResourceService.replaceMapping`。

![structured 权限与API的同系统映射差异](design/sequence-permission-mapping-structured.png)

[structured SVG](design/sequence-permission-mapping-structured.svg)

![DDD 权限与API的同系统映射差异](design/sequence-permission-mapping-ddd.png)

[DDD SVG](design/sequence-permission-mapping-ddd.svg)

#### 部门改名与唯一性（department-rename）

structured在Service判同父重名后修改Entity；DDD用OrganizationPolicy判重及Department.rename。

源码：`service/structured/department/StructuredDepartmentService.java`的`rename`；DDD：`DddDepartmentService.rename`。

![structured 部门改名与唯一性差异](design/sequence-department-rename-structured.png)

[structured SVG](design/sequence-department-rename-structured.svg)

![DDD 部门改名与唯一性差异](design/sequence-department-rename-ddd.png)

[DDD SVG](design/sequence-department-rename-ddd.svg)

#### 部门空节点与可信根保护（department-delete）

structured直接查人员/下级并设墓碑；DDD用usage事实、requireNonRoot/requireEmpty和Department.tombstone。

源码：`service/structured/department/StructuredDepartmentService.java`的`delete`；DDD：`DddDepartmentService.delete`。

![structured 部门空节点与可信根保护差异](design/sequence-department-delete-structured.png)

[structured SVG](design/sequence-department-delete-structured.svg)

![DDD 部门空节点与可信根保护差异](design/sequence-department-delete-ddd.png)

[DDD SVG](design/sequence-department-delete-ddd.svg)

#### 组织树及人数投影（department-view）

structured在部门Service计数、排序及递归组树；DDD部门Service委托DddDepartmentView装配领域事实投影。

源码：`service/structured/department/StructuredDepartmentService.java`的`list`；DDD：`DddDepartmentService.list`。

![structured 组织树及人数投影差异](design/sequence-department-view-structured.png)

[structured SVG](design/sequence-department-view-structured.svg)

![DDD 组织树及人数投影差异](design/sequence-department-view-ddd.png)

[DDD SVG](design/sequence-department-view-ddd.svg)

#### 总览统计的协作边界（overview）

structured调用另一个具体部门Service并单查启用人数；DDD调用同层View，以同份统计投影组织树及人数。

源码：`service/structured/department/StructuredOverviewService.java`的`overview`；DDD：`DddOverviewService.overview`。

![structured 总览统计的协作边界差异](design/sequence-overview-structured.png)

[structured SVG](design/sequence-overview-structured.svg)

![DDD 总览统计的协作边界差异](design/sequence-overview-ddd.png)

[DDD SVG](design/sequence-overview-ddd.svg)

#### 完整维护者判定（maintenance）

只比较完整维护者规则的实现位置：structured遍历根用户并检查级别/K；DDD由MaintenancePolicy判断单人的授权事实。根锁及最终保护调用顺序不画。

源码：`service/structured/security/StructuredMaintenanceGuard.java`的`retainOne`；DDD：`DddMaintenanceGuard.retainOne`。

![structured 完整维护者判定差异](design/sequence-maintenance-structured.png)

[structured SVG](design/sequence-maintenance-structured.svg)

![DDD 完整维护者判定差异](design/sequence-maintenance-ddd.png)

[DDD SVG](design/sequence-maintenance-ddd.svg)

#### 来源核对与导入批次（importing）

structured核对来源、逐项创建Entity；DDD由ImportPolicy产出ImportBatch并交领域仓储。已有来源与首次导入分支明确区分。

源码：`service/structured/importing/StructuredImportService.java`的`importDirectory`；DDD：`DddImportService.importDirectory`。

![structured 来源核对与导入批次差异](design/sequence-importing-structured.png)

[structured SVG](design/sequence-importing-structured.svg)

![DDD 来源核对与导入批次差异](design/sequence-importing-ddd.png)

[DDD SVG](design/sequence-importing-ddd.svg)

#### 首次授权规划与发布（bootstrap）

structured的规划器/Service直接核来源、账号与目录；DDD由InitialAuthorizationPolicy规划/校验并发布领域Publication。重复回执和共同根锁不展开。

源码：`service/structured/bootstrap/StructuredInitialAuthorizationService.java`的`initialize`；DDD：`DddInitialAuthorizationService.initialize`。

![structured 首次授权规划与发布差异](design/sequence-bootstrap-structured.png)

[structured SVG](design/sequence-bootstrap-structured.svg)

![DDD 首次授权规划与发布差异](design/sequence-bootstrap-ddd.png)

[DDD SVG](design/sequence-bootstrap-ddd.svg)

#### 通用应用角色与短码投影（application-projection）

structured的Service执行exportable筛选并投影角色/短码；DDD由ApplicationAuthorizationPolicy计算导出集合。未知/空授权错误契约相同，不重复失败时序。

源码：`service/structured/identity/StructuredApplicationIdentityService.java`的`view`；DDD：`DddApplicationIdentityService.view`。

![structured 通用应用角色与短码投影差异](design/sequence-application-projection-structured.png)

[structured SVG](design/sequence-application-projection-structured.svg)

![DDD 通用应用角色与短码投影差异](design/sequence-application-projection-ddd.png)

[DDD SVG](design/sequence-application-projection-ddd.svg)

### 17.4 覆盖与省略矩阵

每个操作均有明确结论。“共同流程”表示两套语义和协作相同；纯类型/结果装配差异由类图说明，不制造仅换类名的顺序图。

<!-- GENERATED: backend-coverage -->
| HTTP操作 | 用例方法 | 差异图或省略理由 | 核对结论 |
| --- | --- | --- | --- |
| `POST /api/auth/login` | `AuthenticationUseCase.login` | credentials / authorization / global-scope | 两类实现独立校验账号和管理准入；JWT签发共用。 |
| `POST /api/auth/logout` | `AuthenticationUseCase.logout` | 共同流程，不画 | 两套均调用IdentityTokenStore.revoke；幂等管理退出完全相同。 |
| `GET /api/me` | `AuthenticationUseCase.me` | credentials / authorization / global-scope | 账号读取和有效能力按上述差异复用；DTO/Result装配相同，不再画。 |
| `GET /api/me/permissions` | `AuthenticationUseCase.myPermissions` | authorization | 使用同一有效授权差异，按稳定ID排序与输出为共同后置。 |
| `GET /api/departments/tree` | `UserQueryUseCase.tree` | organization-scope / department-view | 可见树最终委托对应部门查询；无第二套相同树流程。 |
| `GET /api/users` | `UserQueryUseCase.list` | organization-scope / user-query | UserJpaRepository.count/findAll 对 UserRepository.search(Users)。 |
| `GET /api/users/{id}/roles` | `UserManagementUseCase.roles` | personnel / assignable-roles | 完整角色与lockedRoleIds基于同一目标保护/角色资格差异；集合读取和排序相同。 |
| `PUT /api/users/{id}/roles` | `UserManagementUseCase.replaceRoles` | personnel | 只检查新增/删除角色，保留无权改变的现有项。 |
| `GET /api/users/{id}/assignable-roles` | `UserManagementUseCase.assignableRoles` | assignable-roles | 完整权限子集、有效级别、自定义资格及范围均在分页前过滤。 |
| `PATCH /api/users/{id}/status` | `UserManagementUseCase.disable` | user-disable | 目标保护后实体修改与领域行为不同，原JWT停用即时拒绝相同。 |
| `PUT /api/users/{id}/department` | `UserManagementUseCase.move` | user-transfer | 原部门和目标部门的双范围及不可变归属修改。 |
| `GET /api/roles` | `RoleUseCase.list` | user-query（分页模式复用） | RoleJpaRepository.count/findAll 对 RoleRepository.search(Roles)；无新分页算法。 |
| `POST /api/roles` | `RoleUseCase.create` | role-create | 空权限角色构造及唯一约束翻译边界。 |
| `PATCH /api/roles/{id}` | `RoleUseCase.patch` | role-update | 字段原地拷贝对领域Role.revise。 |
| `GET /api/roles/{id}/permissions` | `RoleUseCase.selected` | 共同集合读取，不画 | Entity/领域Role读取直接权限编号后排序；类型差异由两套role类图说明。 |
| `PUT /api/roles/{id}/permissions` | `RoleUseCase.replacePermissions` | role-permissions | 直接权限完整集合替换，不按API子集误判。 |
| `GET /api/systems` | `ResourceUseCase.systems` | 共同目录读取，不画 | 均按已登记应用及状态读取；ApplicationSystemJpaRepository对领域ApplicationSystemRepository，静态边界见systems类图。 |
| `GET /api/permissions/tree` | `ResourceUseCase.tree` | 共同只读投影，不画 | 按系统读取并转换权限树；PermissionJpaRepository对领域PermissionRepository，静态边界见resource类图。 |
| `GET /api/api-resources` | `ResourceUseCase.apis` | user-query（分页模式复用） | ApiResourceJpaRepository.count/findAll 对 ApiResourceRepository.search(Apis)；同一范围先筛后分页模式。 |
| `PATCH /api/permissions/{id}` | `ResourceUseCase.patchPermission` | permission-tree | 父级显式null、类型及循环/同系统不变量。 |
| `PATCH /api/api-resources/{id}` | `ResourceUseCase.patchApi` | api-availability | API启停的实体状态对不可变领域值。 |
| `GET /api/permissions/{id}/apis` | `ResourceUseCase.mapping` | 共同集合读取，不画 | 读取完整映射、包含停用项并排序；不重复画与getRolePermissions相同的关联读取模式。 |
| `PUT /api/permissions/{id}/apis` | `ResourceUseCase.replaceMapping` | permission-mapping | 差集关联对完整同系统映射领域策略。 |
| `GET /api/authorization-logs` | `AuditLogUseCase.list` | global-scope / authorization | 日志准入复用；两套都调用同一AuditQueryGateway，游标/快照/脱敏流程相同，不画双份。 |
| `GET /api/departments` | `DepartmentUseCase.list` | organization-scope / department-view | 与部门树相同用例，复用同一图。 |
| `POST /api/departments` | `DepartmentUseCase.create` | department-create | 合法父子类型、同父重名及构造。 |
| `PATCH /api/departments/{id}` | `DepartmentUseCase.rename` | department-rename | 保留组织身份及真实人数。 |
| `DELETE /api/departments/{id}` | `DepartmentUseCase.delete` | department-delete | 可信根和空节点保护后软删除。 |
| `GET /api/overview` | `OverviewUseCase.overview` | overview / department-view | 同快照人数、直接下级分布及两种协作边界。 |
| `POST /auth/login` | `ApplicationIdentityUseCase.login` | credentials / application-projection | 登记应用检查及JWT签发相同；只画账号和有效投影差异。 |
| `GET /auth/me` | `ApplicationIdentityUseCase.current` | application-projection | 逐请求重算本应用角色/短码/API事实。 |
| `GET /auth/permissions/{permission}` | `ApplicationIdentityUseCase.allowed` | application-projection | current后contains(code)完全相同，不增加重复顺序图。 |
| `GET /auth/users/{userId}` | `ApplicationIdentityUseCase.user` | application-projection | 实际查人API映射门禁复用投影；按ID读取与最小3字段响应相同，JPA/领域仓储边界见类图。 |
| `POST /auth/logout` | `ApplicationIdentityUseCase.logout` | 共同流程，不画 | 受众核对及当前会话撤销完全相同，不绘制两张JWT退出图。 |
| `GET /auth/departments` | `ApplicationIdentityUseCase.departments` | application-projection / organization-scope | 目录门禁和范围依上述差异；IdentityDirectoryGateway的计数/分页SQL是共用实现，不画双份。 |
| `GET /auth/departments/{departmentId}/users` | `ApplicationIdentityUseCase.departmentUsers` | application-projection / organization-scope | 直属部门范围差异同上；五字段及page/pageSize查询共用。 |
| `GET /auth/api-permissions/{operationId}` | `ApplicationIdentityUseCase.apiAllowed` | application-projection | ApiOperationCatalog定位、permissionRoles和路由匹配算法相同，仅current投影依两套实现。 |
<!-- END: backend-coverage -->

| 非HTTP入口/支撑 | 对应差异 | 共同部分 |
| --- | --- | --- |
| ImportUseCase.importDirectory | importing；来源校验与领域批次 | Excel读取、BCrypt口令编码及整批事务 |
| AuthorizationBootstrapUseCase.preview/initialize | bootstrap；规划、账号/来源核验及Publication | 只读预览、已有回执不重置、锁和K调用 |
| ManagementAccessUseCase/ManagementAuthorization | global-scope / authorization | 同一服务端实际方法/模板门禁 |
| MaintenanceProtection | maintenance（资格算法） | 根锁、锁后重鉴权、最终保护的顺序 |
| JWT、AuditQueryGateway、IdentityDirectoryGateway、ApiOperationCatalog | 共用技术实现，不画双份 | 签名、单会话撤销、快照、目录SQL与操作配置 |

当前图集82组：包图2、类图32、差异顺序图48。生成数据、源码锚点及运行命令见[图件说明](design/README.md)。被现行图集替代的旧片段及重复图已清理，原版本可从Git历史恢复。

## 18. 通用应用身份与课程本地接入

RBAC负责通用应用、角色、权限、API资源与身份协议。`ApplicationIdentityController`适配8项外部协议；shared port不承载实现，structured直接使用JPA，DDD从领域仓储装配事实并调用ApplicationAuthorizationPolicy。类图与application-projection、organization-scope差异统一收在第17章。

管理端`/api/auth/login`与外部`/auth/login`统一经`JwtIdentityStore`和`JwtIdentityCodec`签发HS256 JWT，只有sub/aud/iss/iat/exp/jti，没有角色权限快照。管理aud固定rbac；外部aud为单个已登记启用应用。两种入口保持原字段token/accessToken及响应包装，均通过Authorization: Bearer携带；相同格式不能越过受众或管理准入。

JWT签名、issuer、严格到期和主体/单受众由标准JOSE/Nimbus校验。进程内会话索引用于单会话即时退出，重启后旧会话失效；固定签名秘密也不代表已支持多实例共享会话。IDENTITY_JWT_SECRET可配置至少256位Base64密钥，未配置每进程随机；IDENTITY_JWT_ISSUER配置issuer，两者同时用于两类JWT，APP_IDENTITY_ENABLED只控制外部8项端点，不控制管理JWT。

角色、账号启停、权限及部门范围逐请求读取。API类型短码须有实际映射且全部映射同应用启用；通用/auth/me返回subject、roles、permissions、system、permissionApis。角色可跨应用拥有权限，Permission与ApiResource映射必须同应用。

外部目录采用page从1、pageSize默认20最多100，按人员子树逐层展开直接子部门，直属人员只返回5个最小字段。IdentityDirectoryGateway在同一数据库快照先筛选、计数、分页。operationId只由IDENTITY_OPERATIONS_FILE显式配置定位，未知操作404、损坏/重复配置启动失败；permissionRoles只决定全局门禁适用角色，课程对象权限仍由课程Service判断。

权限查询通过RequestContext.permissionCheck把真假结果交给共享过滤器，生成独立PERMISSION_CHECK事件；两套用例仍各自计算权限，日志技术不承载业务决策。日志Gateway在已获日志权限的事务内附带登记应用目录，Controller显式映射systems元素；不扩大GET /api/systems的资源目录准入，不新增HTTP端点或数据库表。此日志步骤为共享流程，双策略差异顺序图继续省略。

课程teacher/student/course-admin及路由保存在integrations/course配置中。课程首次接入按总公司本部范围分配teacher、其余student，合格根直属五级RBAC管理员同账号兼course-admin，并建立老师课程本地绑定；由独立初始化脚本显式执行，不加入RBAC领域。课程先提交绑定、RBAC后在根锁事务发布通用授权和回执，不宣称分布式原子事务；重复回执不重置后续授权。

老师原目录保持独立；原本机联调副本保留3个structured生产补丁，固定990cea2的部署副本另有DDD对等修复、当前学期标记释放及安全日志补丁。统一身份审计另2项不在RBAC8项契约中；本机支撑继续保留，服务器采用独立Waitress/MySQL持久保存课程业务审计，老师缺少的Controller等来源限制继续保留。配置、端口及已知缺口以[课程接入](course-integration.md)为准；两个后端均DDD的正式运行边界见[部署说明](../deploy/README.md)，实际云端结果见[发布记录](history/verification/ddd-production-deployment.md)。详设外部文件不改。
