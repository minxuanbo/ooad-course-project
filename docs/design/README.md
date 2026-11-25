# 当前实现图资源

所有当前图统一展示在[后端设计第17章](../backend-design.md#17-结构化与ddd-uml)：包图2组、两套类图17对、差异顺序图26对，共88组同源SVG/PNG。45项HTTP操作和2类离线入口的对应关系见该章17.4及[覆盖数据](ddd-diagrams.json)。

| 来源/脚本 | 生成范围 |
| --- | --- |
| [ddd-diagrams.json](ddd-diagrams.json)、[build-ddd-design.mjs](../../scripts/build-ddd-design.mjs) | 两张包图、16张DDD模块类图、两张通用应用身份类图、52张差异顺序图 |
| [build-structured-design.mjs](../../scripts/build-structured-design.mjs) | 从实际Java字段和方法生成对应16张structured模块类图 |
| [uml-svg.mjs](../../scripts/uml-svg.mjs) | 共用SVG图元、文字宽度、类框/连线几何检查及PNG渲染 |

```powershell
# 使用Node、sharp；若工具运行时未自动定位sharp，显式设UML_SHARP_MODULE。
node scripts/build-ddd-design.mjs
node scripts/build-structured-design.mjs
```

类图按项目指定的`uml-class-sequence-diagrams`绘制，以Service为中心，使用字段名标识关联，区分依赖与接口实现，并以下划线标记静态方法。顺序图校验接收方方法、对象和静态方法生命线、执行条定位及循环边界。`«create»`消息从创建者的执行条发出，落在新对象的生命线框。包图用UML文件夹符号和依赖箭头，[Chen ER](../database-design.md#1-chen-er概念模型)使用矩形、菱形、椭圆和基数。生成结果见[主清单](ddd-generated-manifest.json)和[structured清单](structured-generated-manifest.json)，生成后打开PNG复核布局。

共同JWT/HTTP/日志/锁和提交不绘制两份顺序图；相同分页或集合读取模式在覆盖矩阵明确复用。被现行图集替代的旧局部图及重复图已清理，原版本从Git历史恢复；不再维护另一套历史图集或重复设计说明。
