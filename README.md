# 市政公司统一权限管理系统

以组织树限定人员管理范围，统一管理用户、角色、菜单、按钮及 API 的授权。工号和手机号定位同一账号；实际有效授权决定操作能力，基础角色级别约束人员管理与授予范围。

同一 Java 21 / Spring Boot / JPA 工程提供 structured 与 DDD 两套独立业务实现，共享 Vue 3 前端、HTTP 契约和 MySQL 结构。通过 `APP_BACKEND_STRATEGY=structured|ddd` 互斥选择，默认 structured；服务器两系统均运行 DDD。管理端与外部应用统一使用通过 Bearer 携带的 HS256 JWT，权限逐请求从数据库重算。

## 文档入口

| 阅读目的 | 文档 |
| --- | --- |
| 范围与业务规则 | [文档导航](docs/README.md)、[需求](docs/requirements.md) |
| 模型与实现 | [领域模型](docs/domain-model.md)、[数据库](docs/database-design.md)、[后端](docs/backend-design.md)、[前端](docs/frontend-design.md) |
| HTTP 与页面 | [OpenAPI](api/README.md)、[业务契约](docs/api-contract.md)、[页面接口对照](docs/api-map.md) |
| 数据与双系统接入 | [初始化](docs/initialization.md)、[课程接入](docs/course-integration.md) |
| 运行与验证 | [本地复现](docs/reproduction.md)、[服务器部署](deploy/README.md)、[测试与验收](docs/testing-acceptance.md)、[JaCoCo报告](docs/reports/jacoco/20261124-performance-rbac/README.md) |
| 协作 | [贡献约定](CONTRIBUTING.md) |

## 运行与数据

本地先按[后端说明](backend/README.md)安装固定来源的真实 CloneFactory 0.0.2，在独立新库执行首次 SQL、资料导入、授权预览和首次授权，再启动后端。Vue 在 `frontend` 下执行 `npm ci`、`npm run dev`，默认 5173 代理后端 8081。产品启动只校验结构，不导入或重授角色。

三份原 Excel 保留原件；111 个组织来源合并为 110 个目标部门，10000 员工加独立 admin 共 10001 账号。员工与管理员的初始口令分别由必填配置提供，各账号独立 BCrypt 盐，无公开默认口令。默认初始化只登记 RBAC，课程应用通过独立配置和脚本接入；18个业务系统按原表通过独立首次脚本登记为待接入目录，五基础角色矩阵同步导入，详见[初始化](docs/initialization.md)。待接入权限只用于规划，不提供业务通行能力。

已发布入口：[课程系统](http://114.116.254.25/)、[RBAC](http://114.116.254.25:8088/)。18业务目录、部门角色、原型界面及日志来源IP已发布，RBAC通过批量装载授权关系和总览并行读取改善菜单加载；首次登录首页空白也已修复并完成正式表单登录验证，本次仅更新RBAC前端。课程前端保持老师原样。实际镜像、保留数据、六项100%覆盖率、加载实测及未验证范围统一从[测试与验收](docs/testing-acceptance.md)查阅。
