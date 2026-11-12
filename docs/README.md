# 正式文档导航

本目录按老师项目的文档职责组织，保留本项目业务口径。推荐顺序：需求→领域模型→Chen ER/物理模型→前后端设计→API→部署与验收。

| 文档 | 回答的问题 | 状态/权威边界 |
| --- | --- | --- |
| [requirements.md](requirements.md) | 做什么、来源是谁、范围到哪里 | 当前业务需求正文；设计不等于实现 |
| [domain-model.md](domain-model.md) | 对象、行为、不变量、DDD拟议聚合 | 概念与目标设计；当前没有DDD代码 |
| [database-design.md](database-design.md) | Chen ER、联系及关系映射 | 既有六实体/九核心表，原ER不变 |
| [database/physical-model.md](database/physical-model.md) | 字段、约束、支撑表与初始化 | 现有SQL/JPA对应，双实现共用结构 |
| [backend-design.md](backend-design.md) | 当前分层、目标双实现、事务与依赖 | 当前结构化及未来DDD明确分开 |
| [frontend-design.md](frontend-design.md) | 完整RBAC页面与交互如何组织 | 设计覆盖完整前端，当前生产仅兼容入口 |
| [api-contract.md](api-contract.md) | 方法、路径、字段、错误、权限与范围 | 正式HTTP规范正文；24个操作 |
| [api-map.md](api-map.md) / [API入口](../api/README.md) | 页面/用例如何调用API | 索引，不重复定义字段；尚无经核对OpenAPI |
| [testing-acceptance.md](testing-acceptance.md) | 验收什么、如何复现、哪些未完成 | 当前状态与T13验收入口 |
| [部署说明](../deploy/README.md) | 依赖、建库、秘密、导入、授权及启动 | 唯一维护的产品初始化命令 |
| [当前实现UML](design/README.md) | 现有类协作与身份校验顺序 | 实际代码图，非DDD实现图 |

## 来源、历史与更新规则

老师检查记录、图片与原数据事实见[复核说明](rbac-rectification/RBAC检查记录复核说明.md)。老师示例仓库只提供文档与框架组织参考，不能覆盖已确认的RBAC业务。用户后续确认的双入口、领导合根、独立admin和五角色规则见需求正文。

[整改入口](rbac-rectification/README.md)、[执行计划](rbac-rectification/RBAC整改计划与Chen-ER图.md)、[任务清单](rbac-rectification/RBAC整改任务清单.md)、[原型/评审记录](rbac-rectification/RBAC原型与API契约.md)保留原路径及锚点，用于需求追溯、任务依赖与历史证据；不再把新正式设计混写到历史过程。原始Excel、旧库和本地小组材料保留。

API语义只在正式契约维护，页面对照只链接，历史原契约不作为另一份独立现行规范；变更须同步原型、测试及受影响设计。需求正文维护规则，验收页维护完成状态，Issue维护实时任务状态；出现冲突须修正文档，不能靠选择一份有利旧报告通过验收。

[legacy](legacy/README.md)仅供历史差异参考。[协作规范](../CONTRIBUTING.md)约束分支和交付。本次文档整理后完成T13即停止，不启动后续实现。
