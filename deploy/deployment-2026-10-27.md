# 2026-10-27 后端发布与验收记录

本次仅更新 `rbac_backend`。Vue 与离线原型交付源码，未部署前端；其他 `lab_*` 服务配置逐项比对未变，MySQL 服务未替换或重启。

## 发布产物

- SWR：`swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend:design-20261027`
- 固定摘要：`sha256:249a69385b2838b797bdffad7ba8607264cb05f2fbee149ada7de30cd3c56493`
- JAR SHA256：`4ec842f05e1c51071d65361e4a517d1c0a123f66341b4d3e2320a6112aed54a3`
- 报告：`reports/jacoco/20261027T043315Z-21344/`，61 项测试、六项 100% 门槛通过。
- 72 个发布 class 文件与报告字节码逐一一致；JaCoCo 统计其中 69 个有可执行代码的类。D 的 JAR 与构建产物一致。
- Excel 未进入构建上下文、JAR 或 SWR，仍在 D `/opt/course-rbac/source` 只读挂载到 `/app/source`。

## 数据库执行

先完成数据库／服务配置备份并校验，再由 DBA 安装登录唯一性触发器。生产应用未获得 SUPER 权限，未开启生产 MySQL 的 log_bin_trust_function_creators。
预检查确认用户名／工号／手机号跨员工冲突数为 0。旧 management_grants 的 11 条 GLOBAL 存档全部由当前角色操作与范围承接，逐条核对通过后移除旧表。
启动迁移规范化角色范围、移除 parent_id、补齐日志列及索引，完成版本 3。DDL 不具备整体事务回滚能力；此前先备份的步骤不可省略。

| 项目 | 部署前 | 验收后 |
| --- | ---: | ---: |
| users | 10007 | 10007 |
| user_roles | 10301 | 10301 |
| role_permissions | 3916 | 3916 |
| handovers | 2 | 2 |
| role_management_permissions | 33 | 33 |
| 角色范围 | 301 | 301（management_scopes） |
| 登录标识登记 | 无 | 20008，核对无遗漏 |
| 物理表／外键 | — | 19 表／23 外键，与 ER 图一致 |

范围 GLOBAL 1、COMPANY 64、DEPARTMENT 236；部门范围中冗余 company_id 数为 0。旧 user_role_scopes、management_grants 与 departments.parent_id 均已移除。
既有日志不能补造当时没有采集的登录失败或未认证事件；历史记录的新增 event_type 默认值为 AUTHORIZATION，可结合原 permission_code 辨认旧准入记录，新请求使用明确事件类型。

## 线上验收

Swarm 后端 1/1，D 容器 healthy，公网 `http://120.46.86.147:8081/api` 提供统一 JSON API。
验证响应结构及请求号、401／404／405、登录失败和未认证留痕、过滤查询、游标不重复、不包含查询后新增事件、无效游标拒绝。
归档接口使用 `1970-01-01T00:00:00Z` 验证零命中请求；没有批量归档真实历史日志。有命中的批次、幂等及时间边界在独立测试库验证。
既有专用演示账号验证公司范围、精确部门、多范围、跨公司空结果、局部提权拒绝、日志限制、同会话撤权，结束恢复原角色与范围并退出会话。未修改真实员工或共享角色定义。

## 最新备份与恢复边界

验收后重新保存并核对当前状态，仅保留：

- B：`/root/rbac-backups/current-20261027/`，含 `current.sql.gz`、`current.sha256`、数量与结构核对摘要。
- C：`/root/rbac-backups/current-20261027/`，含后端／MySQL 服务配置、镜像环境文件、服务清单及 SHA256 清单。

旧 `current-20261025` 和本次部署前临时备份已在最新快照验证后按要求清理。最新快照可恢复当前版本，不能据此回退旧数据库结构；不要单独回滚到依赖旧表或旧字段的后端镜像。
备份为项目数据库逻辑备份及服务配置快照，仍依赖现有 Docker secrets、Excel 和镜像；不是全机灾备。本次执行 gzip／摘要／结构检查，未进行整机恢复演练。私有备份与凭据不提交 Git。

## 复现与提交材料

A 的 `/root/rbac-design-20261027` 保存本次实际送测源码；[现场说明](现场运行JaCoCo.md)提供可直接执行命令。构建工具镜像提供依赖，测试每次重新编译当前源码并创建独立 MySQL。
本地仅保留最新完整 [JaCoCo 报告及 ZIP](../reports/README.md)，包含执行数据、源码页面、XML/CSV、资源、测试摘要、执行元数据与源码／字节码清单。
