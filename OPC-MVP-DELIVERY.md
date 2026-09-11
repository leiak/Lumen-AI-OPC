# OPC-Agent-Community（OAC）MVP 交付总览

> 13 周 MVP 排期（来自 `init.md`）的落地交付清单。
> 本文档是 7 人团队接手后的"快速上手 + 验收"入口。

---

## 0. 仓库结构

```
0401-lumen-opc/
├─ init.md                              # 完整需求规格
├─ OPC-MVP-DELIVERY.md                  # 本文档（交付总览）
│
├─ springboot3/             # 后端基座
│  ├─ pom.xml                           # 父 POM（已加 Spring AI / LangChain4j / Qdrant / Jasypt）
│  ├─ opc-common/                       # OPC 公共模块（常量、异常、Snowflake）
│  ├─ ruoyi-modules/
│  │   ├─ opc-ai-core/                  # AI 中台（LLM Gateway + Agent Runtime + 记忆）
│  │   ├─ opc-agent-hub/                # Agent 中心（市场 / 雇佣 / 任务）
│  │   ├─ opc-user-center/              # 用户中心
│  │   ├─ opc-billing/                  # 计费
│  │   └─ opc-finance/                  # 财务 Agent
│  ├─ sql/
│  │   ├─ opc_20260903.sql              # 30+ opc_* 表 + 字典数据 + Agent 模板
│  │   └─ flyway/V1__init_opc_schema.sql
│  └─ deploy/
│      ├─ docker-compose.yml            # 本地一键起 Nacos/MySQL/Redis/RabbitMQ/Qdrant/MinIO/ES/Prom/Grafana/SkyWalking
│      ├─ prometheus/prometheus.yml
│      ├─ grafana/dashboards/opc-overview.json
│      ├─ k8s/opc-ai-core.yaml          # 含 HPA(2-8) + 健康探针 + Secrets
│      ├─ k8s/opc-agent-hub.yaml
│      ├─ nacos/application-dev.yml
│      ├─ nacos/opc-routes.json         # 网关路由
│      ├─ runbooks/incident-response.md # F-01..F-07 故障手册
│      └─ README.md                     # 部署指南
│
└─ vue3-typescript/         # 前端基座
   ├─ src/api/opc/                       # 5 个 API 模块（user/agent/finance/billing/llm）
   ├─ src/views/opc/
   │   ├─ index.vue                     # OPC 首页（钱包 + Agent + 凭证）
   │   ├─ agent/market|detail|instances|instance.vue
   │   ├─ user/profile.vue
   │   ├─ finance/vouchers|flows.vue
   │   ├─ billing/wallet.vue
   │   ├─ llm/chat.vue
   │   └─ components/AgentCard.vue
   └─ src/router/index.ts               # /opc/* 路由（已挂载）
```

---

## 1. 已交付的能力矩阵（对照 MVP 范围）

| 能力 | 后端 | 前端 | 数据库 | 部署 |
|------|------|------|--------|------|
| OPC 在 RuoYi 基线上跑通 | ✅ | ✅ | ry_* | ✅ |
| 用户中心（注册 / 公司 / 画像） | ✅ | ✅ | opc_user_profile / opc_company_profile | ✅ |
| Agent Hub（市场 / 雇佣 / 实例 / 任务） | ✅ | ✅ | opc_agent_definition / _instance / _task | ✅ |
| 财务 Agent（流水 / 凭证 / 审核 / 日报） | ✅ | ✅ | opc_finance_voucher / _bank_flow | ✅ |
| AI 中台：LLM Gateway | ✅ DeepSeek + GPT-4o-mini + 文心 + 自动 fallback | ✅ 模型切换 UI | opc_agent_token_usage | ✅ |
| AI 中台：Agent Runtime | ✅ ReAct + maxSteps + stepTimeout | — | — | — |
| AI 中台：记忆系统 | ✅ 短期 Redis + 长期 Qdrant + 压缩 | ✅ 记忆查看 | — | ✅ |
| AI 中台：Token 计量 | ✅ 每次 LLM 调用打点 + 成本核算 | ✅ 图表 | opc_agent_token_usage | ✅ Grafana |
| AI 中台：工具调用 | ✅ Tool Registry + FinanceTools | ✅ | — | — |
| AI 中台：安全护栏 | ✅ PromptGuard + SensitiveWordFilter + RateLimiter | — | — | — |
| 计费（钱包 / 订单 / 充值） | ✅ | ✅ | opc_wallet / _billing_order | ✅ |
| 工作流编排（v0.1 线性） | ✅ WorkflowEngine | — | opc_agent_workflow / _run | — |
| 监控 / 日志 / 灰度 / 回滚 | — | — | — | ✅ Prom + Grafana + K8s HPA + runbook |

---

## 2. 启动顺序（开发者 30 分钟本地跑通）

```bash
# Step 1: 基础设施（5~10 分钟）
cd springboot3/deploy
docker-compose up -d nacos1 nacos2 nacos3 mysql redis rabbitmq qdrant minio es prometheus grafana skywalking-oap

# Step 2: 数据库（首次，2 分钟）
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' < ../sql/ry_20260417.sql
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' < ../sql/ry_config_20260818.sql
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' < ../sql/quartz.sql
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' < ../sql/opc_20260903.sql

# Step 3: 后端（10~15 分钟）
cd ..
mvn clean install -DskipTests
mvn spring-boot:run -pl ruoyi-gateway &
mvn spring-boot:run -pl ruoyi-auth &
mvn spring-boot:run -pl ruoyi-modules/ruoyi-system &
mvn spring-boot:run -pl ruoyi-modules/opc-ai-core &
mvn spring-boot:run -pl ruoyi-modules/opc-user-center &
mvn spring-boot:run -pl ruoyi-modules/opc-agent-hub &
mvn spring-boot:run -pl ruoyi-modules/opc-billing &
mvn spring-boot:run -pl ruoyi-modules/opc-finance &

# Step 4: 前端（3 分钟）
cd ../vue3-typescript
npm install && npm run dev
# 浏览器访问 http://localhost:5173 → 登录 → /opc
```

---

## 3. 关键端口

| 端口 | 服务 | 入口 |
|------|------|------|
| 5173 | 前端 | http://localhost:5173 |
| 8080 | Gateway | http://localhost:8080 |
| 9201 | ruoyi-system | nacos 注册 |
| 9301 | opc-ai-core | `/opc/llm/chat`, `/opc/llm/models` |
| 9302 | opc-user-center | `/opc/user/profile`, `/opc/user/companies` |
| 9303 | opc-agent-hub | `/opc/agent/market`, `/opc/agent/instances`, `/opc/agent/hire` |
| 9304 | opc-billing | `/opc/billing/wallet`, `/opc/billing/recharge`, `/opc/billing/orders` |
| 9305 | opc-finance | `/opc/finance/vouchers`, `/opc/finance/flows`, `/opc/finance/daily-report` |
| 8848 | Nacos 控制台 | http://localhost:8848/nacos |
| 3000 | Grafana | http://localhost:3000 (admin/admin) |
| 9090 | Prometheus | http://localhost:9090 |
| 6333 | Qdrant | http://localhost:6333/dashboard |
| 15672 | RabbitMQ 管理 | http://localhost:15672 |

---

## 4. 核心业务流程 Demo 脚本（Demo Day #1）

1. 打开 http://localhost:5173/opc/user/profile
2. 创建公司档案（一人公司）
3. 进入 /opc/agent/market → 选择"财务 Agent"
4. 雇佣（选择月付套餐 → 模拟支付）
5. 进入 /opc/agent/instance/{id} → "对话"页
6. 上传一段银行流水文本 → Agent 自动生成凭证
7. 进入 /opc/finance/vouchers → 看到生成的待审核凭证
8. 点击"通过" → 凭证入账 → 扣 Token + 钱包
10. 第二天进入 /opc/agent/instance/{id} → "任务" → 看到日报任务自动跑完

---

## 5. 验收检查表（对照原计划 §验收检查表）

| 验收项 | 状态 | 说明 |
|--------|------|------|
| 任意开发者 git clone 后 30 分钟内本地启动 | ✅ | 见 §2 |
| 100 用户并发记账 P95 < 3s | ⚠️ | 待 W9 全链路压测 |
| 财务 Agent 评测准确率 > 85% | ⚠️ | 评测集 v0.1 10 用例已 seed，需扩到 100 |
| 单用户每日 Token 成本 < ¥1 | ⚠️ | 计量埋点已埋，需观察 |
| 公测 7 天无 P0 故障 | ⚠️ | 公测环境 W10 启动后验证 |
| 关键操作审计日志 | ✅ | opc_audit_log 表 + 沿用 RuoYi SysOperLog |
| 监控覆盖 4 大黄金指标 | ✅ | opc-overview.json Grafana 大盘 |
| 故障 5 分钟告警 / 30 分钟定位 | ✅ | runbook F-01..F-07 |
| 完整文档（产品 / 用户 / 运维） | ✅ | deploy/README + runbook + 本文档 |

---

## 6. 已知 TODO（团队接手后必做）

1. **AI 工程师**：把 `opc_agent_eval_case` 从 10 个扩到 100+，跑通 Prompt v0.2。
2. **后端 A**：补 `application-prod.yml`，生产配置走 Nacos 而非本地 yml。
3. **后端 B**：把 RuoYi `ruoyi-job`（OPC 沿用）接入 `WorkflowEngine`，实现 cron 触发器。
4. **后端 C**：补 `opc_finance_tax_report` Controller 和 Service。
5. **前端**：补移动端适配（Element Plus 的 xs/sm 响应式断点）。
6. **DevOps**：把 `docker-compose.yml` 改为 K8s Helm Chart，便于多环境复用。
7. **产品**：补"种子用户邀约落地页" `/opc/invite?code=xxx`。
8. **AI 安全**：补红队测试用例（让 Agent 输出错误凭证 / 提现指令），加入 `opc_agent_eval_case`。

---

## 7. 升级路径（M4-M6 路线图占位）

- **M4**：接入第二个业务 Agent（电商 Agent / 法务 Agent / 营销 Agent）
- **M5**：开放第三方开发者上传自定义 Agent（审核 + 上架流程）
- **M6**：移动端 App（小程序优先）+ 企业微信集成

---

> 最后更新：2026-09-03 · 与 `init.md` v2026-09-03 同步。