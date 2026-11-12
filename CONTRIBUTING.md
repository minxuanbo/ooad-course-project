# 小组协作与文档维护

现行规范从[文档导航](docs/README.md)进入。需求、领域、数据库、前后端、API、初始化与验收分别维护职责；历史整改过程放在docs/history，不再作为另一份当前规范。GitHub Issue/PR维护实时认领与合入状态。

## 1. 当前工作边界

T13已合入共同基线，当前仅结构化实现。后续顺序见[开发计划](docs/development-plan.md)。本次仅文档清理/完善，迁移参考原型及必要路径测试；用户逐项审核后才决定合并。不得借整理移动生产代码、加入策略配置、重做UI或启动DDD/T14/T15。

## 2. 分支与工作树

共同基线为refactor/rbac-core-backend，main仍是历史工程。先确认本地改动，再获取远端；按本票范围使用独立codex/分支。示例：

```bash
git status --short
git fetch origin
git worktree add -b codex/your-task ../ooad-your-task origin/refactor/rbac-core-backend
```

分支或目录存在时先核对，不覆盖或重置用户工作。每次开工读AGENTS.md、对应Issue、批准需求及依赖证据；不凭Issue关闭状态推断交付物已进入共同基线，不自动领取下游任务。

## 3. 编写与验证

- 业务规则改需求正文，HTTP字段先改api/openapi.yaml并同源导出JSON，范围/事务等改api-contract；同步页面对照和受影响测试。
- 目标设计用“应、拟、规划”等措辞；实际状态及验收分别说明，保留版本、环境、源提交与未验证范围。
- 代码/测试按用户指定编码skill执行TDD、DTO/VO、CloneFactory、中文注释、真实换行与事务规范；RBAC本地身份等用户已确认规则优先。双轴审核按审核skill分别检查规范与需求。
- UML实现图随实际代码更新；不虚构DDD实现图。图件同时交可编辑源、SVG/PNG及生成说明，生成后进行视觉检查。
- 保留原Excel、旧库和本地资料；万人CSV、凭据、运行日志、构建产物和原始测试报告不提交。

## 4. 交付与审核

核对git diff和验证结果，按文件明确暂存。提交并推送任务分支，创建base为refactor/rbac-core-backend的草稿PR；关联实际Issue时用Refs，不用无关的自动关票语句。正文写问题、最终变更、证据、限制与待审核事项。

用户要求先审时，不自行合并、删除在审分支或关闭Issue。文档审核以[逐项指南](docs/review-guide.md)为入口；用户批准后才进行后续动作。变更批准范围时同步计划与受影响Issue，不新建重复任务。本轮不向Apifox推送。
