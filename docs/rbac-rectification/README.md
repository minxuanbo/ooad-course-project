# RBAC 整改

以老师检查记录和补充检查图片为需求核心，结合用户确认，按原版执行计划实施。老师要求、用户补充与方案选择的归属见[复核说明](RBAC检查记录复核说明.md)。

| 文档 | 用途 |
| --- | --- |
| [原版执行计划与 Chen ER 图](RBAC整改计划与Chen-ER图.md) | 核心模型、授权规则、技术边界及验收依据；保留三段 Mermaid 源码 |
| [检查记录复核说明](RBAC检查记录复核说明.md) | 第一轮技术纪要摘录、补充图片、三份原始数据核对及需求归属 |
| [完整任务清单](RBAC整改任务清单.md) | 15 张任务的交付、验收、直接阻塞及共享模块 |
| [四组原型与 API 契约](RBAC原型与API契约.md) / [交互原型](prototype/index.html) | T04/T06/T08/T10 的实际交互、公共 API 目录、验收案例及未决项 |
| Chen ER：[PNG](RBAC-ER图-预览.png) / [SVG](RBAC-ER图.svg) | 标准符号预览与矢量原图 |
| [小组协作](../../CONTRIBUTING.md) | 认领、共同基线、任务工作树和草稿 PR |

GitHub Issues #2—#16 共 15 票：当前范围 13 票、课程后续 2 票，22 条直接依赖。认领和实时状态以 Issue 为准，开工前核对前置产物及其合入证据。

## 当前可以开始什么

- **T01/T02 已合入**：PR #17/#18，Issue #2/#3 已关闭。模型与规则评审见[计划 5.3](RBAC整改计划与Chen-ER图.md#53-t01-评审记录与未决项)，T02 的 52 项独立模型测试及打包证据见[计划 4.3](RBAC整改计划与Chen-ER图.md#43-t02-独立模型与验证)。这些记录属于代理技术评审。
- **T03 实现及代理评审通过**：独立分支`codex/t03-org-import`按用户确认的新项目规则导入110个目标部门和10001个账号，交付[PR #21](https://github.com/minxuanbo/ooad-course-project/pull/21)。112项测试报告失败/错误/跳过为0，Spring Boot打包通过；两路新上下文代理重试后Standards、Spec均0项发现。用户明确JaCoCo本次不作阻塞，原规则保留且不宣称全覆盖。规则、复现与证据见[计划4.4—4.5](RBAC整改计划与Chen-ER图.md#44-原始数据复核与导入前置)，合入/关票实时状态见Issue #4及PR。
- **T04 补充准备已确认**：2026-11-07 四组原型/API 契约随 [PR #19](https://github.com/minxuanbo/ooad-course-project/pull/19) 合入；2026-11-08 双入口登录原型/契约经技术复核后，用户要求“推送合并完结”。[补充证据](RBAC原型与API契约.md#login-review)及[Issue #5](https://github.com/minxuanbo/ooad-course-project/issues/5)记录本次合入与完成状态。
- **T05已取得前置基线**：包含T03 PR #21和T04修订的70c3bef，独立分支`codex/t05-management-loop`实现及两轴代理复核通过，完整回归128项失败/错误/跳过均0，打包/入口验证通过；初审2项修复后剩余0项。JaCoCo本票不阻塞，合入及关票以GitHub为准；五角色规则、RBAC/业务后台边界及复现见[计划4.6](RBAC整改计划与Chen-ER图.md#46-t05-五角色首次授权与实际管理链路)。T07/T09/T11 分别依赖 T05 及对应 T06/T08/T10 契约。开工时获取最新共同基线。
- **T07实现及代理复核通过**：用户指示继续，本票前置T05 [PR #22](https://github.com/minxuanbo/ooad-course-project/pull/22)已合入、Issue #6已完成，T06契约亦已合入。从`8afac13`建立独立分支`codex/t07-resource-mapping`，实现7项必要资源操作，范围/事务及复现见[计划4.7](RBAC整改计划与Chen-ER图.md#47-t07-本系统资源目录与映射维护)。最终完整回归144项失败/错误/跳过均0，打包/实际入口验证通过；Spec初审2项P2修复后两轴剩余均0。合入/关票以Issue #8及PR为准，JaCoCo按用户要求本次不作阻塞。
- **T09已合入**：工作树`D:\Codex\outputs\ooad-t09`，分支`codex/t09-role-permissions-local`，基线为T07合并提交`322e334`。五项角色接口、即时授权变化、回滚、与T07共用锁的并发保护已验证；完整回归166项失败/错误/跳过均0，打包/JAR实际HTTP通过。证据和复现见[计划4.8](RBAC整改计划与Chen-ER图.md#t09-validation)。已随[PR #24](https://github.com/minxuanbo/ooad-course-project/pull/24)合入共同基线`4e2b2a1`，实时状态见[Issue #10](https://github.com/minxuanbo/ooad-course-project/issues/10)。
- **T11 PR复核及补修通过**：组员提交[PR #25](https://github.com/minxuanbo/ooad-course-project/pull/25)，按用户要求以两个skill审核并追加`cce0156`修复。原PR 11项补跑通过，新增5类行为回归复现后修复；最终190项全量失败/错误/跳过均0，打包/真实HTTP通过，Standards与Spec复核剩余各0。实现、修复及复现见[计划4.9](RBAC整改计划与Chen-ER图.md#49-t11-人员维护与-pr-25-复核)，PR仍待小组合入，不把代理验证当作关票。
- **T14/T15 待小组确认启动**：课程受控场景与双系统授权演示仍待落实。

## 四组原型与契约的查看方法

下载本分支完整目录，用 Edge/Chrome 打开 [prototype/index.html](prototype/index.html)，无需构建或后端。账号、口令和数据均为内存模拟，登录页列有使用方法；请在浏览器运行 HTML 查看交互。

契约按 [T04](RBAC原型与API契约.md#t04)、[T06](RBAC原型与API契约.md#t06)、[T08](RBAC原型与API契约.md#t08)、[T10](RBAC原型与API契约.md#t10) 分节，另有[公共约定](RBAC原型与API契约.md#common)、[验收案例](RBAC原型与API契约.md#acceptance)、[评审记录](RBAC原型与API契约.md#review)及[已确认选择与缺项](RBAC原型与API契约.md#decisions)。原型确认覆盖交互与契约准备；正式界面和真实后端按各自任务验收。

T04 登录页可切换“登录边界场景（仅模拟）”，复核无手机号、同值、跨用户歧义和前导零；成功或失败后展开“本次登录响应（模拟）”。步骤见[契约 3.3](RBAC原型与API契约.md#login-scenarios)，证据见[8.1](RBAC原型与API契约.md#login-review)。

## T03 开工指令

按[协作步骤](../../CONTRIBUTING.md)人工认领，获取包含 PR #18 的最新共同基线，核对目标分支和已有工作后使用：

```text
执行 https://github.com/minxuanbo/ooad-course-project/issues/4（T03），只做组织与人员导入闭环。
先读 AGENTS.md、CONTRIBUTING.md、整改入口、原版计划 2.3—2.5、
3.3—3.5、4.1—4.4 和 5.3—5.4、复核说明及任务清单 T03，确认 T02 合入证据及最新口径。
从最新 origin/refactor/rbac-core-backend 建立独立任务分支/工作树。
复用 Excel 读取和 T02 JPA 模型，在独立整改库核对共同根、组织无环连通、
原始10000人、111个组织来源映射至110个目标部门，保留来源映射；读取不得被A1范围标记截断。
用户名就是工号：Excel“用户名”直接映射username，“电话号码”映射phone，不另生成工号。
先删除T02重复employee_no/employeeNo及构造参数/约束/夹具，验证单工号模型、
工号和非空手机号各自唯一及跨用户碰撞拒绝，保留原六列、SHA-256、实际行号和目标ID映射。
按2026-11-10用户确认的新项目方案，不迁移旧ID、哈希、状态或授权。
将公司领导节点合并到总公司，其10名人员归入根；其他组织与人员归属不变。
单独创建默认名admin的维护账号，根直属共11人，总账号10001；不用管理员字段或独立实体。
员工与管理员初始口令分别从必填部署配置读取，逐账号独立BCrypt盐，员工全部启用但不授角色。
T05负责经确认规则/工号清单的首次批量授权，不要求逐人手工授予，T11负责日常维护。
未知归属、重复标识和结构异常拒绝，不按岗位赋权，不导入旧业务目录。
验证重复导入、失败回滚或安全重试，不覆盖已调整授权；保留原库、原始 Excel 与本地资料。
交付可复现的 MySQL/JPA 导入结果和小样本核对，不新增正式 Controller 或页面。
提交本票必要文件并推送，创建base=refactor/rbac-core-backend的草稿PR，正文Refs #4，
按用户授权完成代理双轴评审后维护状态；不把数据导入作为正式接口或授权验收。
```

## 范围和资料维护

账号按[计划 1.2](RBAC整改计划与Chen-ER图.md#login)：`username` 即工号，`phone` 定位同一用户，共用口令与授权；T03 删除重复 `employee_no`，T04 复核，T05 实现，T12 清理残留，T13 验收。有效账号还须具有本系统有效管理操作权限才可登录；沿用现有认证支撑，由后端逐次检查具体权限和范围。

最小鉴权日志复用旧记录与查询并迁 JPA，供有日志权限的全局维护者查看；部门管理员拒绝。归档、导出和完整审计移除，日志作为运行支撑留在核心 ER 之外。人员与组织查询限本部门及下级，本轮组织功能为部门树查询，完整组织 CRUD 留在范围之外。

补充图片要求 REST、读写声明式事务、CloneFactory 及跨部门调动。CloneFactory按用户指定老师项目核实为0.0.1-SNAPSHOT，来源与构建见计划4.6；针对用户的权限通过角色分配实现。完整范围见[原版计划](RBAC整改计划与Chen-ER图.md#11-补充依据与本次保持的边界)。

正式接口先完成对应原型/API 契约评审，正式前端暂缓；课程 T14/T15 需交付实际受控场景与授权结果。

[旧资料](../legacy/README.md)供差异参考，三份原始 Excel 保留在仓库根目录。本目录维护执行计划、任务清单及必要契约；讨论副本、单票镜像、分享压缩包、发布脚本和生成报告留在 Git 之外。业务实现、数据库、HTTP 和并发验证按票记录实际证据。
