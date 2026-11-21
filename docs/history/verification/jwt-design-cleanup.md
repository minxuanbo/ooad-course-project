# R33全JWT及双策略文档/UML整理验证

日期：2026-11-21—22。源码起点`62907464ba76d035c758f4867ed22219a3484271`，任务分支`codex/jwt-design-cleanup`。用户要求管理和外部应用全部JWT、参考老师docs补齐structured类图、核实所有实现差异且只绘差异、整理文档，并指出Apifox“应用身份”空目录。需求见[R33](../../requirements.md#10-全jwt与双策略设计完整性r33)。老师本机docs仅作只读参考，不采用其中旧实施状态或其他框架版本作为本RBAC要求。

## 1. 最终改动

管理端与外部应用统一通过`JwtIdentityStore`/`JwtIdentityCodec`签发和验证HS256身份JWT；删除旧管理opaque分支，原身份端口及token/accessToken响应字段不变。只含sub/aud/iss/iat/exp/jti，不含角色权限快照。管理aud=rbac，外部aud为单个已登记应用；原管理准入、权限实时读取、单会话退出、进程重启失效及12小时有效期保持。无新表、刷新令牌或委托授权机制。

UML原来偏重DDD的15个模块，现在为各模块补对应structured类图，加上通用身份形成16对。37项HTTP、2类离线入口和横切支撑逐项核对，按24组独立实现差异生成48张顺序图；共同JWT/HTTP/日志/锁/提交及相同Gateway读取不画两份，同一分页/集合模式在覆盖表明确复用。每个消息接收方法核实存在，JPA继承方法及Lombok生成setter按真实来源校验。覆盖表与图件使用同一注册表并对照OpenAPI和用例端口生成。

当前docs根保留12份职责明确的文档，旧阶段计划、UI验收、长验收快照及两份重复设计说明归history；14份旧局部/重复图资源也归档。共19个文件搬迁，原资料和历史成绩不删，受影响链接同步。逐文件对照起点Git内容，14份图源/图件内容相同，5份Markdown除导航重定位与历史标识外正文保留。移除已被完整structured图替代的旧生成脚本，防止生成过时图重新混入当前目录。详设Word/PDF/PPT及讲稿保持原版。

全量Python回归暴露旧32人空库夹具生成器写死R29目录数量，导致R32新增IDR/IDU后失效。新增断言先RED，修复为当前配置结构校验；测试独立核18个API权限、24映射、22唯一课程API，并确认IDR/IDU只初授系统/课程管理员角色。未向现有库执行该夹具，也未改旧夹具数据。

## 2. 测试与本机联调

Windows、JDK21.0.13、Maven3.8.5、MySQL8.4.8、真实CloneFactory0.0.2。沿用R31的独立3351/3352实例及各6个专用回归库；原3349/3350、万人库、角色及课程绑定保留。两策略分别`mvn -B -ntp -o -f backend/pom.xml clean package`，不把R32的208项成绩作为本轮通过。

| 检查 | 实际结果 |
| --- | --- |
| JWT TDD | 管理JWT格式新增单测先因旧随机令牌只有1段而RED（1失败、0错误）；实现后3项身份存储及既有4项JOSE单测均GREEN，另增加共享真实管理HTTP JWT/退出契约 |
| structured真实MySQL完整回归 | 212项，失败/错误/跳过均0，clean package成功 |
| DDD真实MySQL完整回归 | 212项，失败/错误/跳过均0，clean package成功 |
| 独立JAR身份HTTP | 两策略各61次通过，新增管理JWT/aud/无权限快照断言，保持8项直连与转接、受众隔离、退出及真实API撤权恢复 |
| 独立JAR课程HTTP | 两策略各43次通过，逐一核对10001角色与绑定、191教师、9810学生、1同账号管理员、10001独立密码哈希；原令牌授撤权及重复初始化保护通过 |
| Python | 26项通过；包含当前夹具目录及真实HTTP stub。第一次沙箱执行的套接字10013和旧目录断言失败不计通过，最终在允许本机套接字环境重跑 |
| 第一组真实Vue | 25项通过，新增管理页面JWT受众及无权限快照断言；课程部门选人另在127.0.0.1与localhost两Origin均通过。未宣称第二组完整浏览器复验 |
| RBAC前端 | 27项测试及生产构建通过 |

原始日志、Surefire、JAR、HTTP JSON与截图放`.local/jwt-design-20261121`，口令仍在原私有目录，不提交或回显。RBAC后端切至本轮已验证JAR：structured8081、DDD8083；课程8080/8084、支撑8085/8086、Vue5173/5174及数据库保持原进程。后端重启后两端均须重新登录，原演示口令不变。

## 3. 契约、图件与归档核对

当前OpenAPI1.5.0仍为37操作、31路径、71模型、22错误、K17。管理BearerAuth和applicationJwt均标JWT，两个登录字段均有JWT三段格式约束；Bearer仍是携带方式，受众相互隔离。本轮OpenAPI3.1校验、YAML=JSON、两策略28个真实响应Schema均通过；CloneFactory两策略生成摘要相同，53个非空重载、185组可变同名同类型字段无遗漏，Java注解独行检查0项。449个变更Markdown本地目标/锚点无缺失；早已删除的旧JaCoCo指南改指经Git核实存在的固定历史版本，而非本轮删除原资料。

当前图源：`docs/design/ddd-diagrams.json`、`scripts/build-ddd-design.mjs`、`scripts/build-structured-design.mjs`、`scripts/build-identity-design.py`。当前82组包括包2、类32、差异顺序48；接收方法、共同流程禁入、几何及循环/条件边界由生成器核对，视觉检查页在本轮`visual-review/manifest.json`。旧图保存在`docs/history/design-assets/through-r32`，不参与当前生成。

已打开22页检查图覆盖82幅PNG，并复看身份/人员类图及权限树、导入、维护保护等原图。审核发现的权限修改分支遗漏、opt条件文字重叠和structured成员选取偏题均已修正：四项字段opt与DDD两条alt对应源码，条件和首消息留足行距，类成员按模块明确选择并核实声明，不再按源码顺序截取。最终视觉及源码复核通过。

## 4. Apifox同步

目标项目9042431、模块8887289、主分支8854997。写前导出备份；本轮更新管理登录说明、2模型JWT字段及管理JWT组件，接口创建/删除均0、失败均0。平台保留的模块info经完整读取、官方CLI Schema校验后更新，最终官方导出1.5.0、37操作、71模型，与本地操作/模型/鉴权/info比较差异0。

“应用身份”原目录100273904是R32分组移动遗留，已逐项核实全部37接口、子目录及项目Markdown文档均不在其下，再删除该唯一空目录并回读目录树。“统一身份鉴权”8项及其他管理接口保留。证据见本轮`apifox-before.json`、`apifox-import.json`、`apifox-final.json`、`apifox-comparison.json`，空目录核对/删除结果在`.local/apifox/r33-*`，不写老师共享项目。

## 5. 审核与边界

按用户代码/UML skill及code-review skill进行fresh-context规范/需求双轴审核，修复后最终规范轴0项、需求轴0项。审核是静态检查，不冒充独立回归。原老师工程工作树干净、当前提交990cea2，副本仅原3个structured生产补丁；不关闭T14/T15，不部署服务器，不修改详设外部文件。沿本会话用户将有效分支合入main的指示，在验证和审核通过后交付；实际PR及提交状态以GitHub为准。
