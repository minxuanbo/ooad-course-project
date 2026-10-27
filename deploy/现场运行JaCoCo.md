# 口试现场运行 JaCoCo（独立 MySQL 测试库）

本流程在 A（course-build，120.46.134.42）的 Termius 终端执行。
报告来自 Maven 测试 JVM，包含真实 MySQL 集成测试与单元测试；不是公网线上实例的运行覆盖率。
B/C/D 的业务服务不参与，不需要重启；已有业务库不会被清空或改动。

## 本次已验证版本

A 的独立目录 `/root/rbac-design-20261027` 保存本次送测源码。它独立于 Git 克隆，适合直接复现已验证报告。

在 A 的 Termius 中执行：

```bash
cd /root/rbac-design-20261027
export RBAC_BUILD_IMAGE=rbac-build:test-20261023
export RBAC_TEST_EXCEL_DIR=/root/ooad-course-project
export RBAC_TEST_MYSQL_IMAGE=swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-mysql@sha256:54ad54987c01f1d7af9e1343910fd0cf0d4c4526b552f13e712ddbeacf13dd02
sh deploy/test-backend.sh
```

工具镜像只提供 JDK/Maven/依赖，实际测试复制本目录当前 pom.xml/src 重新编译；Excel 从原克隆目录读取，不进入镜像。本次 61 项测试通过，全部生产代码的六项覆盖率均为 100%（69 个有可执行代码的类，发布共 72 个 class 文件）。现场以实际输出为准。

下节是更新 Git 克隆后测试当前源码的通用方法，不必与上节重复执行。

## 预约前准备

A 已安装 Docker。代码位于 ~/ooad-course-project，三份课程 Excel 位于该目录。
依赖下载需要网络；建议提前构建一次，现场可复用测试工具镜像。

```bash
cd ~/ooad-course-project
git pull --ff-only
export RBAC_BUILD_IMAGE=rbac-build:exam-$(git rev-parse --short HEAD)
docker build --target build --memory 1200m -t "$RBAC_BUILD_IMAGE" .
export RBAC_TEST_MYSQL_IMAGE=swr.cn-north-4.myhuaweicloud.com/javaee_xmu/rbac-mysql@sha256:54ad54987c01f1d7af9e1343910fd0cf0d4c4526b552f13e712ddbeacf13dd02
```

构建阶段只编译，不运行测试；Excel 被 .dockerignore 排除，不进入镜像、不上传 SWR。
若打开新终端，需重新设置以上两个 export；不必重复构建已经存在且工具版本匹配的镜像。
脚本会从当前项目只读复制 pom.xml/src，在容器新目录重新编译，因此不会用工具镜像内的旧代码冒充当前代码。

## 现场执行

同一终端运行：

```bash
sh deploy/test-backend.sh
```

脚本自动完成：

1. 创建唯一命名的 Docker 网络、临时 MySQL 容器和随机凭据，不发布数据库端口。
2. 等待数据库可连接；新建 rbac_test，测试账号只有该库权限；临时实例启用 log_bin_trust_function_creators 以安装触发器，不对业务库修改此设置。
3. 将当前源码、三份 Excel 只读挂载并复制到测试容器临时目录。
4. 执行 mvn clean verify：真实空库建表/初始化、接口集成测试、单元测试、JaCoCo 报告及覆盖率门槛检查。
5. 已生成覆盖率文件时保存诊断报告；退出时删除本轮测试数据库容器及匿名卷、网络和临时凭据文件。不会删除现有业务卷。

成功判断：Tests run 的 Failures/Errors/Skipped 均为 0，出现 BUILD SUCCESS、All coverage checks have been met，以及 JaCoCo report 路径。
当前门槛要求全部生产类的指令、分支、行、复杂度、方法、类六项 MISSEDCOUNT=0；有任何遗漏时 verify 失败。失败时可能保留诊断报告，不得将其当成成功报告；以 BUILD SUCCESS 与 All coverage checks have been met 为准。
不要使用 -DskipTests、覆盖 argLine 或排除生产类来通过检查。

每次生成新目录 reports/jacoco/<UTC时间>-<进程号>/，重复执行会从另一全新数据库开始，不复用上次测试业务数据。
测试内部集成用例使用事务回滚；整个 MySQL 实例在测试结束后销毁。

## 下载和展示

终端打印例如：

```text
JaCoCo report: /root/ooad-course-project/reports/jacoco/<时间>-<编号>/index.html
```

使用 Termius SFTP 下载整个目录（不是单独一个 HTML），打开 index.html；点包名、类名可查看逐行与分支标色。
目录含 HTML 资源、jacoco.xml、jacoco.csv、jacoco.exec、test-summary.txt、源码/字节码 SHA256 清单。
可在本机把整个目录压缩为 ZIP 上传课程网站，并提交 Gitee 地址；脚本不会代替你提交课程网站。
不要公开 target/surefire-reports/*.xml 或完整调试日志，它们可能含环境和测试连接凭据。

## 如何向老师说明

“在 A 用独立真实 MySQL 运行同一项目的后端自动化测试。集成测试验证初始化、SQL、事务、权限及交接；单元测试模拟文件/JDBC/框架依赖补充边界和故障路径，JaCoCo 记录生产代码的真实执行。”

单元测试的 main 用例验证委派给 SpringApplication；数据库集成测试通过 SpringBootTest 启动上下文。
异常注入、合成 Excel 和私有导入辅助逻辑验证不等同于在线上制造故障。
100% 是当前源码的结构覆盖率，不是无缺陷、所有输入、性能或安全能力的证明。

## 失败排查

- 缺 Excel：确认根目录三个文件齐全，权限清单支持带 (1) 的文件名；不要将文件打入镜像。
- 构建下载失败：检查 A 到 Maven 镜像源的网络，不需要调整 B/C/D。
- 数据库未就绪：查看本轮临时容器日志；脚本退出会清理本轮资源。
- 测试/覆盖率失败：保留终端结果，修复当前代码或用例后重跑；不要混入旧 jacoco.exec。
- 不要将 RBAC_TEST_DB_URL 指向业务库，也不要用线上演示账号替代测试数据。

当前报告只保留最新完整版本。现场每次执行仍产生新目录，成功后再按需清理旧报告；不要将不同版本的 exec 混合。
