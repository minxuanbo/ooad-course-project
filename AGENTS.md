# RBAC项目协作

现行资料从[文档导航](docs/README.md)进入，业务需求以老师说明、原始资料和用户确认形成的[需求说明](docs/requirements.md)为准；老师Gitee项目用于文档与架构参考。HTTP字段主规范为api/openapi.yaml，跨字段、授权、范围与事务语义见docs/api-contract.md。

2026-11-13用户已审核并授权合入文档PR #29和结构化整理PR #30；当前授权进入阶段3新交互原型。用户确认浅色蓝色企业后台、左侧导航/顶部身份栏、可重置模拟数据，先做登录/外壳/部门人员完整流程，要求直接双击HTML离线运行。按[评审记录](docs/prototype-review.md)在独立分支迭代，用户明确满意后才改造正式前端；不自动合并、启动DDD/T14/T15或调整服务器部署。T13历史记录见docs/acceptance/T13.md，后续边界沿[开发计划](docs/development-plan.md)。

用户第三轮反馈覆盖早期仅管理员改名方案：部门支持新增、改名、删除空部门，无层级调整；局部管理员只能改删严格下级，可在本人/下级下新增。另要求同级/上级人员只读、原Excel18业务系统目录及矩阵、跨系统选权。见需求R23—R28；自定义默认仅管理员可授，职位只读。当前只改原型和设计，正式API/后端/数据库尚未加入新规则；后续实现先同步契约，不复用旧验收冒称新规则通过。

当前结构化代码归于backend/src/main/java/com/municipal/rbaccore/service/structured，共享契约为service/port，尚无DDD代码或策略开关；设计必须区分目标和现状。完整前端属于设计范围，当前原型只是参考，不冒称新原型已获批。

后续代码/测试改动遵循用户提供的projectmgt-code-conventions和code-review skill；本RBAC保留本地口令，分页沿既有page/size契约，不照搬课程系统外部身份或pageSize。UML skill只在其适用范围内使用，实际实现图须有源码依据。

从origin/refactor/rbac-core-backend建立独立codex/分支及工作树，按[协作规范](CONTRIBUTING.md)执行。历史任务/依赖只从[历史索引](docs/history/README.md)追溯，实时状态核对GitHub Issue/PR。保留原Excel、用户本地DOCX/Markdown、旧库及历史日志；按文件暂存，临时材料和生成报告不提交。
