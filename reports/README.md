# JaCoCo 覆盖率报告与口试运行说明

本报告实际生成于 **2026-10-20 21:00（北京时间）**，对应代码提交
`de93f9f`。2026-10-21 整理提交材料，未重新执行或修改原始覆盖率数据。

## 报告入口

- [HTML 报告](jacoco/20261020T125928Z-10480/index.html)：下载完整目录后双击打开。
- [XML 数据](jacoco/20261020T125928Z-10480/jacoco.xml)
- [CSV 数据](jacoco/20261020T125928Z-10480/jacoco.csv)
- `jacoco-course-20261020.zip`：课程网站上传包，解压后打开根目录 `index.html`。

不要只上传 index.html，它依赖同目录的资源、包级页面和源码页面。
Gitee 的 HTML 源码页不一定直接渲染报告，请下载 ZIP 后查看。

## 实际结果

Java 21、Spring Boot 3.5.6、Maven 3.9.9、JaCoCo 0.8.14、真实 MySQL 8.4。
在 A 构建机的独立临时数据库执行 `mvn clean verify`。
20 项测试全部通过，0 失败、0 错误、0 跳过；11 个生产类全部纳入统计。

| 指标 | 覆盖/总数 | 覆盖率 |
|---|---:|---:|
| 行 | 222/231 | 96.10% |
| 分支 | 241/301 | 80.07% |
| 指令 | 3565/3830 | 93.08% |
| 方法 | 80/86 | 93.02% |
| 类 | 11/11 | 100.00% |
| 圈复杂度 | 178/240 | 74.17% |

覆盖率按 XML 总计数计算，HTML 页面可能显示取整值。没有排除业务类或设置虚构的课程达标线。
代码较紧凑，一行可能包含多个表达式，行覆盖率不能替代分支覆盖率。
未覆盖分支主要分布在 ApiController、DataInitializer、AuthService、ExcelXmlReader
及 SchemaGuard；需要在报告中逐项审查。测试通过不代表压力、渗透、故障恢复已验证。
设计文档要求的未知路由 404/错误方法 405 尚有实现差距，当前通用异常处理可能返回 500。

## 现场在 A 执行

先确认三份 Excel 仍位于项目根目录。以下命令不使用业务数据库，不修改 B/C/D 的服务：

```bash
cd ~/ooad-course-project
git pull --ff-only
export RBAC_BUILD_IMAGE=rbac-build:$(git rev-parse --short HEAD)
docker build --target build --memory 1200m -t "$RBAC_BUILD_IMAGE" .
export RBAC_TEST_MYSQL_IMAGE=swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-mysql:8.4-20261020
sh deploy/test-backend.sh
```

脚本随机生成临时数据库凭据，创建独立容器和网络，执行测试，然后清理本次临时资源。
成功时打印 `reports/jacoco/<UTC时间>-<进程号>/index.html`，每次使用新目录，保留旧报告。
现场需要能访问 Maven 镜像源；建议预约前在 A 预演以确认网络和依赖缓存。
JaCoCo agent 使用独立 `jacocoAgentArgLine` 与 Surefire 晚绑定，不被内存参数覆盖。
不要再额外传入覆盖 agent 的 `-DargLine`；需要调整内存时使用 `-DtestJvmArgs`。

本地已配置独立测试库时，也可以设置 `RBAC_TEST_DB_URL`、`RBAC_TEST_DB_USER`、
`RBAC_TEST_DB_PASSWORD` 后运行 `mvn clean verify`，输出 `target/site/jacoco/index.html`。
禁止把测试连接指向业务库。

## 报告内容与复现限制

归档保留原始 HTML、XML、CSV、jacoco.exec 和源码标注页面。没有包含 Excel、数据库备份、
环境文件或 Surefire XML（后者可能含连接凭据）。报告不会自动上传课程网站。
修改 Java 代码或测试后必须重跑并提交新报告，不应沿用本次数字。

## 老师要求直接测试已部署后端时

使用 [已部署后端现场采集说明](../deploy/现场运行线上JaCoCo.md)，
不要把本页临时数据库自动化测试的数值描述为线上覆盖率。
真实线上样例见 [live-jacoco](live-jacoco/README.md)。
