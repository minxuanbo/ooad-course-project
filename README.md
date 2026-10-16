# 市政公司统一权限管理系统（RBAC0）

本项目依据 `需求分析.md` 及三份 Excel 数据构建。当前范围是 RBAC0：用户—角色、角色—权限两个多对多关系；不包含角色继承、互斥角色和数据行级权限。

## 已实现

- 首次启动从员工表、部门表和功能权限清单自动初始化 MySQL 数据库。
- 10,000 名员工、组织树、业务系统、功能点和五类预设角色。
- 工号/手机号/用户名 + 密码登录，服务端会话令牌，退出登录。
- 用户分配多个角色；角色分配多个权限；有效权限取并集，默认拒绝。
- 当前用户权限集合接口和逐次后端鉴权接口。
- 鉴权允许/拒绝均写入日志；授权变更另行记录。
- 管理端提供概览、用户授权、角色权限、业务系统、鉴权日志页面。

## 启动

采用前后端分离架构：后端 Spring Boot 3 + JDBC + MySQL，前端 Vue 3 + Vue Router + Vite。前端源码位于 `frontend/`，后端位于根目录 Maven 工程，独立启动、构建和部署。需要 JDK 21+、Maven 3.9+、Node.js 22.12+（建议 Node 24）、MySQL 8.0.16+（本机验证为 8.4.0）。

本机开发环境可运行以下命令（需要 Python 3，且 mysql、mysqld 在 PATH 中）：

```bash
python3 scripts/local_mysql.py
```

脚本启动项目独立的 MySQL，绑定 `127.0.0.1:3307`，分别建立应用库 `rbac` 和测试库 `rbac_test`。随机密码保存在被 Git 忽略的 `.local/mysql/` 中，文件权限为 600；应用启动自动读取本地配置。脚本不安装系统服务，电脑重启后需再次运行。

若使用自行管理的 MySQL，请先建库与专用账号，再通过 `RBAC_DB_URL`、`RBAC_DB_USER`、`RBAC_DB_PASSWORD` 配置连接。测试必须另设 `RBAC_TEST_DB_URL`、`RBAC_TEST_DB_USER`、`RBAC_TEST_DB_PASSWORD`，不得指向业务库。数据库采用 utf8mb4，连接会话时区设为 UTC，完整 DDL 见 `src/main/resources/schema.sql`。

从旧 SQLite 迁移时，必须先停用旧后端，且目标库为空，再在启动新后端之前执行：

```bash
python3 scripts/migrate_sqlite_to_mysql.py
```

脚本只读原 `data/rbac.db`，建立备份，在 MySQL 事务中导入原 ID、密码摘要、授权、日志、会话和交接数据，并校验所有列。时间统一转换到 UTC 毫秒精度。目标非空时拒绝执行，不覆盖、不删除旧数据。当前本机数据已经迁移，无须重复执行；报告保存在 `.local/mysql/migration-report.txt`。

终端一，在项目根目录启动后端：

```bash
mvn spring-boot:run
```

后端 API 地址为 <http://localhost:8081/api/me>（需登录令牌），不再提供前端页面。启动创建缺失表，仅在 users 为空且 `RBAC_INITIALIZE=true` 时导入演示数据；已有数据保留。可通过 `PORT` 环境变量改端口。

终端二，在项目根目录运行：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 <http://localhost:5173>；不要直接打开 HTML 文件。Vite 将 `/api` 代理到后端 8081；后端端口变化时以 `API_TARGET=http://127.0.0.1:新端口 npm run dev` 启动前端。前端使用 Vue 单文件组件、命名过程函数和循环组织业务逻辑，不使用类式组件；Spring Boot 仍按框架要求保留 Java 类，并非纯结构化编程项目。

用户与授权页面支持“公司 → 部门”联动筛选，切换公司清空部门选择，筛选即时查询；可叠加姓名、工号、手机号搜索，支持重置和准确分页总数。电话独立成列，缺失显示“—”。管理页面和这些查询接口仍仅对管理员开放。

独立构建：

```bash
mvn clean package
cd frontend
npm run build
```

后端 JAR 位于 `target/`，前端静态产物位于 `frontend/dist/`，不复制进后端 JAR。生产部署可参考 `frontend/nginx.conf.example`，由 Nginx 托管前端并转发 `/api/`，同时为 Vue 路由提供 history fallback。使用同源反向代理，无需开放任意来源 CORS。生产环境另需配置 HTTPS。

- 管理员：`admin` / `admin123`
- 普通员工：`SG010000` / `123456`（以数据库当前账号状态为准）

这些是课程演示初始密码，正式部署前必须修改并补充强制改密流程。

建议在项目根目录运行，以便加载本地数据库配置和数据源文件。如从其他目录运行，请设置 `RBAC_SOURCE_DIR` 指向三份 Excel 所在目录，并显式提供 MySQL 连接环境变量。

## 核心 API

统一登录后进入“我的应用”，仅显示有权限的系统；只有一个时自动校验并进入。系统内按模块 → 功能点 → 操作展示，无权限按钮置灰。点击可用按钮实时调用后端鉴权并写日志，不执行实际业务 CRUD。普通员工无法查看日志，管理员可查看。系统、模块、功能、动作、权限和角色均须有效。

页面每 3 秒同步权限，重新聚焦时也会同步；点击入口始终读取后端最新权限。撤权提交后的新请求立即拒绝，页面同步间隔不作为授权缓存。服务断开时清空目录并禁用操作，禁止离线进入。撤销后台管理权限后，页面自动返回“我的应用”，管理接口即时拒绝。当前后台准入按有效“系统管理员”角色判断。

新增接口：`GET /api/me/applications` 获取本人系统状态；`POST /api/me/applications/{id}/enter` 实时准入校验并记录允许/拒绝日志；`GET /api/me` 返回 `canManage`。所有管理读写接口均在服务端检查管理权限。

运行 `mvn test` 可验证普通员工接口隔离、同一会话撤权/恢复、离职交接、组织筛选、MySQL 外键和 UTC 会话时间。测试使用独立的真实 MySQL 测试库，测试用例中的修改通过事务回滚；首次测试会初始化测试库，不能使用业务数据库执行。

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/auth/login` | 登录 |
| GET | `/api/users/page?q=&company=&departmentId=&page=0&size=20` | 组合筛选用户，返回 items/total/page/size |
| GET | `/api/users/filters?company=...` | 所有公司及选定公司下的部门选项 |
| GET | `/api/users` | 保留旧数组接口，亦支持公司和部门筛选 |
| GET | `/api/me/permissions?app=...` | 获取当前用户权限集合 |
| POST | `/api/authorize` | 检查当前用户的一项权限并写鉴权日志 |
| GET/PUT | `/api/users/{id}/roles` | 查询/替换用户角色 |
| GET/PUT | `/api/roles/{id}/permissions` | 查询/替换角色权限 |
| GET | `/api/authorization-logs` | 查询鉴权日志 |

鉴权请求示例：

```json
{
  "appCode": "oa协同办公系统",
  "permissionCode": "oa协同办公系统:公文管理:收文登记与流转:read",
  "requestId": "业务系统生成的追踪号"
}
```

所有受保护接口使用 `Authorization: Bearer <token>`。权限清单中的中文名称会经过 NFKC 规范化；动作使用 `read/create/update/delete/approve/export/configure`。

## 设计说明

- 数据库按课程要求采用 MySQL / InnoDB，生产部署仍需补充版本化迁移、备份恢复、高可用和连接安全。
- 每次 `/api/authorize` 都读取当前授权关系，因此撤权提交完成后的新请求立即按新权限判断，不依赖重新登录。
- 前端菜单仅用于交互，真正的业务操作应由业务系统后端调用鉴权接口。
- 当前管理操作限定为“系统管理员”角色。需求中的组织管理范围属于扩展设计，不作为 RBAC0 角色层级或继承实现。
- 员工初始角色按权限表的五类角色和岗位/部门经理信息推导；它是可修改的初始化方案，不将岗位永久等同于角色。

## 后续优先事项

### 离职交接

管理员在“用户与授权”中找到员工，点击“离职交接”。填写接收人及负责人工号、预计完成日期、说明和可选 OA 单号。开始交接保留原角色和登录状态，可通过现有角色分配入口手动回收部分权限；不会把权限复制给接收人。

交接完成后填写完成说明并勾选确认，提交“完成离职处置”。同一事务内回收全部角色、停用账号、撤销所有会话、更新为已离职并记录原角色。重复完成不会重复记账。已离职员工不能重新分配角色，历史人员和日志保留。当前版本暂不提供再入职与撤销离职流程。

员工列表分别显示账号和人员状态。交接页提供当前角色、有效系统和最近 50 条变更记录。当前操作者不能办理自己的离职。该功能仍使用当前管理角色权限边界；操作级权限拆分方案见 [用户操作权限演示方案](docs/用户操作权限演示方案.md)，尚未实施完整用户 CRUD。

生产化前至少需要：强制改密/密码重置、敏感字段加密或脱敏、CSRF/限流、会话清理、数据库迁移、日志归档、组织管理范围校验，以及压力与故障测试。

## 课程设计文档

根目录原始 `需求分析.md` 保留为需求来源记录；以下五份为依据当前实现编写的正式文档（2026-10-08）：

1. [需求分析](docs/课程设计/01-需求分析.md)
2. [界面设计文档](docs/课程设计/02-界面设计文档.md)
3. [API设计文档](docs/课程设计/03-API设计文档.md)
4. [数据库设计文档](docs/课程设计/04-数据库设计文档.md)
5. [详细设计文档](docs/课程设计/05-详细设计文档.md)

## V2 目录与数据库升级（2026-10-16）

当前 MySQL 为 15 表：公司、部门、用户以及系统—模块—功能点—操作权限分层建模。公司通过部门关联查询，功能说明仅保存于功能点；RBAC0 授权关系及历史日志保留。

已有旧版 MySQL 数据库须先停止后端和其他写入程序，再运行：

```bash
python3 scripts/normalize_mysql.py --database rbac
python3 scripts/normalize_mysql.py --database rbac --apply
```

第一条仅预检；第二条先备份再升级，并核对保留字段和有效授权。备份与报告位于私有 `.local/mysql/backups/`，包含敏感数据，不得提交 Git。DDL 不能整体事务回滚，出现中断需检查报告或人工恢复，不能盲目重试。本机业务库已迁移，无需重复执行。全新库启动时直接创建 V2；历史 SQLite 导入使用冻结 V1 DDL，再调用 V2 迁移，该历史导入路径本轮未做端到端复测。

新增目录接口：`GET /api/me/applications/{id}/modules`、`GET /api/me/applications/{id}/modules/{moduleId}/features`，均独立校验系统准入。原离线原型保持离线，不连接真实后端。

验证命令：

```bash
mvn test
npm --prefix frontend test
npm --prefix frontend run build
```

本轮迁移测试库及全新测试库各通过 20 项后端测试，前端通过 6 项逻辑测试及生产构建。详细范围、限制与结果见 [实施与测试报告](docs/课程设计/08-实施与测试报告.md)。
