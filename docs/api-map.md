# 页面、用例与API对照

接口字段、响应和错误的规范正文为[API契约](api-contract.md)；本表只建立索引，不维护第二份参数定义。当前24个操作：20个受控管理API及4个基础登录/本人操作。所有管理请求仍按服务端实际方法/模板鉴权，表中能力名不替代实际授权。

| 页面/用例 | 操作 | 范围/备注 |
| --- | --- | --- |
| 登录/退出 | POST /api/auth/login；POST /api/auth/logout | 单account入口；退出不新增会话落库 |
| 共用外壳 | GET /api/me；GET /api/me/permissions | 有效身份；不要求人员查询权限 |
| 部门人员 | GET /api/departments/tree；GET /api/users | 本部门及后代；人员唯一分页入口 |
| 人员角色 | GET /api/users/{id}/roles；GET /api/users/{id}/assignable-roles；PUT /api/users/{id}/roles | 当前、候选、保存分别检查；范围及可授予权限子集 |
| 人员停用/调动 | PATCH /api/users/{id}/status；PUT /api/users/{id}/department | 仅停用；调动双范围，保持同一用户 |
| 角色管理 | GET /api/roles；POST /api/roles；PATCH /api/roles/{id} | 共同根及具体API；创建201，编码稳定 |
| 角色选权 | GET /api/roles/{id}/permissions；PUT /api/roles/{id}/permissions | 完整已选ids及整体替换；树查询复用资源目录 |
| 资源目录 | GET /api/systems；GET /api/permissions/tree；GET /api/api-resources | 共同根、本系统，API候选分页 |
| 资源维护 | PATCH /api/permissions/{id}；PATCH /api/api-resources/{id} | 必要字段、树约束及启停 |
| API映射 | GET /api/permissions/{id}/apis；PUT /api/permissions/{id}/apis | 完整映射与整体替换，同系统 |
| 鉴权日志 | GET /api/authorization-logs | 日志API+共同根；快照游标，不要求完整K |

初始化命令不属于HTTP API。旧/users/page、/users/filters、业务应用目录、模拟/authorize、交接、独立management权限、归档/导出不作为当前正式入口。课程系统接口未在本次启动，不能把测试/mock/foreign-resource加入正式目录。

原型源码仍在[prototype](rbac-rectification/prototype/index.html)，真实前端设计见[frontend-design](frontend-design.md)。本次规范整理保留原型路径及既有评审链接，避免历史PR和Issue中的引用失效。
