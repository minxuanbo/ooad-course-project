# API 设计文档

版本 5.0 · 2026-10-27。设计依据：现有角色的业务／管理权限 + 员工角色管理范围。本文规定接口契约，不记录部署状态。所有路径以 `/api` 为前缀，JSON 使用 UTF-8。所有响应统一为 `{code,message,data,requestId}`；以下接口表及字段描述均指 data 的内容。成功 code=OK，错误 data=null；集合放在 data，写操作无返回值时 data=null，HTTP 状态仍表达成功或错误。requestId 与 X-Request-Id 响应头相同，由服务端生成。

## 1. 认证与授权规则

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

## 2. 接口清单

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

## 3. 请求与响应契约

### 登录、自身信息与业务鉴权

登录请求：`{"account":"账号","password":"密码"}`。账号或密码为 null、账号超过 100 字符或密码超过 200 字符返回 400；账号去除首尾空格后查询，空字符串按凭据不匹配处理；账号不存在、密码错误或停用账号返回 401/LOGIN_FAILED。用户名、工号、手机号在数据库统一命名空间中跨字段唯一；同人多个字段同值允许，不同人不允许占用同一值。

`GET /me` 中 managementGrants 为数组，每项含 id、roleId、roleName、permission、scope、companyId、departmentId、company、department；由当前角色权限和员工角色范围联表计算。无组织字段为 null。permissionCount 是原始角色关联权限数，不代表有效可操作数量；有效业务权限请用 `/me/permissions`。

`POST /authorize` 请求：`{"appCode":"系统码","permissionCode":"权限码","requestId":"跟踪号"}`。appCode/requestId 最多 191 字符，permissionCode 最多 512。返回 `allowed` 布尔值、permissionCode、requestId。没有权限时本接口仍为 200/allowed=false；这只是鉴权结果，不执行真实业务。

目录路径中的应用及模块 id 都由后端验证；不可利用另一系统模块 id 读取其功能。用户可进入应用，并不代表拥有该应用的全部操作。

### 人员列表

`GET /users/page?q=&company=&departmentId=&page=0&size=20`。

- q 匹配姓名、登录名、电话、工号；company 是公司名称；departmentId 是部门 id。
- 筛选条件只能缩小授权范围，不能扩大范围；越界筛选返回空列表及 total=0。
- page 小于 0 时取 0；size 小于 1 时取 1，大于 100 时取 100；`/users` 默认 size=30。
- 每项含 id、employeeNo、username、name、phone、company、departmentId、department、position、status、employmentStatus、roles、managementActions。
- managementActions 给出目标可尝试的 user.roles／handover.start／handover.complete。角色权限子集、离职状态等业务前置条件仍在写入时检查。
- 无部门人员只有 GLOBAL user.read 可以查看；分页 total 与 items 使用相同范围谓词。

### 业务角色与权限替换

请求体统一为 `{"ids":[1,2]}`；null 列表为 400；空数组表示撤销全部。去重处理，不存在的角色／权限 id 不建立关联；界面应仅提交服务器返回选项。所有授权替换均写 authorization_changes。角色权限写入的目标角色 ID 必须来自角色目录；非法目标在实际建立关联时由外键约束拒绝，不约定所有目标错误都返回 404。

角色替换采用增量更新：保留角色的范围不变；撤销角色级联删除其范围；新角色不会自动获得管理范围。范围由 role-assignments 专用接口调整。禁止已完成离职的人员重新分配角色；局部管理员对子集校验不通过的目标不能部分修改。

### 角色管理操作权限

`PUT /roles/{id}/management-permissions` 请求 `{"permissions":["user.read","user.roles","org.read"]}`。空数组撤销此角色的管理操作，影响所有角色持有者；员工范围保持不变。未知权限或无效角色返回 400。
`audit.read`、`role.permissions`、`management.grant` 仅允许配置给系统管理员角色，运行时还必须有 GLOBAL 范围。配置后必须保留至少一名有效平台授权管理员。

### 员工角色与范围

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

### 组织维护

新增公司（POST）：`{"code":"COMPANY_A","name":"A 公司"}`。
新增部门：`{"code":"DEPT_A","name":"工程部","companyId":10}`。
修改公司／部门（PATCH）允许 `{"code":"DEPT_A","name":"新名称"}` 或只提交其中一项，不得提交 companyId 或 parentId。编码 1–100 字符，名称 1–191 字符，不允许空白值。

组织只包含公司及其部门，不设部门层级。组织维护不提供删除、移动或停用接口。拥有 org.write 的部门范围管理员可改名其已有部门，但不能新增部门；公司／平台管理员可在有权公司新增部门。parentId 非空属于无效输入；授权计算始终按精确部门。部门负责人字段不在此维护接口中调整。

### 离职与审计

发起：`{"receiverNo":"工号","ownerNo":"工号","expectedDate":"2026-11-01","reason":"交接说明","oaReference":"可选单号"}`。

接收人和负责人必须启用、未进入交接且不是离职本人，同时在 handover.start 的授权范围内。不能为当前登录账号办理离职。日期为 YYYY-MM-DD；reason 最长 1000，OA 单号最长 200。开始交接保留授权，不给接收人自动复制角色。

完成：`{"note":"完成说明","confirmed":"true"}`。note 最长 1000。事务内删除用户角色并级联删除角色范围、停用用户、撤销会话、更新交接状态并记录变更；已完成交接重复请求直接返回成功，权限仍需校验。

交接详情中的接收人／负责人如果已不在查看者 user.read 范围内，返回“范围外人员”，工号与 id 为 null。管理授权配置明细仅对 GLOBAL management.grant 查看者开放，其他人员的交接历史过滤该变更类型。

鉴权日志仅系统管理员在 GLOBAL audit.read 生效时可读，返回全部范围日志，包括无法关联当前用户的历史记录。公司领导、部门经理、项目经理不能查看该全局日志。

## 4. 日志查询与归档

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

## 5. 统一响应与业务错误码

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

授权编辑不设置版本冲突提示；多个合法管理员顺序保存采用最后一次提交。所有管理写入串行化是本课程规模的简化选择，大规模系统需进一步细化锁与压力测试。

### PUT 与 PATCH 的字段语义

角色、权限和角色范围使用 PUT 完整替换集合。公司、部门使用 PATCH 局部更新，Content-Type 为 application/json。

例如 `PATCH /organizations/departments/12` 的请求 `{"name":"工程部"}` 只修改名称，编码、所属公司与负责人不变；`{"code":"DEPT_12"}` 只修改编码。
code/name 至少提供一个；省略表示保留，显式 null、空白、code 超过 100 字符、name 超过 191 字符，以及 companyId/parentId/enabled 等其他字段均返回 HTTP 400、code=INVALID_ORGANIZATION_PATCH。重复同一合法 PATCH 保持相同资源状态。成功响应 data=null；组织资源不接受 PUT，返回 HTTP 405。
