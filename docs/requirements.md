# 统一权限管理系统需求说明

本文描述现行业务行为及验收边界。R01—R35是稳定需求标识，供代码、测试和图件追溯；正文按业务主题组织，不以实施轮次追加章节。实际完成范围见[测试与验收](testing-acceptance.md)。

## 1. 来源与适用范围

| 来源 | 用途与优先级 |
| --- | --- |
| 老师检查记录与补充课堂图片 | 核心对象、部门管理、菜单/按钮—API、原型先行、JPA/AOP/事务/CloneFactory及双业务系统方向；摘录见[需求来源](requirements/sources.md#1-老师要求) |
| 用户2026-11-07—12明确确认 | 双入口、管理端准入、最小日志、新项目初始化、领导节点合并、五角色、双实现同仓库；详见下文 |
| 用户2026-11-13原型评审确认 | 防越级角色授予、部门增删改名、人员只读职位/角色摘要、同级人员保护、跨系统资源/原表矩阵、范围内总览及管理员工作区；现行规则见R23—R28 |
| 用户2026-11-24第二次检查记录确认 | 当前只保留RBAC和课程，清除其余未接入目录；仍需动态新增第三个系统，见R28/R34/R35 |
| 三份老师原始Excel | 原始人员、组织和业务权限资料；不是口令、状态、HTTP资源或逐人授权的来源 |
| 课程系统既有协议 | 接入统一身份及业务授权，不把课程对象或业务范围移入RBAC；见[后端设计](backend-design.md) |

冲突按老师业务要求及用户后续明确确认处理。历史需求规格说明书、旧原型和旧代码不覆盖本说明；历史证据保留在[历史索引](history/README.md)及Git历史。待确认的课程系统业务不据示例项目自行补齐。

系统目标是统一维护用户、角色、菜单、按钮及API的授权关系，以组织树限定人员管理范围。RBAC管理系统与Excel中的“系统管理后台”是两个业务系统，后者的五角色业务矩阵不能直接当作本RBAC管理端权限。课程系统按R29显式接入；structured/ddd是同一业务的实现方式，两个维度彼此独立。

## 2. 数据与初始化要求

| 编号 | 要求 |
| --- | --- |
| R01 | 保留六个核心实体：User、Role、Permission、Department、ApplicationSystem、ApiResource；公司、分公司均为部门树节点，不建独立全局账号实体、管理员属性或独立管理范围表。<br>验证场景：[核对六个核心实体表和完整表清单](../backend/src/test/java/com/municipal/rbaccore/r01/R01JpaConstraintTest.java#L200)；[验证用户、角色、权限与 API 关联的持久化](../backend/src/test/java/com/municipal/rbaccore/r01/R01JpaConstraintTest.java#L156)；[导入后核对总公司根节点和领导归属](../backend/src/test/java/com/municipal/rbaccore/r03/R03ImportJpaContractTest.java#L271)。 |
| R02 | 用户必须属于一个部门。人员管理范围是本人部门及后代；共同根须由来源映射和真实树核实，部门名称或任意parentId为空不能作为全局资格。<br>验证场景：[数据库拒绝无部门账号](../backend/src/test/java/com/municipal/rbaccore/r01/R01JpaConstraintTest.java#L385)；[人员查询按部门子树裁剪，拒绝访问范围外部门](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L907)；[调动必须同时符合原部门和目标部门范围](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L432)；[共同根来源必须指向能覆盖全树的实际根节点](../backend/src/test/java/com/municipal/rbaccore/r23/R24StructuredReadBoundaryTest.java#L95)。 |
| R03 | 原始组织111节点，导入仅将“公司领导”合并到“总公司”，其10人归根，目标110部门；10000员工加独立admin共10001账号，根直属11人。保留原件、源行、六列、文件摘要与新ID映射，不迁旧ID/哈希/状态/授权。<br>验证场景：[真实 MySQL 万人导入、根节点合并与来源映射](../backend/src/test/java/com/municipal/rbaccore/r03/R03ImportJpaContractTest.java#L271)；[来源记录任一字段漂移时拒绝重跑](../backend/src/test/java/com/municipal/rbaccore/r03/R03ImportJpaContractTest.java#L151)；[拒绝缺父节点、多根、循环和重名组织](../backend/src/test/java/com/municipal/rbaccore/r03/R03WorkbookImportPlannerTest.java#L93)。 |
| R04 | username就是工号，来自Excel“用户名”；phone来自“电话号码”，是同一账号另一登录入口。工号唯一，手机号可空、非空值唯一；来源导入拒绝跨用户标识碰撞。不增加employee_no、标识注册表或第三种别名。<br>验证场景：[工号和手机号登录同一账号](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L602)；[空手机号、同用户双字段同值及歧义登录边界](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L670)；[导入保留工号和手机号的前导零](../backend/src/test/java/com/municipal/rbaccore/r03/R03WorkbookImportPlannerTest.java#L74)；[导入拒绝重复工号、重复手机号及跨字段碰撞](../backend/src/test/java/com/municipal/rbaccore/r22/R22ImportDatabaseBoundaryTest.java#L36)。 |
| R05 | 员工全部启用，员工统一初始口令与管理员独立口令分别来自必填部署配置，各账号独立BCrypt盐；默认管理员账号名admin，可显式配置。秘密不进入源码、命令参数或报告。<br>验证场景：[生产密码 Bean 使用独立 BCrypt 盐](../backend/src/test/java/com/municipal/rbaccore/config/R03ImportConfigurationTest.java#L47)；[缺少必填凭据时拒绝写入](../backend/src/test/java/com/municipal/rbaccore/r03/R03ImportJpaContractTest.java#L136)；[分别校验员工和管理员初始口令配置](../backend/src/test/java/com/municipal/rbaccore/r22/R22ImportDatabaseBoundaryTest.java#L58)；[导入命令读入配置且输出不泄露口令](../backend/src/test/java/com/municipal/rbaccore/bootstrap/R06BootstrapCommandTest.java#L107)。 |
| R06 | 组织账号导入与首次授权分开。首次按已确认且经来源核对的清单批量授予五角色；预览只读，实际授权整批事务；重复执行不覆盖后续状态、部门、口令或授权调整，来源变化或不一致拒绝。<br>验证场景：[核对原始万人资料的五角色分配](../backend/src/test/java/com/municipal/rbaccore/r06/R06InitialRoleRulesTest.java#L21)；[来源完整时允许发布，匹配回执保留后续调整](../backend/src/test/java/com/municipal/rbaccore/r06/R06InitializationPolicyBoundaryTest.java#L34)；[来源不完整或回执失配时拒绝发布](../backend/src/test/java/com/municipal/rbaccore/r06/R06InitializationPolicyBoundaryTest.java#L46)；[重复初始化保留人工撤权和资源调整](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L1193)；[真实 MySQL 中预览只读，首次授权出错整批回滚](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L490)；[已有回执时预览继续核对来源](../backend/src/test/java/com/municipal/rbaccore/r06/R06InitializationReadBoundaryTest.java#L132)。 |

首次清单人数及业务能力如下。岗位分类只用于这一次已确认清单，不作为运行时鉴权或后续调动自动改角色的依据。

| 初始角色 | 人数 | 本RBAC系统的初始能力 |
| --- | ---: | --- |
| 系统管理员 | 1 | 独立admin，完整维护及日志查询 |
| 公司领导 | 64 | 总公司/分公司直属领导；范围内人员查询、角色分配、停用与调动 |
| 部门经理 | 106 | 部长、副部长；同上，范围为本人部门及后代 |
| 项目经理 | 72 | 项目经理、副经理；同上，范围为本人部门及后代 |
| 普通员工 | 9758 | 无本RBAC管理操作权限，拒绝登录管理端 |

10名总公司领导合并归根后，其范围覆盖整棵组织树；仍不因“领导”称谓获得角色定义、资源维护或日志权限。三种人员管理角色均不含这些全局维护能力；初始另具范围总览和部门管理权限，部门操作仍受R24限制。后续用户可拥有零到多个角色。

## 3. 登录、授权与管理行为

| 编号 | 可验收的业务要求 |
| --- | --- |
| R07 | 单一account/password入口精确匹配工号或手机号；必须定位唯一用户，同用户两字段同值视为一人。未知、跨用户歧义、错口令、停用统一401 LOGIN_FAILED；不得按号码长度猜输入类型。<br>验证场景：[工号和手机号走同一登录入口](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L602)；[停用和错误口令统一返回 401](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L641)；[空值、输入歧义与前导零边界](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L670)。 |
| R08 | 账号/口令通过后，必须具有显式映射POST /api/auth/login的有效RBAC登录权限。只授登录可进入空工作台，不可读取任何业务数据；再授某个菜单显式映射的读取API才可进入该页。无登录权限的普通员工、空角色和仅外系统权限拒绝。已有身份每次管理请求仍重新鉴权；授权变更提交后的新请求立即按最新权限判断，无需重新登录，详见3.1。<br>验证场景：[仅授登录可读本人，业务读取和写入独立授权](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L218)；[只有外系统或导航权限时拒绝管理登录](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L748)；[撤权即时影响当前请求与再次登录](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L370)；[只授登录时浏览器进入空工作台](../frontend/render-tests/applications.spec.mjs#L94)。 |
| R09 | 有效授权沿启用User→Role→Permission→同系统启用ApiResource及ApplicationSystem计算，按方法与服务端路径模板匹配。多角色/多权限按OR；菜单可显式映射本页读取API，按钮另映射写入或独立查询API；二者通过Permission—ApiResource关系关联，父子层级不授予任何隐含API。父节点停用不级联撤销直接授予子项。<br>验证场景：[停用链路不贡献权限，停用父项保留已授子项](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L247)；[多权限按 OR 判定，各环节启停即时生效](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L815)；[直选子权限保留父节点引用](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L86)；[API 和映射变更立即影响实际授权](../backend/src/test/java/com/municipal/rbaccore/r14/R14ResourceContractSupport.java#L272)。 |
| R10 | 人员查询先限定部门范围，再执行直属/子树、角色关联、姓名/工号搜索、状态、分页及同条件计数。直属查询必须指定部门；角色筛选包含停用角色成员，未知角色返回空页。部门页可查看直属员工并切换含下级，角色页可查看成员，均只读且不得扩大可见范围。未知可见部门404，范围外部门403；部门树不得泄露范围外父节点/兄弟节点。<br>验证场景：[按部门或角色查询员工且范围受限](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L149)；[按可见范围计数分页，拒绝查询范围外部门](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L907)；[部门成员查询的参数和异常边界](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L235)。 |
| R11 | 选人后可查看当前角色和受限候选，整体替换角色；候选及保存同时满足R23授予级别和完整有效Permission子集，不能只比较API能力，不得先过滤外系统权限后当作空角色授出。目标人员另受R27保护。<br>验证场景：[原子替换人员角色](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L300)；[不同权限即使映射同一 API 也不视为同权](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L585)；[候选先按授权范围过滤再分页](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L722)。 |
| R12 | 停用账号后现有身份不能继续管理，再次登录拒绝。调动要求原部门与目标部门均在操作者范围内；保持同一用户身份，同事务移除不再适用的部门角色，其他角色保留；调回不自动恢复，后续请求按新部门范围判断。<br>验证场景：[调动校验双范围并保留适用角色](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L432)；[停用使旧会话与再次登录失效](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L391)；[等待组织锁后重验人员范围](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L811)；[调动移除不再适用的部门角色](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L174)。 |
| R13 | 角色定义写入和资源维护须共同根及对应实际API；局部有效领导可按相同实际读取API访问本人子树的部门角色，数据库过滤后分页。部门角色定义及选权另要求可信根有效五级管理员。角色支持查询、新建空权限定义、改名、启停、整体替换权限；不扩展删除、继承、审批或版本。<br>验证场景：[角色定义及权限替换的完整流程](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L384)；[拒绝非法角色定义](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L510)；[按条件过滤角色并分页](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L418)。 |
| R14 | 资源维护限定已登记系统目录（含待接入规划）、权限parentId/type/enabled、API enabled及权限—API整体映射。只有菜单可有子节点，父子同系统、树无环、映射同系统。既有资源页不提供权限system/code/name或API method/path编辑；R35接入向导可维护外部应用权限名称和新增目录，已有ID与code稳定，已有API须保留或停用。应用资料和接入生命周期见R34/R35。<br>验证场景：[保存权限属性、API 状态及映射](../backend/src/test/java/com/municipal/rbaccore/r14/R14ResourceContractSupport.java#L238)；[拒绝循环、跨系统和非法树节点](../backend/src/test/java/com/municipal/rbaccore/r14/R14ResourceContractSupport.java#L345)；[稳定分页与权限树查询](../backend/src/test/java/com/municipal/rbaccore/r14/R14ResourceContractSupport.java#L203)。 |
| R15 | 相关写入共用可信根锁，锁后重新检查当前身份、权限及范围；写后至少保留一名直属共同根、启用、有效基础管理级别为5、具有有效RBAC登录资格且独自具备完整K的用户。同用户多角色可汇总，不同用户不能拼接成一名管理员。冲突和故障全回滚。<br>验证场景：[拒绝破坏最后一名完整管理员](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L538)；[并发撤权后仍保留完整管理员](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L569)；[等待根锁后重验角色操作](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L1165)；[存储故障回滚角色写入](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L1053)。 |
| R16 | 最小鉴权日志记录真实登录/鉴权允许及拒绝，明确事件时间、主体、目标、结果、原因及请求追踪信息，支持筛选、同条件总数和快照游标分页，详见3.2。查询同时要求日志API及共同根，完整K不是查询前置；部门账号即使误授该API也拒绝。不含归档、导出或完整变更审计。<br>验证场景：[鉴权日志记录并筛选来源 IP](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L254)；[分页保持同一筛选和快照](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L1033)；[并发写日志时快照成员保持稳定](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L1102)；[共同根及日志 API 两项均满足才允许查询](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L986)。 |
| R23 | 增加明确角色授予级别：系统管理员可授全部角色；公司领导可授公司领导及以下；部门经理可授部门经理/项目经理/普通员工；项目经理只可授项目经理/普通员工。全局自定义及应用预置角色仅启用、直属可信根且有效基础级别为5的操作者可授；部门自定义角色可由所属部门或上级范围内的有效领导分配/撤回，目标须在角色所属部门子树且低于操作者级别。自定义角色不提升级别；始终叠加R11完整计划权限子集（包含待接入项）和R02范围。<br>验证场景：[应用预置及全局自定义仅有效根五级可授](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L673)；[部门角色按范围分配及调动时调整](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L174)；[授予级别矩阵与自定义角色保护](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L482)。 |
| R24 | 独立部门管理支持新增、改名、删除，不提供调整层级。系统管理员维护全公司；公司领导/部门经理/项目经理仅可改名或删除本人直属部门的严格下级，不能操作本人、上级、平级。新增父级可选本人部门或下级且须符合类型：总公司可建分公司/部门/项目；分公司可建部门/项目；部门只能建项目；项目不可建下级，不创建第二个总公司。仅无人、无下级且无所属角色的空部门可删，根不可删；新增不自动授予人员角色。名称先trim再验1—100字符，同一上级下不能重名；改名不改变ID、人员归属、职位、角色或范围，原Excel不改。每次仍须对应实际操作权限。<br>验证场景：[部门新增、改名及删除流程](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L397)；[拒绝越界、非法层级或非法删除](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L559)；[存在所属角色的部门不可删除](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L1279)。 |
| R25 | 增加总览，展示当前可管理范围的人员总数、启用/停用人数、组织节点及下级分布，提供获准工作区快捷入口。系统管理员可见全公司，其他管理者仅见本人部门及下级；最近鉴权活动仍须日志API和共同根，不向非根混入全局日志。<br>验证场景：[核对部门树的直属人数与含下级总人数](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L458)；[部门新增及删除后核对总览人数和部门数](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L397)；[总览人员入口按实际查询权置灰](../frontend/render-tests/permission-actions.spec.mjs#L84)。 |
| R26 | 人员列表展示只读职位及当前已授角色摘要，职位来源为老师Excel“岗位”等已核实人事资料，不提供职位编辑或职位调动，也不因职位文本自动增减权限。角色摘要包含停用角色，明确标为“角色已停用”，账号列为“账号状态”；二者独立，停用角色保留关联但不贡献权限，不会自动停用持有者账号。进入分配抽屉仍读取完整当前角色和分页候选。<br>验证场景：[列表角色摘要区分角色停用和人员状态](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L458)；[职位与角色摘要经后端映射保留](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L960)。 |
| R27 | 非系统管理员仅可修改级别严格低于本人的范围内人员；本人、同级、上级只读，角色调整/停用/部门调动全部受限。公司领导之间不因共享部门范围而相互修改。可按R23将下级授为本级，成功后按其最新级别保护。系统管理员例外仍须有效API、范围及完整维护者保护。自定义角色不提升操作者或目标级别，不冻结人员。角色整体替换须锁后读取当前集合，新增和删除项分别验证级别、权限子集；无权改变的现有项可以原样保留，不能伪造保留或增删。全局自定义及应用预置仅可信根有效五级管理员增删，部门角色按R23范围授撤，前端显示锁定项；停用和调动按原人员保护与部门范围判断。<br>验证场景：[同级人员的停用、角色替换与调动均拒绝](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L375)；[局部管理员不能调整自身角色或调动自身](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L508)；[无权改动的自定义角色可保留](../backend/src/test/java/com/municipal/rbaccore/r23/R23PrototypeContractTest.java#L518)；[锁后按最新目标范围判断](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java#L811)。 |
| R28 | 正式初始受管目录只包含RBAC自身和真实接入的课程管理系统，不导入原权限Excel的其余18项系统、模拟权限或五角色业务矩阵。五个基础角色的RBAC权限按本系统实际菜单/API定义；课程三角色由独立接入配置登记。保留应用管理新增第三个系统的能力：新应用默认PENDING，配置并完成接入后CONNECTED，待接入不产生实际通行。角色来源仍区分SYSTEM_BUILTIN、APPLICATION_PRESET、DEPARTMENT_CUSTOM、GLOBAL_CUSTOM，所属和授予范围沿R23；不因当前只配置两应用而增加后端白名单。<br>验证场景：[核心首次授权仅生成一个系统目录](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L490)；[部署首次初始化只追加课程应用](../scripts/tests/test_deployment_bindings.py#L26)；[运行阶段拒绝执行首次初始化](../scripts/tests/test_deployment_bindings.py#L40)；[按数据库登记事实列出额外应用](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L286)；[角色来源及所属范围](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L94)；[待接入应用拒绝登录和实际通行](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L787)；[新增、移除、恢复第三个系统](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L48)。 |
| R29 | 对接老师course-project-mgt既有生产前后端与统一身份HTTP协议。Application目录从登记数据读取，不在RBAC代码维护应用白名单、教师/学生标记或课程业务策略。角色可含多个应用权限，Permission—ApiResource映射必须同系统。通用应用身份按凭据、系统启用与当前有效授权签发独立受众；管理端仍沿R08检查独立RBAC登录资格。teacher/student/课程管理员均为显式角色配置，老师自己的登录、Teacher/Student关联和对象规则保留。角色、权限、映射及启停提交后，旧令牌下一请求实时重算。权限type增加API：要求非空映射且全部同系统启用；菜单、按钮可无API表达展示或资格。完整本地体验覆盖学生、教师、课程管理员以及老师已实现的教学业务和日志；老师仅在文档或常量中声明但没有生产实现的功能单列说明，不伪造。老师原工程尽量不改；任何必要修改须报告。不增加委托授予或可授角色清单机制。<br>验证场景：[任意已登记应用可按通用身份协议运行](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L496)；[旧令牌下一请求按撤权结果判断](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L229)；[拒绝跨系统映射，停用 API 即时失权](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L241)；[多路由短码任一必要映射缺失时关闭](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L261)。 |
| R30 | 权限展示名称应采用通俗中文，覆盖RBAC初始目录和课程接入目录；编码保留稳定标识，不修改ID、关联或鉴权语义。两套首次授权和本地联调数据均需一致，不在Vue按角色或英文编码猜测中文名称。<br>验证场景：[权限中文名称及稳定编码核对](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L85)。 |
| R34 | 提供应用管理菜单，按数据库分页查询、新增、改名、接入状态维护、移除和恢复受管系统。新增code稳定唯一、name必填，默认启用且PENDING，不自动授予角色或接口；标记CONNECTED前应完成实际接口接入。移除以enabled=false关闭通行，保留目录、权限/API关系、ID和历史日志；PATCH启用恢复当前仍保留的关系，不恢复人工撤权。全部操作要求对应实际API和可信根，写入根锁后重鉴权及完整维护者保护；移除RBAC导致无维护者时409全回滚。受管系统并非后端代码白名单；登记目录不等于该业务系统已部署。<br>验证场景：[新增、改名、移除与恢复应用](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L48)；[拒绝未授权及未知应用](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L82)；[编码冲突及输入校验](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L113)；[名称搜索和分页边界](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L162)；[移除 RBAC 会触发最后维护者保护并回滚](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L153)；[编码和名称长度上限、接入状态更新及停用筛选](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L198)。 |
| R35 | 可信根直属、有效五级且有各自实际API的管理员可在SPA内登记/复用应用、只读预览OpenAPI3 JSON/YAML、配置菜单/按钮/API及独立登录资格、选择角色在该应用的权限子集、检查并完成接入。草稿只在当前页面内存，关闭提示；已有API/权限保留稳定ID与编码，可停用。完整指纹防止旧草稿覆盖并发维护，写事务获共同根锁后重验当前身份、全部关联和人工确认，再原子差量发布；角色权限变化另需配置角色权限API，其他应用和所有人员角色不变。运行operationId和登录项逐请求读取数据库，不回退文件。自动检查可使用专用测试账号调用本RBAC真实身份HTTP，仅测登录/本人/短码拒绝/受众隔离/退出；待接入按真实拒绝报告PENDING，完成后自动复检。业务后端及对象权限、专用环境授撤权由人工确认，测试口令仅RAM且不持久化/记录。已启用CONNECTED应用entryUrl的精确Origin动态开放/auth/*，不开放管理端跨域或抓取该URL。RBAC自身仍由既有资源页维护。<br>验证场景：[向导双策略 HTTP 契约与发布边界](../backend/src/test/java/com/municipal/rbaccore/r35/R35OnboardingContractTest.java)；[元数据结构及并发指纹检查](../backend/src/test/java/com/municipal/rbaccore/infrastructure/onboarding/R35MetadataBoundaryTest.java)；[使用回环 HTTP 测试桩验证身份探测故障](../backend/src/test/java/com/municipal/rbaccore/infrastructure/onboarding/R35ProtocolProbeBoundaryTest.java)；[向导浏览器交互、确认和凭据清理](../frontend/render-tests/onboarding.spec.mjs)；[首次接入元数据和受控接口映射检查](../scripts/tests/test_onboarding_initialization.py)。 |

完整维护者还须有效RBAC登录权限；登录资格独立于K，不将K扩大为所有操作。K包含角色维护、角色权限配置、人员角色分配、资源与映射维护及其必要查询，共17个方法/路径模板；日志、停人、调动不在K内。精确定义沿[契约2.3](api-contract.md#23-精确目录与管理员保护-k)，不得用角色名或权限编码存在代替实际API覆盖。

### 3.1 实时鉴权与撤权生效（R08、R09、R12、R15）

授权变化以事务成功提交为生效边界。管理员撤销人员角色、调整共享角色权限，或停用角色、权限、API及系统后，用户使用原登录身份发起的后续受控请求必须按最新有效授权及部门范围重新判断；不得等待重新登录、令牌过期、页面刷新或固定缓存周期才生效。事务失败或回滚不应产生部分撤权结果。

撤销一条授权链后，若用户已无其他有效授权授予该API，后续请求必须拒绝；若另一启用角色或权限仍合法映射同一API，应保留该有效访问能力，不能把撤销某个角色误当作撤销全部角色。账号停用后原身份不得继续管理，部门调动后按新部门子树判断，原范围不应继续有效。

例：员工同时持有角色A和B，只有A授予人员查询API。管理员撤销A并提交后，员工无需退出登录，再次调用人员查询API即应被拒绝；若B也有效授予该API，则仍可在当前部门范围内查询。

“立即生效”约束提交后的新请求，不承诺追溯取消已进入执行阶段的在途操作。等待共同根锁的管理写仍须在取得锁后重验最新身份、API及范围，不得凭取锁前的旧授权提交。前端应更新失效操作并展示拒绝结果，后端必须独立保证上述规则；本条不要求推送、强制刷新或新增会话实体。

### 3.2 鉴权日志记录与查询统计（R16、R20）

日志应记录真实登录成功/失败、未认证访问，以及受控请求鉴权的允许/拒绝；同一安全事件不应因过滤器、预检和AOP重复采集而重复计数。鉴权允许只表示通过访问控制，不等同于后续业务操作成功，HTTP状态应与鉴权结论分别记录。

| 信息类别 | 必须记录的信息 | 口径 |
| --- | --- | --- |
| 事件标识与时间 | 日志ID、事件时间 occurredAt、事件类型 eventType | 事件按UTC存储和传输，页面按北京时间显示；区分登录、未认证访问及鉴权事件 |
| 操作主体 | 用户ID userId、工号 username | 无法识别主体时允许为空；工号是账号字段，不复制手机号登录输入或其他未核实原始输入 |
| 访问目标 | 系统编码 appCode、权限编码 permissionCode、HTTP方法 httpMethod、请求路径 requestPath | 未定位权限时允许权限编码为空；路径不含查询串、口令或令牌，不将未知目标伪装成已授权目标 |
| 判定与响应 | 允许/拒绝 allowed、原因码 reason、HTTP状态 httpStatus | 记录真实鉴权结果和安全原因码，禁止用业务成功代替鉴权允许 |
| 请求关联 | requestId | 与接口响应关联，便于定位一次请求及其安全事件 |
| 请求来源 | clientIp | 服务端采集IPv4/IPv6，仅显式可信代理可转交单值来源；历史未采集为空，服务调用地址不冒充最终用户公网IP |

日志响应应附带全部登记应用的名称、编码及状态用于下拉筛选，含停用项，不要求另有资源目录权限。RBAC实际处理的短码/operationId权限查询应单独记录PERMISSION_CHECK，保留真假结果及HTTP200语义；身份认证、权限判定与课程业务执行结果不得混称。

查询应支持用户ID、工号、系统、允许/拒绝、事件类型及时间范围组合筛选；工号、系统、事件按精确值匹配，时间含开始、不含结束。结果按日志ID倒序并使用快照游标分页；每次继续查询仍须检查日志API权限及共同根资格。

统计项为当前筛选条件、同一快照内的匹配总数 total；它不是当前页条数，也不应随翻页缩小或因快照之外的新日志增长。界面可据已加载记录显示已加载条数和是否还有更多；修改筛选或分页批量须重新建立查询，刷新后才纳入新事件。具体字段类型及游标约定沿API契约。

日志范围包括记录、检索及匹配总数，不要求按日访问量、成功率、拒绝率等汇总报表。敏感信息限制沿R20；旧文档中的日志归档、导出及完整管理变更审计不在当前范围内。相关范围如有新要求，后续再调整需求和设计。

### 3.3 角色授予级别与权限子集（R11、R23）

正式初始化中三个管理角色具有相同的RBAC人员、部门和总览权限；实际候选仍同时受级别、人员范围及数据库已登记应用的完整有效Permission子集约束。初始只登记RBAC及课程；未来新应用的待接入权限仍参与完整子集比较，但不产生实际通行能力。

公司领导、部门经理、项目经理虽具有相同类型的人员管理API，也不能据此相互授予任意级别角色。级别是独立的授予约束，不代表权限继承，不扩大人员管理范围，不代替逐次实际API鉴权。

基础角色的授予级别使用明确元数据，不按角色名称、编码文本或职位猜测；改名不改变级别。操作者拥有多个启用角色时取其有效最高级别，再检查目标角色的全部有效Permission集合是否为本人有效集合的子集。全局自定义和应用预置角色仅可信根直属有效级别5操作者可授；部门角色由管理员定义，领导按所属范围及完整子集授撤，持有者不自动升级。人员调出可信根后，即使仍持有五级角色，也不再有资格授予全局自定义或应用预置角色。候选过滤与新增项保存使用同一规则；删除项校验当前级别和完整有效子集。不可改变的现有角色须明确锁定并原样保留，提交时不能静默删除，也不能把客户端自报ID冒充现有项。

完整维护者保护仍须同一启用共同根用户实际覆盖K。在增加授予级别后，保留者还须具备完整角色恢复的授予级别，避免只有API入口却无法恢复基础管理员角色；级别本身不能代替实际K覆盖。两套业务实现均须执行该约束。

### 3.4 部门层级展示及人数口径（R24）

部门目录按父部门、其整棵子树、下一同级部门的顺序显示；项目紧跟所属部门并缩进，不能沿数据创建顺序散列。搜索项目时保留范围内祖先路径及缩进，不展示范围外上级。直属人数只计算departmentId为当前节点的账号；总人数含全部下级及启用/停用账号。每个节点总人数=直属人数+各直接下级总人数，父子行不能再重复相加。此展示不修改组织归属、层级或人员数据。

## 4. 前后端及技术要求

| 编号 | 要求 |
| --- | --- |
| R17 | 前端应包含登录、部门人员、人员角色/停用/调动、角色权限、资源映射、日志必要交互；页面入口按实际查询能力与范围显示，已进入页面内的无权操作按钮保留并置灰，不发起对应查询或提交。只读、锁定、受保护目标及状态限制同样禁用，后端独立执行权限判断。角色选权编辑时，已选摘要与数量仅展示当前系统，候选搜索不收窄摘要，切换系统保留完整草稿；保存仍提交所有系统的完整关联。读取中及失败状态不得冒充零项。完整设计见[前端设计](frontend-design.md)。<br>验证场景：[仅授登录或单菜单时的后端准入](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L218)；[操作权限独立授予](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java#L935)；[应用维护无权按钮置灰和空工作台](../frontend/render-tests/applications.spec.mjs)；[页面按钮、只读集合和受保护目标的浏览器测试](../frontend/render-tests/permission-actions.spec.mjs)；[当前系统选权摘要与跨系统完整保存](../frontend/render-tests/permission-summary.spec.mjs)；[按实际 API 能力计算导航](../frontend/tests/r17-navigation.test.mjs)；[日志查询的筛选竞态](../frontend/tests/log-race.test.mjs)。 |
| R18 | 先原型、后API契约、后实现；共享统一响应、requestId、稳定错误码、输入校验和分页。前端交互原型不能冒充真实HTTP或界面完成证据。<br>验证场景：[HTTP 路由错误符合统一响应契约](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L967)；[校验 API 请求参数和分页](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L1158)；[先鉴权，再处理无效请求体](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java#L101)。 |
| R19 | Java21、Spring Boot、Spring Data JPA、AOP、Service读写声明式事务及老师真实CloneFactory；Controller仅适配，持久化错误在写边界翻译。禁止旧JDBC业务链路复活。<br>验证场景：[控制器只依赖共享用例接口](../backend/src/test/java/com/municipal/rbaccore/architecture/R19R21StructuredBoundaryTest.java#L65)；[CloneFactory 映射不可变身份响应](../backend/src/test/java/com/municipal/rbaccore/architecture/R19CloneFactoryMappingTest.java#L23)；[生成的补丁映射保留未传字段](../backend/src/test/java/com/municipal/rbaccore/r18/R18OptionalInputBoundaryTest.java#L93)。 |
| R20 | 最小日志/AOP不得输出口令、令牌、Authorization、原始手机号登录输入、SQL、请求体或完整隐私对象；安全失败返回可追踪requestId，不泄露内部异常。<br>验证场景：[真实鉴权日志不保存测试口令和令牌](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L878)；[日志的请求路径不保存原始查询参数](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L986)；[审计故障只报告安全信息](../backend/src/test/java/com/municipal/rbaccore/infrastructure/logging/R16AuditWriterBoundaryTest.java#L23)；[异常返回统一错误且不泄露约束细节](../backend/src/test/java/com/municipal/rbaccore/api/shared/error/R18ErrorFallbackTest.java#L16)。 |
| R21 | 同仓库提供structured与ddd两套独立业务实现，共享API/数据库契约，通过配置选择；同一套行为契约分别验证两种实现。DDD应用层依赖抽象领域仓储，领域对象不依赖JPA/MVC。共享接口、持久化技术及前端，不共享具体业务决策实现。<br>验证场景：[配置只启用一种后端策略](../backend/src/test/java/com/municipal/rbaccore/architecture/R19BackendStrategyTest.java#L22)；[未知策略在连接数据库前拒绝](../backend/src/test/java/com/municipal/rbaccore/architecture/R19BackendStrategyTest.java#L61)；[验证两套实现共用一个后端工程](../backend/src/test/java/com/municipal/rbaccore/architecture/R19R21StructuredBoundaryTest.java#L36)；[校验领域、仓储与应用层的依赖边界](../backend/src/test/java/com/municipal/rbaccore/architecture/R19R21StructuredBoundaryTest.java#L46)；[共享用例端口不依赖持久化或 Web 类型](../backend/src/test/java/com/municipal/rbaccore/architecture/R19R21StructuredBoundaryTest.java#L115)。 |
| R22 | 初始化、运行与验收使用明确隔离库，不修改原业务库、Excel或历史日志；启动仅校验结构，不自动建旧表、导入或重授角色。构建、初始化与限制须可复现。<br>验证场景：[导入启动拒绝不安全的数据库配置](../backend/src/test/java/com/municipal/rbaccore/config/R03ImportConfigurationTest.java#L18)；[构建与部署使用当前运行配置](../backend/src/test/java/com/municipal/rbaccore/architecture/R19LegacyRemovalBoundaryTest.java#L105)；[独立导入命令拒绝旧库和自动重建结构](../backend/src/test/java/com/municipal/rbaccore/r22/R22ImportDatabaseBoundaryTest.java#L84)。 |

性能只约束万人场景必须分页、数据库范围筛选及避免候选全量/N+1，不虚构响应时间、吞吐量或SLA。JaCoCo保留六项零遗漏检查；双策略干净回归、生产字节码一致后合并，功能测试通过不能代替覆盖率检查，100%也不代表正式流量覆盖。

## 5. 业务用例与验收边界

| 用例 | 参与者/前置 | 结果与异常 | 需求 |
| --- | --- | --- | --- |
| 登录管理端 | 工号或手机号、口令；账号唯一且有有效RBAC登录权限 | 建立同一身份；错误/歧义401，无登录资格403 | R04、R07—R09 |
| 查询组织人员 | 具对应实际API的管理者 | 返回本人范围树及分页人员；范围外403 | R02、R10 |
| 调整人员 | 有对应API、目标及候选均获准 | 整体授角色/停用/调动，拒绝越权或不完整维护者结果 | R11—R12、R15 |
| 维护角色和资源 | 共同根及每个实际操作API | 共享定义、显式映射、树规则；失败全回滚 | R13—R15 |
| 查询鉴权日志 | 共同根及日志API | 脱敏快照/游标，部门即误授仍拒绝 | R16、R20 |
| 离线新项目初始化 | 原始资料、显式独立库与秘密配置 | 导入、只读预检、首次五角色，重复执行保留变更 | R01—R06、R22 |

成功、拒绝、缺失对象、并发及回滚都属于用例验收，详见[验证矩阵](testing-acceptance.md)。本说明不新增注册、密码重置、组织层级调整、角色权限继承、直接用户权限、归档导出或课程业务需求。部门增删改名按R24限定；两系统初始范围及后续动态扩展按R28，真实课程接入按R29；课程生产页面继续使用老师工程，不在RBAC实现业务页面。

## 6. 质量与演进约束

| 关注点 | 设计/验收要求 |
| --- | --- |
| 安全 | 服务端逐请求判权，写锁后重验，凭据脱敏，任何未知/歧义失败关闭 |
| 一致性 | 关联整体替换、共享根锁、写后至少一个完整维护者，冲突全回滚 |
| 万人数据 | 数据库先范围与筛选再计数/分页，候选先授权过滤再分页，不全量传输或Java截页 |
| 可维护性 | 按层和模块归类、DTO/VO分开、真实CloneFactory、统一错误与日志、版本化契约 |
| 可测试性 | 两策略共用行为契约，真实MySQL覆盖事务/并发，UI与真实HTTP证据分开 |
| 可部署性 | 固定依赖来源、显式初始化、秘密外置、启动只校验、备份与回滚边界可复现 |

不虚构响应时间、吞吐量或可用性SLA；后续性能验收须先约定环境、规模、指标与阈值。覆盖率结果与功能回归分别报告。

## 7. 通用身份与课程接入

### 7.1 身份协议与令牌

| 标识 | 现行要求 |
| --- | --- |
| R32 | RBAC实现老师统一身份鉴权全部8项：`POST /auth/login`、`POST /auth/logout`、`GET /auth/me`、`GET /auth/permissions/{permission}`、`GET /auth/departments`、`GET /auth/departments/{departmentId}/users`、`GET /auth/api-permissions/{operationId}`、`GET /auth/users/{userId}`。另2项统一身份业务审计由独立接入支撑提供，不归入RBAC接口。<br>验证场景：[外部登录 JWT 的签名算法、身份字段和受众](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L528)；[读取外部身份的主体、角色与权限](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L204)；[外部退出即时撤销当前会话](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L553)；[外部部门逐层分页和范围控制](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L579)；[外部部门成员最小字段及直属范围](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L619)；[按应用和操作标识读取实际权限](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L645)；[按短码查询与鉴权结果记录](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L736)；[按用户标识查询时独立验权、返回最小字段及 404](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L364)。 |
| R33 | 管理端和外部应用统一使用HS256身份JWT，仅含sub/aud/iss/iat/exp/jti，不含角色权限；通过Bearer携带。管理aud=rbac，外部aud为单个登记应用，保持管理准入及受众隔离。校验签名、算法、issuer、受众、时效和当前会话；单会话退出即时失效，重启须重新登录，无固定默认密钥，不增加刷新/委托或数据库会话实体。<br>验证场景：[管理端签发和使用 JWT](../backend/src/test/java/com/municipal/rbaccore/r07/R07AuthenticationContractSupport.java#L1302)；[拒绝签名被篡改、无签名或未签发的令牌](../backend/src/test/java/com/municipal/rbaccore/r29/R29ApplicationIdentityContractTest.java#L683)；[受众隔离与单会话撤销](../backend/src/test/java/com/municipal/rbaccore/r29/R33JwtIdentityStoreTest.java#L56)；[到期与错误签发者拒绝](../backend/src/test/java/com/municipal/rbaccore/r29/R32JwtCodecTest.java#L33)。 |

外部目录使用page从1开始、pageSize默认20最多100；不传parentId返回可见范围根，传入时只展开直接下级，按部门名称/编码搜索并返回hasChildren。两个目录接口分别要求本应用实际API映射及本人部门子树范围；用户目录只查直属人员，返回userId/no/name/departmentId/departmentName，不含联系方式或内部角色。未知目标返回协议404。

operationId由通用接入元数据定位方法及模板，不硬编码课程操作或应用白名单。按本应用当前API映射判断全局门禁；对象权限仍由业务系统检查。未知操作404 API_OPERATION_NOT_FOUND；allowed=false为200查询结果。退出可在业务授权撤回后进行，外部204只撤当前令牌，管理端退出保持200。

### 7.2 课程首次身份分配

**R31**：总公司本部（含本部职能部门、项目，排除分公司及其下属）的领导和普通员工首次授teacher，其余员工授student。启用、可信根直属、有效基础级别5的RBAC管理员同账号兼course-admin，初始admin同时绑定课程教师。分公司领导的RBAC管理级别不改变其课程学生身份。

验证场景：[本部领导及普通员工均分为教师](../scripts/tests/test_course_initialization.py#L52)；[分公司领导分为学生，同账号兼课程管理员](../scripts/tests/test_course_initialization.py#L58)；[角色或显示资料变动不改变首次来源摘要](../scripts/tests/test_course_initialization.py#L105)；[匹配的课程身份绑定保持原样](../scripts/tests/test_course_initialization.py#L118)；[员工来源变化使回执失配](../scripts/tests/test_course_initialization.py#L112)；[初始化管理员必须保持有效 RBAC 资格](../scripts/tests/test_course_initialization.py#L78)；[部署时在原业务主键上修正课程身份绑定](../scripts/tests/test_deployment_bindings.py#L47)。

独立初始化同时核对员工来源“分公司”列与可信组织父链，以RBAC user_id绑定课程Teacher/Student。默认产品启动和身份开关不批量授予身份。先完成RBAC资料与五角色，再显式预览/执行课程接入；回执匹配时重复执行保留后续角色、权限、状态及业务身份维护，来源或配置变化拒绝自动覆盖。两库分别提交，不宣称分布式原子事务。命令和恢复边界见[课程接入](course-integration.md#4-首次身份分配与恢复)。

### 7.3 部署与设计约束

服务器两个后端均采用DDD；本地保留两策略验证能力。部署采用独立新库/新卷、Secrets、固定依赖来源，初始化与产品启动分离。原系统清退前备份核验，原卷、原Excel、老师原工程和历史日志保留；老师dump仅导入新空课程库，首次校正数字subject并保留业务主键。

当前会话索引在RBAC进程内，后端各单副本、stop-first，不宣称高可用。C只公开课程80及RBAC8088，数据库、Java、监控端口留内网。日志网页和Java展示采用北京时间，存储/传输及JWT时效继续使用UTC。监控按老师SpringAOP示例采集宿主机CPU，采用1秒采集、2秒CPU窗口及规则计算间隔；设计与运行状态分别见[监控](../deploy/monitoring.md)和[验收](testing-acceptance.md)。

双策略类图应并列反映真实源码；顺序图覆盖全部实现差异，共同流程不画双份，同一差异模式可明确复用。外部详设PDF、PPT和Markdown讲稿仅在用户明确要求的桌面目录同步，不提交Word中间件。

## 8. 测试阅读与编号

需求编号标识验收条目。每条需求内的“验证场景”写明链接所覆盖的行为，优先跳转到具体测试方法，跨多项场景的测试也可链接到对应文件。一个用例可以验证多条需求，各行只描述与该行相关的断言。比如工号和手机号登录同一账号的用例同时验证R04的账号标识与R07的登录规则。

测试方法名、DisplayName和目录可能沿用早期的模块或需求编号。追溯时以本页的场景和代码中的实际断言为准。共享HTTP契约在structured和DDD配置下分别执行，数据库约束、架构检查、初始化脚本和浏览器交互分别提供所标明的验证证据。构建后浏览器测试运行真实 Vue 页面，使用受控 HTTP 数据验证交互。执行结果及环境见[测试与验收](testing-acceptance.md)。
