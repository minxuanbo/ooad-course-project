# 已部署后端 JaCoCo 报告

口试统一使用已部署后端的覆盖率采集：开始采集 → 公网 API 演示 → 导出报告。

- [现场运行说明](../deploy/现场运行线上JaCoCo.md)
- [线上报告说明](live-jacoco/README.md)
- [查看 HTML 样例](live-jacoco/20261021T064241Z-report-2102/html/index.html)
- [完整报告包](live-jacoco/20261021T064241Z-report-2102.tar.gz)

样例生成于 2026-10-21，来自 D 上的 rbac_backend，连接其业务数据库。
行覆盖率 58.44%，分支覆盖率 44.52%；具体范围见线上报告说明。
下载完整报告包并解压后打开 html/index.html，不要只下载单个 HTML 文件。
现场应重新采集并导出新的报告；报告不会自动提交课程网站。

项目保留常规回归测试源码，但不再通过 Maven 测试生命周期自动生成 JaCoCo 报告。
常规测试不可指向业务数据库；线上演示使用专用 API 脚本。
