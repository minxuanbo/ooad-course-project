# RBAC API契约

版本：1.0。此为正式规范正文，保持已评审的24个操作、输入输出、错误、范围及K语义。本次仅提取整理，不改变接口。字段和行为变更须同步原型、测试及本文件；[页面对照](api-map.md)只作索引。

来源为[四组原型/契约的历史评审](rbac-rectification/RBAC原型与API契约.md#review)。下面保留T04/T06/T08/T10编号便于追踪；“原型”“后续”指场景来源，不代表当前实现状态，状态以[验收说明](testing-acceptance.md)为准。原型固定ID、demo口令及/mock路径均非生产约定。

<a id="common"></a>

## 2. 四组共用契约

### 2.1 响应、输入和查询

复用 `ApiResponse`：成功 HTTP 状态有意义，外壳为 `{"code":"OK","message":"成功","data":...,"requestId":"..."}`；失败为 `{"code":"...","message":"...","data":null,"requestId":"..."}`。所有查询只读取业务状态；最小技术鉴权记录不等于业务写操作。客户端不得提供可信 actorId、管理员标志、权限集或管理范围。可信身份沿既有登录/当前用户衔接，未定义新认证接口、令牌格式或生命周期。

人员、角色和 API 资源列表使用 `data={items,total,page,size}`：page 从 0 开始，默认 0；size 默认 20、范围 1–100；按 id 升序稳定排序；越末页返回空 items 并保留同筛选 total。原型为演示翻页可选 size=5。先限制范围，再应用 AND 组合筛选及 q，最后同条件计数与分页；禁止全量返回后由前端裁剪。部门树、有限系统目录、角色已选权限等不是人员全量接口，形状在对应票写清。日志沿旧 cursor/limit 快照分页，额外提供同快照筛选总数。

所有 ID 为正安全整数（原型 JS 安全范围；后端存储仍为 Long）；路径和 query 的整数不得隐式截断。`ids` 必填且必须为数组，元素唯一、存在并满足各组状态规则及系统/授权边界；重复、null、未知或非法项拒绝，不静默去重。三组关联整体替换，`[]` 明确清空；请求中任一项失败则全部回滚。PATCH 只更新显式支持的字段，缺省保持原值；null 仅在 parentId 可作为清除上级时接受；类型错误、空请求、未知字段拒绝。成功写入后返回该操作说明的当前表示或 null，不混用裸数组与分页对象。

| HTTP / code | 语义与反馈 |
| --- | --- |
| 400 `INVALID_ARGUMENT` | 参数、长度、类型、重复 ID、非法关联或树约束错误；不改变状态 |
| 400 `INVALID_CURSOR` | 日志游标错误或筛选与游标不一致；重置查询 |
| 401 `LOGIN_FAILED` | 沿用旧登录错误码；未知账号、口令错误、停用统一提示账号或密码错误，不透露哪项失败 |
| 401 `AUTHENTICATION_REQUIRED` / `SESSION_INVALID` | 匿名或当前可信身份失效；沿既有响应流程 |
| 403 `MANAGEMENT_ACCESS_DENIED` | 账号/口令通过但没有本权限系统有效管理操作权限；不建立成功登录状态 |
| 403 `PERMISSION_DENIED` | 当前请求缺少实际有效操作权限/API 映射 |
| 403 `MANAGEMENT_SCOPE_DENIED` | 越部门范围、非共同根全局操作、部门管理员改自身授权/部门或修改有效全局管理员 |
| 404 `RESOURCE_NOT_FOUND` | 在已许可范围中引用不存在的对象；范围外人员返回 403，不借 404 泄露存在性 |
| 409 `DATA_CONFLICT` | 编码唯一性或关联并发冲突，完整回滚 |
| 409 `LAST_ADMIN_REQUIRED` | 写后不足一名具备完整 K 的有效全局管理员，完整回滚 |
| 500 `INTERNAL_ERROR` | 请求失败，可重试；原子写不得留下部分状态，不返回异常栈 |

检查顺序为可信身份/账号有效性、实际请求权限、部门/全局范围、输入与关联、修改后管理员保护。人员 ID 不可见或不能确认在范围内时统一 403；共同根可见范围内确实不存在返回 404。错误明细可显示字段和拒绝原因，但不回显口令、令牌或超范围对象内容。

### 2.2 权限、系统与范围

目录只选择本轮实际场景，系统 code 本轮采用 `rbac`。真实 systemId、departmentId、roleId、permissionId、apiId 由数据库产生，不承诺原型中的数值。`/mock/foreign-resource` 仅是跨系统拒绝的测试夹具，不是课程或正式 API，后续不得登记到初始化。匿名登录及本人查询不纳入管理能力 K。

统一有效性沿启用用户→启用角色→启用权限→同系统启用 API→启用系统计算，以服务端识别的方法和 Spring 路径模板匹配。任意有效权限映射到实际 API 即可放行，不要求特定角色名；权限编码只标识资源，不由前端传值决定放行。父菜单不自动授予子按钮；导航补祖先不加到授权 ids。权限树父节点停用不自动停用子节点，各节点按自身有效性判断；若要撤销子 API 必须明确改变对应授权、映射或状态。

登录只要求账号/口令通过且至少一项本权限系统已登记的管理操作 API 有效可达，包括相应管理查询；菜单、无角色、空角色或仅外系统业务权限均不满足。登录通过仍逐次校验具体 API 和范围。本人身份/权限查询只要求有效登录，不要求 users.read；全部管理权限撤回后的既有可信身份能否继续承载本人查询沿身份机制处理，管理请求立即拒绝、再次登录拒绝。

共同根须由 T03 对真实组织树核对，不按名称、空 parentId 或角色名判断。共同根本身不授予任何操作。部门人员与组织导航仅限本人部门及后代，不能扩到所属公司其他部门。全局角色/资源目录读写以及日志查询均额外要求共同根范围和相应 API 权限；T10 使用专门的受限候选查询，部门管理员不读取全局角色目录。日志权限不要求完整 K，但部门管理员即使被配到日志权限也不能查询日志。

### 2.3 精确目录与管理员保护 K

下表列已确认契约。T05 初始化查询、最小日志及 K 恢复所需基线，各实现遵守经评审的实际API；端点验收状态见验收说明。

| 权限 code（系统 rbac） | 场景/API 模板 | 范围 | K |
| --- | --- | --- | --- |
| `rbac.users.read` | GET `/api/departments/tree`；GET `/api/users`；GET `/api/users/{id}/roles` | 本部门及后代 | 是 |
| `rbac.users.roles.write` | GET `/api/users/{id}/assignable-roles`；PUT `/api/users/{id}/roles` | 人员范围＋可授予角色子集 | 是 |
| `rbac.users.disable` | PATCH `/api/users/{id}/status` | 人员范围，必要停用 | 否 |
| `rbac.users.department.write` | PUT `/api/users/{id}/department` | 原部门与目标部门均在范围内 | 否 |
| `rbac.resources.read` | GET `/api/systems`；GET `/api/permissions/tree`；GET `/api/api-resources`；GET `/api/permissions/{id}/apis` | 共同根＋具体权限 | 是 |
| `rbac.resources.write` | PATCH `/api/permissions/{id}`；PATCH `/api/api-resources/{id}`；PUT `/api/permissions/{id}/apis` | 共同根＋具体权限 | 是 |
| `rbac.roles.read` | GET `/api/roles`；GET `/api/roles/{id}/permissions` | 共同根＋具体权限 | 是 |
| `rbac.roles.write` | POST `/api/roles`；PATCH `/api/roles/{id}` | 共同根＋具体权限 | 是 |
| `rbac.roles.permissions.write` | PUT `/api/roles/{id}/permissions` | 共同根＋具体权限 | 是 |
| `rbac.logs.read` | GET `/api/authorization-logs` | 共同根＋日志权限 | 否 |

菜单 `rbac.workspace` 仅是导航容器，默认无 API；不列为登录管理能力或 K。一个权限可映射多 API，同 API 可被多权限复用；真实请求看对应 API 是否由任一有效权限覆盖。T06 允许关联同系统停用 API，但停用项不贡献有效能力；T08 仅保存启用权限；T10 仅分配启用且有效权限不超出操作者的角色。原型分组可用性提示使用整个操作组是否可用，具体按钮请求按 method/path 判定，不以“看到了某权限编码”替代接口检查。

K 是上表标为“是”的**所有方法＋路径模板**组成的集合，体现已确认的角色定义、角色选权、人员角色分配和资源映射四类能力及必要查询。每名计入的有效共同根用户必须自己具备整个集合，可由本人多个启用角色汇总；不同用户的零散能力不能拼成一名管理员；按 distinct 用户计数。某 API 的原映射撤掉但仍有其他有效授权覆盖时，该能力仍保留。日志、人员停用和调动不新增为 K，登录也不要求完整 K。

所有会影响 K 的写操作在同一写事务中按修改后状态重算；至少保留一名有效完整维护者。T07/T09/T11 共用稳定保护点和锁定顺序，不能各锁自己的角色后分别计数。需要并发验收的案例是两个管理员同时撤权/停用/移出根，只允许其中不会造成清零的一次提交成功；失败事务保留原用户归属、状态及全部关联。本轮 `R.atomic` 只是单线程内存模拟，未验证数据库锁、并发或真实 HTTP。

T03 提供真实账号与共同根来源，T05 据经评审数据建立授权基线及首个真实链路，后续写票实现各自操作。人员归属按原始来源保留；四张原型票的直接前置仍为 T01。

## 3. 登录、本人、部门人员与最小日志

<a id="t04"></a>

### 3.1 逐项契约

`username`在请求筛选、用户DTO及已确定身份的日志中一律表示工号；不另加`employeeNo`或独立用户名。`phone`仅为定位同一账号的另一入口，不另设口令/权限，也不因此扩充人员列表或本人DTO。

| 交互与资源 | HTTP / 参数 / data | 权限与范围 | 技术结论 |
|---|---|---|---|
| 登录 | `POST /api/auth/login`，JSON `{account,password}`，两项均为字符串；account trim后非空且最多100字符，等值匹配工号`username`或手机号`phone`，只允许唯一用户；password非空最多200，不trim。沿既有登录响应的`{token,user,expiresIn}`外形，原型只模拟`{user:{id,username,name,departmentId,enabled}}`子集。 | 匿名；两种入口定位同一`users.id`，共用有效性/BCrypt口令及统一有效权限计算，至少命中一项本系统实际管理API才成功。仅父菜单、空角色、普通员工、仅外系统权限均不足；不要求完整K或根部门。 | 2026-11-08修正：用户名就是工号，不存在第三种别名或独立`employee_no`。不按输入长度/数字特征分流；跨用户碰撞不得选第一项。T03验证字段唯一性与碰撞，T04补充复核通过后由T05实现，不设计新令牌机制。 |
| 登录失败 | 账号未知、歧义命中不同用户、口令错、停用统一 `401 LOGIN_FAILED` / “账号或密码错误”；账号/口令已正确但无有效管理权限 `403 MANAGEMENT_ACCESS_DENIED` / “该账号没有本权限系统的有效管理权限”。非字符串、必填/长度错误400。 | 任何失败均无成功登录状态；不能通过不同401提示枚举未知/停用账号。仅在已正确身份校验后区分无管理权限。 | 复用既有稳定错误码 `LOGIN_FAILED`，不新增 INVALID_CREDENTIALS。不新增 is_admin、资格表、凭证模型、认证接口。两种登录入口均须覆盖成功、401及403且失败不保留身份；补充复核证据见[原记录8.1](rbac-rectification/RBAC原型与API契约.md#login-review)。 |
| 本人身份 | `GET /api/me`；data 收敛为 `{user:{id,username,name,departmentId,enabled}}`。 | 仅有效身份，无 `users.read` 前置，不接受客户端指定另一个 userId。角色已撤销时本人查询仍可用，管理请求及再次登录另行拒绝。 | 保留路径；移除旧 managementGrants/canManage 的双轨管理模型，部门/启用字段与新模型对应。字段收敛方案本轮由用户确认采用。 |
| 退出 | `POST /api/auth/logout`，无必填请求体；Authorization可携带Bearer令牌。成功200，统一外壳code=OK、data=null并包含requestId。 | 撤销所携带的已有身份，不要求管理API或完整K。重复退出、已失效/未知令牌、缺少或非Bearer头沿既有行为返回成功空操作，不创建身份。 | 2026-11-12补齐原文遗漏，保持CoreAuthController→CoreAuthService→BearerIdentityStore的现有兼容行为，不新增认证流程。前端随后清理本地身份；被撤销令牌再次访问本人返回401 SESSION_INVALID。 |
| 本人权限 | `GET /api/me/permissions`；data 为本人本系统有效权限数组，元素 `{id,systemId,parentId,type,code,name}`，按 system.code=rbac 对应的真实 systemId 过滤；id=1 仅为原型模拟值，本轮不扩成跨系统业务查询。 | 仅有效身份；用统一有效计算。为导航补出的祖先不得混入实际授予 IDs，不隐含子按钮或 API。 | 保留路径；不要求人员管理权限。旧 `?app=` 业务目录含义本轮收口为本系统，不另加资格接口；本轮用户确认采用。 |
| 部门导航 | `GET /api/departments/tree`；data 扁平树节点数组 `{id,parentId,name}`，客户端依据 parentId 呈现。响应中的可见范围根 parentId 投影为 null，不暴露范围外父节点ID；实体实际归属不变，不返回范围外节点、名字或兄弟列表。 | `rbac.users.read` 映射该 GET；只本人部门及后代。共同根仅示例 id=1，真实根必须来自 T03 核对，不能按名称或任意空 parent 判断。 | 读资源且无业务写入；部门树不扩成组织 CRUD。原型工程部只显示工程部、道路组、项目组，兄弟财务部不出现；本轮用户确认采用。 |
| 唯一人员列表 | `GET /api/users?q=&departmentId=&enabled=&page=0&size=20`；q 姓名或工号（username）包含搜索、trim、忽略大小写，最多100；enabled 可省略或 true/false；departmentId 可省略（本人部门及后代）或正 ID（所选部门及后代）；size 1—100。data `{items,total,page,size}`，item `{id,username,name,departmentId,departmentName,enabled}`。 | `rbac.users.read` 映射该 GET；服务端先限制本人范围，再部门+q+enabled，使用相同谓词计数和分页。姓名不是唯一标识，行操作用用户id；不把电话/口令哈希等无场景字段整包返回。 | 固定id升序；越末页 `items:[]` 保留 total/page/size；0结果total=0。原型显式size=5，正式默认20。负页/非法size/非法状态400，具体范围外部门403，root范围内未知部门404；本轮用户确认采用。 |
| 人员后续动作 | 行按钮把已选 userId 交 T10 原型；`GET /api/users/{id}/roles` 等契约由 T10 编制，T04不重复创建。 | 可管理范围与实际角色查询/写入权限继续逐请求检查。 | 只接入口，不实现下游业务。 |
| 最小日志查询 | `GET /api/authorization-logs`；参数为 `userId,username,appCode,allowed,from,to,event,cursor,limit`。username为工号、精确≤100；appCode精确≤191；event精确≤32；userId正安全整数；allowed true/false；from/to有效UTC ISO-8601，from含/to不含，from<to；cursor≤4096；limit默认100、1—500。 | `rbac.logs.read` 映射该 GET，且共同根范围；完整K不是前置。部门管理员即使有该API映射也因范围403；根用户没有日志API则具体权限403。 | 保留快照游标语义，不改offset，不恢复 storage/archive/export；新增同一snapshot筛选集合total，本轮用户确认采用。 |
| 日志响应与记录 | data `{items,nextCursor,hasMore,snapshotId,total}`；items保留 `{id,occurredAt,userId,username,appCode,permissionCode?,allowed,requestId,reason,eventType,httpMethod,requestPath,httpStatus}`。未知身份的userId可null；原型匿名username为空。删除 `archivedAt/archivedBy` 对外字段。 | 服务端ID降序，首次快照为当前最大ID，只查询 id≤snapshotId；继续页 id<上页末条id。筛选/limit绑定游标，变化后清cursor从首批查询；伪造/损坏/筛选不一致 `400 INVALID_CURSOR`。每页重新校验权限/根范围。 | total为snapshot内同一筛选集合总数，并非当前页条数，也不因读取日志的后续技术记录增长；到底nextCursor=null/hasMore=false。UI支持逐批累计和刷新快照。 |

## 4. 资源目录与API映射

<a id="t06"></a>

### 4.2 逐项契约

响应外壳统一 {code,message,data,requestId}，成功 code=OK，错误 data=null。以下写成功为200；原型反馈为同语义的内存通知，不构造真实HTTP。

系统 DTO：{id,code,name,enabled}。权限 DTO：{id,systemId,parentId,type,code,name,enabled}，type=MENU|BUTTON。API DTO：{id,systemId,method,path,enabled}，method为大写HTTP方法，path为服务端识别的路径模板，不能信任前端携带的方法/模板来鉴权。IDs 为正安全整数。

| 方法与路径 | 请求、成功 data | 权限与范围 | REST核对 |
| --- | --- | --- | --- |
| GET /api/systems | 无业务写参数；返回本轮必要系统项数组 [SystemDTO]，当前仅返回 code=rbac 对应的真实系统项 | 对应实际 API 的资源读权限 + 共同根 | 读系统标识/状态，不隐含系统CRUD，不返回未评审外系统目录 |
| GET /api/permissions/tree?systemId={rbacSystemId} | systemId 默认真实 rbac 系统 ID；返回 PermissionDTO + children 的根节点数组，同系统节点按id稳定排序 | 对应实际 API 的资源读权限 + 共同根；其他系统请求403 | 树查询，保留停用项便于维护，不自动选中子按钮 |
| GET /api/api-resources?systemId={rbacSystemId}&q=&page=0&size=20 | q 可选，trim后最多100个字符，对 method或path作不区分大小写包含匹配；systemId 默认真实 rbac 系统 ID；data={items:[ApiDTO],total,page,size} | 对应实际 API 的资源读权限 + 共同根；systemId范围外403 | 过滤后id升序；page从0，size默认20/最大100；原型小样本size5；越末页items=[]且total不变 |
| GET /api/permissions/{id}/apis | data={ids:[apiId],items:[ApiDTO]}，返回全部已保存映射，均按API id排序，停用关联仍保留 | 对应实际 API 的资源读权限 + 共同根；目标须属于rbac | 查询独立关联集合，不能用候选当前分页替代完整已选ids |
| PUT /api/permissions/{id}/apis | body仅{ids:[apiId]}，必填唯一正安全整数数组；data同GET映射 | 对应实际 API 的资源写权限 + 共同根，目标rbac | 整体替换，[]清空；保留未失效关联不产生重复行；重复/未知/跨系统拒绝；事务中重新检查完整K |
| PATCH /api/permissions/{id} | 非空body仅{parentId?:id或null,type?:MENU或BUTTON,enabled?:boolean}；data=更新后PermissionDTO | 对应实际 API 的资源写权限 + 共同根，目标rbac | 部分更新必要字段；显式null表示根；拒绝系统迁移、name/code等未评审字段；树、类型及K检查后整体保存 |
| PATCH /api/api-resources/{id} | body仅{enabled:boolean}，必填；data=更新后ApiDTO | 对应实际 API 的资源写权限 + 共同根，目标rbac | 停用/恢复均保留关联；method/path/systemId只读；影响所有引用，原子检查K |

GET映射以ids为关联真值，items供展示状态/模板。API反向引用可从本系统权限映射构造，T07保持API DTO既定字段，不额外带反向摘要；可由本系统权限树和各节点完整映射构造，不另加通用查询端点。T07应统一DTO字段命名与T05的实际映射约定；本稿沿原型method/path。

### 4.3 关联、树约束与失败

顺序：身份有效 → 当前实际 method+path 对应权限 → 共同根/目标系统范围 → 输入及关联/树规则 → 修改后完整K保护 → 原子提交。前端控件不是授权边界；每次受控请求读取最新用户、角色、权限、API及系统状态。拥有多个角色/多个权限时 API 授权按 OR 合并；某一权限code存在、菜单可见、角色名称或只映射一部分必要API都不能当作完整K。

同屏依赖四个目录读请求，原型全部通过后显示完整编辑器；写请求逐个校验其实际endpoint，不要求操作者同时拥有整个 resources.write 或整个K。K仅作至少一名完整有效维护者防护，不作每位操作者登录/执行单操作的条件。

| 情况 | HTTP / code | 反馈与状态 |
| --- | --- | --- |
| 无身份；当前账号失效 | 401 AUTHENTICATION_REQUIRED / SESSION_INVALID | 不披露资源数据 |
| 根部门成员缺实际目标操作权限 | 403 PERMISSION_DENIED | 根位置不授予权限；先拒绝后不校验/泄露输入资源 |
| 非共同根；请求系统2；直接维护外系统资源 | 403 MANAGEMENT_SCOPE_DENIED | 不泄露范围外资源是否存在 |
| 范围内目标id不存在 | 404 RESOURCE_NOT_FOUND | 无状态改动 |
| ids缺失/非数组/重复/非正安全整数/未知；跨系统关联；未知字段或错误字段类型 | 400 INVALID_ARGUMENT | 不去重、不截断、不部分保存 |
| parentId自指/循环、BUTTON作父、含子菜单改BUTTON、父子不同系统 | 400 INVALID_ARGUMENT | 保留原父级、类型、状态和映射 |
| 权限停用、清空/替换映射、API停用会使最后一名完整维护者失效 | 409 LAST_ADMIN_REQUIRED | 全部撤销，不能只数角色/关联，也不能只看权限code |
| 持久化/约束并发冲突 | 409 DATA_CONFLICT | 整体回滚，重查后再保存；正式实现需返回冲突证据 |
| 未预期故障 | 500 INTERNAL_ERROR | data=null、requestId可追踪，无部分提交；不泄露栈/SQL |

跨系统“关联”是已授权本系统目标上的非法输入，返回400；直接目标或目录在外系统则按scope返回403。unknown关联id按统一checkIds返回400，范围内目标未知返回404。

停用不物理删除，不自动移除角色/权限/API关联；有效授权计算排除停用用户、角色、权限、API或系统。父权限只负责树展示，停用父菜单不级联修改子项或隐含撤销直接授予子按钮的API；子按钮独立启用状态与映射仍按统一有效权限计算。树移动不改变角色的显式permissionIds。

## 5. 角色定义与权限配置

<a id="t08"></a>

### 5.2 逐项契约

| 原型触发 | 方法与路径 | 对应权限及范围 | 请求 | 成功 data / 状态 |
| --- | --- | --- | --- | --- |
| 页面进入、查询、分页 | GET /api/roles | rbac.roles.read + root | q?, enabled?, page?, size? | {items:[{id,code,name,enabled}],total,page,size} / 200 |
| 选择角色、重新读取 | GET /api/roles/{id}/permissions | rbac.roles.read + root | 路径 id | {roleId,ids:[permissionId...]} / 200 |
| 读取选权树（T06拥有契约） | GET /api/permissions/tree | rbac.resources.read + root | systemId={rbacSystemId} | 复用 T06 树，必须能区别 MENU/BUTTON、enabled及父子关系 / 200 |
| 新建角色定义 | POST /api/roles | rbac.roles.write + root | {code,name} | {id,code,name,enabled:true} / 201；权限集合为空 |
| 改名、启用/停用 | PATCH /api/roles/{id} | rbac.roles.write + root | {name?,enabled?}，至少一项 | {id,code,name,enabled} / 200 |
| 确认保存/空选清空 | PUT /api/roles/{id}/permissions | rbac.roles.permissions.write + root | {ids:[...]}；[]清空 | {roleId,ids:[...]} / 200 |

GET 只查询，不附带修改；POST 新建必要定义；PATCH 仅允许更名与状态，不改编码或角色权限；PUT 表达所选权限完整集合，重试相同请求结果相同。未提供 DELETE，不把实体表反推整套 CRUD。

字段与异常逐项：

- code/name 创建时去首尾空白，必填且为1..100字符，与 T02 Role 字段长度一致；name 修改后同样校验。原型在创建后不改code；PATCH 不接受 code/id/permissionIds，空 body或多余字段400。enabled 显式值必须为JSON boolean，不把字符串转换为true。
- POST 编码重复409 DATA_CONFLICT，不能覆盖旧定义。原型按去首尾空白后精确code判重；T09须核对 MySQL 排序规则的大小写/重音判重并统一请求与库约束，不能依靠原型字符串比较冒充DB唯一性验证。
- GET roles/{id}/permissions 返回已保存的直接选择，包括尚未清理的停用项供移除，不补祖先、子按钮或推导有效权限。API响应集合固定id顺序；角色状态与权限有效性在各自DTO明确。
- PUT 不接受未知或停用权限、其他系统权限或不可用系统。校验整组后才替换，不先删后逐个尝试。角色停用时可以配置本轮有效权限；该角色仍无授权效果，直到有效启用。菜单即使无API也可被直接选择，但不据此授予接口。
- 修改权限/启停若使写后完整维护者为0则409 LAST_ADMIN_REQUIRED；任何关联写入及定义状态一起回滚。500 INTERNAL_ERROR为统一失败；不报告部分成功。
- role6/per99/system2 是故意构造的外系统非法/缺项演示夹具，不是课程契约、合法迁移或真实多系统角色设计。真实系统不能借本页PUT静默删掉其他系统既有授权；当前仅接受rbac启用权限的方案边界若需扩展，先由T14及受影响角色契约评审，不在本票增加保留分区、系统角色模型或课程接口。

角色编码创建后不可修改。列表 q 去首尾空白后，按 name/code 不区分大小写包含搜索，最多100字符；enabled 可省略或 true/false；page/size/total 沿 2.1。角色定义无 departmentId、继承或管理员属性。权限及状态变更的后续请求立即重算，不沿用登录时权限快照。

## 6. 人员角色、停用与调动

<a id="t10"></a>

### 6.2 逐项契约

| 原型触发 | 方法与路径 | 对应权限及范围 | 请求 | 成功 data / 状态 |
| --- | --- | --- | --- | --- |
| T04 进入及范围内人员选择 | GET /api/departments/tree | rbac.users.read + 本部门及下级 | 无 | T04 部门树 DTO / 200 |
| T04 搜索分页、人员跳转 | GET /api/users | rbac.users.read + 本部门及下级 | q?,departmentId?,enabled?,page?,size? | T04 {items,total,page,size} / 200 |
| 选人、重新读取、直接 ID 请求 | GET /api/users/{id}/roles | rbac.users.read + 目标人员范围 | 路径 id | {user:{id,username,name,departmentId,enabled,roleIds},roles:[{id,code,name,enabled}]} / 200 |
| 候选角色、候选搜索 | GET /api/users/{id}/assignable-roles | rbac.users.roles.write + 目标人员范围 | q?,page?,size? | {items:[{id,code,name,enabled:true}],total,page,size} / 200 |
| 保存分配/撤销/空选清空 | PUT /api/users/{id}/roles | rbac.users.roles.write + 目标人员范围及自保护 | {ids:[roleId...]} | {id,username,name,departmentId,enabled,roleIds} / 200 |
| 确认停用 | PATCH /api/users/{id}/status | rbac.users.disable + 目标人员范围及全局管理员保护 | {enabled:false} | {id,username,name,departmentId,enabled:false,roleIds} / 200 |
| 确认跨部门调动 | PUT /api/users/{id}/department | rbac.users.department.write + 原/目标双范围及自保护 | {departmentId} | {id,username,name,departmentId,enabled,roleIds} / 200 |

GET 无写入；PUT 表示角色完整集合/单一部门归属的替换；PATCH 只修改启用状态且本票仅允许 false。重复相同 PUT 不累加关联；对已停用用户再次提交 enabled:false 仍为同一状态。不提供 DELETE、人员创建/恢复、完整组织 CRUD 或单独逐角色增删接口。

正式 GET /api/users/{id}/roles 应保留当前全部已保存角色的元数据及 ID，包括停用或现已不可分配项供清理；它不把祖先菜单、子按钮或推导权限加入 roleIds。候选接口是此员工操作的候选，不是部门获得了全局角色目录权限。

### 6.3 候选及整体替换

- 有效权限沿启用用户 → 启用角色 → 启用权限 → 同系统启用 API、启用系统计算。比较候选角色的有效权限集合与操作者本人的有效权限集合；可以汇总本人多个角色，不能只检查角色名称或 code。
- 候选接口返回启用且满足子集的角色；被停用或有效权限超出本人者不返回。外系统模拟角色6/权限99是非法夹具，不是已确认的课程系统或实际角色迁移方案。
- 保存时重新读取当前操作者权限、目标部门和每个角色状态/权限，重新验证整组 ids；客户端提交过期候选、超权或已停用角色统一403 MANAGEMENT_SCOPE_DENIED；未知关联ID400。
- 整体替换包括移除未选角色；[] 明确清空全部。当前旧角色已不再可分配时可移除，不能借“保留旧值”继续提交不满足子集的角色。本方案不增加自动过滤、静默保留或部分成功。
- 先校验整组关系及保护条件再发布新集合。正式实现必须在同一声明式写事务内完成校验和修改，失败回滚全部用户角色；不能先删后逐条尝试保存。
- 清空普通人员角色后本人身份仍由既有基础认证负责，后续管理请求因无权限403；再次管理登录拒绝。不以“角色对象还存在”或“登录过”继续放行。

### 6.4 停用、双范围调动及失败

- 停用使用户有效权限立即失效；保留角色关系和部门，后续请求401 SESSION_INVALID，再次登录按既有流程统一 LOGIN_FAILED。仅缺管理权限再次登录为 MANAGEMENT_ACCESS_DENIED，与 T04 定义对齐；不暴露未知账号/错误口令/停用的细分认证错误。
- 调动先检查当前人员原部门，再检查提交目标部门；两个部门都必须在本人及下级范围。范围外任何方向拒绝，失败保留原部门、enabled及所有角色；目标不存在对非root统一范围拒绝，对已获root范围者404。
- 调动不授予角色或 API 权限，但会使该用户后续人员管理范围随其新部门变化。有效全局管理员移出共同根后不再计入完整管理员；不能借调动绕过最后管理员保护。
- 部门管理员自己的角色/部门变更403，不通过当前候选是否为空绕过；修改有效全局管理员403。共同根可修改自己，但仍受写后至少保留一名完整 K 用户约束。
- 最后一名有效全局管理员不能被停用、撤掉必要角色或移出已核实共同根。冲突409 LAST_ADMIN_REQUIRED，整个状态不变。不能把不同用户的零散能力拼成一个管理员。

K 与 T06/T08 共用 [2.3](#common) 的完整方法/路径集合，可汇总本人多角色、多权限 OR 映射。每名有效共同根用户各自满足完整 K，按 distinct 用户计数，写后至少保留一人；日志、停人及调动不加入 K。

正式 T07/T09/T11 的相关写入口必须共用事务保护与稳定锁顺序，验证撤权、停用、移出根及跨入口并发组合。原型单线程复制发布只演示失败原子，不证明真实事务、数据库锁或并发成立。

## 错误逐项审核

| 情况 | HTTP / code | 数据 |
| --- | --- | --- |
| 无身份、账号停用/失效 | 401 AUTHENTICATION_REQUIRED / SESSION_INVALID | null |
| 缺具体 method/path 有效授权 | 403 PERMISSION_DENIED | null |
| 目标越界、双范围失败、自己授权/部门、全局管理员、角色越权/停用 | 403 MANAGEMENT_SCOPE_DENIED | null |
| 主 ID、关联 ids、分页、q、字段/类型非法 | 400 INVALID_ARGUMENT | null |
| 已获范围内未知人员或目标部门 | 404 RESOURCE_NOT_FOUND | null |
| 写后无完整 K 管理员 | 409 LAST_ADMIN_REQUIRED | null |
| 并发/约束冲突的统一失败 | 409 DATA_CONFLICT | null |
| 服务/模拟下一请求故障 | 500 INTERNAL_ERROR | null |

角色分配、停用及调动失败时，原归属、状态和授权保持不变；查询失败显示错误并提供重试，已保存授权保留。日志沿 T05 的真实 AOP/过滤器记录最小结果，单次去重且排除请求体。

<a id="acceptance"></a>

## 7. 原型场景与后续验收案例

下表给出真实 HTTP/JPA 验收输入与预期，也可先用模拟账号及评审工具复核。每个写失败案例比较前后完整归属、状态和关联；正式接口的验证证据由实现票提供。

| 案例 | 原型操作与输入 | 后续必须验证的结果 |
| --- | --- | --- |
| A04-1 | 模拟工号demo.root和其手机号19900000001分别使用demo；demo.department及19900000003分别登录；工号保留首尾空白再试 | 同一用户的两种入口均成功，返回相同id/username/部门，后续有效权限与范围一致；工号按字符串等值匹配且仅trim首尾，不另生成编号 |
| A04-1b | 普通员工demo.employee/19900000004；仅外系统权限demo.foreign/19900000005；停用demo.person10/19900000015；未知账号、错误口令、空值、超长；两用户跨字段同值 | 前两类两种入口均403 MANAGEMENT_ACCESS_DENIED；停用/未知/错误口令/歧义均统一401 LOGIN_FAILED，必填/长度400；失败无成功身份，日志按已确定用户记工号、不复制手机号输入 |
| A04-1c | 选择“无手机号”“同一用户两字段同值”“跨用户匹配歧义”“工号含前导零”；T03另验证导入重复工号/手机号/跨用户碰撞 | 页面可直接复核缺手机号仍工号成功、同用户两字段只算一人、跨用户401及前导零保留；导入冲突由T03拒绝，不依赖标识注册表/触发器或两列同步，不误合并用户或覆盖授权 |
| A04-2 | demo.department 查询工程部/道路组，组合 q+enabled、翻页、跳越末页；查兄弟财务部 | 范围内 items/total 同谓词；0结果total=0；越末页空items保留total；越界403且不返回兄弟信息 |
| A04-3 | demo.logs 进入日志；部门管理员直接日志请求；根账号撤去日志API后重试 | 仅第一项成功且无完整K要求；分别因范围/具体权限403；每批重新鉴权 |
| A04-4 | 日志limit=1继续、读取后新记录、改筛选带旧cursor、伪造cursor、无匹配username | 同一snapshot total稳定、不重不漏；不一致/损坏400 INVALID_CURSOR；空items/total=0；无归档字段 |
| A04-5 | 注入查询500后重试；本人查询只保留身份而撤去管理权限 | 错误有重试；本人不需要users.read；管理请求403、再次登录403 MANAGEMENT_ACCESS_DENIED |
| A06-1 | 树选节点、API关键词搜索/分页、无结果、选择跨页保存 | 方法/模板/系统明确；当前全部ids不被当前页截断；PUT整体替换，重试不重复 |
| A06-2 | 复用示例给第二权限映射同一树API；解除原引用 | 任一有效映射可放行；剩余路径覆盖完整K时不误报最后管理员；停用同API仍影响全部引用 |
| A06-3 | 跨系统关联、重复ids、树循环、按钮作为父、直接外系统目录 | 前四400 INVALID_ARGUMENT、目录403 MANAGEMENT_SCOPE_DENIED；状态和关联全不变 |
| A06-4 | 普通非关键映射清空/保存；关键API停用；注入保存500 | []可成功且需确认；最后维护者409；500无部分提交且可重试 |
| A08-1 | 搜索角色/分页/空列表；新建空权限角色；重复code | {items,total,page,size}；201且权限空；重复409 DATA_CONFLICT，原定义不覆盖 |
| A08-2 | 清空草稿，仅选子按钮，检查导航再保存；[]确认及取消 | 子按钮独立、祖先只显示不进ids；取消无写；确认整体替换；不静默保留停用/非法项 |
| A08-3 | 普通角色改名、停用/启用；最后维护角色撤权/停用；注入500重试 | 名称不改权限；停用保留配置且下请求失效；关键写409整体回滚；500无部分保存 |
| A08-4 | 根角色查询者缺资源树API；部门账号读/写目录；实际单endpoint权限 | 独立读取树失败；目录/写各自权限及root限制；单操作不要求完整K，不能由角色名放行 |
| A10-1 | T04跳员工；部门账号读取当前与可分配角色，按名称/编码搜索 | 当前含停用/不可再分配项；候选只启用且有效权限为本人子集，同谓词total分页；不返回全局角色目录 |
| A10-2 | 分配部门角色并确认，撤销/[]，重复/未知/超权/停用角色，注入500 | 整体替换、空选撤销；非法400/越权403无部分保存；后续请求重算、失去管理能力再次登录拒绝 |
| A10-3 | 工程部管理员把下级员工从道路组调项目组；调财务部；反向拉入外部员工 | 首项成功且角色保留；其余403，原部门/角色/状态均不变；原与目标都校验 |
| A10-4 | 部门管理员改自己角色/部门、修改有效全局管理员；停用普通人员 | 自己及全局管理员403；合法停用保留关联，后续401 SESSION_INVALID，再次登录统一LOGIN_FAILED |
| A10-5 | 根账号清空最后管理员角色、停用该人、移出根；有第二名完整维护者再操作 | 无备份分别409；备份必须是同一有效根用户各自完整K，多用户拼接无效 |
| AX-1 | T06停用日志API或解除映射，T08改日志角色，T10更换该用户角色，再T04查询 | 四页沿同一实际method/path、有效性和root范围；无缓存授权或独立日志资格 |
| AX-2 | 两管理员同时撤权/停用/移出根，跨T07/T09/T11入口 | 同一保护点/锁序，至多提交不会导致K清零的写；失败完整回滚，须以真实HTTP/数据库锁与并发验证，内存原型不作证明 |
