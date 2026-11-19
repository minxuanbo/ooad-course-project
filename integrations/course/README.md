# 老师课程工程本地接入补丁

本目录只保存相对老师提交`19fbd1a`的必要补丁，不复制整套课程源码。原目录保留不动，联调副本应用[teacher-local-binding.patch](teacher-local-binding.patch)。

统一身份服务可以为有教师标记的账号签发令牌，但课程本地未必已有对应Teacher。老师原登录页检查名单，Bearer过滤器原先只信任身份服务，直接取得令牌可绕过本地名单。因此在每个教师请求进入业务前，继续核对当前未软删除的本地Teacher绑定；名单存储故障返回503并拒绝，功能权限仍由原课程Service判断。

仅改动两个生产文件及两个测试文件：

| 文件 | 改动 |
| --- | --- |
| `infrastructure/security/BearerAuthenticationFilter.java` | 注入现有TeacherJpaRepository，每次教师请求检查有效user_id绑定，无绑定403，存储故障503；不缓存绑定、不改变学生原路径 |
| `infrastructure/security/SecurityConfiguration.java` | 将既有仓储注入过滤器；路由与安全链配置保留 |
| `r42/R42BoundTeacherFilterTest.java` | 6项回归：未绑定、有绑定、软删除、原令牌下一请求撤绑定、存储故障、学生原行为 |
| `testing/BearerAuthenticationFilterAopTest.java` | 适配新增构造参数，保留原AOP初始化测试 |

以上路径均位于老师backend的main/test源码中；无SQL、pom、Vue、课程业务Service或权限码改动，不修本轮另发现的DDD当前学期409问题。补丁不新增身份服务、角色模型或口令存储。

在老师工程副本根目录先确认来源提交及干净工作树，再执行：

```powershell
git apply --check <RBAC仓库绝对路径>/integrations/course/teacher-local-binding.patch
git apply <RBAC仓库绝对路径>/integrations/course/teacher-local-binding.patch
mvn -f backend/pom.xml '-Dtest=R42BoundTeacherFilterTest,BearerAuthenticationFilterAopTest,HttpExchangeUnifiedIdentityGatewayTest,SecurityCourseAuthorizationTest,DddServiceBoundaryTest,ServicePortBoundaryTest' package
```

新文件中的record/身份协议仍由老师原实现负责。该补丁只补认证名单边界，不以小型单元测试替代两个真实系统联调，也不代表老师完整课程回归已通过。配置与实测见[接入设计](../../docs/course-integration.md)和[验收记录](../../docs/history/verification/course-integration-local.md)。
