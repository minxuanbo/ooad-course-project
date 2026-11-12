# 华为云后端部署

> 本页用于运行整改后工程（默认入口为 `com.municipal.rbaccore.runtime.CoreRuntimeCommand`）。数据库及业务代码切换后，须按实际实现复核部署步骤；历史上线结果不代表当前运行状态。

2026-10-27 的旧实现仅部署后端，详见 [历史发布记录](deployment-2026-10-27.md)。现场测试使用 Git 克隆目录，见 [现场说明](现场运行JaCoCo.md)。

此配置只新增 `rbac` Stack，不操作现有 `lab` 服务。A 构建和推送镜像，
C 发布 Stack，B 保存独立 MySQL 数据卷，D 运行后端。

## 构建（A）

在仓库根目录使用 Java 21 多阶段镜像构建，无需在宿主机安装 Java/Maven：

```bash
export RBAC_BACKEND_IMAGE=swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend:替换为版本号
docker build -t "$RBAC_BACKEND_IMAGE" .
docker push "$RBAC_BACKEND_IMAGE"
```

构建阶段先从老师仓库固定提交`237f49c52bdc72da30383571bb2f985ac54a514a`构建安装CloneFactory `0.0.1-SNAPSHOT`，再编译本项目；需可访问Gitee与Maven镜像，不依赖宿主机Maven缓存，不提交老师源码或jar。构建只编译测试，不执行依赖真实数据库的测试。口试覆盖率采用
[独立数据库自动化测试](现场运行JaCoCo.md)。三份 Excel 排除在构建
上下文之外，不进入镜像、不推送 SWR，运行中的后端不再读取 Excel；
组织、人员与账号的初始化由离线命令在数据准备阶段完成（见"新库初始化约束"）。

MySQL 使用官方 8.4 镜像，在 A 拉取后推送到组织内独立镜像仓库。
发布使用镜像摘要，避免可变标签导致不同节点运行不同版本。

## 初始化 secrets（C，仅首次）

使用随机密码创建 `rbac_db_password` 和 `rbac_mysql_root_password` Docker
secrets；密码不要出现在 Git、命令参数或聊天输出中。已有 secret 不覆盖。
后端通过 Spring configtree 读取密码。

## 发布（C）

```bash
export RBAC_BACKEND_IMAGE=实际后端镜像地址及摘要
export RBAC_MYSQL_IMAGE=实际MySQL镜像地址及摘要
docker stack deploy --with-registry-auth -c deploy/stack.yml rbac
docker stack services rbac
docker service logs --tail 100 rbac_backend
```

Swarm 不保证 MySQL 先启动。后端初次连接失败时会自动重试启动。
数据卷 `rbac_mysql_data` 位于 B；不要删除该卷或将数据库调度到其他节点。
新增服务各限制 512 MiB，MySQL 缓冲池为 128 MiB，适用于课程演示。

## 访问与验证

后端只在 D 发布宿主机 8081：`http://120.46.86.147:8081/api`。
安全组应允许所需来源访问 D 的 TCP 8081。MySQL 不发布宿主机端口，
仅在独立加密 overlay 网络供后端连接。

先登录，再使用响应中的 data.token 请求 `/api/me` 验证身份，再以带日志权限的
维护账号请求 `/api/authorization-logs`。管理员账号默认名为 `admin`，口令由初始化配置提供；当前接口为 HTTP，未配置 TLS。
健康检查用无效令牌访问 `/api/me`，预期 401。

## 更新、回滚和备份

每次更新使用新版本镜像；数据库已有数据时保留。单实例更新有短暂中断。
仅无结构变化的兼容版本可直接应用回滚。涉及数据库结构变更时，必须协调对应数据库快照与镜像，不能单独回滚到使用旧表／旧字段的镜像。
首次部署没有上一版本可回滚。数据库变更需事先备份，不能通过应用回滚撤销。
使用 MySQL 逻辑备份并保存到独立位置，禁止把数据库文件或密码提交 Git。
不要执行全局 prune、删除 lab Stack、重启 Docker 或清理已有数据卷。

## 新库初始化约束

首次建库由 DBA 在 MySQL 中创建 `rbac_core` 库（`utf8mb4`、`utf8mb4_0900_bin`）及仅有该库权限的账号，依次执行 `deploy/rbac-core-schema.sql` 与 `deploy/rbac-core-runtime.sql`；脚本只包含 `CREATE TABLE IF NOT EXISTS`，不清空或重建已有表。应用固定 `ddl-auto=validate`、`sql.init.mode=never`、`rbac.initialize=false`，不在启动时建表或导入。
组织、人员与账号初始化使用离线命令，在仓库根目录或持有原始 Excel 的数据准备环境执行。通过环境或秘密配置设置 `RBAC_CORE_DB_URL`、`RBAC_CORE_DB_USER`、`RBAC_CORE_DB_PASSWORD`、`RBAC_CORE_SOURCE_DIR`，并分别提供必填的 `RBAC_CORE_EMPLOYEE_PASSWORD`（全体员工初始口令）与 `RBAC_CORE_ADMIN_PASSWORD`（独立管理员口令）。可选 `RBAC_CORE_ADMIN_USERNAME` 默认 `admin`。不要把口令写在命令参数、Git 或报告中。

依次执行以下步骤；预览与实际授权是两个独立命令：

```bash
# 1. 导入组织、人员及账号，全部启用，此时不授予角色。
java -Dloader.main=com.municipal.rbaccore.importing.CoreImportCommand -cp target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher

# 2. 仅生成预检清单，不写授权；父目录须存在，CSV须为新文件。
mkdir -p .local
java -Dloader.main=com.municipal.rbaccore.runtime.CoreBootstrapCommand -cp target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher --rbac.core.preview-file=.local/initial-role-preview.csv

# 3. 核对清单后，不带preview-file参数执行首次五角色授权。
java -Dloader.main=com.municipal.rbaccore.runtime.CoreBootstrapCommand -cp target/rbac0-0.1.0.jar org.springframework.boot.loader.launch.PropertiesLauncher

# 4. 以产品默认入口启动，不需要指定loader.main或测试profile。
java -jar target/rbac0-0.1.0.jar
```

导入预期为110个部门、10000名员工及1个独立管理员。首次角色人数为系统管理员1、公司领导64、部门经理106、项目经理72、普通员工9758；普通员工不能登录管理端。重复导入/授权命令只核对既有来源与回执，不重置口令、状态或授权。再次启动后，应验证管理员、工号/手机号登录、部门人员查询、部门账号日志403、已删除入口不可执行业务；保留旧库与历史日志，不关联旧用户ID。
后端入口是 D 的 http://120.46.86.147:8081/api，C 不发布后端端口。无登录令牌访问 /api/me 预期返回 HTTP 401。
