# 测试与验收

本页按验证目标组织执行入口与证据。设计文字、打包、静态审核、运行测试及老师验收分别记录；旧版本成绩不替代新修改后的实测。

## 1. 当前状态与证据

当前源码新增R35应用接入向导及数据库操作元数据，并将初始受管范围调整为RBAC和课程。两策略各365项、418份class一致、合并六项100%及原门槛通过；前端54单元/22构建渲染、Python46项通过，Apifox1.8.0的45/37/94语义差异0，见[本轮源码证据](history/verification/implementation-evidence.md#application-onboarding-wizard-20261124)。本轮正式104项HTTP和12组浏览器通过，39次浏览器API均200，七监控目标UP，见[发布证据](history/verification/release-evidence.md#application-onboarding-release-20261125)。最终报告仅保留[当前JaCoCo](reports/jacoco/final-rbac/README.md)。

原应用管理25fdc21的115项HTTP与11组浏览器成绩仅对应[当时版本](history/verification/release-evidence.md#application-management-release-20261124)。此前性能计时只对应其原环境，见[加载实测](history/verification/ui-performance-evidence.md#authorization-loading-performance-20261124)，不能据此断言本轮响应时间。

各版源码及覆盖率证据见[后端档案](history/verification/implementation-evidence.md)，两系统上线与备份见[部署档案](history/verification/release-evidence.md)，UI与性能见[界面档案](history/verification/ui-performance-evidence.md)。历史成绩不混作当前成绩。

## 2. 验证矩阵

| 对象 | 必须检查的行为 |
| --- | --- |
| 初始化 | 原件摘要、110组织/10001账号、独立BCrypt盐、只读预览、重复保留维护、来源完整及整批回滚；课程绑定与有效身份数分开 |
| 登录/JWT | 工号手机号唯一定位；歧义/停用/错口令拒绝；签名/issuer/受众/必填声明/严格到期，无权限快照，单会话退出，重启失效，普通员工不能凭课程角色进入管理端 |
| 实时授权 | 原JWT在角色、权限、API、应用或人员状态提交后重判；有效链OR不误撤；等待写锁者锁后重鉴权 |
| 人员与组织 | 子树范围、本人/同级/上级保护与管理员例外、角色差集、lockedRoleIds、自定义项资格、调动双范围、类型/空节点/人数 |
| 共享定义与并发 | 直接权限集合替换、同应用映射、树不变量、共享根锁、同一人完整K17、并发与故障全回滚 |
| 查询与日志 | 先范围/筛选/计数再分页，最小脱敏、稳定快照total、游标绑定及每批重验；北京时间展示/筛选与UTC存储边界 |
| 外部协议/课程 | 8项直连及经支撑，目录最小字段/范围，operationId配置、204；短码完整映射、业务审计独立、课程对象权限及来源缺口响应 |
| 前端 | 真实Vue、身份切换、窄屏/键盘、读失败禁保存、草稿/取消/晚响应、失权及两个本机Origin |
| 架构与映射 | 双策略独立决策、DDD不依赖JPA/Entity/structured、仓储仅领域类型、真实CloneFactory赋值、record/final构造器映射完整性与返回值、业务组装例外、中文Javadoc及注解独行 |
| 文档与契约 | YAML/JSON一致，45操作/37路径/94模型/K17及独立LOGIN；全部操作及离线入口可追溯，顺序图只画差异，链接与源码锚点有效；Apifox仅在明确授权同步后回读 |
| 部署与监控 | Stack配置可解析、Secrets不泄露、内部端口隔离；promtool校验真实配置/规则及CPU负载、重置、缺样本；线上健康与指标采集另行验证 |

## 3. 执行方法

后端使用JDK21、真实MySQL8.4及固定CloneFactory0.0.2。按[回归专库配置](../backend/README.md#2-本地回归与隔离)为每策略准备6个可丢弃专库，运行`mvn -f backend/pom.xml clean package`，产品万人库与测试库隔离。独立JAR探针见[运行验收器](../backend/scripts/accept-runtime.py)，不以mock数据库、skipTests或旧JAR替代通过。

双策略JaCoCo按[覆盖率复现](../backend/README.md#7-双策略jacoco复现)分别clean完整测试后保存摘要与exec，核对全部生产字节码一致再合并，并将合并数据交给原pom六项零遗漏检查。单元端口故障测试与真实MySQL契约分开说明，不把合并覆盖率替代线上验收。

前端执行`npm test`、`npm run build`，浏览器夹具及显式启用方法见[UI复现](history/verification/ui-performance-evidence.md#structured-ui-acceptance)。两系统HTTP/浏览器验证脚本见scripts目录帮助，含写入的探针只指向明确验收环境。服务器验收和离线监控命令见[部署](../deploy/README.md)及[监控](../deploy/monitoring.md)。纯文档改动无需重跑全部MySQL，配置改动使用实际工具验证。

每份记录注明提交/来源、环境、执行命令、结果及未验证范围；涉及业务代码时记录RED/GREEN。原始日志、截图、凭据、CSV、JAR和数据库保存在忽略目录，只提交脱敏结论。[保留证据索引](history/README.md)说明用途；Git保存已清理旧版本，不保留另一套过期现行规范。

<a id="role-verification-guide"></a>

## 自定义角色操作与验证

应用管理及独立RBAC登录权限已于2026-11-24晚部署；正式验收与备份见[发布记录](history/verification/release-evidence.md#application-management-release-20261124)。用管理员、部门领导、部门普通员工三个独立浏览器会话验证，不共享同一浏览器的登录存储。先记录各账号的部门、基础角色及完整已有角色，保留teacher/student/course-admin等原有关联；本方案不会自动在正式库创建测试角色。

<a id="role-verification-guide-1-选择验证对象"></a>

### 1. 选择验证对象

选择部门A的有效部门经理或其上级领导L，选择A或其下级的普通员工U，以及另一分公司普通员工V。L必须有查询人员、查看人员角色、查询候选及分配人员角色的实际API，U的有效基础级别须低于L。管理员在角色页查看L已有的完整Permission集合，后面部门角色只选其中的有效项；API等价不等于拥有相同Permission。

<a id="role-verification-guide-2-部门自定义角色"></a>

### 2. 部门自定义角色

1. 管理员进入“角色与权限”，新建编码`verify_dept_reader`、名称“部门人员查阅验证”，类型选“部门自定义”，所属部门选A。创建后权限为空，不授予管理级别。
2. 配置该角色权限，显式勾选“登录RBAC”和“查询人员”操作权限。两者分别映射登录与人员列表读取API；不要勾选停用、调动、分配角色等写按钮。保存前再次核对这两个Permission均在L当前有效集合内；若L没有某项，应换用其已有权限，不为测试随意扩大L权限。
3. L进入“人员管理”，定位U，打开分配角色抽屉，在保留原有角色的基础上新增该角色并确认。正常结果是候选可见、保存成功，U级别不变，范围仍为U自己的部门子树。
4. U使用独立会话登录，只有人员读取入口，没有总览、资源、日志等额外业务权时不请求这些数据。人员页无权写按钮保留但置灰；直接调用写API仍403。
5. 用L尝试给V、本人、同级或上级分配，应被范围或人员保护拒绝；给权限不含L完整子集的部门角色，应不在候选且提交同样拒绝。L不能改角色定义、所属部门或权限。
6. L从U撤回该部门角色后，U原JWT下一请求立即按剩余权限判断。若其他角色也授予同一API，访问仍可能成功，这是OR语义；如连LOGIN也无有效链，管理本人读取及再次登录均403。

部门角色的所属范围表示“允许分配给谁”，不会把持有人范围扩展成整个部门A。管理员定义角色，领导只在范围、级别和完整权限子集均满足时分配或撤回。部门调动会在同事务撤下不再适用的部门角色，调回不自动恢复。

<a id="role-verification-guide-3-全局自定义角色"></a>

### 3. 全局自定义角色

1. 管理员新建编码`verify_global_reader`、名称“全局人员查阅验证”，类型选“全局自定义”，不填所属部门。显式配置“登录RBAC”和“查询人员”操作权限，仍不选写按钮。
2. 由启用、可信根直属、有效五级管理员给U分配，保留原有角色。U可以按自己的部门范围查询人员；“全局”表示角色定义不绑定某一部门，不是给U全公司数据范围，也不把U提成管理员。
3. L打开U的角色抽屉，这个全局角色应显示为锁定保留项；L可以调整其他获准变化，不能新增或删除这个角色。若人为提交删除应403，不能因U持有它而冻结U全部管理。
4. 管理员撤回该全局角色，U使用原JWT复查。再次确认授权链按当前事实生效，而不是等退出或JWT过期。

<a id="role-verification-guide-4-补充验证登录和单个按钮"></a>

### 4. 补充验证登录和单个按钮

为避免已有基础角色干扰，可由管理员给无RBAC登录资格的普通员工临时分配只含LOGIN的验证角色。第一次登录应进入空工作台。再为该角色加入某个MENU的显式读取映射后，只出现该页入口；该页其他写按钮仍置灰。只授单个按钮而未授父MENU不会自动补父Permission，但若按钮本身明确映射了页面读取API，前端按实际API能力显示入口。

课程三身份不代表RBAC管理权限；课程Teacher/Student绑定、负责班级、课程与小组对象范围继续由课程系统检查。

<a id="role-verification-guide-5-恢复与证据"></a>

### 5. 恢复与证据

完成后由有权人员撤销本次新增的角色关联，再停用验证角色；当前没有角色删除接口。恢复依据最初记录的完整角色集合，不重跑初始化重置所有用户。保留请求ID与真实允许/拒绝日志，不删除历史日志。测试中最后维护者冲突409属于预期保护，不应通过先撤掉自己的关键能力继续操作。

规则位置：[需求R11/R13/R23/R27/R34](requirements.md)、[API契约](api-contract.md)、[领域规则](domain-model.md)。自动化对应：[部门角色授撤与调动](../backend/src/test/java/com/municipal/rbaccore/r13/R13RoleContractSupport.java)、[全局自定义及锁定项](../backend/src/test/java/com/municipal/rbaccore/r11/R11UserManagementContractSupport.java)、[登录及单菜单](../backend/src/test/java/com/municipal/rbaccore/r34/R34ApplicationManagementContractTest.java)。
