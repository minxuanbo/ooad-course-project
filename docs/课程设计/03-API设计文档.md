# 市政公司统一权限管理系统——API 设计文档

版本：6.0；日期：2026-10-27。

## 文档目的与约定

本文规定客户端与后端之间的网络契约，包含认证、授权、34 项方法与路径组合、请求边界、成功响应、错误码及全部响应模型。它是[详细设计文档](05-详细设计文档.adoc)中接口与数据传递设计的专题表达，二者具有相同语义，不定义不同版本的 API。

接口示例使用虚构标识；客户端应先查询有效实体 ID。本文不包含具体服务地址、部署状态、调试结果或覆盖率成绩。组织与授权业务规则见[需求分析](01-需求分析.md)，前端反馈见[界面设计](02-界面设计文档.md)，持久化约束见[数据库设计](04-数据库设计文档.adoc)。

## 接口契约

### 公共协议

统一路径前缀为 `/api`。请求与响应使用 UTF-8 JSON；携带请求体时使用 `Content-Type: application/json`。路径 ID 为有符号 64 位整数，客户端以服务器返回的正 ID 发起业务操作，不推测连续编号。
接口不提供 URL 版本段、ETag、If-Match、幂等键或 JSON Patch 指令数组。PATCH 使用普通字段对象，仅支持规定字段。
所有正常操作返回 HTTP 200，包括新增、退出和无返回值的写操作；不使用 201 或 204。认证、参数、权限与资源错误按错误码表处理。

```json
{"code":"OK","message":"成功","data":null,"requestId":"00000000-0000-4000-8000-000000000001"}
```

`requestId` 是服务端生成的 UUID，与 `X-Request-Id` 响应头相同。客户端的 `/authorize` 请求体 `requestId` 是独立业务跟踪号，回显在 `data.requestId` 并写该鉴权事件；不得与外层服务端请求号混为一谈。
认证与应用准入事件使用服务端请求号。通用鉴权事件的 HTTP 方法／路径可为空、状态可为 null；不能由这些字段推断所有事件均来自同一条采集路径。
GET 无请求体。除登录和可无令牌的退出外，均要求 `Authorization: Bearer <token>`；Bearer 前缀大小写及空格按此格式匹配。
SQL 日期时间按 UTC 解释。日志 occurredAt 使用不带时区后缀的本地日期时间字符串（语义为 UTC），archivedAt 和交接时间可能使用空格分隔；客户端不得假设所有时间字段具有相同字符串外观。查询时间参数使用带 Z 的 ISO-8601，预计交接日期为 YYYY-MM-DD。
所有集合响应位于 data 内；空集合返回 []，无交接记录返回 handover=null；内部 DTO 中不适用于该查询投影的可选属性可返回 null。

### 认证与授权规则

登录使用用户名、工号或手机号及密码。密码采用 BCrypt；会话令牌只在登录响应返回，数据库保存 SHA-256 摘要。除登录、允许无令牌的退出外，接口要求 `Authorization: Bearer <token>`；会话有效期默认 12 小时。

角色决定业务功能权限和管理操作权限；每名员工的角色分配另绑定公司／部门范围。管理鉴权必须通过同一个 roleId 连接角色管理操作与员工范围，不能把不同角色的操作和范围交叉组合。`canManage` 只表示存在有效管理授权，不能代替具体操作检查。

角色目录定义五类角色：系统管理员、公司领导、部门经理、项目经理、普通员工。部长／副部长映射为部门经理；项目经理／项目副经理／项目总工程师映射为项目经理。岗位是初始化依据，之后由管理员显式修改；不另设审计员等角色。

| 范围 | 约束 | 覆盖目标 |
| --- | --- | --- |
| GLOBAL | 仅系统管理员角色；companyId、departmentId 均为空 | 全平台，包括无部门人员 |
| COMPANY | 非系统管理员角色；companyId 必填，departmentId 为空 | 所选公司全部部门及人员 |
| DEPARTMENT | 非系统管理员角色；公司、部门必填且归属一致 | 精确指定部门／项目部，不递归扩展 |

角色可绑定多个范围。没有范围时，该角色的业务功能权限仍生效，但不产生后台管理权。范围只约束本权限平台的管理操作，不代表外部业务系统内部数据权限。

| 权限码 | 管理操作 | 范围语义 |
| --- | --- | --- |
| user.read | 人员列表、筛选、工作台、交接详情 | 人员的当前部门归属 |
| user.roles | 给人员分配业务角色 | 目标人员的当前部门归属 |
| role.read | 查看共享角色及其权限 | 角色目录共享，关联人数按此权限范围过滤 |
| role.permissions | 配置共享角色的业务及管理权限 | 系统管理员 + GLOBAL |
| org.read | 组织查看 | 按授权范围返回公司及部门 |
| org.write | 组织维护 | 部门改名按范围；新增部门要求公司或平台范围；公司维护仅 GLOBAL |
| app.read | 系统目录查看 | 共享目录；不包含人员数据 |
| audit.read | 鉴权日志查看 | 系统管理员 + GLOBAL，查看全部日志 |
| handover.start | 发起离职交接 | 离职人、接收人和负责人均须在操作范围内 |
| handover.complete | 完成离职处置 | 离职人员在范围内 |
| management.grant | 配置人员角色与范围 | 仅 GLOBAL |

人员查询权不包含修改权；组织查看权不包含维护权；离职发起与完成可以分离授予。使用界面进行人员操作时，应同时授予相同范围的 user.read；使用组织维护界面时应同时授予 org.read。平台配置管理授权的界面还需全平台 user.read、org.read，以选取全部人员和组织。

局部管理员不能修改自己的业务角色，也不能调整任何持有管理授权人员的角色或为其办理离职。局部角色分配还要求目标已有角色及提交角色的全部权限均为操作者当前有效业务权限的子集；否则拒绝整次修改。全平台 user.roles 可处理这些目标，但不会因此获得管理授权配置权。

所有管理写事务先锁定 roles 中 code=系统管理员的固定角色行，再重新校验授权和范围。授权替换、组织维护、角色变更及离职处理共用此锁，避免撤权与写入并发越权。最后一名启用且拥有 GLOBAL management.grant 的人员不得被撤销该权限或完成离职。

### 接口清单

| 方法与路径 | 所需权限 | 返回／用途 |
| --- | --- | --- |
| POST /auth/login | 无 | token、user、expiresIn |
| POST /auth/logout | 可无令牌 | 撤销当前令牌，200，data=null |
| GET /me | 登录 | user、roles、permissionCount、canManage、managementGrants |
| GET /me/permissions?app= | 登录 | 当前有效业务权限列表 |
| GET /me/applications | 登录 | 有有效业务权限的应用 |
| POST /me/applications/{id}/enter | 登录及应用准入 | allowed、application、next；拒绝 403 并留痕 |
| GET /me/applications/{id}/modules | 应用准入 | 应用启用模块与权限计数 |
| GET /me/applications/{id}/modules/{moduleId}/features | 应用准入且模块归属正确 | 功能、说明、各操作 allowed |
| POST /authorize | 登录 | 对当前用户执行功能权限判断并记录日志 |
| GET /dashboard | user.read | 范围内人员／部门统计；角色／目录无查看权时对应数值 0；仅 GLOBAL audit.read 统计全部日志 |
| GET /users | user.read | 人员数组列表接口 |
| GET /users/page | user.read | items、total、page、size |
| GET /users/filters?company= | user.read | 范围内且有人员的公司／部门筛选项 |
| GET /users/{id}/roles | user.roles + 目标保护规则 | 共享角色选项及 assigned |
| PUT /users/{id}/roles | user.roles + 目标保护规则 | 替换目标业务角色并写变更记录 |
| GET /roles | role.read | 共享角色目录，userCount 受范围过滤 |
| GET /roles/{id}/permissions?q= | role.read | 共享权限选项及 assigned |
| PUT /roles/{id}/permissions | GLOBAL role.permissions | 替换共享角色权限 |
| GET /applications | app.read | 共享应用目录及权限计数 |
| GET /authorization-logs | GLOBAL audit.read | 游标分页与筛选，返回 items、nextCursor、hasMore、snapshotId |
| POST /authorization-logs/archive | GLOBAL audit.read | 按截止时间分批逻辑归档，返回 archived、hasMore、snapshotId |
| GET /users/{id}/handover | user.read + 目标范围 | 交接详情、角色、系统、最近 50 条变更 |
| POST /users/{id}/handover | handover.start + 目标保护规则 | 开始交接 |
| POST /users/{id}/handover/complete | handover.complete + 目标保护规则 | 完成撤权、停用、撤会话 |
| GET /management/permissions | GLOBAL role.permissions | 管理权限码与中文名称映射 |
| GET /roles/{id}/management-permissions | role.read | 角色管理操作权限码列表 |
| PUT /roles/{id}/management-permissions | GLOBAL role.permissions | 替换角色管理操作权限 |
| GET /users/{id}/role-assignments | GLOBAL management.grant | roles 选项及 scopes 范围 |
| PUT /users/{id}/role-assignments | GLOBAL management.grant | 原子替换员工角色和范围 |
| GET /organizations | org.read | companies、departments；部门附 writable |
| POST /organizations/companies | GLOBAL org.write | 新增公司 |
| PATCH /organizations/companies/{id} | GLOBAL org.write | 修改公司编码／名称 |
| POST /organizations/departments | COMPANY 或 GLOBAL org.write | 新增指定公司部门 |
| PATCH /organizations/departments/{id} | org.write + 部门范围 | 修改部门编码／名称 |

### 请求与响应契约

#### 登录、自身信息与业务鉴权

登录请求：`{"account":"账号","password":"密码"}`。账号或密码为 null、账号超过 100 字符或密码超过 200 字符返回 400；账号去除首尾空格后查询，空字符串按凭据不匹配处理；账号不存在、密码错误或停用账号返回 401/LOGIN_FAILED。用户名、工号、手机号在数据库统一命名空间中跨字段唯一；同人多个字段同值允许，不同人不允许占用同一值。

`GET /me` 中 managementGrants 为数组，每项含 id、roleId、roleName、permission、scope、companyId、departmentId、company、department；由当前角色权限和员工角色范围联表计算。无组织字段为 null。permissionCount 是原始角色关联权限数，不代表有效可操作数量；有效业务权限请用 `/me/permissions`。

`POST /authorize` 请求：`{"appCode":"系统码","permissionCode":"权限码","requestId":"跟踪号"}`。appCode/requestId 最多 191 字符，permissionCode 最多 512。返回 `allowed` 布尔值、permissionCode、requestId。没有权限时本接口仍为 200/allowed=false；这只是鉴权结果，不执行真实业务。

目录路径中的应用及模块 id 都由后端验证；不可利用另一系统模块 id 读取其功能。用户可进入应用，并不代表拥有该应用的全部操作。

#### 人员列表

`GET /users/page?q=&company=&departmentId=&page=0&size=20`。

- q 匹配姓名、登录名、电话、工号；company 是公司名称；departmentId 是部门 id。
- 筛选条件只能缩小授权范围，不能扩大范围；越界筛选返回空列表及 total=0。
- page 小于 0 时取 0；size 小于 1 时取 1，大于 100 时取 100；`/users` 默认 size=30。
- 每项含 id、employeeNo、username、name、phone、company、departmentId、department、position、status、employmentStatus、roles、managementActions。
- managementActions 给出目标可尝试的 user.roles／handover.start／handover.complete。角色权限子集、离职状态等业务前置条件仍在写入时检查。
- 无部门人员只有 GLOBAL user.read 可以查看；分页 total 与 items 使用相同范围谓词。

#### 业务角色与权限替换

请求体统一为 `{"ids":[1,2]}`；null 列表为 400；空数组表示撤销全部。去重处理，不存在的角色／权限 id 不建立关联；界面应仅提交服务器返回选项。所有授权替换均写 authorization_changes。角色权限写入的目标角色 ID 必须来自角色目录；非法目标在实际建立关联时由外键约束拒绝，不约定所有目标错误都返回 404。

角色替换采用增量更新：保留角色的范围不变；撤销角色级联删除其范围；新角色不会自动获得管理范围。范围由 role-assignments 专用接口调整。禁止已完成离职的人员重新分配角色；局部管理员对子集校验不通过的目标不能部分修改。

#### 角色管理操作权限

`PUT /roles/{id}/management-permissions` 请求 `{"permissions":["user.read","user.roles","org.read"]}`。空数组撤销此角色的管理操作，影响所有角色持有者；员工范围保持不变。未知权限或无效角色返回 400。
`audit.read`、`role.permissions`、`management.grant` 仅允许配置给系统管理员角色，运行时还必须有 GLOBAL 范围。配置后必须保留至少一名有效平台授权管理员。

#### 员工角色与范围

`GET /users/{id}/role-assignments` 返回：roles 中包含 id、code、name、enabled、assigned、managementPermissionCount；scopes 每项包含 roleId、scope、companyId、departmentId。

```json
{
  "roleIds": [3, 5],
  "scopes": [
    {"roleId":3,"scope":"COMPANY","companyId":10,"departmentId":null},
    {"roleId":3,"scope":"DEPARTMENT","companyId":11,"departmentId":20}
  ]
}
```

示例 id 仅说明结构，应使用接口返回的真实选项。两数组必填，范围最多 200 条；范围必须绑定 roleIds 中的启用角色。公司／部门必须启用且归属正确，空值／多余组织字段必须符合范围表。相同范围去重，目标必须 ACTIVE。
保存会原子替换角色与范围并写 SET_ROLE_ASSIGNMENTS；空数组可撤销全部，但不能撤销最后平台授权管理员。任何失败整体回滚。仅设置角色并不会自动授予管理范围。

例如某人持有角色 A 的人员查询权限并绑定甲公司、角色 B 的人员修改权限并绑定乙公司，不能用 B 的修改权操作甲公司人员。
`/me.managementGrants` 字段仅作为派生有效权限供前端消费。

#### 组织维护

新增公司（POST）：`{"code":"COMPANY_A","name":"A 公司"}`。
新增部门：`{"code":"DEPT_A","name":"工程部","companyId":10}`。
修改公司／部门（PATCH）允许 `{"code":"DEPT_A","name":"新名称"}` 或只提交其中一项，不得提交 companyId 或 parentId。编码 1–100 字符，名称 1–191 字符，不允许空白值。

组织只包含公司及其部门，不设部门层级。组织维护不提供删除、移动或停用接口。拥有 org.write 的部门范围管理员可改名其已有部门，但不能新增部门；公司／平台管理员可在有权公司新增部门。parentId 非空属于无效输入；授权计算始终按精确部门。部门负责人字段不在此维护接口中调整。

#### 离职与审计

发起：`{"receiverNo":"工号","ownerNo":"工号","expectedDate":"2026-11-01","reason":"交接说明","oaReference":"可选单号"}`。

接收人和负责人必须启用、未进入交接且不是离职本人，同时在 handover.start 的授权范围内。不能为当前登录账号办理离职。日期为 YYYY-MM-DD；reason 最长 1000，OA 单号最长 200。开始交接保留授权，不给接收人自动复制角色。

完成：`{"note":"完成说明","confirmed":"true"}`。note 最长 1000。事务内删除用户角色并级联删除角色范围、停用用户、撤销会话、更新交接状态并记录变更；已完成交接重复请求直接返回成功，权限仍需校验。

交接详情中的接收人／负责人如果已不在查看者 user.read 范围内，返回“范围外人员”，工号与 id 为 null。管理授权配置明细仅对 GLOBAL management.grant 查看者开放，其他人员的交接历史过滤该变更类型。

鉴权日志仅系统管理员在 GLOBAL audit.read 生效时可读，返回全部范围日志，包括无法关联当前用户的历史记录。公司领导、部门经理、项目经理不能查看该全局日志。

### 日志查询与归档

GET `/authorization-logs` 的可选参数：

| 参数 | 类型与语义 |
| --- | --- |
| userId | 正整数，精确匹配内部用户 ID；未识别身份的记录为空 ID |
| username | 最长 100 字符，精确匹配记录中的账号快照；登录事件保留尝试的登录标识 |
| appCode | 最长 191 字符，精确匹配系统编码；认证事件为空系统码 |
| allowed | true 或 false；成功／允许与失败／拒绝 |
| from / to | UTC ISO-8601，含 from、不含 to；from 必须早于 to |
| event | 最长 32 字符；AUTHORIZATION、APPLICATION_ENTRY、LOGIN_SUCCESS、LOGIN_FAILED、UNAUTHENTICATED、REQUEST_DENIED |
| storage | ALL（默认）、ACTIVE、ARCHIVED |
| limit | 1–500，默认 100 |
| cursor | 后端返回的不透明游标；继续查询须保持所有筛选条件及 limit 不变 |

```json
{"code":"OK","message":"成功","data":{"items":[],"nextCursor":null,"hasMore":false,"snapshotId":0},"requestId":"服务端生成的 UUID"}
```

items 每项包含 id、occurredAt、userId、username、appCode、permissionCode、allowed、requestId、reason、eventType、httpMethod、requestPath、httpStatus、archivedAt、archivedBy。
时间按 UTC 解释；归档字段为空表示未归档。按 id 倒序，首次记录最大 id 作为查询上界，后续按小于上一页末 id 查询，避免新日志插入导致重复；新记录在重新查询时出现。
这不是数据库事务快照：若同时归档，ACTIVE/ARCHIVED 集合会变化，应重新查询；ALL 的事件集合保持可追溯。不提供按页码跳转。

POST `/authorization-logs/archive` 请求 `{"before":"2026-10-01T00:00:00Z","batchSize":1000}`。
截止时间必填且不能晚于当前时间；batchSize 1–5000，默认 1000。仅归档 occurred_at < before 且尚未归档的记录；一次固定最大 id 后按 id 升序更新至批量上限。
返回 `{"archived":1000,"hasMore":true,"snapshotId":12345}`，hasMore=true 时可用同一条件继续。每批事务写 archived_at/archived_by 和 ARCHIVE_LOGS 变更审计；重复请求不会重复归档已完成记录。
此操作针对截止时间前的全部记录，不受列表筛选影响。逻辑归档不删除、不搬离数据库、不减少表占用空间；本设计不提供自动清理或外部冷存储。

认证审计在请求结束时记录登录成功、登录失败及 API 的 401/403；未提供令牌、过期、撤销、停用账号均留痕。无法识别身份时 userId 为空；JSON 无法解析时账号也可为空。
不得记录密码、令牌、请求体或查询字符串。业务鉴权和应用准入记录操作结果；拒绝的应用准入还可能产生一条 REQUEST_DENIED 请求记录，通过请求标识关联。
不存在的系统返回 404，属于资源错误，不伪装成鉴权判定。日志写入使用独立事务，使业务回滚不撤销认证失败留痕；数据库本身不可用时不能保证持久化成功。

### 统一响应与业务错误码

```json
{"code":"MANAGEMENT_SCOPE_DENIED","message":"目标不在允许的管理范围内","data":null,"requestId":"服务端生成的 UUID"}
```

客户端按 code 判断业务类型，message 用于显示，不依赖中文字符串判断。主要错误码：

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | INVALID_ARGUMENT / INVALID_CURSOR / INVALID_ORGANIZATION_PATCH | 参数、格式或游标不合法 |
| 400 | ROLE_UNAVAILABLE / USER_INACTIVE / USER_DEPARTED | 角色、用户状态不允许操作 |
| 400 | SCOPE_ROLE_REQUIRED / SCOPE_ROLE_MISMATCH / SCOPE_TYPE_INVALID / SCOPE_ORGANIZATION_INVALID | 范围与角色或组织不匹配 |
| 400 | COMPANY_UNAVAILABLE / DEPARTMENT_UNAVAILABLE / COMPANY_NOT_FOUND | 组织不存在、停用或不属于指定公司 |
| 400 | LAST_PLATFORM_ADMIN / PLATFORM_ROLE_REQUIRED | 保护最后平台管理员或平台专属角色 |
| 400 | HANDOVER_EXISTS / HANDOVER_NOT_STARTED / SELF_HANDOVER_DENIED | 离职流程前置条件不成立 |
| 401 | AUTHENTICATION_REQUIRED / SESSION_INVALID / LOGIN_FAILED | 缺认证、会话失效或登录失败 |
| 403 | PERMISSION_DENIED / MANAGEMENT_SCOPE_DENIED / APPLICATION_ACCESS_DENIED | 缺操作权、超出管理范围或无应用准入权 |
| 404 | USER_NOT_FOUND / APPLICATION_NOT_FOUND / MODULE_NOT_FOUND / ENDPOINT_NOT_FOUND | 资源或接口不存在 |
| 405 | METHOD_NOT_ALLOWED | HTTP 方法错误 |
| 409 | DATA_CONFLICT | 唯一性或关联完整性冲突 |
| 500 | INTERNAL_ERROR | 未预期的内部错误，不暴露堆栈或 SQL |

局部管理员访问范围外或不存在的人员统一拒绝，避免用返回差异探测。GLOBAL 查询角色分配时，即使目标不存在也返回角色选项和空范围；写入必须校验目标状态。

权限在每次请求中读取；撤权提交后新查询立即生效。Vue 定时和窗口聚焦刷新 `/me`，管理授权集合变化时卸载旧视图、重取范围内数据；无当前页面权限时回到我的应用。已经通过鉴权的外部在途业务操作不保证被中断。

授权编辑不设置版本冲突提示；多个合法管理员顺序保存采用最后一次提交。所有管理写入串行化是小规模管理系统的串行一致性选择；规模扩展须评估锁粒度与竞争。

#### PUT 与 PATCH 的字段语义

角色、权限和角色范围使用 PUT 完整替换集合。公司、部门使用 PATCH 局部更新，Content-Type 为 application/json。

例如 `PATCH /organizations/departments/12` 的请求 `{"name":"工程部"}` 只修改名称，编码、所属公司与负责人不变；`{"code":"DEPT_12"}` 只修改编码。
code/name 至少提供一个；省略表示保留，显式 null、空白、code 超过 100 字符、name 超过 191 字符，以及 companyId/parentId/enabled 等其他字段均返回 HTTP 400、code=INVALID_ORGANIZATION_PATCH。重复同一合法 PATCH 保持相同资源状态。成功响应 data=null；组织资源不接受 PUT，返回 HTTP 405。

### 接口与请求／响应模型对应

下表为所有 34 项方法与路径组合的输入、成功 data 类型及主要业务失败状态。共用错误还包括参数绑定 400、错误方法 405、不可预期异常 500；未列出的数据库完整性冲突由统一处理器映射为 409。GET 无请求体，表中查询参数通过 URL 编码传入。
除鉴权 allowed=false 外，操作拒绝不能以 HTTP 200 伪装为成功。列表查询为空是合法结果；资源是否存在的判定按各接口定义，不将全部缺失 ID 统一描述为 404。

| 方法与路径 | 输入模型 | 成功 data 类型 | 主要失败状态／特殊规则 |
| --- | --- | --- | --- |
| POST /auth/login | Requests.Login | Responses.Login | 400 / 401 |
| POST /auth/logout | 无 | null | 无令牌或未知令牌亦成功 |
| GET /me | 无 | Responses.Me | 401 |
| GET /me/permissions | app 查询参数 | Responses.Permission[] | 401 |
| GET /me/applications | 无 | Responses.Application[] | 401 |
| POST /me/applications/{id}/enter | 无 | Responses.Entry | 401 / 403 / 404 |
| GET /me/applications/{id}/modules | 无 | Responses.Modules | 401 / 403 / 404 |
| GET /me/applications/{id}/modules/{moduleId}/features | 无 | Responses.Features | 401 / 403 / 404 |
| POST /authorize | Requests.Check | Responses.Decision | 400 / 401；业务拒绝为 200 且 allowed=false |
| GET /dashboard | 无 | Responses.Dashboard | 401 / 403 |
| GET /users | 人员查询参数 | Responses.User[] | 400 / 401 / 403 |
| GET /users/page | 人员查询参数 | Responses.UserPage | 400 / 401 / 403 |
| GET /users/filters | company 查询参数 | Responses.Filters | 400 / 401 / 403 |
| GET /users/{id}/roles | 无 | Responses.Role[] | 401 / 403 |
| PUT /users/{id}/roles | Requests.Ids | null | 400 / 401 / 403 / 404 / 409 |
| GET /roles | 无 | Responses.Role[] | 401 / 403 |
| GET /roles/{id}/permissions | q 查询参数 | Responses.Permission[] | 401 / 403 |
| PUT /roles/{id}/permissions | Requests.Ids | null | 400 / 401 / 403 / 409 |
| GET /applications | 无 | Responses.Application[] | 401 / 403 |
| GET /authorization-logs | AuditDtos.Query 查询参数 | AuditDtos.Page | 400 / 401 / 403 |
| POST /authorization-logs/archive | AuditDtos.Archive | AuditDtos.ArchiveResult | 400 / 401 / 403 |
| GET /users/{id}/handover | 无 | Responses.Handover | 401 / 403 / 404 |
| POST /users/{id}/handover | Requests.Handover | null | 400 / 401 / 403 / 404 / 409 |
| POST /users/{id}/handover/complete | Requests.Completion | null | 400 / 401 / 403 |
| GET /management/permissions | 无 | object&lt;string,string&gt;：权限码→名称 | 401 / 403 |
| GET /roles/{id}/management-permissions | 无 | string[] | 401 / 403 |
| PUT /roles/{id}/management-permissions | Requests.Permissions | null | 400 / 401 / 403 / 409 |
| GET /users/{id}/role-assignments | 无 | Responses.Assignments | 401 / 403 |
| PUT /users/{id}/role-assignments | Requests.Assignments | null | 400 / 401 / 403 / 409 |
| GET /organizations | 无 | Responses.Organizations | 401 / 403 |
| POST /organizations/companies | Requests.Organization | null | 400 / 401 / 403 / 409 |
| PATCH /organizations/companies/{id} | Requests.OrganizationPatch | null | 400（含 COMPANY_NOT_FOUND）/ 401 / 403 / 409 |
| POST /organizations/departments | Requests.Organization | null | 400 / 401 / 403 / 409 |
| PATCH /organizations/departments/{id} | Requests.OrganizationPatch | null | 400 / 401 / 403（含范围外或不存在的部门）/ 409 |

### 响应示例

登录成功的令牌为示意值，不可用于真实请求；expiresIn 的单位是秒。

```json
{"code":"OK","message":"成功","data":{"token":"<opaque-token>","user":{"id":12,"username":"E000012","name":"示例员工","status":"ACTIVE"},"expiresIn":43200},"requestId":"00000000-0000-4000-8000-000000000001"}
```

人员筛选无结果时仍返回归一化后的页码及大小。

```json
{"code":"OK","message":"成功","data":{"items":[],"total":0,"page":0,"size":20},"requestId":"00000000-0000-4000-8000-000000000002"}
```

组织更新采用普通 JSON 字段对象；显式 null 不表示删除名称。

```http
PATCH /api/organizations/departments/12
Authorization: Bearer <opaque-token>
Content-Type: application/json

{"name":"工程管理部"}
```

```json
{"code":"OK","message":"成功","data":null,"requestId":"00000000-0000-4000-8000-000000000003"}
```

## 请求字段与输入边界

正文中的字段对象就是 JSON 请求体，不额外嵌套 DTO 名称。OrganizationPatch 在 JSON 中直接使用 code/name，不带 fields 外壳。请求体无法解析、数字参数无法绑定等返回 INVALID_ARGUMENT。

| 请求模型／使用接口 | 字段 | 输入规则 |
| --- | --- | --- |
| Login：POST /auth/login | account、password | 均为字符串且不能为 null；长度上限 100／200；account 去首尾空格，password 保留原字符串。空凭据不匹配时按登录失败处理。 |
| Check：POST /authorize | appCode、permissionCode、requestId | 字符串上限分别 191／512／191；缺系统或权限码返回 allowed=false；跟踪号可省略。 |
| Ids：两类业务集合 PUT | ids | 整数数组，必填；空数组撤销全部，重复 ID 去重；缺失的引用 ID 不创建关联。 |
| Permissions：角色管理操作 PUT | permissions | 字符串数组，必填；元素来自 11 项管理权限码；空数组须通过最后管理员保护。 |
| Assignments：员工角色范围 PUT | roleIds、scopes | 两个数组均必填；scopes 最多 200 条；roleIds 必须为启用角色。 |
| Scope：scopes 元素 | roleId、scope、companyId、departmentId | roleId 必须属于所选角色；按 GLOBAL／COMPANY／DEPARTMENT 校验组织字段；部门输入的公司只用于归属校验。 |
| Organization：公司 POST | code、name | 均必填且非空白，上限 100／191；公司新增不依赖组织父节点。 |
| Organization：部门 POST | code、name、companyId | 编码名称规则同上；companyId 必填且对应公司存在；parentId 非 null 拒绝，不提供部门层级。 |
| OrganizationPatch：组织 PATCH | code 和／或 name | 至少一项；每个出现字段不能为 null、空白或超长；其他字段一律拒绝。 |
| Handover：发起 POST | receiverNo、ownerNo、expectedDate、reason、oaReference | 前四项必填且非空白，长度上限 100／100／10／1000；日期可解析为 YYYY-MM-DD，不额外要求晚于当前日；OA 参考号可空，上限 200。 |
| Completion：完成 POST | note、confirmed | note 必填且非空白，上限 1000；confirmed 的协议值为字符串 "true"；已完成时在权限校验后直接成功。 |
| Archive：日志归档 POST | before、batchSize | before 为有效 UTC 截止时间且不晚于当前时刻；batchSize 省略取 1000，范围 1–5000。 |

### 查询参数及排序

| 接口 | 参数／默认值 | 排序与边界 |
| --- | --- | --- |
| /me/permissions | app 可省略 | 省略查询全部有效权限；显式空串按空应用码过滤。按应用码、权限码排序。 |
| /users 与 /users/page | q=""，company=""，departmentId 可省略，page=0；size 分别 30／20 | 按人员 id 升序；page/size 归一化。q 去首尾空格后 LIKE 匹配，% 和 _ 具有 SQL 模式字符语义；company 精确匹配名称。 |
| /users/filters | company="" | 公司名升序；部门名、id 升序。只返回范围内有人员的组织选项。 |
| /roles/{id}/permissions | q="" | 按功能、模块、系统名称 LIKE 筛选；按应用 id、权限 id 升序。 |
| /authorization-logs | 查询字段见接口契约；limit 默认 100 | 按 id 降序，游标最多 4096 字符；event 按字符串精确过滤，不将未知事件码解释成权限。 |
| /me/applications/{id}/modules | 无查询参数 | 按模块 sort_order、id 排序；操作按动作 sort_order、code、权限 id 排序。 |

## 响应模型与字段字典

本节类型统一使用 JSON 类型：整数对应 Java int/long，布尔值对应 boolean；Integer/Long 等包装类型允许投影缺省为 null。String 和嵌套对象的空值规则结合业务约束解释。数组不与逗号分隔的展示文本混用。
Responses.Role 与 Responses.Permission 用于多个查询场景，未投影的可选字段为 null。例如 /me.roles 不包含目录计数，/me/permissions 不提供编辑选中态。assigned 是 0/1 数值，不是 JSON 布尔值；enabled 也是 0/1。
User.roles 是使用“、”分隔的角色名展示文本，不可据此还原角色 ID；编辑必须读取相应角色选项接口。身份响应不包含密码摘要。

### Responses.Identity

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| username | string / null | 登录名或记录时账号；审计事件可为尝试的登录标识 |
| name | string / null | 显示名称 |
| status | string / null | 账号状态 ACTIVE／DISABLED |

### Responses.Role

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| code | string / null | 业务编码 |
| name | string / null | 显示名称 |
| description | string / null | 说明文本 |
| enabled | integer / null | 启用标志 0／1 |
| userCount | integer / null | 授权范围内关联人数 |
| permissionCount | integer / null | 权限数量；具体统计口径由接口决定 |
| assigned | integer / null | 编辑选项是否选中，0／1 |
| managementPermissionCount | integer / null | 角色的管理操作数量 |

### Responses.Grant

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| roleId | integer | 角色 ID |
| roleName | string / null | 角色名称 |
| permission | string / null | 管理操作码 |
| scope | string / null | GLOBAL／COMPANY／DEPARTMENT |
| companyId | integer / null | 公司 ID；GLOBAL 为 null |
| departmentId | integer / null | 部门 ID；GLOBAL／COMPANY 为 null（人员 DTO 为所属部门） |
| company | string / null | 公司名称 |
| department | string / null | 部门名称 |

### Responses.User

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| employeeNo | string / null | 工号 |
| username | string / null | 登录名或记录时账号；审计事件可为尝试的登录标识 |
| name | string / null | 显示名称 |
| phone | string / null | 手机号，缺失时 null |
| company | string / null | 公司名称 |
| departmentId | integer / null | 部门 ID；GLOBAL／COMPANY 为 null（人员 DTO 为所属部门） |
| department | string / null | 部门名称 |
| position | string / null | 岗位文本 |
| status | string / null | 账号状态 ACTIVE／DISABLED |
| employmentStatus | string / null | EMPLOYED／HANDOVER／DEPARTED |
| roles | string / null | 角色集合；User.roles 特指展示文本 |
| managementActions | array&lt;String&gt; | 对该人员允许尝试的管理操作码数组 |

### Responses.Permission

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer / null | 实体主键 |
| appCode | string / null | 应用编码 |
| appName | string / null | 应用名称 |
| code | string / null | 业务编码 |
| module | string / null | 模块名称 |
| feature | string / null | 功能点名称 |
| action | string / null | 动作名称；变更审计中为动作码 |
| assigned | integer / null | 编辑选项是否选中，0／1 |

### Responses.Application

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| code | string / null | 业务编码 |
| name | string / null | 显示名称 |
| enabled | integer / null | 启用标志 0／1 |
| permissionCount | integer / null | 权限数量；具体统计口径由接口决定 |

### Responses.Module

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| name | string / null | 显示名称 |
| featureCount | integer | 启用功能点数量 |
| permissionCount | integer | 权限数量；具体统计口径由接口决定 |
| grantedPermissionCount | integer | 当前用户获授权的有效权限数量 |

### Responses.Operation

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| permissionId | integer | 权限 ID |
| permissionCode | string / null | 权限编码 |
| actionCode | string / null | 动作码 |
| label | string / null | 操作显示名称 |
| allowed | boolean | 允许／成功为 true，拒绝／失败为 false |

### Responses.Feature

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| name | string / null | 显示名称 |
| description | string / null | 说明文本 |
| operations | array&lt;Operation&gt; | 该功能点的操作数组 |

### Responses.Company

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| code | string / null | 业务编码 |
| name | string / null | 显示名称 |
| enabled | integer | 启用标志 0／1 |

### Responses.Department

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| code | string / null | 业务编码 |
| name | string / null | 显示名称 |
| companyId | integer | 公司 ID；GLOBAL 为 null |
| enabled | integer | 启用标志 0／1 |
| writable | boolean | 当前人员能否维护该部门 |

### Responses.CompanyOption

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| company | string / null | 公司名称 |

### Responses.DepartmentOption

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| name | string / null | 显示名称 |

### Responses.SystemName

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| name | string / null | 显示名称 |

### Responses.Change

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| occurred_at | string / null | UTC 事件时间 |
| operator_id | integer / null | 操作者 ID，可为空 |
| action | string / null | 动作名称；变更审计中为动作码 |
| detail | string / null | 变更明细，不含凭据 |

### Responses.HandoverRecord

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| user_id | integer / null | 离职主体 ID |
| receiver_id | integer / null | 接收人 ID；范围遮蔽时 null |
| owner_id | integer / null | 负责人 ID；范围遮蔽时 null |
| expected_date | string / null | 预计完成日期 |
| reason | string / null | 原因／判定说明 |
| oa_reference | string / null | 可选 OA 参考号，非外部审批结果 |
| state | string / null | HANDOVER／DEPARTED |
| started_by | integer / null | 发起者 ID |
| started_at | string / null | UTC 发起时间 |
| completed_by | integer / null | 完成者 ID，未完成为 null |
| completed_at | string / null | UTC 完成时间，未完成为 null |
| completion_note | string / null | 完成说明，未完成为 null |
| receiver_name | string / null | 接收人显示名或“范围外人员” |
| receiver_no | string / null | 接收人工号，范围遮蔽时 null |
| owner_name | string / null | 负责人显示名或“范围外人员” |
| owner_no | string / null | 负责人工号，范围遮蔽时 null |

### Responses.Login

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| token | string / null | 随机 Bearer 令牌，仅登录响应返回 |
| user | Identity | 当前用户或目标用户身份 |
| expiresIn | integer | 会话有效秒数，默认 43200 |

### Responses.Me

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| managementGrants | array&lt;Grant&gt; | 派生有效管理授权数组 |
| canManage | boolean | 是否至少有一条有效管理授权 |
| user | Identity | 当前用户或目标用户身份 |
| roles | array&lt;Role&gt; | 角色集合；User.roles 特指展示文本 |
| permissionCount | integer | 权限数量；具体统计口径由接口决定 |

### Responses.Decision

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| allowed | boolean | 允许／成功为 true，拒绝／失败为 false |
| permissionCode | string / null | 权限编码 |
| requestId | string / null | 该模型对应的跟踪号；与外层请求号区分 |

### Responses.Dashboard

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| users | integer | 范围内人员数量 |
| departments | integer | 部门集合；Dashboard 中为范围内部门数量 |
| applications | integer | 系统数量，缺 app.read 时为 0 |
| permissions | integer | 权限数量，缺 app.read 时为 0 |
| roles | integer | 角色总数，缺 role.read 时为 0 |
| authLogs | integer | 日志总数，仅 GLOBAL audit.read 可见，否则为 0 |

### Responses.UserPage

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| items | array&lt;User&gt; | 当前页结果 |
| total | integer | 匹配人员总数 |
| page | integer | 从 0 起的页码 |
| size | integer | 归一化后的人员页大小 |

### Responses.Filters

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| companies | array&lt;CompanyOption&gt; | 公司选项／公司实体数组 |
| departments | array&lt;DepartmentOption&gt; | 部门集合；Dashboard 中为范围内部门数量 |

### Responses.Entry

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| allowed | boolean | 允许／成功为 true，拒绝／失败为 false |
| application | Application | 准入目标应用 |
| next | string / null | 下一步名称，固定 modules |

### Responses.Modules

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| applicationId | integer | 应用 ID |
| modules | array&lt;Module&gt; | 启用模块数组 |

### Responses.Features

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| applicationId | integer | 应用 ID |
| moduleId | integer | 模块 ID |
| features | array&lt;Feature&gt; | 启用功能点数组 |

### Responses.Handover

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| user | Identity | 当前用户或目标用户身份 |
| handover | HandoverRecord | 交接记录，没有记录时 null |
| roles | array&lt;Role&gt; | 角色集合；User.roles 特指展示文本 |
| systems | array&lt;SystemName&gt; | 有效业务系统名称数组 |
| history | array&lt;Change&gt; | 最近最多 50 条可查看人员变更记录 |

### Responses.Assignments

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| roles | array&lt;Role&gt; | 角色集合；User.roles 特指展示文本 |
| scopes | array&lt;Requests.Scope&gt; | 员工角色范围数组 |

### Responses.Organizations

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| companies | array&lt;Company&gt; | 公司选项／公司实体数组 |
| departments | array&lt;Department&gt; | 部门集合；Dashboard 中为范围内部门数量 |

### AuditDtos.Entry

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| id | integer | 实体主键 |
| occurredAt | string / null | UTC 事件时间字符串 |
| userId | integer / null | 事件用户 ID，未识别时 null |
| username | string / null | 登录名或记录时账号；审计事件可为尝试的登录标识 |
| appCode | string / null | 应用编码 |
| permissionCode | string / null | 权限编码 |
| allowed | boolean | 允许／成功为 true，拒绝／失败为 false |
| requestId | string / null | 该模型对应的跟踪号；与外层请求号区分 |
| reason | string / null | 原因／判定说明 |
| eventType | string / null | 认证／准入／鉴权事件类型 |
| httpMethod | string / null | 请求方法，非请求型事件可为空 |
| requestPath | string / null | 请求路径，不含查询串 |
| httpStatus | integer / null | HTTP 状态，非请求型事件可为 null |
| archivedAt | string / null | UTC 归档时间，未归档为 null |
| archivedBy | integer / null | 归档操作者 ID，未归档为 null |

### AuditDtos.Page

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| items | array&lt;Entry&gt; | 当前页结果 |
| nextCursor | string / null | 下一页不透明游标，无后页为 null |
| hasMore | boolean | 是否存在后续记录 |
| snapshotId | integer | 本次查询／归档的日志 ID 上界 |

### AuditDtos.ArchiveResult

| 字段 | JSON 类型／元素模型 | 含义 |
| --- | --- | --- |
| archived | integer | 本批归档条数 |
| hasMore | boolean | 是否存在后续记录 |
| snapshotId | integer | 本次查询／归档的日志 ID 上界 |

## 契约验证准则

| 场景 | 预期结果 |
| --- | --- |
| 登录、会话过期、退出 | 登录凭据规则及令牌有效性成立；非登录请求 401 不当作业务许可 |
| 公司／部门 PATCH | 省略字段保持原值；null、空白、超长、未知字段拒绝；组织 PUT 返回 405 |
| 集合 PUT | 提交完整选择；重复 ID 去重；空数组表达撤销并接受保护校验 |
| 多角色与多范围 | 操作和范围由同一角色关联，不可拼接不同角色的能力 |
| 分页与筛选 | 人员参数归一化；日志游标绑定首屏条件和 limit，条件变化时拒绝旧游标 |
| 日志归档 | 截止时间半开、批次原子、不受列表筛选限制、事件保留可查询 |
| 错误与返回模型 | 成功及错误外层结构一致，HTTP 状态和 code 语义正确；不得返回凭据或 SQL 堆栈 |
| 响应投影 | 可空字段、0/1 标志、布尔值和数组按模型定义，不从展示文本反推关联 ID |

验证准则只定义期望行为，执行记录由独立测试材料承载。接口变更应同步维护此文档及详设，不以修订叙述代替最终契约。
