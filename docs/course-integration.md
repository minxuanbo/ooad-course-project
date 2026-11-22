# 课程系统接入设计

RBAC与老师课程工程是两个独立业务系统。RBAC提供通用授权目录和身份协议；课程系统保留教师/学生业务身份、user_id绑定、课程对象规则及生产Vue。本文说明接入职责与执行边界；实测版本和成绩见[测试与验收](testing-acceptance.md)。

## 1. 系统与配置边界

| 归属 | 责任 |
| --- | --- |
| RBAC | 用户凭据、数据库登记的Application/Role/Permission/ApiResource、JWT、实时授权与自身鉴权日志 |
| 课程系统 | Teacher/Student、课程/教学班/里程碑/作业等业务对象及权限、文件内容、生产页面 |
| 独立接入支撑 | 实时身份转接、课程短码完整路由投影、独立业务审计及必要的只读对象上下文 |
| 接入初始化 | 核对员工来源与组织事实，创建课程绑定，再发布通用角色目录及授权回执 |

课程身份不加入RBAC领域。`teacher`、`student`、`course-admin`是配置角色编码，不是RBAC内置身份；角色可跨应用拥有权限，但Permission—ApiResource必须同应用。默认万人初始化只登记RBAC，身份开关不自动授课程角色。

| 文件 | 设计用途 |
| --- | --- |
| [catalog.json](../integrations/course/catalog.json) | 应用、角色、中文权限名、短码与全部必要路由 |
| [operations.json](../integrations/course/operations.json) | operationId、方法/模板及全局门禁适用角色；由IDENTITY_OPERATIONS_FILE显式装配 |
| [initialization.json](../integrations/course/initialization.json) | 本部教师、其他学生及同账号管理员的首次分配规则 |

## 2. 身份与授权链

RBAC实现8项统一身份鉴权，清单和字段见[OpenAPI](../api/openapi.yaml)及[业务契约](api-contract.md)。管理`/api/auth/login`与外部`/auth/login`均签发HS256身份JWT，仅含sub/aud/iss/iat/exp/jti。Bearer是HTTP携带方式；管理aud=rbac，课程aud为登记的课程应用编码，不能交叉使用。签名秘密外置，无固定默认值。

每次请求按当前User→Role→Permission→ApiResource及应用状态重算，JWT不缓存角色权限。原JWT在授撤权提交后的下一请求立即体现变化；退出只撤当前会话，RBAC重启须重新登录。普通员工即便有课程角色，也须实际RBAC管理API才能进入管理端。

`/auth/me`返回subject、roles、permissions、system、permissionApis。API类型权限必须有非空且全部同应用启用的映射才导出短码；菜单/按钮可表达无API的展示资格。接入层再按catalog核查老师同一粗粒度短码所需的路由全集，任一必要映射缺失即关闭整个短码，防止部分撤权后借其他路由继续放行。

目录接口遵循page从1、pageSize默认20最多100；部门逐层展开、人员仅直属，实际API和本人子树同时受限。operationId由文件配置定位；未知操作404，配置损坏/重复阻止启动。全局门禁通过后，课程Service仍检查授课、选课、分组、课程状态等对象条件。

## 3. 为什么需要course-integration服务

Swarm中的`ooad_course-integration`运行[deployment_gateway.py](../integrations/course/deployment_gateway.py)，由Waitress承载。它不是第三套业务系统，也不是mock；当前课程配置依赖它完成以下工作：

1. 实时转接真实RBAC的8项身份接口，保持204空退出和上游错误状态，不缓存授权。
2. 将通用权限事实按课程配置收敛为完整短码，限制当前应用受众。
3. 提供课程依赖的`/auth/audit-logs`追加/查询，持久存储于独立course_audit库，不混入RBAC鉴权日志。

业务审计主体来自真实身份，服务端决定时间；同应用requestId由唯一键保证幂等，不同内容重放拒绝。查询在数据库计数/分页，仍要求AV全局权限；course_context账号只读课程归属，不扩展普通教师对象日志范围。

课程浏览器经RBAC Nginx的`/course-identity/`挂载访问支撑；课程后端用`http://course-integration:8085`。公共`/auth/*`直接到RBAC，继续服务其他登记应用。挂载前缀是部署路由，不是新增RBAC业务Controller。当前直接删除此服务会破坏课程身份投影和业务审计链；未来替换须同时调整外部依赖及地址。

本机[local_identity_gateway.py](../integrations/course/local_identity_gateway.py)使用SQLite审计及本地HTTP服务，仅用于隔离联调；不能当作服务器方案。两种支撑实现均不把课程对象投影加入RBAC业务服务。

## 4. 首次身份分配与恢复

总公司本部含根直属领导、本部职能部门和项目，排除分公司及其全部下属。本部员工不论职位均为teacher，其余员工为student；启用、可信根直属、有效基础级别5的RBAC管理员同时为course-admin并建立教师绑定。

当前原件首次核对为191教师（190员工+admin）、9810学生、1名同账号课程管理员。老师dump另有1教师和1学生演示对象，保留但不自动授RBAC角色，物理表可为192/9811。人数是首次核对值，不能作为运行期强制恢复目标。

先完成RBAC导入与五角色授权，再显式预览/执行：

| 场景 | 工具与限制 |
| --- | --- |
| 独立全新本机库 | [initialize_course_integration.py](../scripts/initialize_course_integration.py)，客户端必须显式指向loopback及端口；默认预览，--apply才写入 |
| 新服务器首发 | [initialize_deployment.py](../deploy/initialize_deployment.py)，离线维护窗口执行；新空库导入老师dump后首次校正工号user_id为数字subject，保留课程业务主键 |

本机示例（凭据ini与新报告放.local）：

```powershell
python scripts/initialize_course_integration.py --mysql <mysql.exe绝对路径> --rbac-client <RBAC客户端ini> --course-client <课程客户端ini> --report .local/your-run/course-preview.json
python scripts/initialize_course_integration.py --mysql <mysql.exe绝对路径> --rbac-client <RBAC客户端ini> --course-client <课程客户端ini> --apply --report .local/your-run/course-applied.json
```

工具同时核对原员工“分公司”列及组织父链，不把所有根子树算教师、不按岗位推权。课程库先事务建立唯一user_id绑定；匹配的既有绑定保留，冲突或已删除教师拒绝覆盖。随后RBAC在根锁事务发布目录、角色、权限、人员关联及独立seed_key回执；固定管理员角色得到课程完整权限子集以便合法授撤。

两库分别提交。课程已成功而RBAC失败时尚无相应新应用授权，可在核对后重试，匹配绑定不重复插入；不宣称分布式原子事务。完成回执匹配时重跑跳过，不恢复后来撤回的角色/权限或改动的身份资料。来源、账号全集或配置变化拒绝自动覆盖；此流程不是旧库升级，也不做运行期组织调动自动同步。

## 5. 来源、补丁与运行

正式来源固定老师提交990cea2cc3686599ef5068945706a13e7e0afc5b。使用[prepare_course_source.py](../scripts/prepare_course_source.py)导出独立新副本，原目录保持不动。副本依次应用：

| 补丁 | 内容 |
| --- | --- |
| [course-local-fixes.patch](../integrations/course/course-local-fixes.patch) | structured组长关联差集保存、有效已选课学生读取开放里程碑、已有CV/CTM管理员读取教学班 |
| [course-deployment-fixes.patch](../integrations/course/course-deployment-fixes.patch) | DDD对等修复、当前学期唯一标记释放顺序、两策略安全日志及回归；不改pom、Flyway或Vue业务源码 |

服务器两个后端均DDD，课程公开80、RBAC公开8088，支撑8085及数据库/Actuator/Prometheus留内网。容器DNS、业务端口与内部管理端口不同，精确路由以[Stack](../deploy/stack.yml)和[部署步骤](../deploy/README.md)为准。CloneFactory固定真实0.0.2来源，见[依赖安装](../deploy/README.md#本机依赖安装)。

本机可以分别选择两策略，但不得同时写同一验收库；课程来源与补丁使用上述准备脚本，初始化命令见第4节。RBAC开启APP_IDENTITY_ENABLED、配置operations文件绝对URI；课程Vue关闭mock，分别设置课程API和身份支撑地址。运行配置和检查见[本地复现](reproduction.md#6-课程系统本地联调)。

### 本机浏览器入口与跨域配置

`http://localhost:5174`与`http://127.0.0.1:5174`是不同Origin。课程后端CORS_ALLOWED_ORIGINS、RBAC的IDENTITY_ALLOWED_ORIGINS及本机支撑--origin须显式允许实际入口；不要用通配掩盖配置错误。更改后需相应进程重新读取配置，不必重导身份。原19fbd1a/32人夹具只供历史隔离回归，不能混入万人正式初始化，旧库与日志保留。

## 6. 老师业务的已知限制

文档声明但生产Controller缺失的5项：

- `POST /courses/{courseId}/reset-to-application`
- `PATCH /courses/{courseId}/description`
- `GET /groups/{groupId}`
- `POST /milestones/{milestoneId}/students/{studentId}/oral-records`
- `POST /oral-records/{oralRecordId}/memo-summary`

个人制教师批改链不完整，列表明确409 COURSE_NOT_GROUP_BASED；学生建组未形成完整负责班级归属，完整口试演示使用老师已有教师建组入口。独立支撑业务日志读取只演示课程管理员AV，未扩写普通教师对象范围。这些缺口不能通过追加RBAC权限修复，也不宣称全部课程功能实现。

老师DDD当前学期切换的唯一标记冲突已在部署副本修复，不再列为现行未解决项；原目录保持原版。实际部署、两策略回归、浏览器及来源缺口响应分别保留于[验收入口](testing-acceptance.md)。
