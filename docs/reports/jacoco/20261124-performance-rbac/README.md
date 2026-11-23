# RBAC性能优化后双策略JaCoCo报告（2026-11-24）

两策略各330项真实隔离MySQL回归，失败、错误、跳过均为0；347份生产class摘要一致。未排除业务类或降低门槛，原Maven六项零遗漏检查通过。测试源码提交为`e51af4f30655c1aaf82076d8a26b9a27a7f50f69`。

| 指标 | 已覆盖 | 遗漏 | 覆盖率 |
| --- | ---: | ---: | ---: |
| INSTRUCTION | 24208 | 0 | 100% |
| BRANCH | 1948 | 0 | 100% |
| LINE | 4862 | 0 | 100% |
| COMPLEXITY | 2021 | 0 | 100% |
| METHOD | 1040 | 0 | 100% |
| CLASS | 264 | 0 | 100% |

[完整报告ZIP](rbac-jacoco-report.zip)包含可离线打开的html/index.html、XML、摘要和验证元数据；[manifest](manifest.json)记录逐文件SHA-256。机器会话名已替换，原exec、class、环境XML、数据库和凭据未发布。

这是测试执行覆盖率；加载性能须另看[本轮实测](../../../history/verification/authorization-loading-performance-20261124.md)，不把100%等同于响应速度或正式流量覆盖率。[复现方法](../../../../backend/README.md#7-双策略jacoco复现)适用于本报告。
