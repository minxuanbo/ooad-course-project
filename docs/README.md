# 项目文档

这里仅维护当前规范；阶段过程、旧设计与验收原始结论集中在[history](history/README.md)。参考老师课程项目的文档分工，所有模型与实现均以本RBAC源码为准。

| 阅读目的 | 当前入口 |
| --- | --- |
| 业务范围与确认依据 | [需求](requirements.md)、[来源](requirements/sources.md) |
| 领域对象与存储 | [领域模型](domain-model.md)、[数据库设计](database-design.md)、[物理字典](database/physical-model.md) |
| 两套后端怎么实现 | [后端设计](backend-design.md)，第17章统一展示两套类图、差异顺序图及完整覆盖矩阵 |
| 页面和HTTP契约 | [前端设计](frontend-design.md)、[页面接口对照](api-map.md)、[业务契约](api-contract.md)、[OpenAPI](../api/openapi.yaml) |
| 首次初始化与课程接入 | [初始化](initialization.md)、[课程本地接入](course-integration.md) |
| 运行及验收 | [本地复现](reproduction.md)、[测试与验收](testing-acceptance.md) |

当前37项接口共用structured/DDD两策略和同一Vue；管理端与外部应用统一使用JWT，受众和管理准入保持隔离。历史测试成绩不视为当前修改后的通过证据，最新执行范围见测试与验收。

字段以OpenAPI YAML为唯一规范，JSON同源派生；api-contract解释Schema之外的业务语义。图件和生成数据集中在design，说明见[图件入口](design/README.md)；不单独维护另一套DDD或structured设计稿。服务器部署等待老师说明，详设Word/PDF/PPT依用户要求保持原版。
