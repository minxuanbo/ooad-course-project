# UML图件来源与复现

本目录图件只表达当前结构化实现CoreAuthService.require，依据T12基线a79b668的实际代码，不表示DDD已实现。类图截取相关字段/方法，顺序图省略简单get访问器及返回对象构造；Bearer内部异常沿当前代码在其生命线内部产生，公开方法未捕获时直接回复caller。

采用用户提供的uml-class-sequence-diagrams skill检查Service居中、字段关联/局部依赖、静态MetaClass、每次独立执行条及异常回复。该skill不用于ER图；既有Chen ER保持原件及评审路径。

脚本为[scripts/build-current-uml.mjs](../../scripts/build-current-uml.mjs)，使用Node及sharp渲染SVG/PNG。使用已有工具环境或在仓库外安装sharp，不往生产前端添加绘图依赖；可通过UML_SHARP_MODULE指向其模块路径，随后在仓库根运行：

```text
node scripts/build-current-uml.mjs
```

脚本检查源码关键操作、类框无重叠、依赖不穿第三方类框以及同步消息进入执行条顶边、回复/异常两端处于执行状态。生成后仍须打开两张PNG检查文字、留白、控制框与箭头；自动检查不等于完整UML语义或视觉验收。图件是设计插图，不是测试执行报告。
