# 测试与验收

本页按验证目标组织执行入口与证据。设计文字、打包、静态审核、运行测试及老师验收分别记录；旧版本成绩不替代新修改后的实测。

## 1. 运行状态与证据

| 关注点 | 可追溯证据与边界 |
| --- | --- |
| 当前正式发布 | [2026-11-23发布](history/verification/release-20261123.md)：两系统DDD、108次公网HTTP、即时撤权、北京时间、监控及备份；公网浏览器被客户端拦截，未取得本轮浏览器通过结果 |
| 当前源码RBAC覆盖率（未发布） | [验证记录](history/verification/rbac-jacoco-complete.md)、[完整JaCoCo报告](reports/jacoco/20261123-rbac/README.md)：两策略各304项无失败/错误/跳过；340份生产class摘要一致，合并六项100%，原六项零遗漏门槛通过；JWT必填iat修复尚未上线 |
| 已发布版本回归及覆盖率 | RBAC两策略各215项、课程499项均无失败/错误/跳过；当时合并RBAC行96.19%、分支82.94%，课程行90.60%、分支62.17%；仅代表发布记录对应版本，不用本轮源码100%替代 |
| 首发历史 | [双DDD首发](history/verification/ddd-production-deployment.md)：首次导入、逐人身份、公网及重启结果；只代表该版本，不宣称高可用或压测通过 |
| 时间及文档修正来源 | [北京时间与终态服务清理](history/verification/beijing-time-and-service-cleanup.md)、[文档与监控调整](history/verification/current-docs-and-monitoring.md)：保留本机及离线验证过程；其中待发布项已在11月23日发布 |
| 两系统部署适配 | [适配回归](history/verification/ddd-deployment-adaptation.md)：两RBAC及老师副本真实MySQL、双DDD HTTP、前端构建与日志；独立于云端实测 |
| JWT及UML | [全JWT与图件核对](history/verification/jwt-design-cleanup.md)：受众、单会话、即时撤权、82组图件及操作追溯 |
| 完整外部协议 | [八项身份契约](history/verification/identity-eight-contract.md)：目录、operationId、退出及Apifox回读 |
| 万人首次身份 | [课程初始化](history/verification/course-initialization-r31.md)：191教师/9810学生/1管理员、回执与原库边界 |
| 跨系统授权与UI | [通用接入](history/verification/course-integration-generic.md)、[structured UI](history/verification/structured-ui-acceptance.md)、[DDD UI](history/verification/stage6-ddd.md) |

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
| 架构与映射 | 双策略独立决策、DDD不依赖JPA/Entity/structured、仓储仅领域类型、真实CloneFactory赋值、record/final显式构造、中文Javadoc及注解独行 |
| 文档与契约 | YAML/JSON一致，37操作/31路径/71模型/K17；全部操作及离线入口可追溯，顺序图只画差异，链接与源码锚点有效；Apifox仅在明确授权同步后回读 |
| 部署与监控 | Stack配置可解析、Secrets不泄露、内部端口隔离；promtool校验真实配置/规则及CPU负载、重置、缺样本；线上健康与指标采集另行验证 |

## 3. 执行方法

后端使用JDK21、真实MySQL8.4及固定CloneFactory0.0.2。按[回归专库配置](../backend/README.md#2-本地回归与隔离)为每策略准备6个可丢弃专库，运行`mvn -f backend/pom.xml clean package`，产品万人库与测试库隔离。独立JAR探针见[运行验收器](../backend/scripts/accept-runtime.py)，不以mock数据库、skipTests或旧JAR替代通过。

双策略JaCoCo按[覆盖率复现](../backend/README.md#7-双策略jacoco复现)分别clean完整测试后保存摘要与exec，核对全部生产字节码一致再合并，并将合并数据交给原pom六项零遗漏检查。单元端口故障测试与真实MySQL契约分开说明，不把合并覆盖率替代线上验收。

前端执行`npm test`、`npm run build`，浏览器夹具及显式启用方法见[UI复现](history/verification/structured-ui-acceptance.md)。两系统HTTP/浏览器验证脚本见scripts目录帮助，含写入的探针只指向明确验收环境。服务器验收和离线监控命令见[部署](../deploy/README.md)及[监控](../deploy/monitoring.md)。纯文档改动无需重跑全部MySQL，配置改动使用实际工具验证。

每份记录注明提交/来源、环境、执行命令、结果及未验证范围；涉及业务代码时记录RED/GREEN。原始日志、截图、凭据、CSV、JAR和数据库保存在忽略目录，只提交脱敏结论。[保留证据索引](history/README.md)说明用途；Git保存已清理旧版本，不保留另一套过期现行规范。
