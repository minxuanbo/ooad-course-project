# 通用应用接入与课程本地验证

日期：2026-11-20；分支：codex/course-local-integration；main基线717345f。业务实现与审核修复快照为09c2093、ee629a9，后续仅补浏览器定位和验收记录。现行设计见[课程接入](../../course-integration.md)，此前教师最小试验只作为历史。

## 最终验证

| 检查 | structured | ddd |
| --- | --- | --- |
| RBAC真实MySQL完整clean package | 195项，失败/错误/跳过0 | 195项，失败/错误/跳过0 |
| 独立JAR对接老师structured的真实HTTP | 141次全部符合预期 | 142次全部符合预期 |
| 两个正式Vue的真实Chrome交互 | 27项通过 | 27项通过 |

HTTP计数包含实际允许和预期401/403/404/409，其中个人制批改列表的409是老师来源缺口，不代表该业务已经完成。两策略场景相同；次数相差1是新增教师时遇到此前软删除的虚构候选，按老师规则409后换下一候选，未清空历史来制造通过。

RBAC前端27项Node测试及生产构建通过；课程原始Vue的TypeScript检查/构建通过。辅助脚本8项通过，覆盖持久审计幂等/隔离、chunked请求体、多路由权限部分撤回失败关闭、临时授权故障恢复及夹具目录。

真实浏览器覆盖学生课程/个人提交/组队/口试/成绩，教师课程/教学班/名册/小组/里程碑与批改工作区，课程管理员课程/学期/教师/日志/团队。另在RBAC页面撤回菜单和按钮并观察原课程令牌后续结果，恢复后原登录继续可用。新增检查核实RBAC及课程中文权限名称、稳定编码和局部管理员锁定角色，不开启网络mock、不录制密码或令牌。

## 关键修复与CloneFactory

- 自定义角色不冻结人员。写事务锁后重读数据库集合，对角色新增/删除逐项验权，锁定项可原样保留；局部管理者仍受人员级别及部门范围约束，自定义项仍仅管理员可增删。真实HTTP与页面均验证。
- 目录从已登记Application读取；RBAC不写课程身份、业务模型或路由白名单。老师角色与粗粒度路由需求仅在接入配置中表达。
- /auth/me提供通用permissionApis事实；课程适配器核实同一短码的全部必要路由。部分撤回TCM映射先复现204错误放行，修复后真实DELETE/PUT均403，恢复映射后原令牌可用。
- 新身份查人端点补正安全整数上限；角色数组去除旧maxItems=1。统一过滤器记录实际查人授权允许/拒绝，门禁通过但目标404仍为allowed=true；不把元数据查询伪装成课程业务执行。
- R30中文权限名由两套首次授权共用目录提供，当前虚构库13项RBAC及33项课程名称均已同步，编码/ID/关联不变；前端不按英文码硬翻译。

clean生成的真实CloneFactory共53个重载，全部有字段赋值，空映射0；185组可变同名同类型字段核对无缺失。新增ApplicationLoginDTO.password及UserRolesResult.lockedRoleIds生成赋值已明确核实。record/final按字段显式构造。注解独行扫描0项。OpenAPI共33操作、17项K，YAML/JSON同源且3.1校验通过。

## Standards

两个fresh-context子代理按指定code-review skill独立审查。首次规范轴发现1项：身份查人缺安全整数上限；已补API层@Max、同源Schema及行为回归，复核剩余0项，判断项0项。浏览器精确文本定位的额外风险已改为按编码定位行，并由真实27项交互确认。

## Spec

首次需求轴发现2项：身份角色数组旧单项上限、查人鉴权允许日志遗漏。均已修复并复核为0项；新增安全边界与审计回归先观察到真实行为RED，再14项GREEN。固定scenario路径的闲置副作用已移除，报告父目录支持新建。没有新增委托机制、擅自课程数据权限或另一套RBAC。

最终：Standards硬违规0、判断项0；Spec剩余0。

## 环境、原件与必要改动

原3349实例及历史数据全部保留；本轮新建3350实例，含rbac_core/course_project和六个隔离回归库。初始32虚构RBAC账号、4本地教师、3本地学生、2课程、2教学班、4里程碑。真实写入后保留虚构申请课程、作业、成绩及审计，不通过重导入覆盖这些状态。口试预约上限100是已有课程配置，便于重复体验；未修改老师默认值或清理预约历史。

老师原目录提交19fbd1a的943个来源文件SHA-256不变；副本仅改StructuredCourseService、StructuredMilestoneService、StructuredTeachingClassService三个生产文件。修复重复保留课程组长、已选课学生读取里程碑、持CV/CTM管理员读取教学班；[补丁及清单](../../../integrations/course/README.md)可相对原件git apply --check。未改老师pom、Flyway或Vue；旧额外教师名单安全补丁撤除。

本地外部支撑8085实时转接RBAC身份并持久保存业务审计，归属查询使用独立SELECT账号；不混入RBAC鉴权日志，不作为服务器部署方案。当前运行RBAC DDD8081、课程structured8080、RBAC Vue5173、课程Vue5174。运行JAR与最终测试产物SHA-256一致。凭据、SQL、JAR、数据库、原始日志和截图均在.local/course-integration/full，不提交。

可核对产物：rbac-structured-full-final.log、rbac-ddd-full-final.log、structured-http.json、ddd-http.json、browser-structured/result.json、browser-ddd/result.json及对应PNG、mapping-audit.txt、artifact-check.json。原始账号口令在私有配置，不在报告中复制。

## 已知来源限制与评审关口

老师生产Controller缺少课程重置申请、单独description更新、小组GET详情、个人口试成绩、录音摘要5个文档路径；个人制教师批改列表明确409 COURSE_NOT_GROUP_BASED，其详情/批改链未完成。学生建组尚缺负责教学班归属，口试完整链使用老师已有教师建组入口。普通教师对象范围的外部审计读取未另行实现，本轮日志演示为持AV课程管理员。老师DDD当前学期切换409的旧问题未改；不宣称老师DDD整套验收通过。

这些来源缺口不解释为RBAC授权不足，不通过提权或在RBAC补课程业务掩盖。T14 #15负责真实场景/身份/原型与契约，T15 #16负责两系统授撤权及隔离证据，仍open供小组评审。本分支不合main、不关闭票、不部署服务器，也不推送Apifox。
