# 小组协作

正式资料从[文档导航](docs/README.md)阅读：需求、领域/数据库、前后端、API与验收各司其职；API规范正文为docs/api-contract.md。历史整改文档保留追溯路径，不再另写一份相互冲突的现行规范。

当前只有结构化业务实现；同仓库DDD方案属于目标设计。2026-11-12用户安排本次先整理文档、再完成T13后停止。不得借文档整理移动生产代码、启用不存在的策略配置或启动后续任务。

共同整改基线为 refactor/rbac-core-backend；现有文档 PR 尚未合入 main。整改任务从远端共同基线建立分支，不从旧 main 开工。任务范围与验收见[完整任务清单](docs/rbac-rectification/RBAC整改任务清单.md)。

## 认领与开工

1. 打开目标 GitHub Issue，确认负责人，人工认领。核对全部 Blocked by，以及前置结论和所需产物是否进入共同基线；关闭 Issue 本身不替代证据。课程 T14/T15 另须小组确认启动。
2. 新组员先克隆仓库；已有仓库先检查本地改动，再获取远端更新：

```bash
git status --short
git fetch origin
```

3. 每票使用独立任务分支；多人或多对话写入时使用独立工作树。T03 示例：

```bash
git worktree add -b task/t03-org-import ../ooad-t03 origin/refactor/rbac-core-backend
```

分支或目录已存在时，先核对所属任务后复用，不覆盖。打开新工作树后阅读 AGENTS.md 和对应 Issue；当前可开工范围及 [T03 指令](docs/rbac-rectification/README.md#t03-开工指令)见整改入口；前置票需有评审与合并证据。

## 提交与评审

- 只提交本票需要的改动；改动共享授权、AOP、事务、初始化和 DTO 前，与相关票负责人协调。
- 原型/契约评审票交付具体产物、评审结论和未决项。正式接口实现先核对对应评审已完成；正式前端继续暂缓。
- 保留原始 Excel、数据库及本地资料。本地小组文档放在已忽略的 .local/group-documents/；临时材料、报告和构建产物不提交。
- 检查 git diff，按实际文件路径 git add，再提交和推送本票分支。不要批量添加未知文件。
- 创建草稿 PR，base 指向 refactor/rbac-core-backend，正文使用 Refs #实际编号，列交付行为、验收证据、未验证项及共享模块影响。
- 小组解决未决项、回写执行计划并评审合入共同基线后，由评审者更新/关闭 Issue。代理不自行合并、关闭票或领取下游。

GitHub 负责认领、评论及实时验收状态；任务清单保留批准的交付与依赖。调整范围或验收标准时，同步执行计划、任务清单和受影响 Issues，不新建重复票。
