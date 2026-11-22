# 小组协作与文档维护

现行规范从[文档导航](docs/README.md)进入。需求、领域、数据库、前后端、API、初始化与验收分别维护职责；历史整改过程放在docs/history，不再作为另一份当前规范。GitHub Issue/PR维护实时认领与合入状态。

## 1. 当前工作边界

R37用户已授权并完成PR #43合入main（44a898c）、清理8个GitHub及6个本地已合入开发分支，当前从main继续协作。提交历史和detached工作树保留；此次Git操作不包含重新部署。R36北京时间源码已进入main，但线上仍运行R35版本，等待用户集中反馈后明确统一发布。以下阶段记录按当时语境保留，不再把PR #43视为待合入草稿。

R36用户要求继续集中反馈，暂缓再次部署。仅授权清理8项已结束的初始化/排障服务，已在完整归档并核验后执行，7项正式服务不变；北京时间网页/Java日志修正完成本机验证，等待用户明确统一发布。不以先前R35部署授权自动更新线上Stack或重启应用。结果见[R36记录](docs/history/verification/beijing-time-and-service-cleanup.md)。

R35用户已明确授权两系统正式部署及旧服务清退，覆盖此前搁置服务器的阶段限制。当前在codex/ddd-swarm-adaptation交付，草稿PR #43面向main；不据部署授权自动合并或关闭T14/T15。原库/卷/Excel/历史日志及老师原目录保留，实际云端结果见[部署记录](docs/history/verification/ddd-production-deployment.md)。密码仅放Secrets与私有交付说明，不提交；详设外部文件保持原版。本轮37项业务API未改变，不产生新的Apifox写入。

R33阶段由用户要求统一全部JWT、补齐双策略类图/差异覆盖并整理docs。当前规范从docs/README进入，旧计划与设计/验收快照归history；不再把历史阶段状态当当前待办。37项HTTP与两类离线入口由UML注册表追溯，更新图须生成并目检。Apifox项目仍为9042431，变更契约时依明确授权同步；历史证据与用户资料保留。

2026-11-21更新：用户明确授权将有效分支改动合入main；PR #38（课程通用接入）、#39（设计一致性）、#40（R31首次身份接入与CloneFactory0.0.2）已经合入。本轮R32完整8项身份协议按相同验证/双轴审核流程交付并合入main，记录见[本轮验收](docs/history/verification/identity-eight-contract.md)。用户同时明确要求同步Apifox项目9042431，必须导入后回读核对。此次授权不包含删除分支、关闭T14/T15或服务器部署，详设外部文件保持原版。

截至2026-11-18，文档PR #29、结构化整理PR #30、原型PR #31、阶段4正式前后端PR #32及同步PR #1均已合入。阶段5独立DDD实现经双轴审核、修复、CloneFactory专项核对及两策略隔离回归后，已获用户批准随[PR #34](https://github.com/minxuanbo/ooad-course-project/pull/34)合入main（e521f97）。组员按[复现指南](docs/reproduction.md)从main检出并记录提交号；阶段6剩余UI复验、课程接入和部署边界见[开发计划](docs/history/planning/development-plan-through-r32.md)。

## 2. 分支与工作树

main已包含structured/ddd双实现；新任务分支从origin/main建立。先确认本地改动，再获取远端；按本票范围使用独立codex/分支。示例：

```bash
git status --short
git fetch origin
git worktree add -b codex/your-task ../ooad-your-task origin/main
```

分支或目录存在时先核对，不覆盖或重置用户工作。每次开工读AGENTS.md、对应Issue、批准需求及依赖证据；不凭Issue关闭状态推断交付物已进入共同基线，不自动领取下游任务。

## 3. 编写与验证

- 业务规则改需求正文，HTTP字段先改api/openapi.yaml并同源导出JSON，范围/事务等改api-contract；同步页面对照和受影响测试。
- 目标设计用“应、拟、规划”等措辞；实际状态及验收分别说明，保留版本、环境、源提交与未验证范围。
- 代码/测试按用户指定编码skill执行TDD、DTO/VO、CloneFactory、中文注释、真实换行与事务规范；RBAC本地身份等用户已确认规则优先。双轴审核按审核skill分别检查规范与需求。
- UML实现图随实际代码更新；不虚构DDD实现图。图件同时交可编辑源、SVG/PNG及生成说明，生成后进行视觉检查。
- 保留原Excel、旧库和本地资料；万人CSV、凭据、运行日志、构建产物和原始测试报告不提交。

## 4. 交付与审核

核对git diff和验证结果，按文件明确暂存。提交并推送任务分支，创建base为main的草稿PR；任务另行明确其他目标时再按其约定，不沿用旧修复分支示例。关联实际Issue时用Refs，不用无关的自动关票语句。正文写问题、最终变更、证据、限制与待审核事项。

用户要求先审时，不自行合并、删除在审分支或关闭Issue。项目文档以[正式导航](docs/README.md)为入口；用户批准后才进行后续动作。变更批准范围时同步计划与受影响Issue，不新建重复任务。Apifox写入按当次明确授权执行；2026-11-21用户已要求本轮同步，目标与回读要求见第1节。
