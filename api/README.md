# RBAC API文档

| 文件 | 职责 |
| --- | --- |
| [openapi.yaml](openapi.yaml) | OpenAPI3.1主文件；24个现有操作、20个路径、56个Schema，评审稿 |
| [openapi.json](openapi.json) | 同一规范的JSON派生文件，不独立手改 |
| [业务约束](../docs/api-contract.md) | 共享授权、范围、错误顺序、完整K、原子性及日志快照 |
| [页面对照](../docs/api-map.md) | 24项操作与目标页面/用例索引 |

参照老师项目分开机器契约与业务说明，不移植课程系统接口。规范覆盖当前结构化实现与未来DDD应共同遵守的HTTP行为；x-initial-permission-code仅说明初始映射，不要求固定权限code才能调用API；x-maintenance-member标出17项K。

YAML为唯一机器规范。使用PyYAML时可在仓库根重新导出JSON：

```bash
python -c "import json,pathlib,yaml; p=pathlib.Path('api'); d=yaml.safe_load((p/'openapi.yaml').read_text(encoding='utf-8')); (p/'openapi.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')"
```

导出后核对YAML/JSON语义相等、24项操作、17项K、所有引用可解析，使用OpenAPI3.1校验器检查。类型/长度静态约束不能替代跨字段、归一化、状态、数据库、授权或范围验证。

本次不修改服务、不上传Apifox、不改变契约。后续同步须用户明确确认目标项目和覆盖范围，不能沿用老师项目ID或凭据。
