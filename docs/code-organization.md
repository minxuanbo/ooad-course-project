# 结构化代码与测试归类

当前分支codex/rbac-vue-implementation（PR #32），已包含阶段2结构化归类及阶段4正式Vue/后端变更。阶段2历史起点d1438d7及验收独立保留；本文以下目录和依赖说明以当前源码为准。DDD及服务器部署另行安排。

## 1. 项目及生产代码边界

后端Maven项目从根pom.xml/src移到backend；frontend仍是生产Vue入口，prototype仍是参考交互，docs维护正式规范。全库只保留一份后端源码，不复制structured/ddd两套工程。实际目录与本地命令见[backend/README](../backend/README.md)。

| 目录 | 责任与依赖 |
| --- | --- |
| api.auth/user/department/role/resource/audit | 共享Controller、dto、vo；只依赖公共用例，执行输入校验及CloneFactory映射 |
| api.shared | 响应信封、异常HTTP适配、ID上限；requestId及错误事件经RequestContext端口获取，不直接引用过滤器 |
| service.port | 用例接口、独立Command/Query/Result、纯枚举及认证/请求/日志/来源读取支撑抽象；不引用Spring、JPA、Servlet、Entity或HTTP DTO/VO |
| service.structured | 事务脚本、范围/有效授权/可授予/根锁/K及首次初始化；直接使用Infrastructure中的JPA仓储，保持结构化实现 |
| infrastructure.persistence.entity/repository | 明确Entity和JpaRepository命名、映射、查询及数据库约束；持久化数据局部校验不作为DDD聚合 |
| infrastructure.security/web/logging/excel | BCrypt/Bearer、MVC预检和参数绑定、请求支撑、日志及Excel读取适配 |
| config / bootstrap | Spring上下文/秘密读取/扫描及HTTP、离线导入、首次授权入口 |

不创建service.ddd目录，不启用假策略开关，不把JPA Entity换名后宣称DDD。本轮结构化Service可以使用JPA Specification和仓储；未来DDD应用服务必须另行通过领域仓储端口，不能调用structured冒充领域实现。

## 2. 共享用例与基础设施

| 用例/支撑 | 实际接口 | 当前实现/适配 |
| --- | --- | --- |
| 登录、本人 | AuthenticationUseCase | StructuredAuthenticationService |
| 范围树、人员列表 | UserQueryUseCase | StructuredUserQueryService |
| 人员角色/停用/调动 | UserManagementUseCase | StructuredUserManagementService |
| 角色定义、选权 | RoleUseCase | StructuredRoleService |
| 资源与API映射 | ResourceUseCase | StructuredResourceService |
| 部门维护 / 范围总览 | DepartmentUseCase / OverviewUseCase | StructuredDepartmentService / StructuredOverviewService |
| 最小日志查询 | AuditLogUseCase | StructuredAuditLogService |
| 全局范围与操作预检 | ManagementAccessUseCase | StructuredGlobalManagementAccess |
| 新项目账号导入 / 首次五角色 | ImportUseCase / AuthorizationBootstrapUseCase | StructuredImportService / StructuredInitialAuthorizationService |
| 最新API集合 / 根锁及K | ManagementAuthorization / MaintenanceProtection | StructuredEffectivePermissionService / StructuredMaintenanceGuard |
| 口令 / 身份索引 | PasswordHashing / IdentityTokenStore | BCryptPasswordHashing / BearerIdentityStore |
| 请求操作与追踪 / 事件记录 | RequestContext / AuditWriter | HttpRequestContext / JpaAuditWriter |
| 原始资料读取 | SourceWorkbookReader | WorkbookImportPlanner（复用ExcelXmlReader） |

Controller与预检/AOP不再引用具体StructuredService。写用例的事务/根锁/锁后重验及完整K顺序保留；身份索引只保存身份，不缓存权限；双入口、实际API OR、人员Permission子集、110部门/10001账号及五角色均沿现行需求。

## 3. 映射、校验与日志整改

以下保留阶段2整理来源；阶段4实际映射核对另见[映射清单](acceptance/stage4-mapping-audit.md)。公共契约中的DepartmentType仅声明取值，层级判断位于StructuredDepartmentService；LoginIdentifiers归infrastructure.excel，供离线源文件/目标数据预检使用，不从共享Excel读取器引用结构化实现。

- 将CoreResults/各Models及Views容器拆成独立文件，名称直接表达Command、Query、Result、DTO、VO。PermissionType移到纯用例值，API/端口不再依赖Entity内嵌枚举。
- Entity→Result的CloneFactory声明改由Entity端CopyTo维护，避免结果模型反向导入持久化类；同名字段仍用真实处理器。primitive的is前缀、关系ID、集合元素及派生值保留显式转换。
- 角色PATCH及API启停补copyNotNull；角色编码不在PATCH输入内，parentId缺省/显式null仍显式处理，不绕过树约束。原getter/setter/空构造样板交Lombok，保留只读集合和必要数据校验。
- 人员查询q先在DTO trim后验长，部门筛选ID补安全整数上限；Service保留范围及数据库筛选职责。由行为失败先复现后修复，未改变OpenAPI既有规则。
- 鉴权日志allowed与后续400/404/409/500业务结果分开；401/403和范围拒绝仍记拒绝。统一层日志覆盖API/Service/Infrastructure，Servlet过滤器生命周期从方法AOP排除，避免代理破坏容器初始化；其安全事件仍由原过滤器采集。

## 4. 按需求组织测试

| 需求组/目录 | 测试类/契约 | 旧任务来源 |
| --- | --- | --- |
| r01 | R01ModelRulesTest、R01JpaConstraintTest | T02核心模型/真实约束 |
| r03、r04、r06 | R03StructuredImportJpaTest、R03WorkbookImportPlannerTest、R03ExcelSourceTest、R03ExcelImportTest、R04UserIdentifierTest、R06InitialRoleRulesTest | T03/T05原件、账号与首次分类 |
| r07 | R07AuthenticationContractSupport + R07StructuredAuthenticationContractTest | T05双入口/准入/查询/日志，覆盖R04/R07—R10/R16等 |
| r11 | R11UserManagementContractSupport + R11StructuredUserManagementContractTest | T11人员管理、子集/调动/并发保护 |
| r13 | R13RoleContractSupport + R13StructuredRoleContractTest | T09角色、选权/共享维护者 |
| r14 | R14ResourceContractSupport + R14StructuredResourceContractTest | T07资源树/API映射/并发 |
| r23 | R23PrototypeContractTest | 阶段4级别、目标保护、部门增删改名、聚合及摘要 |
| r18 | R18UserQueryValidationTest | DTO清洗顺序及安全ID回归 |
| r22 | R22Import/Resource/Role/UserDatabaseBoundaryTest | 各专库/profile守卫 |
| infrastructure | R03ExcelReaderBoundaryTest、R16RequestAuditBoundaryTest、R16ManagedAuditTest | 格式/隐私/日志切面职责 |
| architecture | R19R21StructuredBoundaryTest、R19CloneFactoryMappingTest、R19LegacyRemovalBoundaryTest | 分层、真实映射、旧物禁用 |
| shared | TestProjectPaths | 统一定位仓库根、后端和原Excel |

HTTP测试拆成可复用的抽象ContractSupport与当前Structured启动类，保留所有原断言、真实MySQL、实际HTTP、故障注入和确定并发同步点。ContractSupport不依赖具体StructuredService；当前启动类只装配结构化实现，不创建DDD测试类。共享端口/基础设施依赖方向由架构测试保护；当前无DDD的阶段断言在阶段5需替换为互斥装配测试，详见[DDD边界](ddd-boundary.md)。测试名/DisplayName带需求编号，旧Txx只用于历史追溯；profile和库名保留以保持既有安全约束。

R19R21StructuredBoundaryTest使用仅测试依赖ArchUnit1.4.1导入生产字节码，检查API、公共端口、基础设施及结构化层依赖；Controller还检查真实非静态字段必须为service.port下用例接口，注释中的UseCase不参与判定。R18ErrorSchemaTest归r18，路由404/405的真实HTTP断言归共享r07契约；前端增量归tests/r17-navigation.test.mjs及r17-workspaces.test.mjs，不放入生产源码或复制另一套页面。

本地运行验收和3项环境隔离单测移至backend/scripts，测试辅助代码不混入生产src/main。SQL位于deploy；阶段4已直接更新首次结构以加入角色级别、组织类型和墓碑，未搬迁旧数据库。实际结果见[测试说明](testing-acceptance.md)及[本阶段验收](acceptance/structured-organization.md)，不覆盖[T13历史事实](acceptance/T13.md)。

## 5. 本轮交付与后续边界

当前只有结构化后端；正式Vue七工作区及对应29项HTTP契约已更新，运行/复现见[组员复现指南](reproduction.md)。根Dockerfile、.dockerignore、Stack与旧容器测试脚本按用户要求暂不适配，待新服务器方案统一整理，不能将本地JAR验收写成部署成功。

阶段4在独立分支交付草稿PR，不自行合并或推进DDD/T14/T15。后续每次编码同步更新需求、设计、实际路径和当阶段结果；历史验收固定版本/时间，避免旧结论代表当前状态。

## 阶段4增量目录

api/department提供部门及总览Controller和DTO/VO，service/port/department定义共享用例、命令、结果和类型，service/structured/department实现组织维护及范围聚合。StructuredPersonnelAuthority集中处理角色级别和目标保护，不属于DDD领域模型。真实数据库与HTTP增量测试归r23/R23PrototypeContractTest；原r11/r13/r14安全回归保留并按新增字段和规则更新夹具。正式Vue归frontend/src/pages，通用弹窗、分页和完整集合编辑器归components，不复制prototype/model.js。
