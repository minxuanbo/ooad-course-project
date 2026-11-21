# 课程系统本地联调

本轮使用老师`course-project-mgt`提交`19fbd1a`的生产前后端，RBAC基线为main `717345f`，在`codex/course-local-integration`分支调整通用应用能力。老师原目录保留不动。接口和实际测试证据分别见[OpenAPI](../api/openapi.yaml)与[本轮验证](history/verification/course-integration-generic.md)；此前教师最小试验记录仅为历史，不代表本轮全部功能通过。

## 1. 系统职责

RBAC负责User—Role—Permission—ApiResource及Application目录。角色全局共享，可以组合多个应用权限；每项Permission与其ApiResource映射必须同系统。Application从数据库登记数据读取，不维护`rbac/course`代码白名单。原型的18个演示系统仍不自动导入。

教师、学生与课程管理员均为角色配置。配置`teacher`、`student`供老师原协议识别；另建`course-admin`组合管理菜单、按钮和后台权限，同时授teacher资格。LOGIN是本地课程目录中的资格权限配置，不是RBAC内置的教师/学生标记。RBAC只返回当前有效角色编码与权限，不识别课程对象。万人首次授权仍只配置本RBAC自身，课程角色通过独立步骤明确授予；2026-11-21的本部教师、分公司学生与同一管理员规则见第8节，不给全体员工统一加教师。

RBAC管理端仍要求本系统实际管理API，普通员工即使有课程角色也不能登录管理端。课程沿老师`POST /login`：先确认其本地Teacher/Student，再校验外部身份、角色及user_id绑定。课程、选课、教学班、组长、提交、评分等业务和对象权限均由老师工程维护，不增加RBAC的Student/Teacher实体、岗位猜权、课程准入策略或课程业务API。

## 2. 人员与角色的最小规则

自定义角色不提升管理级别，也不使持有人整个人被冻结。人员的停用、调动和角色变更仍受有效级别、本人/同级/上级保护和部门范围限制。

角色整体替换在共同根锁后重读数据库当前集合，对新增、删除分别验权；不可改变的现有项可以原样保留。自定义角色只由可信根直属有效五级管理员增删，不增加委托授权或“可授角色清单”机制。前端根据lockedRoleIds显示锁定项，客户端不能借此伪造保留，非法变化整次回滚。

## 3. 通用身份协议

设置`APP_IDENTITY_ENABLED=true`开放四项通用协议。成功响应保持老师既有原始JSON，错误沿RBAC稳定code/message/requestId。`IDENTITY_ALLOWED_ORIGINS`只配置实际前端来源。

| 端点 | 行为 |
| --- | --- |
| POST /auth/login | username/password/systems；systems为单个登记外部应用；当前有效授权非空才签发独立受众随机Bearer |
| GET /auth/me | subject、实际roles、当前permissions、服务端system；每次查最新账号/角色/权限/系统状态 |
| GET /auth/permissions/{permission} | 仅检查当前受众的有效权限；未知或撤回返回allowed=false |
| GET /auth/users/{userId} | 先检查本应用对此真实API的有效映射，再返回userId/no/name最小投影，不写死UL或TEC |

Permission.type为MENU/BUTTON/API。菜单、按钮可无API独立控制展示或资格；API权限必须具有非空映射且全部映射同系统启用，否则不导出短码。这避免清空映射后仍凭短码放行。老师粗粒度短码覆盖多路由时全部一并登记；API映射、角色授撤权及启停提交后，旧令牌下一请求重算。系统受众隔离不依赖前端隐藏菜单，K保持原17项RBAC维护路由。

## 4. 本地外部审计依赖

老师实际调用统一身份的`/auth/audit-logs`追加和查询。该依赖原本不存在；不能把课程业务快照写成RBAC鉴权日志，也不能让mock返回虚假成功。本轮用独立[本地支撑工具](../integrations/course/local_identity_gateway.py)转接身份并将真实审计事件持久保存到忽略目录SQLite。此工具为本地测试基础设施，不属于RBAC核心业务，也不是服务器部署方案。

课程后端的IDENTITY_BASE_URL和课程前端的VITE_IDENTITY_BASE_URL指向本地支撑8085；它实时转发RBAC8081，核实system受众，不缓存权限。读日志要求配置的AV全局权限，写日志要求LOGIN配置权限；匿名、其他受众、撤权及上游故障都拒绝。requestId按应用分区幂等；主体由已认证身份确定，时间由服务端生成。日志是调用者上报的业务事件，不冒充对课程事务的独立证明。

本地支撑只读课程数据库补充termId/courseId/classId，业务写入仍全部经老师接口。普通教师不能绕过课程服务直接读取全局日志；本轮验证的是课程管理员日志工作区。教师受对象范围限制的外部日志读取能力未另行实现，不用全局放行替代。持久文件、数据库配置、账号口令、生成SQL和浏览器截图均不得提交。

## 5. 场景与账号

本节为2026-11-20以前的32人历史回归夹具，不是R31万人首次接入的身份分配结果。原夹具保留以便复现既有授撤权和拒绝场景。账号口令从本地私有配置或环境传入，不在文档保存。

| 账号 | 用途 |
| --- | --- |
| ui-admin | RBAC系统管理员；管理角色定义及课程授权；不因该称谓自动成为课程本地教师 |
| ui-manager | 部门经理；可管理范围内较低级人员，课程自定义角色显示锁定 |
| ui-user-05 | teacher；课程组长/任课教师，可按课程自身对象关系维护教学 |
| ui-user-06 | teacher；没有全局课程管理权限的对照账号 |
| ui-user-08 | teacher + course-admin；从教师入口登录，可用学期、课程、教师和日志管理工作区 |
| ui-user-09 / ui-user-10 | student；已选个人制与分组制两门开放课程，可组队、提交、预约、查看成绩 |
| ui-user-07 / ui-user-11 | 本地有教师/学生记录但未授RBAC应用资格，用于拒绝验证 |

夹具含32个RBAC账号、4名本地教师、3名本地学生、基准学期、个人/分组两门开放课程、两教学班及4个里程碑。学生可以组队；教师建组可明确负责班级。老师的学生建组自动负责班级归属尚未完成，口试完整链使用老师既有教师建组入口，不通过SQL伪造接口成功。真实探针保留虚构作业、文件、成绩及审计供人工体验，临时学期和申请课程尽量清理；中断后的虚构资料保留并说明，不重置真实资料。

## 6. 复现与验证

1. 准备独立MySQL实例及空rbac_core/course_project，记录端口；原有库全部保留。RBAC执行当前两份首次SQL；课程按原Flyway建表。只对空库生成并导入`scripts/course_integration_fixture.py --output-dir .local/your-run/fixtures`的两份SQL，先通过UI_FIXTURE_PASSWORD提供虚构口令。
2. 老师工程从原提交复制到忽略目录，按[integrations/course](../integrations/course/README.md)应用必要修复补丁，保留原目录。Java21、Boot4.1.2、Lombok1.18.48、CloneFactory0.0.2沿老师pom；历史课程构建使用真实38fd817源码。2026-11-21本RBAC依用户提供源码更新为真实40583ff提交的0.0.2，构建指引见[依赖安装](../deploy/README.md#本机依赖安装)，不是改名旧jar；不因此替换已验课程副本。
3. RBAC设置数据库凭据、APP_BACKEND_STRATEGY=structured或ddd、APP_IDENTITY_ENABLED=true、IDENTITY_ALLOWED_ORIGINS=http://127.0.0.1:5174，启动8081。不要并行用两策略修改同一测试库。
4. 启动本地支撑：`python integrations/course/local_identity_gateway.py --database .local/your-run/audit.sqlite3 --port 8085 --origin http://127.0.0.1:5174 --origin http://localhost:5174 --mysql-config <本地只读客户端配置绝对路径>`。客户端配置指向课程实例，数据库名称仍为course_project，文件不入Git。
5. 课程设置APP_BACKEND_STRATEGY=structured、DB_HOST/PORT/NAME/USER/PASSWORD及IDENTITY_BASE_URL=http://127.0.0.1:8085；原JAR8080，Flyway启用、ddl-auto=validate。老师DDD另有当前学期切换冲突，本轮不将其全部功能宣称通过；RBAC两种策略分别联调老师structured。
6. RBAC Vue5173沿原代理8081；老师Vue5174设置VITE_API_BASE_URL=http://127.0.0.1:8080、VITE_IDENTITY_BASE_URL=http://127.0.0.1:8085、VITE_MOCK_ENABLED=false。使用两个正式前端，不使用离线原型或mock替代真实RBAC。
7. 通过环境提供口令后执行`python scripts/verify_course_integration.py --fixture --report .local/your-run/http.json`与`node scripts/verify_course_browser.mjs`；执行Python辅助测试、前端测试/构建及两RBAC完整回归。脚本有真实写入，仅在明确的虚构夹具环境使用。

本机当前实例及实际成绩以本轮验证记录为准。首次SQL不承担已有未知库升级，不交付升级脚本。服务端口8082被Windows保留，因此本轮支撑使用8085，不修改系统保留端口。

### 本机浏览器入口与跨域配置

浏览器把`http://localhost:5174`与`http://127.0.0.1:5174`视为不同Origin。课程Vue直接调用8080；只允许后者时，前者会被浏览器跨域检查阻止，登录页可能只显示“请求失败，请稍后重试”，不能据此判断口令或身份出错。

启动课程JAR前设置`CORS_ALLOWED_ORIGINS=http://127.0.0.1:5174,http://localhost:5174`。独立本地支撑使用上面两个`--origin`；需要浏览器直接访问RBAC通用身份入口时，相应将`IDENTITY_ALLOWED_ORIGINS`设为这两个Origin。继续显式允许本机入口，不使用通配放行。已运行进程需读取新配置后重启对应服务，不必重新导入或授权。

## 7. 老师项目的实际限制

老师OpenAPI包含但生产Controller没有对应方法的业务路径：课程重置申请、单独修改课程description、小组GET详情、个人口试成绩写入、录音转写摘要，共5项。只在常量中出现的CRS不登记为已实现能力，不为其编造RBAC业务端点。

个人制提交的学生上传/读取存在，教师批改列表原实现明确返回COURSE_NOT_GROUP_BASED，个人教师详情/批改链也未完成。不能把它们的不可用解释成RBAC没授够权限，更不能通过提高RBAC级别绕过。学生建组尚缺负责教学班归属，课程管理员与普通教师的对象范围由原工程判断。本轮保留并报告这些来源缺口，不扩写老师尚未完成的业务模块。

T14为Issue #15：真实场景、身份、原型/API契约；T15为Issue #16：两系统授撤权、隔离与证据。本轮不自动关票、不合main、不部署服务器。


权限展示名称采用完整中文（如“查看总览”“分配人员角色”“查询鉴权日志”），稳定编码仍保留供关联、排错和接口使用，不按中文名称鉴权。两套首次授权与本地夹具读取同一RBAC中文目录；课程显示名称也来自独立接入配置，不在Vue按英文编码硬翻译。

通用/auth/me还返回permissionApis，即每个有效权限当前实际映射的方法/模板事实。RBAC不内置接入系统的预期路由；本地课程适配器依据catalog.json核实老师同一粗粒度短码的全部必要路由，缺少一条即保守关闭整个短码，菜单检查也采用相同投影。这样删除多路由权限的部分映射不能借另一条映射继续调用已撤路由，映射变更仍即时生效。

虚构口试里程碑显式设置预约次数上限100，便于重复联调和人工体验；这只是课程已有配置，不修改老师默认值或业务规则。重复探针保留取消/已完成预约历史，不用清库恢复次数。

## 8. 本部教师与分公司学生首次接入（R31）

依据用户2026-11-21转达的老师要求及范围确认，总公司本部包括本部职能部门和项目，排除分公司及其下属。本部领导、普通员工均为teacher；分公司领导和员工均为student。当前启用、可信根直属、有效基础级别5的RBAC管理员兼course-admin；独立admin有teacher及course-admin两角色，用同一账号管理两系统。

显式配置在[integrations/course/initialization.json](../integrations/course/initialization.json)，目录与路由仍在catalog.json；执行入口为[scripts/initialize_course_integration.py](../scripts/initialize_course_integration.py)。它读取已完成RBAC导入的来源和组织事实，交叉核员工原“分公司”列与父链，识别本部而不按岗位猜测。Teacher/Student只写入课程数据库，RBAC仅写通用Application、Role、Permission、ApiResource和关联数据。RBAC固定管理员角色获得课程完整权限子集，以便后续合法授撤课程角色；其他基础角色级别与原RBAC权限保持。

在新本地实例完成RBAC资料导入、五角色授权，以及老师原Flyway建表后，先执行只读预览。客户端ini必须显式指向loopback及端口，密码只存忽略目录；报告必须为.local下的新文件。

```powershell
python scripts/initialize_course_integration.py --mysql <mysql.exe绝对路径> --rbac-client <RBAC客户端ini> --course-client <课程客户端ini> --report .local/your-run/course-preview.json
# 核对人数与范围后，在同一组明确的新库执行。
python scripts/initialize_course_integration.py --mysql <mysql.exe绝对路径> --rbac-client <RBAC客户端ini> --course-client <课程客户端ini> --apply --report .local/your-run/course-applied.json
```

默认不写库。实际执行先在课程库事务建立唯一user_id绑定，匹配的既有绑定只保留，身份冲突或已删除教师拒绝覆盖；成功后在RBAC根行锁内一次发布目录、角色、权限、全部人员角色和完成回执。两系统分别提交，不宣称分布式原子事务：若课程提交后RBAC失败，尚无应用角色授权，中断后可重跑，匹配的课程绑定不重复插入。

回执使用通用bootstrap_receipts表的独立seed_key，不新增业务表或老师Flyway迁移。匹配回执重复执行直接跳过，不恢复被手动撤回的角色和权限；原件来源、账号全集或初始化配置变化会拒绝自动覆盖。此入口不是已有课程库升级或动态组织调动同步，后续身份修改仍由管理端明确授撤，并维护课程本地业务身份。

R31详细职责及维护窗口约束见[初始化设计](design/course-initialization-r31.md)，真实人数、两策略HTTP与界面结果见[独立验证记录](history/verification/course-initialization-r31.md)；不能把历史32人夹具成绩当作新规则通过。服务与数据库仍仅本地运行，服务器方案待老师部署说明。
