# RBAC最终双策略JaCoCo报告

2026-11-24应用接入向导与两系统初始范围源码：两策略各365项隔离真实MySQL测试，失败、错误、跳过均0；418份生产class摘要一致。DDD与structured均clean完整365项，包含向导异常分类、真实存储失败回滚与双确认防线。原Maven六项零遗漏门槛通过，未排除业务类或降低门槛。

| 指标 | 已覆盖 | 遗漏 | 覆盖率 |
| --- | ---: | ---: | ---: |
| INSTRUCTION | 30486 | 0 | 100% |
| BRANCH | 2534 | 0 | 100% |
| LINE | 6019 | 0 | 100% |
| COMPLEXITY | 2528 | 0 | 100% |
| METHOD | 1254 | 0 | 100% |
| CLASS | 326 | 0 | 100% |

[完整报告](rbac-jacoco-report.zip)包含html/index.html、XML、摘要和验证元数据；[manifest](manifest.json)记录报告及本轮源码逐文件SHA-256，可核对对应版本。机器会话名已脱敏，不发布exec、class、凭据、数据库或环境XML。

该生产字节码已完成本轮发布并回读JAR摘要；正式验收见[发布记录](../../../history/verification/release-evidence.md#application-onboarding-release-20261125)。覆盖率为隔离测试执行覆盖，不代表正式流量覆盖、性能或业务无缺陷；前轮性能及正式验收保留其原版本边界。复现方法见[后端说明](../../../../backend/README.md#7-双策略jacoco复现)。
