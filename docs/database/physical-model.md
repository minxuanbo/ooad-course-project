# MySQL物理模型与约束字典

版本1.1评审稿；字段依据[核心/来源SQL](../../deploy/rbac-core-schema.sql)与[运行SQL](../../deploy/rbac-core-runtime.sql)，不新增DDL或迁移。概念模型见[数据库设计](../database-design.md)。

## 1. 通用约定

MySQL8.4、InnoDB、utf8mb4/utf8mb4_0900_bin。实体BIGINT主键自增，对外只接受正安全整数；工号/手机号按字符串。BIT表示启用或结论，技术时间为UTC的DATETIME(3)。未声明DEFAULT的字段由业务显式赋值，不擅自补充审计字段。

下面逐字段列SQL类型、空值和含义；各表后列现有主键/唯一键/外键/索引。默认无级联删除。9张核心表与3张运行支撑表不等于12个核心实体。

## 2. departments：部门树

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| code | VARCHAR(100) | 否 | 稳定业务编码 |
| name | VARCHAR(191) | 否 | 显示名称 |
| parent_id | BIGINT | 是 | 直接上级标识；NULL为根/顶层 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_department_code UNIQUE (code)`
- `CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES departments(id)`

## 3. systems：应用系统

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| code | VARCHAR(100) | 否 | 稳定业务编码 |
| name | VARCHAR(191) | 否 | 显示名称 |
| enabled | BIT | 否 | 是否启用，影响实际有效性 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_system_code UNIQUE (code)`

## 4. roles：共享角色定义

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| code | VARCHAR(100) | 否 | 稳定业务编码 |
| name | VARCHAR(100) | 否 | 显示名称 |
| enabled | BIT | 否 | 是否启用，影响实际有效性 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_role_code UNIQUE (code)`

## 5. users：人员与本地账号

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

## 6. api_resources：API资源

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

## 7. permissions：菜单/按钮权限

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| id | BIGINT | 否 | 新库内部标识，不是工号；数据库自增 |
| system_id | BIGINT | 否 | 所属应用系统标识 |
| parent_id | BIGINT | 是 | 直接上级标识；NULL为根/顶层 |
| code | VARCHAR(100) | 否 | 稳定业务编码 |
| name | VARCHAR(191) | 否 | 显示名称 |
| type | ENUM('MENU','BUTTON') | 否 | MENU或BUTTON |
| enabled | BIT | 否 | 是否启用，影响实际有效性 |

现有约束/索引：

- `PRIMARY KEY (id)`
- `CONSTRAINT uk_permission_system_code UNIQUE (system_id, code)`
- `CONSTRAINT fk_permission_system FOREIGN KEY (system_id) REFERENCES systems(id)`
- `CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id) REFERENCES permissions(id)`

## 8. user_roles：用户—角色关联

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| user_id | BIGINT | 否 | 新库用户标识 |
| role_id | BIGINT | 否 | 角色标识 |

现有约束/索引：

- `PRIMARY KEY (user_id, role_id)`
- `CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES users(id)`
- `CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES roles(id)`

## 9. role_permissions：角色—权限关联

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| role_id | BIGINT | 否 | 角色标识 |
| permission_id | BIGINT | 否 | 权限标识 |

现有约束/索引：

- `PRIMARY KEY (role_id, permission_id)`
- `CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES roles(id)`
- `CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)`

## 10. permission_apis：权限—API关联

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| permission_id | BIGINT | 否 | 权限标识 |
| api_id | BIGINT | 否 | API标识 |

现有约束/索引：

- `PRIMARY KEY (permission_id, api_id)`
- `CONSTRAINT fk_permission_api_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)`
- `CONSTRAINT fk_permission_api_api FOREIGN KEY (api_id) REFERENCES api_resources(id)`

## 11. import_sources：导入来源

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

## 12. authorization_logs：最小鉴权日志

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

## 13. bootstrap_receipts：首次授权回执

| 字段 | SQL类型 | 可空 | 含义/生成方式 |
| --- | --- | --- | --- |
| seed_key | VARCHAR(64) | 否 | 首次授权方案键 |
| manifest_sha256 | VARCHAR(64) | 否 | 首次清单摘要 |
| completed_at | DATETIME(3) | 否 | 完成UTC时间，毫秒精度 |

现有约束/索引：

- `PRIMARY KEY (seed_key)`

## 14. 约束分工与查询路径

| 场景 | 数据库兜底 | 业务层补足 |
| --- | --- | --- |
| 登录标识 | username、phone各自唯一，NULL手机号可多条 | 导入校验跨用户跨列碰撞；登录仍拒绝多用户歧义，不能误认两个索引已防跨列 |
| 组织/权限树 | 可空自引用FK、非空归属FK | 单根连通无环；权限父子同系统，BUTTON无子 |
| 授权关联 | 联合主键、两端FK | 角色有效Permission子集、同系统、整体替换、实际API OR |
| 共享维护 | 独立事务和根行锁所需行 | 锁后重验及同一启用根用户完整K，拒绝拼人 |
| 来源/回执 | 来源键/用户唯一、目标CHECK、seed主键 | 原件摘要、源行与新ID核对；重复执行不重置变更 |

主要查询路径：departments.parent_id遍历范围；users.department_id限定人员；用户/角色/权限关联表连接计算有效授权；api_resources的系统+方法+模板唯一键定位操作；日志按id降序快照，user/time复合索引辅助过滤。外键所需索引由MySQL维护，不凭文档假称已添加额外复合索引。姓名contains搜索和复杂候选谓词的实际计划须后续用EXPLAIN与万人基准检查，本次不宣称性能SLA。

## 15. 初始化、迁移与双实现

目标110部门、10001启用账号、根直属11人；首次五角色关联10001，人数1/64/106/72/9758。来源111组织可合并映射同根；后续合法授权/状态变化不得按初始人数恢复。具体阶段见[初始化设计](../initialization.md)，命令只维护在[部署说明](../../deploy/README.md#新库初始化约束)。

structured与ddd共享逻辑表，不建ddd_users等镜像表；领域对象与JPA Entity通过适配器映射。两策略测试使用相同结构的隔离库。应用validate/never且关闭旧初始化，CREATE TABLE IF NOT EXISTS不代表旧库自动升级。后续结构变化须新增明确版本迁移并评审备份/回滚，不修改已执行迁移或复用旧ID/日志外键。

source_json、password_hash、手机号与原始日志只供必要运行/部署访问，不在公共VO和审核报告中泄露。本轮不改SQL、不初始化数据库。
