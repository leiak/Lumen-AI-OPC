# OPC 120 人企业硬件配置 — 方案 B-阿里云版

> **文档版本**: v1.0 · 2026-10-13
> **作者**: OPC 基础设施组
> **基线**: [HARDWARE-SIZING-120-USERS.md](./HARDWARE-SIZING-120-USERS.md) — 方案 B (机房自建:1×应用服务器 + 1×数据库服务器 + Synology NAS = ~96K CapEx + 12K/yr OpEx)
> **本版目标**: 将方案 B 全部组件一对一映射到阿里云托管服务,**CapEx → 0,OpEx → 约 9.5K/月 (~11.4 万/年)**,**RPO ≤ 5min / RTO ≤ 30min**
> **适用范围**: OPC Spring Cloud 全栈 12 模块 22 容器(ai-core / user-center / agent-hub / billing / finance / notification / crm / community / hr / erp / content / system + job / file / gen + 基础设施 nacos / mysql / redis / minio / qdrant / es / prometheus / grafana),Vue 3 + TypeScript 前端,目标 120 用户,峰值 QPS ~30,LLM (DeepSeek + MiniMax) 网关出口

---

## 0. 目录

- [1. 业务假设与基线复用](#1-业务假设与基线复用)
- [2. 与方案 B 的逐组件映射](#2-与方案-b-的逐组件映射)
- [3. 区域 / 可用区 / 网络规划](#3-区域--可用区--网络规划)
- [4. 计算 / 存储 / 中间件选型](#4-计算--存储--中间件选型)
- [5. 安全组 / 防火墙 / RAM / KMS](#5-安全组--防火墙--ram--kms)
- [6. 备份 / 快照 / 异地容灾](#6-备份--快照--异地容灾)
- [7. 监控 / 告警 / 日志 / 审计](#7-监控--告警--日志--审计)
- [8. 成本估算(CNY)](#8-成本估算cny)
- [9. 与 W79 bootstrap-remote.sh 的兼容改造](#9-与-w79-bootstrap-remotesh-的兼容改造)
- [10. 90 天上线 Roadmap](#10-90-天上线-roadmap)
- [11. 升级 / 缩容触发条件](#11-升级--缩容触发条件)
- [12. 与自建方案 B 的对比与决策建议](#12-与自建方案-b-的对比与决策建议)
- [13. 修订历史](#13-修订历史)

---

## 1. 业务假设与基线复用

本节完全沿用方案 B 的业务假设,只把**物理设备清单替换为阿里云产品清单**,业务参数(用户数、QPS、容器数、存储增长、备份保留)保持一致。

### 1.1 复用基线(摘自方案 B §1)

- **用户规模**: 120 个并发用户,峰值 80 在线,日活 400
- **业务模块**: 12 个 Spring Boot 3 微服务(ai-core 9301 / user-center 9302 / agent-hub 9303 / billing 9304 / finance 9305 / notification 9310 / crm 9312 / community 9316 / hr 9313 / erp 9311 / content 9325 / system 9201)+ RuoYi 公共服务(ruoyi-gateway 8080 / ruoyi-auth 9200 / ruoyi-job 9207 / ruoyi-file 9300 / ruoyi-gen 9210)+ Nacos(8848/9848)+ MySQL(3306)+ Redis(6379)+ MinIO(9000/9001)+ Qdrant(6333)+ ES(9200)+ Prometheus(9090)+ Grafana(3000)
- **目标容量**: 实测 96 GB 内存 / 32 核 CPU(峰值 58 GB / 22 核),响应 380 ms,磁盘 1.4 TB / 1.92 TB
- **网络**: 公网出方向 5 Mbps 平均 / 10 Mbps 峰值,入方向 200 Mbps
- **数据增长**: MySQL 日增 ~1.5 GB,MinIO 日增 ~3 GB,日志日增 ~2 GB,年化增长 ~2 TB

### 1.2 阿里云选型原则(本方案独有)

| 原则 | 取舍 |
|------|------|
| **包年 vs 按量** | 全部资源**包年包月**(稳态业务节省 30-40%),非生产可叠加抢占式实例 |
| **专有域 vs 经典** | VPC + 4 子网隔离(替代方案 B 的 VLAN 隔离) |
| **多 AZ vs 单 AZ** | 主备必须跨 AZ(华东 2 上海 AZ-a + AZ-b);数据库强制跨 AZ HA |
| **托管 vs 自建** | 优先托管服务(RDS / Redis / ES / Nacos / Prometheus)以削减运维;只有 Qdrant / MinIO / 自定义容器保留在 ECS |
| **存储分层** | 热数据 → ESSD PL1 / Redis 持久内存;温数据 → NAS 通用型;冷数据 → OSS 低频 / 归档 |
| **安全合规** | 等保 2.0 三级:启用 KMS 加密 + WAF + 堡垒机 + ActionTrail 审计 + 数据库审计 |

---

## 2. 与方案 B 的逐组件映射

| 方案 B 物理设备 | 方案 B 规格 | 阿里云对应产品 | 阿里云规格 | 替换理由 |
|------------|----------|------------|----------|----------|
| **应用服务器** | Dell PowerEdge R750 · Xeon Gold 6338 · 64 GB DDR4 · 2×480 GB NVMe RAID1 + 1.92 TB NVMe | **ECS g7ne.4xlarge × 1** | 16 vCPU / 64 GB · Intel Xeon Platinum 8369B (Cascade Lake) · 3.1 GHz 基频 / 5.0 GHz 睿频 · 系统盘 ESSD PL1 40 GB + 数据盘 ESSD PL1 500 GB | 单实例托管全部 22 容器,资源与方案 B 1:1 对等(16 物理核 32 线程 vs 16 vCPU 32 线程);保留数据盘用于 MinIO 热数据 + Qdrant 快照 |
| **数据库服务器** | Dell PowerEdge R750 · Xeon Gold 6338 · 64 GB · 2×1.92 TB NVMe RAID10 + 960 GB NVMe (redo) + 4 TB SATA (本地备份) | **RDS for MySQL 8.0 高可用版 mysql.n4.xlarge.2** | 8 vCPU / 16 GB · MySQL 8.0 · 1 主 1 备跨 AZ · 1 TB ESSD PL1 · 备份保留 7 天 · 1 个只读实例(可选) | 托管服务自带主备切换 + binlog + 自动备份;取消本地备份,改 OSS 异地 |
| **自建 Redis** | 单实例 8 GB | **阿里云 Redis 社区版 4 GB 主备版 redis.community.master.2.xdb.4g** | 1 主 1 备 · 4 GB · 持久化 · 跨 AZ 部署 | 托管 Redis 自动容灾,免运维;4 GB 足够 120 用户 QPS 30 的缓存需求 |
| **自建 Elasticsearch** | 3 节点 16 GB | **阿里云 Elasticsearch elasticsearch.n4.small.2 × 3 节点** | 2 vCPU / 4 GB × 3 · 6 节点 / 12 GB · 通用型 · 6 个月日志保留 | 托管 ES 自带 IK 分词器 + Kibana;或可选降级为 ECS 自建(本方案 B 采用托管) |
| **自建 Qdrant** | 单实例 8 GB 向量库 | **ECS 自建 Qdrant(与 MinIO 共用 g7ne.4xlarge 节点)** | 容器部署 4 GB 内存上限,持久化到 NAS 通用型 | Qdrant 无阿里云托管,保留自建,挂载 NAS 共享存储 |
| **Synology DS923+ 备份 NAS** | 4×8 TB RAID 5 (24 TB) | **NAS 通用型 1 TB + 极速型 500 GB** | 通用型 1 TB 用于 backup-mysql-local.sh 落盘,极速型 500 GB 用于 MinIO / Qdrant 高频访问 | NAS 替代本地 NAS,支持 NFS/SMB,免硬件维护 |
| **自建 Nacos** | 容器 8848/9848 | **MSE 微服务引擎 Nacos(专业版)** | 1 节点 1 vCPU / 2 GB · 1 个命名空间 `opc-prod` | 托管 Nacos 自带鉴权 + 推送轨迹 + 灰度;1 节点 ~100 元/月,免运维 |
| **Prometheus + Grafana** | 容器 9090/3000 | **阿里云 Prometheus(容器服务版)+ 阿里云 Grafana** | 1 套基础指标采集 · 1 个 Grafana 工作区 · 50 GB 指标保留 | 免自建,免维护;告警集成 CloudMonitor |
| **固定公网 IP 10 Mbps** | 月费 ~600 RMB | **EIP(弹性公网 IP)+ NAT 网关 + SLB 应用型 5 Mbps** | EIP 1 个 5 Mbps 固定带宽 + SLB 1 实例 5 Mbps + NAT 1 个 20 Mbps | SLB 替代 nginx 公网入口;NAT 让 ECS 主动访问外网(LLM API) |
| **24 口千兆交换机** | 一次性 ~3000 RMB | (取消) | VPC 内部使用 vSwitch 100 Gbps,无物理交换机 | 云端虚拟化网络 |
| **APC UPS 3 kVA** | 一次性 ~6000 RMB | (取消) | 阿里云机房双路市电 + 柴发 + UPS,99.99% SLA | 免自购 |
| **WAF(可选)** | 一次性 ~5000 RMB | **WAF 应用防火墙(高级版)** | 1 实例 · HTTPS 防护 · CC 防护 · Bot 管理 | 替代机房硬件防火墙,弹性扩展 |
| **OSS 异地备份** | 50 GB/月 | **OSS 标准存储 200 GB + 跨区域复制到华南 1(异地容灾)** | 标准存储 200 GB · 跨区域复制 CRR 到 oss-cn-shenzhen | 替代方案 B §6.3 的 OSS 异地副本 |

### 2.1 关键替换说明

1. **应用服务器 ECS**:`g7ne.4xlarge`(通用算力增强型,Intel Cascade Lake 8369B / 8338C)是方案 B Xeon Gold 6338 的直接对应:同 16 核 32 线程、同代架构、AVX-512 指令集。**不推荐 g7 / c7 / r7**:g7 缺 AVX-512,c7 缺多核(单核强),r7 内存成本翻倍。`g7ne` 系列在通用计算 + 内存带宽之间平衡,适合 Java/Spring 应用。
2. **数据库服务器**:从自建切换到 **RDS 高可用版 8 核 16 GB**,而非"等容量"16 核 64 GB。理由:RDS 自带 buffer pool 调优、自动主备切换、binlog 备份、慢 SQL 洞察、CloudDBA,运维成本大幅降低;16 GB buffer pool 对 1 TB 数据集命中率 90%+,且只读实例可随时扩缩。**保留 1 个只读实例**(用于报表 + 数据分析),写入仍走主库。
3. **存储介质**:方案 B 的 2×1.92 TB NVMe RAID 10 由 **RDS ESSD PL1 1 TB** + 备份 7 天 + 增量快照替代。ESSD 是阿里云自研 NVMe + 25 Gbps RDMA,单盘 IOPS 5 万,延迟 0.3 ms,优于自建 RAID10 软阵列。
4. **MinIO**:从自建切换为 **OSS 标准存储**(`oss-cn-shanghai.aliyuncs.com`),前端通过 STS 临时凭证 + 签名 URL 直传,不再占用 ECS 磁盘。Bucket:`opc-prod-attach`(附件)/ `opc-prod-avatar`(头像)/ `opc-prod-backup`(数据库异地备份)。
5. **本地备份 4 TB HDD**:**取消**。改由 RDS 自动备份(7 天 PIT)+ OSS 跨区域复制(华南 1 异地)+ NAS 极速型 500 GB(本地最近 3 天快照)。
6. **Nacos**:从自建切换为 **MSE Nacos 专业版**。理由:MSE Nacos 与 Spring Cloud Alibaba 原生兼容,自带鉴权 + 配置加密 + 推送轨迹;1 节点 1 vCPU/2 GB 约 100 元/月,免维护;1 个命名空间 `opc-prod` 可托管 12 模块 + 公共配置。
7. **Elasticsearch**:从自建切换为 **阿里云 ES 通用型 2 vCPU/4 GB × 3 节点**;或选择自建(降本 ~1.5K/月)。本方案 B 选择托管 ES,原因是 IK 分词器 / Kibana / 集群运维自建成本高。
8. **WAF**:从可选升级为**必选**。阿里云 WAF 高级版支持 HTTPS 卸载 + CC 防护 + Bot 管理 + 防 SQL 注入,800 元/月;比方案 B 一次性 5K 防火墙更划算(且 WAF 弹性伸缩应对突发流量)。

---

## 3. 区域 / 可用区 / 网络规划

### 3.1 区域与可用区

**首选区域**:**华东 2(上海)·可用区 a + 可用区 b**(主备跨 AZ)

| 候选区域 | 优势 | 劣势 | 决策 |
|--------|------|------|------|
| **华东 2(上海)** | 与 RuoYi 生态最近、容器镜像仓库 ACR 默认区域、延迟 ~5 ms 到江浙沪 | 略贵于深圳 | ✅ **首选** |
| 华东 1(杭州) | 与阿里云总部同城,产品最全 | 距离 OPC 用户群略远 | 备选 |
| 华南 1(深圳) | 价格最低(~95% 上海) | 距离用户群远,OSS 跨区域复制目标 | 仅作**异地容灾目标** |
| 华北 2(北京) | 政治合规(等保) | 价格最高 | 不推荐 |

### 3.2 VPC + 交换机规划(替代方案 B §6.1)

**VPC 名称**:`vpc-opc-prod` · CIDR `10.0.0.0/16`

| 交换机 | 名称 | CIDR | 用途 | 替代方案 B 哪个网段 |
|-------|------|------|------|----------------|
| vSwitch-1 | `vsw-opc-public-a` | 10.0.1.0/24 | SLB 后端 ECS / 公网入口 | 方案 B §6.1 公网/应用网段 |
| vSwitch-2 | `vsw-opc-app-b` | 10.0.2.0/24 | 应用 ECS 节点(本方案 B 单台 ECS) | 方案 B 应用内网 10.0.1.0/24 |
| vSwitch-3 | `vsw-opc-db-b` | 10.0.3.0/24 | RDS / Redis / ES(各 AZ 跨 AZ 部署) | 方案 B 数据内网 10.0.2.0/24 |
| vSwitch-4 | `vsw-opc-ops-a` | 10.0.4.0/24 | Bastionhost / KMS / CloudMonitor | 方案 B 运维网 10.0.4.0/24 |
| vSwitch-5 | `vsw-opc-backup-b` | 10.0.5.0/24 | NAS 挂载点(同 AZ 高带宽) | 方案 B 备份网 10.0.3.0/24 |

> **VPC 跨 AZ 设计原则**:每个交换机跨 2 个 AZ(本方案选 a + b),AZ 故障时 ECS / RDS / Redis / ES 跨 AZ 容灾自动切换。

### 3.3 网络拓扑图

```
                    ┌──────────────────────────────────────────┐
                    │        公网用户 / 移动端                  │
                    └──────────────┬───────────────────────────┘
                                   │ HTTPS
                                   ▼
                    ┌──────────────────────────────────────────┐
                    │  WAF 应用防火墙(高级版)· 阿里云          │
                    │  HTTPS 卸载 · CC 防护 · Bot 管理         │
                    └──────────────┬───────────────────────────┘
                                   │ 80/443
                                   ▼
                    ┌──────────────────────────────────────────┐
                    │  SLB 应用型 5 Mbps(华东 2 AZ-a)        │
                    │  健康检查 / 跨 AZ 主备                    │
                    └──────────────┬───────────────────────────┘
                                   │ 8080
                                   ▼
                    ┌──────────────────────────────────────────┐
                    │  ECS g7ne.4xlarge(华东 2 AZ-b)            │
                    │  22 容器(Docker Compose)                  │
                    │  - ruoyi-gateway 8080                     │
                    │  - opc-ai-core 9301 ... opc-content 9325  │
                    │  - nacos-mysql / redis / minio / qdrant   │
                    │  - prometheus 9090 / grafana 3000         │
                    └────┬─────────┬─────────┬──────────┬───────┘
                         │         │         │          │
        ┌────────────────┘         │         │          └────────────────┐
        ▼                          ▼         ▼                           ▼
┌────────────────┐      ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│  RDS MySQL     │      │  Redis 4GB     │  │  NAS 通用型     │  │  OSS 标准存储   │
│  高可用 8c/16G │      │  主备版        │  │  1 TB          │  │  200 GB        │
│  跨 AZ 主备    │      │  跨 AZ         │  │  备份挂载点     │  │  异地复制到     │
│  ESSD PL1 1 TB │      │                │  │                │  │  华南 1 深圳    │
└────────────────┘      └────────────────┘  └────────────────┘  └────────────────┘
   vsw-opc-db-b           vsw-opc-db-b         vsw-opc-backup-b    跨区域 CRR

     ┌──────────────────────────────────────────┐
     │  NAT 网关(主动出公网 → LLM API)        │
     │  EIP 1 个 20 Mbps 共享带宽              │
     └──────────────────────────────────────────┘
                          │
                          ▼
                 ┌────────────────────┐
                 │  DeepSeek + MiniMax  │
                 │  OpenAI 兼容 API   │
                 └────────────────────┘
```

### 3.4 跨区域异地容灾(替代方案 B §6.3)

- **OSS 跨区域复制 CRR**:`opc-prod-backup` Bucket 同步到华南 1 深圳 `opc-prod-backup-dr`
- **RDS 跨区域灾备实例**:**本方案 B 不启用**(成本 4K/月,RPO 1 分钟级别已满足需求)
- **NAS 异地容灾**:通过 OSS 跨区域复制实现(NAS 极速型与 OSS 互通)
- **DMS 数据迁移**:如未来需要跨 region 迁移,使用阿里云 DTS(Data Transmission Service)

---

## 4. 计算 / 存储 / 中间件选型

### 4.1 ECS 应用节点(替代方案 B §5.1)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| 实例规格 | **ecs.g7ne.4xlarge** | 16 vCPU / 64 GB · Intel Xeon Platinum 8369B · 5.0 GHz 睿频 · 25 Gbps 内部带宽 | ¥3,024 |
| 系统盘 | ESSD PL1 | 40 GB · 5K IOPS · 0.3 ms 延迟 | ¥10 |
| 数据盘 | ESSD PL1 | 500 GB · 5K IOPS(挂载 /var/lib/docker + /mnt/minio) | ¥150 |
| 公网带宽 | 不直接绑 EIP | 通过 NAT 网关主动出 | — |
| 镜像 | 公共镜像 centos_8_5_x64_20G_alibase_20231220.vhd | Rocky 8.10 替代品 | — |
| 操作系统许可 | CentOS 8 → Anolis 8(免费兼容) | Alibaba Cloud Linux 3 | ¥0 |
| 包年优惠 | 1 年期 8.3 折 | 享 1 年 75 折 + 3 年 5 折 | -¥4,536/年 |
| **小计** | — | — | **¥3,184/月** · **¥2,562/月(包年 8.3 折)** |

> **关键决策**:**只 1 台 ECS g7ne.4xlarge**,与方案 B 单应用服务器 1:1 对应。**不部署 2 台 ECS + SLB 主备**,理由:120 用户 22 容器的负载远低于单台 16 核 64 GB 上限,多实例增加维护成本与跨实例 session 同步问题。如需升级,直接升配到 `g7ne.8xlarge`(32 vCPU / 128 GB),无需分实例。

### 4.2 RDS for MySQL 高可用版(替代方案 B §5.2)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| 实例规格 | **mysql.n4.xlarge.2** | 8 vCPU / 16 GB · MySQL 8.0 · 高可用版(1 主 1 备) | ¥2,180 |
| 存储 | ESSD PL1 | 1 TB · 5K IOPS · 0.3 ms 延迟 | ¥500 |
| 主备 AZ | 跨 AZ-a → AZ-b | 自动主备切换 < 30s | ¥0(包含) |
| 备份 | 数据备份 + binlog | 7 天保留 · 免费额度外按量 | ¥100(预估) |
| 性能洞察 | 慢 SQL + CloudDBA | 免费 | ¥0 |
| 只读实例 | **1 个 mysql.n4.large.2**(4 vCPU / 8 GB) | 用于报表查询(1 主 1 只读 1 备,共 3 实例) | ¥1,100 |
| 包年优惠 | 1 年 8.3 折 | 主实例 + 只读 | -¥2,734/年 |
| **小计(主 + 只读)** | — | — | **¥3,880/月** · **¥3,128/月(包年)** |

> **关键决策**:**8 核 16 GB 而非 16 核 64 GB**:RDS 8 核 16 GB 的 buffer pool 等效自建 32-40 GB(因 RDS 优化更好),且只读实例分担读压力;自建 16 核 64 GB 单实例实际只用 24 GB buffer pool。**金融版(3 节点)不启用**:多 1 节点 +1.2K/月,120 用户 QPS 30 不足以触发强一致需求。

### 4.3 阿里云 Redis 社区版(替代自建 Redis)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| 实例规格 | **redis.community.master.2.xdb.4g** | 1 主 1 备 · 4 GB · 跨 AZ | ¥200 |
| 持久化 | AOF + RDB 双开 | 免费 | ¥0 |
| 包年优惠 | 1 年 8.3 折 | — | -¥160/年 |
| **小计** | — | — | **¥200/月** · **¥166/月(包年)** |

> 自建 Redis 8 GB 的方案 B 是过度配置。120 用户 + QPS 30 的 Spring Cloud 缓存(只有会话 + 字典 + 分布式锁),4 GB 足够,**节省 50%**。

### 4.4 阿里云 Elasticsearch(替代自建 ES)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| 集群规格 | **elasticsearch.n4.small.2** × 3 节点 | 2 vCPU / 4 GB × 3 = 6 vCPU / 12 GB · 通用型 | ¥1,500 |
| 存储 | ESSD PL1 | 100 GB × 3 = 300 GB(含 1 副本) | ¥150 |
| IK 分词器 | 内置 | 免费 | ¥0 |
| Kibana | 内置 | 免费 | ¥0 |
| 包年优惠 | 1 年 7 折 | 集群 + 存储 | -¥1,260/年 |
| **小计** | — | — | **¥1,650/月** · **¥1,155/月(包年)** |

> **可选降级**:如不开通阿里云 ES(节省 1.5K/月),可直接使用 ECS 自建 ES 单节点 4 GB(足够 12 模块的 ops 索引使用),本方案 B 选择托管 ES 以获得 Kibana + 集群运维。

### 4.5 阿里云 MSE Nacos(替代自建 Nacos)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| 实例规格 | **MSE Nacos 专业版 · 1 节点** | 1 vCPU / 2 GB · 1 个命名空间 `opc-prod` | ¥100 |
| 配置推送轨迹 | 内置 | 免费 | ¥0 |
| 鉴权 | 内置 | 免费 | ¥0 |
| **小计** | — | — | **¥100/月** · **¥83/月(包年)** |

> **自建 Nacos 替代**:如不开通 MSE,在 ECS g7ne.4xlarge 上容器化部署 Nacos(资源消耗 ~1 GB 内存 / 1 vCPU),**节省 100 元/月**,但失去托管推送轨迹 + 一键回滚 + 鉴权。本方案 B 选择 MSE。

### 4.6 阿里云 NAS 文件存储(替代 Synology DS923+)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| 文件系统类型 | **通用型 + 极速型** | 通用型 1 TB(备份)+ 极速型 500 GB(MinIO 临时 / Qdrant 快照) | ¥230 + ¥300 = ¥530 |
| 协议 | NFS 4.0 + SMB 3.0 | 同时支持 Linux / Windows 挂载 | ¥0 |
| 跨 AZ 复制 | 同 region 跨 AZ 复制 | 99.99% 持久性 | ¥0 |
| 包年优惠 | 1 年 8 折 | — | -¥130/年 |
| **小计** | — | — | **¥530/月** · **¥424/月(包年)** |

> **关键替换**:Synology DS923+ 4×8 TB RAID 5 = 24 TB 可用容量;阿里云 NAS 通用型 1 TB + 极速型 500 GB = 1.5 TB,远小于自建。理由:120 用户的备份数据(PIT 7 天 + 数据库 7 天备份 + MinIO 临时)实际占用 ~500 GB,1.5 TB 已含 200% 冗余。如需 24 TB 容量,改为通用型 5 TB(¥1,150/月)。

### 4.7 OSS 对象存储(替代方案 B §6.3 异地副本)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| Bucket | `opc-prod-attach` / `opc-prod-avatar` / `opc-prod-backup` | 3 个 | — |
| 存储类型 | 标准存储 | 200 GB 总容量 | ¥30 |
| 跨区域复制 | CRR 到华南 1(深圳)`opc-prod-backup-dr` | 异地容灾 | ¥30(异地存储) |
| 请求次数 | PUT/GET ~50 万次/月 | 免费额度内 | ¥0 |
| CDN 回源 | 静态资源 CDN 回源 100 GB | — | ¥18 |
| **小计** | — | — | **¥78/月** · **¥60/月(包年)** |

### 4.8 阿里云容器镜像服务 ACR(可选)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| 实例规格 | **个人版**(够用) | 100 个命名空间 · 无限仓库 | ¥0 |
| 跨区域同步 | 华东 2 → 华南 1(异地容灾) | 个人版不支持 | — |
| **企业版**(可选) | 1 实例 · 1 个地域 | 含跨地域同步 + 漏洞扫描 | ¥300/月 |
| **小计(本方案 B)** | 个人版 | — | **¥0** |

> **关键决策**:**本方案 B 使用个人版**,免 300 元/月。如需等保三级 + 异地镜像,改用企业版。

### 4.9 SLB / EIP / NAT 网关

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| SLB 应用型 | **1 实例 5 Mbps 公网固定带宽** | 替代 nginx 公网入口,含 7 层 HTTPS | ¥400 |
| EIP | **1 个 5 Mbps**(SLB 绑定) | 包含 | — |
| NAT 网关 | **1 个 20 Mbps 共享带宽** | ECS 主动出公网 → DeepSeek / MiniMax | ¥300 + ¥80(EIP) |
| 包年优惠 | 1 年 7-8 折 | — | -¥600/年 |
| **小计** | — | — | **¥780/月** · **¥660/月(包年)** |

> **关键决策**:**SLB 替代方案 B 的 nginx 公网入口**,5 Mbps 固定带宽 100 GB 流量(~7,200 元/月 vs 5 Mbps 包月),比按量计费更划算。

### 4.10 WAF / Bastionhost / KMS / CloudMonitor

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| WAF 应用防火墙 | **高级版** | 1 实例 · HTTPS 防护 · CC 防护 · Bot 管理 | ¥800 |
| 堡垒机 Bastionhost | **基础版** | 1 实例 · 50 资产 · 2 管理员 | ¥100 |
| KMS 密钥管理 | 1 个主密钥 | 10 个版本 + 自动轮转 | ¥50 |
| CloudMonitor | 1 个项目 | 1 个告警联系人 · 100 个指标 | ¥50 |
| Prometheus(容器服务) | 1 个实例 | 50 GB 指标保留 · 30 天 | ¥50 |
| 阿里云 Grafana | 1 个工作区 | 免费 14 天试用,长期 ¥0(自带) | ¥0 |
| ActionTrail 审计 | 1 个跟踪 | 90 天免费 | ¥0 |
| **小计** | — | — | **¥1,050/月** · **¥840/月(包年)** |

### 4.11 阿里云日志服务 SLS(替代 ELK 自建)

| 配置项 | 选型 | 规格 | 月费(CNY)|
|------|------|------|---------|
| SLS Project | `opc-prod-logs` | 1 个 · 100 GB · 90 天保留 | ¥200 |
| Logtail | 12 个 ECS 容器日志接入 | 免费 | ¥0 |
| **小计** | — | — | **¥200/月** · **¥160/月(包年)** |

---

## 5. 安全组 / 防火墙 / RAM / KMS

### 5.1 安全组(Security Group)规划(替代方案 B §6.2 防火墙)

| 安全组 | 名称 | 关联资源 | 入方向规则 | 出方向规则 |
|------|------|---------|----------|----------|
| sg-opc-public | 公网入口 | SLB / WAF | 80/443 任意源 · 8080 仅 sg-opc-public-self | 全开 |
| sg-opc-app | 应用层 | ECS g7ne.4xlarge | 80/443/8080 仅 sg-opc-public · 22 仅 sg-opc-bastion · 3306/6379/9200 仅 sg-opc-db-self | 全开(LLM API) |
| sg-opc-db | 数据层 | RDS / Redis / ES | 3306/6379/9200 仅 sg-opc-app · 22 仅 sg-opc-bastion | 仅 sg-opc-backup-self |
| sg-opc-backup | 备份层 | NAS / OSS | 2049/111 仅 sg-opc-app + sg-opc-db | 跨区域复制到 sg-opc-dr |
| sg-opc-bastion | 堡垒机 | Bastionhost | 22 限 IP 白名单(运维 1-2 个固定 IP) | 全开(SSH) |
| sg-opc-ops | 运维层 | KMS / CloudMonitor | 443 仅 sg-opc-bastion + sg-opc-app | 全开 |

### 5.2 RAM 角色 / 策略(替代方案 B 手动 SSH 密钥)

| 角色 | 用途 | 授权策略 | 关联身份 |
|------|------|--------|---------|
| `ram-opc-ecs` | ECS 实例 RAM 角色 | AliyunECSReadOnlyAccess + AliyunRDSReadOnlyAccess + AliyunOSSFullAccess | ECS g7ne.4xlarge |
| `ram-opc-deploy` | 部署用户(RAM 用户) | AliyunContainerRegistryReadOnly + AliyunECSFullAccess(限定 tag) | 部署工程师 / CI/CD |
| `ram-opc-ops` | 运维用户 | BastionhostAdministrator + AliyunCloudMonitorReadOnly | 运维工程师 |
| `ram-opc-appcode` | 应用代码调用 OSS / KMS | AliyunOSSReadOnlyAccess(限定 bucket `opc-prod-attach`) + AliyunKMSCryptoUser | Spring Boot 应用 STS 凭证 |

> **关键改进**:不直接给 ECS 配置 AccessKey,而是通过 RAM 角色 + STS 临时凭证(默认 1 小时过期),代码从 `http://100.100.100.200/latest/meta-data/ram/security-credentials/` 拉取,完全避免 AK 泄露。

### 5.3 KMS 密钥管理

| 密钥 | 用途 | 轮转策略 | 调用方 |
|------|------|---------|--------|
| `kms-opc-jasypt` | Jasypt 加密密码(`JASYPT_PASSWORD` 环境变量) | 90 天自动轮转 · 保留 5 个历史版本 | ECS 启动脚本拉取 |
| `kms-opc-db-root` | RDS root 密码 | 180 天轮转 | 堡垒机 / RDS 控制台 |
| `kms-opc-redis-auth` | Redis 鉴权密码 | 90 天轮转 | ECS 启动脚本 |
| `kms-opc-llm-key` | DeepSeek / MiniMax API Key | 180 天轮转 · 人工触发 | opc-ai-core 启动脚本 |
| `kms-opc-oss-sts` | OSS 临时凭证签名 | 1 小时自动过期 | opc-ai-core / opc-crm 附件上传 |

> **集成方式**:Spring Boot 启动时通过 `--kms.jasypt.password=$(aliyun kms GetSecretValue --SecretName kms-opc-jasypt)` 注入,或使用阿里云 KMS SDK 在 `OpcNacosStartupChecker` 中初始化时拉取。**禁止明文写入 bootstrap.yml 或 .env**。

### 5.4 WAF 防护策略

| 防护模块 | 策略 |
|--------|------|
| **CC 防护** | 单 IP 100 QPS 阈值 · 验证码挑战 · 30 分钟封禁 |
| **Bot 管理** | 搜索引擎白名单(百度 / Google / Bing) · 异常 UA 拦截 · 人机验证 |
| **SQL 注入** | 阿里云 WAF 内置规则集 · 拦截 99% 注入尝试 |
| **XSS** | 内置规则集 · 拦截常见 payload |
| **0day 防护** | 阿里云 7×24 应急响应 · CVE 24 小时内下发规则 |
| **HTTPS 卸载** | 上传证书到 WAF · 后端 SLB 走 80 · 减少 ECS 证书负担 |
| **地理封禁** | 业务仅服务国内 + 部分东南亚,封禁非常用国家 |

---

## 6. 备份 / 快照 / 异地容灾

### 6.1 RDS 自动备份(替代方案 B §6.3 mysqldump)

| 备份类型 | 频率 | 保留 | 存储位置 | 恢复点 |
|--------|------|------|---------|--------|
| 数据快照 | 每天 02:00 | 7 天 | RDS 自动备份盘 | PIT 恢复 |
| Binlog | 实时 | 7 天 | RDS 自动备份盘 | 任意时间点 |
| 跨区域备份 | 每天 04:00 | 30 天 | OSS 华南 1(深圳)`opc-prod-backup-dr` | 异地容灾 |
| 长期归档 | 每周 1 次 | 365 天 | OSS 归档存储(0.0033 元/GB/月) | 合规归档 |

**RPO ≤ 5 分钟**(binlog 实时归档)·**RTO ≤ 30 分钟**(RDS 主备切换 < 30s + 应用自动重连)

### 6.2 ECS 快照(替代方案 B §6.3 整机备份)

| 快照类型 | 频率 | 保留 | 用途 |
|--------|------|------|------|
| 系统盘快照 | 每周日 03:00 | 4 周 | 整机恢复 |
| 数据盘快照(500 GB) | 每天 03:00 | 7 天 | MinIO / Qdrant 数据恢复 |
| 关键节点(发布前) | 手动 | 永久 | 版本回滚 |

**RTO ≤ 15 分钟**(ECS 镜像 + 数据盘快照恢复)

### 6.3 OSS 跨区域复制(CRR)

| Bucket | 源 | 目标 | 规则 | 延迟 |
|------|---|------|------|------|
| `opc-prod-backup` | 华东 2(上海) | 华南 1(深圳)`opc-prod-backup-dr` | 实时同步 | < 5 分钟 |
| `opc-prod-attach` | 华东 2(上海) | 华南 1(深圳)`opc-prod-attach-dr` | 实时同步 | < 5 分钟 |
| `opc-prod-avatar` | 华东 2(上海) | (无需异地) | — | — |

### 6.4 NAS 快照(替代 Synology Snapshot Replication)

| 文件系统 | 快照频率 | 保留 | 用途 |
|--------|--------|------|------|
| 极速型 500 GB | 每 4 小时 | 7 天(42 个快照) | Qdrant 临时数据 |
| 通用型 1 TB | 每天 02:00 | 30 天 | 数据库 PIT 文件 |

### 6.5 灾难恢复演练(替代方案 B §6.3 周演练)

- **季度 1**:RDS 主备切换演练(在维护窗口手动触发主备切换,验证应用自动重连 < 30s)
- **季度 2**:ECS 整机恢复演练(从系统盘快照启动新 ECS,挂载数据盘,验证 Docker Compose 起容器 < 15 分钟)
- **季度 3**:OSS 跨区域恢复演练(从华南 1 拉取 `opc-prod-backup-dr` 到新 ECS,验证完整性 + 恢复关键表)
- **季度 4**:NAS 快照恢复演练(创建 NAS 临时文件系统,从快照恢复关键数据集,验证)

---

## 7. 监控 / 告警 / 日志 / 审计

### 7.1 CloudMonitor 监控指标(替代自建 Prometheus 部分)

| 指标类别 | 关键指标 | 告警阈值 | 通知方式 |
|--------|---------|--------|---------|
| ECS CPU / 内存 / 磁盘 | CPU > 80% 持续 5 分钟 · 内存 > 85% · 磁盘 > 85% | 短信 + 钉钉 | 5 分钟内 |
| RDS 连接数 / QPS / TPS | 连接数 > 800 · 主备延迟 > 5s · 慢 SQL > 100 条/小时 | 短信 | 5 分钟内 |
| Redis 内存 / 连接 | 内存 > 80% · 连接 > 5,000 | 钉钉 | 10 分钟内 |
| SLB 带宽 / 健康检查 | 出带宽 > 4.5 Mbps(峰值预警)· 健康检查失败 | 短信 | 1 分钟内 |
| EIP / NAT 带宽 | 出口带宽 > 18 Mbps(预警 90%) | 钉钉 | 10 分钟内 |
| 容器健康 | 任意容器连续 3 次健康检查失败 | 钉钉 | 1 分钟内 |
| 自定义业务 | LLM 调用失败率 > 5% · 支付回调失败率 > 1% | 钉钉 | 5 分钟内 |

### 7.2 阿里云 Prometheus(替代自建 Prometheus 部分)

- 指标采集:容器服务 ACK 集群自动接入,本方案 B 用 ECS 自建 Prometheus 容器 + 阿里云托管 Prometheus 接入
- 指标保留:**50 GB · 30 天**(本方案 B 的 Prometheus 容器自存 + 阿里云托管并存)
- 关键面板:
  - **OPC 业务大盘**:QPS / DAU / 转化率 / 错误率(拉取 opc-ai-core / opc-crm 的 Actuator metrics)
  - **LLM 大盘**:DeepSeek / MiniMax 调用延迟 / Token 用量 / 失败率
  - **基础设施大盘**:ECS / RDS / Redis / ES CPU 内存磁盘 / 主备延迟

### 7.3 日志服务 SLS(替代自建 ELK)

- **接入方式**:Logtail 部署在 ECS,自动采集 12 模块容器 stdout / stderr
- **存储**:100 GB · 90 天热保存 · 90 天后转冷归档(0.0033 元/GB/月)
- **检索**:SQL 92 语法 · 实时秒级 · 支持 trace ID 全链路
- **告警**:基于日志关键词 + 阈值触发,例如 "ERROR" 出现 > 50 条/分钟
- **审计日志**:ActionTrail 推送至 SLS,保留 365 天(等保要求)

### 7.4 审计与合规

| 审计项 | 工具 | 保留 | 用途 |
|------|------|------|------|
| 阿里云操作审计 | **ActionTrail** | 365 天 | 所有 RAM 用户的控制台 / API 操作 |
| 数据库审计 | **RDS SQL 审计** | 90 天 | 所有 DDL / DML / 慢 SQL |
| SSH 登录审计 | **Bastionhost 操作录像** | 90 天 | 运维登录回放 |
| 应用审计 | **业务日志** | 90 天 | 邀请 / 支付 / 工资单等关键操作 |
| WAF 拦截日志 | **WAF 日志** | 30 天 | 攻击溯源 |

---

## 8. 成本估算(CNY)

### 8.1 月度成本明细(包年 8 折平均)

| # | 类别 | 阿里云产品 | 规格 | 包月价(元)| 包年 8 折后(元/月)|
|---|------|----------|------|---------|------------------|
| 1 | **计算** | ECS g7ne.4xlarge | 16 vCPU / 64 GB | 3,024 | **2,562** |
| 2 | **数据库** | RDS MySQL 8.0 高可用 + 1 只读 | 8c/16G + 4c/8G · 1 TB ESSD | 3,880 | **3,128** |
| 3 | **缓存** | Redis 社区版 4 GB 主备 | 跨 AZ | 200 | **166** |
| 4 | **搜索** | Elasticsearch 3 节点 12 GB | 100 GB × 3 | 1,650 | **1,155** |
| 5 | **配置中心** | MSE Nacos 专业版 1 节点 | 1c/2G | 100 | **83** |
| 6 | **文件存储** | NAS 通用型 1 TB + 极速型 500 GB | — | 530 | **424** |
| 7 | **对象存储** | OSS 标准 200 GB + 跨区域 CRR | — | 78 | **60** |
| 8 | **负载均衡** | SLB 5 Mbps + NAT 20 Mbps | 2 实例 | 780 | **660** |
| 9 | **安全** | WAF 高级版 + Bastionhost + KMS | 1+1+1 | 950 | **780** |
| 10 | **监控/日志** | CloudMonitor + Prometheus + SLS | 50 GB + 100 GB | 300 | **240** |
| 11 | **审计** | ActionTrail + RDS SQL 审计 | 免费额度内 | 0 | **0** |
| 12 | **CDN** | 100 GB 流量 | 静态资源 | 18 | **15** |
| 13 | **域名/DNS** | 1 个域名 + DNSSEC | 阿里云 DNS 免费版 | 1 | **1** |
| 14 | **ACR** | 个人版 | — | 0 | **0** |
| — | **合计** | — | — | **¥11,511/月** | **¥9,274/月(包年)** |

> **包年总额**:**¥111,288/年**(主年),**续费 9.3 万/年**。

### 8.2 三年 TCO 对比(关键决策依据)

| 维度 | 方案 B(自建) | 方案 B-阿里云 |
|------|------------|-------------|
| **第一年总成本** | 96K CapEx + 12K OpEx = **108K** | 0 CapEx + 11.5K × 12 = **138K** |
| **第二年总成本** | 12K OpEx = **12K** | 9.3K × 12 = **111K** |
| **第三年总成本** | 12K OpEx = **12K** | 9.3K × 12 = **111K** |
| **三年累计** | **132K** | **360K** |
| **盈亏平衡点** | — | **约 4 年后**(硬件折旧 5 年周期) |

### 8.3 可选降本(进一步压缩 20-30%)

| 降本项 | 节省 | 风险 |
|------|------|------|
| **不启用阿里云 ES**,改用 ECS 自建 4 GB | -¥1,155/月 | 自建 ES 集群运维 |
| **不启用 MSE Nacos**,改用 ECS 自建 Nacos | -¥83/月 | 失去推送轨迹 + 一键回滚 |
| **不启用 SLB**,改用 nginx 公网入口 + EIP | -¥660/月 | 失去 4 层负载均衡 + 健康检查 |
| **不启用 WAF**,改用 nginx mod_security | -¥780/月 | 失去阿里云 0day 防护 |
| **降配 RDS** 到 mysql.n4.large.2(4c/8G) | -¥800/月 | 性能可能瓶颈(待评估) |
| **降配 ECS** 到 g7ne.2xlarge(8c/32G) | -¥1,300/月 | CPU 内存可能不够(实测峰值 22 核) |
| **降配 Redis** 到 1 GB | -¥100/月 | 容量风险 |
| **降配 NAS** 到通用型 500 GB | -¥230/月 | 备份保留缩短到 14 天 |
| **最小化配置合计** | **约 ¥5,200/月** | 风险高,需逐项验证 |

### 8.4 计费模式选择

| 模式 | 适用 | 节省 |
|------|------|------|
| **包年包月 1 年** | 稳态生产 | ~20% |
| **包年包月 3 年** | 长期生产 | ~50% |
| **按量付费** | 测试 / 短期 | 0(基线) |
| **抢占式实例** | 非生产(测试) | ~70% |

**推荐**:生产 1 年 8.3 折 + 3 年 5 折;非生产(测试 / 预发)用抢占式 + 按量。

---

## 9. 与 W79 bootstrap-remote.sh 的兼容改造

### 9.1 现有 W79 部署脚本回顾

W79 `bootstrap-remote.sh` 是 5 阶段 idempotent 部署脚本:
- **Stage 1**:基础环境(Rocky 8.10 / Docker 24 / Docker Compose)
- **Stage 2**:基础设施(Nacos 8848 / MySQL 3306 / Redis 6379 / MinIO 9000 / Qdrant 6333)
- **Stage 3**:12 模块镜像(ai-core / user-center / ... / system)
- **Stage 4**:前端 nginx(端口 8079)
- **Stage 5**:健康检查(22 项)

### 9.2 阿里云部署改造点

| 阶段 | 自建 W79 | 阿里云改造 | 改造脚本 |
|------|--------|---------|---------|
| Stage 1 | 装 Docker / Compose | ECS 公共镜像已含 Docker · 装 **aliyun-cli 3.x** | `bootstrap-aliyun-stage-1.sh` |
| Stage 2 | 启 MySQL / Redis / MinIO / Qdrant / ES | **跳过容器化部署**,改用 **aliyun-cli 创建 RDS / Redis / ES / MSE Nacos** | `bootstrap-aliyun-stage-2.sh` |
| Stage 3 | 拉镜像 + docker-compose up | ECS 容器化部署 12 模块 + 基础设施容器(Qdrant / MinIO / 自建 Nacos 兜底) | `bootstrap-aliyun-stage-3.sh` |
| Stage 4 | 装 nginx + 前端 | SLB + WAF + 阿里云前端 CDN | `bootstrap-aliyun-stage-4.sh` |
| Stage 5 | 健康检查 | **22 项容器健康 + 9 项托管服务健康** = 31 项 | `bootstrap-aliyun-stage-5.sh` |

### 9.3 aliyun-cli 集成

**安装**:`curl -O https://aliyuncli.alicdn.com/aliyun-cli-linux-3.0.215-amd64.tgz && tar -xzf && mv aliyun /usr/local/bin/`

**配置**:`aliyun configure set --profile opc-prod --mode AK --access-key-id ${ALIYUN_AK} --access-key-secret ${ALIYUN_SK} --region cn-shanghai`

**Stage 2 核心命令**(伪代码):

```bash
# 创建 RDS
aliyun rds CreateDBInstance --Engine MySQL --EngineVersion 8.0 \
  --DBInstanceClass mysql.n4.xlarge.2 --DBInstanceStorage 1000 \
  --PayType Prepaid --Period 12 \
  --VPCId vpc-opc-prod --VSwitchId vsw-opc-db-b \
  --MasterHaConfig '{"ZoneId":"cn-shanghai-a","StandbyZoneId":"cn-shanghai-b"}'

# 创建 Redis
aliyun r-kvstore CreateInstance --InstanceClass redis.community.master.2.xdb.4g \
  --RegionId cn-shanghai --VpcId vpc-opc-prod --VSwitchId vsw-opc-db-b

# 创建 ES
aliyun elasticsearch CreateInstance --instanceCategory general \
  --nodeAmount 3 --nodeSpec elasticsearch.n4.small.2 \
  --dataNodeAmount 3 --dataNodeSpec elasticsearch.n4.small.2 \
  --dataNodeDiskType cloud_ssd --dataNodeDiskSize 100

# 创建 MSE Nacos
aliyun mse CreateCluster --ClusterType=Nacos --ClusterVersion=2.0.0 \
  --InstanceCount=1 --MSEPayType=PayBySpec
```

### 9.4 健康检查增强(31 项)

原 W79 22 项容器健康 + 新增 9 项托管服务健康:

| # | 检查项 | 命令 |
|---|------|------|
| 1-22 | 22 个容器 `/actuator/health`(原 W79) | `curl -s http://localhost:PORT/actuator/health` |
| 23 | RDS 主实例可用 | `aliyun rds DescribeDBInstanceAttribute --DBInstanceId ...` |
| 24 | RDS 备实例健康 | `aliyun rds DescribeDBInstanceHA --DBInstanceId ...` |
| 25 | Redis 主从同步 | `aliyun r-kvstore DescribeInstances` |
| 26 | ES 集群状态 | `aliyun elasticsearch DescribeInstances` |
| 27 | MSE Nacos 推送 | `curl -s http://MSE_IP:8848/nacos/v1/cs/health` |
| 28 | SLB 健康检查 | `aliyun slb DescribeLoadBalancerHealth` |
| 29 | WAF 域名解析 | `dig +short opc.example.com` |
| 30 | OSS 异地复制延迟 | `aliyun oss GetBucketReplicationProgress` |
| 31 | KMS 密钥可访问 | `aliyun kms ListKeys` |

---

## 10. 90 天上线 Roadmap

### 阶段 1(第 1-3 周):账号 / 网络 / 安全基线

| 周 | 任务 | 交付物 |
|---|------|--------|
| W1 | 1. 申请阿里云企业账号 + 完成实名认证 + 充值 5K<br>2. 申请 VPC + 5 个交换机 + 5 个安全组<br>3. 申请 RAM 用户(部署 / 运维 / 应用)<br>4. 申请 KMS 5 个密钥 | 阿里云控制台资源就绪 |
| W2 | 1. 申请 ECS g7ne.4xlarge + 安装 aliyun-cli<br>2. 申请 Bastionhost + 配置白名单<br>3. 申请 SLB + 绑定 EIP<br>4. 配置 WAF 高级版 + 上传证书 | 基础网络 + SSH 通道就绪 |
| W3 | 1. 申请 RDS 高可用 1 主 1 备 1 只读 + 创建 12 模块 DB<br>2. 申请 Redis 4 GB 主备<br>3. 申请 ES 3 节点 + Kibana<br>4. 申请 MSE Nacos + 命名空间 `opc-prod` | 托管服务就绪 |

### 阶段 2(第 4-7 周):应用容器化 + 部署

| 周 | 任务 | 交付物 |
|---|------|--------|
| W4 | 1. 改造 `bootstrap-remote.sh` → 阿里云版本(stage 1-2)<br>2. Docker Compose 适配(改用环境变量注入托管服务地址)<br>3. Aliyun 镜像仓库 ACR 个人版创建 12 模块仓库 | 部署脚本就绪 |
| W5 | 1. Stage 3:12 模块镜像构建 + 推送 ACR<br>2. ECS 部署 Qdrant / MinIO / Prometheus / Grafana 容器<br>3. nginx 前端容器化 + SLB 绑定 | 22 容器全 UP |
| W6 | 1. 启动 22 容器 + 31 项健康检查<br>2. 配置 12 模块 Jasypt 密码 KMS 注入<br>3. 配置 OSS / KMS 凭证 RAM 角色注入 | 端到端联通 |
| W7 | 1. 性能压测(120 用户并发)<br>2. 灾备演练(RDS 主备切换 + ECS 快照恢复)<br>3. 等保 2.0 三级测评预约 | 性能达标 + 等保完成 50% |

### 阶段 3(第 8-12 周):监控 / 告警 / 优化

| 周 | 任务 | 交付物 |
|---|------|--------|
| W8 | 1. 接入 CloudMonitor 31 个告警规则<br>2. 接入 SLS 12 模块日志 + trace ID<br>3. 配置 钉钉 / 短信 通知 | 监控告警就绪 |
| W9 | 1. 接入 Prometheus 业务指标 + Grafana 大盘<br>2. 接入 ActionTrail + RDS SQL 审计<br>3. 配置 Bastionhost SSH 录像 | 审计就绪 |
| W10 | 1. WAF 规则调优(CC 防护阈值 / 误拦截白名单)<br>2. SLB + CDN 优化 + 缓存策略<br>3. 跨区域 OSS 复制验证 | 安全 / 网络优化 |
| W11 | 1. 等保 2.0 三级测评现场测评 + 整改<br>2. 文档化:RECOVERY-ALIYUN.md + RUNBOOK<br>3. 团队培训(运维 / 开发 / 安全) | 等保 2.0 三级通过 |
| W12 | 1. 切流 10% → 50% → 100%(2 周灰度)<br>2. 监控大盘 + 告警阈值调优<br>3. **正式上线** | 阿里云生产环境 100% 上线 |

---

## 11. 升级 / 缩容触发条件

### 11.1 升级触发

| 资源 | 当前规格 | 升级条件 | 升级目标 |
|------|---------|---------|---------|
| ECS | g7ne.4xlarge (16c/64G) | CPU > 80% 持续 30 分钟 或 内存 > 85% 持续 30 分钟 | g7ne.8xlarge (32c/128G) |
| RDS 主 | n4.xlarge.2 (8c/16G) | CPU > 80% 持续 30 分钟 或 连接数 > 800 | n4.2xlarge.2 (16c/32G) |
| RDS 只读 | n4.large.2 (4c/8G) | 读 QPS > 5,000 或 延迟 > 100ms | n4.xlarge.2 (8c/16G) |
| Redis | 4 GB 主备 | 内存 > 80% 或 连接 > 8,000 | 8 GB 主备 |
| ES | 3 节点 12 GB | 节点 CPU > 85% 或 索引 > 1 TB | 5 节点 20 GB |
| SLB | 5 Mbps | 出带宽 > 4.5 Mbps 持续 5 分钟 | 10 Mbps |
| NAS | 1 TB 通用型 | 使用 > 80% | 2 TB 通用型 |

### 11.2 缩容触发(成本优化)

- 业务量下降 > 30% 持续 30 天
- 灰度环境 / 测试环境长期空闲:切换为抢占式实例(节省 70%)

### 11.3 降级方案(紧急)

- **RDS 故障**:5 分钟内自动主备切换;RTO ≤ 30s
- **ECS 故障**:从最新快照启动新 ECS(15 分钟),应用自动重连
- **Redis 故障**:5 分钟内主从切换;RTO ≤ 5 分钟
- **LLM API 故障**:熔断降级到 MiniMax 备用 provider(opc-ai-core 已有 fallback)

---

## 12. 与自建方案 B 的对比与决策建议

### 12.1 维度对比

| 维度 | 方案 B(自建) | 方案 B-阿里云 | 优劣 |
|------|------------|------------|------|
| **CapEx** | 96K(一次性) | 0 | 阿里云 ✅ |
| **第一年 OpEx** | 12K | 111K(包年 8 折) | 自建 ✅ |
| **三年 TCO** | 132K | 360K | 自建 ✅ |
| **运维人力** | 需要专职 DBA + 系统工程师 | DBA / 系统工程师需求减半(托管服务) | 阿里云 ✅ |
| **可靠性** | 99.9% SLA(无补偿) | 99.95% SLA + 等保 2.0 三级 | 阿里云 ✅ |
| **灾备** | 同城机柜 + OSS 异地 | 多 AZ + 跨区域复制 + 快照 | 阿里云 ✅ |
| **弹性** | 需提前采购 | 分钟级扩缩容 | 阿里云 ✅ |
| **合规** | 需自建等保三级(成本高) | 阿里云平台已过等保三级,业务系统复用 | 阿里云 ✅ |
| **数据安全** | 自管机房门禁 + 硬盘消磁 | 阿里云 KMS 加密 + 等保 + 异地复制 | 阿里云 ✅ |
| **响应速度** | 采购 + 部署需 2-4 周 | 分钟级创建 | 阿里云 ✅ |
| **3 年后成本** | 维护费 12K/yr(无折旧) | 持续 110K/yr | 自建 ✅ |

### 12.2 决策建议

| 场景 | 推荐方案 | 理由 |
|------|--------|------|
| **初创公司 / 现金流紧张** | 自建方案 B | 96K 一次性 + 12K/yr 比 110K/yr 友好 |
| **已盈利 / 注重合规** | 阿里云方案 B | 等保 2.0 + 弹性 + 免运维 |
| **120 人企业 + 5 年规划** | 阿里云 3 年 5 折 | 3 年 5 折后 ≈ 5.5 万/yr,接近自建 12K/yr 4.6 倍 |
| **核心数据在境内部署要求** | 自建 / 阿里云专有云 | 公有云合规性需逐项评估 |
| **跨国业务 / 多 region** | 阿里云多 region | 阿里云全球 region + 跨 region 复制 |
| **快速上线 / MVP 阶段** | 阿里云 + 按量付费 | 1 天即可上线,按量计费 |

### 12.3 混合方案(折中)

如预算敏感但需享受阿里云 SLA,推荐:

1. **核心托管**:RDS / Redis / ES(数据库与缓存是关键,故障恢复慢)
2. **应用自建**:ECS g7ne.4xlarge(自建 22 容器,节省 ECS 升级成本)
3. **WAF + SLB 必选**:公网入口防护不可省
4. **MSE Nacos 选配**:可改 ECS 自建 Nacos 节省 100 元/月
5. **OSS + NAS 必选**:异地容灾需托管服务
6. **CloudMonitor + SLS 必选**:日志与监控自建成本高

**混合方案成本**:**~7,500 元/月**(较全阿里云降 19%)

---

## 13. 修订历史

| 版本 | 日期 | 作者 | 变更 |
|------|------|------|------|
| v1.0 | 2026-10-13 | OPC 基础设施组 | 初始发布,基于 [HARDWARE-SIZING-120-USERS.md](./HARDWARE-SIZING-120-USERS.md) 方案 B 一对一映射阿里云产品 |

---

## 附录 A:阿里云 SLB / NAT / EIP 选型速查

| 场景 | 选型 | 规格 |
|------|------|------|
| 7 层 HTTPS 入口 | SLB 应用型 | 5 Mbps 起,可平滑升级到 100 Mbps |
| 4 层 TCP 入口 | SLB 网络型 | 仅 TCP 转发,无 HTTP 解析 |
| 公网 IP | EIP 弹性公网 IP | 5-100 Mbps 包年 |
| 主动出公网 | NAT 网关 + 共享带宽 | 20-50 Mbps 适合 12 模块 + LLM |
| 跨 AZ 高速互联 | vSwitch + 25 Gbps 内部带宽 | 免费 |

## 附录 B:阿里云 RDS for MySQL 8.0 高可用版规格

| 实例规格族 | vCPU | 内存 | 最大连接数 | 适用场景 |
|----------|------|------|-----------|--------|
| n4.large.2 | 2 | 4 GB | 600 | 测试 / 预发 |
| n4.xlarge.2 | 4 | 8 GB | 1,200 | 小型生产 |
| **n4.xlarge.2**(本方案 B)| 8 | 16 GB | 2,500 | 120 用户生产 |
| n4.2xlarge.2 | 8 | 32 GB | 5,000 | 中型生产(升级目标) |
| n4.4xlarge.2 | 16 | 64 GB | 10,000 | 大型生产 |
| n4.8xlarge.2 | 32 | 128 GB | 20,000 | 旗舰生产 |

## 附录 C:参考资料

- [HARDWARE-SIZING-120-USERS.md](./HARDWARE-SIZING-120-USERS.md) — 自建方案 B 基线
- [RECOVERY.md](./RECOVERY.md) — W79 部署恢复文档
- [deploy.sh](./deploy.sh) — W79 一键部署脚本
- [W79 Remote SSH 全栈部署](../../README.md) — W79 部署总结
- [阿里云 ECS 实例规格族](https://help.aliyun.com/document_detail/25378.html)
- [阿里云 RDS for MySQL 文档](https://help.aliyun.com/document_detail/26192.html)
- [阿里云 Redis 文档](https://help.aliyun.com/document_detail/26351.html)
- [阿里云 VPC 专有网络](https://help.aliyun.com/document_detail/34217.html)
- [阿里云等保 2.0 合规白皮书](https://help.aliyun.com/document_detail/188432.html)
- [MSE 微服务引擎 Nacos](https://help.aliyun.com/document_detail/141805.html)

---

**方案选择决策**:
- 若 **首年预算 ≤ 12 万**且**5 年内不扩容** → 选自建方案 B
- 若 **首年预算 ≥ 12 万**或**3 年内可能扩容 2-3 倍**或**需等保 2.0 三级认证** → 选阿里云方案 B
- 若 **现金流紧张 + 长期规划** → 选混合方案(核心托管 + 应用自建,7.5K/月)
- **本推荐**:120 人企业未来 3 年 1.5 倍增长 → **阿里云方案 B + 3 年 5 折**(总成本 ≈ 5.5 万/yr × 3 = 16.5 万,优于自建 3 年 13.2 万,享受弹性 + 免运维 + 等保)
