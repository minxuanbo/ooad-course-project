# 数据库概念与关系设计

业务依据为[需求](requirements.md)和[领域模型](domain-model.md)。核心ER表达六对象关系，首次建库结构包含角色级别、组织类型和删除墓碑，不因双策略实现新增重复模型。

## 1. Chen ER概念模型

![六实体核心Chen ER](database/chen-er-overview.png)

[可编辑SVG](database/chen-er-overview.svg)；[三段关系源码及既有评审](database/chen-er-source.md)。图中仅六个核心实体，账号认证支撑、日志、导入来源及回执不进入核心ER；全局管理员使用独立初始化User，具备有效基础级别5、可信根范围与实际维护权限，不单列实体。

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

完整字段、索引、运行支撑与初始化顺序见[物理模型](database/physical-model.md)及[部署说明](../deploy/README.md)。

## 5. 设计取舍与扩展限制

MENU/BUTTON树表达导航和显式选权，API类型权限表达通用API短码；ApiResource独立表达方法/路径；多对多映射允许一个动作由多个有效权限覆盖。部门树表达人员范围和部门角色适用范围；所属角色不隐式给部门员工授权，仍需明确user_roles关联。五角色是首次清单，不在数据库设计中固化为管理员类型。

启停仅改变有效性，不物理删除用户、角色及历史关联。部门提供删除空节点接口并保留内部来源墓碑，其他实体没有删除接口；外键不设置级联删除，避免共享授权及日志来源被误清理。完整K、真实根和子集校验由业务事务实现，不能伪称SQL单行约束已覆盖跨对象规则。

后续双实现沿同一概念/关系模型，领域聚合不与每张表一一对应。真正新增需求须先更新模型及评审。若未来需要保留已有部署数据，再另行设计迁移；当前首次SQL不承担旧库升级，不用DDL自动更新旧业务库。

Permission.type采用MENU/BUTTON/API。API项用于必须映射真实接口的后端短码，缺少映射或任一映射失效即不导出；类型不改变六实体关系。两套首次SQL针对新库，不交付升级脚本。课程业务审计由独立本地依赖保存，不混入authorization_logs。

鉴权运行支撑表authorization_logs新增可空client_ip VARCHAR(45)，记录经服务器核实的IPv4/IPv6。该字段不改变六核心对象及核心Chen ER关系；字段传递见后端设计，首次SQL仍仅面向新库，不自动改写旧表或补造历史日志。
