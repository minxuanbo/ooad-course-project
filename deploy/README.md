# 首次数据库结构与本地依赖

本轮使用全新独立MySQL库和默认JAR/Vue本地运行；完整步骤见[组员复现指南](../docs/reproduction.md)。用户已明确后续新增服务器，当前不执行云部署、镜像发布或Stack操作。根Dockerfile/Stack及旧容器测试脚本仍是历史配置，尚未适配backend目录，不是本次可复现发布入口；旧拓扑与公网地址由Git历史追溯，不在当前说明中列可直接执行的发布命令。

两份首次SQL依次为[核心与来源结构](rbac-core-schema.sql)、[运行日志和回执](rbac-core-runtime.sql)，共12表。应用只validate，不自动建库建表。已有同名库时另备独立实例，不删除或覆盖旧库；不提供旧库升级脚本。

## 本机依赖安装

使用JDK21、Maven及Git。老师course-project-mgt通过真实CloneFactory映射；本项目沿用cn.edu.xmu:clonefactory:0.0.1-SNAPSHOT，但以源码提交固定实际内容，不按可变SNAPSHOT拉取未知实现。来源为[老师spring-fundamentals](https://gitee.com/mingqcn/spring-fundamentals/tree/237f49c52bdc72da30383571bb2f985ac54a514a/clonefactory)，已核对来源版本和Java21编译方式；来源仓库许可为Apache-2.0，不将其源码或jar复制进本仓库。

以下PowerShell命令在仓库根执行，使用新的本地忽略目录；目录已存在时先核对所属及提交，不覆盖。安装命令与Dockerfile一致，编译器proc=full使老师处理器源码可在Java21正确编译，不改老师源码或升级本项目Boot。

```powershell
git clone --no-checkout https://gitee.com/mingqcn/spring-fundamentals.git .local/teacher-clonefactory-build
git -C .local/teacher-clonefactory-build checkout --detach 237f49c52bdc72da30383571bb2f985ac54a514a
git -C .local/teacher-clonefactory-build rev-parse HEAD
Push-Location .local/teacher-clonefactory-build/clonefactory
mvn -B -ntp -Dmaven.compiler.proc=full clean org.apache.maven.plugins:maven-compiler-plugin:3.14.0:compile jar:jar install:install
Pop-Location
```

确认安装BUILD SUCCESS且提交完全一致，再构建本工程。完整功能回归需先准备对应隔离MySQL专库，见[T13复现](../docs/acceptance/T13.md#2-可复现步骤)；只打包时可以显式跳过测试，但不得把该构建结果表述为回归通过。首次依赖下载需要Gitee/Maven网络。密码、token、生成报告和老师源码留在忽略目录，不提交Git。

## 本地初始化及运行

组织账号导入、只读预检、首次批量授权和默认JAR是四个分开的命令，见[后端说明](../backend/README.md#3-新项目本地初始化与运行)。口令从环境或秘密配置提供，预检CSV留在忽略目录。初始目录只含RBAC，18业务系统仅在原型演示，课程接入暂不考虑。

默认后端8081、Vue开发5173；API_TARGET须与后端端口一致。Docker、云端TLS、Swarm及服务器切换待新方案另行设计；本地测试结果不作为云部署证据。[旧现场JaCoCo说明](现场运行JaCoCo.md)仅为历史材料，当前按用户要求不作为阻塞。
