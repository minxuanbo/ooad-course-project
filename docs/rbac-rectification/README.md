# RBAC 整改

以检查记录及用户补充确定需求，以原版执行计划指导实施。T01 模型、规则与迁移边界已通过用户授权的代理评审；业务实现、原型/API 契约及运行验收仍须按票完成。

| 文档 | 用途 |
| --- | --- |
| [原版执行计划与 Chen ER 图](RBAC整改计划与Chen-ER图.md) | 核心模型、授权规则、技术边界及验收依据；保留三段 Mermaid 源码 |
| [检查记录复核说明](RBAC检查记录复核说明.md) | 第一轮技术纪要原文摘录、需求归属、旧错误及待办 |
| [完整任务清单](RBAC整改任务清单.md) | 15 张任务的交付、验收、直接阻塞及共享模块 |
| Chen ER：[PNG](RBAC-ER图-预览.png) / [SVG](RBAC-ER图.svg) | 标准符号预览与矢量原图 |
| [小组协作](../../CONTRIBUTING.md) | 认领、共同基线、任务工作树和草稿 PR |

GitHub Issues #2—#16 是认领、讨论与状态入口，共 22 条原生阻塞关系；当前范围 13 张、课程后续 2 张。执行前读取实际 Issue 状态、评论和评审证据。

## 当前可以开始什么

[T01 / Issue #2](https://github.com/minxuanbo/ooad-course-project/issues/2) 的核查和代理评审已完成，证据直接保存在原版计划的 [2.5 模型映射](RBAC整改计划与Chen-ER图.md#25-t01-模型核对与-t02-映射基线)、[3.3 管理员保护](RBAC整改计划与Chen-ER图.md#33-t01-有效全局管理员与保护边界)、[4.1—4.2 替换及隔离边界](RBAC整改计划与Chen-ER图.md#41-t01-工程复用替换与删除对照)和 [5.3 评审记录](RBAC整改计划与Chen-ER图.md#53-t01-评审记录与未决项)。评审由主 agent 按用户明确授权完成，不代表老师或真人组员验收。

T01 产物合入共同基线并更新平台状态后，[T02 / Issue #3](https://github.com/minxuanbo/ooad-course-project/issues/3) 与 T04/T06/T08/T10 可分别开展。T02 仅建模与独立 MySQL/JPA 验证，原型评审不必等待它；T14/T15 仍须另行确认后续范围。

## T02 开工指令

按[协作步骤](../../CONTRIBUTING.md)核对 T01 的合并证据，从最新 origin/refactor/rbac-core-backend 建立 T02 独立工作树后发送：

```text
执行 https://github.com/minxuanbo/ooad-course-project/issues/3（T02）。
先读 AGENTS.md、CONTRIBUTING.md、整改入口、原版计划 2.3—2.5、
4.2 和 5.3、复核说明及任务清单 T02，确认 T01 评审和合并证据。
复用原 Maven 工程，在独立整改库建立六实体和三组关联，加入所需 JPA；
验证外键、联合唯一性、同系统与树约束、多角色/无角色及共享 API。
隔离旧 SQL、DataInitializer、DatabaseConstraints 和原数据库配置；
保留旧调用、原始 Excel 及本地资料，不新增正式 Controller 或页面。
记录可复现的 MySQL/JPA 结果、共存边界及未验证项。
提交本票必要文件并推送，创建 base=refactor/rbac-core-backend 的草稿 PR，
正文 Refs #3，不自行合并、关闭票或启动其他下游任务。
```

## 范围和资料维护

有效账号通过既有身份校验可登录；无管理权限显示提示；管理请求按具体权限和部门范围校验。不增加管理员属性、独立登录资格或新的认证功能。

正式接口先完成对应原型/API 契约评审；前端正式开发暂缓，原型仍要做。课程受控场景由后续两票落实，不能用系统字段预留代替完成。

[旧实现资料](../legacy/README.md)仅供差异核对；旧原型不是整改原型，也未因此获得界面验收。三份原始 Excel 保留在仓库根目录。

只维护本目录的执行计划与任务清单，不提交单票镜像、讨论副本、分享压缩包或发布脚本。业务代码、数据库、HTTP 和并发验证以相应任务的实际证据为准。
