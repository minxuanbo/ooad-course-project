# API业务约束与接口阅读说明

版本1.5。接口字段、方法、路径、必填、分页和响应模型的唯一机器规范为[api/openapi.yaml](../api/openapi.yaml)，[JSON](../api/openapi.json)由同一内容派生。29项RBAC操作及8项可选通用应用身份操作；默认首次授权仅RBAC，应用目录从数据库登记事实读取，原型演示系统不接入。本文解释Schema不能独立表达的授权、范围、原子性和交互语义；[页面对照](api-map.md)只作索引。

管理协议包含授予级别、人员目标保护、部门增删改名、范围总览及只读职位/角色摘要。GET /me增加最新实际routes、managementLevel、globalScope和departmentName，前端按路由能力与范围显示入口，不额外查询日志来探测权限。

## 1. 共用HTTP约定

RBAC管理端成功/失败包含code、message、data、requestId；成功code=OK，失败data=null。POST创建角色201，其余成功200；退出200/data=null。可信用户ID、权限、范围由服务端读取，不接受客户端actor/admin/scope字段。请求JSON类型严格，未知字段拒绝；DTO与VO分开，不返回JPA关系、哈希、手机号或完整原始导入资料；职位以只读字段返回。

不存在的端点返回404 ENDPOINT_NOT_FOUND；路径存在但HTTP方法不支持返回405 METHOD_NOT_ALLOWED，与业务对象不存在的404 RESOURCE_NOT_FOUND区分。部门父子类型错误为400 DEPARTMENT_TYPE_INVALID；删除根、删除非空节点及同级重名分别为409 DEPARTMENT_ROOT_REQUIRED、DEPARTMENT_NOT_EMPTY、DEPARTMENT_NAME_CONFLICT。以上均属于OpenAPI的Error.code枚举并保留requestId；离线导入/首次授权命令的错误不冒充HTTP接口契约。

人员、角色、API候选及可分配角色使用items/total/page/size；第0页、默认20、最多100，id升序，数据库先范围/过滤/计数再分页。日志单独使用cursor/limit快照，不改成offset。有限系统目录、部门树及完整已选集合按规范返回，不受当前候选页截断。

列表q先去首尾空白，处理后最多100字符；按各接口指定字段作不区分大小写的包含搜索。人员查姓名/工号，角色及可分配角色查name/code，API资源查method/path；不把登录标识的精确匹配或角色code唯一性规则混同为搜索规则。

新建角色的code/name及PATCH提供的name先去首尾空白，再校验非空及1—100字符；code创建后不可改。上述q/code/name的长度约束作用于归一化后的值，Schema静态长度不能代替服务端先归一化的处理顺序。

IDs为正安全整数，保留内部主键/工号职责差异；工号不转数值。ids必须是唯一、非null、存在且符合状态/系统/授权的集合；[]表示清空。三类PUT整体替换，任何失败全回滚，不能静默去重/过滤/部分成功。PATCH缺省保留；parentId显式null清父，其余支持字段不能null；空请求/未知字段拒绝。

## 2. 有效授权与范围

### 2.1 登录与身份

account/password是一个入口，工号或手机号定位唯一用户，共用口令/状态/授权；同用户两字段同值算一人，跨用户歧义不选择第一条。未知/错口令/停用/歧义统一401 LOGIN_FAILED；账号口令正确但无本系统有效管理API为403 MANAGEMENT_ACCESS_DENIED。普通员工、空角色、仅父菜单/外系统权限均不准入。不新增刷新令牌、注册或找回流程。

管理登录与外部应用登录统一签发HS256身份JWT，通过Authorization: Bearer携带。仅含sub/aud/iss/iat/exp/jti，无角色权限快照；管理aud为rbac，外部aud为单个登记应用。两类登录沿原token/accessToken字段及响应包装，受众不能混用。签名、issuer、严格到期及当前会话索引均须通过，所有业务授权继续逐请求读库。进程内会话支持单令牌退出，重启后旧JWT失效；共享签名秘密不等于多实例共享会话。

本人及本人权限只要求有效身份；管理权撤回后旧身份本人查询沿现有支撑处理，管理操作立即拒绝，再次登录拒绝。退出沿现有幂等行为，缺失/未知/重复/非Bearer头成功空操作，携带有效Bearer则撤销；不创建新身份。

实时鉴权以授权变更事务提交为边界：提交后的新受控请求使用原身份即须按最新授权/范围判断，无需重新登录或等待令牌过期。仅撤销一条链而其他有效链仍覆盖该API时继续按OR判定；无剩余授权时拒绝。已进入执行阶段的在途操作不追溯取消，等待根锁的管理写仍按2.3执行锁后重验；完整需求见[实时鉴权](requirements.md#31-实时鉴权与撤权生效r08r09r12r15)。

### 2.2 实际API与部门树

沿启用用户→启用角色→启用权限→同系统启用API/系统，按服务端方法和路径模板判定。多角色/多权限OR，同一API可由多个权限覆盖；初始权限编码仅用于追溯，不是强制的授权入口。父菜单或为导航补祖先不授予子API；父停用不级联撤销直接授予子项。

人员范围是本人部门及后代；共同根必须来自核实的来源映射和完整组织覆盖，不根据名称或任意null父节点认定。部门树可见根parentId投影null只用于不泄露范围外父节点，不能让前端据此推断全局资格。

全局角色/资源目录读写及日志要求共同根和具体API；单操作不要求整组权限或完整K。部门人员管理使用受限候选接口，不读取全局角色目录。即使误授日志API，非共同根仍拒绝。

### 2.3 精确目录与管理员保护 K

本系统25项受控API中，日志GET、人员停用PATCH、调动PUT不属于K，排除新增总览及部门四项操作后，原17项仍为维护恢复集合；四个登录/本人基础操作不在K。精确方法/路径以OpenAPI的x-maintenance-member标记核对。

每名计入的用户必须启用、直属可信共同根、有效基础管理级别为5，并独自覆盖全部K；本人多角色可汇总，不同用户不能拼接成一名完整维护者。改名/角色名/admin用户名均不能替代此判断。至少保留一人是系统不变量，完整K不是每位操作者登录/执行单操作或查询日志的前置。

资源、映射、角色权限、人员角色/状态/部门等相关写共用同一稳定根锁；取锁后重新核对身份、具体API和范围，再写并重算K，在同一事务提交。等待锁期间撤权/调出范围的旧请求必须拒绝。并发不得各自把另一人当保底管理员而同时提交；失败恢复原状态及关联。

## 3. 各操作组的业务边界

| 操作组 | 必须保持的语义 |
| --- | --- |
| 人员查询 | 部门/q/状态AND组合，q查姓名/工号；先操作者子树再所选部门子树及过滤；同谓词total，越末页空items但保留total |
| 人员角色 | 当前集合包含停用或不可再授的已保存角色；候选只启用角色，同时满足固定授予级别上限及当前已接入系统完整有效Permission ID子集（含无API菜单）；所有已登记应用的有效权限参与子集比较；保存锁后按新增/删除差集重验，原样保留的不可改项不算重新授予 |
| 人员保护 | 非系统管理员不能改本人、同级、上级；自定义角色不冻结持有人，但该关联仍仅管理员可增删；系统管理员仍受实际API、范围与完整K限制。停用保留部门/角色，旧请求401，再登录LOGIN_FAILED；调动双范围，保持同一用户/角色 |
| 角色定义 | 新建启用空权限角色；编码稳定，唯一性按真实列比较规则；改名不改权限，停用保留关联；无删除/继承/审批/版本 |
| 角色选权 | 直接ids独立选择，不自动补祖先/后代；可组合所有已登记应用的启用权限，空集撤销，未知/重复/停用权限拒绝；Permission—API映射跨系统仍拒绝 |
| 资源树 | 父子同系统、无环，仅MENU可有子；仅parentId/type/enabled，系统/code/name只读 |
| API与映射 | method/path/system只读，只启停；映射完整读/整体替换，同系统可保留停用API但不贡献有效能力；影响全部引用 |
| 最小日志 | 同时日志API+共同根；每批重新校验，username为工号，不复制手机号输入或查询串；无归档/导出/完整变更历史 |

### 日志快照

日志记录的信息类别、隐私及查询统计要求见[日志需求](requirements.md#32-鉴权日志记录与查询统计r16r20)；LogEntryVO沿OpenAPI返回事件时间、用户ID/工号、系统/权限、允许结果/原因、requestId、事件类型、HTTP方法/安全路径/状态。未知主体及未定位权限允许相应字段为空，鉴权允许与后续业务成功分别理解，不增加汇总统计接口。

LogPageVO.systems提供日志筛选所需全部已登记应用（id/code/name/enabled），包含停用项；目录不随本次日志筛选缩小，读取沿日志API及可信根资格，不要求资源目录API。页面使用名称下拉并提交稳定appCode，旧日志仍可按编码定位。

LOGIN_SUCCESS/LOGIN_FAILED表示RBAC凭据及应用准入结果，不证明课程Teacher/Student绑定或业务登录已成功。实际调用的短码与operationId权限查询以PERMISSION_CHECK留痕，每个查询一次，allowed为真实真假结果；false仍可HTTP200，reason=PERMISSION_NOT_GRANTED，不是一次课程业务HTTP403。AUTHORIZATION继续表示RBAC实际执行的门禁。课程使用/auth/me取权限后自行执行的对象判定和业务结果留在课程日志，RBAC不伪造自己未收到的功能执行记录。

未携带或无法核实令牌的真实身份请求保留UNAUTHENTICATED记录，主体可空。运维健康检查使用内网Actuator，不使用匿名身份请求；已有探测噪声作为原始历史保留，不能通过批量删除日志或屏蔽全部401解决。

首次snapshotId为当前最大日志ID，选id≤snapshotId，后续按id<上页末条降序；total为同一快照筛选总数，不随翻页缩小或读取产生的新技术记录增长。筛选/limit绑定游标，改变后清cursor；损坏/伪造/不一致400 INVALID_CURSOR。from含、to不含，必须UTC Z格式且from<to；到底nextCursor=null、hasMore=false。

页面显示和筛选输入按北京时间处理，前端转换后仍提交上述UTC边界；接口occurredAt继续返回UTC ISO-8601，不因展示时区修改数据库时间、Instant时钟或JWT时效。实际发布与Java日志时间核对见[发布验证](history/verification/release-20261123.md)，本机修正过程见[时间记录](history/verification/beijing-time-and-service-cleanup.md)。

上述最大 ID 表示已提交日志的稳定前缀。生产写入统一经 `JpaAuditWriter`：在独立事务中先锁定稳定 RBAC 系统目录行，再分配日志 ID 并提交，防止较大 ID 抢先提交、较小 ID 随后污染旧快照。新增日志写入入口也必须沿用该追加协议，不能直接调用日志仓储绕过协调。接口字段、筛选和游标形式保持不变。

## 4. 错误与检查顺序

| HTTP/code | 语义 |
| --- | --- |
| 400 INVALID_ARGUMENT / INVALID_CURSOR | 格式/字段/关联/树约束或游标错误，无部分更新 |
| 401 LOGIN_FAILED | 未知/停用/错口令/登录歧义统一响应 |
| 401 AUTHENTICATION_REQUIRED / SESSION_INVALID | 缺失可信身份或已有身份失效 |
| 403 MANAGEMENT_ACCESS_DENIED | 登录身份校验后仍无有效管理准入 |
| 403 PERMISSION_DENIED | 缺当前方法/模板的实际API权，不靠角色名称判断 |
| 403 MANAGEMENT_SCOPE_DENIED | 部门/根/系统范围、人员自身保护或可授予子集不满足 |
| 404 RESOURCE_NOT_FOUND | 已获可见范围中的直接目标不存在；非根未知/越界人员统一403 |
| 404 IDENTITY_USER_NOT_FOUND / IDENTITY_DEPARTMENT_NOT_FOUND / API_OPERATION_NOT_FOUND | 外部身份用户、可见部门或当前应用操作不存在 |
| 409 DATA_CONFLICT / LAST_ADMIN_REQUIRED | 唯一性/并发冲突或写后无完整维护者，全回滚 |
| 500 INTERNAL_ERROR | 安全内部错误，带requestId，不泄露SQL/栈/请求体/秘密 |

设计顺序是身份/账号→实际API→部门/根/系统范围→输入及关联→写后完整K。直接外系统目标/目录403；已获本系统目标但提交跨系统关联400；未知关联ID400，已获范围直接目标未知404。自定义角色的候选及提交均要求操作者直属可信根且当前有效基础级别为5，并继续检查目标范围和权限子集。前端隐藏按钮不构成安全边界。输入预检不代替Service实际门禁；实现变化须按契约复验。

## 5. 评审及变更

契约变更先确认需求、Schema、范围与错误语义，再同步用例、实现和测试；前端只依赖已评审接口。OpenAPI字段与业务说明分别维护职责，Apifox只在明确授权的范围同步并回读核对。

OpenAPI不能单独证明事务、范围、K、脱敏或并发成立；验证方法见[测试说明](testing-acceptance.md)。各记录只证明对应版本及执行范围，不跨版本或策略继承成绩。

## 6. 部门与总览规则

GET /api/departments为完整范围导航，返回type、directUsers、totalUsers及canCreate/canEdit/canDelete；沿父节点和子树连续排列。GET /api/departments/tree保留人员筛选入口，复用同一范围/人数口径。POST /api/departments仅接parentId/name/type；PATCH /api/departments/{id}仅接name；DELETE只处理空且非根节点，没有层级调整、恢复或级联删除接口。名称先trim再验证1—100字符，同父重名409；所有写与人员调动共用根锁，锁后重验。

新增仅支持总公司→分公司/部门/项目，分公司→部门/项目，部门→项目，项目叶子。总公司身份依可信来源，不按名称或任意parentId=null推断。局部可在本人/下级下新增，只能改删严格下级。删除保留内部墓碑及来源外键，查询、范围、统计不含墓碑；含停用人员也不能删除。错误包括DEPARTMENT_TYPE_INVALID(400)、DEPARTMENT_NAME_CONFLICT、DEPARTMENT_ROOT_REQUIRED、DEPARTMENT_NOT_EMPTY(409)。

GET /api/overview在同一快照聚合范围人数、启停人数、有效组织数及直接下级分布。总数等于根直属人数加每个直接子树人数，不重复加孙节点；总览不混入无权访问的全局日志。新增5操作按对应权限独立控制，不扩大原17项K；完整恢复资格同时增加基础级别5，防止自定义角色仅凑齐接口后冒充系统管理员。

## 通用应用身份协议（R29）

八项可选端点为POST /auth/login、POST /auth/logout、GET /auth/me、GET /auth/permissions/{permission}、GET /auth/departments、GET /auth/departments/{departmentId}/users、GET /auth/api-permissions/{operationId}及GET /auth/users/{userId}。以APP_IDENTITY_ENABLED开启，成功响应不包装data，错误保持稳定code/message/requestId。账号本地工号/手机号规则沿用，systems只接收一个已登记外部应用；RBAC管理端仍使用原入口及准入。system响应字段为服务端受众，应用停用、账号停用或授权撤回均在下一请求生效。

角色全局共享，可包含不同应用的权限。系统目录从数据库登记事实读取，包含停用项供管理员维护；不维护应用代码白名单。API类型权限须非空映射且所有路由同系统启用才导出短码；菜单、按钮可以不映射API，独立控制展示和登录资格。不同应用同名短码分别计算，不混用受众；RBAC完整维护者K仍只覆盖原17项管理路由。

GET /users/{id}/roles增加lockedRoleIds，表示当前不可删除的已授项。整体替换锁后重新读库计算新增/删除；原样保留无需重新授予，任何非法新增/删除整体回滚。自定义角色只由可信根直属有效五级管理员增删，局部管理员仍可在范围内停用或调动较低级人员。不存在委托授权机制。

老师课程业务端点及外部业务审计协议不属于RBAC业务OpenAPI；完整本地接入与外部依赖另见[接入说明](course-integration.md)。


权限展示名称采用完整中文（如“查看总览”“分配人员角色”“查询鉴权日志”），稳定编码仍保留供关联、排错和接口使用，不按中文名称鉴权。两套首次授权与本地夹具读取同一RBAC中文目录；课程显示名称也来自独立接入配置，不在Vue按英文编码硬翻译。

通用/auth/me还返回permissionApis，即每个有效权限当前实际映射的方法/模板事实。RBAC不内置接入系统的预期路由；本地课程适配器依据catalog.json核实老师同一粗粒度短码的全部必要路由，缺少一条即保守关闭整个短码，菜单检查也采用相同投影。这样删除多路由权限的部分映射不能借另一条映射继续调用已撤路由，映射变更仍即时生效。

通用身份查人是实际受控操作，其API映射判定由用例登记到请求上下文，再由统一过滤器记录一次AUTHORIZATION事件；通过门禁后的目标404仍是鉴权允许，HTTP状态独立记录。普通/auth/me和/permissions元数据查询不冒充课程业务执行或把allowed=false的数据值写为查询HTTP被拒。查人路径使用字符串标识；本RBAC签发正安全整数字符串，超界数字400，其他不存在字符串在鉴权后返回404 IDENTITY_USER_NOT_FOUND。


外部身份使用HS256 JWT，只有sub、aud、iss、iat、exp、jti，权限仍逐请求重算。退出204只撤当前外部令牌，撤权后也允许本人退出；管理端退出协议保持原有200。目录接口使用page从1开始/pageSize默认20最大100，各自需实际API映射且按人员部门子树限制，用户目录只查直属人员五字段。operationId通过IDENTITY_OPERATIONS_FILE显式配置，同应用内匹配；全局门禁不代替课程对象权限。详细设计见[完整身份契约](backend-design.md#18-通用应用身份与课程本地接入)。统一身份审计另2项不属于这8项，不加入RBAC业务接口。
