# RBAC 四组原型与 API 契约

> 2026-11-07；Refs #5、Refs #7、Refs #9、Refs #11。本轮原型与契约准备已获用户确认完成，见第8节；不代表正式前端、后端、数据库或双系统验收。

## 1. 入口、依据与前置核对

原型入口为 [prototype/index.html](prototype/index.html)。下载本分支完整目录后双击该文件，Edge/Chrome 可直接打开；不需要构建、依赖安装、登录真实系统或连接后端。所有账号、部门、角色和资源 ID 都是虚构演示数据，口令仅为 `demo`，仅保存在内存；刷新或“重置全部模拟数据”清空改动。仓库网页只显示 HTML 源码，不能当作运行原型。每个业务页都有实际查询或保存交互，评审工具可注入下一请求失败。

需求顺序为[检查记录及复核说明](RBAC检查记录复核说明.md)与本轮用户确认、[原版执行计划](RBAC整改计划与Chen-ER图.md)、[任务清单](RBAC整改任务清单.md)。旧文档与产品源码仅用于确定复用点和差异，不是新增需求。老师明确要求先原型后 API、分部门搜索分页、菜单/按钮关联 API、JPA/AOP 与分层；管理端登录拒绝和最小日志是用户补充；全局共享角色、共同根维护、整体替换、分页默认等是已记录的方案默认。

本轮开工核对：GitHub 四票正文、全部评论和原生 `dependencies/blocked_by` 已读取，均 open、无评论、未指派，原生前置均只有 closed #2。PR #17 已合入，代理评审记录为 `pullrequestreview-5731499420`，原版计划 5.3 是前置结论；并非老师/真人组员验收。远端共同基线核对为 `c2e3adad3ce72934bc1000c7b9143d3131ab0c3e`。本轮没有修改依赖、替组员自动认领或执行下游。

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

下表是本轮已确认的契约目录，不代表端点已经实现。T05 只初始化自己需要的查询、最小日志及 K 恢复基线；后续票按已通过契约接实际 API，不批量生成未评审 Controller。

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

本轮目录为后续初始化提供经用户确认的契约清单，不证明尚未实现的 API 可调用。T05 负责真实账号、授权基线及第一个真实链路；后续写票分别实现自己的已评审操作。T03 提供账号与根部门来源，不能为满足 K 编造账号或移动原人员，也不因此给四张原型票新增硬依赖。

<a id="shared-modules"></a>

### 2.4 后续共用模块边界

| 归属 | 责任与复用边界 |
| --- | --- |
| T05：身份、统一有效权限、AOP | 复用 AuthService 的口令/状态与现有响应流程，可信身份衔接新 users；AOP 识别实际 HTTP 方法和 Spring 模板；本人查询是身份入口；后续票复用同一权限计算，不另立资格表 |
| T05：组织范围、查询与最小日志 | 先 scope 再 filter/count/page；JPA 迁移 Audit* 最小记录和受保护查询，复用 requestId；沿旧敏感字段排除边界，清理归档扩展；不让接口查询依赖旧模拟鉴权 |
| T07：资源 | 复用 T02 ApplicationSystem/Permission/ApiResource 及 Repository；树、同系统映射和必要停用在 Service 写事务中验证；不自定义另一套授权/保护计数 |
| T09：角色 | 角色定义和角色权限关联；角色全局共享，不引入继承、版本、审批；T10 的候选使用同一有效权限定义 |
| T11：人员变更 | 当前角色、候选、替换、停用、双范围调动；不提供直接用户授权；保留 T03 人员来源与组织归属 |
| T07/T09/T11 共用保护 | 修改后完整 K、distinct 计数、稳定锁点、失败回滚；任何影响 K 的入口都必须覆盖，不能只防人员停用 |
| DTO / 映射 / 事务 | Controller 只管输入输出，Service 管规则与读写声明式事务，Repository 用 JPA；CloneFactory 课程来源与具体用法仍由 T05 核对，不能以本轮 DTO 表或现有 ObjectMapper 宣称完成 |

原型源码仅用于评审，不能复制其 actorId、固定根 ID、模拟口令或全量内存数组到正式后端。权限、角色与 API 的 DTO 统一沿下述字段命名；数据库列名与 HTTP 字段的转换由正式切片落实。

<a id="t04"></a>

## 3. T04 / #5：准入、人员与最小日志

原型源码 [t04.js](prototype/t04.js)。从登录页选择模拟账号，进入部门人员、本人信息或鉴权日志；每次查询显示模拟响应，可直接复核状态、字段和分页。先完成这些交互，再形成下表的逐项契约；本轮确认见第8节。

### 3.1 逐项契约

| 交互与资源 | HTTP / 参数 / data | 权限与范围 | 技术结论 |
|---|---|---|---|
| 登录 | `POST /api/auth/login`，JSON `{account,password}`；account 非空、trim 后唯一 username，最多100；password 非空最多200，不 trim。沿既有 `Responses.Login` 的 `{token,user,expiresIn}` 外形，原型只模拟 `{user:{id,username,name,departmentId,enabled}}` 子集。 | 匿名；既有有效性/BCrypt 口令通过后，复用统一有效权限计算，至少命中一项本系统实际管理 API 才成功。仅父菜单、空角色、普通员工、仅外系统权限均不足；不要求完整 K 或根部门。 | 方法/路径/参数通过静态技术核对；工号/手机号别名本轮不支持，明确只保留 username。保留旧认证运行支撑外形，不定义 token 格式、期限、签发/刷新/退出机制。身份运行衔接交 T05；本轮用户确认采用。 |
| 登录失败 | 账号未知、口令错、停用统一 `401 LOGIN_FAILED` / “账号或密码错误”；账号/口令已正确但无有效管理权限 `403 MANAGEMENT_ACCESS_DENIED` / “该账号没有本权限系统的有效管理权限”。必填/长度错误400。 | 任何失败均无成功登录状态；不能通过不同401提示枚举未知/停用账号。仅在已正确身份校验后区分无管理权限。 | 复用旧 `AuthService` 实际 `LOGIN_FAILED`，不新增 INVALID_CREDENTIALS。不新增 is_admin、资格表、凭证模型、认证接口。原型实际演示两种拒绝与成功；本轮用户确认采用。 |
| 本人身份 | `GET /api/me`；data 收敛为 `{user:{id,username,name,departmentId,enabled}}`。 | 仅有效身份，无 `users.read` 前置，不接受客户端指定另一个 userId。角色已撤销时本人查询仍可用，管理请求及再次登录另行拒绝。 | 保留路径；移除旧 managementGrants/canManage 的双轨管理模型，部门/启用字段与新模型对应。字段收敛方案本轮由用户确认采用。 |
| 本人权限 | `GET /api/me/permissions`；data 为本人本系统有效权限数组，元素 `{id,systemId,parentId,type,code,name}`，按 system.code=rbac 对应的真实 systemId 过滤；id=1 仅为原型模拟值，本轮不扩成跨系统业务查询。 | 仅有效身份；用统一有效计算。为导航补出的祖先不得混入实际授予 IDs，不隐含子按钮或 API。 | 保留路径；不要求人员管理权限。旧 `?app=` 业务目录含义本轮收口为本系统，不另加资格接口；本轮用户确认采用。 |
| 部门导航 | `GET /api/departments/tree`；data 扁平树节点数组 `{id,parentId,name}`，客户端依据 parentId 呈现。响应中的可见范围根 parentId 投影为 null，不暴露范围外父节点ID；实体实际归属不变，不返回范围外节点、名字或兄弟列表。 | `rbac.users.read` 映射该 GET；只本人部门及后代。共同根仅示例 id=1，真实根必须来自 T03 核对，不能按名称或任意空 parent 判断。 | 读资源且无业务写入；部门树不扩成组织 CRUD。原型工程部只显示工程部、道路组、项目组，兄弟财务部不出现；本轮用户确认采用。 |
| 唯一人员列表 | `GET /api/users?q=&departmentId=&enabled=&page=0&size=20`；q 姓名或用户名包含搜索、trim、忽略大小写，最多100；enabled 可省略或 true/false；departmentId 可省略（本人部门及后代）或正 ID（所选部门及后代）；size 1—100。data `{items,total,page,size}`，item `{id,username,name,departmentId,departmentName,enabled}`。 | `rbac.users.read` 映射该 GET；服务端先限制本人范围，再部门+q+enabled，使用相同谓词计数和分页。姓名不是唯一标识，行操作用用户id；不把电话/口令哈希等无场景字段整包返回。 | 固定id升序；越末页 `items:[]` 保留 total/page/size；0结果total=0。原型显式size=5，正式默认20。负页/非法size/非法状态400，具体范围外部门403，root范围内未知部门404；本轮用户确认采用。 |
| 人员后续动作 | 行按钮把已选 userId 交 T10 原型；`GET /api/users/{id}/roles` 等契约由 T10 编制，T04不重复创建。 | 可管理范围与实际角色查询/写入权限继续逐请求检查。 | 只接入口，不实现下游业务。 |
| 最小日志查询 | `GET /api/authorization-logs`；沿旧 `userId,username,appCode,allowed,from,to,event,cursor,limit`。username 精确≤100；appCode精确≤191；event精确≤32；userId正安全整数；allowed true/false；from/to有效UTC ISO-8601，from含/to不含，from<to；cursor≤4096；limit默认100、1—500。 | `rbac.logs.read` 映射该 GET，且共同根范围；完整K不是前置。部门管理员即使有该API映射也因范围403；根用户没有日志API则具体权限403。 | 复用原 `AuditService` 游标，不改offset，不恢复 storage/archive/export；新增同一snapshot筛选集合total，本轮用户确认采用。 |
| 日志响应与记录 | data `{items,nextCursor,hasMore,snapshotId,total}`；items保留 `{id,occurredAt,userId,username,appCode,permissionCode?,allowed,requestId,reason,eventType,httpMethod,requestPath,httpStatus}`。未知身份的userId可null；原型匿名username为空。删除 `archivedAt/archivedBy` 对外字段。 | 服务端ID降序，首次快照为当前最大ID，只查询 id≤snapshotId；继续页 id<上页末条id。筛选/limit绑定游标，变化后清cursor从首批查询；伪造/损坏/筛选不一致 `400 INVALID_CURSOR`。每页重新校验权限/根范围。 | total为snapshot内同一筛选集合总数，并非当前页条数，也不因读取日志的后续技术记录增长；到底nextCursor=null/hasMore=false。UI支持逐批累计和刷新快照。 |

### 3.2 复用与旧入口收口

- `ApiController` 现有 `GET /api/users` 返回数组且默认size30，`GET /api/users/page` 返回统一分页，`GET /api/users/filters` 返回旧公司/部门选项。新契约唯一列表采用 `/api/users` + 分页外形，导航采用 `/api/departments/tree`；T05迁移同切片，T12负责旧 `/page` `/filters`、Users.vue及说明/测试调用收口。不在T04改源码，不双轨保留两个正式列表。
- owning证据：`AuthService.java` 的 `BCryptPasswordEncoder.matches`、统一 LOGIN_FAILED 和旧Response.Login；`ApiResponse.java` / `ApiResponseAdvice.java` / `ApiExceptionHandler.java` 的统一外壳；`ApplicationService.userPage` 及 `ApplicationRepository` 的已有搜索/总数分页行为；`AuditController/AuditService/AuditRepository/AuditDtos/RequestAuditFilter/AuditRecorder` 的既有最小日志和快照游标。
- 日志事件保留登录成功/失败、匿名、请求拒绝及真实AUTHORIZATION判定；原型模拟每次查询的单条技术记录，绝不将模拟请求冒称真实AOP结果。真实方法/服务端匹配路径、结果/原因/requestId等最小字段须随T05真实AOP接入；不存口令、token、请求体、查询串，失败文本用安全错误码。
- Service 查询采用只读声明式事务；JPA与CloneFactory真实调用、认证与AOP、数据库日志失败处理及单次去重由T05实现验证。本票不因文档写出技术名词而标为完成。

<a id="t06"></a>

## 4. T06 / #7：资源目录与 API 映射

原型源码 [t06.js](prototype/t06.js)。树、系统归属、方法与路径模板、完整已选映射、候选分页和非法用例均可操作。code/name/systemId 与 API method/path 只读；必要维护仅权限 parentId/type/enabled、API enabled 和映射替换。

### 4.1 原型场景

| 可操作原型 |对应契约与核对结果 |
| --- | --- |
| 重新查询系统、树、API 与所选映射 | 仅 GET；读目录同样需要共同根范围及相应实际 API 权限，不以根成员身份授予权限 |
| 选择节点、搜索方法/路径、上一/下一页 | 查询无写入；API 按 id 排序、过滤后总数与分页同源，选择跨页保留 |
| 勾选候选并保存映射 | PUT 整体替换 {ids}；空数组清空；缺失、重复、未知、非法类型不静默去重；失败原子 |
| 清空选择、恢复已保存选择 | 本地草稿交互；仅“保存映射”写入；切换节点重新载入该节点已保存映射 |
| 载入 API 复用示例 → 保存 | 将 GET /api/permissions/tree 同时映射到 resources.read 与 resources.write，反向引用显示两个权限。用户从任一有效权限命中该 API 即可访问，范围约束仍另行执行 |
| 保存节点父级/类型/启用状态 | PATCH 仅必要字段 parentId/type/enabled，覆盖树维护及停用/恢复；系统/code/name只读，不增新建/删除或整套 CRUD |
| 停用/启用 API | PATCH 仅 enabled，保留映射并明确全部引用受影响；method/path只读，无路径注册或系统停用场景 |
| 非法用例与确认 | 树循环、按钮子节点、跨系统、重复、权限/范围不足和关键 API 保护均走同一模拟入口；撤销映射和停用有确认 |
| 下一请求模拟失败 | 内存运行时模拟500；保存原子，查询显示失败及重试，不能视为真实网络/事务验证 |

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

GET映射以ids为关联真值，items供展示状态/模板。API反向引用可从本系统权限映射构造，正式DTO是否带反向权限摘要交T07实现评审确认；不得为此另加通用查询端点。T07应统一DTO字段命名与T05的实际映射约定；本稿沿原型method/path。

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

<a id="t08"></a>

## 5. T08 / #9：角色定义与选权

原型源码 [t08.js](prototype/t08.js)。角色全局共享，先通过列表选择角色，再在独立权限树核对直接选择与导航补全。

### 5.1 原型场景

1. 左侧按名称/编码、启用状态查询共享角色，按 id 升序、每页 5 项显示总数与空结果。选择角色后独立读取其权限 ID。
2. 必要定义维护仅新建空权限角色、改名、启用/停用；新建需要编码与名称，默认启用且 ids=[]。编码创建后不可改。不提供删除、继承、版本、审批或完整 CRUD。
3. 权限树单独要求资源目录查询权限。各 checkbox 独立，无父子级联。选子按钮时祖先可补入导航预览，但不加入请求 ids；父菜单本身不产生子按钮 API 权限。
4. 保存前展示所选 ids 并确认整体替换；空选清空也需确认。清空和勾选先改本地草稿；确认前不写已保存关系。取消返回草稿；重新读取明确放弃草稿。
5. 改名/启停也有确认，明确共享影响。停用保留定义及权限关联，但该角色不再贡献有效权限；启用以当前有效状态重算。
6. 原型每个模拟请求按实际方法/服务端模板调用 guard，写走共享 atomic 副本发布。500、400、403或409不产生部分保存；模拟失败后草稿可直接重试。
7. 最后一名完整维护者角色不能停用或撤掉关键权限。判定来自有效的用户→角色→权限→同系统 API/系统链，不读取角色名称。多用户各自零散能力不能拼成一名完整管理员；同一用户多角色可以汇总。

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

<a id="t10"></a>

## 6. T10 / #11：人员角色、停用与调动

原型源码 [t10.js](prototype/t10.js)。T04 人员行进入本页；当前角色、候选、草稿与提交后结果可分别检查。无独立有效权限来源页面；使用“下一请求”模拟解释角色变更效果，不增加代他人调用正式接口。

### 6.1 原型场景

1. T04 人员行可携带 selectedUserId 进入 T10；T10 可选择本人及下级人员，读取当前角色、回到 T04 人员搜索分页。直接输入人员 ID 的评审入口能构造自己、同级其他部门、全局维护者或未知目标的请求。
2. 已保存角色与待保存选择分开。候选角色只通过 GET /api/users/{id}/assignable-roles 读取，按目标人员范围及本人最新有效权限计算；部门管理员不请求全局角色/权限目录。候选支持名称/编码搜索及分页契约；当前小样本 UI 一次显示默认页。
3. 候选必须启用，角色贡献的有效权限集合须为本人有效权限集合的子集。角色名称、岗位或是否非空不参与判断。无有效 API 的菜单角色可以是空有效权限集，不使人员获得管理准入。
4. 选择只改草稿，保存前展示角色 IDs 的整体替换和受影响人员。[] 清空仍须确认；可取消、恢复已保存值；停用或当前超权的旧角色明确展示并允许移除，不能原样重授。
5. 人员停用与跨部门调动各自确认。停用保留部门及角色关系，不提供人员恢复；调动保留角色，原部门和目标部门必须都在操作者本部门及后代。不会从本部门扩大到所属公司的其他部门。
6. 部门管理员不能改自己的角色或部门，也不能修改有效全局管理员。用户停用未额外增加“不能停用自己”的规则；如停用自己，后续请求为 SESSION_INVALID。共同根人员也必须有当前具体权限。
7. 所有写入在 shared atomic 草稿检查后一次发布；400/403/404/409/500 失败不改变已保存状态。模拟失败后草稿及确认仍可重试；首次读取失败有明确错误与重试入口。
8. 变更后的小型模拟访问面板展示下一次人员查询的权限/范围结果及再次管理登录准入。它只是评审用状态解释，不增加代他人调用接口、用户直接权限关联或完整有效权限来源页；最终有效权限列表页本票不新增。
9. 每个操作按实际 method/path template 调用 guard；写操作不因显示过候选或确认过草稿而跳过重新鉴权。T08 改角色权限后，T10 候选及保存重新计算；T10 改人员角色后，T04 再次登录与管理请求按新状态判定。

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

K 与 T06/T08 共享，按同一有效用户的实际方法/路径能力判定：users.read、users.roles.write、resources.read、resources.write、roles.read、roles.write、roles.permissions.write 的全部必要已冻结 API；可通过本人多个角色及多个权限 OR 映射汇总。distinct 启用用户、直属共同根且每人各自完整 K，写后至少保留1名。日志、停人、调动不新增为 K 必要能力。

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

角色分配、停用、调动失败均不得改变原归属、状态或授权；查询失败明确错误并可重试，不能当作空集合清空授权。日志由后续统一真实 AOP/过滤器复用最小记录，避免重复或记录请求体，不另加本票审计平台。

<a id="acceptance"></a>

## 7. 原型场景与后续验收案例

以下是后续真实 HTTP/JPA 验收的输入与预期，也可用原型的模拟账号、评审工具及非法用例先复核；原型检查不等于正式接口通过。每个写失败案例都要比较变更前后完整归属、状态和关联，而不是只看提示。

| 案例 | 原型操作与输入 | 后续必须验证的结果 |
| --- | --- | --- |
| A04-1 | demo.root/demo；demo.employee/demo；demo.foreign/demo；错误口令或停用账号 | 分别成功、403 MANAGEMENT_ACCESS_DENIED、403 MANAGEMENT_ACCESS_DENIED、统一401 LOGIN_FAILED；失败无成功身份 |
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
| AX-2 | 两管理员同时撤权/停用/移出根，跨T07/T09/T11入口 | 同一保护点/锁序，至多提交不会导致K清零的写；失败完整回滚，本轮内存尚未验证 |

<a id="review"></a>

## 8. 技术评审证据与范围

2026-11-07 先制作各票原型，再逐项核对上述REST契约；主代理统一检查共享目录、字段、错误、候选及K。技术评审不能代替真实数据、正式界面或老师验收；本轮准备产物随后获用户确认，记录如下。

各票处理函数自检：T04 120项、T06 27项、T08 17项、T10 66项及修复回归7项；共享模型12项及独立endpoint/OR覆盖检查通过。最终集成b6fad59上再次执行均通过；五个JS文件语法、全部变更diff空白检查通过。自检脚本和临时报告保留在Git外，不引入仓库测试框架。

独立 code-review 以c2e3adad为固定点，先审a5990b0，再复核至b6fad59；两轴分别记录：

| 评审轴 | 首次发现 | 修复及独立复核 |
| --- | --- | --- |
| Standards | 明文违规0；1项P3 possible Duplicated Code（判断项） | 删除T10未使用的人员/部门查询副本及导出；原P3关闭，增量明文违规0、判断项0 |
| Spec | 1项P2：上述副本DTO偏离共同查询契约 | 统一复用T04实际查询；可见范围根parentId:null，人员含departmentName且不附roleIds；原P2关闭，增量无新增确定问题 |

修复提交bc81877，合入b6fad59；T04浏览器另发现失败后账号被默认维护者覆盖，以7c7ab1e保留失败账号并沿现有escape渲染。两个原评审代理分别复跑120/66/7项检查并确认关闭各自发现。原问题属于未使用模拟入口的一致性问题，不宣称真实接口或当前页面泄露。

主代理通过本机HTTP预览实际操作：

| 原型 | 浏览器已观察的交互与反馈 |
| --- | --- |
| T04 | 普通员工准入403、错口令401、失败账号保留后正确重试；部门树/11人总数/翻页/无结果/范围外403；查询500重试；日志limit=1继续、稳定total、空结果及损坏cursor400；无完整K的根日志账号可查询 |
| T06 | 菜单/按钮树、系统/方法/模板；跨系统/重复/循环400；同API双引用保存；最后维护API停用409；写500后重试与无API空态 |
| T08 | 角色搜索空态；仅选子按钮而祖先不入ids；确认保存、500重试、空选确认清空；最后维护角色撤权409 |
| T10 | T04跳转、当前角色/候选搜索空态；分配500后重试、下一请求重新获权；空选撤销后请求/再次准入拒绝；部门内调动成功、调财务及读财务人员403；改自己403；普通人员停用及最后管理员停用409 |

另检查部门账号访问日志/资源/角色的无权反馈；具备实际API但缺root的独立范围拒绝由处理函数自检覆盖，未将其冒称为额外浏览器步骤。1440/768/390像素浏览器视口抽查四页，文档宽度未超过视口；窄屏表格折行显示。Tab可从查询文本框移至状态选择，Enter可提交查询；本次未做完整无障碍审计或真实手机验收。最终浏览器控制台未见error/warn。截图已在工具回显核对；只读沙箱阻止文件落盘，未提供持久化截图文件。临时预览已停止，视口已恢复。

以上均为模拟原型与静态契约证据；未运行真实HTTP、JPA、AOP、数据库事务/锁并发、正式前端或双系统验收。Issue验收不因代理技术复核自动勾选；本轮由以下用户确认授权勾选并标为完成。

**用户确认（2026-11-07）：**用户要求查看实际效果及验收方法后，已打开本机可操作原型，并提供T04/T06/T08/T10的操作与预期反馈路线。用户随后明确表示“应该可以了，更新推送，对应的标为完成”。据此接受本轮四组原型/API契约准备产物及当前方案选择，更新任务清单与四张Issue为完成。记录不声称用户逐项亲手运行了全部案例，也不冒充老师或其他组员签字。用户确认的原型代码为ba0251d；本次收尾仅更新文档和票状态。PR #19继续保持草稿、未合共同基线；真实账号与课程技术等后续缺项仍保留。

<a id="decisions"></a>

## 9. 已确认选择、后续缺项与开工条件

| 确认项/后续缺项 | 本轮具体选择 | 责任与边界 |
| --- | --- | --- |
| 登录账号和本人DTO | 只支持唯一username，不保留工号/手机号别名；沿account/password与既有Login外形；本人DTO按3.1收敛 | 本轮用户确认G3取舍；T05落实可信身份运行衔接，不设计新令牌机制 |
| 日志查询 | 沿旧cursor/limit快照、增加同快照total、去对外归档字段；root+日志API | 本轮用户确认；T05验证最小记录/JPA与一次去重；旧历史用户ID不能误接新用户 |
| 维护范围/DTO | T06只改必要parentId/type/enabled与映射；T08空权限新建/改名/启停/整体选权；T10候选子集、旧超权角色须移除、无人员恢复 | 本轮用户接受现有路由、字段与失败语义；不扩CRUD；反向引用正式摘要可由T07在既有DTO内确认 |
| 完整K、共享角色与保护 | 2.3精确API集合、同用户多角色汇总、distinct根用户、写后至少一人 | 本轮用户确认的方案选择，不是老师逐项规定；变更须统一原版计划及T07/T09/T11 |
| 数据与初始化 | 真实用户名/工号/哈希/状态来源、共同根、维护账号及归属仍缺 | T03/T05落实，不使用模拟demo.root作为默认管理员或从岗位推权；不为原型新增T03硬依赖 |
| 课程技术/多系统 | CloneFactory指定来源仍缺；真实跨系统角色与课程API未评审；MySQL编码collation待核对 | T05核实课程映射用法，T09核对判重；T14/T15需另行确认，不把非法fixture当课程接口 |

四票的原型/契约评审条件已由用户确认完成。T05仍需要T03完成并取得已评审T04产物；T07/T09/T11仍分别需要T05与T06/T08/T10对应契约。产物仍在草稿PR #19，后续从共同基线开工前须先确认其已合入或经协作取得，关闭Issue本身不能代替证据；不能宣称全部实现票可直接开工。真实数据与课程技术等缺项按表交后续票核实。正式前端、真实后端及双系统验收继续暂缓或由相应票落实；本轮仅依用户最新授权完成四票状态，不合共同基线、不ready、不执行下游。
