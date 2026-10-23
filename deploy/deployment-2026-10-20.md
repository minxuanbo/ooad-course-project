# 后端部署记录（2026-10-20，北京时间）

本文记录 2026-10-20 的发布快照，不保证后续时刻的运行状态或数据数量仍相同。

## 当日上线情况

- 公网 API：`http://120.46.86.147:8081/api`
- C 管理节点：`rbac` Stack，新增 `rbac_backend`、`rbac_mysql`，均 1/1。
- B：MySQL 8.4.11，独立数据卷 `rbac_mysql_data`，不发布数据库端口。
- D：Java 21 后端，8081 使用 host 模式，仅在 D 发布。
- 两个新增容器均为 healthy；原有四个 `lab` 服务副本正常，原容器未重启。

## 发布产物

构建提交：`b32bf21`。

后端：
```
swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend@sha256:a49579eed569d64cfaeba953769eb00d9fe56586d65f378a54daa4900a38c9ba
```

MySQL：
```
swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-mysql@sha256:54ad54987c01f1d7af9e1343910fd0cf0d4c4526b552f13e712ddbeacf13dd02
```

MySQL 代理来源的多架构摘要与 Docker Hub 官方 8.4 记录一致：
`sha256:0f51e63e537a1f176423fa03ef559aabe6fa4372a53160a44bc7e75da7d15364`。
SWR 中保存的是本次拉取的 linux/amd64 镜像。

三份 Excel **不在发布镜像中**；在 D 的 `/opt/course-rbac/source/` 只读挂载，
传输后逐份 SHA256 与本地一致。目录 UID/GID 为 10001，权限 750，文件权限 640。
数据库随机密码保存于 C 的 `/root/.config/course-rbac/`（仅 root 可读），并
通过 Docker secrets 提供给服务，未提交 Git。该目录的 `images.env` 保存
本次镜像地址；再次发布前可 `source /root/.config/course-rbac/images.env`。

## 验证

- 公网管理员登录、`/api/me`、`/api/dashboard` 均成功。
- 普通员工登录成功，`canManage=false`，访问管理员接口返回 403。
- 测试登录会话已注销。
- 初始化：10,001 个用户（含管理员）、111 个部门、18 个应用、5 个角色、
  2,521 项权限。
- 验收时新增 MySQL 使用约 242 MiB，后端约 158 MiB，各限制 512 MiB。

首次发布时，后端曾因数据库镜像尚未就绪而退出一次，之后由 Swarm 自动
重启并成功初始化。旧失败任务会保留在 Swarm 历史中，不代表当前服务故障。

## 当前范围

仅部署后端，未部署前端；访问 `/` 不会显示登录页面。登录使用 POST
`/api/auth/login`，请求字段为 `account` 和 `password`，后续使用 Bearer token。
演示账号见项目根 README。当前公网入口为 HTTP，仍使用课程默认登录密码；
本次未实施 HTTPS、密码策略改造或压力测试。


## 后续测试方案调整（2026-10-23）

2026-10-21 曾在原业务 JAR 外附加仅容器内监听的 JaCoCo agent，并创建专用演示人员。
此后镜像为 rbac-backend@sha256:5b4a9585ff77368514394822375b9dc1fb879f3c7946016b182906743d1be805，不能将上方首次发布摘要当成最新运行摘要。
2026-10-23 口试改为 A 上独立测试库自动化测试，原线上采集工具和报告已从当前仓库移除。
本次没有重启服务、删除线上 agent/历史文件或删除演示人员；旧数据不纳入新测试报告。
正常发布使用 stack.yml；将来发布会按该文件的普通 JVM 参数运行。发布前应核对实际目标镜像和数据备份。
