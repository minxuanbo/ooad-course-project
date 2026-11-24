# 来源与验证证据

此目录保留有独立追溯价值的材料，均只对应各自日期、提交和环境。现行规则从[文档导航](../README.md)进入，当前执行及发布状态从[验收](../testing-acceptance.md)进入。

| 材料 | 保留用途 |
| --- | --- |
| [应用管理与按钮置灰发布](verification/application-management-release-20261124.md) | 精确25fdc21源码、旧库兼容及双份备份、115项公网HTTP与11组浏览器；记录接入服务依赖健康自动重启及PR59未合main边界 |
| [按钮统一置灰](verification/disabled-permission-actions-20261124.md) | 逐按钮禁用、只读集合、保护目标与撤权状态的前端行为验证；保留当时暂不部署的边界，后来与应用管理同批上线 |
| [原型登录与新版构造映射](verification/login-prototype-constructor-mapping-20261124.md) | 本轮用户追加新版映射规范、331项双策略及最终六项100%、Apifox及外部材料一致性与独立发布结果；旧报告副本已清理，历史事实不改写 |
| [首次登录渲染与匿名访问](verification/login-render-and-anonymous-audit-20261124.md) | 延迟Teleport修复、54项单元/4项构建渲染及当时正式9组浏览器验证；仅前端发布、旧会话有效与匿名请求来源边界，镜像现由原型登录发布替代 |
| [授权装载性能优化](verification/authorization-loading-performance-20261124.md) | 当时两个RBAC镜像、330项双策略回归及六项100%、97项HTTP、三轮菜单耗时和查询量；镜像现由原型登录/构造映射发布替代，性能计时仍独立保留 |
| [18系统与部门角色发布](verification/access-extension-release-20261124.md) | 目录发布时的三项服务更新、双份备份及原库保留、97项公网HTTP、真实RBAC浏览器8组及课程浏览器限制 |
| [应用目录及部门角色覆盖率](verification/access-extension-coverage-20261124.md) | 双策略各328项、347份class一致、六项100%及原Maven门槛，完整脱敏报告归档 |
| [18系统与部门角色](verification/access-extension-20261124.md) | 发布前源码、原表矩阵、部门授撤边界、双策略317项及Apifox1.6.0回读；记录新增代码后的真实覆盖率及限制 |
| [JWT修复发布](verification/jwt-coverage-release-20261123.md) | 11月23日RBAC镜像、单服务更新、91次HTTP、其余服务不变及当时验收/来源限制 |
| [RBAC JaCoCo补齐](verification/rbac-jacoco-complete.md) | 11月23日发布源码双策略六项100%、同字节码合并和原门槛证据；不替代后续改动及线上流量覆盖率 |
| [11月23日发布与JaCoCo](verification/release-20261123.md) | 前次集中发布镜像、HTTP、实时撤权、北京时间、监控及当时覆盖率限制 |
| [老师纪要复核](rbac-rectification/RBAC检查记录复核说明.md) | 保留原始技术摘录、来源事实及早期确认语境 |
| [原型确认](design-decisions/prototype.md) | 保留多轮用户选择及原表矩阵的转换解释，不作为当前待办 |
| [T13](verification/T13.md) | 固定早期基线的真实数据库、初始化与运行器安全复现证据 |
| [structured UI](verification/structured-ui-acceptance.md)、[DDD UI](verification/stage6-ddd.md) | 两策略独立界面验收与可复现夹具方法 |
| [通用课程接入](verification/course-integration-generic.md) | 真实双系统授撤权、多路由映射及来源缺口证据 |
| [万人课程身份](verification/course-initialization-r31.md) | 本部/分公司规则、绑定及回执验证 |
| [八项身份](verification/identity-eight-contract.md) | 扩展协议、目录/operationId及Apifox回读证据 |
| [JWT与图件](verification/jwt-design-cleanup.md) | 管理/外部全JWT与双策略图件覆盖检查 |
| [部署适配](verification/ddd-deployment-adaptation.md) | 固定老师来源副本、补丁及部署前完整回归 |
| [云端发布](verification/ddd-production-deployment.md) | 实际首发版本、镜像与云端验收边界 |
| [北京时间与服务清理](verification/beijing-time-and-service-cleanup.md) | 时间修正本机证据及终态任务归档清理结果 |
| [文档与监控](verification/current-docs-and-monitoring.md) | 当前文档归并、删除依据及离线监控验证 |

过期开发计划、重复文档整理说明、被当前模型/验收覆盖的早期实施报告、重复设计决策和旧图已清理，未再转存另一层历史目录。需要原版本时从清理前提交`7fed862dca56844e02b8793d44527b9538e48dee`或Git历史读取。原Excel、用户外部设计文件、旧库/卷和原始运行日志均保留。

- [成员查询与来源IP验证](verification/member-queries-audit-ip-20261123.md)：本轮双策略回归、必要前端/接入检查与未发布边界。

本轮入口：[课堂检查与应用管理](verification/teacher-check-applications-20261124.md)保留本轮独有问题及源码测试；后续正式结果见[应用管理发布](verification/application-management-release-20261124.md)。
