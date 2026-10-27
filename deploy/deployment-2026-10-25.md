# 2026-10-25 后端发布记录

> 历史发布记录。当前状态及保留备份以 [2026-10-27 发布记录](deployment-2026-10-27.md)为准；本文所列旧备份与报告已清理。

北京时间 2026-10-25 发布 V4；服务器日志采用 UTC。仅更新 rbac_backend，原型和 Vue 修改但未部署前端。

## 版本与证据

- 镜像：`swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend:role-scope-20261025`
- 固定摘要：`sha256:efa138abcc9f59b1c3169979a3562104f8d6bbeb33ab78a70992e95bdb780a19`
- JAR SHA256：`516efbe268cba1e5b8da6b3ff7f8ee07e6feb7f06e503222fc7f0991da894e8f`
- A 送测源码：`/root/rbac-role-scope-20261025`，覆盖率报告：`reports/jacoco/20261024T172153Z-3859`。
- 48 项测试及六项 100% 覆盖率，所有 19 个发布生产类与报告字节码一致，源码清单与本地文件一致。
- [本地报告入口](../reports/README.md)和[现场测试说明](现场运行JaCoCo.md)。

## 部署拓扑及保留内容

A 120.46.134.42 编译／推送 SWR；B 120.46.31.246 保存 rbac_mysql；C 114.116.254.25 管理 Swarm；D 120.46.86.147 运行后端，公网 8081。
API 基址为 http://120.46.86.147:8081/api，需先 POST /auth/login，再携带令牌请求 /me 等接口；直接浏览器打开基址或用 GET 登录不是有效验证。

更新前后 lab_mysql、lab_productdemoaop、lab_prometheus 均为 1/1，lab_node-exporter 为 3/3；未修改这些服务。rbac_mysql 保持原镜像和数据卷。新版后端 1/1，D 容器 healthy。
Excel 继续从 D `/opt/course-rbac/source` 只读挂载到 `/app/source`，RW=false，不进入镜像或 SWR。无在线 JaCoCo agent。

## 数据迁移与核对

先完成 B 业务库备份并 gzip 校验，再更新后端镜像。启动新增 role_management_permissions 和 user_role_scopes，旧 management_grants 保留存档但不参与鉴权。
一次性迁移 version=2/completed=1；184 名无人工角色修改记录的导入员工角色修正，有人工记录的分配不会自动覆盖（当前导入员工此类记录为 0）。

| 项目 | 更新前 | 更新后 |
| --- | ---: | ---: |
| users | 10007 | 10007 |
| user_roles | 10240 | 10301 |
| role_permissions | 3916 | 3916 |
| handovers | 2 | 2 |
| role_management_permissions | 不存在 | 33 |
| user_role_scopes | 不存在 | 301 |

范围分布 GLOBAL 1、COMPANY 64、DEPARTMENT 236。53 名部长、53 名副部长均有部门经理；三个项目管理岗位各 36 人，均有项目经理，去除错误公司领导／部门经理映射；普通员工基础角色保留。

公网验证公司过滤、精确部门、多范围、越权拒绝、管理员日志及同会话撤权。使用既有专用演示账号，验证结束恢复原角色和范围并退出，不修改真实员工做测试。

## 当前保留的最新备份

按用户要求清理旧 JaCoCo 和旧备份前，重新创建并校验了当前 V4 状态快照。仅保留以下最新数据库及服务配置备份，文件位于服务器私有目录，不提交 Git：

- B：`/root/rbac-backups/current-20261025/current.sql.gz`、`current.sha256`、`counts-current.txt`。完整 rbac 库逻辑备份，包含表结构、数据、例程及触发器，gzip 和 SHA256 已校验。
- C：`/root/rbac-backups/current-20261025/backend.json`、`mysql.json`、`images.env`、`services.txt`、`config.sha256`。后端／MySQL 服务规格及镜像配置，JSON 与镜像摘要已核对。

两台的 management-20261024、role-scope-20261025 旧部署前备份，以及旧 live-jacoco 配置快照已删除。上文“发布前备份”是当时的执行记录，不代表旧备份目前仍存在。当前备份用于恢复 V4 状态，不能用于回退 V3。

恢复需保留现有 Swarm secrets 和 D 的 Excel 挂载来源；以上是项目数据库及服务规格备份，不是整机或 Swarm 集群灾备。恢复前停止本项目写入、核对快照后新增数据，并按所保存的固定镜像摘要恢复；不要操作其他 Stack。MySQL DDL 不具备整体事务回滚能力。

## 报告清理

本地及 A 删除旧测试报告、旧源码打包副本；D 删除旧线上覆盖率采集输出。最新完整报告、ZIP、A 的 `/root/rbac-role-scope-20261025` 送测源码和测试工具镜像均保留，现场复现命令不变。三份 Excel、业务数据和其他项目服务／备份未删除。

本次使用独立送测目录发布；Git 克隆更新不等于自动发布。未来更新应先测试、备份，再构建固定摘要镜像并仅更新本项目服务。
