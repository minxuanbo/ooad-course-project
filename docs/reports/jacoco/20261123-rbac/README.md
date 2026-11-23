# RBAC JaCoCo报告 · 2026-11-23

[下载完整报告包](rbac-jacoco-report.zip)，解压后用浏览器打开`html/index.html`。GitHub和Gitee的文件页面不直接运行HTML，需下载或从本地克隆目录解压查看。

报告对应实测代码提交`91d1b56a18c4f3fe7965edf3d108070d0b3af91b`，由JaCoCo0.8.14合并structured与DDD两次完整回归产生。两策略各304项测试，失败、错误和跳过均为0；全部340份生产class的摘要一致，原Maven六项零遗漏门槛通过。

| 计数 | 已覆盖 | 遗漏 | 覆盖率 |
| --- | ---: | ---: | ---: |
| 指令 | 21860 | 0 | 100% |
| 分支 | 1678 | 0 | 100% |
| 行 | 4440 | 0 | 100% |
| 复杂度 | 1831 | 0 | 100% |
| 方法 | 985 | 0 | 100% |
| 类 | 257 | 0 | 100% |

报告包共613个文件，包含完整HTML及其本地样式/脚本/图片、`jacoco.xml`、`summary.json`和`verification.json`。两处机器生成的会话名称已改为策略标签，其余报告数据、时间和覆盖率计数保持不变。原exec、class/JAR、Surefire环境XML、数据库、凭据及运行日志保留在本机忽略目录，不包含在公开包内。

[manifest.json](manifest.json)记录ZIP及包内每个文件的SHA-256、实测源码提交、测试计数和报告来源。ZIP的SHA-256为：

```text
be70f96471465d3274b6121ef1d26bc70d00b237b5cb12215f5643711b460e71
```

在PowerShell中可用`Get-FileHash -Algorithm SHA256 ./rbac-jacoco-report.zip`核对下载完整性。完整环境、测试分类和发布边界见[验证记录](../../../history/verification/rbac-jacoco-complete.md)，重新生成的方法见[后端说明](../../../../backend/README.md#7-双策略jacoco复现)。

本归档记录该次源码的合并覆盖率，不代表线上已更新，也不将100%等同于穷尽全部业务和性能场景。此次报告归档及双仓同步未修改生产代码，无需重复运行数据库回归。
