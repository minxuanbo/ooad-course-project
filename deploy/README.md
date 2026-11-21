# 首次数据库结构与本地依赖

本轮使用全新独立MySQL库和默认JAR/Vue本地运行；完整步骤见[组员复现指南](../docs/reproduction.md)。用户已明确后续新增服务器，当前不执行云部署、镜像发布或Stack操作。根Dockerfile/Stack及旧容器测试脚本仍是历史配置，尚未适配backend目录，不是本次可复现发布入口；旧拓扑与公网地址由Git历史追溯，不在当前说明中列可直接执行的发布命令。

两份首次SQL依次为[核心与来源结构](rbac-core-schema.sql)、[运行日志和回执](rbac-core-runtime.sql)，共12表。应用只validate，不自动建库建表。已有同名库时另备独立实例，不删除或覆盖旧库；不提供旧库升级脚本。

## 本机依赖安装

使用JDK21、Maven及Git。2026-11-21按用户提供的老师本地spring-fundamentals核对并升级为真实`cn.edu.xmu:clonefactory:0.0.2`；源码固定提交`40583ff94764d2e758bcb13be17f5c05ea4d6352`，不是将旧构件改名。来源为[老师spring-fundamentals](https://gitee.com/mingqcn/spring-fundamentals/tree/40583ff94764d2e758bcb13be17f5c05ea4d6352/clonefactory)，源码工作树已核对干净，CopyProcessor.java的SHA-256为`161253932c623301e3f90726548e8821c87d9928ee518a47ee7b6f51ec6e3fd8`。来源仓库许可为Apache-2.0，源码与jar只放忽略目录及本机Maven仓库。

以下PowerShell命令在仓库根执行，使用新的本地忽略目录；目录已存在时先核对所属及提交，不覆盖。编译器proc=full使老师处理器源码可在Java21正确编译，不改老师源码，也不连带升级本项目Boot、JDK或Lombok。历史Dockerfile不是当前复现入口，不宣称其依赖已随本轮适配。

```powershell
git clone --no-checkout https://gitee.com/mingqcn/spring-fundamentals.git .local/teacher-clonefactory-build
git -C .local/teacher-clonefactory-build checkout --detach 40583ff94764d2e758bcb13be17f5c05ea4d6352
git -C .local/teacher-clonefactory-build rev-parse HEAD
Push-Location .local/teacher-clonefactory-build/clonefactory
mvn -B -ntp '-Dmaven.compiler.proc=full' clean install
Pop-Location
```

确认安装BUILD SUCCESS且提交完全一致，再构建本工程。完整功能回归需先准备对应隔离MySQL专库，见[T13复现](../docs/history/verification/T13.md#2-可复现步骤)；只打包时可以显式跳过测试，但不得把该构建结果表述为回归通过。首次依赖下载需要Gitee/Maven网络。密码、token、生成报告和老师源码留在忽略目录，不提交Git。

## 本地初始化及运行

组织账号导入、只读预检、首次批量授权和默认JAR是四个分开的命令，见[后端说明](../backend/README.md#3-新项目本地初始化与运行)。口令从环境或秘密配置提供，预检CSV留在忽略目录。默认初始目录只含RBAC，18业务系统仅在原型演示；显式课程接入另按[R31首次身份步骤](../docs/course-integration.md#8-本部教师与分公司学生首次接入r31)执行，不由产品启动或身份开关触发。

默认后端8081、Vue开发5173；API_TARGET须与后端端口一致。Docker、云端TLS、Swarm及服务器切换待新方案另行设计；本地测试结果不作为云部署证据。[旧现场JaCoCo说明](现场运行JaCoCo.md)仅为历史材料，当前按用户要求不作为阻塞。
