# 课程接入组件

系统职责、身份链、首次分配与来源限制统一见[课程接入设计](../../docs/course-integration.md)；服务器步骤见[部署指南](../../deploy/README.md)。本目录维护接入配置和必要的外部依赖支撑，不把课程实体加入RBAC。

| 文件 | 用途 |
| --- | --- |
| [catalog.json](catalog.json) | 应用、角色、权限中文名称及短码路由全集 |
| [operations.json](operations.json) | operationId与方法/模板，独立首次脚本显式导入api_operations |
| [initialization.json](initialization.json) | 本部教师、其他学生、同账号管理员首次分配 |
| [local_identity_gateway.py](local_identity_gateway.py) | 本机联调：实时身份转接、完整短码校验及SQLite业务审计 |
| [deployment_gateway.py](deployment_gateway.py) | 服务器Waitress入口，实时转接与独立MySQL审计 |
| [initialize_deployment.py](../../deploy/initialize_deployment.py) | 新服务器离线首发，预览及显式绑定/授权 |
| [course-local-fixes.patch](course-local-fixes.patch) | structured组长差集、学生里程碑、管理员教学班修复 |
| [course-deployment-fixes.patch](course-deployment-fixes.patch) | DDD对等修复、当前学期标记释放、安全日志及回归 |

用[prepare_course_source.py](../../scripts/prepare_course_source.py)从老师固定990cea2导出新副本、依次应用两补丁；不修改老师原目录、pom、Flyway或Vue业务源码，不复用未知旧JAR。CloneFactory固定真实0.0.2，构建方法见部署指南。

两种支撑均不缓存权限，不伪造身份，不将业务审计混入RBAC鉴权日志。身份8项由RBAC实现，课程依赖的另2项业务审计由此独立支撑；审计读取仍要求AV全局权限。私有凭据、SQLite、报告及员工明细只留.local。

默认产品启动不执行课程身份初始化。首次脚本顺序、两库中断恢复、重复回执及老师未实现的业务以接入设计为准；原32人夹具仅供历史隔离回归，不能覆盖万人库。
