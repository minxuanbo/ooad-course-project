# 构建、初始化与后端部署

> 2026-11-13：用户确认将增加服务器，部署方案另行确定。本页云拓扑、根Dockerfile/Stack及旧容器测试脚本保留历史配置，尚未适配新的backend目录，不作为当前发布步骤。当前本地构建/初始化统一见[backend/README](../backend/README.md)。

当前后端本机隔离验收及完整初始化复现见[T13记录](../docs/acceptance/T13.md)。该结果包含实际Java21、正式空库/五角色初始化及默认JAR两轮，不代表Docker镜像/Swarm已实测；本页仍分别说明部署步骤与尚未验证的云端状态。

> 本页用于运行整改后工程（当前本地默认入口为 `com.municipal.rbaccore.bootstrap.RbacApplication`）。数据库及业务代码切换后，须按实际实现复核部署步骤；历史上线结果不代表当前运行状态。

旧发布说明已从当前树清理，原版本仍在Git历史；当前结构和默认入口按本页执行。现场测试见[现场说明](现场运行JaCoCo.md)。下文A/B/C/D为既有课程部署拓扑，公网地址仅为环境配置记录，不表示本分支已发布或地址当前健康。

此配置只新增 `rbac` Stack，不操作现有 `lab` 服务。A 构建和推送镜像，
C 发布 Stack，B 保存独立 MySQL 数据卷，D 运行后端。

## 本机依赖安装

使用JDK21、Maven及Git。老师course-project-mgt通过真实CloneFactory映射；本项目沿用cn.edu.xmu:clonefactory:0.0.1-SNAPSHOT，但以源码提交固定实际内容，不按可变SNAPSHOT拉取未知实现。来源为[老师spring-fundamentals](https://gitee.com/mingqcn/spring-fundamentals/tree/237f49c52bdc72da30383571bb2f985ac54a514a/clonefactory)，已核对来源版本和Java21编译方式；来源仓库许可为Apache-2.0，不将其源码或jar复制进本仓库。

以下PowerShell命令在仓库根执行，使用新的本地忽略目录；目录已存在时先核对所属及提交，不覆盖。安装命令与Dockerfile一致，编译器proc=full使老师处理器源码可在Java21正确编译，不改老师源码或升级本项目Boot。

```powershell
git clone --no-checkout https://gitee.com/mingqcn/spring-fundamentals.git .local/teacher-clonefactory-build
git -C .local/teacher-clonefactory-build checkout --detach 237f49c52bdc72da30383571bb2f985ac54a514a
git -C .local/teacher-clonefactory-build rev-parse HEAD
Push-Location .local/teacher-clonefactory-build/clonefactory
mvn -B -ntp -Dmaven.compiler.proc=full clean org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile jar:jar install:install
Pop-Location
```

确认安装BUILD SUCCESS且提交完全一致，再构建本工程。完整功能回归需先准备对应隔离MySQL专库，见[T13复现](../docs/acceptance/T13.md#2-可复现步骤)；只打包时可以显式跳过测试，但不得把该构建结果表述为回归通过。首次依赖下载需要Gitee/Maven网络。密码、token、生成报告和老师源码留在忽略目录，不提交Git。

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

数据库与来源业务规则沿[初始化设计](../docs/initialization.md)。当前本地构建、必填秘密、离线导入、预检、首次授权及默认JAR命令统一维护在[后端说明](../backend/README.md#3-新项目本地初始化与运行)；不再维护第二份旧目录命令。云端新服务器方案确认后，再在本页同步适配部署路径和秘密装配。
