# 华为云后端部署

此配置只新增 `rbac` Stack，不操作现有 `lab` 服务。A 构建和推送镜像，
C 发布 Stack，B 保存独立 MySQL 数据卷，D 运行后端。

## 构建（A）

在仓库根目录使用 Java 21 多阶段镜像构建，无需在宿主机安装 Java/Maven：

```bash
export RBAC_BACKEND_IMAGE=swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend:替换为版本号
docker build -t "$RBAC_BACKEND_IMAGE" .
docker push "$RBAC_BACKEND_IMAGE"
```

构建只编译测试，不执行依赖真实数据库的测试。发布前需在 A 的独立临时
MySQL 测试库执行 `mvn test`，不得使用现有业务库。三份 Excel 随后端镜像
打包，新库启动自动初始化；数据库已有用户时不会重复导入。

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
docker stack config -c deploy/stack.yml
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

先登录，再使用响应中的 token 请求 `/api/me`、`/api/dashboard` 验证。
默认课程账号见根目录 README；当前接口为 HTTP，未配置 TLS。
健康检查用无效令牌访问 `/api/me`，预期 401，同时触发数据库会话查询。

## 更新、回滚和备份

每次更新使用新版本镜像；数据库已有数据时保留。单实例更新有短暂中断。
应用更新失败由 Swarm 回滚；也可以运行 `docker service rollback rbac_backend`。
首次部署没有上一版本可回滚。数据库变更需事先备份，不能通过应用回滚撤销。
使用 MySQL 逻辑备份并保存到独立位置，禁止把数据库文件或密码提交 Git。
不要执行全局 prune、删除 lab Stack、重启 Docker 或清理已有数据卷。
