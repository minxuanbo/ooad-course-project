# 市政公司统一权限管理系统

以《2-9组检查记录》整改 RBAC 核心授权链路。T01 已评审合入；T02 独立 JPA 模型已通过用户授权的代理评审，52 项测试及打包通过；T03 开工前须确认 PR #18 已合入共同基线。产品业务仍走旧实现，原型、接口和运行验收须按各票完成。

- [整改文档](docs/rbac-rectification/README.md)：检查依据、执行计划、Chen ER 图和任务清单。
- [小组协作](CONTRIBUTING.md)：认领任务、建立工作树、提交草稿 PR。
- [旧实现资料](docs/legacy/README.md)：仅供差异核对，不能作为整改需求或接口契约。

有效账号通过既有身份校验可登录；无管理权限显示提示；管理请求校验具体权限和部门范围。正式接口须先完成对应原型/API 契约评审。正式前端暂缓，课程系统受控场景列为后续。

## 工程与运行

后端使用 Java 21、Spring Boot、MySQL，现有 JDBC 将按整改计划逐块替换为 JPA；前端为 Vue/Vite。源文件分别位于 src/ 和 frontend/，运行配置见 src/main/resources/application.yml。

根目录三份 Excel 是初始化及测试输入，保留原位。已有数据库应先备份；整改使用独立数据库，不能用旧报告证明新方案已经通过。

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
