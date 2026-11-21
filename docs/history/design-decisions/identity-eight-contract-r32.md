# 完整统一身份鉴权设计（R32）

> 历史快照：保留R32及以前的规则和证据；当前设计从[文档入口](../../README.md)阅读。

本设计补齐老师“统一身份鉴权”的8项协议。RBAC继续是通用系统，Teacher/Student及课程对象关系只在课程工程；“统一身份审计”2项不进入本轮实现。

## 1. 用例与共享边界

ApplicationIdentityController负责DTO校验、协议字段映射与无data包装响应。ApplicationIdentityUseCase由StructuredApplicationIdentityService和DddApplicationIdentityService分别实现。外部目录使用独立ApplicationDirectoryQueryDTO及page/pageSize，不改变内部管理端page/size。

两策略独立读取并判断当前授权、组织范围。structured读取JPA权限与StructuredDepartmentScopeService；DDD使用AuthorizationRepository、OrganizationRepository和ApplicationAuthorizationPolicy/OrganizationPolicy。目录的数据库计数、直接父级筛选、人员最小字段投影由共享IdentityDirectoryGateway技术适配器执行，不放入领域仓储，不依赖任何应用实现。

## 2. JWT与退出

BearerIdentityStore为管理端保留随机Bearer，为外部应用签发JWT，并保留会话索引用于即时撤销。ApplicationJwtCodec使用Spring Security OAuth2 JOSE及Nimbus，固定HS256，仅签身份和单一受众；校验签名、签发者、主体、受众及严格到期边界。JWT不缓存角色权限，业务访问仍重读数据库。JWT的aud按标准可编码为单值字符串或数组，均表示同一单应用受众。

IDENTITY_JWT_SECRET可提供至少256位的Base64秘密，不能打印或提交。未配置时生成本进程随机秘密，没有写死默认值。IDENTITY_JWT_ISSUER配置签发者。当前身份索引仍是进程内存；重启失效，不宣称已支持多实例共享会话。POST /auth/logout验证当前外部身份并只撤当前令牌；即使权限已撤回也可退出，管理端令牌不可借此入口被撤销。

## 3. 组织与人员目录

目录读取先核本应用对对应API的直接有效映射，再计算当前人员部门子树。根省略外部父引用，hasChildren只统计有效直接子级。关键词按文本转义百分号、下划线及转义符；数据库先筛选计数再分页，不在Java下载万人后截页。列表与total使用同一REPEATABLE_READ快照。

根范围可区分未知部门404，局部人员访问范围外目标统一403；不会借404探测其他部门。人员目录只返回直属人员五个字段；单人查询返回userId/no/name。当前RBAC用户标识为正安全整数字符串；其他合法字符串在授权后返回IDENTITY_USER_NOT_FOUND，超界数字400。

## 4. API操作接入元数据

IDENTITY_OPERATIONS_FILE指向显式资源（如file:///C:/.../integrations/course/operations.json）。ConfiguredApiOperationCatalog只读取通用元数据，键为system加operationId；数据库应用目录仍决定是否登记/启用，元数据不充当应用准入白名单。

每项包含system、operationId、method、path及显式requiresPermission。permissionRoles可指定混合入口中需要全局门禁的配置角色；未匹配者只通过全局查询，课程Service仍必须校验业务对象。需要门禁时按当前完整有效权限的真实API方法/模板映射判断；没有元数据的操作404，损坏、重复配置使启动失败。没有配文件时不猜测任何操作。

课程接入元数据在integrations/course/operations.json，权限/路由目录仍在catalog.json。新增目录权限只授予显式课程管理员及已有合格RBAC管理员角色，不提升其他人员管理级别。此变更不增加表或修改首次SQL，不交付升级SQL。

## 5. 验证与交付

回归覆盖八项协议、签名/到期、退出隔离、分页/范围/最小字段、操作未知与即时撤权；两策略须分别运行真实MySQL回归。独立JAR、课程真实HTTP和浏览器、图源以及Apifox回读另行记录，不能沿用旧版本成绩。
