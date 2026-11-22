# R35 双DDD服务器部署（2026-11-22）

## 1. 授权、来源与边界

用户明确授权正式部署两系统并清退现有服务，随后要求初始化口令便于输入。沿用R34已审核的部署适配，应用源码为496ed3e0b69d59e775f8c61b53d1c47e7fee0a67，分支codex/ddd-swarm-adaptation；老师课程源码固定990cea2cc3686599ef5068945706a13e7e0afc5b，使用独立副本及两份已交付补丁，老师原目录不改。详设Word/PDF/PPT保持原版，T14/T15不关闭。

本轮正式运行两套DDD，各单副本、stop-first；没有将structured部署到同一负载池。37项业务API未改变，未产生需要同步Apifox的契约差异。口令由Docker Secrets提供，真实值仅交付用户及保存在受控忽略目录，不构成项目默认密码。

## 2. 旧环境保留与清退

先保存五节点配置、旧容器日志和C的九项原服务Spec。B原MySQL全库一致性逻辑备份经gzip完整性和SHA核验；E原Mongo停止后冷备并核验。原MySQL/Mongo卷与其他旧卷全部保留，没有volume prune或drop旧库。

A原压测results/runtime压缩归档，约3.8GB，传至E并核对两端SHA256为68f0a72e5ee906117e4e45f82751f21c535b6e28dfbe82302971a70ff51fcba9，E解压完整性检查通过后，才清理A重复归档及原results/runtime。A的约14GB原data目录完整保留。传输临时密钥限定内网来源和固定接收命令，传输后已撤销并删除；用户私钥未上传。

随后清退原exp4/exp4mon九项Swarm服务和A独立node-exporter。保留现有B/C/D/E集群、唯一manager及javaee-proxy网络；A不加入运行集群。新数据卷ooad_mysql_v1与原exp4卷分离。各节点备份根为/opt/ooad/backups/r35-496ed3e-20261122，发布根为/opt/ooad/releases/r35-496ed3e-20261122。

## 3. 真实构建与发布

Docker Hub直连在服务器受限，运行时基础镜像经DaoCloud镜像源拉取并核对摘要。Java21基础仍固定老师SWR镜像；Maven3.9.9和老师真实CloneFactory0.0.2在A容器内构建，固定来源提交40583ff94764d2e758bcb13be17f5c05ea4d6352，库自身5项测试通过。

Linux实建发现两个问题并修复：CopyProcessor摘要按去CR后的固定LF字节校验，消除Windows检出换行差异；Maven下载从中途停滞的BusyBox wget改为带失败退出、超时及重试的curl。归一化只用于校验管道，不修改老师源码。修正通过规范轴和需求轴审核，各0项遗留。

五个应用镜像全部在A串行构建成功，两个前端完成正式生产构建，课程Vue同时通过类型检查。Java产品打包显式跳过测试，不将构建冒充后端完整回归；本轮未改变Java业务实现，原R34回归单独追溯。镜像推送现有SWR命名空间javaee_xmu后回读RepoDigest，C部署配置使用摘要固定版本。

| 镜像 | SHA256摘要 |
| --- | --- |
| rbac-backend | 1288c148bcc4b864829ada0a1a07f1e710554e4e7f59f019e288dd4a58102ea2 |
| rbac-frontend | 40d77f0608ae7948dad29cae224f745ba8f1aa12f7a882603178d7dab646b85f |
| course-backend（含字体运行依赖） | a6c53fd7f3fefb7710e83f1086c5ff242668e47e8a95cb0b38bfcd10679e9318 |
| course-frontend | 15ce7e5a9302f64cad74e4a43d21c97e288d1832c16a02cb3e222640f88f6e8e |
| course-integration | e6392ace29fc8f9a31ee6bc77fb65b1ef01a942ca1ec0102858f4084e9963db8 |
| MySQL8.4.13 | a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902 |
| Prometheus | 6927e0919a144aa7616fd0137d4816816d42f6b816de3af269ab065250859a62 |

Docker20的stack deploy没有--detach选项，本轮使用该版本支持的默认异步部署；一次性任务使用service create --detach并单独等待Complete及ExitCode=0。

## 4. 新库及首次初始化

MySQL健康后另用真实SQL确认rbac_core的12张表及course_audit表就绪，五项产品服务保持0副本。老师原始dump摘要82d9f8f092d2fbac9193ba5573f407cf7f644bca8f52f39a48e3f5733057061b复核通过，只导入新的空course_project库；没有关闭Flyway校验或伪造baseline。

首次import计算全部账号口令后，出现Communications link failure/Connection reset，连接已空闲1118秒；MySQL wait_timeout为28800秒，users/import_sources查询均为0，整批未留下部分结果。保留原失败任务及完整私有日志，Java离线任务改为tasks.mysql直连唯一MySQL任务，避开VIP空闲连接；使用import2后缀的新任务重试，不降低BCrypt强度或改动Java事务。此修正经两轴增量复核，各0项遗留。

另用两个同节点、同镜像、同账号的只读SELECT 1连接做真实对照：均成功建立连接后空闲960秒；mysql虚拟IP再次查询触发OperationalError，tasks.mysql直连再次查询成功。两项探针符合预期，支持上述故障定位，不把重试本身当作修复证据。

重试import、base-preview、base-apply、course-preview、course-apply五项均Complete且ExitCode=0。首次导入得到110组织、10000员工加admin；五角色人数为1/64/106/72/9758。课程首发原位校正10000条工号绑定，新增admin教师绑定，保留两条额外演示对象。

逐人读库核对10001份课程角色及数字subject绑定全部正确；10001个BCrypt10哈希各不相同。有效教师191、学生9810、兼任课程管理员1，物理教师192/学生9811，正式应用2。没有向正式库导入32人虚构夹具。

## 5. 公网与运行验收

七服务按既定节点全部1/1：B数据库，C两个前端及Prometheus，D课程DDD，E RBAC DDD及独立接入服务。所有已配置容器健康检查通过，两个Prometheus目标均up、lastError为空。运行中实测MySQL约470MiB、课程约353MiB、RBAC约337MiB、接入约23MiB；各节点仍有约0.9GiB以上可用内存，未发生OOM或应用异常重启，不据此宣称性能容量或高可用验收。

公网RBAC为http://114.116.254.25:8088/，课程为http://114.116.254.25/。入口8项检查通过，匿名业务链均401；Java、数据库及监控未直接发布公网端口。

真实教学场景经现有生产API创建：新学期、开放的小组制课程、教学班、本部领导/普通员工任教、两名分公司人员选课、小组及作业里程碑。学生上传文件、教师批改已成功，之后完成锁拒绝再次提交409 MILESTONE_DONE_LOCKED，重跑验收读取原作业继续验证，不放宽业务锁。演示对象的私有ID与体验账号留在忽略目录。

首轮成绩导出返回500，实际日志为Fontconfig head is null。课程运行镜像补充fontconfig、DejaVu及文泉驿中文字体并构建字体缓存，保留非root10001运行；更新前后JAR的SHA256均为165021fcfc983f43a8956addd7ec3af8e95efd01ea3482da0ace2b854489e8ae。新镜像真实Excel导出200且内容为XLSX，未修改老师业务源码或POM。

最终直连真实HTTP共62项全部符合预期，覆盖同一admin双系统管理、四类员工代表按教师/学生登录、统一身份八项直连/接入层、无权限快照JWT、受众隔离、独立退出、CORS、当前学期反复切换、组长重复保存、教学班/里程碑对象权限、已有文件/成绩回读、Excel导出及原JWT即时撤权/恢复。首次失败及后续重跑报告保留，不把失败尝试合计为最终通过数量。

真实Chrome六个RBAC菜单均HTTP200，逐次对应容器Controller和DDD Service Logback。普通Chrome另实测admin登录RBAC总览及课程教师入口，显示课程/学期/教师/日志管理菜单。课程三角色Playwright尝试受本机客户端代理拦截，未计为浏览器通过；一次HTTP502未到达服务器Nginx，最终HTTP探针显式直连授权服务器后完成，不修改浏览器安全设置。

独立接入服务、课程DDD、RBAC DDD分别强制更新重启后，新增10项真实HTTP全部通过：接入重启仍接受原会话且审计总数不回退；课程重启后文件字节及成绩原样回读；RBAC重启后原管理/应用JWT均401，重新登录后的两系统功能正常。未将应用重启等同于服务器整体重启。

完成验收后对rbac_core、course_project、course_audit做single-transaction三库备份，约1.3MiB压缩包，SHA256为0259aabc240aab26078b35e2a6c5e0f7b5ed3d6bf496f655a2833b8d9509d877。B、本机私有目录及E均保留副本；E回读SHA和gzip完整性均通过。备份位于上述备份根的production-after-verification，目录700、文件600；未把完整恢复演练记作已通过。

从C回读实际非秘密swarm.env后，通过七服务、两DDD、单副本、固定镜像及公网Origin/配置引用校验；A/C发布目录的三项最终构建/初始化定义摘要一致。真实密码和体验账号只交付用户及私有access.private.md，不进入Git或PR。

## 6. 保留限制

单manager、后端单副本及进程内JWT索引的边界不变，应用更新使用stop-first；本轮不宣称服务器整体重启、节点故障切换、负载性能或高可用通过。老师已有5项缺Controller、个人制批改409及学生建组归属等来源限制仍按[课程接入](../../course-integration.md)披露，不将部署成功等同于老师全部业务已实现。
