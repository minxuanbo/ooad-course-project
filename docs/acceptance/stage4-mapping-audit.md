# 阶段4映射核对记录

本记录逐处区分课程CloneFactory负责的同名同类型字段与需要显式处理的关系、集合类型转换和业务聚合；不是仅根据“使用过skill”认定不存在遗漏。

| 映射链 | 实际方式 | 保留显式处理的理由 |
| --- | --- | --- |
| DepartmentCreateDTO→CreateDepartmentCommand；DepartmentRenameDTO→RenameDepartmentCommand | DTO @CopyTo及Controller CloneFactory.copy | DTO先trim再校验；禁止接收父级更新等未知字段 |
| Create/RenameDepartmentCommand→DepartmentEntity | Entity @CopyNotNullFrom及Service copyNotNull | 新编码由Service生成，父级经范围/类型校验后显式关联；不复制客户端关系对象 |
| DepartmentEntity→DepartmentResult→DepartmentVO | Entity @CopyTo、VO @CopyFrom及两次copy | parentId为关系/范围投影，人数和操作能力由服务计算 |
| UserEntity→UserResult→UserVO | Entity @CopyTo、VO @CopyFrom及copy | position共同字段复制；部门名称/ID由关系派生；角色集合元素变换逐个copy RoleResult/RoleVO |
| RoleEntity→RoleResult→RoleVO | 同名字段及managementLevel由真实处理器copy | primitive启用状态→Boolean在Entity结果层显式处理 |
| UserEntity→IdentityResult→IdentityVO | 同名身份字段copy，Controller继续copy | routes、globalScope和managementLevel来自最新授权/范围，departmentName从关系派生 |
| OverviewResult→OverviewVO | @CopyFrom及Controller copy | 分布元素DepartmentResult→DepartmentVO逐个copy；OverviewResult本身是数据库统计与多个来源的聚合，无单一同名源对象 |
| 既有用户/角色/资源/日志DTO、Command、Result、VO | 逐个Controller和Service核对其copy/copyNotNull入口 | ids整体集合、分页元素类型转换、API字段别名、Instant转换及父级三态等保留显式处理 |

创建User/Role/Permission等实体的必要构造器负责非空、稳定编码或关系不变量，不为“出现copy调用”而开放本应不可修改的编码setter；没有用反射、ObjectMapper或BeanUtils替代映射。聚合结果构造器、枚举转换、集合元素类型转换不属于遗漏同名标量映射。

修改注解后运行clean构建，核对target/generated-sources/annotations中的真实CloneFactory：新部门DTO/Command、copyNotNull实体映射、职位、角色级别、身份路由、部门人数及VO均有实际生成赋值。生成代码不提交。真实HTTP回归同时验证部门名称/类型、职位/角色状态、级别和本人路由，避免只凭源码注解宣称字段正确。
