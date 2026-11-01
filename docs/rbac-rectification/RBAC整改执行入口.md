# RBAC 整改执行入口

> 2026-11-02｜15 张 GitHub Issues 已发布，22 条原生依赖已核对；任务均未认领，业务尚未整改。

原版执行计划 v0.6 是执行依据。15 张票的粒度与依赖已经用户确认，发布并不表示各票的模型、原型、契约或运行验收已经完成。当前只有 T01/#2 没有技术阻塞；T14/T15 继续属于课程后续范围。

## 先读什么

1. 阅读[原版执行计划 v0.6](RBAC整改计划与Chen-ER图.md)和[检查记录复核说明](RBAC检查记录复核说明.md)。检查记录及用户补充决定需求范围；讨论版用于讨论，旧材料用于定位差异。
2. 阅读[完整任务清单](RBAC整改任务清单.md)，再读取用户指定的单票和对应 GitHub Issue。GitHub 是认领、状态和依赖的跟踪入口；本地 Markdown 是阅读及执行镜像。
3. 检查记录 DOCX 原件未随本轮指定文档进入仓库。可先按复核说明开展核对；涉及原文歧义时，由组内补充原件并记为待核对，不能拿旧需求代替老师要求，也不能冒充已经复核原件。

## 基线与多人协作

- 共同整改基线为 refactor/rbac-core-backend，PR #1 尚未合并至 main。当前整改优先遵守本页，CONTRIBUTING 中从 main 开始的步骤属于通用示例。
- 开工先检查 Git 状态、获取远端更新，再从最新整改基线建立任务分支。每个执行对话使用独立分支；多个对话同时写入时使用独立工作树。
- 组员人工认领，代理只执行用户明确指定的票；本轮未创建实现分支、工作树或执行对话，也未设置 ready-for-agent。
- 先核对本票全部 Blocked by 及前置评审证据。被阻塞的正式接口票不得跳过原型/API 契约评审；Issue 被关闭本身也不替代评审及产物进入共同基线的证据。
- 保护原始 Excel、原库和已有未跟踪的小组文档。按本次实际修改文件明确暂存，保持无关改动在提交之外。
- 改动共享模块前先和相关票负责人协调。T07/T09/T11 满足各自前置后可以并行，但有效授权、AOP、初始化、事务与保护规则保持一致。
- 每票提交关联该 Issue 的草稿 PR，base 指向共同整改分支。合入非默认分支时，由评审者按验收证据维护完成状态；代理不自行合并或自动领取下游。

## 范围规则

- 有效账号通过既有身份校验即可登录；无管理权限显示提示；管理请求按具体权限和部门范围校验。不增加管理员属性、独立登录资格或新的认证功能。
- 保留可复用工程骨架，逐块替换错误模型和 JDBC 实现；旧功能清理覆盖调用、初始化、测试、配置和文档。
- 正式前端暂缓，原型仍须完成。原型演示或后端通过不代表正式界面已经验收。
- 课程受控场景由 T14/T15 单独落实，须小组另行确认启动；系统字段预留不能替代真实课程操作和双系统授权演示。

## 单票、平台编号与直接依赖

| 本地单票 | GitHub Issue | 交付主题 | Blocked by | 初始启动状态 |
| --- | --- | --- | --- | --- |
| [T01](../../.scratch/rbac-core/issues/01-t01-model-review.md) | [#2](https://github.com/minxuanbo/ooad-course-project/issues/2) | 确认核心模型、授权规则与逐块替换边界 | 无 | 可人工认领 |
| [T02](../../.scratch/rbac-core/issues/02-t02-jpa-core.md) | [#3](https://github.com/minxuanbo/ooad-course-project/issues/3) | 在原工程建立可独立验证的 JPA 核心模型 | [T01/#2](https://github.com/minxuanbo/ooad-course-project/issues/2) | 等待前置 |
| [T03](../../.scratch/rbac-core/issues/03-t03-organization-import.md) | [#4](https://github.com/minxuanbo/ooad-course-project/issues/4) | 把原始组织与人员导入层级部门并核对 | [T02/#3](https://github.com/minxuanbo/ooad-course-project/issues/3) | 等待前置 |
| [T04](../../.scratch/rbac-core/issues/04-t04-identity-query-review.md) | [#5](https://github.com/minxuanbo/ooad-course-project/issues/5) | 评审登录后状态与人员检索的原型/API 契约 | [T01/#2](https://github.com/minxuanbo/ooad-course-project/issues/2) | 等待前置 |
| [T05](../../.scratch/rbac-core/issues/05-t05-identity-query-authorization.md) | [#6](https://github.com/minxuanbo/ooad-course-project/issues/6) | 跑通有效账号与分部门人员查询的真实授权闭环 | [T03/#4](https://github.com/minxuanbo/ooad-course-project/issues/4)、[T04/#5](https://github.com/minxuanbo/ooad-course-project/issues/5) | 等待前置 |
| [T06](../../.scratch/rbac-core/issues/06-t06-resource-api-review.md) | [#7](https://github.com/minxuanbo/ooad-course-project/issues/7) | 评审本权限系统资源目录与 API 映射契约 | [T01/#2](https://github.com/minxuanbo/ooad-course-project/issues/2) | 等待前置 |
| [T07](../../.scratch/rbac-core/issues/07-t07-resource-api-mapping.md) | [#8](https://github.com/minxuanbo/ooad-course-project/issues/8) | 让本权限系统资源目录与 API 映射真实生效 | [T05/#6](https://github.com/minxuanbo/ooad-course-project/issues/6)、[T06/#7](https://github.com/minxuanbo/ooad-course-project/issues/7) | 等待前置 |
| [T08](../../.scratch/rbac-core/issues/08-t08-role-permission-review.md) | [#9](https://github.com/minxuanbo/ooad-course-project/issues/9) | 评审角色定义与角色权限配置契约 | [T01/#2](https://github.com/minxuanbo/ooad-course-project/issues/2) | 等待前置 |
| [T09](../../.scratch/rbac-core/issues/09-t09-role-permission-change.md) | [#10](https://github.com/minxuanbo/ooad-course-project/issues/10) | 让角色权限配置改变真实 API 访问结果 | [T05/#6](https://github.com/minxuanbo/ooad-course-project/issues/6)、[T08/#9](https://github.com/minxuanbo/ooad-course-project/issues/9) | 等待前置 |
| [T10](../../.scratch/rbac-core/issues/10-t10-user-role-review.md) | [#11](https://github.com/minxuanbo/ooad-course-project/issues/11) | 评审人员角色分配与受限人员变更契约 | [T01/#2](https://github.com/minxuanbo/ooad-course-project/issues/2) | 等待前置 |
| [T11](../../.scratch/rbac-core/issues/11-t11-scoped-user-roles.md) | [#12](https://github.com/minxuanbo/ooad-course-project/issues/12) | 在部门范围内分配人员角色并拒绝越权 | [T05/#6](https://github.com/minxuanbo/ooad-course-project/issues/6)、[T10/#11](https://github.com/minxuanbo/ooad-course-project/issues/11) | 等待前置 |
| [T12](../../.scratch/rbac-core/issues/12-t12-legacy-cleanup.md) | [#13](https://github.com/minxuanbo/ooad-course-project/issues/13) | 收掉旧模型与旧功能并切换到整改数据链路 | [T07/#8](https://github.com/minxuanbo/ooad-course-project/issues/8)、[T09/#10](https://github.com/minxuanbo/ooad-course-project/issues/10)、[T11/#12](https://github.com/minxuanbo/ooad-course-project/issues/12) | 等待前置 |
| [T13](../../.scratch/rbac-core/issues/13-t13-backend-acceptance.md) | [#14](https://github.com/minxuanbo/ooad-course-project/issues/14) | 验收当前 RBAC 后端并提交可复核证据 | [T12/#13](https://github.com/minxuanbo/ooad-course-project/issues/13) | 等待前置 |
| [T14](../../.scratch/rbac-core/issues/14-t14-course-scenario-review.md) | [#15](https://github.com/minxuanbo/ooad-course-project/issues/15) | 确认课程系统受控场景及原型/API 契约 | [T01/#2](https://github.com/minxuanbo/ooad-course-project/issues/2) | 后续暂缓 |
| [T15](../../.scratch/rbac-core/issues/15-t15-course-authorization.md) | [#16](https://github.com/minxuanbo/ooad-course-project/issues/16) | 接入已评审课程操作并演示双系统授权 | [T09/#10](https://github.com/minxuanbo/ooad-course-project/issues/10)、[T11/#12](https://github.com/minxuanbo/ooad-course-project/issues/12)、[T14/#15](https://github.com/minxuanbo/ooad-course-project/issues/15) | 后续暂缓 |

标签 rbac-rectification 汇总全部任务，rbac-current 对应 13 张当前范围，rbac-later 对应 2 张课程后续任务。状态表为发布时快照；执行前读取 GitHub 实际状态、评论、评审证据及最新基线。

## T01 怎么开始

1. 由实际负责人打开 [T01 / Issue #2](https://github.com/minxuanbo/ooad-course-project/issues/2)，确认没有其他人正在负责，再由组员设置 Assignee 或留言说明认领及协作人。当前 T01 没有 Blocked by。
2. 在自己克隆的仓库根目录检查本地改动并获取远端更新。下面命令新建独立工作树，不搬动原目录的未跟踪资料：

```bash
git status --short
git fetch origin
git worktree add -b task/t01-model-review ../ooad-t01 origin/refactor/rbac-core-backend
```

若该分支或目录已存在，先确认负责人及所属任务后复用，不覆盖已有工作。还没有仓库的组员先按仓库地址克隆，再执行以上步骤。

3. 在 VS Code 或 Codex 中打开新工作树 ooad-t01，建立自己的执行对话。将下面指令发给该对话：

```text
请在当前工作树执行 https://github.com/minxuanbo/ooad-course-project/issues/2（T01）。
先读 AGENTS.md、docs/rbac-rectification/RBAC整改执行入口.md、原版执行计划 v0.6、检查记录复核说明和 .scratch/rbac-core/issues/01-t01-model-review.md。
先检查 Git 状态和当前工作树基线，保护未跟踪小组资料；确认当前分支基于最新 refactor/rbac-core-backend。
只完成 T01 的模型、授权规则与逐块替换边界审视。按验收标准核对源码，交付实体关系及规则结论、复用/替换/删除对照、必要身份衔接、调用/初始化/测试/配置/文档影响，以及未决项和受影响下游票。
区分老师明确要求、用户补充和方案建议。原件未提供或规则需小组决定时如实标记，不冒充已经评审通过。
本票不写正式接口，不自动启动 T02 或其他下游。完成后提交仅含本票的文档变更及验证边界，推送本票分支，创建 base=refactor/rbac-core-backend 的草稿 PR，正文写 Refs #2；不要自行合并或关闭 Issue。
```

4. T01 的交付是可评审材料：模型与授权规则核对、工程复用及替换/删除对照、各项证据出处、原版需回写的差异和未决项。可集中放在整改目录的 reviews/ 中。源码静态核对不等于数据库、HTTP 或并发验收。
5. 负责人提交文档 PR，附 Issue #2 的验收逐项对照和未验证内容；由小组确认未决项、回写原版并评审。结论及所需产物合入共同基线后，由评审者关闭/更新 #2。仅在这些条件满足后，T02/T04/T06/T08/T10 才能分别启动；课程 T14 仍保持后续。

其他票按同样方式执行，将 Issue 编号、单票文件、分支及产物替换为目标票，并逐项核对其实际 Blocked by。

## 发布与验证记录

- GitHub Issues #2—#16 已创建并逐票核对，保留 83 条未勾选验收标准；22 条原生阻塞关系与已确认拆分一致，没有增加传递依赖。
- 所有票保持未分配；T14/T15 标为后续，没有自动领取或启动实现。旧的 T01 建票表单无需再次提交。
- 任务清单和单票镜像已回填真实链接。GitHub Projects 为可选看板，当前使用 Issues、范围标签及原生依赖跟踪。
- 本轮仅更新文档和任务跟踪，未运行 Java/MySQL/HTTP 或并发验收，未修改业务代码、部署或合并 PR。
