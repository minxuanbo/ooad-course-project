# JaCoCo回归与报告

报告记录测试JVM实际执行的代码，不是线上进程的运行覆盖率。测试使用独立可丢弃MySQL，不能把测试URL指向正式业务库；不重新初始化线上、不重启产品服务。最近双策略各328项及六项100%实测见[11月24日覆盖率](../docs/history/verification/access-extension-coverage-20261124.md)，[完整报告](../docs/reports/jacoco/20261124-rbac/README.md)提供脱敏HTML、XML及摘要。

## 本机执行

使用JDK21、Maven、真实CloneFactory0.0.2及MySQL8.4。按[后端回归与隔离](../backend/README.md#2-本地回归与隔离)配置六个专用数据库URL和测试账号，口令只放本机环境。保留三份原Excel；不要复制他人的.local或运行凭据。

分别设置APP_BACKEND_STRATEGY为ddd和structured，执行：

```powershell
$env:APP_BACKEND_STRATEGY='ddd'
mvn -B -ntp -f backend/pom.xml clean test jacoco:report
```

每次成功后先将backend/target/site/jacoco整个目录、backend/target/jacoco.exec、测试计数以及编译class摘要复制到不同的私有输出目录，再清理构建并运行另一策略。不要只复制index.html，也不要让第二次clean覆盖唯一一份报告。测试失败或被杀死时保留诊断日志，不能将部分exec算作完整回归。

单策略报告包含整个生产代码分母，也就包含未启用的另一策略。因此可在确认两次源码、工具链及全部class文件逐一SHA-256一致后，使用官方同版JaCoCo CLI0.8.14合并；不同时期或不同字节码不能合并。

```powershell
java -jar <jacoco-cli.jar> merge <ddd.exec> <structured.exec> --destfile <combined.exec>
java -jar <jacoco-cli.jar> report <combined.exec> --classfiles backend/target/classes --sourcefiles backend/src/main/java --html <combined-html> --xml <combined.xml> --csv <combined.csv>
```

占位路径须替换为自己的完整私有输出路径。原始两策略报告继续保留，合并不删除未覆盖的生产类，也不等于只测了已部署DDD。

## 覆盖率门槛

pom保留指令、分支、行、复杂度、方法、类六项MISSEDCOUNT=0门槛。`mvn clean test jacoco:report`验证测试及生成报告，不执行verify阶段门槛；需要门槛结论时另行执行check，并记录其退出码。

check实际默认读取backend/target/jacoco.exec。检查合并报告时，先备份原文件、核对合并exec摘要后临时复制到该位置，再执行以下命令，最后无论成功失败都恢复原文件：

```powershell
mvn -B -ntp -o -f backend/pom.xml org.jacoco:jacoco-maven-plugin:0.8.14:check@coverage-check
```

不要假定`-Djacoco.dataFile`会改变check的读取位置，应以实际日志为准。功能测试通过和覆盖率门槛是否达到是两个独立结论，实际数值集中在发布证据。不得降低门槛、排除生产类、跳过测试或用无意义测试宣称全覆盖。

## 构建机隔离入口

现行脚本[deploy/test-backend.sh](test-backend.sh)从backend子目录准备源码，创建独立网络、临时MySQL及六个专库，不连接正式数据库。使用本项目已安装真实CloneFactory的构建镜像及明确固定的MySQL镜像运行；先检查脚本参数和资源上限，再在A工作副本记录实际main提交。原有未跟踪报告先保留，更新源码使用fetch及ff-only，不再使用旧refactor分支命令。

构建机复现前须安排足够内存，不能通过关闭线上服务挤出资源。容器被杀死或测试未完成不算通过；构建机运行与本机运行在证据中分别说明，最近一次资源失败及后续完整运行见发布记录。

## 课程副本及展示

课程源码由prepare_course_source.py导出独立副本，保留已确认补丁，不改老师原目录和pom。使用独立course_test，通过Maven测试JVM的argLine附加官方JaCoCo代理，再显式执行report；不要覆盖已有其他代理配置，先核对最终argLine确实含代理。课程报告属于既有测试集执行范围，不能视为同数量的公网DDD验收；实际计数及覆盖率见发布证据。

报告完整目录可下载后打开index.html，按包、类查看红黄绿行及分支。只交付HTML资源、XML/CSV、exec、脱敏计数及源码/class摘要，不公开Surefire XML、数据库口令或完整debug日志。100%也只代表结构覆盖，不代表所有输入、无缺陷、安全或性能已获证明。
