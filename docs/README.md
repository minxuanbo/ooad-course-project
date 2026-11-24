# 项目文档

现行文档按职责维护，字段与报文以OpenAPI为准；版本、环境、成绩及限制集中在验收证据，历史成绩只对应当时源码。

| 阅读目的 | 入口 |
| --- | --- |
| 业务要求与来源 | [需求及对应测试](requirements.md)、[资料来源](requirements/sources.md) |
| 领域与数据库 | [领域模型](domain-model.md)、[概念模型及完整物理字典](database-design.md) |
| 双策略与页面 | [后端及差异UML](backend-design.md)、[前端设计](frontend-design.md)、[可编辑图件](design/README.md) |
| 接口与接入 | [业务契约及全部页面API对照](api-contract.md)、[OpenAPI](../api/openapi.yaml)、[课程接入边界](course-integration.md) |
| 初始化与运行 | [首次初始化及完整本地复现](initialization.md)、[服务器部署](../deploy/README.md)、[监控](../deploy/monitoring.md) |
| 验证与操作 | [测试方法、当前状态及自定义角色操作方案](testing-acceptance.md)、[最终JaCoCo](reports/jacoco/final-rbac/README.md) |

当前契约为45操作、37路径、94模型，其中37项管理与8项外部身份操作，完整维护集合K仍17。structured与DDD共用HTTP契约、SQL和Vue SPA；运行版本须以[验收](testing-acceptance.md)为准。

接口对照已并入业务契约，复现并入初始化，角色演示并入测试与验收，物理字典并入数据库设计；不再维护重复入口。独有历史证据合为[三份主题档案](history/README.md)，原型确认及老师纪要仍保留。旧文件可由Git历史恢复，原Excel、桌面原件、旧库/卷和原始日志不属于文档清理范围。
