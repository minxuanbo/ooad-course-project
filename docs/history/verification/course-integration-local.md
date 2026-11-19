# 课程系统本地联调记录（2026-11-19）

本轮由用户明确启动，基线为main `717345f`，工作分支`codex/course-local-integration`。阶段6原验收已随PR #37合入，不重复以本轮替代；本记录只覆盖R29课程接入。设计、准确契约和复现前置见[课程接入](../../course-integration.md)。T14 #15、T15 #16仍保留小组评审和关闭关口。

## 1. 环境及老师工程变更

- Java21；独立MySQL8.4.8监听127.0.0.1:3349。RBAC业务库和六个专用测试库与课程业务库在该私有实例分开；两套RBAC按顺序运行同一验证，老师后端最终采用structured，未使用原业务库。
- 老师原目录`course-project-mgt`提交19fbd1a保持Git干净；副本在`.local/course-integration/course-project-mgt`。老师原件943个来源文件的SHA-256保持不变。独立审查后，副本有3个既有源码文件变化（两个生产安全文件、一个既有测试），增加1个教师绑定回归类；补丁见[integrations/course](../../../integrations/course/README.md)。pom、Vue、Flyway及业务Service未改。
- 配置只通过环境/启动参数注入：课程IDENTITY_BASE_URL及前端身份地址指向RBAC，课程直连8080，RBAC8081，页面分别5174/5173。未启用Mock身份，没有用stub代替两系统的本轮HTTP结果。
- 老师依赖的真实CloneFactory0.0.2从老师spring-fundamentals提交38fd817安装，未改其版本。RBAC原依赖0.0.1-SNAPSHOT保留。凭据/SQL/日志/JAR/浏览器产物均只在忽略的.local目录。
- 虚构夹具为RBAC32账号、3个课程Teacher及1门课程，不按员工岗位自动授权，不将一万真实员工建成教师。课程目录33个Permission、19个唯一API、20个Permission—API关联；AV/MLG停用并排除联调员工授权，CRS无真实端点不登记。

## 2. 已取得的实测证据

| 检查 | 实际结果 |
| --- | --- |
| R29 TDD | 原行为测试先RED：6项HTTP/目录断言失败、0错误、0跳过；加入兼容实现后通过。后续扩大到12项覆盖准入、受众、即时撤权、同系统映射、多路由、UL、目录、CORS、完整课程子集及未知系统污染 |
| structured完整回归 | clean package，192项，0失败/错误/跳过 |
| ddd完整回归 | clean package，192项，0失败/错误/跳过 |
| 真实双系统HTTP | 审查前RBAC structured＋老师structured、RBAC ddd＋老师structured各55项通过；修复后增加直接身份令牌名单边界及真实UL调用链，最终重跑中，含真实学期新建/编辑/当前切换/删除、角色与人员授撤权、映射/状态、跨系统拒绝及夹具恢复；此前两端ddd的47项最小场景曾通过，扩大当前学期场景后发现老师DDD自身409，不以此前47项掩盖该差异 |
| 两个生产前端 | 两策略均使用相同UI流程通过6项：RBAC真实登录/选课程角色、老师真实登录/读课程、按钮授权、RBAC页面撤按钮后禁用、撤菜单后直达拒绝、恢复授权。截图等待课程数据及其他有效按钮加载，避免把初始加载禁用误当撤权结果 |
| 身份服务故障 | 实际停止本轮RBAC进程，老师课程GET /admin/courses返回503；随后恢复进程，无旧权限放行 |
| RBAC Vue | 26项测试及生产构建通过 |
| 老师原始工程 | 生产JAR及原始Vue构建通过；新增6项教师绑定回归，连同原AOP、身份适配/授权/两类边界共17项通过，未宣称完成老师全业务数据库回归 |
| 机器契约 | YAML=JSON；OpenAPI3.1校验通过；33操作、27路径、66 Schema、19错误；完整K仍17项 |
| CloneFactory | clean生成52个重载，全部含真实赋值；新增CourseLoginDTO→LoginCommand确有password赋值，username→account异名显式转换；全部新增record输出显式构造，无空final映射 |

可核对的本地原始产物位于`.local/course-integration/`：`rbac-structured-full-final.log`、`rbac-ddd-full-final.log`、`structured-http.json`、`ddd-http.json`、`browser/result.json`及PNG、`unavailable.json`、`course-focused-tests.log`。报告只保留步骤/状态，日志遵循既有集中脱敏规则；凭据不进入Git或验收正文。

## 3. 实际允许/拒绝边界

真实请求经过老师原Controller/Service和实时身份HTTP客户端。普通员工有课程准入与所选功能时可以进入课程，仍不能进入RBAC管理端。仅教师身份不能查看管理课程；课程本地虽有Teacher但未获准入时登录403。管理/课程受众令牌混用为401。

角色权限撤回、人员角色撤回、TC映射撤回、API停用均使原课程令牌下一请求403，恢复后原身份再次成功；账号停用后401。RBAC授予另一虚构员工管理角色后可登录/读真实总览，撤回后其旧管理令牌请求403，另一个员工的课程权不受影响。跨系统API映射400，无部分覆盖。

## 4. 限制与交接

本轮验证课程查看与学期维护，不宣称学生、名册、分组、里程碑、成绩、语音/文件或课程审计全部接入。老师课程审计依赖额外`/auth/audit-logs`，本轮没有提供假接口。老师Vue原布局只在装载时取得按钮集合，已打开页面的旧按钮可能保留到再次读取/刷新；服务端的下一受控请求仍按当前授权拒绝，菜单直达守卫实时查询。没有以推送或自动刷新改写老师Vue。

正式账号状态接口只支持停用。HTTP夹具会停用虚构06；重复验证/人工检查前，只能在该专用夹具库核对ID/工号后恢复或另建空库，不能据此宣称产品有启用账号操作。未进行万人教师初始化、服务器部署、Apifox写入或main合并。

老师DDD单独发现：有当前学期1时，对新建的另一学期调用PUT /current-term返回409 TERM_CURRENT_CONFLICT；同样有效TCM在其structured实现返回204。其DddTermService先加载目标，再clearCurrent；JpaTermRepositoryAdapter延后清除旧标记的SQL，到保存目标时一并flush，可能先把目标设为当前而触发uk_term_current_marker。该原因与真实409一致，不按403权限拒绝处理。原始失败步骤见`ddd-to-ddd-expanded.json`，单独复现为`teacher-ddd-conflict.json`。本轮为保持老师源码不动，最终课程服务使用原structured，两种RBAC策略分别验证；修复老师DDD业务属于另行处理项。

最终本地保留课程structured后端8080、RBAC ddd后端8081、RBAC前端5173、课程原前端5174，均用于本机访问；MySQL3349。脚本结束后仅在专用夹具库核对工号恢复06，便于人工比较“教师可登录但没有管理功能”的初始状态。秘密见忽略目录`account-private.json`，不在正文公布。

独立初审发现：规范轴1项解析耦合问题，需求轴3项（含同一解析问题、UL路由错位和本地教师旁路）。已补修复，正在复查；教师名单旁路修复前的真实HTTP RED为200对预期403，UL修复前R29为1失败/0错误，夹具为3个执行错误，修复后夹具及失败恢复4项、课程17项测试通过；TEC多路由新增回归也先以1失败/0错误RED确认，再通过两策略最终192项回归；最终全链路重跑和补充复查完成后补录结论；本记录不作为小组最终批准。

复现交付补充：最终格式化源码可生成夹具，已在预留空库rbac_core_t14_test实际执行两份首次SQL及新生成夹具，得到32用户、2系统、33课程Permission、19课程API、20关联；报告fresh-fixture.json。临时UL流程有独立try/finally，纯内存故障注入先RED（遗留UL）后GREEN（恢复原集合），不把成功路径复验当失败恢复证据。
