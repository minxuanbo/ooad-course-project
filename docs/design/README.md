# 结构化现状与DDD目标图集

本图集配合[DDD设计](../ddd-design.md)和[审核指南](../ddd-review-guide.md)。结构化图依据已合入main的源码；所有DDD图都是待实现设计，不宣称存在对应Java类。类图/顺序图采用用户2026-11-17提供新版UML skill；用户明确要求先设计再编码，故DDD图以设计契约为来源。包图按UML包依赖语义单独检查，不以该skill替代包图规范。

## 1. 包图

两套实现分别展开包边界；DDD目标中的蓝色实现箭头表示包内类型实现接口，不能理解为包本身是Java接口。

![结构化现状包图](package-structured-current.png)

[结构化SVG](package-structured-current.svg)

![DDD目标包图](package-ddd-design.png)

[DDD SVG](package-ddd-design.svg)

## 2. 模块类图

每张以目标Service为几何中心，只列本切片关键字段/操作；完整业务和其余依赖见DDD设计。实线空心箭头为字段关联并标role，虚线空心箭头为局部/类型依赖，虚线空心三角由实现指向接口。Repository方法签名表达领域返回值；日志明确用Gateway返回公共读模型，不能误认为领域Repository允许Result。

### 身份与准入（identity）

![身份与准入目标类图](class-ddd-identity.png)

[可编辑SVG](class-ddd-identity.svg)；目标Service：DddAuthenticationService。

### 人员角色、停用与调动（personnel）

![人员角色、停用与调动目标类图](class-ddd-personnel.png)

[可编辑SVG](class-ddd-personnel.svg)；目标Service：DddUserManagementService。

### 范围内人员查询（query）

![范围内人员查询目标类图](class-ddd-query.png)

[可编辑SVG](class-ddd-query.svg)；目标Service：DddUserQueryService。

### 共享角色及直接权限（role）

![共享角色及直接权限目标类图](class-ddd-role.png)

[可编辑SVG](class-ddd-role.svg)；目标Service：DddRoleService。

### 权限树及API映射（resource）

![权限树及API映射目标类图](class-ddd-resource.png)

[可编辑SVG](class-ddd-resource.svg)；目标Service：DddResourceService。

### 部门维护（department）

![部门维护目标类图](class-ddd-department.png)

[可编辑SVG](class-ddd-department.svg)；目标Service：DddDepartmentService。

### 范围总览（overview）

![范围总览目标类图](class-ddd-overview.png)

[可编辑SVG](class-ddd-overview.svg)；目标Service：DddOverviewService。

### 最小鉴权日志查询（audit）

![最小鉴权日志查询目标类图](class-ddd-audit.png)

[可编辑SVG](class-ddd-audit.svg)；目标Service：DddAuditLogService。

### 新项目组织账号导入（importing）

![新项目组织账号导入目标类图](class-ddd-importing.png)

[可编辑SVG](class-ddd-importing.svg)；目标Service：DddImportService。

### 首次批量授权（bootstrap）

![首次批量授权目标类图](class-ddd-bootstrap.png)

[可编辑SVG](class-ddd-bootstrap.svg)；目标Service：DddInitialAuthorizationService。

### 完整维护者保护（maintenance）

![完整维护者保护目标类图](class-ddd-maintenance.png)

[可编辑SVG](class-ddd-maintenance.svg)；目标Service：DddMaintenanceGuard。

### API资源启停（api-resource）

![API资源启停目标类图](class-ddd-api-resource.png)

[可编辑SVG](class-ddd-api-resource.svg)；目标Service：DddResourceService。

### RBAC应用系统目录（systems）

![RBAC应用系统目录目标类图](class-ddd-systems.png)

[可编辑SVG](class-ddd-systems.svg)；目标Service：DddResourceService。

### 逐请求有效授权（authorization）

![逐请求有效授权目标类图](class-ddd-authorization.png)

[可编辑SVG](class-ddd-authorization.svg)；目标Service：DddAuthorizationService。

### 实际API与共同根门禁（management-access）

![实际API与共同根门禁目标类图](class-ddd-management-access.png)

[可编辑SVG](class-ddd-management-access.svg)；目标Service：DddManagementAccess。

### 现有结构化身份校验静态参考

![现有结构化身份类图](current-auth-class.png)

[现有类图SVG](current-auth-class.svg)。此类图依据实际require代码，保留作静态边界参考；不再配套绘制完整共同身份流程顺序图。

## 3. 只含差异的顺序图

以下均截取成功路径差异。共同HTTP绑定/校验、Controller、DTO/VO、Bearer、AOP/日志、根锁取得、锁后重新鉴权、K检查及提交不画。第一条调用是外层方法进入该差异片段的边界标记，不新增同名公共方法；外层前后约束沿DDD设计第6、9章。赋值消息已表达返回，正常回复不重复；元类用于真实静态factory，独立调用各自有短执行条，循环框完整包住循环接收执行条。

这些图不是全面异常流程图。身份/API/范围拒绝、未知对象、冲突及事务回滚在正文和既有错误契约约定；实现中不得因成功片段未展开而省略异常。若后续补画领域特有异常，须从实际抛出对象仍激活的执行条直接返回obj，不经Service重复转发。

### 有效API计算

结构化来源：backend/src/main/java/com/municipal/rbaccore/service/structured/security/StructuredEffectivePermissionService.java；DDD为目标设计。只比较仓储、领域行为及策略，未展示的共同链路不取消。

![有效API计算Structured现状差异](sequence-authorization-structured.png)

[Structured现状差异 SVG](sequence-authorization-structured.svg)

![有效API计算DDD目标差异](sequence-authorization-ddd.png)

[DDD目标差异 SVG](sequence-authorization-ddd.svg)

### 人员角色整体替换

结构化来源：backend/src/main/java/com/municipal/rbaccore/service/structured/user/StructuredUserManagementService.java；DDD为目标设计。只比较仓储、领域行为及策略，未展示的共同链路不取消。

![人员角色整体替换Structured现状差异](sequence-personnel-structured.png)

[Structured现状差异 SVG](sequence-personnel-structured.svg)

![人员角色整体替换DDD目标差异](sequence-personnel-ddd.png)

[DDD目标差异 SVG](sequence-personnel-ddd.svg)

### 角色权限整体替换

结构化来源：backend/src/main/java/com/municipal/rbaccore/service/structured/role/StructuredRoleService.java；DDD为目标设计。只比较仓储、领域行为及策略，未展示的共同链路不取消。

![角色权限整体替换Structured现状差异](sequence-role-permissions-structured.png)

[Structured现状差异 SVG](sequence-role-permissions-structured.svg)

![角色权限整体替换DDD目标差异](sequence-role-permissions-ddd.png)

[DDD目标差异 SVG](sequence-role-permissions-ddd.svg)

### 权限树修改

结构化来源：backend/src/main/java/com/municipal/rbaccore/service/structured/resource/StructuredResourceService.java；DDD为目标设计。只比较仓储、领域行为及策略，未展示的共同链路不取消。

![权限树修改Structured现状差异](sequence-permission-tree-structured.png)

[Structured现状差异 SVG](sequence-permission-tree-structured.svg)

![权限树修改DDD目标差异](sequence-permission-tree-ddd.png)

[DDD目标差异 SVG](sequence-permission-tree-ddd.svg)

### 新增合法下级部门

结构化来源：backend/src/main/java/com/municipal/rbaccore/service/structured/department/StructuredDepartmentService.java；DDD为目标设计。只比较仓储、领域行为及策略，未展示的共同链路不取消。

![新增合法下级部门Structured现状差异](sequence-department-create-structured.png)

[Structured现状差异 SVG](sequence-department-create-structured.svg)

![新增合法下级部门DDD目标差异](sequence-department-create-ddd.png)

[DDD目标差异 SVG](sequence-department-create-ddd.svg)

### 范围内人员分页

结构化来源：backend/src/main/java/com/municipal/rbaccore/service/structured/user/StructuredUserQueryService.java；DDD为目标设计。只比较仓储、领域行为及策略，未展示的共同链路不取消。

![范围内人员分页Structured现状差异](sequence-user-query-structured.png)

[Structured现状差异 SVG](sequence-user-query-structured.svg)

![范围内人员分页DDD目标差异](sequence-user-query-ddd.png)

[DDD目标差异 SVG](sequence-user-query-ddd.svg)

## 4. 覆盖与省略矩阵

| 范围 | 静态设计/类图 | 顺序差异与省略理由 |
| --- | --- | --- |
| 认证/本人4操作 | identity及maintenance | authorization对比SQL授权链和领域事实策略；工号口令/Bearer公共技术不重复画 |
| 部门树/人员分页2操作 | query | user-query；树范围、批量摘要和空页规则见正文 |
| 人员管理5操作 | personnel | personnel代表整组授权；停用/调动复用相同写事务边界，领域行为见类图及正文，不画重复锁/K链 |
| 角色5操作 | role | role-permissions展示集合替换；创建/改名/启停的factory及不变量见静态设计 |
| 资源7操作 | resource/api-resource/systems | permission-tree展示JPA局部保护与领域策略；API启停/映射同事务约束不重复整链 |
| 部门4操作、总览1操作 | department/overview | department-create展示层级规则和factory；改名/空删及聚合职责见正文 |
| 日志1操作 | audit | 共同准入不画，复杂技术查询经Gateway；游标快照协议保持 |
| 两类离线命令与横切支撑 | importing/bootstrap/maintenance | 来源、原子发布、映射和K职责明确；不把共用Excel/密码/锁/日志技术流程重复画入顺序图 |

HTTP总计29操作；初始化为离线命令，不混入HTTP计数。新增图件共29组（包2、类15、差异顺序12），每组同源SVG/PNG；另保留现有结构化类图1组。没有全套公共登录/HTTP/技术日志顺序图。

## 5. 编辑、生成和检查

设计数据源为[ddd-diagrams.json](ddd-diagrams.json)，唯一生成脚本为[scripts/build-ddd-design.mjs](../../scripts/build-ddd-design.mjs)，复用[scripts/uml-svg.mjs](../../scripts/uml-svg.mjs)。它们只生成文档，不进入后端或正式前端依赖。使用Node和sharp；可用UML_SHARP_MODULE指向仓库外已安装sharp，或使用含sharp的工具运行时。不要将sharp加到生产frontend/package.json。

```powershell
node scripts/build-ddd-design.mjs
# 仅需要重新核对现有结构化身份类图时：
node scripts/build-current-uml.mjs
```

旧脚本现只生成current-auth-class，不再产生完整身份顺序图。新脚本校验设计状态、结构化方法锚点、框无重叠/边不穿第三方框、共同步骤禁入、调用与执行条顶边、循环接收执行条完整性和文字宽度；生成清单见[ddd-generated-manifest.json](ddd-generated-manifest.json)。自动检查不等于视觉或业务语义通过，交付前必须实际打开PNG，见[本轮核对](../acceptance/ddd-design.md)。
