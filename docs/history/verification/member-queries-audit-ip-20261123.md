# 部门员工、角色成员与来源IP验证

日期：2026-11-23。基线main为9ff74bea9cd88f840adb45f8330348fb830fe91e；本轮功能分支codex/member-queries-audit-ip。仅实施用户确认的步骤1、2，不部署、不合main、不写Apifox，不导入18系统或启用部门角色分配。

## 1. 实现及设计边界

- 复用GET /api/users，增加departmentScope=DIRECT/SUBTREE及roleId；默认子树兼容原接口，直属必须指定departmentId。先范围再过滤/计数/分页；角色停用保留关联，未知角色为空页。
- 部门“查看员工”、角色“查看成员”共用只读分页组件，支持搜索、账号状态及直属/子树切换；关闭、筛选和身份变化丢弃晚响应。原角色目录共同根门禁及所有分配保护保持。
- 日志clientIp贯穿过滤器、执行期AOP、MVC预检、持久化、真实映射及详情；首次SQL加可空client_ip。仅显式可信代理的单值来源有效，默认不信任转发头；Nginx覆盖外来头，正式接入服务以请求上下文隔离后转交来源。服务间地址不冒称最终用户IP。
- 角色来源、部门分配和待接入状态见[待确认设计](../../requirements/access-extension-plan.md)。后续按Excel完整18系统准备，加现有RBAC和课程共20目录项；本轮不落库、不启用。

核心六对象和Chen ER关系未变；37操作、31路径、71 Schema及K17不变。OpenAPI YAML/JSON同源。查询类图增加roleId，DDD差异顺序图更新实际查询条件构造参数；不重复绘制共同HTTP/IP采集顺序。

## 2. RED与GREEN

真实MySQL的新增HTTP测试先确认三个缺口：DIRECT返回10001而非根直属11；roleId=0返回200而非400；client_ip列及日志采集不存在。最初一次执行被沙箱拒绝本机套接字，不算行为RED；恢复项目私有测试实例并用许可运行后获得上述实际失败。

实现后新增三项HTTP测试及四项IP边界测试。覆盖直属/子树、角色与部门交集、未知角色空页、停用角色关联、状态组合、越界/不存在部门、安全ID/枚举/关联校验，以及登录、匿名拒绝、执行期AOP、已识别MVC预检的来源记录。IP边界覆盖IPv4/IPv6、未信任代理、非法/重复/多地址头、CIDR错误及过滤器传递。

环境：Windows、JDK21、真实CloneFactory0.0.2、私有MySQL8.4.8的127.0.0.1:3353，六个既有可丢弃专库。为这些测试专库对齐client_ip列后执行；未改线上、万人业务库、旧实例、原Excel及原始日志。

| 验证 | 本轮结果 |
| --- | --- |
| DDD完整回归 | 311项，失败/错误/跳过均0 |
| structured完整回归 | 311项，失败/错误/跳过均0 |
| 双策略生产class核对 | 342份SHA-256完全相同，才合并exec |
| 合并JaCoCo | 指令22216、分支1730、行4498、复杂度1867、方法995、类259全部覆盖；六项遗漏均0 |
| 原pom门槛 | 将本轮合并exec交原coverage-check，六项零遗漏通过；未降低门槛或新增排除 |
| Vue | 35项通过，生产构建通过；含新成员组件的筛选、失败、晚响应和身份切换测试 |
| Python | 41项通过、无跳过，含请求来源隔离、正式Waitress中间件和已有真实MySQL业务审计回归 |
| CloneFactory | 53个生成重载均非空，198次赋值；189组可变同名同类型字段核对无遗漏 |
| UML | 生成器通过；受影响PNG已目检，参数可读、无裁切 |

后端按backend/README.md的六专库环境分别设置APP_BACKEND_STRATEGY后运行clean test jacoco:report；同字节码合并与原门槛执行方式沿该文第7节。Vue运行npm test、npm run build；Python显式提供本机隔离数据库配置后执行python -B -m unittest discover -s scripts/tests -v。原始日志、HTML/XML/exec及摘要留.local/member-queries-20261123，不覆盖之前归档的304项报告。

## 3. 未发布与限制

服务器仍为此前发布版本，不会立即出现按钮或IP字段。尚未对新版本取得真实浏览器端到端、正式代理链来源和云端运行结果；组件测试、构建及本机HTTP不冒称这些结果。正式发布前必须另外核对现有数据兼容和可信代理范围；只执行CREATE TABLE IF NOT EXISTS不会升级旧表，不能以自动DDL或重跑初始化覆盖现库。

Apifox仍对应已发布契约，本轮仅修改仓库契约；写入需用户明确授权。18系统导入、应用预置角色分类与部门角色运行规则，以及后续集中验收/发布，等待用户确认。角色成员查询不提前开放部门领导的全局角色维护能力。

## Standards

规范轴首次发现1项P2：新增OpenAPI参数重复及required数组重复，严格校验拒绝。4b79028已去重并从YAML同源生成JSON，两份OpenAPI及LogEntryVO JSON Schema严格校验均通过。独立审核复核未解决项0；其余分层、真实CloneFactory、隐私及编码规范未发现阻断问题。

## Spec

需求轴首次发现2项P2：上述契约重复，以及正式Waitress默认提前清除来源头。后者已在Docker入口保留原始头、保持trusted_proxy=None，仍由Gateway唯一核查可信连接方与单值IP；新增实际中间件测试先RED（默认清头），修复后GREEN。Python最终41项无失败/错误/跳过。独立复核两项均消除，未解决项0；未发现人员范围扩张或提前实施待确认阶段。

审核基线为9ff74be；Java完整回归与342份class对应9aa49db的源码，后续4b79028及记录提交没有修改Java。原pom合并门槛成功日志为.local/member-queries-20261123/coverage-gate-merged.log；原始HTML/exec、测试输出、映射扫描及图件目检资料留在本轮忽略目录，不覆盖此前已发布版本的归档报告。本轮未取得新的真实浏览器端到端证据，Vue脚本测试/构建不冒充浏览器交互验收。

审核结论：Standards首次1项、Spec首次2项；修复复核后两轴未解决项均0。
