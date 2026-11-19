# 老师课程工程的本地接入

老师原目录和提交`19fbd1a`保留不动。RBAC核心只提供通用应用授权，课程角色与权限配置保存在[catalog.json](catalog.json)，不在RBAC生产代码维护teacher/student或课程路由常量。

## 必要副本补丁

[course-local-fixes.patch](course-local-fixes.patch)仅修改老师structured的三个生产文件：

| 文件 | 原问题与修复 |
| --- | --- |
| StructuredCourseService.java | 重复保存或部分保留课程组长时，批量删除后merge相同复合主键触发乐观锁错误；改为保留未变关联、删除移除项、仅插入新增项 |
| StructuredTeachingClassService.java | 团队管理页面调用教学班读取被对象规则误拒绝；读取分支沿既有CV/CTM权限，普通无关教师仍拒绝 |
| StructuredMilestoneService.java | 列表强制解析教师导致已选课学生403；增加有效学生角色、账号状态、开放课程和选课关系的读取分支，教师及写入规则不放宽 |

旧teacher-local-binding补丁已撤除，不额外改老师登录/安全过滤器。补丁通过真实两系统HTTP回归观察RED→GREEN；来源和限制见[接入说明](../../docs/course-integration.md)。未修改老师pom、迁移或前端，不在此实现老师生产代码中缺少的个人批改/录音摘要等业务。

在干净的老师源码副本根目录，先核对提交，再执行：

```powershell
git apply --check <RBAC仓库绝对路径>/integrations/course/course-local-fixes.patch
git apply <RBAC仓库绝对路径>/integrations/course/course-local-fixes.patch
mvn -f backend/pom.xml -DskipTests clean package
```

## 外部依赖支撑

[local_identity_gateway.py](local_identity_gateway.py)仅供本机联调：实时转接RBAC身份、验证配置应用受众、持久追加和查询外部业务审计。SQLite与数据库配置必须位于忽略目录；不是生产服务器方案，不替代RBAC鉴权日志，也不把前端菜单显示当作后端权限。

读取外部审计要求AV全局授权；不向普通教师开放全部业务日志。写日志的主体与服务端时间不可由请求伪造，requestId同应用幂等，不同内容重放拒绝。老师Spring客户端的chunked JSON请求与浏览器普通请求均支持。老师对象ID到课程/班级的只读投影留在此联调工具中，不进入RBAC应用或领域层。

老师将同一短码用于多个路由。RBAC只提供通用permissionApis事实，本工具依据catalog.json核对全部必要路由，缺失任一条即关闭整个短码；不把课程路由常量重新塞回RBAC。
