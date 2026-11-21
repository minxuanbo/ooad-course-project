# 课程首次身份接入设计（R31）

本设计实现用户2026-11-21转达并确认的首次分配规则。RBAC默认万人初始化继续只登记自身；课程接入由独立配置和脚本显式执行。它不是HTTP接口、数据库升级脚本，也不在登录或启动时运行。

## 1. 范围与身份

| 来源与组织事实 | 初始课程身份 | RBAC管理能力 |
| --- | --- | --- |
| 总公司本部领导及普通员工，含本部职能部门和项目 | teacher | 保持原五角色能力 |
| 分公司及其下属的领导、普通员工 | student | 保持原五角色能力 |
| 启用、可信根直属、有效基础级别5的管理员 | teacher及course-admin | 保持管理员资格规则 |

总公司根的整棵子树包含分公司，不能把“根的后代”直接当作本部。脚本沿部门父链寻找COMPANY祖先，同时核对员工来源六列中的分公司字段；来源不完整、树矛盾或初始管理员失格时拒绝。独立admin沿BOOTSTRAP来源识别，不凭账号字符串猜测。

## 2. 配置与职责

- [initialization.json](../../integrations/course/initialization.json)：本部来源键、应用名称、三个角色编码、登录权限、菜单编码、独立seedKey。
- [catalog.json](../../integrations/course/catalog.json)：老师现有权限短码、中文名及必要路由。
- [initialize_course_integration.py](../../scripts/initialize_course_integration.py)：来源核对、只读预览、首次写入、回执检查。
- [verify_course_initialization.py](../../scripts/verify_course_initialization.py)：独立本地环境的全量关联核对和真实HTTP联调；有演示业务写入，不指向已有业务环境。

Teacher/Student及其user_id绑定只存在于课程库。RBAC仍仅持有通用应用、角色、权限、API及关联。三个课程角色management_level为空，不提升人员管理级别。teacher/student授LOGIN；course-admin授课程维护权限；有效五级管理员角色同时取得完整课程权限，以满足后续授撤角色时的完整权限子集条件。

## 3. 执行与事务

执行前完成RBAC资料导入、五角色授权和课程原Flyway建表；使用独立新本机库，在对外开放业务前串行完成首次接入。客户端必须显式指定loopback及端口，报告必须为.local下新文件，默认只读，显式--apply才写入。

MySQL只加载指定defaults-file，禁用login-path，并在命令参数中固定已验证的127.0.0.1、端口、TCP协议及skip-force，避免其他配置组覆盖连接或在SQL失败后继续提交。INI解析错误仅返回固定错误码，不输出原始配置行。

1. 读取来源、组织、账号和管理员资格；生成稳定计划与摘要。
2. 课程库事务建立teacher/student绑定。已存在的匹配绑定保留，不更新姓名或业务属性；冲突身份、额外记录、已删除教师拒绝覆盖。
3. RBAC事务锁可信根，发布应用、角色、权限、API、人员关联及完成回执，一次提交。

两个数据库分别提交，不具有分布式原子性。课程提交后RBAC失败时，没有发布应用角色；核对后重跑会保留匹配绑定。首次接入要求维护窗口内串行执行，不提供运行期并发组织调整或多人并行初始化的协议。

## 4. 重复执行与后续变更

回执绑定配置、目录、不可变来源和账号ID全集。匹配回执立即跳过，不把运行期角色、权限、启停、显示名或部门调整还原。原件、配置或账号全集改变时拒绝自动覆盖；已有课程目录但没有本脚本回执时拒绝接管。因此不能把新脚本直接用于旧32人夹具迁移。

初始分配完成后，部门调动不会自动切换teacher/student；管理员增减也不会隐式同步course-admin。后续必须明确管理RBAC角色与课程本地绑定。该边界使“首次规则”和“运行期授权”彼此独立。

## 5. 双策略与图件影响

structured和DDD继续独立实现RBAC用例，共用相同数据库事实。此次只新增应用外部的初始化入口，没有修改Java应用服务、领域仓储或Controller，不增加HTTP操作，R31交付时OpenAPI为33项；当前R32扩充为37项，见[完整身份设计](identity-eight-contract.md)。现有Java包图、类图和差异顺序图的类、方法锚点不变；本设计补充初始化流程，不把Python脚本伪装成Java UML类。

实际人数、依赖来源、回归与界面证据见[本轮验证记录](../history/verification/course-initialization-r31.md)。
