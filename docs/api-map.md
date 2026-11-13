# 页面与API对照

> 接口字段与响应的唯一机器规范为[OpenAPI](../api/openapi.yaml)，JSON为同源派生；[业务约束](api-contract.md)解释鉴权、范围与原子性。此表只作24项操作索引。

| 页面/操作组 | 操作 | 用途 | 请求模型 | 成功data |
| --- | --- | --- | --- | --- |
| 认证与本人 | `POST /api/auth/login` | 工号或手机号登录 | LoginDTO | LoginVO |
| 认证与本人 | `POST /api/auth/logout` | 退出当前身份 | 路径/query/无body，见规范 | null |
| 认证与本人 | `GET /api/me` | 本人身份 | 路径/query/无body，见规范 | MeVO |
| 认证与本人 | `GET /api/me/permissions` | 本人本系统有效权限 | 路径/query/无body，见规范 | 数组 |
| 部门与人员 | `GET /api/departments/tree` | 可管理部门树 | 路径/query/无body，见规范 | 数组 |
| 部门与人员 | `GET /api/users` | 人员组合查询与分页 | 路径/query/无body，见规范 | UserPageVO |
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
| 资源与映射 | `GET /api/systems` | 本轮系统目录 | 路径/query/无body，见规范 | 数组 |
| 资源与映射 | `GET /api/permissions/tree` | 权限树目录 | 路径/query/无body，见规范 | 数组 |
| 资源与映射 | `GET /api/api-resources` | API候选分页 | 路径/query/无body，见规范 | ApiPageVO |
| 资源与映射 | `PATCH /api/permissions/{id}` | 维护权限父级类型状态 | PermissionPatchDTO | ResourcePermissionVO |
| 资源与映射 | `PATCH /api/api-resources/{id}` | 启停API资源 | ApiPatchDTO | ApiVO |
| 资源与映射 | `GET /api/permissions/{id}/apis` | 完整已保存API映射 | 路径/query/无body，见规范 | PermissionApisVO |
| 资源与映射 | `PUT /api/permissions/{id}/apis` | 整体替换权限API映射 | IdsDTO | PermissionApisVO |
| 鉴权日志 | `GET /api/authorization-logs` | 最小鉴权日志快照查询 | 路径/query/无body，见规范 | LogPageVO |

前端目标页面与状态见[前端设计](frontend-design.md)。当前唯一交互原型见[prototype](../prototype/index.html)，替换旧四组原型并供小组审核；批准后先调整RBAC自身契约，再统一更新前后端，不实现其他系统的业务API。原型不作为真实HTTP或完整界面验收。

初始化走离线命令，不新增HTTP初始化入口。课程系统及T14/T15等待老师要求，本次不加入课程路径、模拟外系统路径或旧应用/交接/归档接口。
