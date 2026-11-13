# 市政公司统一权限管理系统

管理组织人员、角色、菜单/按钮及API的授权关系。用户名即工号，手机号是同一账号的另一个登录入口；有效授权决定可执行操作，本人部门及后代限定人员管理范围。

## 文档与审核

资料按老师[course-project-mgt](https://gitee.com/mingqcn/course-project-mgt)的职责组织；业务依据为老师说明、原Excel及用户后续确认，不以老师课程业务或旧需求规格说明书替代本RBAC需求。

| 入口 | 内容 |
| --- | --- |
| [文档导航](docs/README.md) / [阶段4审核记录](docs/acceptance/stage4-vue.md) | 正式目录、逐项阅读顺序、变更与待审点 |
| [需求](docs/requirements.md) / [来源](docs/requirements/sources.md) | 项目范围、业务规则及确认依据 |
| [领域](docs/domain-model.md) / [数据库](docs/database-design.md) | 核心对象、规则、Chen ER、12张物理表 |
| [后端](docs/backend-design.md) / [前端](docs/frontend-design.md) | 同仓库双实现目标、完整RBAC前端设计 |
| [API](api/README.md) / [参考原型](prototype/README.md) | OpenAPI、29项操作及原交互参考 |
| [初始化](docs/initialization.md) / [部署](deploy/README.md) | 来源映射、五角色、依赖安装与运行 |
| [测试验收](docs/testing-acceptance.md) / [开发计划](docs/development-plan.md) | 历史T13基线、当前检查及后续审核关口 |
| [协作](CONTRIBUTING.md) / [历史](docs/history/README.md) | 分支、审核与只读整改追溯 |

## 当前基线与目标

共同基线为refactor/rbac-core-backend，main仍保留历史工程。当前后端Maven项目位于backend，代码位于backend/src/main/java/com/municipal/rbaccore，使用Java21、Boot3.5.6、JPA、MySQL8.4、AOP与老师真实CloneFactory。业务层已按service/structured归类，共享API经service/port调用，持久化和认证/日志等归入infrastructure；没有DDD实现或策略开关。详见[代码归类](docs/code-organization.md)。

正式前端frontend为Vue3/Vite，阶段4包含登录、总览、部门、人员、角色、资源与日志，全部对接真实RBAC接口，使用说明见[前端README](frontend/README.md)。顶层prototype仅保留当前交互原型，旧四组和v2并行目录已按用户要求替换，历史从Git追溯。课程系统指老师Gitee课程项目，T14/T15等待老师进一步要求。

T13历史验收记录142项后端回归、3项运行器安全单测、70次默认JAR真实HTTP及两轮启动，详见[原验收记录](docs/acceptance/T13.md)。当前结构化归类另取得156项后端回归、3项运行器安全单测、70次完整探针及最终产物3次真实HTTP检查通过的新证据，详见[本阶段记录](docs/acceptance/structured-organization.md)；这些结果不代表完整前端、DDD或云端通过。

文档PR #29、结构化整理PR #30及原型PR #31均已获批合入。阶段4在独立codex/rbac-vue-implementation分支交付正式前后端，当前仅管理RBAC自身权限，18业务系统只在离线原型演示，课程系统暂不考虑接入。首次SQL直接适用于全新库，不提供旧库升级脚本；本轮不操作服务器。

本轮后端162项、前端17项及构建、全新隔离库默认JAR47次HTTP通过；CloneFactory逐处及真实生成代码已核对。完整浏览器验证仍有明确限制，双轴审核与证据见[阶段4记录](docs/acceptance/stage4-vue.md)，不把组件/接口成绩冒称全量UI通过。分支待用户审核，不自动合并或推进DDD。

## 运行入口

本地构建/初始化见[后端说明](backend/README.md)，先安装固定来源CloneFactory、显式准备独立rbac_core及秘密配置，再执行离线导入、只读预览、实际五角色授权。用户确认云部署待新增服务器方案确定后再整理，旧容器配置暂不适配本轮目录。默认jar为backend/target/rbac0-0.1.0.jar；启动不建表、不导入、不重授角色。不能指向旧业务库。

现有前端在frontend执行npm ci、npm run dev，默认5173代理/api到后端8081；npm test和npm run build分别检查测试与构建。参考原型可直接打开prototype/index.html。

三份原Excel保持原件；目标为111组织来源合并到110部门、10000员工加独立admin共10001账号。新项目不复用旧ID/哈希/状态/授权，口令来自部署秘密。测试使用隔离MySQL专库；JaCoCo独立报告，当前不作为阻塞，不提交凭据或生成报告。
