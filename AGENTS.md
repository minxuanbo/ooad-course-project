# RBAC项目协作

2026-11-19补充：用户指定的structured UI范围已在独立虚构库上完成，浏览器13项、前端26项及构建通过；修复窄屏日志计数隐藏、Modal键盘首尾循环及CollectionEditor失权事件声明，分支codex/structured-ui-acceptance待审核合入。详见docs/structured-ui-acceptance.md。ddd同项UI复验仍待执行，不把共用前端通过当作双策略总验收完成；下段保留阶段5历史基线。

截至2026-11-18，阶段5已完成双轴审核、修复及CloneFactory专项核对，用户批准PR #34合入main，合并提交e521f97，文件树与验收提交83dbf17一致。structured和ddd各178项后端测试、71次真实HTTP通过，前端26项测试/构建及运行器5项单测通过；两套空库各完成10001个BCrypt10账号导入及首次授权。桌面浏览器已复验登录、六个管理页面和关键写入；阶段6仍待窄屏、完整键盘及其他写入组合复验。组员从main复现并记录实际提交，见docs/reproduction.md及docs/testing-acceptance.md。T14/T15、课程接入及服务器部署仍按开发计划。

业务依据为老师说明、原始资料和用户确认形成的docs/requirements.md；老师Gitee仅提供组织和框架参考。当前正式系统只管理RBAC自身权限，18业务系统及矩阵仅在离线原型演示，不进入正式目录/初始化/授权子集。课程系统暂不考虑；采用全新库首次SQL，不交付升级脚本。历史原型讨论以其时间语境保留在docs/history/design-decisions/prototype.md及requirements/sources.md，不覆盖最新范围。

HTTP字段主规范为api/openapi.yaml，JSON同源；业务语义见docs/api-contract.md。结构化实现归service/structured；service/port是共享用例/模型/协议取值，不放具体实现依赖或组织层级策略。共享基础设施不调用结构化或DDD应用服务，DDD持久化适配器实现领域仓储并映射领域模型。产品、导入与首次授权共用互斥装配，见docs/backend-design.md第4章。

后续生产代码与测试按用户2026-11-17提供新版projectmgt-code-conventions执行；本RBAC保留本地口令，分页沿page/size。领域仓储只输入输出domain.model及JDK容器，DomainPage装领域对象，复杂读模型用service.port Gateway；application不依赖JPA/Entity/结构化。final/record不可依赖CloneFactory写入，须显式构造；可变同名同类型字段仍优先真实处理器并核对生成赋值。注解独立换行、中文Javadoc、TDD、Service事务及锁后重鉴权沿新规范。本轮不为了格式改动已批准生产代码。

两套策略分别保留业务实现，共用正式接口和数据模型。完整K保留原17项并要求有效基础级别5及可信根；人员目标/授予级别和部门规则见R23—R27。文档写目标职责，实际测试和限制独立记验收；不以历史成绩替代新版本通过。

按CONTRIBUTING.md协作，保留原Excel、用户未跟踪DOCX/Markdown、原库及历史日志；临时凭据/CSV/数据库/日志/产物不提交。按文件提交并以main为草稿PR目标。Apifox写入须明确要求。类图/顺序图用新版UML skill，图件应与代码实际类和方法对应；包图使用UML包语义，不宣称该skill适用于包图。顺序图只画两套差异，共同部分一律不画。
