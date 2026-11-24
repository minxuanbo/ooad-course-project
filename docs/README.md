# 项目文档

正文按现行规则和设计职责组织。需求解释“系统应做什么”，设计解释“如何实现”，运行文档给出执行方法；实测版本、结果和限制集中在验收证据中。

| 阅读目的 | 入口 |
| --- | --- |
| 业务范围与依据 | [需求](requirements.md)、[来源](requirements/sources.md) |
| 对象与存储 | [领域模型](domain-model.md)、[数据库设计](database-design.md)、[物理字典](database/physical-model.md) |
| 两套后端 | [后端设计](backend-design.md)，第17章包含并列类图、差异顺序图与操作追溯 |
| 页面与 HTTP | [前端设计](frontend-design.md)、[页面接口对照](api-map.md)、[业务契约](api-contract.md)、[OpenAPI](../api/openapi.yaml) |
| 数据与外部应用 | [初始化](initialization.md)、[课程接入](course-integration.md) |
| 执行与验证 | [本地复现](reproduction.md)、[服务器部署](../deploy/README.md)、[监控设计](../deploy/monitoring.md)、[测试与验收](testing-acceptance.md)、[JaCoCo报告](reports/jacoco/final-rbac/README.md) |

当前契约共37项操作（29管理、8身份），structured 与 DDD 共用一套前端和数据库。OpenAPI YAML 是 HTTP 字段主规范，JSON 同源派生；业务契约补充范围、事务与错误语义。[图件入口](design/README.md)说明有效图集及生成方式。

有追溯价值的来源、原型确认和实测证据保留在[证据索引](history/README.md)；过期计划、重复说明及被现行图集替代的旧图已清理，原版本仍可从 Git 历史恢复。原 Excel、用户外部文件、旧库及原始日志不属于清理范围。

[18系统目录与部门角色设计](requirements/access-extension-plan.md)为现行设计，完整18系统首次配置及部门角色授撤已发布；具体版本、数据保留和通过范围见验收入口。
