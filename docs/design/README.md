# 当前实现图资源

所有当前图统一展示在[后端设计第17章](../backend-design.md#17-结构化与ddd-uml)：包图2组、两套类图16对、差异顺序图24对，共82组同源SVG/PNG。37项HTTP操作和2类离线入口的对应关系见该章17.4及[覆盖数据](ddd-diagrams.json)。

| 来源/脚本 | 生成范围 |
| --- | --- |
| [ddd-diagrams.json](ddd-diagrams.json)、[build-ddd-design.mjs](../../scripts/build-ddd-design.mjs) | 两套包图、15张DDD模块类图、48张差异顺序图 |
| [build-structured-design.mjs](../../scripts/build-structured-design.mjs) | 从实际Java字段和方法生成对应15张structured模块类图 |
| [build-identity-design.py](../../scripts/build-identity-design.py) | 完整通用身份模块的两张类图，同时交PlantUML源 |
| [uml-svg.mjs](../../scripts/uml-svg.mjs) | 共用SVG图元、文字宽度、类框/连线几何检查及PNG渲染 |

```powershell
# 使用Node、sharp；若工具运行时未自动定位sharp，显式设UML_SHARP_MODULE。
node scripts/build-ddd-design.mjs
node scripts/build-structured-design.mjs
# Java/PlantUML路径由本机工具配置提供，不加入生产依赖：
python scripts/build-identity-design.py --java <java.exe绝对路径> --plantuml <plantuml.jar绝对路径>
```

生成器检查实际类型、字段和接收方法、类框重叠、连线穿框、共同流程禁入、两套消息是否只有改名、执行条与循环边界。清单见[主清单](ddd-generated-manifest.json)和[structured清单](structured-generated-manifest.json)。生成后仍须打开PNG视觉核对，自动通过不等于布局或语义已验证。

共同JWT/HTTP/日志/锁和提交不绘制两份顺序图；相同分页或集合读取模式在覆盖矩阵明确复用。旧局部身份类图、单侧应用投影/异常和重复目录图移入[历史图件](../history/design-assets/through-r32/)，退出当前设计。历史R31/R32说明在history/design-decisions保留，只作为来源追溯。
