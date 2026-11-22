# R36 北京时间展示修正与临时服务清理（2026-11-22）

## 1. 授权与执行边界

起点d3ca697e832914da4ac11e02781fccc6c7f49f8e。用户先要求定位网页UTC时间并暂停重新部署，随后明确授权清理不需要的0/1服务，并希望服务器应用日志也显示北京时间。此次只执行8个终态临时服务清理；时间修正完成源码及本机验证，等待用户继续反馈后明确统一发布。没有发布新镜像、更新线上Stack、重启正式应用或修改服务器时区/数据库数据。老师原目录、详设外部文件、T14/T15和草稿PR边界保持。

## 2. 原因及修正

截图2026-11-22T04:28:10.754Z对应北京时间12:28:10.754。只读核对客户端、E主机/容器、B主机/数据库UTC时刻一致；E主机本地+0800，Java容器与MySQL为UTC。不是8小时时钟漂移。RBAC以Clock.systemUTC/Instant记事件，JPA/JDBC按UTC存储，Controller用Instant.toString导出带Z时间，原Vue直接显示该字符串。

网页列表、详情和总览活动现显式按Asia/Shanghai格式化为北京时间，保留毫秒和原UTC机器值/悬浮提示。筛选标签明确北京时间，使用显式+08:00转换UTC，不再依赖浏览器时区；非法日期和日期滚动拒绝。API的UTC Z格式、数据库、JWT时效、Clock及两策略业务实现不改，37项契约未变，不新增Apifox写入。

两个Java服务的Stack环境新增LOGGING_PATTERN_DATEFORMAT=yyyy-MM-dd'T'HH:mm:ss.SSSXXX,Asia/Shanghai。两系统都使用Boot默认Logback格式，课程XML亦引用默认格式；无需改老师源码或JVM全局时区。未来发布后应用消息时间应为2026-11-22T12:37:29.205+08:00。Docker --timestamps额外添加的采集时间仍为UTC，课堂查看命令改用不带该选项；归档可继续保留原始UTC采集时间。

## 3. 实际清理与证据

清理目标固定为2项idle-probe和6项init服务，包括第一次失败import及成功import2。先核对服务名、restart-condition=none、所有任务终态，再保存完整Spec、ID/版本、任务列表/详情及日志；C与本机归档SHA256均为620f6af19fa8e861cdc6e3a2d963b57b44819111ddfeed3501d5e28430ed58d8，并验证8组完整记录。

随后重新核对ID/版本及终态，只删除这8个服务定义。清理前后正式七服务列表完全一致，全部1/1；没有删卷、Secrets、业务数据或初始化结果。归档保存在C的/opt/ooad/backups/r35-496ed3e-20261122/temporary-services-20261122T044112Z.tar.gz及本机忽略目录。旧失败证据仍可追溯，不能因服务列表变干净宣称原失败不存在。

## 4. 本机实际验证

- TDD RED：原查询在UTC与America/Los_Angeles进程中，将北京时间12:28误转为12:28Z/20:28Z；Asia/Shanghai为04:28Z。修正后3种时区均得到04:28Z，并覆盖跨日期边界。
- 前端31项完整测试通过，失败/跳过0；包括北京时间显示、年末跨日、空/坏值、非法日期和毫秒筛选。正式Vue构建通过。
- 本机Chrome在UTC、Asia/Shanghai、America/Los_Angeles三个上下文完成列表/详情/筛选/总览共12项展示检查。网络使用明确的本地展示夹具，不算真实服务器授权或业务验收；截图与原始输出不提交。
- 两个实际JAR的Boot/Logback依赖中，通过真实LoggingSystemProperties环境映射及默认console-appender渲染固定事件，8项探针通过：旧配置在UTC下仍为04:37Z；新配置在3种JVM默认时区下均为12:37:29.205+08:00。

没有修改Java业务代码，未重复运行MySQL全量业务回归，也不借用R35的云端62/8/10项成绩充当本次时间修正已上线证据。正式发布后的真实浏览器与docker service logs回看待后续统一执行。
