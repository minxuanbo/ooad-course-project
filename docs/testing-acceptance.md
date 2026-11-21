# 测试与验收

本页维护当前验证规则与最近执行入口。历史阶段成绩和旧需求矩阵保存在[截至R32的验收快照](history/verification/testing-acceptance-through-r32.md)及[历史索引](history/README.md)，不替代后续修改后的实测。

## 1. 当前验证入口

| 范围 | 证据 |
| --- | --- |
| R33全JWT、双策略图件及文档整理 | [本轮记录](history/verification/jwt-design-cleanup.md)，只记录实际完成的检查 |
| R32八项身份协议、目录及operationId | [独立记录](history/verification/identity-eight-contract.md) |
| R31万人首次课程身份 | [独立记录](history/verification/course-initialization-r31.md) |
| 早期双策略UI及业务验收 | history/verification按日期及提交追溯 |

## 2. 必须验证的行为

| 对象 | 验收要求 |
| --- | --- |
| 初始化 | 原件摘要、110组织、10000员工加admin、独立BCrypt盐、预览只读、重复执行保留维护、失败整批回滚 |
| 登录/JWT | 工号手机号唯一定位、停用/歧义/错口令拒绝；两入口签名/issuer/严格到期、无权限快照、受众隔离、单会话退出、重启失效；普通员工不能因外部角色进入管理端 |
| 即时授权 | 原JWT在角色/权限/API/账号状态修改后按当前数据库重判；角色并集仍有效时不误撤全部能力 |
| 人员/范围 | 部门子树、本人/同级/上级保护、角色差集锁后校验、lockedRoleIds及不可改项原样保留、自定义项资格、调动双范围 |
| 角色/资源/并发 | 完整集合替换、同应用映射、树不变量、共享根锁、锁后重鉴权、同一人完整K17、并发/故障全回滚 |
| 查询/日志 | 先筛后计数分页、只读投影同快照、最小脱敏、游标绑定、稳定total、追加提交顺序及每批重新鉴权 |
| 外部八项协议 | 直连/经本地支撑、目录page从1/pageSize、直属用户5字段、operationId配置、204空退出；统一身份审计另2项不在本轮契约 |
| 前端/课程 | 真实Vue登录与管理页、选人目录、失权/失败/取消、两个本机Origin；课程对象权限与老师既有缺口分别记录 |
| 架构/映射 | 两策略独立业务，DDD不依赖JPA/Entity/structured；真实CloneFactory生成赋值、final/record显式构造、中文注释及注解独行 |
| 文档/UML/Apifox | YAML=JSON且OpenAPI合法、37操作/31路径/71模型/K17；37项加离线入口完整追溯、无重复共同顺序图、图源匹配实际方法、PNG目检、Apifox导出回读 |

## 3. 复现与证据

后端使用JDK21、真实MySQL8.4及CloneFactory0.0.2。按[后端回归配置](../backend/README.md#2-本地回归与隔离)准备每策略的6个专用测试库，执行`mvn -f backend/pom.xml clean package`；产品万人库与测试专库隔离。不以mock数据库、skipTests或旧产物代替通过。JaCoCo verify及服务器容器部署另有边界，见后端/部署说明。

每轮记录源码起点/最终提交、环境、RED/GREEN、完整回归、独立JAR HTTP、浏览器覆盖、审核结论与未验证范围。测试、代理静态审核、用户/老师验收是三类证据。日志、截图、凭据、CSV、JAR及数据库保存在忽略目录；只提交脱敏结论，不覆盖旧日志或删除原库。
