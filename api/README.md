# RBAC API文档

| 文件 | 职责 |
| --- | --- |
| [openapi.yaml](openapi.yaml) | OpenAPI3.1主文件；45个操作、37个路径、94个Schema |
| [openapi.json](openapi.json) | 同一规范的JSON派生文件，不独立手改 |
| [业务约束](../docs/api-contract.md) | 共享授权、范围、错误顺序、完整K、原子性及日志快照 |
| [页面对照](../docs/api-contract.md#api-map) | 37项管理操作及8项统一身份鉴权操作索引 |

机器契约与业务说明分开维护；身份协议由RBAC实现，课程业务由课程系统实现。规范覆盖structured/ddd两种实现共同遵守的HTTP行为；x-initial-permission-code仅说明初始映射，不要求固定权限code才能调用API；x-maintenance-member标出17项K。

YAML为唯一机器规范。使用PyYAML时可在仓库根重新导出JSON：

```bash
python -c "import json,pathlib,yaml; p=pathlib.Path('api'); d=yaml.safe_load((p/'openapi.yaml').read_text(encoding='utf-8')); (p/'openapi.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')"
```

导出后核对YAML/JSON语义相等、45项操作、17项K、所有引用可解析，使用OpenAPI3.1校验器检查。类型/长度静态约束不能替代跨字段、归一化、状态、数据库、授权或范围验证。

Apifox目标为本项目9042431，写入须在用户明确授权范围内，写后回读。当前1.8.0为45操作/37路径/94模型，含4项向导操作；JWT、独立LOGIN、K17和角色来源范围保持一致。初始只配置RBAC及课程，未来新应用PENDING在完成接入前失败关闭。实际语义核对与部署状态见[验收入口](../docs/testing-acceptance.md)。

## Apifox真实服务器调试

选择“服务器验收（真实RBAC）”（环境ID50398304），Base URL为http://114.116.254.25:8088，默认服务及模块8887289均使用此地址。接口路径已包含/api或/auth，不再额外拼/api。环境原有参数保留，不保存共享明文密码、RBAC JWT或课程JWT；本地Mock/云端Mock仅用于显式示例，不能作为真实授权或数据库测试。

管理操作先调用/api/auth/login取得data.token，身份八项先调用/auth/login取得accessToken；两类JWT通过Bearer携带但受众不同，按各自鉴权配置填写，不混用。课程业务API仍在老师项目，本项目环境指向RBAC，不能改成课程80端口。日志systems目录、权限查询事件和clientIp随现行服务读取；18项PENDING应用只有计划授权，不允许外部登录通行。

同步OpenAPI时只导入契约资源，环境通过environment命令单独维护，避免每次导入相对servers地址生成重复环境。保留现有Mock环境不代表默认使用Mock；客户端选择环境属于个人界面状态，执行前核对环境名称和最终请求URL。

公共Error.code当前23项；状态与语义见[业务约束](../docs/api-contract.md)。实际导航只读取GET /me的当前路由与范围，权限明细接口仍保留。
