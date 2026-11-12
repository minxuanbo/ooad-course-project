# 市政公司统一权限管理系统

面向组织人员、角色、菜单/按钮及API的统一授权管理。用户名即工号，手机号是同账号另一登录入口；实际有效授权决定操作，部门树决定人员管理范围。

## 文档

从[正式文档导航](docs/README.md)开始，按需求、领域模型、数据库、前后端设计、API及验收阅读。业务依据是老师检查说明、原始Excel和用户后续确认；老师示例项目用于文档组织与双实现架构参考。旧需求规格说明书不覆盖现行规则。

| 入口 | 内容 |
| --- | --- |
| [需求说明](docs/requirements.md) | 来源、初始化、五角色、双入口、实际授权、部门范围及项目边界 |
| [后端设计](docs/backend-design.md) / [前端设计](docs/frontend-design.md) | 当前结构化基线、完整前端目标及同仓库双实现方案 |
| [API入口](api/README.md) | 正式契约、页面/用例对照与原型 |
| [测试与验收](docs/testing-acceptance.md) | 阶段状态、验收矩阵、隔离与证据要求 |
| [部署说明](deploy/README.md) | 构建、秘密配置、离线初始化、默认jar及部署边界 |
| [协作规范](CONTRIBUTING.md) | 分支、工作树、评审与Issue维护 |

## 工程状态

共同整改分支为refactor/rbac-core-backend，main仍保留历史工程。T12已合入a79b668，当前生产后端使用整改JPA链路，代码位于src/main/java/com/municipal/rbaccore。当前只有结构化业务实现，尚未分成structured/ddd包，尚无策略切换；DDD属于目标设计。

前端Vue/Vite位于frontend，生产页面目前为登录、最小日志兼容与登录成功外壳；完整人员、角色、资源交互见正式前端设计及[已评审原型](docs/rbac-rectification/prototype/index.html)。原型为内存模拟，不作为真实界面/后端验收。课程受控系统与双业务系统演示尚未完成。

本次工作顺序是文档整理→T13当前后端验收→停止，不启动DDD编码、T14/T15或其他后续任务。阶段结果见[验收说明](docs/testing-acceptance.md)，历史整改与PR证据保留在[整改记录](docs/rbac-rectification/README.md)。

T13本轮实际OpenJDK21/MySQL8.4验收已通过：142项后端回归、3项运行器隔离单测、70次默认JAR真实HTTP及两轮启动/重复初始化；完整证据、源提交和未验收范围见[T13记录](docs/acceptance/T13.md)。用户指定的后续停止点保持不变。

## 运行

后端采用Java21、Boot3.5.6、Spring Data JPA、MySQL8.4、AOP及老师真实CloneFactory。新环境先按[部署说明](deploy/README.md)准备老师固定源码依赖、显式整改库和账号，执行离线导入、只读预览及实际五角色授权；设置RBAC_CORE_DB_URL、RBAC_CORE_DB_USER、RBAC_CORE_DB_PASSWORD后运行：

```bash
mvn spring-boot:run
```

打包后默认java -jar target/rbac0-0.1.0.jar即整改HTTP入口。应用启动不建表、不导入、不重授角色，不能指向旧业务库。

现有前端在frontend目录执行npm ci、npm run dev，默认localhost:5173代理/api到后端8081；npm test及npm run build检查前端。原型可直接打开HTML。

三份原始Excel保留：10000人员、111组织来源，目标合并为110部门、独立admin后10001账号；新项目不复用旧ID/口令/状态/授权。口令由部署秘密配置提供，不在文档给出默认明文。

测试使用与profile对应的本机隔离MySQL专库；功能回归与JaCoCo分别报告，用户确认本轮覆盖率不阻塞，配置保留。不要提交生成报告、CSV、数据库或凭据。
