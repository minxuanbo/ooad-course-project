# R34 双DDD部署前适配（2026-11-22）

## 1. 授权与基线

用户提供老师deploy/README、五台SSH服务器，先要求方案，随后明确两个后端均使用DDD、其余完成适配；补充询问菜单对应Logback是否要改前端。此次完成部署材料与本机隔离验证，不将部署方案当作服务器已上线。起点main bdfb3e0294ebfbaeb7340f9a9d516eb2f81a4a33；任务分支codex/ddd-swarm-adaptation。详设Word/PDF/PPT不改，T14/T15不关闭。

老师原目录干净提交990cea2cc3686599ef5068945706a13e7e0afc5b，原目录不写入。与历史19fbd1a相比只有4个Java生产文件变化及1个新增，仍重新验证，不复用旧成绩。副本先保留原3处structured补丁，再增加DDD相同行为修复、学期标记释放、安全日志及相关回归。产品37项HTTP契约未变；Actuator属于内部运维入口，不并入业务OpenAPI或Apifox目录。

## 2. 实测环境与配置

五台均2vCPU、可见内存约1800MiB、40GB、Ubuntu18.04.6、Docker20.10.21，无swap。A剩余约1.9GB；B/C/D/E是现有Swarm节点，C为唯一manager，A不加入运行集群。本轮SSH仅调查；尝试临时目录中的配置语法核对后即清理，没有创建/更新/删除Swarm服务，没有清理镜像、数据卷或旧实验资料。

配置将MySQL放B，两个Nginx和Prometheus放C，课程DDD放D，RBAC DDD及独立Waitress服务放E。仅C发布host模式TCP80/8088，Java、MySQL和监控留overlay。正式HTTP入口、Secrets、单副本、stop-first、轮转、第一次导入/预览/绑定/授权步骤见[部署说明](../../../deploy/README.md)。当前会话索引仍在RBAC内存中，重启需重新登录。

公网/auth直接访问通用RBAC八项协议，课程前端的专用投影经/course-identity挂载独立接入服务；课程后端仍使用Swarm内部地址。这样不把课程单应用准入约束施加给其他数据库登记的应用，也不新增RBAC课程Controller。课程Vue以此最终baseURL重新类型检查及生产构建。

Dockerfile已适配backend目录及真实CloneFactory0.0.2固定提交；两个前端正式构建、两个Java镜像、独立接入服务分别构建。Java镜像构建显式跳过测试，不能算回归。服务器自带Docker CLI没有stack config子命令；本轮使用临时官方27.5.1客户端解析完整Stack，并用服务器bash检查五个脚本语法，临时文件随后清理；另有PyYAML和引用文件核对。没有安装替换系统Docker，也不伪报镜像构建或Stack已经上线。

## 3. RED与修复

1. 学期测试采用已提交的两个学期及独立Service事务；原DDD触发term.uk_term_current_marker并返回TERM_CURRENT_CONFLICT。新仓储批量SQL先释放标记、清理上下文，再由Service重读并保存目标；不把未带身份的认证失败计作RED。
2. 老师新登录服务直接打印含accessToken的LoginResult；统一日志规范RED。部署补丁移除两套登录直接日志，由统一切面记录开始/完成和异常类型；外部HTTP日志集中到切面，删去异常消息、完整URI查询串、Header/Body。
3. 原绑定工具遇到老师dump工号user_id时拒绝。新的离线首发工具只在新库按工号校正，冲突失败停止，原业务主键保留。另两项演示身份保留且不自动授权；原本机初始化工具的严格边界不放宽。
4. 新网关最初把过期会话退出转成401；契约RED后恢复上游204幂等空体。真实HTTP首连MySQL8.4发现RSA依赖缺失，补齐PyMySQL[rsa]/cryptography并重新运行；不通过降低认证方式绕过。
5. 实测发现全局ApiResponseAdvice将Health包进业务JSON、使Prometheus返回500。真实HTTP回归RED后，将Advice限定业务API包，保留Actuator原始JSON/文本及身份RawResponse；当前两策略各213项包含此回归。
6. 全量测试的旧部署健康检查预期改为真实Actuator；老师测试默认令牌含全部管理权限，教学班负例显式改用无CV/CTM的教师身份，并同页验证真实全局CV可读。日志测试验证统一切面及Header/Body/查询串均不泄露，不删除负例或放宽预期。

审核另修复官方MySQL入口source脚本时的nounset不兼容、初始化短任务等待/私有仓库认证、测试摘要旧路径。两轴修复复核各0遗留项。

## 4. 当前源码实际结果

| 范围 | 本轮结果 |
| --- | --- |
| RBAC真实MySQL全量 | DDD213、structured213，失败/错误/跳过均0 |
| 老师部署副本真实MySQL全量 | 两策略及架构等499项，失败/错误/跳过均0；含新增独立事务学期回归 |
| 两DDD独立JAR+正式Waitress/MySQL接入 | 最终133次真实HTTP，全部符合预期，含预期拒绝与老师已有缺口 |
| 内部监控与接入服务重启 | 8次真实HTTP，两个DDD健康和Prometheus原始响应正确；Waitress重启后原JWT有效、MySQL业务审计原样回读 |
| Python辅助脚本 | 38项通过，含真实MySQL并发幂等、重建存储回读、应用分区、数据库分页和首发绑定 |
| RBAC前端 | 27项测试、生产构建通过；真实Chrome六个菜单均HTTP200且出现对应Controller与DDD Service日志 |
| 老师Vue | 新副本关闭mock、/api及公网统一身份地址，类型检查与生产构建通过；Vue业务源码未改 |
| 原dump绑定 | 原SQL导入3353新course_dump库；10000条原工号原位校正、补建1名admin；逐人核对10001绑定、原业务ID不变、再次计划0变更 |
| 有效与物理人数 | 191有效教师、9810有效学生、1同账号管理员；保留演示记录后物理teacher192/student9811 |

首次HTTP曾通过136次，最终JAR复验为133次；已有虚构场景的条件创建请求不重复，不以旧次数覆盖本轮结果。

六个菜单验证使用正式构建的本机预览及真实DDD JAR、无网络mock。课堂现场还需在C实际Nginx入口及docker service logs中重做，不拿本机预览冒充Swarm运行证据。源副本和第一次失败报告均保留在.local/deployment-adaptation-20261122，不提交凭据、个人明细、运行产物或原始日志。

## 5. 未执行范围

未清退旧服务、未清理A磁盘、未创建云端Secrets/新库、未构建推送服务器镜像或发布Stack、未执行公网浏览器/服务器重启/节点故障验收。镜像仓库使用操作者有写权限的SWR命名空间，模板不假设老师仓库可写。部署操作和云端结果另行记录。

课程缺Controller的5个入口、个人制批改409、学生建组负责班级等既有业务限制继续按[课程接入](../../course-integration.md)披露；本轮只修复已确认接入阻塞，不声称老师全部业务完成。独立业务审计仍是已认证调用者上报事件，不伪装成课程事务提交证明。
