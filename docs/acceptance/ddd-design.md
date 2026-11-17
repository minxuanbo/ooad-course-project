# DDD设计交付与核对记录

日期2026-11-17。本记录只陈述合并、设计产物及实际检查，不是DDD运行验收。

## 1. 授权与基线

用户确认结构化无问题并授权合并：PR #32从codex/rbac-vue-implementation的a9c184d合入修复分支，合并提交4822114298f30b5bfc41cefd60282203de883516；随后既有PR #1从修复分支合入main，提交a4275be32391c200b7269171a6d3f19c1617a348。main与修复分支的Git tree均为85fbd71648d600b27a012fa502b5661e11387ea7，内容一致，未强推或丢弃历史。

本地main已快进，用户未跟踪的旧Word/Markdown仍保留。新建codex/ddd-design及独立工作树；本轮不合并设计、不编写DDD生产代码。结构化167后端/26前端/3运行器测试等成绩是上一阶段历史证据，本次文档检查不重报为新执行或DDD通过。

## 2. 参考与新版skill

老师示例仓库固定到[ca90f1cb7faa7d73b71b93a125460513f46b730e](https://gitee.com/mingqcn/course-project-mgt/tree/ca90f1cb7faa7d73b71b93a125460513f46b730e)。核对backend-design第17章及backend下DddCourseService、CourseRepository、DomainPage、JpaCourseRepositoryAdapter实际源码；采用分层、接口装配和模块图件组织，未移植课程逻辑、Boot4或外部身份。

用户提供压缩包只读解包在本地忽略目录，未把技能包/老师源码放进生产项目：

| 文件 | SHA-256 | 本轮采用与优先级 |
| --- | --- | --- |
| projectmgt-code-conventions（写代码）.zip | 530e0a20f6fa4753634b54ffaa736ab48c038837120b71bc63c3dbc10a540897 | 领域Repository只接收/返回领域模型、DomainPage、Gateway边界、final/record显式构造、注解独立换行列入后续设计约束。其生产TDD等规则在编码阶段执行，不为纯文档虚构RED |
| uml-class-sequence-diagrams（类图、顺序图）.zip | f85e0a1a8d817cdcf51796b447ceb74c56de2835c364c5a7a5b2eb49d97592bc | Service居中、关系箭头与role、执行条/MetaClass/loop/文字/几何及PNG核查；用户明确要先设计，所以DDD图以设计契约标目标，覆盖skill仅画已有代码的默认限制。该skill不用于包图/ER图 |

本RBAC本地口令、page/size、当前Boot3.5.6及只管理RBAC的用户确认继续优先，不照搬课程skill的外部统一身份或示例项目的其他业务。顺序图共同部分按用户要求全部省略，不因参考仓库存在完整流程图而照抄。

## 3. 交付清单及审核入口

- docs/ddd-design.md：聚合、领域仓储/查询、装配、事务/即时鉴权/K、映射、初始化及后续验收设计。
- docs/ddd-review-guide.md：用户逐项审核顺序；docs/design/README.md为完整图集及覆盖/省略矩阵。
- 新增29组SVG/PNG：结构化现状与DDD目标包图2组、DDD模块类图15组、六组两套成功差异顺序图12组。保留原结构化身份类图；原完整身份顺序图退出当前图集，Git历史可追溯。
- 设计注册表ddd-diagrams.json、生成清单ddd-generated-manifest.json及文档绘图脚本同源，维护一个图件来源。图件脚本不由产品/业务测试加载。
- 同步总览文档、需求状态、领域/后端、代码归类、复现、测试计划及AGENTS/协作说明，明确main已更新和DDD仅设计。

## 4. 本轮检查与限制

生成脚本已执行；自动检查框重叠、穿框、字体宽度、结构化方法锚点、共用接口方法/技术仓储来源、差异片段禁入共同步骤及参与者、每次调用独立执行条及循环框完整性。29组SVG均通过XML解析且PNG存在；327处现行/导航/验收Markdown本地链接和锚点无缺失。三个绘图脚本通过Node语法检查。SVG可编辑，注册表+脚本可重新生成，不手工维护平行图片。

已打开总联系表查看所有新图，并放大检查DDD包图、资源/首次授权/有效授权类图，以及人员循环、部门自调用/静态factory和DDD人员顺序图原图；修正过循环条件文字与外层执行条重叠的问题。类图关系方向、Service居中和签名留白，顺序图执行条、loop和MetaClass已实际目视核对。联系表留在忽略目录，不作为业务验收截图。

Git范围检查确认backend/src、frontend生产代码/测试及依赖、OpenAPI和首次SQL相对a4275be无变化；不存在DDD Java目录；原三份Excel及Chen ER图件摘要不变。没有把新增包和目标签名伪装成可运行实现。

按既有code-review skill，两名全新上下文审核者分别检查规范与需求，固定范围a4275be...HEAD。首轮Standards指出1项协作示例/base不一致，Spec指出1项UserCriteria工厂应用/领域输入歧义；59a44a8同步main工作流、显式领域查询参数及初始化种子边界，并重新生成/打开相关3张PNG。最终Standards硬规范0/启发式0，Spec0；此为设计复核，不是DDD执行验收。用户审核关口仍保留。

本轮不改backend/src、frontend生产代码/测试、OpenAPI、首次SQL或业务测试，不启动服务或数据库，不宣称完整回归/DDD验收完成。未构建镜像、未部署服务器、未同步Apifox。类图截取关键成员，不把省略的依赖当作不存在；成功差异片段未展开的异常处理仍须沿既有契约及设计正文实现。
