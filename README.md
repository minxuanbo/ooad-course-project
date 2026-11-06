# 市政公司统一权限管理系统

以《2-9组检查记录》、补充检查图片及用户确认整改 RBAC 核心授权链路。T01/T02 已分别经 PR #17/#18 合入共同基线；T02 独立 JPA 模型的 52 项测试及打包通过。T03 可人工认领，须先落实真实数据和字段/初始维护账号缺项。产品业务仍走旧实现，2026-11-07 补充要求、原型、接口及运行验收仍按票完成。

- [整改文档](docs/rbac-rectification/README.md)：检查依据、执行计划、Chen ER 图和任务清单。
- [小组协作](CONTRIBUTING.md)：认领任务、建立工作树、提交草稿 PR。
- [旧实现资料](docs/legacy/README.md)：仅供差异核对，不能作为整改需求或接口契约。

账号有效且具有本权限系统有效管理权限才允许登录，普通员工/无管理权限拒绝；复用原登录流程，不新增管理员属性或资格模型。管理请求逐次校验具体权限及部门范围。鉴权日志复用旧最小记录和查询，只允许具备日志权限的全局维护者查看，部门管理员拒绝；不恢复完整审计。

正式接口须先完成对应原型/API 契约评审；REST、读写声明式事务、CloneFactory 及跨部门调动已纳入任务，尚未验收。正式前端暂缓，课程系统受控场景仍为后续。

## 工程与运行

后端使用 Java 21、Spring Boot、MySQL，现有 JDBC 将按整改计划逐块替换为 JPA；前端为 Vue/Vite。源文件分别位于 src/ 和 frontend/，运行配置见 src/main/resources/application.yml。

根目录三份 Excel 与老师提供文件的 SHA-256 一致，保留原位；全量静态核对为 10,000 人、111 个组织节点，具体字段缺项见[数据复核](docs/rbac-rectification/RBAC检查记录复核说明.md#12-原始数据复核)。账号/授权信息不由原表完整提供，不能整包恢复业务清单。已有数据库先备份，整改使用独立库，不用旧报告证明新方案通过。

提供 RBAC_DB_URL、RBAC_DB_USER、RBAC_DB_PASSWORD 后，在仓库根目录启动后端：

```bash
mvn spring-boot:run
```

另开终端启动现有前端：

```bash
cd frontend
npm ci
npm run dev
```

前端默认在 http://localhost:5173，代理 /api 到后端 8081。旧认证与接口细节见[旧 API 资料](docs/legacy/课程设计/03-API设计文档.md)，不作为整改接口要求。

测试与部署方法见[现场测试](deploy/现场运行JaCoCo.md)和[部署说明](deploy/README.md)。生成报告、构建缓存和本地小组资料不提交 Git。
