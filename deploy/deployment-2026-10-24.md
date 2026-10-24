# 细粒度管理权限后端发布记录

> 历史发布记录；该模型已由 [2026-10-25 版本](deployment-2026-10-25.md)替代，以下状态仅代表当时执行情况。该次备份和报告已按用户要求清理，当前仅保留 10 月 25 日最新完整版本及当前状态备份。

日期：2026-10-24。本次只部署后端。原型与 Vue 源码已修改和验证，前端未发布。所有改动基于 880ba22 留在本地工作区，**没有 Git commit 或 push**。

## 发布内容

- 业务 RBAC 与管理授权分离，新增 management_grants、management_migrations 两张表。
- 管理操作独立校验 GLOBAL／COMPANY／DEPARTMENT 范围，支持多范围与部门后代。
- 范围约束覆盖列表、筛选、统计、日志与写入；局部管理员不能操作自身或其他管理账号，也不能分配超出自身有效权限的业务角色。
- 共享角色权限配置与管理授权配置仅允许全平台对应权限。
- 组织新增／改名、最后平台授权管理员保护、管理写事务锁及离职同时撤销两类授权。
- 一次性初始化将既有启用系统管理员转换为全平台管理授权，后续启动不重授。

## 环境与版本

- A 构建／测试目录：`/root/rbac-management-20261024`，通过源码归档上传，未通过 Git 拉取本次改动。
- 公网 API：`http://120.46.86.147:8081/api`，各受保护接口仍需 Bearer 令牌。
- 更新服务：`rbac_backend`，运行于 D；`rbac_mysql` 与 `lab_*` 服务未更新。
- SWR 标签：`swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend:management-20261024`。
- 镜像 digest：`sha256:65e93d5759bd42839535add1e66a894530644b2960fac7d2dc25780c6d23a6ef`。
- JAR SHA256：`1638cd367bb51daf6e786819a0f6163be425a8d029bc4962cf9708241a070f6d`。
- 镜像 JAR 中全部 17 个生产类与本版 JaCoCo class-sha256.txt 一致。
- 后端健康检查通过，Swarm 1/1；原 lab 服务保持 1/1，node-exporter 保持 3/3。

Excel 继续使用 D `/opt/course-rbac/source` 到 `/app/source` 的只读挂载，不进入镜像或 SWR。
新镜像不含在线 JaCoCo agent；本次服务更新移除旧 JAVA_TOOL_OPTIONS 的 agent 参数，保留原内存限制。
C `/root/.config/course-rbac/images.env` 已更新后端镜像引用。

## 备份与数据核对

B 的备份：`/root/rbac-backups/management-20261024/before.sql.gz`，已通过 gzip 完整性校验，旁有 before.sha256。文件含业务数据，仅保存在服务器 root 私有目录，不下载到仓库。
C 保留 `/root/rbac-backups/management-20261024/backend-before.json` 和 images-before.env。

| 业务记录 | 升级前 | 升级后 |
| --- | ---: | ---: |
| users | 10007 | 10007 |
| user_roles | 10240 | 10240 |
| role_permissions | 3916 | 3916 |
| handovers | 2 | 2 |

新增管理授权最终为 11 条，初始化标记 version=1/completed=1。公网验证期间临时为此前专用演示接收账号授予公司查询／部门查看权限，验证后已恢复原授权并退出验证会话；产生的授权变更审计保留。

## 验证

- 45 项后端测试：失败／错误／跳过均为 0；隔离真实 MySQL，六项 JaCoCo 指标全部 100%。
- 前端／原型 9 项逻辑检查和 Vue 构建通过；没有发布前端。
- 公网管理员登录与管理读取成功。
- 专用演示账号公司范围列表正确，其他公司部门筛选为空，指定部门组织查询只有一项。
- 未授权角色、系统目录、日志、管理权限目录和写请求返回 403。
- 不能自行配置管理授权；管理员撤权后原会话立刻无法再读人员。
- 原演示账号管理授权已恢复，业务人员及业务角色未因验证改变。

## 回退说明

本次迁移为增加表与授权，不改写原业务关系。上一版镜像为 `sha256:5b4a9585ff77368514394822375b9dc1fb879f3c7946016b182906743d1be805`。
如确需回退，在 C 核对当前服务版本及保存规格后，可使用 `docker service rollback rbac_backend` 恢复上一份服务规格，并恢复 images-before.env。该命令仅适用于服务仍以本次更新为最近一次修改的情形。

旧版本依赖粗粒度系统管理员角色，不理解管理范围。回退会退回旧授权规则，需先评估访问边界；不能把回退旧代码当作保留新范围控制。新增表保留，不能直接 DROP 或重置 completed 标记，否则可能重授已撤回权限。
数据库备份用于人工核对和必要时受控恢复，不在正常发布或回退中自动覆盖业务库。

现场测试见 [现场运行 JaCoCo](现场运行JaCoCo.md)；此前部署情况保留于 [10 月 20 日记录](deployment-2026-10-20.md)。
