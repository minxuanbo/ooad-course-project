# 数据库概念与关系设计

业务依据为[需求](requirements.md)和[领域模型](domain-model.md)。核心ER表达六对象关系，首次建库结构包含角色级别、组织类型和删除墓碑，不因双策略实现新增重复模型。

## 1. Chen ER概念模型

![六实体核心Chen ER](database/chen-er-overview.png)

[可编辑SVG](database/chen-er-overview.svg)；[关系源码](database/chen-er-source.md)。图中仅六个核心实体，账号认证支撑、日志、导入来源及回执不进入核心ER；全局管理员使用独立初始化User，具备有效基础级别5、可信根范围与实际维护权限，不单列实体。

| 联系 | 基数及参与约束 |
| --- | --- |
| User—Role | M:N，允许无角色用户、无用户角色 |
| Role—Permission | M:N，角色可暂时无权限 |
| Permission—ApiResource | M:N，多个权限可映射同一API；必须同系统 |
| Department—User | 1:N，每人一个非空部门 |
| Department—Department | 一个可空上级、多个下级；真实组织为单根无环连通树 |
| ApplicationSystem—Permission/ApiResource | 各1:N，每项只属一个系统 |
| Department—Role | 1:N；角色端0..1，DEPARTMENT_CUSTOM恰好一个所属部门 |
| ApplicationSystem—Role | 1:N；角色端0..1，APPLICATION_PRESET恰好一个来源应用，不限制跨应用选权 |
| Permission—Permission | 一个可空父节点；同系统、无环，只有MENU可有子节点；BUTTON/API均不可有子节点 |

## 2. 关系映射

六实体表为users、roles、permissions、departments、systems、api_resources；三组M:N分别落user_roles、role_permissions、permission_apis，共九张核心表。实体表使用BIGINT代理主键，关联表使用双外键联合主键。角色代码全局唯一；DEPARTMENT_CUSTOM关联一个所属部门，APPLICATION_PRESET关联一个来源应用，其余两类均无此归属。

工号username、可空phone、password_hash及岗位position作为users的现有属性保存，不增加employee_no、login_identifiers、独立公司、会话、管理员资格或直接用户权限表。

## 3. 存储约束与业务约束分工

数据库兜底主键、外键、编码唯一、双字段分别唯一与关联去重；树循环、跨用户工号/手机号碰撞、跨系统映射、完整K及人员范围由实际业务入口验证。不能宣称自引用外键已防环，也不能宣称工号/手机号两个独立唯一索引已防跨列歧义。登录匹配仍须对不同用户歧义失败关闭。

同一业务事务中的关联整体替换、状态变更及维护者检查共同提交；发生故障或冲突不留下部分更新。并发写共用导入来源核实的根部门行锁，取锁后重新鉴权，不以“先查后写”替代锁保护。

## 4. 双实现共用存储

structured与ddd共用同一逻辑表结构、首次初始化来源及协议；当前按用户要求直接交付新库首次SQL，不提供升级脚本。DDD领域对象与JPA Entity分开，通过适配器装配；不复制ddd_users等第二套业务表。两种策略分别测试时使用隔离的相同结构数据库，不并行修改同一套测试数据。

部署选择显式rbac_core，应用ddl-auto=validate、sql.init.mode=never、旧初始化关闭；建表由部署步骤显式执行，不从应用启动恢复旧模型。既有SQL不承担自动升级未知旧库的职责，结构冲突先停止并核对备份/迁移。

完整字段、索引、运行支撑与初始化顺序见[物理模型](database-design.md#physical-model)及[部署说明](../deploy/README.md)。

## 5. 设计取舍与扩展限制

MENU/BUTTON树表达导航和显式选权，API类型权限表达通用API短码；ApiResource独立表达方法/路径；多对多映射允许一个动作由多个有效权限覆盖。部门树表达人员范围和部门角色适用范围；所属角色不隐式给部门员工授权，仍需明确user_roles关联。五角色是首次清单，不在数据库设计中固化为管理员类型。

启停仅改变有效性，不物理删除用户、角色及历史关联。部门提供删除空节点接口并保留内部来源墓碑，其他实体没有删除接口；外键不设置级联删除，避免共享授权及日志来源被误清理。完整K、真实根和子集校验由业务事务实现，不能伪称SQL单行约束已覆盖跨对象规则。

后续双实现沿同一概念/关系模型，领域聚合不与每张表一一对应。真正新增需求须先更新模型及评审。若未来需要保留已有部署数据，再另行设计迁移；当前首次SQL不承担旧库升级，不用DDL自动更新旧业务库。

Permission.type采用MENU/BUTTON/API。API项用于必须映射真实接口的后端短码，缺少映射或任一映射失效即不导出；类型不改变六实体关系。两套首次SQL针对新库，不交付升级脚本。课程业务审计由独立接入服务写入course_audit，不混入authorization_logs。

图中“权限层级”是Permission的自引用目录关系，落到permissions.parent_id：一个权限最多一个父节点，MENU可有多个子节点。它组织菜单、按钮和API权限的展示及选权，不表达角色上下级，也不产生授权继承。授予父菜单不自动获得子按钮；直接授予的子权限也不因父节点停用而自动失效。后端始终沿用户、适用角色、直接权限及同应用API映射检查有效链；导航补齐祖先只是展示，不把祖先写入授权集合。

鉴权运行支撑表authorization_logs新增可空client_ip VARCHAR(45)，记录经服务器核实的IPv4/IPv6。该字段不改变六核心对象及核心Chen ER关系；字段传递见后端设计，首次SQL仍仅面向新库，不自动改写旧表或补造历史日志。

<a id="physical-model"></a>

## MySQL物理模型与约束字典

字段依据[核心/来源SQL](../deploy/rbac-core-schema.sql)与[运行SQL](../deploy/rbac-core-runtime.sql)，按用户要求直接维护全新部署的首次初始化DDL，不提供旧库升级脚本。概念模型见[数据库设计](database-design.md)。

<a id="physical-model-1-通用约定"></a>

### 1. 通用约定

MySQL8.4、InnoDB、utf8mb4/utf8mb4_0900_bin。实体BIGINT主键自增，对外只接受正安全整数；工号/手机号按字符串。BIT表示启用或结论，技术时间为UTC的DATETIME(3)。未声明DEFAULT的字段由业务显式赋值，不擅自补充审计字段。

下面逐字段列SQL类型、空值和含义；各表后列现有主键/唯一键/外键/索引。默认无级联删除。9张核心表与5张运行支撑表不等于14个核心实体。

<a id="physical-model-2-departments：部门树"></a>

### 2. departments：部门树

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| code | VARCHAR(100) | 否 | 稳定业务编码 |
| name | VARCHAR(191) | 否 | 显示名称 |
| parent_id | BIGINT | 是 | 直接上级标识；NULL为根/顶层 |
| type | VARCHAR(20) | 否 | HEADQUARTERS/COMPANY/DEPARTMENT/PROJECT，服务端控制合法父子类型 |
| deleted | BIT | 否 | 默认0；删除空节点后为1，保留来源外键 |
| active_name | VARCHAR(191)生成列 | 是 | 有效节点TRIM(name)，删除节点NULL |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_department_code UNIQUE (code)`
- `CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES departments(id)`

补充约束：uk_department_sibling_name(parent_id,active_name)约束有效同级名称；ck_department_type约束四种类型，ck_department_name约束trim后1—100字符。根唯一和父子类型由可信来源及共同根锁内服务规则保证，HTTP不接受第二个总公司或层级移动。删除不解除来源外键，不级联人员。

<a id="physical-model-3-systems：应用系统"></a>

### 3. systems：应用系统

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| code | VARCHAR(100) | 否 | 稳定业务编码 |
| name | VARCHAR(191) | 否 | 显示名称 |
| enabled | BIT | 否 | 是否启用，影响实际有效性 |
| connection_status | VARCHAR(16) | 否 | PENDING/CONNECTED，与enabled独立；CHECK兜底 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_system_code UNIQUE (code)`

<a id="physical-model-4-roles：角色定义与来源归属"></a>

### 4. roles：角色定义与来源归属

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| code | VARCHAR(100) | 否 | 稳定业务编码 |
| name | VARCHAR(100) | 否 | 显示名称 |
| enabled | BIT | 否 | 是否启用，影响实际有效性 |
| management_level | INT | 是 | SYSTEM_BUILTIN为5/4/3/2/1，其余NULL；HTTP不可修改 |
| role_type | VARCHAR(24) | 否 | SYSTEM_BUILTIN/APPLICATION_PRESET/DEPARTMENT_CUSTOM/GLOBAL_CUSTOM |
| owner_department_id | BIGINT | 是 | DEPARTMENT_CUSTOM必有所属部门，其余为空；FK departments |
| application_id | BIGINT | 是 | APPLICATION_PRESET必有来源应用，其余为空；FK systems |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_role_code UNIQUE (code)`
- `CONSTRAINT ck_role_level CHECK (management_level BETWEEN 1 AND 5)`
- `fk_role_owner_department`、`fk_role_application`两条外键；`ck_role_scope`保证分类、级别及归属组合一致，完整表达式以首次SQL为准。

角色补充字段management_level INT可空，CHECK范围1—5。首次五角色分别为5/4/3/2/1；自定义NULL。级别不参与HTTP PATCH复制，仅用于授予上限、目标保护及完整维护者资格；不能根据可变名称或职位重算。

<a id="physical-model-5-users：人员与本地账号"></a>

### 5. users：人员与本地账号

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| username | VARCHAR(100) | 否 | 工号，按字符串保留前导零 |
| password_hash | VARCHAR(100) | 否 | BCrypt哈希，各账号独立盐 |
| name | VARCHAR(100) | 否 | 显示名称 |
| phone | VARCHAR(32) | 是 | 同一账号的另一个登录入口 |
| position | VARCHAR(191) | 是 | 原表岗位，只作来源资料 |
| department_id | BIGINT | 否 | 部门标识 |
| enabled | BIT | 否 | 是否启用，影响实际有效性 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_user_username UNIQUE (username)`
- `CONSTRAINT uk_user_phone UNIQUE (phone)`
- `CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES departments(id)`

<a id="physical-model-6-api_resources：api资源"></a>

### 6. api_resources：API资源

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| system_id | BIGINT | 否 | 所属应用系统标识 |
| http_method | VARCHAR(16) | 否 | 规范HTTP方法 |
| path_template | VARCHAR(512) | 否 | 服务端登记路径模板 |
| enabled | BIT | 否 | 是否启用，影响实际有效性 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_api_system_method_path UNIQUE (system_id, http_method, path_template)`
- `CONSTRAINT fk_api_system FOREIGN KEY (system_id) REFERENCES systems(id)`

<a id="physical-model-7-permissions：菜单、按钮与api权限"></a>

### 7. permissions：菜单、按钮与API权限

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| system_id | BIGINT | 否 | 所属应用系统标识 |
| parent_id | BIGINT | 是 | 直接上级标识；NULL为根/顶层 |
| code | VARCHAR(100) | 否 | 稳定业务编码 |
| name | VARCHAR(191) | 否 | 显示名称 |
| type | ENUM('MENU','BUTTON','API') | 否 | MENU、BUTTON或API |
| enabled | BIT | 否 | 是否启用，影响实际有效性 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_permission_system_code UNIQUE (system_id, code)`
- `CONSTRAINT fk_permission_system FOREIGN KEY (system_id) REFERENCES systems(id)`
- `CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id) REFERENCES permissions(id)`

<a id="physical-model-8-user_roles：用户—角色关联"></a>

### 8. user_roles：用户—角色关联

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| user_id | BIGINT | 否 | 新库用户标识 |
| role_id | BIGINT | 否 | 角色标识 |

现有约束/索引：

- `PRIMARY KEY (user_id, role_id)`
- `CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users(id)`
- `CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES roles(id)`

<a id="physical-model-9-role_permissions：角色—权限关联"></a>

### 9. role_permissions：角色—权限关联

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| role_id | BIGINT | 否 | 角色标识 |
| permission_id | BIGINT | 否 | 权限标识 |

现有约束/索引：

- `PRIMARY KEY (role_id, permission_id)`
- `CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES roles(id)`
- `CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)`

<a id="physical-model-10-permission_apis：权限—api关联"></a>

### 10. permission_apis：权限—API关联

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| permission_id | BIGINT | 否 | 权限标识 |
| api_id | BIGINT | 否 | API标识 |

现有约束/索引：

- `PRIMARY KEY (permission_id, api_id)`
- `CONSTRAINT fk_permission_api_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)`
- `CONSTRAINT fk_permission_api_api FOREIGN KEY (api_id) REFERENCES api_resources(id)`

<a id="physical-model-11-import_sources：导入来源"></a>

### 11. import_sources：导入来源

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| source_type | ENUM('ORGANIZATION','EMPLOYEE','BOOTSTRAP') | 否 | 组织/员工/独立账号来源类别 |
| source_key | VARCHAR(191) | 否 | 类别内稳定来源键 |
| file_sha256 | VARCHAR(64) | 是 | 原Excel摘要，独立账号来源可空 |
| source_row | INT | 是 | 原数据行号，非文件来源可空 |
| source_json | LONGTEXT | 否 | 保留的原始字段/来源内容，限制库访问 |
| department_id | BIGINT | 是 | 部门标识；仅组织来源使用 |
| user_id | BIGINT | 是 | 新库用户标识；组织来源不使用 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_import_source_key UNIQUE (source_type, source_key)`
- `CONSTRAINT uk_import_source_user UNIQUE (user_id)`
- `CONSTRAINT fk_import_department FOREIGN KEY (department_id) REFERENCES departments(id)`
- `CONSTRAINT fk_import_user FOREIGN KEY (user_id) REFERENCES users(id)`
- `CONSTRAINT ck_import_source_target CHECK (`，完整表达式见SQL；组织仅连部门，员工/独立账号仅连用户。

<a id="physical-model-12-authorization_logs：最小鉴权日志"></a>

### 12. authorization_logs：最小鉴权日志

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| occurred_at | DATETIME(3) | 否 | 事件UTC时间，毫秒精度 |
| user_id | BIGINT | 是 | 新库用户标识 |
| username | VARCHAR(100) | 是 | 工号，按字符串保留前导零 |
| app_code | VARCHAR(191) | 否 | 目标系统编码 |
| permission_code | VARCHAR(191) | 是 | 已定位权限编码，可空 |
| allowed | BIT | 否 | 实际允许/拒绝结论 |
| request_id | VARCHAR(64) | 否 | 请求关联标识 |
| client_ip | VARCHAR(45) | 是 | 服务端核实的请求来源IPv4/IPv6；历史未采集为空，不接收客户端自报值 |
| reason | VARCHAR(64) | 否 | 稳定安全原因码 |
| event_type | VARCHAR(32) | 否 | 登录或鉴权事件 |
| http_method | VARCHAR(16) | 否 | 规范HTTP方法 |
| request_path | VARCHAR(512) | 否 | 安全路径，不含查询串或凭据 |
| http_status | INT | 否 | 实际HTTP状态 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `INDEX ix_authorization_log_user (user_id, id)`
- `INDEX ix_authorization_log_time (occurred_at, id)`
- `CONSTRAINT fk_authorization_log_user FOREIGN KEY (user_id) REFERENCES users(id)`

<a id="physical-model-13-bootstrap_receipts：首次授权回执"></a>

### 13. bootstrap_receipts：首次授权回执

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| seed_key | VARCHAR(64) | 否 | 首次授权方案键 |
| manifest_sha256 | VARCHAR(64) | 否 | 首次清单摘要 |
| completed_at | DATETIME(3) | 否 | 完成UTC时间，毫秒精度 |
| source_json | LONGTEXT | 是 | 独立业务目录来源矩阵、摘要及计划；不参与运行授权 |

现有约束/索引：

- `PRIMARY KEY (seed_key)`

<a id="physical-model-14-约束分工与查询路径"></a>

### 14. 约束分工与查询路径

| 场景 | 数据库兜底 | 业务层补足 |
| --- | --- | --- |
| 登录标识 | username、phone各自唯一，NULL手机号可多条 | 导入校验跨用户跨列碰撞；登录仍拒绝多用户歧义，不能误认两个索引已防跨列 |
| 组织/权限树 | 可空自引用FK、非空归属FK | 单根连通无环；权限父子同系统，仅MENU可有子节点 |
| 授权关联 | 联合主键、两端FK | 角色有效Permission子集、同系统、整体替换、实际API OR |
| 共享维护 | 独立事务和根行锁所需行 | 锁后重验及同一启用根用户完整K，拒绝拼人 |
| 来源/回执 | 来源键/用户唯一、目标CHECK、seed主键 | 原件摘要、源行与新ID核对；重复执行不重置变更 |

主要查询路径：departments.parent_id遍历范围；users.department_id限定人员；用户/角色/权限关联表连接计算有效授权；api_resources的系统+方法+模板唯一键定位操作；日志按id降序快照，user/time复合索引辅助过滤。外键所需索引由MySQL维护，不凭文档假称已添加额外复合索引。姓名contains搜索和复杂候选谓词的实际计划须后续用EXPLAIN与万人基准检查，本次不宣称性能SLA。

<a id="physical-model-15-初始化、迁移与双实现"></a>

### 15. 初始化、迁移与双实现

目标110部门、10001启用账号、根直属11人；首次五角色关联10001，人数1/64/106/72/9758。来源111组织可合并映射同根；后续合法授权/状态变化不得按初始人数恢复。具体阶段见[初始化设计](initialization.md)，当前本地命令只维护在[后端说明](../backend/README.md#3-新项目本地初始化与运行)。

structured与ddd共享逻辑表，不建ddd_users等镜像表；领域对象与JPA Entity通过适配器映射。两策略测试使用相同结构的隔离库。应用validate/never且关闭旧初始化，CREATE TABLE IF NOT EXISTS不代表旧库自动升级。交付始终采用全新库首次SQL；已有环境的兼容操作在发布前单独审查、备份并核对，不交付自动升级脚本，不复用旧ID或改写原日志。

source_json、password_hash、手机号与原始日志只供必要运行/部署访问，不在公共VO和审核报告中泄露。数据库及报告访问采用最小必要范围。


## 接入元数据支撑表

以下两表使应用接入可在界面维护，属于通用接入支撑，不改变六核心实体/九核心关系表及Chen ER的业务语义。当前合计14表、81字段；核心图保留六实体，物理模型包含全部支撑关系。

### application_profiles

| 字段 | SQL类型 | 可空 | 作用 |
| --- | --- | --- | --- |
| system_id | BIGINT | 否 | 主键及外键systems.id，一应用一份接入说明 |
| entry_url | VARCHAR(512) | 是 | 应用入口；精确Origin用于身份跨域，不由RBAC抓取 |
| login_permission_code | VARCHAR(100) | 是 | 本应用独立登录项编码；无profile的旧应用沿原准入语义 |

外键fk_profile_system无级联删除。登录项属于动态业务关联，保存事务校验同应用且启用，不以同名跨系统权限放行。

### api_operations

| 字段 | SQL类型 | 可空 | 作用 |
| --- | --- | --- | --- |
| system_id | BIGINT | 否 | 联合主键及外键systems.id |
| operation_id | VARCHAR(128) | 否 | 应用内唯一稳定操作标识；不同应用可同名 |
| http_method | VARCHAR(16) | 否 | 映射到真实API的方法 |
| path_template | VARCHAR(512) | 否 | 映射到真实API的路径模板 |
| requires_permission | BIT(1) | 否 | 是否进入全局API权限门禁 |
| permission_roles | LONGTEXT | 否 | JSON字符串数组；指定哪些配置角色适用全局门禁 |

联合主键(system_id,operation_id)、fk_operation_system、ck_operation_method、ck_operation_roles_json。permission_roles不是给人员授角色，不能代替课程对象范围；多operationId允许指向同一路由。运行每次读库，移除操作即404，无文件回退。已有权限/API只停用，接入操作元数据允许明确差量移除。
