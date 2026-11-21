# 市政公司统一权限管理系统

管理组织人员、角色、菜单/按钮及API的授权关系。用户名即工号，手机号是同一账号的另一个登录入口；有效授权决定可执行操作，部门子树和角色级别限定管理边界。

## 项目文档

| 文档                                                                    | 内容                                |
| ----------------------------------------------------------------------- | ----------------------------------- |
| [文档导航](docs/README.md)、[需求](docs/requirements.md)                | 项目范围、业务规则与设计入口        |
| [领域模型](docs/domain-model.md)、[数据库](docs/database-design.md)     | 核心对象、不变量、Chen ER及12张表   |
| [后端设计](docs/backend-design.md)、[前端设计](docs/frontend-design.md) | structured/DDD总体方案及完整Vue交互 |
| [API](api/README.md)、[API映射](docs/api-map.md)                        | 37项操作（29管理+8身份）、OpenAPI及页面对应关系     |
| [初始化](docs/initialization.md)、[本地复现](docs/reproduction.md)      | 全新库、来源、五角色、运行与核对    |
| [测试验收](docs/testing-acceptance.md)、[历史](docs/history/README.md)  | 验证方案、版本结果与限制            |
| [历史阶段计划](docs/history/planning/development-plan-through-r32.md)、[协作](CONTRIBUTING.md)           | 实施阶段与分支约定                  |

文档按老师[course-project-mgt](https://gitee.com/mingqcn/course-project-mgt)的职责组织，业务来源为老师说明、原Excel及确认需求，全部内容和命名使用本RBAC模型。两套实现放在同一后端工程，共享API、Vue与数据库；structured和DDD设计统一见后端文档，第17章展示包图、类图及仅差异顺序图。

## 当前实现

后端backend使用Java21、Spring Boot3.5.6、JPA、MySQL8.4、AOP与真实CloneFactory。service.structured为已实现的事务脚本，Controller只经service.port调用，持久化/认证/日志/Excel归infrastructure。DDD在service.ddd中独立计算领域规则，持久化由领域仓储适配器完成。通过APP_BACKEND_STRATEGY选择structured（默认）或ddd。

正式前端frontend使用Vue3/Vite，包含登录、总览、部门、人员、角色、资源和日志工作区。默认正式目录仅管理RBAC自身；2026-11-19用户启动[课程本地接入](docs/course-integration.md)，显式配置后接入老师课程系统。18业务系统仍仅在[离线原型](prototype/README.md)演示。阶段6约定验收已完成，课程接入证据独立记录。

截至2026-11-18，阶段5已通过审核并随[PR #34](https://github.com/minxuanbo/ooad-course-project/pull/34)合入main（e521f97，验收代码83dbf17）。structured和ddd各178项后端测试、71次真实HTTP检查通过；两套空库各完成10001个BCrypt10账号导入及首次授权。前端26项测试/构建及运行器5项单测通过，CloneFactory生成的51个重载均有实际赋值。

随后main核对修复日志晚提交污染快照、重复导入无来源账号及注解格式。两套独立MySQL实例各180项完整后端测试通过，前端26项测试/构建与运行器5项单测通过，51个CloneFactory生成重载再次核对。新增回归的RED/GREEN及实际验证边界见[测试说明](docs/testing-acceptance.md)。

阶段6两种策略分别在独立数据库通过同一组13项浏览器用例，覆盖窄屏、键盘操作、写入、失败反馈及权限变化。实际结果见[测试验收](docs/testing-acceptance.md)及[两策略对照](docs/history/verification/stage6-ddd.md)。

## 本地运行

依照[后端说明](backend/README.md)安装固定来源CloneFactory，准备独立rbac_core、秘密配置和首次SQL，再执行导入、只读预览及首次授权。默认产物backend/target/rbac0-0.1.0.jar，后端8081；启动不建表、不导入、不重授角色。

前端在frontend执行npm ci、npm run dev，默认5173代理/api至8081；npm test及npm run build用于测试和构建。完整步骤见[本地复现](docs/reproduction.md)。原型可直接打开prototype/index.html。

三份原Excel保留原件，首次111组织来源合并为110部门，10000员工加独立admin共10001账号；不复用旧ID、哈希、状态或授权。口令由配置提供，不提交Git。当前仅提供全新库SQL。[双DDD部署材料](deploy/README.md)已按老师说明适配两个前端、两个单副本DDD后端及独立接入服务；C公开80/8088，云端发布尚未执行，详见[本轮证据](docs/history/verification/ddd-deployment-adaptation.md)。
