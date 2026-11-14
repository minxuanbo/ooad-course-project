# 正式文档导航

按老师项目的requirements/domain-model/database/backend/api/prototype/deploy职责组织。文档阶段已于2026-11-13审核并随PR #29合入；当前代码归类见[代码归类](code-organization.md)。原[审核指南](review-guide.md)保留上一阶段文件清单。需求来自老师说明与用户确认，示例仓库用于组织和框架参考。

| 顺序 | 文档 | 职责 |
| --- | --- | --- |
| 1 | [需求说明](requirements.md)、[需求来源](requirements/sources.md) | 做什么、原始事实、后续确认、范围与质量要求 |
| 2 | [领域模型](domain-model.md) | 统一语言、核心不变量、目标聚合/行为/策略 |
| 3 | [数据库设计](database-design.md)、[物理模型](database/physical-model.md)、[Chen源码](database/chen-er-source.md) | 六实体联系、九核心表与三支撑表，保留原ER |
| 4 | [后端设计](backend-design.md)、[当前实现图](design/README.md) | 17章总体架构、模块、双实现、事务与技术约束；图仅表示当前结构化代码 |
| 复现 | [组员复现](reproduction.md)、[DDD隔离边界](ddd-boundary.md)、[最终一致性核对](acceptance/final-alignment.md) | 正确分支、全新库、依赖/口令/命令、默认端口与共享边界 |
| 代码 | [代码与测试归类](code-organization.md)、[本地后端说明](../backend/README.md) | 实际结构化目录、共享用例、测试需求编号及本地构建 |
| 5 | [前端设计](frontend-design.md)、[新原型](../prototype/index.html)、[评审记录](prototype-review.md)、[历史参考](../prototype/README.md) | 正式Vue及获批离线原型；演示系统仅在原型，正式产品仅RBAC |
| 6 | [API入口](../api/README.md)、[OpenAPI](../api/openapi.yaml)、[API业务约束](api-contract.md)、[页面对照](api-map.md) | Schema主规范与29项操作、授权/范围/事务语义 |
| 7 | [初始化](initialization.md)、[部署说明](../deploy/README.md) | 数据转换、五角色、依赖与唯一运行命令 |
| 8 | [测试验收](testing-acceptance.md)、[组员审核后修复](acceptance/peer-followup.md)、[阶段4验收](acceptance/stage4-vue.md)、[映射核对](acceptance/stage4-mapping-audit.md)、[结构化归类验收](acceptance/structured-organization.md)、[T13历史结果](acceptance/T13.md) | 需求验证矩阵、隔离、证据及实际限制 |
| 9 | [开发计划](development-plan.md)、[协作规范](../CONTRIBUTING.md) | 六阶段顺序、分支与审核关口 |
| 追溯 | [历史索引](history/README.md) | 只读整改记录，不作为第二份当前规范 |

## 文档之间的维护规则

requirements定义业务规则；domain-model定义行为与不变量；数据库文档定义概念映射和存储；前后端文档定义实现方案。OpenAPI YAML唯一维护字段，JSON同源导出，api-contract只解释Schema以外的约束，api-map只作索引。状态与证据进入testing-acceptance或独立验收记录，不混写成设计已实现。

变更业务、模型或接口时同步受影响规范与测试，保留同一个需求编号。历史摘录保留时间语境，新结论以现行正文为准。原Excel、用户本地设计文档及数据库不清理；已淘汰资料从当前树删除后仍可在Git历史追溯。

结构化整理PR #30已由用户批准合入，原型PR #31也已获批合入，当前进入阶段4正式Vue与结构化后端更新；T13及阶段2保留各自历史证据。阶段4交付另待用户审核，DDD/T14/T15及服务器部署沿后续计划。文档阶段的修改、迁移、归档和删除清单仍见原审核指南。
