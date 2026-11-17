# RBAC项目协作

2026-11-17用户批准合并阶段4：PR #32合入refactor/rbac-core-backend为4822114，PR #1同步main为a4275be，文件树一致。用户已授权基于codex/ddd-design实现双策略后端。组员按交付分支及实际提交复现，见docs/reproduction.md。T14/T15、课程接入及服务器部署仍按开发计划。

业务依据为老师说明、原始资料和用户确认形成的docs/requirements.md；老师Gitee仅提供组织和框架参考。当前正式系统只管理RBAC自身权限，18业务系统及矩阵仅在离线原型演示，不进入正式目录/初始化/授权子集。课程系统暂不考虑；采用全新库首次SQL，不交付升级脚本。历史原型讨论以其时间语境保留在docs/history/design-decisions/prototype.md及requirements/sources.md，不覆盖最新范围。

HTTP字段主规范为api/openapi.yaml，JSON同源；业务语义见docs/api-contract.md。结构化实现归service/structured；service/port是共享用例/模型/协议取值，不放具体实现依赖或组织层级策略。共享基础设施不调用结构化或DDD应用服务，DDD持久化适配器实现领域仓储并映射领域模型。产品、导入与首次授权共用互斥装配，见docs/backend-design.md第4章。

后续生产代码与测试按用户2026-11-17提供新版projectmgt-code-conventions执行；本RBAC保留本地口令，分页沿page/size。领域仓储只输入输出domain.model及JDK容器，DomainPage装领域对象，复杂读模型用service.port Gateway；application不依赖JPA/Entity/结构化。final/record不可依赖CloneFactory写入，须显式构造；可变同名同类型字段仍优先真实处理器并核对生成赋值。注解独立换行、中文Javadoc、TDD、Service事务及锁后重鉴权沿新规范。本轮不为了格式改动已批准生产代码。

两套策略分别保留业务实现，共用正式接口和数据模型。完整K保留原17项并要求有效基础级别5及可信根；人员目标/授予级别和部门规则见R23—R27。文档写目标职责，实际测试和限制独立记验收；不以历史成绩替代新版本通过。

按CONTRIBUTING.md协作，保留原Excel、用户未跟踪DOCX/Markdown、原库及历史日志；临时凭据/CSV/数据库/日志/产物不提交。按文件提交并以main为草稿PR目标。Apifox写入须明确要求。类图/顺序图用新版UML skill，图件应与代码实际类和方法对应；包图使用UML包语义，不宣称该skill适用于包图。顺序图只画两套差异，共同部分一律不画。
