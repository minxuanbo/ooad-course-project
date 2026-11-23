# RBAC双策略JaCoCo报告（2026-11-24）

实测源码为`7fd1b60e1e76d0d74d3fa5db7c535c404c2a9c47`，structured与DDD各328项测试，失败/错误/跳过均0。两次测试和当前347份生产class的SHA-256逐项一致，官方JaCoCo CLI0.8.14合并探针后通过pom原六项零遗漏门槛，未排除业务代码或降低门槛。

| 计数 | 覆盖 | 遗漏 | 比例 |
| --- | ---: | ---: | ---: |
| 指令 | 24208 | 0 | 100% |
| 分支 | 1948 | 0 | 100% |
| 行 | 4862 | 0 | 100% |
| 复杂度 | 2021 | 0 | 100% |
| 方法 | 1040 | 0 | 100% |
| 类 | 264 | 0 | 100% |

下载[完整报告ZIP](rbac-jacoco-report.zip)后打开html/index.html。[摘要](summary.json)记录计数，[manifest](manifest.json)提供ZIP及包内逐文件SHA-256。ZIP包含HTML和本地资源、jacoco.xml、summary.json及verification.json；机器会话名称已替换为测试标签，覆盖数据、时间及class标识保留。原exec、class/JAR、Surefire环境XML、数据库、口令和运行日志只留在忽略目录。

这是测试执行覆盖率，不表示线上流量覆盖或不存在缺陷。新增部门角色边界、应用预置真实MySQL往返及PENDING组合均已补验；发布结果另见[验收入口](../../../testing-acceptance.md)。复现方法见[双策略覆盖率](../../../../backend/README.md#7-双策略jacoco复现)。
