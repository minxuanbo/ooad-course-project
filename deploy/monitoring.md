# Prometheus监控设计

## 1. 服务职责与来源

`ooad_prometheus`是当前部署的指标存储与PromQL查询服务，位于C节点；首发时按课程部署架构新建，使用官方Prometheus镜像和30秒采集。此前删除的是旧实验Stack内已备份的监控服务，不代表当前配置已采用老师新CPU口径。

本配置参考用户指定的`spring-fundamentals/4. SpringAOP`：

| 参考文件 | 核对事实 |
| --- | --- |
| conf/prometheus/prometheus.yml | 全局scrape_interval=1s、evaluation_interval=2s、scrape_timeout=1s；Java示例job单独覆盖为2s采集 |
| conf/prometheus/rules/recording.yml | node:cpu_usage_percent采用rate(...[2s])；两规则组仍显式interval=30s，会覆盖全局2s计算 |
| conf/docker/productdemoaop-docker.yml | Prometheus及global模式node-exporter来自老师SWR；示例IP、数据库配置和公网监控端口不套用于本项目 |

本地参考文件读取于2026-11-22，原字节SHA-256分别为`53fe719a875c47cb80f87ddbfe204f2555356b30e2ca6406cee21ee281987187`（prometheus.yml）、`68806bb952ad7b2eab860cec45f853d526ce237b1a84c3bf00d80fe07738d5c1`（recording.yml）。老师目录保持不动；此监控参考不改变CloneFactory固定源码提交。

老师Prometheus镜像内仅有默认配置，实际公式由外部配置文件提供。待发布env固定老师SWR当前回读摘要，避免latest漂移：Prometheus3.7.3，node-exporter1.10.2；完整摘要见[swarm.env.example](swarm.env.example)。不据镜像仓库或服务名称断言CPU规则已生效。

## 2. 采集与计算

[prometheus.yml](prometheus.yml)与[prometheus-rules.yml](prometheus-rules.yml)通过两个Swarm Config挂载。CPU采用老师同一公式：

```promql
(1 - sum(rate(node_cpu_seconds_total{mode="idle"}[2s])) by (instance)
   / sum(rate(node_cpu_seconds_total[2s])) by (instance)) * 100
```

结果`node:cpu_usage_percent{instance=...}`表示每个采集实例所有CPU核合计的非空闲时间比例，单位百分比；不是Java进程CPU，也不是把多个服务器平均到一起。rate按计数器增量计算并处理重置；2秒窗口是计算回看范围，1秒是取样频率，2秒规则interval是生成记录值的频率，三个值必须分别配置。

节点job每1秒采集；两规则组显式interval改为2秒，避免老师样例30秒组覆盖。两个Java job按样例独立覆盖为2秒采集，提供HTTP/JVM/GC/线程等指标。非CPU的QPS、延迟、GC和磁盘速率保留样例5分钟窗口及相应rate5m名称，不能把“CPU改2秒”理解为所有5m指标都改为2秒。

2秒窗口在1秒采集下通常仅有两个有效样本，漏采或初次启动可能暂无CPU结果；不把缺样本填成0%。离线测试覆盖负载、双核/双实例隔离、计数重置、漏采及恢复。线上延迟、DNS发现、实际CPU和存储占用仍需发布后验证，不能用合成样本替代。

## 3. 内网node-exporter

宿主机公式依赖node_cpu_seconds_total，Java Actuator本身只暴露process_cpu_usage、system_cpu_usage等不同口径；不能替换指标名后宣称同一公式。待发布Stack增加`ooad_node-exporter`，global模式在Swarm的B/C/D/E各运行一个任务；A是独立构建机，不在此自动采集范围。

node-exporter只读挂载宿主机/proc、/sys及根目录，显式配置host路径，仅启用CPU、内存、文件系统、磁盘及主机信息采集器。文件系统排除Docker虚拟挂载，96MiB上限/24MiB预留。Prometheus通过overlay DNS `tasks.node-exporter:9100`逐任务发现，不能用单个VIP随机采到不同节点后合并成一条计数器；instance为任务IP:9100，可通过node_uname_info关联主机名，任务重建后形成新时序。

overlay容器的/proc/net/dev仍受网络命名空间限制，只读挂载宿主/proc不等于获得宿主网络统计。因此不启用netdev，也不复制老师两条node_network速率规则，避免把exporter容器流量冒充节点流量。当前保留老师13条中的11条原表达式；宿主网络监控需另行设计采集通道，本次不使用host网络或增加公网端口。

9100和9090均不发布公网端口，不需额外开放华为云安全组。正式应用仍单副本stop-first；监控调整后为8项服务定义（含1个global服务），不是把应用扩成多副本。

## 4. 离线校验与发布边界

使用固定镜像中的真实promtool，在含配置、规则和测试的目录执行：

```bash
promtool check config /etc/prometheus/prometheus.yml
promtool check rules /etc/prometheus/rules/recording.yml
promtool test rules /work/prometheus-rules.test.yml
```

前两项要求按Stack目标路径挂载配置；测试文件在工作目录引用prometheus-rules.yml。可用`docker run --rm --network none --read-only`挂载文件执行；规则测试还须提供`--tmpfs /tmp:rw,noexec,nosuid,size=64m`存放临时时序数据库，既不启动Prometheus，也不连接线上服务。Stack另用`docker stack config`验证Compose展开。

当前线上仍为七项正式服务、旧Prometheus配置。以上为待统一发布的材料，不执行热加载、Stack更新、应用重启或新增线上exporter；北京时间修正与本监控变更一并等待用户发布指示。发布时应使用新RELEASE_ID和镜像摘要，按[部署指南](README.md)执行并另记真实采集及资源结果。
