# 项目文档

按老师项目的需求、领域、数据库、后端、API职责组织；增加本RBAC所需的前端、初始化及本地复现说明。structured和DDD统一编排在相同正式文档中，不分别维护审核稿。参考项目为课程管理系统，本项目所有模型与命名均采用RBAC业务。

阶段5双实现已随[PR #34](https://github.com/minxuanbo/ooad-course-project/pull/34)合入main（e521f97）。从[本地复现](reproduction.md)运行当前基线；阶段6的浏览器结果见[测试验收](testing-acceptance.md)及[两策略对照](history/verification/stage6-ddd.md)。

| 文档                                                                                                             | 内容                                                                    |
| ---------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| [需求说明](requirements.md)、[来源](requirements/sources.md)                                                     | 范围、统一业务规则、原件及确认依据                                      |
| [领域模型](domain-model.md)                                                                                      | 六核心对象、聚合、值、策略、生命周期与不变量                            |
| [数据库设计](database-design.md)、[物理模型](database/physical-model.md)、[Chen源码](database/chen-er-source.md) | 概念联系、12表字典、约束及两策略共用存储                                |
| [后端设计](backend-design.md)                                                                                    | 总体结构、包、共享契约、structured/DDD职责、仓储、事务、映射、测试与UML |
| [前端设计](frontend-design.md)                                                                                   | Vue页面、组件、权限能力、状态、交互与错误处理                           |
| [API映射](api-map.md)、[业务契约](api-contract.md)、[OpenAPI](../api/openapi.yaml)                               | 页面/操作索引、Schema外语义及唯一字段规范                               |
| [首次初始化](initialization.md)                                                                                  | 原件转换、五角色、来源、整批事务与重复执行保护                          |
| [课程接入](course-integration.md)、[课程补丁](../integrations/course/README.md) | 老师现有身份协议、两个系统的本地配置、最小名单校验补丁及验证范围 |
| [测试与验收](testing-acceptance.md)                                                                              | 需求矩阵、隔离、验证方案及实际结果引用                                  |
| [本地复现](reproduction.md)、[后端运行](../backend/README.md)、[前端运行](../frontend/README.md)                 | 依赖、全新库初始化、命令与端口                                          |
| [开发计划](development-plan.md)、[协作](../CONTRIBUTING.md)                                                      | 实施顺序和分支约定                                                      |
| [历史索引](history/README.md)                                                                                    | 原型决策、阶段检查与验证记录，不作为另一套当前设计                      |

## 维护约定

requirements回答做什么；domain-model定义行为和不变量；数据库文档定义存储；前后端文档定义怎么做。OpenAPI YAML唯一维护字段，JSON同源导出，api-contract解释Schema外规则，api-map仅作索引。图件与源数据归design，第17章直接展示。

structured和DDD共用正式HTTP契约及Vue，通过环境变量选择后端策略。实际执行版本、结果与限制见测试与验收说明。业务范围变化同步受影响文档、接口及测试；原Excel和用户未跟踪的本地概要设计文档不清理。
