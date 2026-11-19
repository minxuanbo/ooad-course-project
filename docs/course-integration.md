# 课程系统本地联调

本轮由用户于2026-11-19明确启动，替代此前“课程接入暂缓”的排期限制。RBAC起点为main `717345f`，任务分支为`codex/course-local-integration`。老师工程来自本地`course-project-mgt`，提交`19fbd1a`；使用其现有生产前端和HTTP客户端作为T14的契约/交互依据，不另造课程业务系统；原目录保留，联调副本应用[最小教师名单补丁](../integrations/course/README.md)。

## 1. 范围与身份边界

- 阶段6的约定双策略验收已完成，见[阶段6记录](history/verification/stage6-ddd.md)；本轮新增联调证据单独记录。
- T14对应Issue #15：选定真实操作、确认身份和接口。T15对应Issue #16：完成真实允许/拒绝、授撤权及跨系统隔离演示。联调通过不自动关闭票或代表小组最终审核。
- 正式资源目录只允许RBAC和明确接入的`course-project-management`；原型18业务系统仍不接入。
- 接入通过`APP_COURSE_INTEGRATION_ENABLED=true`显式启用，默认关闭。两套RBAC业务实现使用相同契约。
- RBAC保留原本地工号/手机号、BCrypt和随机Bearer。课程身份使用同一账号凭据，但令牌受众与RBAC管理端隔离；课程登录不能绕过管理端准入。
- 课程身份必须显式授予`COURSE_TEACHER`权限，协议中映射为老师定义的`teacher`身份。它不授予RBAC管理级别，也不根据员工职位推断教师身份。课程自己的Teacher记录和对象权限继续由课程系统维护。
- 本轮先用隔离库及虚构员工验证课程查看、学期维护和菜单/按钮；不自动把一万名员工全建为教师或替换真实业务资料，不新增课程的部门数据范围。

## 2. 老师现有协议

课程后端每个Bearer请求通过`UnifiedIdentityClient`实时查询身份，不读取令牌中的权限快照。配置`IDENTITY_BASE_URL`指向RBAC，课程原有Controller/Service及功能权限守卫保持原职责。最小补丁使每个教师请求继续验证本地未删除Teacher，不能绕过课程登录页直接持身份令牌访问；学生路径不在本轮扩展。

| RBAC兼容端点 | 老师已有调用与返回 |
| --- | --- |
| `POST /auth/login` | 输入`username/password/systems`，systems只接受单个课程编码；返回`accessToken/tokenType/expiresIn` |
| `GET /auth/me` | 返回`subject/roles/permissions`，全部取当前有效关系；不使用RBAC统一包裹格式替代老师协议 |
| `GET /auth/permissions/{permission}` | 课程生产前端已直接调用，返回`allowed`；未知权限失败关闭 |
| `GET /auth/users/{userId}` | 课程添加教师时已调用；要求课程UL权限，仅返回`userId/no/name` |

这些只是身份/权限兼容接口。课程的`/terms`、`/admin/courses`等业务HTTP处理器仍只在老师工程，RBAC仅登记其真实方法/模板。

前端菜单/按钮短码和后端`AuthorityCodes`分别登记，不能把菜单显示当作业务授权。后端短码只有其自身权限有效、同系统映射且对应全部路由启用时才导出；同一短码覆盖多条路由时，只要一条失效即不导出，避免老师的粗粒度守卫误放行该路由。父菜单不隐含按钮或API。

## 3. RBAC维护约束

角色定义仍只能由具实际RBAC权限及可信根范围者维护；人员角色候选和保存均检查当前完整权限子集、授予级别及目标保护。启用接入后子集含上述两系统有效权限；未知系统污染角色仍拒绝。跨系统Permission—API映射仍拒绝。完整维护者K保持原17项RBAC路由，不以课程权限替代。

首次结构继续使用现有两份SQL，不新增账户/会话/管理员表。课程目录及虚构Teacher绑定属于显式联调夹具；正常应用启动不自动导入或授予。

## 4. 真实路由与权限目录

`CourseContract`归口老师协议取值：16个后端短码、19条唯一API及20条权限—API关联；4个菜单、12个按钮及1个显式教师标记，共33个权限。老师只声明了CRS常量，没有reset业务端点，本轮不登记或导出CRS。TCM、CV、CTM、TEC分别覆盖两条真实路由，不能只核对其中一条。TEC还保护老师身份查询入口，该调用链需TEC和UL同时有效；共享的GET /auth/users/{userId}只建一条API，再分别关联TEC与UL。完整列表由源码与[夹具生成器](../scripts/course_integration_fixture.py)共同核对数量，联调SQL不是手工臆造的其他系统接口。

所选验证场景为CV课程查看、TC/TE/TD/TCM学期维护、MCO/MTR菜单及相应按钮。教师本地对象约束仍由老师系统判断。AV/MLG虽保留来源目录，在夹具中停用且不授给联调员工；老师额外依赖的`/auth/audit-logs`不在本轮适配范围，不返回虚假成功。名册、分组、里程碑、成绩等依赖外部审计的业务，以及学生、语音转写和文件存储，均未作为本轮端到端验收场景。

## 5. 本地复现配置

只对独立空库执行下述夹具，不得对原始员工库执行。原RBAC首次SQL与老师Flyway保持原样：先在独立MySQL准备`rbac_core`和`course_project`；RBAC执行两份首次SQL，课程JAR使用`spring.flyway.enabled=true`与`spring.jpa.hibernate.ddl-auto=validate`启动并建立其自有表。正常万人初始化说明保持不变，本轮虚构夹具不替代真实来源导入。

老师工程使用本地提交`19fbd1a`的可构建源码副本；保留其pom、迁移和Vue；副本仅改两个生产安全配置/过滤文件，增加一个测试并适配一个既有测试，见[补丁清单](../integrations/course/README.md)。其pom要求Java21、Boot4.1.2、Lombok1.18.48、CloneFactory0.0.2；后者从老师[spring-fundamentals](https://gitee.com/mingqcn/spring-fundamentals)提交`38fd81730e7294bb3a1ef708faa78996b3c7fa80`的`clonefactory`子项目安装到本机Maven仓库，不伪装依赖版本。RBAC继续使用自身已锁定的真实0.0.1-SNAPSHOT。

本轮课程后端最终采用其structured策略，RBAC的structured和ddd分别联调。老师DDD在有当前学期时替换到另一学期会返回409 TERM_CURRENT_CONFLICT，保留独立复现证据且不修改老师业务；不能将该模式的整个学期用例宣称通过。

| 进程 | 必要配置与本轮地址 |
| --- | --- |
| RBAC后端 | `APP_BACKEND_STRATEGY=structured`或`ddd`；`APP_COURSE_INTEGRATION_ENABLED=true`；`RBAC_CORE_DB_URL/USER/PASSWORD`指向隔离库；`COURSE_UI_ALLOWED_ORIGINS=http://127.0.0.1:5174`；HTTP8081 |
| 课程后端 | `APP_BACKEND_STRATEGY`独立选定；`DB_HOST/PORT/NAME/USER/PASSWORD`指向课程隔离库；`IDENTITY_BASE_URL=http://127.0.0.1:8081`；`--server.port=8080` |
| RBAC前端 | 正常`npm ci`、`npm run dev`，API_TARGET8081；页面5173 |
| 课程原始前端 | 正常`npm ci`；`VITE_API_BASE_URL=http://127.0.0.1:8080`、`VITE_IDENTITY_BASE_URL=http://127.0.0.1:8081`、`VITE_MOCK_ENABLED=false`；`npm run dev -- --host 127.0.0.1 --port 5174 --strictPort` |

通过秘密环境提供`UI_FIXTURE_PASSWORD`，安装Python bcrypt后在仓库根运行：

```powershell
python scripts/course_integration_fixture.py --output-dir .local/course-integration/fixtures
# 将新生成的rbac-fixture.sql、course-fixture.sql分别导入上述两个专库。
python scripts/verify_course_integration.py --fixture --report .local/course-integration/actual-http.json
# 需要已安装Chrome与frontend锁定的Playwright开发依赖。
node scripts/verify_course_browser.mjs
python -m unittest discover -s scripts/tests -v
```

SQL生成器只写新文件，不连接数据库；操作者须确认目标库为空再导入，不提供清库选项。虚构管理员`ui-admin`；`ui-user-05`有显式教师身份和学期/课程权限；`ui-user-06`仅教师身份；`ui-user-07`虽有课程本地Teacher但没有RBAC教师准入。RBAC普通员工管理准入仍拒绝。

HTTP脚本会真实停用虚构账号06，正式产品只支持停用、不提供恢复。本地重复运行前仅在该隔离夹具库核对ID/工号后恢复06；不能调用不存在的“恢复账号”API或扩大正式功能。脚本清理本轮新建学期并恢复课程角色集合；如中途中断，先核对报告及关系/映射状态，再使用新专库重建或在明确夹具范围恢复。

## 6. 验证与改动记录

实际证据与覆盖限制单列在[本地联调验收](history/verification/course-integration-local.md)。本设计不等于T14/T15小组评审或整体课程验收，不自动关闭Issue。课程完整业务审计不以最小身份鉴权日志代替。
