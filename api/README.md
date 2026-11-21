# RBAC API文档

| 文件 | 职责 |
| --- | --- |
| [openapi.yaml](openapi.yaml) | OpenAPI3.1主文件；37个操作、31个路径、71个Schema |
| [openapi.json](openapi.json) | 同一规范的JSON派生文件，不独立手改 |
| [业务约束](../docs/api-contract.md) | 共享授权、范围、错误顺序、完整K、原子性及日志快照 |
| [页面对照](../docs/api-map.md) | 29项管理操作及8项统一身份鉴权操作索引 |

参照老师项目分开机器契约与业务说明，不移植课程业务处理器；仅适配其既有身份协议。规范覆盖structured/ddd两种实现共同遵守的HTTP行为；x-initial-permission-code仅说明初始映射，不要求固定权限code才能调用API；x-maintenance-member标出17项K。

YAML为唯一机器规范。使用PyYAML时可在仓库根重新导出JSON：

```bash
python -c "import json,pathlib,yaml; p=pathlib.Path('api'); d=yaml.safe_load((p/'openapi.yaml').read_text(encoding='utf-8')); (p/'openapi.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')"
```

导出后核对YAML/JSON语义相等、37项操作、17项K、所有引用可解析，使用OpenAPI3.1校验器检查。类型/长度静态约束不能替代跨字段、归一化、状态、数据库、授权或范围验证。

Apifox同步须用户明确确认目标项目和覆盖范围，不能沿用老师项目ID或凭据。用户已授权同步本项目9042431；当前1.5.0将管理端与外部应用全部统一为JWT，37操作、31路径、71模型不变，实际回读证据见[R33验证](../docs/history/verification/jwt-design-cleanup.md)。R32的1.4.0成绩保留为历史。

阶段4新增部门目录/增删改名及范围总览5操作，扩展身份实际路由、角色级别、人员职位/角色摘要和部门人数。默认首次授权仅RBAC，维护目录按已登记应用读取；原型18业务系统不进入首次授权。写入Apifox依当次明确授权执行。

公共Error.code当前22项，含R32新增的身份用户、部门及API操作不存在3个稳定404编码；状态与语义见[API约束](../docs/api-contract.md)。权限明细接口仍保留，正式导航只读取GET /me提供的实际路由与范围，不因减少重复请求删除公共API。
