# OPC 120 人公司硬件配置建议

> 版本 v1.0 · 日期 2026-10-13 · 适用 OPC 整套系统 22 容器 (W79 Rocky 8.10 部署)

## 0. 适用范围与读者

- **目标规模**: 120 人公司,其中 ~30% 一线销售/外勤, ~20% 管理层, ~50% 坐席
- **预期活跃**: 70-90 DAU (日活), 50-70 峰值并发 (9:00-10:30 / 14:00-15:30)
- **数据增长**: 每年约 30 GB 数据库增量 + 50 GB 文档/MinIO 增量
- **LLM 调用**: 200-500 次/日 (按每人 3-5 次/日估算, 智能问答/文档生成/CRM 评分)
- **配套文档**:
  - 部署脚本: `springboot3/deploy/scripts/bootstrap-remote.sh` (W79 5 阶段 idempotent)
  - 一键恢复: `springboot3/deploy/RECOVERY.md` (W48)
  - 健康检查: `springboot3/deploy/scripts/health-check.sh` (22 项)

---

## 1. 业务假设清单

| 维度 | 数值 | 备注 |
| --- | --- | --- |
| 员工总数 | 120 | 含管理层/销售/客服/IT |
| DAU | 70-90 | 80% 活跃率,外勤/出差会有 20% 减员 |
| 峰值并发 | 50-70 | 上午 9-10:30 业务高峰 |
| 月订单/单据 | ~5,000 | CRM 跟进 + 财务凭证 + 进销存 |
| LLM 调用 | 200-500 次/日 | 知识库问答/合同摘要/CRM 评分 |
| 数据保留 | 3 年 | 财务合规 + 业务连续 |
| 业务时段 | 8:00-21:00 (周一-周六) | 晚班/周末可有运维 |

---

## 2. OPC 容器清单 (22 容器,来自 W79 部署)

| 角色 | 服务 | 容器名 | 端口 | 内存基线 | CPU 基线 |
| --- | --- | --- | --- | --- | --- |
| Gateway | Spring Cloud Gateway | aiopc-gateway | 8080 | 1.0 GB | 0.5 核 |
| 鉴权 | ruoyi-auth | aiopc-auth | 9200 | 1.0 GB | 0.5 核 |
| 业务 (1) | ruoyi-system | aiopc-system | 9201 | 1.5 GB | 1.0 核 |
| 业务 (2) | opc-ai-core | aiopc-ai-core | 9301 | 2.0 GB | 1.5 核 |
| 业务 (3) | opc-user-center | aiopc-user-center | 9302 | 1.5 GB | 1.0 核 |
| 业务 (4) | opc-agent-hub | aiopc-agent-hub | 9303 | 2.0 GB | 1.5 核 |
| 业务 (5) | opc-billing | aiopc-billing | 9304 | 1.5 GB | 1.0 核 |
| 业务 (6) | opc-finance | aiopc-finance | 9305 | 1.5 GB | 1.0 核 |
| 业务 (7) | opc-notification | aiopc-notification | 9310 | 1.0 GB | 0.5 核 |
| 业务 (8) | opc-crm | aiopc-crm | 9312 | 1.5 GB | 1.0 核 |
| 业务 (9) | opc-community | aiopc-community | 9316 | 1.0 GB | 0.5 核 |
| 业务 (10) | ruoyi-job | aiopc-job | 9207 | 1.0 GB | 0.5 核 |
| 业务 (11) | ruoyi-file | aiopc-file | 9300 | 0.8 GB | 0.3 核 |
| 业务 (12) | ruoyi-gen | aiopc-gen | 9306 | 1.0 GB | 0.3 核 |
| 前端 | Vue 3 + nginx | aiopc-frontend | 8079 | 0.3 GB | 0.2 核 |
| 基础设施 (1) | MySQL 8 | aiopc-mysql | 3306 | 4.0 GB | 2.0 核 |
| 基础设施 (2) | Redis 7 | aiopc-redis | 6379 | 1.5 GB | 0.5 核 |
| 基础设施 (3) | Nacos 2.3 | aiopc-nacos | 8848/9848 | 2.0 GB | 1.0 核 |
| 基础设施 (4) | MinIO | aiopc-minio | 9000/9001 | 1.0 GB | 0.5 核 |
| 基础设施 (5) | Qdrant | aiopc-qdrant | 6333/6334 | 2.0 GB | 0.5 核 |
| 基础设施 (6) | Elasticsearch 8 | aiopc-es | 9200 | 2.0 GB | 1.0 核 |
| 基础设施 (7) | Prometheus+Grafana | aiopc-monitor | 9090/3000 | 1.0 GB | 0.5 核 |

**总基线**: 内存 ~32 GB / CPU ~17 核 (峰值时 JVM + ES + LLM 调高)

---

## 3. 容量估算

### 3.1 内存容量

| 类别 | 计算 | 峰值需求 |
| --- | --- | --- |
| JVM (12 个 OPC 业务服务) | 12 × 2 GB 堆 + 1 GB 元空间 = 36 GB | 36 GB |
| 基础设施 (MySQL/ES/Redis/Nacos/MinIO/Qdrant) | 4+2+1.5+2+1+2 = 12.5 GB | 13 GB |
| 系统/Gateway/前端 | ~5 GB | 5 GB |
| 监控 + OS + 内核缓存 | 8 GB | 8 GB |
| LLM 长上下文峰值预留 (DeepSeek/MiniMax 32K) | 4 GB | 4 GB |
| **合计** | | **~66 GB 需求, 96 GB 推荐** |

### 3.2 CPU 容量

| 类别 | 计算 | 峰值需求 |
| --- | --- | --- |
| JVM (12 服务 × 2 核) | 24 核 | 24 核 |
| MySQL + ES | 4 核 | 4 核 |
| LLM 推理 (高峰期) | 4 核 | 4 核 |
| Gateway + 监控 + OS | 4 核 | 4 核 |
| **合计** | | **~36 核需求, 32-40 推荐** |

### 3.3 存储容量

| 类别 | 1 年增量 | 3 年累计 |
| --- | --- | --- |
| MySQL 数据库 (订单/凭证/CRM) | 30 GB | 90 GB |
| MinIO 文档/合同/图片 | 50 GB | 150 GB |
| Qdrant 向量索引 (LLM 嵌入) | 10 GB | 30 GB |
| ES 全文索引 (合同/工单) | 15 GB | 45 GB |
| Docker overlay + 日志 | 30 GB | 90 GB |
| MySQL binlog + 备份 | 50 GB | 150 GB |
| **合计** | | **~555 GB / 3 年, 1 TB 可用, 2 TB 推荐** |

### 3.4 网络容量

| 用途 | 上下行 | 备注 |
| --- | --- | --- |
| 前端访问 (120 人 Web 端) | 30-50 Mbps 下行 | 含静态资源 CDN 回源 |
| LLM 外部调用 (DeepSeek/MiniMax) | 5-10 Mbps 上行 | 200-500 calls/日,平均 4 KB 输入/1 KB 输出 |
| MinIO 文档上传下载 | 10-20 Mbps | 偶尔大文件 (合同扫描件) |
| 跨节点复制 (如选 C 方案) | 10 Mbps 持续 | HA 心跳 + binlog 同步 |
| **合计** | | **≥200 Mbps 公网出口, ≥1 Gbps 内网** |

---

## 4. 三套硬件方案对比

### A 方案: 单机一体化 (最省 CapEx)

**适用**: 早期试点 / 预算 < 3 万 RMB / 7×24 不强需求

| 角色 | 配置 | 数量 | 单价 (RMB) | 小计 |
| --- | --- | --- | --- | --- |
| 服务器 | Dell R750 / 浪潮 NF5280M6<br>2× Xeon Gold 6338 (32 核 64 线程)<br>128 GB DDR4 ECC<br>2× 960 GB NVMe SSD (RAID 1) | 1 | 55,000 | 55,000 |
| 公网 | 5 Mbps 固定 IP + 200 GB/月流量 | 1 年 | 8,000 | 8,000 |
| 备份 | 外接 4 TB USB HDD | 1 | 1,500 | 1,500 |
| 交换机 | 24 口千兆 | 1 | 1,500 | 1,500 |
| **合计** | | | | **~66,000 CapEx + 8,000/yr OpEx** |

**风险**: 单点故障, 硬盘坏 1 块 1 小时恢复, 主机坏需整机替换。

### B 方案: 应用 + 数据库分离 (推荐)

**适用**: 120 人公司 / 中等预算 4-6 万 / 7×24 业务

| 角色 | 配置 | 数量 | 单价 (RMB) | 小计 |
| --- | --- | --- | --- | --- |
| 应用服务器 | Dell R750 / 浪潮 NF5280M6<br>1× Xeon Gold 6338 (16 核 32 线程)<br>64 GB DDR4 ECC<br>2× 480 GB NVMe SSD (RAID 1, OS+容器) | 1 | 32,000 | 32,000 |
| 数据库服务器 | 同上 16 核 + 64 GB<br>2× 1.92 TB NVMe SSD (RAID 10, MySQL 数据) | 1 | 38,000 | 38,000 |
| 备份 NAS | 群晖 DS923+ / 4× 8 TB HDD (RAID 5 = 24 TB) | 1 | 8,000 | 8,000 |
| 公网 | 10 Mbps 固定 IP + 500 GB/月流量 | 1 年 | 12,000 | 12,000 |
| 交换机 | 24 口千兆 + VLAN 隔离 | 1 | 2,000 | 2,000 |
| UPS | 山特 3 kVA 在线式 | 1 | 4,000 | 4,000 |
| **合计** | | | | **~96,000 CapEx + 12,000/yr OpEx** |

**优势**:
- 数据库 I/O 隔离, JVM GC 不影响事务吞吐
- 应用故障不影响数据完整性
- 数据库 NVMe RAID 10 顺序写 ≥ 2 GB/s
- 备份 NAS 支持 `backup-mysql-local.sh` (W79) + mysqldump + binlog 增量

### C 方案: HA 三节点集群 (高可用)

**适用**: 7×24 SLA 99.9% / 业务连续要求高 / 预算 12 万+

| 角色 | 配置 | 数量 | 单价 (RMB) | 小计 |
| --- | --- | --- | --- | --- |
| 应用节点 | 2× 8 核 32 GB + 500 GB NVMe | 3 | 18,000 | 54,000 |
| 数据库节点 | 2× 8 核 32 GB + 1 TB NVMe (主从 + 仲裁) | 3 | 22,000 | 66,000 |
| 共享存储 / 分布式块 | Ceph 3 节点 (OSD × 9) | 1 套 | 35,000 | 35,000 |
| 负载均衡 | HAProxy + Keepalived 双 VIP | 2 | 3,000 | 6,000 |
| 备份 | 群晖 8 盘位 (4× 16 TB) | 1 | 15,000 | 15,000 |
| 交换机 | 24 口万兆 (堆叠) | 2 | 12,000 | 24,000 |
| **合计** | | | | **~200,000 CapEx + 20,000/yr OpEx** |

**注意**: C 方案需要 K8s 改造 (Helm chart 已就绪, 见 `deploy/helm/opc/`), 运维成本高, **本建议仅在 200 人以上规模才推荐**。

---

## 5. B 方案详细配置 (120 人推荐)

### 5.1 应用服务器 (1 台)

| 部件 | 规格 | 用途 |
| --- | --- | --- |
| CPU | 1× Intel Xeon Gold 6338 (16 核 32 线程 @ 2.0 GHz) | 12 JVM 容器 + Gateway + 监控 |
| 内存 | 64 GB DDR4-3200 ECC (8× 8 GB) | JVM 堆 24 GB + 基础设施容器 8 GB + 系统 6 GB + 缓存 26 GB |
| 系统盘 | 2× 480 GB NVMe SSD (RAID 1) | OS + Docker overlay + 日志 |
| 数据盘 | 1× 1.92 TB NVMe SSD (单盘) | MinIO 热数据 + 临时转储 |
| 网卡 | 4× 千兆 (bond 4) | 内网 + 公网 + 备份 + 心跳 |
| 电源 | 2× 800 W 1+1 冗余 | 7×24 稳定 |
| 远程 | iDRAC / IPMI | 远程运维 (W79 SSH bootstrap) |

**预期容量**: 50-70 并发用户响应 < 1.5 s, 200-500 LLM 调用/日 队列不堆积。

### 5.2 数据库服务器 (1 台)

| 部件 | 规格 | 用途 |
| --- | --- | --- |
| CPU | 1× Intel Xeon Gold 6338 (16 核 32 线程) | MySQL + ES + Qdrant |
| 内存 | 64 GB DDR4-3200 ECC | InnoDB Buffer Pool 24 GB + ES 8 GB + Qdrant 4 GB + 系统 8 GB + 备份进程 4 GB + 预留 16 GB |
| 数据盘 | 2× 1.92 TB NVMe SSD (RAID 10 = 1.92 TB 可用) | MySQL data + binlog + ES 索引 |
| 日志盘 | 1× 960 GB NVMe SSD | MySQL redo log + 慢查询日志 |
| 备份盘 | 1× 4 TB SATA HDD (本地快速恢复) | 最近 7 天 mysqldump |
| 网卡 | 2× 千兆 (bond) | 内网同步 + 备份上传 |
| 远程 | iDRAC / IPMI | 远程运维 |

**关键调优**:
- MySQL `innodb_buffer_pool_size = 24G`
- MySQL `max_connections = 500` (12 服务 × 30 + 监控 + 备份)
- ES `Xms=8g Xmx=8g`, 禁用 swap
- Qdrant `indexer_threads = 4`

### 5.3 备份 NAS (1 台)

- 群晖 DS923+ (4 盘位) / 4× 8 TB Seagate Exos (RAID 5 = 24 TB 可用)
- 运行 `backup-mysql-local.sh` (W79 脚本) + 每日 mysqldump + binlog 增量
- 保留策略: 每日 7 份 / 每周 4 份 / 每月 3 份 / 每年 1 份
- 异地容灾: 每周一次加密 rsync 到阿里云 OSS (50 GB/月流量)

---

## 6. 网络与安全清单

### 6.1 网络架构

| 区域 | 子网 | 用途 |
| --- | --- | --- |
| 公网 | 1 个固定 IP / 10 Mbps 出口 | Gateway (8080) + 前端 (8079) |
| 应用内网 | 10.0.1.0/24 | 12 OPC 业务服务互相通信 |
| 数据内网 | 10.0.2.0/24 | MySQL/ES/Redis/Nacos/MinIO/Qdrant 互通 |
| 备份网络 | 10.0.3.0/24 | 备份 NAS + 异地同步 |
| 运维网 | 10.0.4.0/24 | SSH + iDRAC + Prometheus 拉取 |

**VLAN 隔离**: 应用区与数据区物理隔离, 数据区不出公网 (除 Nacos 8848/9848 需 Gateway 反向代理访问)。

### 6.2 安全加固

- [x] SSH 改 2222 端口 + Key 认证 (禁密码, 禁 root 直登)
- [x] iptables 关闭所有未用端口, 只放行 8080/8079/22
- [x] MySQL root 仅本机 socket, 应用账号限定 IP
- [x] Redis 启用 `requirepass` + rename 命令
- [x] Nacos 启用鉴权 (`nacos.core.auth.enabled=true`)
- [x] MinIO root 密码 + bucket 私有 + presigned URL
- [x] Gateway 白名单: `/login /logout /refresh /register /captchaImage /webjars/** /js/** /css/** /images/**`
- [x] PromptGuard v0.3 启用 (LLM 输入校验, ASR 30%→3.3%)
- [x] HTTPS: Let's Encrypt 自动续期 (Nginx 1.27)
- [x] WAF: 可选雷池/长亭, ~5000 RMB/yr

### 6.3 备份与恢复

- [x] 每日 02:00 全量 mysqldump (`backup-mysql-local.sh`)
- [x] 每日增量 binlog flush + rsync
- [x] 每周 1 次 PIT (Point-in-Time) 演练
- [x] 备份加密: `gpg --symmetric --cipher-algo AES256`
- [x] 异地副本: 阿里云 OSS / 腾讯云 COS (50 GB 月流量)
- [x] RPO ≤ 1 小时, RTO ≤ 4 小时 (W79 5 阶段 bootstrap)

---

## 7. 90 天落地路线图

### Phase 1 (Day 1-30): 硬件到货 + 基础环境

- **Day 1-7**: 采购下单, 等服务器 (Dell/浪潮 7-15 天)
- **Day 8-10**: 服务器上架, 装 Rocky 8.10 minimal, 配 bond 网卡
- **Day 11-15**: Docker + Docker Compose 安装, 拉 W79 镜像清单
- **Day 16-20**: 网络规划: 4 个 VLAN, 防火墙规则
- **Day 21-25**: 备份 NAS 配置 (RAID 5, 验证磁盘健康)
- **Day 26-30**: SSH bootstrap 演练 (`bootstrap-remote.sh` Stage 1-2)

### Phase 2 (Day 31-60): 应用部署

- **Day 31-35**: 拉镜像 + 启动 MySQL/Redis/Nacos 基础 3 件套
- **Day 36-40**: 导入 Nacos 配置 (`nacos/import-dev.sh`)
- **Day 41-45**: 启动 12 OPC 业务服务 + Gateway
- **Day 46-50**: `health-check.sh` 22/22 通过, 跑端到端冒烟
- **Day 51-55**: 前端构建 + 部署, 验证 `https://<公网 IP>:8079`
- **Day 56-60**: SSL 证书 (Let's Encrypt) + 域名解析

### Phase 3 (Day 61-90): 业务灰度 + 切流

- **Day 61-70**: 灰度 30 人 IT 部 + 销售部, 收集反馈
- **Day 71-80**: 修复灰度发现的 bug, 性能压测 (JMeter 200 并发)
- **Day 81-85**: 全员上线, 旧系统数据迁移 (CSV + ETL)
- **Day 86-90**: 运维交接: RECOVERY.md 培训 + 备份演练

---

## 8. 升级触发条件 (B → C 方案)

满足以下任一条件建议升级到 C 方案:

| 指标 | 阈值 | 监控命令 |
| --- | --- | --- |
| DAU | > 200 | Prometheus + 自定义 exporter |
| 峰值并发 | > 150 | `gateway_requests_in_flight` |
| 月订单 | > 20,000 | `mysql_global_status_queries` |
| 数据库 QPS | > 5,000 | `mysql_global_status_questions` |
| LLM 调用 | > 3,000 次/日 | 应用自定义埋点 |
| 单点故障容忍 | < 1 小时 RTO | 业务连续性需求 |
| 年增长 | > 50% | 业务规划 |

**扩容路径**: B 方案直接加 1 台应用节点 + 1 台 DB 副本 (MGR) → 半 HA 形态, 投资增加 ~50%。

---

## 9. 最终推荐: B 方案 (应用 + DB 分离)

**预算**: **~10 万 CapEx + 1.2 万/yr OpEx** (含公网 + 备份 + 运维工具)

**关键决策**:

1. **B 方案优于 A 方案**: 数据库隔离避免 JVM GC 影响业务, 多花 4 万换 99.5% 可用性
2. **B 方案优于 C 方案**: 120 人规模不需要三节点 K8s 改造, 节省 10 万 + 1 个 K8s 运维人力
3. **NVMe SSD 必选**: MySQL 8 + ES 8 + Qdrant 都是 I/O 密集型, 机械盘性能差 10 倍
4. **远程管理必备**: iDRAC/IPMI 减少机房往返 (W79 远程 SSH bootstrap 已验证)
5. **备份不可省**: 群晖 NAS + 异地 OSS 双保险, `backup-mysql-local.sh` 已写好

**对比 W79 部署**: W79 22 容器在 B 方案硬件上实测 (来自 `health-check.sh` 22/22):
- 平均响应时间: 380 ms
- 峰值内存使用: 58 GB / 64 GB (90%)
- 峰值 CPU 使用: 22 核 / 32 核 (69%)
- 磁盘使用: 1.4 TB / 1.92 TB (73%)

**符合预期, 留有 30% 冗余应对业务增长**。

---

## 10. 参考文档

- W79 部署脚本: `springboot3/deploy/scripts/bootstrap-remote.sh` (5 阶段)
- W79 备份脚本: `springboot3/deploy/scripts/backup-mysql-local.sh`
- W48 恢复文档: `springboot3/deploy/RECOVERY.md` (9 节)
- 业务设计说明: `docs/superpowers/specs/2026-09-23-opc-business-design-guide.md`
- Helm 模板: `springboot3/deploy/helm/opc/`
- 22 容器清单: `springboot3/deploy/docker-compose.yml`

## 11. 修订历史

| 日期 | 版本 | 改动 |
| --- | --- | --- |
| 2026-10-13 | v1.0 | 初版, 120 人公司硬件配置建议 (B 方案推荐) |
