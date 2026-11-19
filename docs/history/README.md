# 只读历史追溯

现行需求和设计从[正式导航](../README.md)阅读。本目录历史资料仅用于复核来源、曾经的依赖、评审与验收时间线，不参与当前规则或新代码生成；旧“当前/待实现”按原记录时间理解。

早期整改资料保留下列来源、任务与验收证据，原相对引用指向固定T13基线。现行需求、ER与设计均在正式文档中维护；本目录不另立当前规范。

| 历史资料 | 用途 |
| --- | --- |
| [原整改入口](rbac-rectification/README.md) | 当时的任务导航及开工说明 |
| [检查记录复核](rbac-rectification/RBAC检查记录复核说明.md) | 老师纪要/数据核对与用户确认过程 |
| [原型与契约评审](rbac-rectification/RBAC原型与API契约.md) | 四组契约的历史评审与补充确认 |
| [执行计划与原ER源码](rbac-rectification/RBAC整改计划与Chen-ER图.md) | T01—T13实施/修复证据；现行ER另在database |
| [原任务清单](rbac-rectification/RBAC整改任务清单.md) | 历史15票依赖；当前范围见development-plan |

原始版本也可由[固定基线Git历史](https://github.com/minxuanbo/ooad-course-project/tree/ffc7ceb783dd71b069a41664b11c994b31da2371/docs)追溯。必要现行事实提取至requirements/sources及正式设计；阶段结果按原版本归入verification，不作为第二份当前设计。

## 文档与验证记录

[文档整理记录](documentation/docs-cleanup.md)保留早期设计清理过程。以下为固定阶段证据，不用于覆盖正式设计。DDD已随PR #34合入main，当前双策略实现结果见[阶段5验收](../testing-acceptance.md#阶段5-交接复核2026-11-17)。

- [DDD设计交付与核对记录](verification/ddd-design.md)
- [阶段4最终文档与结构化边界核对](verification/final-alignment.md)
- [PR #32组员审核后修复](verification/peer-followup.md)
- [阶段4映射核对记录](verification/stage4-mapping-audit.md)
- [阶段4正式Vue与结构化后端验收](verification/stage4-vue.md)
- [结构化代码归类验收记录](verification/structured-organization.md)
- [T13 当前RBAC后端集成验收](verification/T13.md)
- [课程本地联调及双轴审查记录](verification/course-integration-local.md)

[离线原型决策](design-decisions/prototype.md)保存此前讨论与获批口径；现行页面交互以frontend-design、正式API和需求为准。
