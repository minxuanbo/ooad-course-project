# 小组协作与文档维护

现行规范从[文档导航](docs/README.md)进入。需求、领域、数据库、前后端、HTTP、初始化及运行分别维护职责；实际验证和限制写入[验收证据](docs/testing-acceptance.md)，不以实施轮次不断追加规范章节。

## 1. 当前边界

两个系统DDD已发布，当前RBAC性能优化、330项双策略回归及加载实测见[本次证据](docs/history/verification/authorization-loading-performance-20261124.md)，18业务目录和部门角色的原库保留见[前次发布](docs/history/verification/access-extension-release-20261124.md)。源码合入不代表再次部署，未获新发布指示时不更新Stack或重启正式应用。课程前端保持老师原始源码；外部设计材料仅按明确授权同步，原件备份保留。老师原目录、原Excel、原库/卷及原始日志保留。T14/T15不自动关闭；Apifox仅在明确授权范围内写入，目标为本项目9042431，写后回读。

11月23日的JWT修复作为[历史发布](docs/history/verification/jwt-coverage-release-20261123.md)保留。后续文档提交不重复构建或发布同一业务源码，不延伸单次授权为未来变更的自动发布许可。

用户2026-11-24已授权步骤3/4及Apifox同步，随后追加授权PR49合入main、正式部署及恢复JaCoCo六项100%。发布前备份并验证保留原库的数据兼容处理；首次SQL和产品启动仍不自动升级现有库，未来任务仍须遵守各自授权边界。

## 2. 分支与工作树

先检查本地改动，再获取远端；新任务从origin/main建立codex/分支或独立工作树，存在时先核对，不覆盖或重置用户工作。

```bash
git status --short
git fetch origin
git worktree add -b codex/your-task ../ooad-your-task origin/main
```

开工读取AGENTS.md及任务依据，不凭Issue状态推断成果已进入基线，不自动领取下游任务。历史单次合入/部署授权不延伸为新任务的永久授权。

## 3. 编写与验证

- 业务规则更新需求正文；字段先改api/openapi.yaml并同源导出JSON，事务/范围语义写api-contract；同步受影响实现、页面和测试。
- 业务代码遵守指定20261117编码skill及用户确认的例外：本RBAC保留本地口令；管理分页page/size，外部目录沿老师page/pageSize；全新库首次SQL，不交付升级脚本。
- 两套业务实现独立，共享端口不依赖实现；DDD应用不依赖JPA/Entity/structured，领域仓储只用领域类型与JDK容器。事务在Service，写入锁后重鉴权。
- 中文Javadoc、注解独行、真实CloneFactory；不可变领域record/final按约显式构造。业务改动先RED再GREEN，真实MySQL验证事务，配置与文档用相应工具验证。
- UML与源码对应，按指定UML skill交付可编辑源、SVG/PNG并目检；类图并列双策略，顺序图只画差异，共同流程不重复。
- 当前设计按职责组织，稳定需求ID保留追溯；删除无用历史时核对独有价值、引用及Git可恢复性，有用来源/实测保留。
- 凭据、万人CSV、数据库、运行日志、构建产物和原始报告只在忽略目录；不提交用户原件或私有资料。

## 4. 交付

核对diff和验证结果，按指定审核skill进行规范/需求双轴审核，修复并复核。明确按文件暂存，提交推送任务分支，以main为目标创建草稿PR；标题/正文描述最终问题、变更、验证和限制。关联真实Issue用Refs，不自动关闭无关票。用户另有明确合入授权时按该任务范围执行，否则保留待审。
