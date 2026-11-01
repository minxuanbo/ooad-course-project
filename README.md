# 市政公司统一权限管理系统（RBAC 课程项目）

当前依据《2-9组检查记录》第一轮纪要整改。**原版整改计划是后续执行依据；本次只同步文档，业务代码仍是旧实现。**检查记录规定的要求、用户补充和方案建议在整改文档中分别标注。

## 当前整改入口

| 文档 | 用途 |
| --- | --- |
| [整改执行入口](docs/rbac-rectification/RBAC整改执行入口.md) | 其他对话的开工规则、整改基线、单票入口与可复制指令 |
| [已确认任务清单](docs/rbac-rectification/RBAC整改任务清单.md) | 15 张票、22 条依赖；平台发布尚未完成，组员人工认领 |
| [原版整改计划与 Chen ER 图](docs/rbac-rectification/RBAC整改计划与Chen-ER图.md) | v0.6，执行依据；包含模型、规则、实施顺序与验收清单 |
| [组内讨论版](docs/rbac-rectification/RBAC整改方案-组内讨论版.md) | 阅读、讨论和核对；讨论结论先回写原版再实施 |
| [检查记录复核说明](docs/rbac-rectification/RBAC检查记录复核说明.md) | 检查要求、旧问题、方案建议及未完成事项 |
| Chen ER 图：[PNG](docs/rbac-rectification/RBAC-ER图-预览.png) / [SVG](docs/rbac-rectification/RBAC-ER图.svg) | 六个核心实体；两份计划均保留三段 Mermaid 关系源码 |

本次采用的账号规则（用户补充）：有效账号在既有身份校验通过后允许登录；无管理权限时显示“当前账号暂无管理权限”提示。后端按具体权限和部门范围限制管理请求。不增加管理员属性、独立登录资格或新的认证功能。

正式接口实现前，仍须先评审正确的界面原型，再审核需求/API 契约。课程系统的受控场景及双系统授权演示尚待落实，仅预留多系统字段不算完成。文档导航见 [docs 入口](docs/README.md)。

## 旧实现资料（仅供差异参考）

以下功能、接口、运行方式和报告记录整改前的实现，仅用于核对差异及了解旧版运行状态；旧需求、数据库设计、详细设计、ER、原型和测试报告均不作为整改执行契约，也不能证明新结构已通过验收。

课程项目：Spring Boot 3 / Java 21 / JDBC / MySQL，前端 Vue 3 / Vite。业务权限采用用户—角色—权限；管理权限由角色操作与员工该角色的公司／部门范围共同决定。

### 功能

- 三份 Excel 首次导入组织、10,000 名员工、应用功能目录及五类角色。
- 用户名／工号／手机号登录，统一标识空间跨字段唯一；BCrypt 密码，服务端会话摘要与集中撤销。
- 应用准入、功能鉴权、角色权限和员工范围配置、精确部门、多范围、最后平台管理员保护、离职交接撤权。
- 用户／系统／结果／事件／时间过滤、日志游标分页、逻辑归档；记录登录失败、未认证及拒绝请求。
- 统一 `{code,message,data,requestId}` 响应，稳定业务错误码；Controller、Service、Repository、DTO 分层。
- 组织采用公司—部门，数据库部门范围仅存部门 ID；完整结构为 18 表、23 外键。

沿用系统管理员、公司领导、部门经理、项目经理、普通员工。部长／副部长使用部门经理，项目经理／项目副经理／项目总工程师使用项目经理，不增加独立审计员。只有系统管理员的 GLOBAL audit.read 可查看／归档全局日志。

### 本地运行

需要 Java 21、Maven 3.9+、MySQL 8.0.16+（推荐 8.4）、Node 22.12+。从仓库根目录启动后端，Excel 保持原位置，或设置 RBAC_SOURCE_DIR。
自行管理数据库时，通过 RBAC_DB_URL、RBAC_DB_USER、RBAC_DB_PASSWORD 提供连接；DBA 先执行 schema.sql 和 deploy/install-login-constraints.sql，应用不需要 SUPER 权限。
项目私有 MySQL 辅助脚本 `python3 scripts/local_mysql.py` 面向支持 Unix socket 的本地 MySQL 环境，在 127.0.0.1:3307 建立独立开发／测试库；Windows 现场推荐使用构建服务器测试流程。凭据只写入忽略目录 .local/mysql，不提交 Git。

```bash
mvn spring-boot:run
```

另开终端：

```bash
cd frontend
npm ci
npm run dev
```

浏览器打开 http://localhost:5173。Vite 代理 /api 到后端 8081；可用 API_TARGET 指定目标。前后端独立构建，前端资源不打入后端 JAR。当前云环境只部署后端，前端交付源码与离线原型。
课程初始账号 admin/admin123、SG010000/123456 仅适用于初始化后的演示数据，实际状态以数据库为准。

### API 与测试

所有路径以 /api 开头。POST /auth/login 返回 data.token；受保护接口使用 Authorization: Bearer <token>。GET 登录接口返回 405，浏览器访问 API 根路径不代表应用页面。
成功 code=OK；列表放在 data 中；写操作 data=null。错误包含稳定 code、中文 message、data=null 和服务端 requestId；HTTP 状态仍区分 400/401/403/404/405/409/500。

完整接口见 [API 设计](docs/课程设计/03-API设计文档.md)。GET /authorization-logs 返回游标页，POST /authorization-logs/archive 按截止时间逻辑归档；归档不会删除或移出数据库。

[旧版报告](reports/README.md)：59 项后端测试与六项 100% 结构覆盖率，13 项前端／原型逻辑测试通过。[口试现场操作](deploy/现场运行JaCoCo.md)会启动独立真实 MySQL，自动生成完整 HTML/XML/CSV/exec 报告，不能将测试连接指向业务库。
测试包含故障模拟；100% 不代表所有输入、性能或安全无缺陷。历史未采集的认证事件不能事后补造。

### 交付与边界

- [文档导航](docs/README.md)、[数据库设计.adoc](docs/课程设计/04-数据库设计文档.adoc)、[详细设计.adoc](docs/课程设计/05-详细设计文档.adoc)、[旧版 ER 图](docs/课程设计/图表/07-数据库ER图.md)。
- [离线原型](docs/原型/06-界面原型.html)与四份配套 JS 同目录，不连接真实后台。
- [部署说明](deploy/README.md)、[旧版上线与备份记录](deploy/deployment-2026-10-27.md)。Excel 只在服务器本地只读挂载，不进入镜像或 SWR。

本平台演示权限判断，不执行业务系统真实 CRUD。HR 持续同步、OA 自动审批、用户全量 CRUD、组织移动、密码重置、外部冷存储、HTTPS 和性能专项验证不在已交付能力内。
从空 MySQL 数据库启动：schema.sql 建立最终表结构，DatabaseConstraints 建立外键与登录标识约束，DataInitializer 在同一事务导入 Excel 和默认角色范围。非空库不会重新授予默认权限。
