# RBAC设计图资源

正式包图、类图与差异顺序图统一编排在[后端设计第17章](../backend-design.md#17-结构化与ddd-uml)，不另设设计稿或审核图集。结构化图和DDD图均描述当前实现。

独立课程初始化的范围、配置、双库提交与幂等边界见[课程首次身份接入设计（R31）](course-initialization-r31.md)。它不改变现有Java类和HTTP接口。

## 编辑、生成和检查

设计数据源为[ddd-diagrams.json](ddd-diagrams.json)，唯一生成脚本为[scripts/build-ddd-design.mjs](../../scripts/build-ddd-design.mjs)，复用[scripts/uml-svg.mjs](../../scripts/uml-svg.mjs)。它们只生成文档，不进入后端或正式前端依赖。使用Node和sharp；可用UML_SHARP_MODULE指向仓库外已安装sharp，或使用含sharp的工具运行时。不要将sharp加到生产frontend/package.json。

```powershell
node scripts/build-ddd-design.mjs
# 仅需要重新核对现有结构化身份类图时：
node scripts/build-current-uml.mjs
```

旧脚本现只生成current-auth-class，不再产生完整身份顺序图。生成脚本校验两套源码的类与方法锚点、框无重叠/边不穿第三方框、共同步骤禁入、调用与执行条顶边、循环接收执行条完整性和文字宽度；生成清单见[ddd-generated-manifest.json](ddd-generated-manifest.json)。自动检查不等于视觉或业务语义通过，交付前必须实际打开PNG，历史设计记录见[设计阶段证据](../history/verification/ddd-design.md)。
