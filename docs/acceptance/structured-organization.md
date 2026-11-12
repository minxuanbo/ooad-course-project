# 结构化代码归类验收记录

日期2026-11-13；本阶段范围是现有RBAC结构化代码及测试归类，不是DDD、新前端或服务器部署验收。共同起点为d1438d7（文档PR #29）；生产逻辑提交16adbcb153e31f38c2c8c0ccfe126ea3a5e3786d，最终注释/测试需求命名及本地运行入口提交34f3cecc0c3364e2a1e64bde406284e46c8cc5bd。

## 1. 实际环境与隔离

Microsoft OpenJDK21.0.13.0.2、MySQL8.4.8、Maven3.8.5。本轮新建工作区私有mysql-structured-data，MySQL仅绑定127.0.0.1:3322；分别建立rbac_core_t02/t03/t05/t07/t09/t11_test六个回归专库及空rbac_core运行探针库。配置前核实实际@@datadir，测试用户仅授六个专库权限。原业务库、T13私有实例/证据和三份Excel未改。

当前源码、测试编号与边界见[代码归类](../code-organization.md)；本机变量及可复现命令见[后端说明](../../backend/README.md)。执行完整回归使用正式backend/scripts/test-backend.ps1入口，传入clean package及UTC/JVM参数，不跳过测试。运行探针使用backend/scripts/accept-runtime.py，拒绝非空库与Spring/JVM额外配置，不提供自动清库。

## 2. 先失败后修复

| 目标 | 失败证据 | 最终处理 |
| --- | --- | --- |
| R19/R21分层边界 | 原目录下4项断言失败：缺少独立backend、共享用例与结构化层 | 迁移并抽取真实用例/支撑端口，边界测试通过 |
| R13/R19非空映射 | 角色copyNotNull方法实际不存在，2项测试中1项行为失败 | 补老师处理器注解及Service调用，保留编码/缺省字段，三态另测 |
| R16判定职责 | 业务400/404/409/500被误记拒绝，6项中4项失败 | allowed与HTTP业务结果分开，401/403及范围拒绝仍记拒绝 |
| R10/R18输入边界 | q清洗顺序和安全ID上限2项失败 | DTO先trim再验长，补安全整数上限，Service不做请求清洗 |

迁移后的首次全回归发现过滤器被方法AOP代理而影响Servlet生命周期，已排除该过滤器的层方法日志，保留原事件采集。最终注释清理曾误识别路由通配符，已由3项真实权限顺序用例发现、恢复16adbcb原路径；对161份生产Java源码进行解析后语法树比较，确认最终生产语义与通过完整运行探针的16adbcb一致。失败和诊断运行不计入通过成绩。

## 3. 当前通过结果

| 检查 | 真实结果与范围 |
| --- | --- |
| 最终完整clean package | 156项，Failures/Errors/Skipped均0，BUILD SUCCESS；原142项保留，加14项分层/映射/日志/输入回归，四组共享HTTP契约由Structured启动类执行 |
| 验收工具环境安全 | 3项Python单测通过；覆盖额外Spring/JVM覆盖前置拒绝、正常核实路径和Java子进程变量白名单 |
| 正式空库初始化 | 正式SQL、生产BCrypt10逐账号盐、111组织来源→110部门、10000员工+独立admin、根直属11人；导入不授角色 |
| 首次五角色 | 只读预检不写授权，实际人数1/64/106/72/9758；预检与实际命令分开 |
| 16adbcb默认JAR完整探针 | 70次真实HTTP及两轮启动通过；双入口、范围/筛选、角色/资源/人员授撤、停用/调动、完整维护者回滚、日志快照、旧入口、重复初始化保留维护结果 |
| 最终34f3cec产物启动 | 另3次真实HTTP通过：匿名本人、系统目录、非法角色PATCH均先返回401及requestId；校验默认Start-Class和真实CloneFactory，旧包不进入jar |
| 代码/图件/文档 | Java21解析检查类/记录/关键方法的Javadoc；需求编号进入测试名/DisplayName；2组当前UML由同一脚本生成SVG/PNG并打开检查；正式链接/OpenAPI/资产摘要检查通过 |

完整探针JAR SHA-256为d2ea9948d934fdbfdb32fd3c00f5ef572ba7b8e93f1d7c1e1ad9e217f6d661b0，产物在本地忽略目录structured-artifacts/16adbcb-rbac0-0.1.0.jar保留。最终34f3cec JAR SHA-256为6563580fcf568e5a1667f1bb51ba424d203d58b68ee46cb13fe7829e15afa29c。之后文档/图件不改变生产源码；未来重建不要求相同打包时间或摘要。

## 4. 证据与限制

原始证据仅放共同工作区忽略目录.local/rbac-issues：structured-final-regression-restored.log、structured-audit-red-unsandbox.log/structured-audit-green.log、structured-query-red.log、structured-mapping-red.log、structured-boundary-red.log，以及structured-final-jar-summary.json。完整运行证据目录为structured-runtime-1794502488520565900，含environment.json、summary.json、离线初始化和两轮JAR日志。原始日志/凭据/万人CSV不提交；以上给出可复核摘要。

JaCoCo配置保留，按用户要求不作为本轮阻塞；未运行verify来宣称100%覆盖。根Dockerfile、.dockerignore、Stack及旧容器脚本未适配，待新增服务器方案统一处理；本轮不操作服务器，不宣称部署可用。仅有结构化实现和旧前端兼容入口，DDD及新前端尚未实施。

本分支技术检查和代理审核不代替用户验收，按草稿PR交付，不自动合并或推进下阶段。后续代码变化另记对应版本及新结果，T13原记录保留其当时事实。
