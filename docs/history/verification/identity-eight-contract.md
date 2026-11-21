# R32完整8项统一身份鉴权验证

日期：2026-11-21。任务起点`0f1848ebd796ac9b48190c05eee976874545811e`，业务实现提交`29ab1d20de85252c19b0eedbfe34a15c72a658eb`，任务分支`codex/identity-eight-contract`。用户随后明确授权有效分支合入main，PR #38/#39/#40已按依赖顺序合入，并由`68ace41`将远端main纳入本轮验证分支。后续提交只补契约错误枚举、操作别名、文档/图件及验收探针；不以之前195项成绩代替本轮结果。

## 1. 交付范围与边界

依据[需求R32](../../requirements.md#9-完整统一身份鉴权契约r32)及[完整身份设计](../../design/identity-eight-contract.md)，交付登录、退出、本人、短码判断、部门目录、部门直属用户、operationId权限判断、按统一身份查人共8项。新增的是退出、部门、部门直属用户及operationId权限判断4项，RBAC总操作数由33增至37；原29项管理操作和完整K17保持。

外部应用使用标准HS256 JWT，JOSE/Nimbus校验签名、issuer、单受众、主体与严格到期边界；只含身份和会话字段，不含角色权限快照。管理端仍独立随机Bearer，双方不能混用。进程内会话索引实现单令牌即时退出和重启失效；业务授权实时读数据库。目录按当前人员部门子树控制，逐层返回直接子部门，直属用户只给最小5字段；page从1、pageSize默认20最多100。查询和总数在同一数据库快照中计算。

operationId由显式`IDENTITY_OPERATIONS_FILE`登记，不推断应用或URL。课程目录包含真实课程操作及老师/本项目身份operationId别名，共91条；混合入口的配置角色按permissionRoles判断全局门禁，对象关系仍由课程Service校验。未知操作404，重复/损坏配置阻止启动。统一身份审计另2项依用户澄清不纳入，独立本地支撑继续保存原课程业务审计。

不新增Teacher/Student实体、应用白名单、数据库表或升级SQL，不扩大自定义角色委托能力，不补老师已记录的5个Controller缺口、个人制批改等业务。详设Word/PDF/PPT及讲稿保持原版，不部署服务器、不关闭T14/T15。

## 2. 环境与执行

Windows、JDK21.0.13、Maven3.8.5、MySQL8.4.8，真实CloneFactory0.0.2。沿用R31本机独立实例：structured为3351、DDD为3352，各有6个回归专库及独立`rbac_core`。原3349/3350、32人夹具、万人库和历史日志保留。

完整回归命令为`mvn -B -ntp -o -f backend/pom.xml clean package`，显式设置各策略和6专库环境变量。默认JAR身份探针用`scripts/verify_identity_contract.py`，课程场景用`scripts/verify_course_initialization.py`；后者对R31旧库用`--initialization-catalog`指向原目录快照，以保留原回执，不用新增目录覆盖旧完成记录。

本轮只在两验收库显式登记IDR/IDU对应2权限、2API及管理员角色关联，无重导入、无重置原有角色集合。逐一复核10001人的角色、课程绑定及独立密码哈希，仍为191教师（190员工与admin）、9810学生、1名同账号兼任管理员。

## 3. 本轮结果

| 检查 | 结果与证据边界 |
| --- | --- |
| TDD | 新增身份行为8项先RED后GREEN；JWT严格到期单测先RED后GREEN；gateway真实HTTP stub先404 RED后修复GREEN |
| structured完整MySQL回归 | 208项，失败/错误/跳过均0，clean package成功 |
| DDD完整MySQL回归 | 208项，失败/错误/跳过均0，clean package成功；采用`full-regression-resumed.log`，暂停中断的旧日志不作通过证据 |
| 独立JAR身份HTTP | 两策略各61次通过，覆盖8项直连/转接、签名篡改、受众隔离、目录范围与最小字段、分页、别名、退出即时失效、管理API停用/恢复后原JWT即时生效 |
| 独立JAR课程HTTP | 两策略各43次通过，逐人核10001绑定及口令哈希；真实管理员、总部员工/领导、分公司员工/领导和授撤权场景 |
| Python | 25项通过，包括gateway真实HTTP stub转接和204空响应 |
| 浏览器 | 第一组正式Vue24项通过；课程“添加教师”部门树、直属人员搜索选人另在127.0.0.1及localhost均通过，未提交创建教师。未将第二组HTTP结果称为第二组完整浏览器复验 |
| RBAC前端 | 27项测试及生产构建通过 |
| 老师Vue副本 | vue-tsc类型检查与生产构建通过，无Vue源码修改 |
| CloneFactory | 两策略生成文件SHA256相同；53个非空重载、185组可变同名同类型字段无遗漏；final/record继续显式构造 |
| Java注解 | 生产/测试词法独行检查0项；旧简易正则的TYPE_USE候选不作违规结论 |
| 机器契约 | OpenAPI3.1校验通过，YAML=JSON；37操作、31路径、71 Schema、22错误、K17；15个POST/PUT/PATCH中13个有body，另2个为退出操作，空对象body为0 |
| 真实响应Schema | 两策略共22个实际HTTP响应校验通过，覆盖全部8项、204空响应及3种新增404错误；不输出或保存JWT |
| 文档链接 | 本轮变更Markdown的285个本地目标/标题锚点均存在，git diff --check通过 |
| UML | 更新DDD身份类图，新增structured身份类图与两份目录差异顺序图，同源puml/svg/png；核实际类/方法并视觉检查，共享HTTP/JWT/SQL不在两份差异图重复绘制 |
| 老师目录 | 原目录工作树干净，副本相对于原接入来源仍仅3个structured Java补丁；原目录后来独立更新，未用旧来源摘要错误宣称其全部文件未变 |

原始报告在忽略目录`.local/identity-eight-20261121`：各策略`full-regression*.log`、Surefire、JAR、`identity-http.json`、`course-http.json`；公共`python-tests.log`、`browser-result.json`、`directory-browser.json`、`mapping-check.log`和`artifact-check.json`。凭据、数据库、完整日志与构建产物不提交。

## 4. Apifox同步与回读

用户明确授权同步项目9042431，模块8887289，主分支8854997。写前导出备份；用Node fetch官方导入，新增4接口、更新7接口、新增5模型、更新2模型及新增JWT组件；endpoint/schema/securityScheme失败均0，未删除其他资源。平台保留旧鉴权及模块版本元数据后，经CLI完整读取、Schema校验，再显式修正7项JWT入口及模块信息。

最终官方导出回读为1.4.0、37操作、71模型，全部8项在“统一身份鉴权”目录，匿名login、JWT鉴权和204空退出响应均一致。比较器展开公共参数/响应引用并忽略平台装饰，操作、模型、鉴权组件及info的语义差异为0。证据为`apifox-before.json`、`apifox-import.json`、`apifox-final.json`及`apifox-comparison.json`。公共文档入口为[本项目Apifox](https://lmbnuppe75.apifox.cn)，不写入老师共享项目。

## 5. 审核与合入

按code-review skill进行fresh-context双轴静态审核：规范初审0项；需求初审2项P2（公共Error缺3个新404编码、现行文档与图件未完整同步），均修复。收尾复核继续修正旧接口总述/README、HTTP错误状态说明、R32链接及类图标签位置，最终规范轴0项、需求轴0项。审核不冒充独立测试执行。用户已授权PR #38/#39/#40及本轮有效分支合入main，旧UI分支用祖先关系核对，分支暂不删除；GitHub合并状态以实际PR为准。
