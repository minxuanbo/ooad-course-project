# MySQL物理模型与初始化

版本：1.0；结构来源：[核心及导入SQL](../../deploy/rbac-core-schema.sql)、[运行支撑SQL](../../deploy/rbac-core-runtime.sql)。本文不新增迁移或改库；SQL与JPA须一起通过validate及真实MySQL测试。

## 1. 通用约定

MySQL8.4、InnoDB、utf8mb4/utf8mb4_0900_bin。BIGINT主键由数据库产生；接口使用正安全整数。启用状态为BIT，权限类型为MENU/BUTTON。技术时间以UTC处理，日志/回执用DATETIME(3)。不自行给核心实体增加老师未要求的审计字段。

## 2. 九张核心表字段字典

除id外，以下“必填”为NOT NULL；enabled均必填，状态默认值由业务创建过程显式提供。

| 表 | 字段及SQL类型 | 约束与空值 |
| --- | --- | --- |
| departments | id BIGINT；code VARCHAR(100)；name VARCHAR(191)；parent_id BIGINT | id主键自增；code/name必填，code唯一；parent_id可空、自引用外键 |
| systems | id BIGINT；code VARCHAR(100)；name VARCHAR(191)；enabled BIT | id主键自增；其余必填，code唯一 |
| roles | id BIGINT；code/name VARCHAR(100)；enabled BIT | id主键自增；其余必填，code唯一，按二进制排序规则判重 |
| users | id BIGINT；username/password_hash/name VARCHAR(100)；phone VARCHAR(32)；position VARCHAR(191)；department_id BIGINT；enabled BIT | id主键自增；phone/position可空，其余必填；username/phone分别唯一且使用utf8mb4_0900_bin；department_id外键 |
| api_resources | id/system_id BIGINT；http_method VARCHAR(16)；path_template VARCHAR(512)；enabled BIT | id主键自增；其余必填；system_id外键；system_id+http_method+path_template联合唯一 |
| permissions | id/system_id BIGINT；parent_id BIGINT；code VARCHAR(100)；name VARCHAR(191)；type ENUM('MENU','BUTTON')；enabled BIT | id主键自增；parent_id可空，其余必填；system_id/parent_id外键；system_id+code联合唯一 |
| user_roles | user_id/role_id BIGINT | 双外键、联合主键、均必填 |
| role_permissions | role_id/permission_id BIGINT | 双外键、联合主键、均必填 |
| permission_apis | permission_id/api_id BIGINT | 双外键、联合主键、均必填 |

username保持前导零，不转换为数值；phone空值允许多条，不把空字符串当作新的独立登录账号。角色code的大小写/重音比较沿真实列规则，不自行改为不区分大小写。

## 3. 三张运行支撑表

| 表 | 字段及用途 | 约束/索引 |
| --- | --- | --- |
| import_sources | id；source_type ENUM(ORGANIZATION,EMPLOYEE,BOOTSTRAP)；source_key VARCHAR(191)；file_sha256 VARCHAR(64)；source_row INT；source_json LONGTEXT；department_id/user_id BIGINT | 主键；source_type+source_key唯一；user_id唯一；目标外键；CHECK保证组织只关联部门、员工/独立账号只关联用户。摘要/行号/关联按来源类型可空，source_type/key/json必填。 |
| authorization_logs | id；occurred_at DATETIME(3)；user_id BIGINT可空；username VARCHAR(100)可空；app_code/permission_code VARCHAR(191)，后者可空；allowed BIT；request_id/reason VARCHAR(64)；event_type VARCHAR(32)；http_method VARCHAR(16)；request_path VARCHAR(512)；http_status INT | 主键、可空用户外键；user_id+id及occurred_at+id索引；其他字段必填。仅最小认证/鉴权记录，历史用户引用不接到新ID。 |
| bootstrap_receipts | seed_key VARCHAR(64)；manifest_sha256 VARCHAR(64)；completed_at DATETIME(3) | 均必填，seed_key主键；仅首次批量授权执行回执，不参与运行时权限或管理员资格计算。 |

共12张物理表，其中9张核心、3张运行支撑；不把物理表数量误称为12个领域核心实体。source_json包含原始资料，应受部署库访问权限控制，不进入公开报告。

## 4. 初始化与保护

顺序为显式创建独立库/账号→执行核心SQL→执行运行SQL→配置员工/管理员独立口令→T03离线导入→生成新CSV预览并核对→不带preview参数实际授权→默认jar启动。完整命令只维护在[部署说明](../../deploy/README.md#新库初始化约束)，避免多份不一致脚本。

首次目标110部门、10001启用账号、10001条角色关联；角色人数1/64/106/72/9758。角色授权之后可合法变更，重复初始化不得拿首次人数强行恢复已撤销关联。回执/来源损坏或已有无来源数据时拒绝覆盖。

原Excel、原业务库和历史日志保留；独立新库不复用旧用户ID。SQL使用CREATE TABLE IF NOT EXISTS不清数据，但不能据此推断旧结构兼容；由JPA validate及初始化安全守卫拒绝错库或错结构。

## 5. 查询与事务

人员、角色、API及候选列表在数据库过滤、计数、排序、分页；候选过滤先于分页，不在Java中取全量后截页。候选角色有效权限子集采用数据库谓词，必要一致读取沿既有事务隔离约定。日志使用ID降序快照游标，total与筛选及快照一致。

跨聚合不变量以Service事务和根悲观锁保护，数据库约束作为并发兜底。普通保存不额外flush；必须在当前写边界转换约束错误时才使用saveAndFlush等，以稳定400/409回应并回滚。
