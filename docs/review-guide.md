# 本次文档清理与逐项审核指南

本次分支：codex/docs-cleanup-and-design；目标共同分支：refactor/rbac-core-backend；固定整理基线ffc7ceb783dd71b069a41664b11c994b31da2371。状态为待用户审核，未批准前不合并，也不启动代码整理、新原型、正式前端、DDD或T14/T15。

## 1. 组织方式与阅读顺序

老师项目按需求、领域、数据库、后端、API、顶层prototype及deploy归类。本项目采用相同职责方式，并补齐完整前端、初始化和验证说明；不复制课程业务、外部身份或无关集成。建议按下表逐项审核，方框由用户确认后更新，本次不预先勾选。

| 审核 | 文档/类型 | 主要变更与建议审核点 |
| --- | --- | --- |
| [ ] | [requirements.md](requirements.md)，修改 | 保留R01—R22，更新业务来源/范围，补用例与质量要求；先核对双入口、领导合根、五角色与部门范围 |
| [ ] | [requirements/sources.md](requirements/sources.md)，新增 | 老师纪要、原Excel事实、用户确认及参考项目边界；历史语句与现行结论有时间区分 |
| [ ] | [domain-model.md](domain-model.md)，扩充 | 六核心对象、目标聚合、Permission子集、完整K、命令行为、生命周期及跨聚合一致性 |
| [ ] | [database-design.md](database-design.md)，修改 | 陈氏ER、关系、业务/数据库约束分工、双实现共用存储；原图语义保留 |
| [ ] | [database/physical-model.md](database/physical-model.md)，重写 | 12表逐字段/空值/主外键/索引，9核心与3支撑区分，不宣称SQL已覆盖跨对象规则 |
| [ ] | [database/chen-er-source.md](database/chen-er-source.md)，提取 | 保留原三段关系源码，对应迁移后的SVG/PNG，不重画已评审ER |
| [ ] | [backend-design.md](backend-design.md)，扩充 | 参照老师17章职责，明确现状/目标、端口/双实现/切片/事务/安全/映射/测试/部署 |
| [ ] | [frontend-design.md](frontend-design.md)，扩充 | 完整页面、组件状态、交互/异常、权限与真实API验收；不宣称新原型或全量页面已完成 |
| [ ] | [api/openapi.yaml](../api/openapi.yaml)，新增 | 20路径/24操作、Schema、参数、错误及17项K；核对现有接口一致性，JSON同源不必重复审 |
| [ ] | [api-contract.md](api-contract.md)，重写 | 仅保留Schema外业务约束：授权OR、子集、范围、K、锁、幂等退出、日志快照 |
| [ ] | [api-map.md](api-map.md)、[api/README.md](../api/README.md)，修改 | 页面/操作索引与规范主从关系，不维护第二份字段定义 |
| [ ] | [initialization.md](initialization.md)，新增 | 新项目来源映射、111→110、根11、五角色人数、整批事务与重复不重置 |
| [ ] | [deploy/README.md](../deploy/README.md)、[现场JaCoCo](../deploy/现场运行JaCoCo.md)，修改 | 固定CloneFactory本机安装、唯一初始化命令、历史发布清理与云环境未验证边界 |
| [ ] | [testing-acceptance.md](testing-acceptance.md)，修改 | 需求矩阵、当前/后续验证，T13为历史基线；不复用旧结果声称本分支运行过 |
| [ ] | [development-plan.md](development-plan.md)，新增 | 六阶段顺序，当前只阶段1，课程系统及T14/T15等老师要求 |
| [ ] | [README](../README.md)、[docs导航](README.md)、[AGENTS](../AGENTS.md)、[CONTRIBUTING](../CONTRIBUTING.md)，修改 | 当前入口、协作/分支/审核边界，删除旧“T13后停止”现行指令 |
| [ ] | [prototype/README](../prototype/README.md)、[index](../prototype/index.html)，新增/迁移 | 原四组参考原型搬顶层；HTML仅改说明/链接，5个JS及CSS原逻辑不变 |
| [ ] | [history/README](history/README.md)及5份归档 | 保留追溯证据、头注只读历史，原链接固定到基线，不作为现行规范 |

## 2. 删除、迁移与保留

删除当前树docs/legacy共19文件：旧需求/界面/API/数据库/详细设计5份，旧ER dot/md/png/svg4份，旧原型HTML/说明/4JS共6份，旧演示方案1份、旧测试/评审2份及legacy导航1份。它们包含旧会话/凭证、交接、独立管理范围或归档等已淘汰设计，完整旧版本仍可从Git历史取得。

删除deploy下2026-10-20、10-24、10-25、10-27四份旧发布记录；现行步骤统一进部署README。部署栈、脚本及SQL未改，不表示清理或操作实际云资源。

迁移原docs/rbac-rectification/prototype七个文件到顶层prototype；ER图PNG/SVG移至docs/database并改为chen-er-overview命名；三段关系源码提取到chen-er-source。原图及原型JS/CSS保持内容不变，HTML只改入口文字/链接。当前实现UML及T13验收记录保留原件。

五份整改追溯文档移到docs/history/rbac-rectification：README、RBAC检查记录复核说明、RBAC原型与API契约、RBAC整改计划与Chen-ER图、RBAC整改任务清单。为防止误用，加只读历史头注并将原相对文档链接固定到基线；其正文、任务依赖和验收证据保留。该归档是自动审批拒绝直接删除后的低风险处理，详见历史索引。

保留三份原Excel、用户主工作区未跟踪概要设计MD/DOCX、数据库、历史日志及其他工作树。本次不生成DOCX，也不覆盖用户本地设计文件。

## 3. 必要的测试配套改动

仅改[frontend/tests/prototype.test.mjs](../frontend/tests/prototype.test.mjs)：旧测试针对已淘汰门户/范围/归档原型，改为加载迁移后的真实四组参考原型，检查加载、人员范围拒绝和资源同系统拒绝。先确认新路径不存在时行为RED，再搬迁并回归；未改生产前端/Java/SQL。

这项改动只维护迁移后的可验证入口，不代表新前端原型设计或正式页面实现。未来阶段不得用该内存原型测试替代真实HTTP/数据库或UI验收。

## 4. 检查与审核结论

本次应核对：现行链接/锚点、HTML资源、OpenAPI合法及YAML/JSON等价、24操作/17项K、原件摘要、原型迁移测试、现有前端构建、git diff空白及生产文件无改动。真实完成结果写入草稿PR的验证摘要；未执行的数据库/云部署不得标为通过。

2026-11-12实际检查：30份Markdown的190个本地链接/锚点及HTML资源通过；OpenAPI3.1校验、YAML/JSON等价、24操作与17项K通过；11个保留资产的Git内容摘要及3段Mermaid源码不变；npm test共8项通过，npm run build通过，git diff --check通过。生产Java、SQL、部署脚本与前端页未改；本轮未重跑数据库或云部署验收。

代理按用户提供code-review skill分别做Standards/Spec复核，发现问题先修复；该结论只代表技术复核。用户仍须逐项审核并明确决定合并；未确认项保留，不自动推进后续任务。
