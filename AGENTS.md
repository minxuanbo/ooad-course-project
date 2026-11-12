# RBAC项目协作

现行资料从[文档导航](docs/README.md)进入，业务需求以老师说明、原始资料和用户确认形成的[需求说明](docs/requirements.md)为准；老师Gitee项目用于文档与架构参考。HTTP字段主规范为api/openapi.yaml，跨字段、授权、范围与事务语义见docs/api-contract.md。

2026-11-13用户已审核并授权合入文档PR #29；当前授权进入结构化代码/测试归类，确定共享API、用例及基础设施边界，修复格式、CloneFactory和职责，并同步文档。使用独立分支交付，不自动合并或推进新前端原型、正式前端、DDD或T14/T15。用户补充部署先不处理，待增加服务器后确定新方案。T13已完成，记录见docs/acceptance/T13.md；旧“T13后停止”是当时边界，不代替最新[开发计划](docs/development-plan.md)。

当前结构化代码归于backend/src/main/java/com/municipal/rbaccore/service/structured，共享契约为service/port，尚无DDD代码或策略开关；设计必须区分目标和现状。完整前端属于设计范围，当前原型只是参考，不冒称新原型已获批。

后续代码/测试改动遵循用户提供的projectmgt-code-conventions和code-review skill；本RBAC保留本地口令，分页沿既有page/size契约，不照搬课程系统外部身份或pageSize。UML skill只在其适用范围内使用，实际实现图须有源码依据。

从origin/refactor/rbac-core-backend建立独立codex/分支及工作树，按[协作规范](CONTRIBUTING.md)执行。历史任务/依赖只从[历史索引](docs/history/README.md)追溯，实时状态核对GitHub Issue/PR。保留原Excel、用户本地DOCX/Markdown、旧库及历史日志；按文件暂存，临时材料和生成报告不提交。
