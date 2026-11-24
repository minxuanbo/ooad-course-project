# 原始资料与首次初始化设计

本文说明新项目数据如何建立与核对，两种策略分别执行下述规则，使用同一来源、表结构和回执格式。本地命令见[后端说明](../backend/README.md#3-新项目本地初始化与运行)。初始化采用全新库首次SQL，原Excel保留原件；历史执行结果另记于验证记录。

## 1. 输入与来源

| 输入 | 唯一用途 | 不作为来源的内容 |
| --- | --- | --- |
| 员工表10000行/六列 | 姓名、工号、组织路径、岗位、手机号及行号 | 口令、启用状态、角色分配不是原表数据 |
| 部门表111节点 | 根、父子关系与组织来源 | 负责人姓名不直接授予角色或管理权限 |
| 权限清单 | 五类角色的业务参考、系统/模块/功能原资料 | 不含HTTP模板，不把其他业务“系统管理后台”矩阵当作本RBAC管理端 |
| 部署秘密配置 | 员工统一初始口令、独立admin口令，可选账号名 | 不从旧库复用哈希/状态，不写入Git或日志 |
| 已确认的首次授权规则 | 将核实工号分到五角色 | 后续运行不再按岗位动态授权 |

原件摘要、真实行数、A1声明范围及合并单元格注意事项见[需求来源](requirements/sources.md)。读取必须按实际单元格和合并区域恢复数据，不能依据声明A1截断。姓名不唯一，按工号和核实部门定位人员。

## 2. 来源到目标

唯一根为总公司。仅将原“公司领导”节点并入总公司，其10人直接归根；其他节点及员工路径保持原意。原件不改：111组织来源→110目标部门；10000员工+独立admin→10001账号；根直属10领导+admin共11人。111来源节点可以映射同一目标根，不因此伪造另一个部门。

users.id只负责新库关联；username来自“用户名”即工号，phone来自“电话号码”，均按字符串保存。工号必填唯一、手机号可空且非空值唯一；导入另校验跨用户工号/手机号碰撞，不能只依赖两列各自唯一索引。原表无手机号的未来场景仍可用工号登录。

## 3. 阶段与事务

| 阶段 | 写入 | 核对/失败行为 |
| --- | --- | --- |
| 显式建库/结构 | DBA选择rbac_core，运行两份正式SQL | 新库隔离、utf8mb4_0900_bin；JPA validate拒绝错结构，应用不自动建旧表 |
| 资料/账号导入 | 110部门、10001启用账号、111组织/10000员工/admin来源记录 | 原件摘要、行号、六列、唯一性、树无环连通及全量归属；任何失败整批回滚 |
| 授权预检 | 仅在忽略目录输出新的CSV清单 | 预览不写角色/关联；不得覆盖已有清单或提交万人资料 |
| 实际首次授权 | 本系统目录、五角色、10001首次关联、执行回执 | 整批Service事务与根锁、完整K；失败不留下半套目录/回执 |
| 产品启动 | 不导入、不重授、不建旧表 | 默认RbacApplication，读取现有资料和最新授权 |

初始员工全部启用，不等于可登录管理端。每个账号独立BCrypt盐，员工和admin的初始口令分别配置；初始配置须非空且不超过BCrypt支持的72个UTF-8字节。admin默认账号名可配置，身份仍是普通User，不增加管理员类或字段。

## 4. 五角色首次清单

| 角色 | 已确认分类 | 人数 | 初始RBAC能力 |
| --- | --- | ---: | --- |
| 系统管理员 | 独立admin | 1 | 本系统全部维护及日志 |
| 公司领导 | 总公司/分公司直属领导 | 64 | 范围总览、人员查询/角色分配/停用/调动及部门增删改名，均受本人子树与级别保护 |
| 部门经理 | 部长、副部长 | 106 | 同上 |
| 项目经理 | 项目经理、项目副经理 | 72 | 同上 |
| 普通员工 | 其余核实人员 | 9758 | 不含RBAC管理操作，拒绝管理端登录 |

三管理角色不含角色定义写入、资源配置或日志查询，增加角色及已选权限读取；局部仅可见其子树部门角色。总公司领导因归根具有全组织人员范围，不因此变成完整维护者。首次分类按已确认来源/规则生成可复核工号清单；未明确的岗位不猜测提权，后续调动不自动按岗位换角色。

初次五角色type=SYSTEM_BUILTIN、management_level=5/4/3/2/1。组织及本RBAC首次授权仍与业务目录分开；另显式执行scripts/initialize_business_catalog.py，读取原权限表登记18个PENDING系统及原表五角色矩阵。首次SQL直接含组织type/deleted及同级名称约束；全新部署不执行升级脚本。

## 5. 重复执行与恢复

已有有效来源及回执时按稳定编码/来源及原父ID核对，不重置口令、启用状态、部门改名、新增/删除节点、角色或资源调整；初始人数是首次核对值，不是后续强制恢复目标。原件变化、来源缺失/损坏、已有无来源数据或admin来源不一致时停止，不能自动拼接旧数据。

重复导入还要求现存账号 ID 集合与 `EMPLOYEE`、`BOOTSTRAP` 来源所关联的账号 ID 集合完全相等。仅核对来源条数或逐条原员工均存在不足以证明来源完整；额外的无来源账号返回 `SOURCE_MAPPING_INCOMPLETE`，不删除、认领或重置账号。工号/手机号碰撞仍先返回既有 `IDENTIFIER_CONFLICT`。此检查针对账号，不禁止运行期合法新增部门，也不恢复已删除部门的墓碑。

导入失败的整批事务可核对后重试；数据库结构冲突须备份并明确迁移，CREATE TABLE IF NOT EXISTS不代表升级能力。旧业务库、历史日志及原Excel保留，不复用旧user_id，也不要求把旧日志整迁新库。验收回放使用私有本机实例，生产部署使用显式已备份目标，二者不混用。

## 6. 显式课程首次接入

默认万人导入及五角色首次授权只登记RBAC。18系统由独立business-catalog-v1回执配置；课程由独立catalog/initialization配置及脚本接入，产品启动或身份开关不自动批量授予教师、学生或管理员角色。

完成本章步骤后，总公司本部领导、职能部门/项目员工首次为teacher，分公司及下属为student；合格根直属有效五级管理员兼course-admin并建立课程教师绑定。当前原件核对191教师、9810学生及1名兼任管理员；不按首次人数恢复运行期授权。

本机使用initialize_course_integration.py，新服务器首发使用initialize_deployment.py；两库提交顺序、预览、回执及中断恢复统一见[课程首次接入](course-integration.md#4-首次身份分配与恢复)。旧32人夹具仅用于隔离回归，不覆盖原库；老师Flyway原样使用，不交付升级SQL。

## 7. 权限中文展示名称

name用于人员理解，code保持稳定，不能按中文名称鉴权。两套首次授权共用RbacCatalog.displayNames；课程名称来自独立接入目录，不在Vue建立翻译白名单。

| 稳定编码 | 中文名称 |
| --- | --- |
| rbac.login | 登录RBAC |
| rbac.systems.create | 新增受管应用 |
| rbac.systems.page.read | 查询受管应用 |
| rbac.systems.write | 维护应用资料与接入 |
| rbac.systems.remove | 移除受管应用 |
| rbac.overview.read | 查看总览 |
| rbac.departments.read | 查看部门目录 |
| rbac.departments.write | 修改部门名称 |
| rbac.departments.create | 新增下级部门 |
| rbac.departments.delete | 删除空部门 |
| rbac.departments.tree.read | 查看部门树 |
| rbac.users.read | 查询人员 |
| rbac.users.roles.read | 查看人员角色 |
| rbac.users.roles.candidates.read | 查询可授予角色 |
| rbac.users.roles.write | 分配人员角色 |
| rbac.users.disable | 停用人员 |
| rbac.users.department.write | 调动部门归属 |
| rbac.systems.read | 查看系统目录 |
| rbac.resources.read | 查看权限树 |
| rbac.apis.read | 查询API资源 |
| rbac.resources.apis.read | 查看API映射 |
| rbac.resources.write | 维护权限属性 |
| rbac.apis.write | 启停API资源 |
| rbac.resources.apis.write | 配置API映射 |
| rbac.roles.read | 查询角色 |
| rbac.roles.permissions.read | 查看角色权限 |
| rbac.roles.create | 新建角色 |
| rbac.roles.write | 修改角色 |
| rbac.roles.permissions.write | 配置角色权限 |
| rbac.logs.read | 查询鉴权日志 |

默认30个BUTTON分别映射实际API，7个MENU显式映射本页读取API；rbac.login独立无父项。管理员初始化具备全部项，三种人员管理角色增加登录资格，普通员工仍无RBAC登录权限。已有来源回执不会因重跑自动补授新目录；既有环境的兼容处理须在另行授权发布时备份并核对，不由产品启动偷偷重授。

## 8. 业务目录与角色来源

`scripts/initialize_business_catalog.py`默认只读预览，`--apply`显式发布。它复用只读Excel提取器，登记APP_01—APP_18，保留原表摘要、原单元格、五列原始矩阵及规范化记录到bootstrap_receipts.source_json。115个模块MENU、359个功能MENU及各显式动作BUTTON按原表展开；有动作的角色显式授予相应展示菜单，运行期不继承父权限。“全部”不成为通配符，“查看本人/编辑本人”保留，不凭文字编造对象级接口。

脚本复用现存五基础角色，只追加该目录关系，初始级别及已有RBAC/课程权不变。相同manifest回执直接跳过，不恢复人工撤权、改名或停用；来源变化、缺失基础角色、已有目录而无回执均拒绝。回执与目录同事务提交。18项默认PENDING，接入真实接口、操作目录及对象规则并独立验收后才可配置CONNECTED；应用管理PATCH可维护接入状态，标记CONNECTED前仍须完成接口和对象规则接入验证。

课程首次配置将3角色标为APPLICATION_PRESET并关联来源ApplicationSystem，无管理级别；五基础角色SYSTEM_BUILTIN无所属部门/应用。新建DEPARTMENT_CUSTOM必须有效ownerDepartmentId，GLOBAL_CUSTOM保留旧全局角色语义，不能根据名称猜分类。定义、选权与启停由管理员维护；领导只分配/撤回，不在初始化时任意认领既有角色。

部署离线首次阶段由deploy/initialize_deployment.py分别执行业务目录及课程初始化，镜像包含原表只读副本。新字段仅交付首次SQL；既有部署库在授权发布时先备份，另建全新结构副本并核对原账号、口令、授权及日志，再停写切换；原库保留。产品启动和CREATE IF NOT EXISTS不能代替数据兼容处理。
