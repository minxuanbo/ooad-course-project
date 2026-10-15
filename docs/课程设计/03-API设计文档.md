# 市政公司统一权限管理系统——API设计文档

版本：1.1；日期：2026-10-15；设计依据：最新离线界面原型；数据库：MySQL 8。

## 文档状态

本文定义原型对应的目标接口契约，不表示接口已经全部实现。本次仅修改文档，离线原型不调用任何 API，正式前后端与 schema.sql 均未改动。API-05、API-06、API-18 及新增 API-22/23 以下述目标设计为准；其余接口保留现有实现基线。文中明确标注的“当前”行为用于说明兼容差异，不是建议照搬的目标行为。

## 1. 接口约定

- 开发后端地址：http://localhost:8081；统一前缀 /api。Vue 开发服务器转发 /api，不直接连接数据库。
- 请求体为 UTF-8 JSON，Content-Type: application/json；查询参数应 URL 编码。
- 认证采用 Authorization: Bearer &lt;token&gt;。token 是随机不透明令牌，不是 JWT；数据库存其 SHA-256 摘要。
- 默认会话有效期 12 小时，expiresIn 单位为秒，无刷新令牌接口。
- 登录接口无需认证；退出接口容许缺失令牌；其他接口要求认证。标为“管理员”的接口还检查有效系统管理员角色。
- ID 为整数，业务标识如工号、电话、系统代码为字符串，不得按数字解析电话。
- enabled、assigned、revoked、日志 allowed 通常为数值 0/1；鉴权响应 allowed、canManage 为 JSON 布尔值。目标本人系统与准入接口不再返回 connected 字段；本次只设计功能权限演示，不承诺真实业务接入。
- 查询返回对象或数组本身，不统一包裹 code/data。void 写接口成功返回 HTTP 200、空响应体，不是 204。
- 页面/日志时间文本按 UTC 展示；日期字段为 YYYY-MM-DD，日志/交接时间通常 YYYY-MM-DD HH:mm:ss。数据库保存 DATETIME(3)，展示不保留毫秒。
- 本文示例均为格式示例，目标 ID、数量及权限码应以实际查询返回为准，不使用示例值覆盖真实数据。

### 1.1 状态码与异常

| HTTP | 含义 | 典型响应 |
|---|---|---|
| 200 | 请求完成；鉴权可仍为拒绝 | 对象、数组或空体 |
| 400 | 业务校验失败 | {"error":"请正确填写字段：reason"} |
| 401 | 凭据错误、令牌过期/撤销、账号停用 | {"error":"登录已失效"} |
| 403 | 无管理权限或系统准入拒绝 | {"error":"没有执行该操作的权限"} |
| 404 | 明确检查到目标用户或系统不存在 | {"error":"用户不存在"} |
| 500 | 未分类异常 | {"error":"服务器内部错误"} |

当前通用 Exception 处理器将部分框架参数解析异常、缺少必需请求头、数据库约束异常也转换为 500，并非完整标准化错误体系；不能假设所有非法 JSON/参数都返回 400。后续应补充参数异常映射和稳定错误码。本阶段调用方必须检查 HTTP 状态及响应中的业务字段。

### 1.2 权限分层

认证确定“是谁”，系统准入确定“能否进入某系统”，功能鉴权确定“能否执行某项操作”，三者不能互相代替。管理权限依赖角色 code=系统管理员 且 enabled=1；暂未拆成 user:read/user:update 等管理操作权限。

## 2. 接口总表

| 编号 | 方法与路径（均加 /api） | 权限 | 用途 |
|---|---|---|---|
| API-01 | POST /auth/login | 无 | 登录 |
| API-02 | POST /auth/logout | 可选令牌 | 撤销当前会话 |
| API-03 | GET /me | 登录 | 身份、角色、管理资格 |
| API-04 | GET /me/permissions | 登录 | 当前有效权限集合 |
| API-05 | GET /me/applications | 登录 | 仅本人有权系统 |
| API-06 | POST /me/applications/{id}/enter | 登录 | 实时准入并记录日志 |
| API-07 | POST /authorize | 登录 | 功能级鉴权并记录日志 |
| API-08 | GET /dashboard | 管理员 | 统计 |
| API-09 | GET /users/page | 管理员 | 用户筛选分页 |
| API-10 | GET /users | 管理员 | 兼容数组查询 |
| API-11 | GET /users/filters | 管理员 | 组织筛选选项 |
| API-12 | GET /users/{id}/roles | 管理员 | 用户角色选项 |
| API-13 | PUT /users/{id}/roles | 管理员 | 替换用户角色 |
| API-14 | GET /roles | 管理员 | 角色列表 |
| API-15 | GET /roles/{id}/permissions | 管理员 | 角色权限选项 |
| API-16 | PUT /roles/{id}/permissions | 管理员 | 替换角色权限 |
| API-17 | GET /applications | 管理员 | 系统目录 |
| API-18 | GET /authorization-logs | 管理员 | 鉴权日志 |
| API-19 | GET /users/{id}/handover | 管理员 | 交接详情与变更记录 |
| API-20 | POST /users/{id}/handover | 管理员 | 开始交接 |
| API-21 | POST /users/{id}/handover/complete | 管理员 | 完成离职处置 |
| API-22 | GET /me/applications/{id}/modules | 登录且有系统权限 | 当前系统模块列表 |
| API-23 | GET /me/applications/{id}/modules/{moduleId}/features | 登录且有系统权限 | 功能点及操作按钮状态 |

## 3. 认证与本人信息

### API-01 登录

请求：POST /api/auth/login。

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| account | string | 是 | 工号/用户名/电话；服务端去除两端空白 |
| password | string | 是 | 不 trim；验证 BCrypt 摘要 |

```json
{"account":"SG010000","password":"123456"}
```

成功示例：

```json
{
  "token":"<本次生成的不透明令牌>",
  "expiresIn":43200,
  "user":{"id":10001,"username":"SG010000","name":"示例员工"}
}
```

账号匹配数必须恰好为 1；账号必须 ACTIVE。密码错误、账号不存在或停用均为 401“账号或密码错误”。null 字段返回 400；空白格式校验尚不完整。每次登录新建会话，当前允许同一账号多会话。

### API-02 退出

POST /api/auth/logout，无请求体。撤销请求令牌对应会话，成功 200 空体；缺失、未知或已撤销令牌不会新增会话。不会主动注销该用户其他有效会话；离职完成才撤销其全部会话。

### API-03 当前身份

GET /api/me，无参数：

```json
{
  "canManage":false,
  "user":{"id":10001,"username":"SG010000","name":"示例员工"},
  "roles":[{"id":5,"code":"普通员工","name":"普通员工"}],
  "permissionCount":94
}
```

roles 当前返回已分配角色，不过滤角色启用状态；permissionCount 为已关联权限去重数量，当前不按角色/权限/系统启用状态过滤。因此二者是关系概览，不可作为执行操作的最终授权依据；canManage 单独按有效角色计算。

### API-04 本人权限

GET /api/me/permissions?app=系统代码；app 可省略，提供时精确匹配。

返回数组项：appCode:string、appName:string、code:string、module:string、feature:string、action:string。action 为“查看/新增/编辑/删除/审批/导出/配置”等中文名称，code 末段使用 read/create/update/delete/approve/export/configure。

仅返回当前启用角色、启用权限、启用系统的去重集合，按 appCode、权限 code 排序；无权限返回 []。数组用于界面参考，后续操作仍须重新鉴权。

## 4. 系统准入与功能鉴权

### API-05 本人系统列表（目标变更）

GET /api/me/applications，只返回当前用户至少拥有一项有效操作权限的启用系统：

```json
[
  {"id":1,"code":"oa协同办公系统","name":"OA协同办公系统","permissionCount":14}
]
```

有效权限要求账号、角色、系统、模块、功能点、操作类型和权限条目均有效，并存在用户—角色—权限关系；多角色权限取并集并去重。permissionCount 按不同权限 ID 计数，不保存为冗余字段。无权或停用系统不返回名称、ID 或目录；无授权返回 HTTP 200 和 []。

客户端返回 0 个系统时显示“暂无可用应用，请联系管理员分配权限”；1 个时调用 API-06 校验后进入；多个时显示选择页。管理员的“我的应用”也按本人权限过滤，完整系统目录另由管理员 API-17 提供。管理身份不自动绕过业务权限检查。

**现有实现差异：** 当前仍返回全部系统和 enabled/connected 字段，需后续修改查询和调用方，本次未实施。

### API-06 系统入口（目标变更）

POST /api/me/applications/{id}/enter，无请求体；id 为 applications.id。服务端重新检查当前数据库授权，不信任列表或按钮的历史状态。

有权时 HTTP 200：

```json
{
  "allowed":true,
  "application":{"id":1,"code":"oa协同办公系统","name":"OA协同办公系统"},
  "next":"modules"
}
```

next 是客户端视图指示，不是外部跳转 URL。随后读取 API-22；不再返回“暂未接入”，也不在成功响应中返回 error。该响应仅允许浏览本系统功能权限界面，不表示任何具体操作已经获准或执行业务。

无权、已撤权或停用系统返回 HTTP 403：

```json
{"allowed":false,"error":"无权访问该系统或授权已被回收"}
```

不附带系统名称、模块或功能内容；系统不存在返回 404，登录失效返回 401。已存在系统的准入允许与拒绝写入 authorization_logs，permission_code 使用 system:enter。客户端遇到 403 清空当前系统目录、退回“我的应用”并重载列表；401 清理身份并回到登录页。请求失败不得展示上一次缓存目录。

**现有实现差异：** 当前成功响应为 allowed/connected/message/error；修改响应契约需同步调用方，本次未实施。

### API-22 系统模块列表（新增设计，未实现）

GET /api/me/applications/{id}/modules，无请求体。读取前再次检查当前用户的系统准入资格；不能只检查登录，也不能因 API-06 曾成功而放行。

```json
{
  "applicationId":1,
  "modules":[
    {"id":10,"name":"公文管理","featureCount":5,"permissionCount":35,"grantedPermissionCount":3}
  ]
}
```

有系统权限后可浏览该系统所有启用模块，包括本人在该模块操作权限为 0 的模块，与原型一致。featureCount 是启用功能点数，permissionCount 是有效操作条目数，grantedPermissionCount 是本人有效权限去重数，均实时查询派生。模块按 sort_order、id 排序；合法空目录返回 modules:[]。无系统权限返回 403，不返回模块信息；未知系统 404；无效会话 401。

### API-23 模块功能点与操作（新增设计，未实现）

GET /api/me/applications/{id}/modules/{moduleId}/features，无请求体。先检查系统权限，再验证模块属于该系统且启用；无系统权限 403，模块不存在、已停用或不属于该系统 404。

```json
{
  "applicationId":1,
  "moduleId":10,
  "features":[
    {
      "id":100,
      "name":"收文登记与流转",
      "description":"支持外部来文登记、公文要素识别与流转路径配置",
      "operations":[
        {"permissionId":1000,"permissionCode":"oa协同办公系统:公文管理:收文登记与流转:read","actionCode":"read","label":"查看","allowed":true},
        {"permissionId":1001,"permissionCode":"oa协同办公系统:公文管理:收文登记与流转:update","actionCode":"update","label":"编辑","allowed":false}
      ]
    }
  ]
}
```

ID 仅为格式示例，以真实查询为准。功能点按 sort_order、id 排序，操作按操作字典 sort_order、code 排序。只返回启用功能和已登记的有效操作，不为每个功能强制生成七种操作；Excel 中“全部”的初始化展开规则仍适用。操作集合空时 operations:[]，功能集合空时 features:[]。

allowed 由本人所有有效角色权限并集计算，不是客户端提交项。false 的按钮灰色且 disabled，不能触发操作；true 也只是本次查询的展示状态，点击仍须 API-07 校验。description 来自功能点表，不在每个操作权限中重复保存。API-22/23 的正常目录读取不产生日志，避免刷新带来大量鉴权操作记录；系统准入和显式操作鉴权仍记录。

### API-07 操作鉴权

POST /api/authorize：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| appCode | string | 业务上必需 | 系统唯一代码 |
| permissionCode | string | 业务上必需 | 完整操作权限码，必须属于 appCode |
| requestId | string | 否 | 调用方追踪标识，建议不超过 191 字 |

```json
{"appCode":"oa协同办公系统","permissionCode":"oa协同办公系统:公文管理:收文登记与流转:read","requestId":"demo-001"}
```

返回示例：

```json
{"allowed":true,"permissionCode":"oa协同办公系统:公文管理:收文登记与流转:read","requestId":"demo-001"}
```

授权拒绝仍为 HTTP 200，但 allowed=false；不要与 API-06 的 403 混淆。appCode/permissionCode 缺失时当前实现按不允许处理并记录日志，不一定返回 400。无效会话先返回 401。

目标校验须沿 permissions → features → modules → applications 检查归属与全部启用状态，并检查 actions.enabled，不能使用客户端提供的系统名替代归属校验。客户端仅对可用按钮发起本接口；服务端仍独立检查，不因按钮可用而信任请求。allowed=false 时提示拒绝并重新加载应用列表与当前目录，无权限系统须退出。目录响应中的 allowed 与本接口结果不得持久化为用户授权。

当前 requestId 只用于日志关联，不是幂等键，重复调用会重复写鉴权记录。后端业务必须在 allowed=true 后才执行业务操作；此接口不替业务系统读写其业务数据。当前没有独立的调用方客户端凭证、OAuth2/OIDC 或服务间签名认证，真实接入前必须补齐可信服务边界。

## 5. 管理查询

### API-08 工作台

GET /api/dashboard 返回对象，所有值为记录数：

```json
{"users":10001,"departments":111,"applications":18,"permissions":2521,"roles":5,"authLogs":16}
```

统计不区分启用/停用；applications 是目录数量，不是实际接入数量。

### API-09 用户分页

GET /api/users/page。

| 参数 | 类型 | 默认 | 规则 |
|---|---|---|---|
| q | string | 空 | 姓名、用户名、工号、手机号 LIKE 模糊匹配；两端空白去除 |
| company | string | 空 | 非空时精确匹配公司 |
| departmentId | integer | 不传 | 非空时精确匹配部门主键 |
| page | integer | 0 | 从 0 开始，负数归零 |
| size | integer | 20 | 限制为 1–100 |

组织条件与关键词匹配组取 AND；页号超过末页返回空 items，不自动回到末页。LIKE 中 % 和 _ 目前作为通配符，不转义为普通字符。

```json
{
  "items":[{
    "id":10001,"employeeNo":"SG010000","username":"SG010000","name":"示例员工",
    "phone":"13800000000","company":"示例公司","departmentId":10,
    "department":"示例部门","position":"职员","status":"ACTIVE",
    "employmentStatus":"EMPLOYED","roles":"普通员工"
  }],
  "total":1,"page":0,"size":20
}
```

phone/company/departmentId/department/position/roles 可以为 null。employmentStatus 为 EMPLOYED、HANDOVER、DEPARTED。roles 为展示字符串，授权编辑应调用 API-12，不解析该字符串生成 ID。

### API-10 兼容用户数组

GET /api/users，参数同 API-09，但 size 默认 30，返回用户对象数组，没有 total/page/size 包装。新前端使用 API-09。

### API-11 公司与部门选项

GET /api/users/filters?company=公司名。company 默认空：

```json
{"companies":[{"company":"示例公司"}],"departments":[{"id":10,"name":"示例部门"}]}
```

companies 始终为全部非空公司名称去重；departments 为所选公司有员工关联的部门。未选择公司时返回全部人员关联部门；不存在的公司返回空部门列表，不是 404。该接口不提供空编制部门的完整组织树。

### API-14 角色列表

GET /api/roles，返回数组项：id、code、name、description、enabled、userCount、permissionCount。count 使用 DISTINCT 避免两个关系表联合导致重复计数；数量是关联数量，不等于有效访问权限数。

### API-17 系统目录

GET /api/applications，返回数组项：id、code、name、enabled、permissionCount。permissionCount 为系统登记的权限数量；此接口不是本人权限查询。

### API-18 鉴权日志

GET /api/authorization-logs?limit=100，仅系统管理员可访问。普通员工不显示日志入口或日志页面，即使直接调用接口也返回 403，且不返回日志条目。员工操作仍被服务端记录，管理员查询可包含员工记录，不提供员工个人日志接口。

目标 limit 默认 100，取值 1–500，非法类型或越界返回 400；当前代码仅限制最大值，尚需补齐校验。管理资格每次请求重查，失去管理权限后客户端清空已加载日志并退出日志页。

返回数组项：id、occurredAt、username、appCode、permissionCode、allowed、requestId、reason。按 id 降序，时间 UTC；requestId 可为 null。无高级过滤、游标或全量导出接口。

## 6. 授权管理

### API-12 用户角色选项

GET /api/users/{id}/roles，返回全部角色及 assigned：

```json
[{"id":5,"code":"普通员工","name":"普通员工","assigned":1}]
```

assigned=1 表示已分配。当前读取接口不单独校验目标用户存在，不存在时仍可能返回全部未勾选选项。

### API-13 替换用户角色

PUT /api/users/{id}/roles，请求 {"ids":[2,5]}，成功 200 空体。

ids 必须是数组，不可 null；[] 表示清空。ID 去重后全量替换，而不是追加。目标不存在 404，已离职 400，普通员工 403。系统记录 SET_ROLES。无效角色 ID 在当前 INSERT…SELECT 中被忽略，并非逐个报错；客户端应只提交选项列表中的有效 ID。停用角色仍可能被分配，但不参与有效鉴权。当前未防止管理员移除自己的最后一个管理角色。

同一用户的授权替换、发起交接和完成处置通过目标用户行锁协调。关系替换与变更日志在一个事务内提交。

### API-15 角色权限选项

GET /api/roles/{id}/permissions?q=关键词，返回全部匹配项，无 500 项截断：

字段：id、appName、module、feature、action、code、assigned。q 默认空，在功能、模块或系统名中 LIKE 匹配。当前前端首次不传 q，拿到全部选项后本地筛选，避免全量替换时清除未加载的授权。

### API-16 替换角色权限

PUT /api/roles/{id}/permissions，请求 {"ids":[1,2,3]}；[] 清空；成功 200 空体。

去重后替换全部关联，并记录 SET_PERMISSIONS，detail 为 count=提交数量。角色权限变更影响所有持有该角色的用户。不存在的权限 ID 当前忽略；目标角色不存在的错误响应尚不统一，可能因外键返回 500或在空集合时成功，正式接入前应补齐存在性校验。不要依赖这些边缘行为构造客户端逻辑。

角色替换 API 没有版本号/ETag，也未实现冲突提示，多管理员并发修改同一角色仍有竞争风险；前端完整加载后提交不等于乐观锁。

## 7. 离职交接

### API-19 读取交接详情

GET /api/users/{id}/handover，目标不存在 404。

响应对象：

| 字段 | 结构 |
|---|---|
| user | id、name、username、status |
| handover | 无记录时 {}；有记录时为下列字段 |
| roles | [{id,name}] 当前已分配角色 |
| systems | [{name}] 当前有效系统 |
| history | 最近 50 条 USER 目标变更，按最新优先 |

handover 保持数据库 snake_case 命名：user_id、receiver_id、owner_id、expected_date、reason、oa_reference、state、started_by、started_at、completed_by、completed_at、completion_note，以及关联查询的 receiver_name、receiver_no、owner_name、owner_no。

history 项为 occurred_at、operator_id、action、detail。completed_* 在未完成时可为 null。历史 detail 是字符串，可能是 JSON 文本、ID 集合文本或普通文本，不应全部强制解析为同一 JSON 类型。

### API-20 开始交接

POST /api/users/{id}/handover：

```json
{
  "receiverNo":"SG000012",
  "ownerNo":"SG000011",
  "expectedDate":"2026-10-31",
  "reason":"工作资料与未结事项交接",
  "oaReference":"OA-DEMO-001"
}
```

| 字段 | 必填与长度 | 规则 |
|---|---|---|
| receiverNo | 是，≤100 | 启用且无交接记录，不能是目标本人 |
| ownerNo | 是，≤100 | 同上；允许与接收人为同一人 |
| expectedDate | 是，≤10 | 可解析的 YYYY-MM-DD，不要求将来日期 |
| reason | 是，≤1000 | 非空白 |
| oaReference | 否，≤200 | 未填记空串 |

成功 200 空体，创建 HANDOVER 并记录 BEGIN_HANDOVER。不删除角色、不撤销会话。对象必须存在、ACTIVE、无历史交接记录、不是操作者；不满足为 400或404。重复发起拒绝，不按 OA 单号做幂等。

### API-21 完成处置

POST /api/users/{id}/handover/complete：

```json
{"note":"已确认资料移交与未结事项接收","confirmed":"true"}
```

note 非空、最长 1000；confirmed 当前请求模型是 Map&lt;String,String&gt;，客户端按字符串 "true" 提交，不定义为布尔契约。无交接记录、未确认或自己办理自己离职返回 400。

事务内依次锁定目标用户和交接记录、保存原角色快照、删除所有用户角色、将账号置 DISABLED、撤销全部会话、更新 DEPARTED 与完成信息、写 COMPLETE_HANDOVER。成功 200 空体。已经 DEPARTED 的重复请求直接成功，不重复处置和日志；保留用户与交接记录。

## 8. 页面流程与权限变化（目标设计）

1. 登录后读取 /me 和 API-05；只有 canManage=true 才展示管理导航和日志入口。员工仅显示个人应用。
2. 进入应用调用 API-06，获准后调用 API-22；选择模块调用 API-23。直接访问目录也必须经服务端检查，前端路由不构成安全边界。
3. 正式实现可在应用页每 3 秒及窗口重新聚焦时刷新授权列表与当前目录；这是未来在线实现建议，纯离线原型不轮询、不发请求。显示可能有短暂延迟，实际按钮操作始终逐次鉴权。
4. 权限减少时更新按钮；当前系统有效权限归零时清空目录、返回“我的应用”并隐藏该系统。全部应用无权时显示空态。新授权下次查询可见，不能依赖登录时权限快照。
5. 网络故障时暂停操作并清空不可确认的目录，不使用旧 allowed=true 放行；401 回登录，403 按失去系统或管理权限处理，404 清除失效目录目标。
6. 查询和鉴权不得写入业务数据。本阶段不设计“执行新增/编辑/审批”的通用业务端点；以后接入实际业务时必须在业务服务执行边界再次校验，解决鉴权成功到业务提交之间的权限变化问题。

## 9. 接入和兼容性说明

1. 数据库迁移未改变公开 URL；SQLite 字符串时间改为 MySQL UTC 时间类型后，响应仍保持页面可读的字符串。
2. 业务系统应从本权限服务验证令牌代表的用户，不能自行提交 userId 冒充他人。
3. 避免长期缓存 allowed=true；新操作重新鉴权，服务不可用应按拒绝或事先明确的降级策略处理，当前没有离线授权方案。
4. SQL 参数绑定防止参数拼接注入；前端权限隐藏不是安全边界。
5. 当前没有创建/修改/删除用户、创建角色、注册系统、HR 同步、OAuth 回调、密码重置等接口；不得虚构对接地址。
6. 生产化还需 HTTPS、限流、输入长度统一校验、稳定错误码、服务身份认证和凭据生命周期管理。

## 10. 文档与测试对应

API-09/10/11 的组合筛选、API-12/13 的越权限制、API-06 撤权/恢复、API-19/20/21 生命周期在 AccessBoundaryTest 中覆盖。MySQL 读接口回归检查统计、角色、权限目录、日志与本人权限；不等于全部并发、性能和故障场景已验证。流程与实现细节见[详细设计](05-详细设计文档.md)。

### 10.1 本次设计的待实现验收

| 场景 | 目标结果 |
|---|---|
| 员工无财务系统权限 | API-05 不返回财务系统；直接准入或读其模块/功能返回 403，无目录内容 |
| 仅有 OA 查看权限 | OA 可进入；对应查看 allowed=true，未授权操作 false |
| 多角色重复授权 | 权限按 ID 去重，任一有效角色授予即可允许 |
| 后台撤销系统全部权限 | 下一次目录请求 403、列表移除系统、操作 allowed=false |
| 后台撤销单项操作 | 系统其他权限保留时可继续进入，该按钮变灰；直接鉴权拒绝 |
| 普通员工访问日志 | 导航不可见，直接请求 403；管理员仍可查询员工操作记录 |
| 部门/功能点改名 | ID 与既有 permissionCode 不变，显示名称通过关联更新 |
| 模块或功能点停用 | 其权限不再贡献准入计数，目录不可读，操作鉴权拒绝 |
| 跨系统 moduleId | 在有权系统路径中使用其他系统模块 ID 返回 404 |
| 请求失败/登录失效 | 不复用旧权限放行，清理不可确认内容 |

本表是待实现的验收要求，不代表现有测试已覆盖。API-22/23 依赖数据库目标设计中的模块、功能点与操作字典；实施时需要独立迁移并保留原权限 ID 与 code，见数据库设计第 10 节。
