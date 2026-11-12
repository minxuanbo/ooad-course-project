# 后端概要设计与双实现方案

版本：1.0；设计依据：[需求](requirements.md)、[领域模型](domain-model.md)、[数据库](database-design.md)和[API契约](api-contract.md)。设计目标与实现现状分节表达，目标架构不作为已实现声明。

## 1. 目标与参考

采用单仓库、单后端应用、共享HTTP契约与数据库结构，业务层提供structured和ddd两种实现，启动配置选择其中一种。两种实现应对同一身份、授权、范围、错误与事务场景返回相同业务结果。

参考老师[course-project-mgt后端设计](https://gitee.com/mingqcn/course-project-mgt/blob/fde08d5dd417abc543145aff33ca8378deb9ca36/docs/backend-design.md)的组织和双实现选择方式；不复制课程领域、外部统一身份、Redis/存储/语音等无关集成，也不为文档对齐升级现有Boot版本。

## 2. 当前实现基线

T12共同基线a79b668的后端是Java21、Boot3.5.6、Spring Data JPA、MySQL、AOP、真实CloneFactory的结构化实现。生产代码位于src/main/java/com/municipal/rbaccore，尚未设置service.structured/service.ddd包，也没有双策略选择配置。

| 当前模块 | 责任 |
| --- | --- |
| api及dto/vo | Controller、Bean Validation、HTTP字段类型、CloneFactory映射；MVC预检在部分管理请求参数解析前鉴权 |
| service | 结构化用例编排、有效授权、部门范围、根锁与完整K保护；读写声明式事务 |
| repository/model | Spring Data JPA仓储及持久化实体；当前不是独立的纯DDD领域层 |
| security | Bearer身份支撑、AOP实际方法/模板门禁与精确API目录 |
| logging | JPA最小日志、游标、统一分层AOP日志与故障降级 |
| importing/bootstrap/runtime | 原始资料导入、首次五角色授权、安全配置守卫及启动命令 |
| com.municipal.rbac保留骨架 | 统一响应/异常/requestId、过滤器、日志端口、Excel读取器 |

本次仅整理文档，不移动源码、改包、改数据库或启用DDD。不能将JPA Entity改名为聚合后宣称完成DDD。

## 3. 拟议分层与包结构

```text
com.municipal.rbaccore
├── api                       共享Controller、DTO、VO、HTTP异常适配
├── service
│   ├── port                  用例接口、Command、Query、Result、共享错误
│   ├── common                已确认的共用横切能力
│   ├── structured            结构化事务脚本及辅助规则
│   └── ddd
│       ├── application       用例编排、事务、调用领域行为
│       └── domain
│           ├── model         聚合、实体及有实际规则的值对象
│           ├── policy        有效授权、范围、可授予与完整K规则
│           └── repository    领域仓储接口
├── infrastructure            JPA Entity/Repository/Adapter、认证支撑、日志、Excel
└── config                    装配、基础设施配置、策略选择
```

API仅依赖service.port，不依赖具体实现。DDD应用层只依赖领域/用例抽象；领域层不依赖Spring MVC、JPA、数据库或基础设施。Infrastructure实现仓储端口，完成JPA与领域对象装配。结构化实现通过适合用例的持久化端口编排，不通过另一套业务实现获取规则结果。

service.common不应成为绕过领域边界的“大公共Service”；共享安全基础设施与领域授权规则需区分。RBAC本身的有效授权、范围和完整K是业务核心，DDD须真实表达这些规则。

## 4. 双实现选择与共同契约

拟议配置使用app.backend-strategy=structured或ddd，默认structured；通过Spring条件装配选择同一用例接口的一种实现，Controller不写策略if/else，不同时登记重复路由。配置尚未接入现有代码；不应把该配置写入当前运行指引当作可用开关。

两套实现共用前端、DTO/VO、状态码、错误码、权限/范围语义、数据库逻辑结构及初始化来源；核心业务各自实现。共享契约测试分别启动两种策略及隔离数据库，验证响应、最终持久化状态、回滚与并发，不仅比较成功JSON。DDD不得转调原结构化Service冒充第二套实现。

## 5. 模块与用例

| 用例组 | 主要输入/输出 | 关键业务边界 |
| --- | --- | --- |
| 登录/本人 | account/password、Bearer；身份/本人有效权限 | 同一账号双入口、歧义拒绝、有效管理API准入 |
| 部门人员 | 部门、q、enabled、page/size；范围树/分页 | 范围先于过滤；可见树根投影null；数据库分页 |
| 人员角色/状态/调动 | 目标用户、角色ids、enabled:false、目标部门 | 角色权限子集、双范围、自身/管理员限制、同一身份即时生效 |
| 角色定义与授权 | code/name、部分改名/启停、permission ids | 共享角色、整体替换、停用保留关联、实际完整K |
| 资源与映射 | 本系统目录、父级/类型/状态、api ids | 同系统、树约束、API多对多与默认拒绝 |
| 最小日志 | 用户/系统/事件/结果/UTC时间/游标 | 日志API+共同根，快照total、脱敏、无归档导出 |
| 初始化 | 原Excel、配置口令、来源摘要/清单 | 新项目、预览只读、一次事务授权、重试不重置 |

## 6. 请求、事务与错误

请求链为requestId/最小审计过滤器→HTTP身份/操作预检→DTO校验与Controller→真实AOP门禁→用例Service事务→JPA。管理写取得可信根行锁后重新核对身份、权限与范围，再验证关联、写入并检查完整K；失败回滚。预检不替代AOP或锁后重验，拒绝日志只记一次。

查询采用只读事务；跨仓储写在同一Service事务。普通持久化不手动flush，约束需在写边界捕获时才使用saveAndFlush；转换为稳定业务错误，不把SQL/表名判断集中到Controller Advice。未授权或范围外请求不能靠非法参数枚举对象。

输入格式、长度、类型、数组唯一和跨字段时间在API边界；有效权限、组织、可授予、树规则及完整K由业务层处理。统一响应为{code,message,data,requestId}；分页使用现有page/size/total，保持第0页及日志游标契约，不照搬示例的pageSize。

## 7. 认证、日志及运行

当前工号/手机号口令和Bearer外形沿既有支撑；身份索引在进程内，重启失效，不写会话表。管理操作每请求读取最新授权与部门；撤权后旧身份不能继续管理。本人查询只要求当前可信身份，不额外要求人员查询权限。

最小鉴权日志由共享端口经JPA独立事务记录。写日志故障只输出异常类型运维事件，不改变原授权结论；查询日志存储故障安全报错。分层技术日志由统一AOP记录方法/耗时/异常类型，禁止参数、凭据或隐私值。日志与回执不进入核心ER。

当前产品入口CoreRuntimeCommand、离线导入CoreImportCommand、首次授权CoreBootstrapCommand互相分离。ddl-auto=validate、sql.init.mode=never、旧初始化关闭，错误库/测试profile组合失败关闭。部署configtree、实际命令与老师CloneFactory固定来源只维护在[部署说明](../deploy/README.md)及[既有来源核对](rbac-rectification/RBAC整改计划与Chen-ER图.md#46-t05-五角色首次授权与实际管理链路)。

## 8. 当前实现UML

下图仅反映a79b668中CoreAuthService.require的现有协作者与调用，省略与该方法无关成员和部分访问器。采用用户提供的uml-class-sequence-diagrams skill；不是DDD目标类图。

![当前身份校验类图](design/current-auth-class.png)

[SVG](design/current-auth-class.svg)

![当前身份校验顺序图](design/current-auth-sequence.png)

[SVG](design/current-auth-sequence.svg)。最左caller代表发起该调用的Controller/业务门禁；未捕获异常直接指向caller，HTTP异常转换在图外。RequestAuditFilter的actor为静态调用，以MetaClass生命线表示。省略返回对象构造及简单访问器，不省略安全判断顺序。

可复现生成脚本：[build-current-uml.mjs](../scripts/build-current-uml.mjs)，运行说明见[图件说明](design/README.md)。图件必须随实际代码变更更新；本次不生成虚构DDD实现类图。

## 9. 实施顺序与停止点

本次先整理文档，再T13验收并固定结构化基线；T13后停止。后续另行授权才抽取公共用例接口、调整基础设施边界、保持结构化回归，再以“人员角色分配”等完整切片落地DDD并逐步覆盖。不得把重构混入当前验收或提前启动课程系统T14/T15。
