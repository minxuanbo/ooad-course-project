# 市政公司统一权限管理系统

依据《2-9组检查记录》、补充检查图片及用户确认，整改“用户—角色—权限—API”授权链路和部门管理。

- [整改文档](docs/rbac-rectification/README.md)：检查依据、执行计划、Chen ER 图、契约和任务清单。
- [小组协作](CONTRIBUTING.md)：认领任务、建立工作树、提交草稿 PR。
- [旧实现资料](docs/legacy/README.md)：历史设计与实现，供差异核对。

**用户名就是工号。** `username` 存工号，`phone` 用于同一账号的手机号登录，两种入口共用口令和授权。有效账号具备本系统实际管理权限才可登录，普通员工拒绝；管理请求逐次检查具体权限和部门范围。最小鉴权日志供有日志权限的全局维护者查询，部门管理员拒绝。字段与范围见[执行计划](docs/rbac-rectification/RBAC整改计划与Chen-ER图.md#login)。

已记录进度：T01/T02经PR #17/#18合入，T02的52项测试为历史证据；四组原型/API契约经PR #19合入，T04双入口登录补充见Issue #5。T03已完成单工号修正、组织人员和新项目账号初始化，112项测试通过、代理双轴审核均0项发现，交付[PR #21](https://github.com/minxuanbo/ooad-course-project/pull/21)；合入及关票证据见Issue #4。用户确认JaCoCo本次不作阻塞，原规则保留。T12后产品默认入口已切换为整改链路，离线导入方法见[计划4.5](docs/rbac-rectification/RBAC整改计划与Chen-ER图.md#45-t03离线导入与交接)。

T09角色定义与权限配置已在独立分支实现并完成本地验证：五项真实接口、即时撤权及恢复、最后管理员与并发保护。完整回归166项失败/错误/跳过均0，打包及JAR实测通过；[验证记录与复现](docs/rbac-rectification/RBAC整改计划与Chen-ER图.md#t09-validation)区分本地验证和待完成的小组评审。

正式接口依赖对应原型/API 契约评审。REST、读写声明式事务、CloneFactory 和跨部门调动按票实现与验收；正式前端暂缓，课程受控场景由 T14/T15 后续落实。

## 工程与运行

后端为 Java 21、Spring Boot、MySQL，持久化统一为 JPA；前端为 Vue/Vite，仅保留登录与鉴权日志查询兼容入口。代码在 src/ 和 frontend/，运行配置见 src/main/resources/application-core-runtime.yml，默认入口为 `com.municipal.rbaccore.runtime.CoreRuntimeCommand`。

根目录三份 Excel 与老师原件的 SHA-256 一致：10,000 人、111 个组织节点。“用户名”提供工号，“电话号码”提供手机号；口令哈希、有效状态和初始维护账号方案待核对，详见[数据复核](docs/rbac-rectification/RBAC检查记录复核说明.md#12-原始数据复核)。原件保留，原库备份后使用独立整改库；权限目录按已评审场景建立。

设置 RBAC_CORE_DB_URL、RBAC_CORE_DB_USER、RBAC_CORE_DB_PASSWORD 后，在仓库根目录启动整改后端：

```bash
mvn spring-boot:run
```

组织/人员/账号初始化与五角色首次授权使用离线命令（T03 导入、CoreBootstrapCommand），见[计划 4.5—4.6](docs/rbac-rectification/RBAC整改计划与Chen-ER图.md)；后端启动不建表、不导入。

另开终端启动现有前端：

```bash
cd frontend
npm ci
npm run dev
```

前端默认在 http://localhost:5173，代理 /api 到后端 8081。保留页面仅为登录与日志查询兼容；完整新前端暂缓，整改接口以新契约为准。

测试与部署方法见[现场测试](deploy/现场运行JaCoCo.md)和[部署说明](deploy/README.md)。生成报告、构建缓存和本地小组资料留在 Git 之外。
