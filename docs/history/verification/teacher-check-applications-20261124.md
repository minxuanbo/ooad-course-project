# 课堂检查与应用管理验证

2026-11-24，任务分支`codex/teacher-check-application-management`，从main建立并保留PR58两项按钮置灰修改。本文记录本轮源码及离线验证，不表示正式服务器已更新。本轮不合入main、不部署、不重启服务、不修改课程前端、正式数据库或原始日志。

## 1. 对照老师检查项

| 检查项 | 实际结论与处理 | 可核对位置 |
| --- | --- | --- |
| 应用管理菜单 | 原来仅能枚举已登记目录，缺运行期新增/移除界面。本轮新增SPA应用管理，数据库分页、新增、名称/接入状态修改、逻辑移除与恢复；无代码白名单 | R34、ApplicationController、Applications.vue |
| 正常异常与边界 | 新增应用生命周期、权限拒绝、编码冲突、字段长度、分页边界、维护者保护、存储故障直接用例；每条稳定需求放对应方法链接，复用真实共享契约，不补空r02目录 | requirements.md逐条链接、r34契约 |
| 本人权限排除停用项 | 原有有效链计算已过滤；补直接读取本人权限响应，逐个断开权限/API/角色链，验证OR替代、未授父项及父停用不级联。系统停用使RBAC准入拒绝 | R09_ExcludeInactivePermissionChains |
| 登录与单菜单 | 原准入要求任意管理API。本轮独立LOGIN：只授登录可进入空工作台；授单菜单显式读取API后才有该页能力；撤LOGIN后旧JWT管理本人查询也403 | R08_LoginOnlyAndSingleMenu、RbacCatalog |
| MENU/BUTTON与API | Permission及ApiResource仍分表、多对多显式映射。七个初始MENU明确映射本页读取API，各按钮单独映射真实操作；父子不继承，不隐含其他写按钮 | RbacCatalog.menuRoutes、R06目录检查 |
| 中文名称 | 补真实初始化响应逐项核对30个中文操作名，以及未开放名称编辑时400且不改库；编码、ID及关系不因中文展示改变 | R30_ChineseCatalogNamesAndImmutableMetadata |
| 角色关联批删重建 | 未发现。两策略已有Set差量保存；锁后读取现有集合，仅新增/删除逐项重验，锁定项可保留 | R11/R13/R27共享契约、User行为与JPA Adapter |
| 当前状态及两策略 | 逐请求读库、无JWT权限快照、共根锁后重鉴权保留。应用分页两策略均同一RR快照；新增应用预检在MVC解析前，数据库并发错误统一409，PATCH混合显式null先RED后拒绝且不改库 | Application契约、ManagementPreflight、JpaWriteErrors |
| JPA写法 | 生产链无旧JDBC业务查询。原外部身份目录技术Gateway直接使用EntityManager，本轮改为Spring Data专用投影及计数，领域仓储不泄漏技术类型 | JpaIdentityDirectoryGateway、Department/UserJpaRepository |
| 选择器装配 | 互斥策略没有重复业务执行。StructuredImportService无组件注解，必须保留显式Import，不能因外层扫描就误删 | R19BackendStrategyTest、R07完整契约 |
| 分层、类图、差异图 | 应用生命周期类图同步，新增一组真实差异；structured部门删除图补所属角色占用。84组图=2包+32类+50差异序列；41操作和两类离线入口可追溯 | backend-design第17章、ddd-diagrams.json |
| SPA与课程边界 | 正式Vue只有index.html入口，Router切换同一应用；离线原型只是旧交互参考，明确列出与新源码差异。课程对象权限和原始Vue行为未改 | frontend/src/main.js、prototype/README.md、course-integration.md |

## 2. 测试及覆盖率

隔离MySQL8.4在本轮私有3355实例建立六个测试库，未用正式业务库或旧联调库。新应用、独立LOGIN、中文名称及拒绝顺序采用真实嵌入式HTTP；持久化故障另按端口注入，不将其称为真实数据库故障实验。

显式null拒绝修复后，DDD与structured均重新clean完整运行344项。两策略失败、错误、跳过均0；360份生产class逐文件SHA-256一致后合并。原Maven六项MISSEDCOUNT=0检查通过，未排除业务类或降低门槛。

| 指标 | covered | missed |
| --- | ---: | ---: |
| INSTRUCTION | 25173 | 0 |
| BRANCH | 2000 | 0 |
| LINE | 5013 | 0 |
| COMPLEXITY | 2099 | 0 |
| METHOD | 1092 | 0 |
| CLASS | 276 | 0 |

[最终报告](../../reports/jacoco/final-rbac/README.md)只维护当前版本，原报告留在Git历史；100%是本轮隔离执行覆盖，不代表业务无缺陷、线上流量覆盖或性能结论。前端54项单元与17项构建渲染通过；浏览器为真实Vue配可控HTTP，不能冒充真实业务后端或公网验收。

真实CloneFactory0.0.2检查：63个有效生成重载、0空实现；224处可变setter赋值，214组可变同名同类型字段无遗漏；5个不可变构造映射、21参数完整且不读取null目标。业务工厂、跨层/多来源/派生转换仍明确组装。注解独行扫描排除注解类型声明及字符串后无违规。

## 3. 外部契约与材料

Apifox9042431主分支导入新增4操作及4模型，失败0；再基于完整模块读取更新版本及说明，保留环境设置。最终回读为1.7.0、41操作、33路径、77模型，规范化比较语义差异0；原17项K不变，完整维护者另要求有效LOGIN。已纠正旧x-initial-permission-code中仍指向分组授权的元数据，实际鉴权继续允许任一有效Permission映射覆盖。

桌面详设PDF、同版PPT、Markdown讲稿、三份教师问答和提交ZIP按用户要求同步。详设133页；PPT25页主讲含封面、目录及致谢，附录19页共44页，新增应用管理放技术附录。PPT经原生渲染核对，保留原目录图的自定义裁切；生成工具导入曾丢失该图形几何，交付前已恢复。Word与演示评审PDF仅为忽略目录中间件，不交付。ZIP提供PDF、Markdown、可编辑图源、OpenAPI及首次SQL，不含Word、完整工程、凭据或数据库。

操作演练见[自定义角色验证方案](../../role-verification-guide.md)。方案没有在正式库创建账号、角色或测试数据。新增人员仍属人力来源，本轮不增加注册/创建人员功能。

## 4. 未验证和发布边界

新应用管理、LOGIN及菜单映射未做公网发布验收；本轮未重新测试课程业务、监控或性能，不沿用PR56/前轮结果证明新版本通过。现有初始化回执不会自动补授新权限，晚上若授权发布，须先备份现有数据、明确补录目录与原授权保留规则并验证，再更新两个RBAC镜像。部署本轮不是通过重跑初始化来重置用户授权。
