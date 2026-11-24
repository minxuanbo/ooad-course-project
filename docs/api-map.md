# 页面与API对照

> 接口字段与响应的唯一机器规范为[OpenAPI](../api/openapi.yaml)，JSON为同源派生；[业务约束](api-contract.md)解释鉴权、范围与原子性。此表索引33项RBAC管理操作；下文补充8项完整通用身份操作。

| 页面/操作组 | 操作 | 用途 | 请求模型 | 成功data |
| --- | --- | --- | --- | --- |
| 认证与本人 | `POST /api/auth/login` | 工号或手机号登录 | LoginDTO | LoginVO |
| 认证与本人 | `POST /api/auth/logout` | 退出当前身份 | 路径/query/无body，见规范 | null |
| 认证与本人 | `GET /api/me` | 本人身份 | 路径/query/无body，见规范 | MeVO |
| 认证与本人 | `GET /api/me/permissions` | 本人本系统有效权限明细；保留契约，正式导航不再额外调用 | 路径/query/无body，见规范 | 数组 |
| 范围总览 | `GET /api/overview` | 人员启停及直接下级分布 | 无body | OverviewVO |
| 部门管理 | `GET /api/departments` | 完整范围层级及人数 | 无body | DepartmentVO数组 |
| 部门管理 | `POST /api/departments` | 新增合法下级 | DepartmentCreateDTO | DepartmentVO |
| 部门管理 | `PATCH /api/departments/{id}` | 仅改名 | DepartmentRenameDTO | DepartmentVO |
| 部门管理 | `DELETE /api/departments/{id}` | 删除空且非根节点 | 无body | null |
| 部门与人员 | `GET /api/departments/tree` | 可管理部门树 | 路径/query/无body，见规范 | 数组 |
| 部门与人员、部门员工/角色成员弹窗 | `GET /api/users` | departmentScope直属/子树、roleId关联与搜索状态组合分页 | 路径/query/无body，见规范 | UserPageVO |
| 人员授权 | `GET /api/users/{id}/roles` | 当前完整角色 | 路径/query/无body，见规范 | UserRolesVO |
| 人员授权 | `GET /api/users/{id}/assignable-roles` | 受限候选角色分页 | 路径/query/无body，见规范 | AssignableRolePageVO |
| 人员授权 | `PUT /api/users/{id}/roles` | 整体替换人员角色 | IdsDTO | UserWithRolesVO |
| 人员授权 | `PATCH /api/users/{id}/status` | 停用人员 | UserDisableDTO | UserWithRolesVO |
| 人员授权 | `PUT /api/users/{id}/department` | 跨部门调动 | UserDepartmentDTO | UserWithRolesVO |
| 角色与权限 | `GET /api/roles` | 角色组合查询 | 路径/query/无body，见规范 | RolePageVO |
| 角色与权限 | `POST /api/roles` | 新建空权限角色 | RoleCreateDTO | RoleVO |
| 角色与权限 | `PATCH /api/roles/{id}` | 改名或启停角色 | RolePatchDTO | RoleVO |
| 角色与权限 | `GET /api/roles/{id}/permissions` | 完整直接权限集合 | 路径/query/无body，见规范 | RolePermissionsVO |
| 角色与权限 | `PUT /api/roles/{id}/permissions` | 整体替换角色权限 | IdsDTO | RolePermissionsVO |
| 应用管理 | `GET /api/systems/page` | 维护列表分页搜索和状态筛选 | SystemQueryDTO | SystemPageVO |
| 应用管理 | `POST /api/systems` | 登记待接入系统 | SystemCreateDTO | SystemVO |
| 应用管理 | `PATCH /api/systems/{id}` | 名称、接入与启停状态 | SystemPatchDTO | SystemVO |
| 应用管理 | `DELETE /api/systems/{id}` | 逻辑移除，保留历史 | 无body | SystemVO |
| 资源与映射 | `GET /api/systems` | 已登记系统目录 | 路径/query/无body，见规范 | 数组 |
| 资源与映射 | `GET /api/permissions/tree` | 权限树目录 | 路径/query/无body，见规范 | 数组 |
| 资源与映射 | `GET /api/api-resources` | API候选分页 | 路径/query/无body，见规范 | ApiPageVO |
| 资源与映射 | `PATCH /api/permissions/{id}` | 维护权限父级类型状态 | PermissionPatchDTO | ResourcePermissionVO |
| 资源与映射 | `PATCH /api/api-resources/{id}` | 启停API资源 | ApiPatchDTO | ApiVO |
| 资源与映射 | `GET /api/permissions/{id}/apis` | 完整已保存API映射 | 路径/query/无body，见规范 | PermissionApisVO |
| 资源与映射 | `PUT /api/permissions/{id}/apis` | 整体替换权限API映射 | IdsDTO | PermissionApisVO |
| 鉴权日志 | `GET /api/authorization-logs` | 最小鉴权日志快照查询 | 路径/query/无body，见规范 | LogPageVO |

前端目标页面与状态见[前端设计](frontend-design.md)。获批离线原型见[prototype](../prototype/index.html)，正式Vue初始展示数据库登记的20项应用，后续可通过应用管理扩展，包含RBAC、课程及18项待接入业务目录；待接入项不提供真实业务通行。课程业务界面继续使用老师原始前端。原型不作为真实HTTP或完整界面验收。

初始化走离线命令，不新增HTTP初始化入口。课程接入见[专门记录](course-integration.md)，RBAC不承载老师的课程业务路径、模拟外系统路径或旧归档接口。

| 通用应用身份（课程既有客户端使用） | 操作 | 成功原始响应 |
| --- | --- | --- |
| 登录 | `POST /auth/login` | ApplicationLoginVO |
| 每次Bearer请求 | `GET /auth/me` | ApplicationIdentityVO |
| 菜单/按钮检查 | `GET /auth/permissions/{permission}` | ApplicationPermissionVO |
| 添加教师时身份查询 | `GET /auth/users/{userId}` | ApplicationUserVO |
| 退出外部身份 | `POST /auth/logout` | 204无body |
| 逐层身份部门 | `GET /auth/departments` | ApplicationDepartmentPageVO |
| 部门直属身份用户 | `GET /auth/departments/{departmentId}/users` | ApplicationDepartmentUserPageVO |
| 全局API门禁查询 | `GET /auth/api-permissions/{operationId}` | ApplicationApiAuthorityVO |

八项仅显式启用时提供，由RBAC两策略实现，不属于管理API或K；课程业务处理器仍在老师工程。
