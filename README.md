# 市政公司统一权限管理系统

依据《2-9组检查记录》、补充检查图片及用户确认，整改“用户—角色—权限—API”授权链路和部门管理。

- [整改文档](docs/rbac-rectification/README.md)：检查依据、执行计划、Chen ER 图、契约和任务清单。
- [小组协作](CONTRIBUTING.md)：认领任务、建立工作树、提交草稿 PR。
- [旧实现资料](docs/legacy/README.md)：历史设计与实现，供差异核对。

**用户名就是工号。** `username` 存工号，`phone` 用于同一账号的手机号登录，两种入口共用口令和授权。有效账号具备本系统实际管理权限才可登录，普通员工拒绝；管理请求逐次检查具体权限和部门范围。最小鉴权日志供有日志权限的全局维护者查询，部门管理员拒绝。字段与范围见[执行计划](docs/rbac-rectification/RBAC整改计划与Chen-ER图.md#login)。

已记录进度：T01/T02 经 PR #17/#18 合入，T02 的 52 项独立模型测试及打包通过；四组原型/API 契约经 PR #19 合入。2026-11-08 登录修正待 T04 补充复核；T03 可认领，负责模型修正、数据导入及账号缺项。产品业务仍使用旧实现。

正式接口依赖对应原型/API 契约评审。REST、读写声明式事务、CloneFactory 和跨部门调动按票实现与验收；正式前端暂缓，课程受控场景由 T14/T15 后续落实。

## 工程与运行

后端为 Java 21、Spring Boot、MySQL，JDBC 按业务块迁往 JPA；前端为 Vue/Vite。代码在 src/ 和 frontend/，运行配置见 src/main/resources/application.yml。

根目录三份 Excel 与老师原件的 SHA-256 一致：10,000 人、111 个组织节点。“用户名”提供工号，“电话号码”提供手机号；口令哈希、有效状态和初始维护账号方案待核对，详见[数据复核](docs/rbac-rectification/RBAC检查记录复核说明.md#12-原始数据复核)。原件保留，原库备份后使用独立整改库；权限目录按已评审场景建立。

设置 RBAC_DB_URL、RBAC_DB_USER、RBAC_DB_PASSWORD 后，在仓库根目录启动后端：

```bash
mvn spring-boot:run
```

另开终端启动现有前端：

```bash
cd frontend
npm ci
npm run dev
```

前端默认在 http://localhost:5173，代理 /api 到后端 8081。历史接口见[旧 API 资料](docs/legacy/课程设计/03-API设计文档.md)，整改接口以新契约为准。

测试与部署方法见[现场测试](deploy/现场运行JaCoCo.md)和[部署说明](deploy/README.md)。生成报告、构建缓存和本地小组资料留在 Git 之外。
