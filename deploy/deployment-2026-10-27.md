# 2026-10-27 后端发布与验收记录

仅部署后端；前端更新源码与组织 PATCH 调用，不部署。其他 lab_* 服务配置逐项比对一致，rbac_mysql 未更新或重启。

## 发布产物

- SWR 标签：`swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-backend:fresh-20261027`
- 固定摘要：`sha256:ce54fee2bbcb69adae179dafa27866d56dfeea5555e3b822fbf127eab82741ed`
- JAR SHA256：`cb113dbaea4705d22deac1ef9ae1295938648b9e694fad551cde89d117ac75af`
- 报告：`reports/jacoco/20261027T094610Z-6831/`；59 项后端测试、13 个测试类，失败／错误／跳过为 0。
- 六项 JaCoCo 100%，71 个发布 class 文件与报告一致，68 个有可执行代码的类计入覆盖率；D 实际 JAR 摘要与构建产物一致。
- Excel 不在镜像中，D `/opt/course-rbac/source` 只读挂载到 `/app/source`。

## API 与初始化

公司、部门局部更新使用 PATCH，code/name 至少提供一个，省略字段保持原值；显式 null、空白、超长或其他字段拒绝。角色、权限与范围集合保持 PUT 整体替换。
空库创建最终结构，建立登录触发器和循环外键，然后在同一事务内导入 Excel、默认角色管理操作及员工范围。非空库不重复导入，也不恢复人工撤销的授权。
历史转换代码和工具已删除，管理写事务锁定固定的系统管理员角色行。

## 线上操作与核对

更新前校验数据库及服务配置快照；发布成功后移除不再使用的 management_migrations 状态表（3 条基础设施记录），没有删除业务记录。新库本身不创建该表。

| 项目 | 发布前 | 验收完成后 |
| --- | ---: | ---: |
| users | 10007 | 10007 |
| user_roles | 10301 | 10301 |
| role_permissions | 3916 | 3916 |
| handovers | 2 | 2 |
| role_management_permissions | 33 | 33 |
| management_scopes | 301 | 301 |
| login_identifiers | 20008 | 20008 |

最终现用表 18 张、物理外键 23 个，与 ER 图一致；登录标识无遗漏。生产未授予 SUPER 或修改 log_bin_trust_function_creators。

## 验收

Swarm 后端 1/1，D 容器 healthy。入口为 `http://120.46.86.147:8081/api`，端口采用 host 发布；C 管理节点不是 API 入口。未携带令牌访问 /api/me 返回 HTTP 401，请先登录。
公网验证公司／部门 PATCH（提交原名称，不改变真实资料）、省略字段保留、PUT 返回 405、非法字段返回 400；真实字段变更与失败回滚在隔离测试库验证。
同时验证统一响应、401/404/405、失败登录与匿名审计、过滤游标、无效游标拒绝、零命中归档。归档使用 1970 年截止时间，不归档真实历史。
专用演示账号验证公司／部门／多范围、跨范围拒绝、平台审计限制和同会话撤权，结束恢复原角色范围并退出测试会话。
前端 13 项逻辑测试和 Vue 构建通过；原型模拟 DOM 验证通过，未宣称真实浏览器端到端验收。

## 最新备份与复现

B 与 C 最新快照均位于 `/root/rbac-backups/current-fresh-20261027/`，分别保存数据库压缩备份和服务配置／镜像环境文件，已检查 gzip 与 SHA256；未做完整恢复演练。旧快照在最新快照校验后清理。
最新数据库不含历史状态表，不能单独回滚到依赖该表的旧应用；恢复需要匹配的数据库、镜像、现有 secrets 和 Excel。私有备份和凭据不提交 Git。
现场统一进入 A 的 `/root/ooad-course-project`，拉取 Gitee 并使用[现场命令](现场运行JaCoCo.md)。本次实际送测目录为 `/root/rbac-fresh-20261027`。
[最新完整报告与 ZIP](../reports/README.md)包含 HTML、源码页、资源、XML、CSV、exec、测试摘要、执行元数据和源码／字节码 SHA256 清单，仅保留这一版。
