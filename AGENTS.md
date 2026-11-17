# RBAC项目协作

当前正式工作在codex/rbac-vue-implementation（PR #32，基于已合入原型PR #31的bd72a34），尚未获准合并。阶段4已提交正式Vue及结构化后端，正在最终文档/共享边界核对；不自动推进DDD、T14/T15、课程接入或服务器部署。主目录main是历史工程，组员复现检出当前分支，见docs/reproduction.md。

业务依据为老师说明、原始资料和用户确认形成的docs/requirements.md；老师Gitee仅提供组织和框架参考。当前正式系统只管理RBAC自身权限，18业务系统及矩阵仅在离线原型演示，不进入正式目录/初始化/授权子集。课程系统暂不考虑；采用全新库首次SQL，不交付升级脚本。历史原型讨论以其时间语境保留在docs/prototype-review.md及requirements/sources.md，不覆盖最新范围。

HTTP字段主规范为api/openapi.yaml，JSON同源；业务语义见docs/api-contract.md。结构化实现归service/structured；service/port是共享用例/模型/协议取值，不放具体实现依赖或组织层级策略。共享基础设施不调用结构化或DDD应用服务，未来DDD持久化适配器可以实现领域仓储并映射领域模型。DDD当前不存在或可用策略开关；后续必须独立业务链和互斥装配，见docs/ddd-boundary.md。

生产代码与测试按用户提供projectmgt-code-conventions和code-review skill执行；本RBAC保留本地口令，分页沿page/size。CloneFactory逐处核对同名同类型映射及实际生成代码，派生/转换字段显式处理，不能只因使用skill就认定无遗漏。Java真实换行、中文Javadoc、TDD、Service事务及根锁后重鉴权沿现规则。

当前只结构化，设计须标明未来DDD目标与实际状态。完整K保留原17项并要求有效基础级别5及可信根；人员目标/授予级别和部门规则见R23—R27。文档写目标职责，实际测试和限制独立记验收；不以历史成绩替代新版本通过。

按CONTRIBUTING.md协作，保留原Excel、用户未跟踪DOCX/Markdown、原库及历史日志；临时凭据/CSV/数据库/日志/产物不提交。修复当前PR可在同一分支追加，按文件暂存并推送草稿PR待用户审核，不自行合并、关闭无关Issue或改变服务器。Apifox写入须用户明确要求。UML仅在适用范围使用用户skill，不生成虚构DDD实现图。
