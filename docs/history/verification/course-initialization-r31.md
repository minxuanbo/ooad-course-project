# R31课程首次身份与CloneFactory0.0.2验证

日期：2026-11-21。任务起点为f70ec359f04949c2fe33d3bf562867d0a86ca52f，工作分支codex/course-identity-initialization。本记录只报告本轮实测；不合main、不关闭T14/T15、不推Apifox、不部署服务器。

## 1. 变更及来源

用户明确本部含职能部门和项目，排除分公司子树；本部员工包括领导与普通员工均teacher，其余student，RBAC管理员兼course-admin。规则由独立Python接入入口及配置执行，不加入RBAC核心实体/应用服务。默认导入和五角色授权仍只登记RBAC。

用户提供的spring-fundamentals工作树干净，提交40583ff94764d2e758bcb13be17f5c05ea4d6352，CloneFactory实际版本0.0.2。从git archive源码副本执行clean install，老师5项测试通过；普通provided依赖与annotationProcessorPaths统一引用0.0.2属性。新版支持record/final构造器映射；本项目现有不可变领域映射继续显式构造。

课程运行副本仍固定原19fbd1a及既有3项structured修复，没有升级老师整套业务。核对时老师原目录已由外部更新到e9a6dff（部署说明），工作树干净；因此不能声称原目录仍与旧19fbd1a逐文件相同，本轮不采用该部署说明开展部署。

## 2. 独立环境与首次数据

| 组合 | MySQL | RBAC | 课程structured | 身份及业务审计支撑 |
| --- | ---: | ---: | ---: | ---: |
| structured | 3351 | 8081 | 8080 | 8085 |
| DDD | 3352 | 8083 | 8084 | 8086 |

两套全新本机实例均有独立rbac_core/course_project及六个回归专库。原3349/3350、原32人夹具及历史日志保留。正式Vue5173/老师Vue5174当前连接第一组，mock关闭。

两套均实际完成原Java万人导入、五角色授权、老师Flyway及新课程接入，逐人核对10001账号的角色与user_id绑定：190名本部员工加admin共191教师、9810学生、1名兼任课程管理员。10001个BCrypt10哈希各不相同。首次万人BCrypt导入在CloneFactory升级前使用既有已验JAR完成；升级后新JAR实际完成两库10001人重复导入及首次授权回执核对，不将其误记为新版全新万人导入。

## 3. 本轮实测

| 检查 | 实际结果 |
| --- | --- |
| 新初始化TDD | policy-red.log及safety-red.log保存行为RED，随后新增14项GREEN |
| Python课程辅助测试 | 新14项加原8项，共22项通过 |
| structured真实MySQL完整回归 | clean package，195项，失败/错误/跳过均0 |
| DDD真实MySQL完整回归 | clean package，195项，失败/错误/跳过均0 |
| 新0.0.2独立JAR HTTP | structured43次、DDD51次，全部符合预期；含预期401/403/404 |
| Chrome真实生产Vue | 第一组24项检查通过，18张页面截图；第二组由真实HTTP覆盖，未宣称双组浏览器复验 |
| RBAC前端 | 27项测试及生产构建通过 |
| 老师Vue | vue-tsc及生产构建通过 |
| CloneFactory生成专项 | 两策略生成文件SHA-256相同；53个重载均非空，194处赋值，185组可变同名同类型字段无遗漏 |
| 注解扫描 | 3个候选均为误报（2处@interface声明、1处Column字符串），实际同行注解0项 |
| API | OpenAPI YAML/JSON同源，仍33操作、27路径、66 Schema，17项完整K；本轮无新增HTTP接口 |

HTTP覆盖同一admin双系统登录、本部领导与普通员工教师、分公司领导与普通员工学生、错误身份入口拒绝、普通教师不能读全局目录、跨受众令牌拒绝。通过课程生产API建立演示学期、开放分组课程、教学班、两名任课教师、两名选课学生及作业里程碑。重复保存组长保持关联，教师读名册，学生读开放里程碑，管理员读教学班和独立持久业务日志。

真实RBAC撤回学生角色LOGIN后，原课程token下一请求403；此时重跑初始化跳过，仍403；恢复原完整权限后原token200。重复初始化不覆盖运行期授权。structured本轮复用了升级前已建的演示课程，故少8次创建请求，覆盖场景相同。

新JAR SHA-256：

- structured：`7f51e313290808b6222cff0fedfbea53936bece83bbda0808754d5ad9741e93a`
- DDD：`09684fb36ba7313802431a9346423264e633a352d6a469e84cb153f111dc346b`
- 两策略生成CloneFactory：`d34cb572a58e5c0cf3408b45813dba7acbdb167eec1d98837b1e33b7c53490e6`

## 4. 证据与限制

原始日志、报告、JAR、截图与秘密均在忽略目录`.local/course-initialization-20261121`。主要文件：python-tests.log、clonefactory-build.log、mapping-scan.log、browser/result.json，以及各策略的clonefactory-002-full.log、course-preview/applied/repeated.json、http-clonefactory-002.json、repeat-002-ImportCommand.log和repeat-002-BootstrapAuthorizationCommand.log。含凭据的配置不提交或在聊天输出。

首次接入在开放业务前维护窗口串行执行，课程及RBAC分别提交，不是分布式事务；运行期调动、后续管理员变更不自动重推课程身份。旧32人夹具不是升级目标。老师5项Controller缺失、个人制批改、学生建组归属和DDD当前学期等限制继续见[课程接入第7节](../../course-integration.md#7-老师项目的实际限制)。本轮没有扩写这些业务，也未测得吞吐量或服务器部署结论。
