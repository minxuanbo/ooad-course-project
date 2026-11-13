# 结构化代码与测试归类

2026-11-13；本分支codex/structured-code-organization，起点d1438d7（文档PR #29已合入）。本阶段整理当前RBAC结构化代码及本地测试，用户后续另行安排DDD和新前端；服务器/部署方案等待新增服务器确定，不在本轮实施。

## 1. 项目及生产代码边界

后端Maven项目从根pom.xml/src移到backend；frontend仍是生产Vue入口，prototype仍是参考交互，docs维护正式规范。全库只保留一份后端源码，不复制structured/ddd两套工程。实际目录与本地命令见[backend/README](../backend/README.md)。

| 目录 | 责任与依赖 |
| --- | --- |
| api.auth/user/role/resource/audit | 共享Controller、dto、vo；只依赖公共用例，执行输入校验及CloneFactory映射 |
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
| 最小日志查询 | AuditLogUseCase | StructuredAuditLogService |
| 全局范围与操作预检 | ManagementAccessUseCase | StructuredGlobalManagementAccess |
| 新项目账号导入 / 首次五角色 | ImportUseCase / AuthorizationBootstrapUseCase | StructuredImportService / StructuredInitialAuthorizationService |
| 最新API集合 / 根锁及K | ManagementAuthorization / MaintenanceProtection | StructuredEffectivePermissionService / StructuredMaintenanceGuard |
| 口令 / 身份索引 | PasswordHashing / IdentityTokenStore | BCryptPasswordHashing / BearerIdentityStore |
| 请求操作与追踪 / 事件记录 | RequestContext / AuditWriter | HttpRequestContext / JpaAuditWriter |
| 原始资料读取 | SourceWorkbookReader | WorkbookImportPlanner（复用ExcelXmlReader） |

Controller与预检/AOP不再引用具体StructuredService。写用例的事务/根锁/锁后重验及完整K顺序保留；身份索引只保存身份，不缓存权限；双入口、实际API OR、人员Permission子集、110部门/10001账号及五角色均沿现行需求。

## 3. 映射、校验与日志整改

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
| r18 | R18UserQueryValidationTest | DTO清洗顺序及安全ID回归 |
| r22 | R22Import/Resource/Role/UserDatabaseBoundaryTest | 各专库/profile守卫 |
| infrastructure | R03ExcelReaderBoundaryTest、R16RequestAuditBoundaryTest、R16ManagedAuditTest | 格式/隐私/日志切面职责 |
| architecture | R19R21StructuredBoundaryTest、R19CloneFactoryMappingTest、R19LegacyRemovalBoundaryTest | 分层、真实映射、旧物禁用 |
| shared | TestProjectPaths | 统一定位仓库根、后端和原Excel |

HTTP测试拆成可复用的抽象ContractSupport与当前Structured启动类，保留所有原断言、真实MySQL、实际HTTP、故障注入和确定并发同步点。ContractSupport不依赖具体StructuredService；当前启动类只装配结构化实现，不创建DDD测试类。测试名/DisplayName带需求编号，旧Txx只用于历史追溯；profile和库名保留以保持既有安全约束。

本地运行验收和3项环境隔离单测移至backend/scripts，测试辅助代码不混入生产src/main。SQL仍保留deploy原件，未修改表结构或搬迁旧数据库。实际结果见[测试说明](testing-acceptance.md)及[本阶段验收](acceptance/structured-organization.md)，不覆盖[T13历史事实](acceptance/T13.md)。

## 5. 本轮交付与后续边界

本地接口和数据库契约保留，当前只有结构化实现；前端未重做。根Dockerfile、.dockerignore、Stack与旧容器测试脚本按用户要求暂不适配，待新服务器方案统一整理，不能将本地JAR验收写成部署成功。

代码整理在独立分支交付草稿PR，不自行推进新原型/DDD或T14/T15。后续每次编码同步更新需求、设计、实际路径和当阶段结果；历史验收固定版本/时间，避免旧结论代表当前状态。
