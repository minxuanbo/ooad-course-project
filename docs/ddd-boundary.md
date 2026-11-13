# 公共代码与后续DDD隔离边界

当前只有service/structured，实现入口为RuntimeConfiguration扫描api、service.structured和infrastructure。DDD及策略开关尚未实现；本说明是当前依赖事实和未来实施约束，不是DDD已通过的证明。

## 当前可共用与必须独立的内容

| 区域 | 当前内容 | 后续DDD边界 |
| --- | --- | --- |
| api、frontend、OpenAPI | HTTP DTO/VO、校验、错误/信封、Vue和29操作契约 | 复用同一接口/页面，Controller只调service.port，不写实现分支 |
| service.port | 用例/支撑接口、Command/Query/Result、协议枚举、错误码、固定角色/方法路径目录 | 不导入Entity、Spring、Servlet、结构化或DDD类型；目录是协议/首次数据元信息，不执行动态权限、范围或组织层级策略 |
| infrastructure.security/web/logging | BCrypt/Bearer、请求/游标/技术日志、MVC预检及门禁 | 通过AuthenticationUseCase、ManagementAuthorization、ManagementAccessUseCase等端口调用当次装配实现，不能直接调用StructuredService或DDD应用服务 |
| infrastructure.excel | Excel解析、原件/源字段校验、LoginIdentifiers离线标识碰撞检查 | 复用来源读取端口与技术预检；不依赖结构化服务，DDD业务仍独立保证身份不变量 |
| infrastructure.persistence | JPA Entity/Repository及技术查询 | 结构化服务可直接使用；DDD需独立适配器实现领域仓储，映射领域模型，不让DDD应用直接使用JPA实体/仓储 |
| service.structured | 有效授权、人员范围/级别、部门类型规则、根锁/K、事务脚本和当前初始化编排 | 当前全部为结构化代码；DDD领域策略/行为独立实现同一规则，不继承/转调这些服务或把其搬入common |
| config/bootstrap | 当前扫描、数据源守卫、生产/导入/首次授权启动 | 是显式装配根；DDD阶段需改成互斥注册一套业务Bean，技术与初始化装配须单独复核 |

DepartmentType只声明HEADQUARTERS/COMPANY/DEPARTMENT/PROJECT；结构化层级判断位于StructuredDepartmentService。源数据标识校验归infrastructure.excel，不再出现共享WorkbookImportPlanner导入service.structured的反向依赖。实体中的局部持久化保护不代表DDD领域对象，不能直接改名充当领域实现。

当前RbacCatalog保存方法/路径、初始权限映射和固定K目录，两套实现须遵守同一契约；有效授权计算、人员保护及完整维护者计算不在目录类中。CurrentIdentity与请求能力快照不跨请求缓存授权，DDD仍须锁后读取最新事实。

## DDD开始前必须完成的装配与验证

- 当前没有可用的app.backend-strategy开关，传参不能切换DDD。实现DDD后才引入互斥条件装配；同一个用例接口不能同时存在两套无条件Service Bean。
- DDD的application仅使用共享用例抽象及领域模型/仓储；持久化适配器可引用DDD领域模型/仓储接口，不得反向调用DDD应用服务。架构检查不得误禁合法领域适配器依赖。
- 认证准入、实际API、人员范围/子集/级别、部门规则、K保护、事务与回滚都要由DDD实现自己的业务链。当前ImportUseCase/AuthorizationBootstrapUseCase也有结构化编排；未来共享来源或技术工具不等于直接共享整段结构化初始化服务。
- r07/r11/r13/r14已有共享HTTP契约与结构化启动类。DDD阶段分别启动同契约及隔离专库；r23现为结构化增量测试，届时提取共享断言再新增DDD启动类，不复制或调用结构化服务冒充通过。
- R19R21StructuredBoundaryTest中的“当前无DDD目录”是阶段状态断言，DDD阶段应明确替换为双实现互斥装配检查；公共端口不依赖具体实现、基础设施不调用业务编排等方向约束长期保留。

本次只解除现有反向依赖、归位规则并增加边界测试，没有创建DDD代码、策略占位或更改业务授权范围。源码/测试变化的实际证据见[最终一致性核对](acceptance/final-alignment.md)。
