# OPC 扩展路线图 设计 Spec (W49-W90 / 2026-Q4 ~ 2027-Q2)

> 元信息: 设计者 Claude Code / 日期 2026-09-10 / 版本 v1.0 / 状态 approved
> 关联:
> - 项目现状: [`OPC-MVP-DELIVERY.md`](../../OPC-MVP-DELIVERY.md) + [`init.md`](../../init.md)
> - M4 Insight MVP 模板: [`docs/verification/milestones/m4-insight/`](../../verification/milestones/m4-insight/)
> - W48 全栈 Docker 化模板: [`OPC-W48-DEPLOYMENT-PERSISTENCE`](../../springboot3/deploy/RECOVERY.md)
> - 文档总索引: [`docs/README.md`](../../README.md)

---

## 0. 执行摘要

**目标**: 在 MVP (6 个核心 OPC 服务) 基础上,**9 个新服务 + React Native 移动端**,分 3 个迭代 (各 14 周) 交付。

**团队**: 1 后端 + 1 前端 + 1 AI = 3 人。

**约束**:
- 复用现有 6 服务模板 (Spring Boot 3 + Nacos + MyBatis + Docker thin jar + Jasypt + HttpLlmClient),**不动 opc-common**
- 通知统一走 opc-notification
- LLM 调用统一走 opc-common HttpLlmClient (W48.6)
- 每个服务模板化 (降低单点成本)
- 每服务提交 `OPC-W<N>-VERIFICATION-<service>.md`

**核心数字**:
- 9 服务 + 1 RN App,9 个月 (39 周)
- ~60 个新 REST API,~28 张新表,~10 个新前端模块,~12 个 RN 页面
- 容器数 16 → ~25, MySQL 表 32 → ~60

---

## 1. 范围 / 非范围

### 范围 (In)
- 9 个新微服务 (后端 + 前端 + DB + Docker + Nacos + Helm)
- 1 React Native 跨端移动 App (iOS + Android)
- 跨服务 Feign 调用
- Nacos 配置管理 (dev/prod 2 套)
- MySQL V__*.sql 迁移脚本 + seed 数据
- LLM prompt 模板 + eval 集 + 红队测试
- Helm chart 增量更新
- K8s yaml 占位 + HPA
- health-check.sh 端点增加到 ~35 个
- RECOVERY.md 更新

### 非范围 (Out)
- 原生 iOS / Android (只用 RN)
- 自研 ASR / TTS 模型 (用阿里云 / 讯飞)
- 区块链 / Web3
- 海外部署 (仍单机房)
- 商户实名认证 (用现有邀请链路代替)
- 财务银行 API 对接 (opc-finance 已 mock)
- 公网注册 (邀请制)

---

## 2. 架构总览

### 2.1 依赖图

```
opc-notification ──┬──> opc-crm
                   ├──> opc-hr
                   ├──> opc-community ──> opc-marketplace
                   ├──> opc-voice-agent
                   └──> opc-ecommerce ──> (3rd party payment)

opc-erp ──> opc-ecommerce
opc-content (独立, 仅复用 LLM Gateway)

React Native App ──> 各服务 REST API (并行, 不阻塞后端)
```

### 2.2 跨服务调用矩阵

| 调用方 \ 被调方 | notification | erp | crm | billing | user-center | agent-hub |
|----------------|:---:|:---:|:---:|:---:|:---:|:---:|
| crm            | ✅ |   |   |   | ✅ |   |
| erp            | ✅ |   |   |   |   |   |
| ecommerce      | ✅ | ✅ | ✅ | ✅ | ✅ |   |
| content        |   |   |   | ✅ |   |   |
| hr             | ✅ |   |   |   | ✅ |   |
| community      | ✅ |   | ✅ |   | ✅ | ✅ |
| voice-agent    | ✅ |   |   | ✅ |   |   |
| marketplace    | ✅ |   | ✅ | ✅ | ✅ |   |

**实现**: OpenFeign + `@InnerAuth` + `from-source: INNER` header (沿用现有 pattern)
**fallback**: 每个 Feign client 必带 `FallbackFactory`,避免级联失败

### 2.3 复用现有 opc-common

| 现有模块 | 新服务使用方式 |
|---------|--------------|
| `HttpLlmClient` | 所有需要 LLM 的服务 |
| `OpcConstants` | Provider/Model 价格常量 |
| `SnowflakeIdWorker` | 所有表主键 |
| `OpcException` + 异常体系 | 所有异常 |
| `BaseController` / `R<T>` | 所有 Controller |
| `SecurityUtils` (ruoyi-common) | 当前用户 ID |
| `@InnerAuth` (ruoyi-common) | Feign 调用鉴权 |
| `@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api","com.ruoyi.opc"})` | 启动类 |
| `@ComponentScan("com.ruoyi.opc")` | 启动类 |

---

## 3. 优先级矩阵 + 估时

| P | 服务 | 周数 | 阻塞依赖 | 价值 |
|:-:|------|:---:|:--------:|:---:|
| **P0** | opc-notification | 2 | 无 | 基础设施,被 5 个依赖 |
| **P0** | opc-erp | 6 | 无 | 商业核心 |
| **P0** | opc-crm | 4 | notification | 商业核心 |
| **P1** | opc-ecommerce | 4 | erp | GMV 入口 |
| **P1** | opc-content | 4 | 无 | 营销素材 |
| **P1** | opc-hr | 3 | notification | 招聘人事 |
| **P2** | opc-community | 4 | notification+crm | 互动留存 |
| **P2** | opc-voice-agent | 4 | notification | 探索 (ASR) |
| **P3** | opc-marketplace | 3 | community+billing | 商务合作 |
| **P3** | React Native App | 8 (并行) | 上述 5+ 服务 | 触达 |

**总周数**: 2+6+4+4+4+3+4+4+3 = **34 周** (RN 8 周并行不计入) + 5 周 buffer = 39 周 = 9 个月

---

## 4. 三迭代详细排期 (W49-W90)

### 4.1 迭代 1: P0 基础设施 (W49-W62, 14 周)

| 周 | 后端 | 前端 | AI |
|:--:|------|------|-----|
| W49 | notification 设计 + Domain | notification UI 草图 | 通知模板 prompt 设计 |
| W50 | notification 完成 (邮件/SMS/站内信/WS) | notification 列表 + WS 客户端 | 模板生成 eval (10 case) |
| W51 | erp Domain + 商品/库存表 + Service | 商品/库存页 (响应式) | - |
| W52 | erp 采购单 Service | 采购单页 | - |
| W53 | erp 销售单 Service + Controller | 销售单页 | - |
| W54 | erp e2e + 文档 | ERP 跨页联动 | - |
| W55 | crm Domain + 客户/联系人 Service | CRM 列表 + 详情 | 商机打分 prompt v0.1 |
| W56 | crm 跟进/商机 Service | 跟进看板 | 商机打分 eval (20 case) |
| W57 | crm Controller + 前端整合 | 商机看板 + 报表 | prompt v0.2 调优 |
| W58 | 集成测试 + bug 修复 | E2E 流程演练 | 红队 10 case |
| W59 | W49 iteration end-to-end verification 报告 | - | - |
| W60 | notification/erp/crm 各自 OPC-W*-VERIFICATION-*.md | - | - |
| W61 | Helm chart 更新 + K8s yaml | - | - |
| W62 | **buffer** / hardening | - | - |

**Iter 1 交付**:
- 3 服务 (notification/erp/crm) + 3 验证报告
- ~15 个 REST API,~5 张新表
- 3 个前端模块 (notification/ERP/CRM)
- health-check.sh 新增 ~6 端点

### 4.2 迭代 2: P1 商业拓展 (W63-W76, 14 周)

| 周 | 后端 | 前端 | AI |
|:--:|------|------|-----|
| W63 | ecommerce 设计 + 商品挂店 Service | 店铺首页 + 商品列表 | 商品文案 prompt v0.1 |
| W64 | ecommerce 购物车 + 订单 Service | 购物车 + 结算页 | 文案 eval (30 case) |
| W65 | ecommerce 支付 (mock 第三方) + 物流 Service | 订单详情 + 物流跟踪 | - |
| W66 | ecommerce 完整 e2e + 报告 | - | 红队 10 case |
| W67 | content 设计 + 短剧脚本 Domain | 内容编辑器 + 模板 | 短剧 prompt v0.1 |
| W68 | content 视频脚本 + 图文 Service | 视频脚本模板 | 内容 eval (40 case) |
| W69 | content 跨平台适配 (抖音/小红书) | 多平台预览 | prompt v0.2 调优 |
| W70 | content e2e + 报告 | - | - |
| W71 | hr 设计 + 招聘需求 + 候选人 Service | HR 看板 | JD 自动生成 prompt |
| W72 | hr 面试 + Offer Service | 候选人详情 + Offer | 简历筛选 eval (30 case) |
| W73 | hr e2e + 报告 | - | 红队 10 case |
| W74 | 集成测试 + bug 修复 | - | - |
| W75 | ecommerce/content/hr 各自 OPC-W*-VERIFICATION-*.md | - | - |
| W76 | **buffer** / hardening | - | - |

**Iter 2 交付**:
- 3 服务 + 3 报告
- ~20 REST API,~8 张新表
- 4 个前端模块

### 4.3 迭代 3: P2-P3 社区 + 语音 + 移动 (W77-W90, 14 周)

| 周 | 后端 | 前端 (Web) | RN 移动端 | AI |
|:--:|------|-----------|---------|-----|
| W77 | community 设计 + 模块市场 Domain | 社区主页 | RN 项目初始化 + 登录 + 首页 | 推荐算法 prompt v0.1 |
| W78 | community 互动 + 评论 + 评分 | 模块详情 + 评论 | Agent Hub 列表 | 推荐 eval |
| W79 | community e2e + 报告 | - | Insight 移动版 | 红队 |
| W80 | voice-agent 设计 + ASR/TTS 集成 | 语音配置后台 | IM 消息 (WS) | ASR 选型 (阿里云 vs 讯飞) |
| W81 | voice-agent 外呼脚本 + 客服对话 | 通话记录 | 语音消息 (录/播) | ASR/TTS 红队 (反注入) |
| W82 | voice-agent e2e + 报告 | - | Wallet 移动版 | - |
| W83 | marketplace 设计 + 商务合作 | 合作方管理 | 个人中心 | - |
| W84 | marketplace 三方分账 | 报表 + 合同 | 设置 + 关于 | - |
| W85 | marketplace e2e + 报告 | - | iOS TestFlight / Android APK | - |
| W86 | 集成测试 + 总 bug 修复 | - | - | - |
| W87 | community/voice/marketplace 各自 OPC-W*-VERIFICATION-*.md | - | - | - |
| W88 | App Store / Google Play 上架 | - | - | - |
| W89 | 总验收 + roadmap 总结 | - | - | - |
| W90 | **buffer** / 后续规划 | - | - | - |

**Iter 3 交付**:
- 3 服务 + 1 RN App 上架
- ~25 REST API,~10 张新表
- ~12 RN 页面 (iOS + Android)

---

## 5. 9 服务中层 Spec (统一模板)

每个服务 spec 包含:
- Goal (1 段, 5 AC)
- Key Interfaces (REST 列表)
- DB Tables (新表 + 复用)
- Cross-Service Calls (Feign)
- LLM Integration (是否需要 + prompt 名)
- Test Strategy
- Deployment (Dockerfile + Nacos + initdb + Helm)
- Acceptance

### 5.1 opc-notification (P0, 2 周)

**Goal**: 统一通知中心,被 5 个下游服务调用。支持邮件/SMS/站内信/WebSocket 实时推送,LLM 生成个性化通知文案。

**AC**:
- [ ] 邮件发送成功率 ≥ 99% (含重试)
- [ ] SMS 模板支持变量替换
- [ ] 站内信分页查询 < 200ms
- [ ] WS 推送延迟 < 1s (单房间 100 并发)
- [ ] 通知模板版本管理 (灰度)

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| POST | `/opc/notification/email/send` | 邮件发送 |
| POST | `/opc/notification/sms/send` | SMS 发送 |
| GET  | `/opc/notification/inbox` | 站内信列表 |
| POST | `/opc/notification/inbox/read/{id}` | 标记已读 |
| GET  | `/opc/notification/inbox/unread-count` | 未读数 |
| WS   | `/ws/notification` | 实时推送 |

**DB Tables (新)**:
- `opc_notification_email_log` (id, to, subject, body, status, retry_count, sent_at)
- `opc_notification_sms_log` (id, phone, template_id, vars, status, sent_at)
- `opc_notification_inbox` (id, user_id, type, title, body, link, read_at, created_at)
- `opc_notification_template` (id, code, channel, subject, body, version, enabled)

**Cross-Service Calls**: 无 (基础服务)

**LLM Integration**: `notification-template-generator` (可选, 用于个性化文案)

**Test Strategy**:
- unit: 4 Service 类 × 8 = 32 @Test
- integration: Mock Email/SMS provider,验证重试
- e2e: WS 推送延迟测试 (100 并发)
- redteam: 无 (基础服务)

**Deployment**:
- Dockerfile thin jar (复用 W48.1)
- Nacos yml: dev/prod (`deploy/nacos/opc-notification-{dev,prod}.yml`)
- initdb: `V20260915__notification_schema.sql` + seed 模板
- Helm: 新增 `notification` 到 `services`

### 5.2 opc-erp (P0, 6 周)

**Goal**: 进销存 + 库存管理。商品 SKU、批次、库存预警、采购单、销售单、退货单。

**AC**:
- [ ] 商品 CRUD + 多规格 SKU
- [ ] 库存实时计算 (可用/锁定/在途)
- [ ] 库存预警 (低于阈值触发通知)
- [ ] 采购单 / 销售单 / 退货单 状态机
- [ ] 库存报表 (日报/月报)

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| GET/POST | `/opc/erp/product` | 商品 CRUD |
| GET | `/opc/erp/product/{id}/inventory` | 库存查询 |
| POST | `/opc/erp/purchase` | 采购单创建 |
| POST | `/opc/erp/sale` | 销售单创建 |
| POST | `/opc/erp/return` | 退货单 |
| GET | `/opc/erp/inventory/report` | 库存报表 |
| GET | `/opc/erp/inventory/low-stock` | 低库存预警 |

**DB Tables (新)**:
- `opc_erp_product` (id, sku, name, category, unit, price, status)
- `opc_erp_product_sku` (id, product_id, spec, price, stock, locked)
- `opc_erp_inventory_log` (id, sku_id, change, type, ref_id, created_at)
- `opc_erp_purchase` (id, vendor, items, total, status, created_at)
- `opc_erp_sale` (id, customer, items, total, status, created_at)
- `opc_erp_return` (id, ref_sale_id, reason, status, created_at)

**Cross-Service Calls**:
- `notification` → 低库存预警 / 单据状态通知

**LLM Integration**: `erp-auto-category` (商品自动分类, 辅助)

**Test Strategy**:
- unit: 6 Service × 10 = 60 @Test
- integration: 库存并发 (乐观锁)
- e2e: 采购 → 库存 → 销售 → 退货 全链路
- redteam: 无

### 5.3 opc-crm (P0, 4 周)

**Goal**: 客户关系管理。客户档案、联系人、跟进记录、商机、销售漏斗。LLM 做商机打分 + 跟进建议。

**AC**:
- [ ] 客户 CRUD + 标签 + 来源
- [ ] 联系人 1:N 关联
- [ ] 跟进记录时间线
- [ ] 商机漏斗 (阶段拖拽)
- [ ] LLM 商机打分 (0-100)

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| GET/POST | `/opc/crm/customer` | 客户 CRUD |
| GET/POST | `/opc/crm/contact` | 联系人 |
| POST | `/opc/crm/follow-up` | 跟进记录 |
| GET/POST | `/opc/crm/opportunity` | 商机 |
| POST | `/opc/crm/opportunity/{id}/score` | LLM 打分 |
| GET | `/opc/crm/dashboard` | 销售漏斗 |

**DB Tables (新)**:
- `opc_crm_customer` (id, name, source, tags, owner_id, level)
- `opc_crm_contact` (id, customer_id, name, phone, email, position)
- `opc_crm_follow_up` (id, customer_id, type, content, next_at, owner_id)
- `opc_crm_opportunity` (id, customer_id, name, amount, stage, score, expected_close)

**Cross-Service Calls**:
- `notification` → 跟进提醒 / 商机阶段变更
- `user-center` → 归属人信息

**LLM Integration**: `crm-opportunity-scorer` (必装) + `crm-followup-suggester` (推荐)

**Test Strategy**:
- unit: 4 Service × 12 = 48 @Test
- integration: 商机状态机
- e2e: 客户创建 → 跟进 → 商机 → 成交
- redteam: 商机打分 prompt 反注入

### 5.4 opc-ecommerce (P1, 4 周)

**Goal**: 独立站 + 商品挂店 + 订单 + 支付 (mock) + 物流。

**AC**:
- [ ] 店铺自定义 (主题/logo)
- [ ] 商品挂店 + 价格策略
- [ ] 购物车 + 结算
- [ ] 订单状态机
- [ ] 物流跟踪 (mock)
- [ ] 销售报表

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| GET/POST | `/opc/ecommerce/shop` | 店铺 CRUD |
| POST | `/opc/ecommerce/shop/{id}/product` | 挂店 |
| GET/POST | `/opc/ecommerce/cart` | 购物车 |
| POST | `/opc/ecommerce/order` | 下单 |
| GET | `/opc/ecommerce/order/{id}` | 订单详情 |
| POST | `/opc/ecommerce/order/{id}/pay` | 支付 (mock) |
| GET | `/opc/ecommerce/order/{id}/logistics` | 物流 |

**DB Tables (新)**:
- `opc_ecom_shop` (id, owner_id, name, theme, logo)
- `opc_ecom_shop_product` (id, shop_id, product_id, price_strategy)
- `opc_ecom_cart` (id, user_id, items_json, updated_at)
- `opc_ecom_order` (id, user_id, shop_id, items, total, status, address)
- `opc_ecom_payment` (id, order_id, amount, channel, status, paid_at)
- `opc_ecom_logistics` (id, order_id, tracking_no, status, history_json)

**Cross-Service Calls**:
- `erp` → 扣减库存
- `crm` → 下单即创建客户
- `notification` → 订单状态通知
- `billing` → 收款入账
- `user-center` → 用户地址

**LLM Integration**: `ecommerce-product-copywriter` (商品文案)

**Test Strategy**:
- unit: 5 Service × 10 = 50 @Test
- integration: 库存扣减并发 + 订单状态机
- e2e: 浏览 → 加车 → 下单 → 支付 → 发货 → 收货
- redteam: 商品文案 prompt 反注入

### 5.5 opc-content (P1, 4 周)

**Goal**: AI 内容生成中心。短剧脚本、视频脚本、图文、营销文案,支持多平台适配 (抖音/小红书/B站)。

**AC**:
- [ ] 短剧脚本生成 (分镜 + 台词)
- [ ] 视频脚本生成 (60s/3min/10min)
- [ ] 图文文案 (标题 + 正文 + 标签)
- [ ] 多平台风格适配
- [ ] 模板市场

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| POST | `/opc/content/short-drama/generate` | 短剧生成 |
| POST | `/opc/content/video-script/generate` | 视频脚本 |
| POST | `/opc/content/article/generate` | 图文 |
| GET | `/opc/content/template` | 模板列表 |
| POST | `/opc/content/template` | 创建模板 |
| GET | `/opc/content/history` | 历史记录 |

**DB Tables (新)**:
- `opc_content_task` (id, user_id, type, prompt, output, platform, status)
- `opc_content_template` (id, code, type, prompt_template, vars_schema, version)
- `opc_content_history` (id, user_id, task_id, output, edited_output, rating)

**Cross-Service Calls**:
- `billing` → Token 计量

**LLM Integration**: 必装 4 个 prompt:
- `content-short-drama-v1.0`
- `content-video-script-v1.0`
- `content-article-v1.0`
- `content-platform-adapter-v1.0` (抖音/小红书/B站)

**Test Strategy**:
- unit: 3 Service × 12 = 36 @Test
- integration: LLM eval 集 (40 case) + 跨平台 prompt 切换
- e2e: 用户输入 → 生成 → 编辑 → 保存
- redteam: 必装 (内容生成风险高) 30 case

### 5.6 opc-hr (P1, 3 周)

**Goal**: 招聘需求 / 候选人 / 面试 / Offer。LLM 自动写 JD + 简历筛选。

**AC**:
- [ ] 招聘需求发布
- [ ] 候选人录入 + 简历解析
- [ ] LLM 简历匹配打分
- [ ] 面试日程 + 反馈
- [ ] Offer 模板 + 发送

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| GET/POST | `/opc/hr/job-requisition` | 招聘需求 |
| POST | `/opc/hr/job-requisition/{id}/generate-jd` | LLM 写 JD |
| POST | `/opc/hr/candidate` | 候选人录入 |
| POST | `/opc/hr/candidate/{id}/match` | 简历打分 |
| POST | `/opc/hr/interview` | 面试安排 |
| POST | `/opc/hr/offer` | Offer |

**DB Tables (新)**:
- `opc_hr_requisition` (id, title, dept, jd, status, owner_id)
- `opc_hr_candidate` (id, name, resume_json, source, status)
- `opc_hr_match` (id, candidate_id, requisition_id, score, reason)
- `opc_hr_interview` (id, candidate_id, requisition_id, time, feedback)
- `opc_hr_offer` (id, candidate_id, salary, content, status, sent_at)

**Cross-Service Calls**:
- `notification` → 面试通知 / Offer 通知
- `user-center` → 招聘负责人

**LLM Integration**:
- `hr-jd-generator`
- `hr-resume-matcher`

**Test Strategy**:
- unit: 4 Service × 10 = 40 @Test
- integration: 简历解析 (PDF/DOCX)
- e2e: 发布 → 收简历 → 匹配 → 面试 → Offer
- redteam: 简历筛选 prompt 反注入 (10 case)

### 5.7 opc-community (P2, 4 周)

**Goal**: 业务模块市场 + 互动 + 评论 + 评分 + 关注。LLM 做个性化推荐。

**AC**:
- [ ] 模块市场 (浏览/搜索/分类)
- [ ] 模块详情 + 评分 + 评论
- [ ] 用户关注 + 动态时间线
- [ ] LLM 个性化推荐
- [ ] 举报 + 审核

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| GET | `/opc/community/module` | 模块列表 |
| GET | `/opc/community/module/{id}` | 详情 |
| POST | `/opc/community/module/{id}/comment` | 评论 |
| POST | `/opc/community/module/{id}/rate` | 评分 |
| GET | `/opc/community/timeline` | 动态 |
| POST | `/opc/community/follow/{userId}` | 关注 |
| GET | `/opc/community/recommend` | LLM 推荐 |

**DB Tables (新)**:
- `opc_community_module` (id, code, name, category, owner_id, rating, install_count)
- `opc_community_comment` (id, module_id, user_id, content, parent_id)
- `opc_community_rating` (id, module_id, user_id, score, UNIQUE(module_id,user_id))
- `opc_community_follow` (follower_id, followee_id, UNIQUE(follower,followee))
- `opc_community_timeline` (id, user_id, type, ref_id, content_json, created_at)
- `opc_community_report` (id, ref_type, ref_id, reason, status)

**Cross-Service Calls**:
- `notification` → 评论/关注通知
- `crm` → 模块作者归属
- `agent-hub` → 模块关联 Agent
- `user-center` → 用户画像

**LLM Integration**: `community-recommender`

**Test Strategy**:
- unit: 5 Service × 10 = 50 @Test
- integration: 时间线聚合查询
- e2e: 浏览 → 评论 → 关注 → 推荐
- redteam: 推荐 prompt 反注入

### 5.8 opc-voice-agent (P2, 4 周)

**Goal**: 语音数字员工。ASR (语音转文字) + TTS (文字转语音) + 外呼脚本 + 智能客服。

**AC**:
- [ ] ASR 实时识别 (阿里云 / 讯飞 / Whisper)
- [ ] TTS 语音合成 (支持音色)
- [ ] 外呼脚本 (流程节点)
- [ ] 智能客服对话 (ASR → LLM → TTS)
- [ ] 通话记录 + 质检

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| POST | `/opc/voice/asr` | 音频转文字 |
| POST | `/opc/voice/tts` | 文字转音频 |
| POST | `/opc/voice/call/outbound` | 创建外呼 |
| GET | `/opc/voice/call/{id}` | 通话详情 |
| GET | `/opc/voice/call/{id}/recording` | 录音 |
| POST | `/opc/voice/agent/chat` | 客服对话 |

**DB Tables (新)**:
- `opc_voice_provider` (id, code, type, config_json, enabled) — 多 provider 抽象
- `opc_voice_script` (id, name, flow_json, enabled)
- `opc_voice_call` (id, script_id, phone, status, duration, recording_url, transcript)
- `opc_voice_tts_voice` (id, code, name, provider, sample_url)
- `opc_voice_chat_session` (id, user_id, messages_json, started_at)

**Cross-Service Calls**:
- `notification` → 通话结果通知
- `billing` → 通话计费

**LLM Integration**: `voice-chat-agent` (实时对话 prompt)

**ASR/TTS 选型**: **阿里云语音** (主) / **讯飞** (备) / **Whisper** (本地 fallback)
**抽象**: `VoiceProvider` 接口,3 个实现可切换

**Test Strategy**:
- unit: 4 Service × 12 = 48 @Test
- integration: Mock provider,验证切换
- e2e: 音频上传 → ASR → LLM → TTS → 播放
- **redteam: 必装, 30 case** (ASR/TTS 反注入 + 语音克隆检测)

### 5.9 opc-marketplace (P3, 3 周)

**Goal**: 商务合作市场。线下 OPC 园区对接、合作伙伴、三方分账。

**AC**:
- [ ] 合作方入驻
- [ ] 商务合同模板
- [ ] 三方分账 (我方 / 园区 / 合作伙伴)
- [ ] 结算报表

**Key Interfaces**:
| Method | Path | 说明 |
|--------|------|------|
| GET/POST | `/opc/marketplace/partner` | 合作方 |
| POST | `/opc/marketplace/contract` | 合同 |
| GET | `/opc/marketplace/settlement` | 结算列表 |
| POST | `/opc/marketplace/settlement/{id}/approve` | 审批 |

**DB Tables (新)**:
- `opc_market_partner` (id, name, type, contact, level, joined_at)
- `opc_market_contract` (id, partner_id, content, amount, split_rule_json, status)
- `opc_market_settlement` (id, contract_id, period, amount, splits_json, status)

**Cross-Service Calls**:
- `community` → 合作方展示页
- `billing` → 实际分账
- `notification` → 结算通知

**LLM Integration**: 无

**Test Strategy**:
- unit: 3 Service × 10 = 30 @Test
- integration: 分账计算正确性
- e2e: 入驻 → 合同 → 结算
- redteam: 无

---

## 6. React Native 移动端 (8 周并行, Iter 3)

### 6.1 技术栈

| 层 | 选型 | 理由 |
|----|------|------|
| 跨端 | React Native 0.74+ | 单代码库,iOS + Android |
| 导航 | React Navigation v6 | 主流 |
| 状态 | Zustand | 轻量 |
| 网络 | Axios + RTK Query | 缓存 |
| UI | React Native Elements | 兼容 Element Plus 设计语言 |
| WS | 自实现 (复用前端 WS client 协议) | 与 Web 端统一 |
| 构建 | Expo (开发) + EAS (发布) | 简化 iOS/Android 配置 |

### 6.2 页面清单 (~12 个)

| 页面 | 复用 Web 端 API |
|------|--------------|
| 登录 / 验证码 | `/login` / `/captchaImage` |
| 首页 (Agent Hub) | `/opc/agent/market` |
| Agent 详情 | `/opc/agent/detail/{id}` |
| Insight 仪表盘 | `/opc/insight/dashboard` |
| Insight 日报 | `/opc/insight/daily` |
| Wallet | `/opc/billing/wallet` |
| 订单列表 | `/opc/ecommerce/order` |
| 邀请页 | `/opc/invite` |
| 通知中心 | `/opc/notification/inbox` |
| 个人中心 | `/opc/user/profile` |
| 设置 | - |
| 关于 | - |

### 6.3 与 Web 端关系

- Web 端 H5 已响应式,RN 是 H5 的**原生加强版** (推送 / 后台运行 / 离线)
- 后端 API 完全共享,RN 不需要新后端
- 认证: JWT + Refresh Token,沿用 Web 端 `/login`

### 6.4 上架

- iOS: TestFlight (W88) → App Store (W89 后)
- Android: APK 直装 + Google Play (W88)

---

## 7. 团队分工 (3 人 × 9 月)

| 人 | 主要 | 辅助 | 占工作时间 |
|----|------|------|:---:|
| 后端 (Claude + 用户) | 9 服务后端 + DB + Docker + Nacos + Helm | RN 后端 API 联调 | 70% |
| 前端 (用户) | 10 个 Web 模块 + RN 跨端 | 后端接口联调 | 100% |
| AI (用户) | 9 服务 LLM prompt + eval + 红队 | 内容生成 / 客服对话 | 50% |

**注**: Claude (我) 主导后端 + 文档;用户 1 人身兼 3 角色 (前/AI)。如能扩到 5 人 (加 DBA + 移动端专职 + 商务),可压缩到 6 个月。

---

## 8. 测试策略

### 8.1 每个服务统一测试矩阵

| 类型 | 目标 | 工具 | 通过率 |
|------|------|------|:---:|
| 单元测试 | Service / Controller | JUnit5 + Mockito | ≥ 80% |
| 集成测试 | Feign Mock + DB | SpringBootTest + H2/MySQL | 100% 通过 |
| 端到端 | 跨服务流程 | Python urllib + tmp_e2e/e2e_all.py | 100% 通过 |
| mutation testing | 测试质量 | PIT (opc-finance/ruoyi-gateway 已有) | ≥ 75% |
| LLM eval | prompt 质量 | finance-agent-v2.0.json 模板 | 视服务而定 |
| 红队 | 反注入 | PromptGuard v0.3 + 30 用例 | ASR ≤ 10% |

### 8.2 总测试覆盖

- 每服务 ≥ 30 @Test
- 9 服务合计 ≥ 270 @Test (新增)
- LLM eval 集 合计 ≥ 200 case
- 红队合计 ≥ 120 case (跨服务复用)

### 8.3 health-check.sh

从 22 端点扩到 ~35 端点,每服务至少 1 健康检查 + 1 业务检查。

---

## 9. 部署策略

### 9.1 每个服务统一部署清单

```yaml
service: opc-<name>
dockerfile: springboot3/ruoyi-modules/opc-<name>/Dockerfile
  # 沿用 W48.1 thin jar 模板 (mvn dependency:copy-dependencies + java -cp "xxx.jar:lib/*" Main-Class)
nacos:
  - deploy/nacos/opc-<name>-dev.yml
  - deploy/nacos/opc-<name>-prod.yml
initdb:
  - V2026MMDD__<name>_schema.sql  (schema)
  - <name>_seed.sql                (种子数据, if any)
helm:
  - deploy/helm/opc/values.yaml:    新增 services.<name>
  - deploy/helm/opc/templates/<name>.yaml
docker_compose:
  - deploy/docker-compose.yml: 新增 aiopc-<name> 服务
gateway_route:
  - deploy/nacos/opc-gateway-dev.yml: 新增 /opc/<name>/** 路由
recover_md:
  - deploy/RECOVERY.md: 新增服务启动验证
health_check:
  - deploy/scripts/health-check.sh: 新增端点
verification_report:
  - docs/verification/week-<N>/OPC-W<N>-VERIFICATION-<name>.md
```

### 9.2 Nacos 配置模板

每个服务 2 个 yml (dev/prod),继承 `application-dev.yml` 共享配置。
**关键变量**: `${MYSQL_HOST:mysql}` / `${REDIS_HOST:redis}` / `${NACOS_NAMESPACE:opc-dev}` / `JASYPT_PASSWORD=OpcEncrypt!2026`

### 9.3 启动顺序 (docker compose up)

```
Phase 1 (基础): nacos1, mysql, redis, rabbitmq, qdrant, minio, elasticsearch
Phase 2 (RuoYi): gateway, auth, system
Phase 3 (OPC 旧): user-center, agent-hub, ai-core, billing, finance, insight
Phase 4 (OPC 新): notification, erp, crm, ecommerce, content, hr, community, voice-agent, marketplace
Phase 5 (前端): frontend (nginx)
```

---

## 10. 风险 + 缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|:---:|:---:|------|
| 后端单点 (1 人 + Claude) | 高 | 高 | 模板化降低重复成本;PR review 强制 |
| RN 与 Web 端页面冲突 | 中 | 中 | RN 从 Iter 3 开始,Web 端先稳定 |
| 语音 ASR 厂商锁定 | 中 | 中 | 抽象 `VoiceProvider` 接口,支持多 provider |
| Iter 1 延期影响全局 | 中 | 高 | 14 周里预留 2 周 buffer (W62 / W76 / W90) |
| LLM API 不稳定 | 中 | 中 | 复用 W48.6 fallback chain |
| 微信/支付宝支付接口 | 高 | 中 | Iter 2 用 mock,Iter 3+ 接真实 |
| iOS App Store 审核被拒 | 中 | 低 | TestFlight 先灰度 |
| 内容审核合规 | 中 | 高 | 内容生成 prompt 必装 PromptGuard |
| 数据迁移风险 | 低 | 高 | V__*.sql 用 `IF NOT EXISTS` (MySQL 8 stored procedure) |
| Nacos 推配置不一致 | 低 | 中 | import-dev.sh 已带 md5 校验 |

---

## 11. 验收标准

### 11.1 每个服务完成验收

- [ ] `OPC-W<N>-VERIFICATION-<name>.md` 提交 (沿用现有模板)
- [ ] Nacos dev 命名空间配置已推
- [ ] Docker compose 起服务后 health-check 全绿
- [ ] 单元测试 ≥ 30 @Test 通过
- [ ] 端到端 ≥ 1 路径通过
- [ ] mutation testing 通过 (如适用)
- [ ] LLM eval 集 ≥ 20 case 通过 (如适用)
- [ ] 红队 ASR ≤ 10% (如适用)
- [ ] RECOVERY.md 已更新该服务启动步骤
- [ ] Helm chart values 已加该服务

### 11.2 整体完成验收 (W90)

- [ ] 9 服务 + 1 RN App 全部交付
- [ ] health-check.sh 35/35 PASS
- [ ] MySQL 表数 60 (32 + 28)
- [ ] 容器数 25 (16 + 9)
- [ ] @Test 累计 ≥ 700 (430 现有 + 270 新增)
- [ ] LLM eval 累计 ≥ 400 case
- [ ] RN App 在 iOS + Android 上架
- [ ] Helm chart 验证 prod 通过 (29+9 = 38 resources)

---

## 12. 后续迭代展望 (W91+)

- 区块链积分系统 (Agent 算力交易)
- 海外部署 (新加坡节点)
- AI 数字分身 (用户上传 10 张照片训练专属 Agent)
- AI 团队协作 (多 Agent 协同完成复杂任务)
- 区块链存证 (合同 / 凭证 / 报税)

---

## 附录 A: 命名约定

```
Service code:    opc-<name>           (kebab-case, e.g. opc-voice-agent)
Java package:    com.ruoyi.opc.<name> (kebab → camel, e.g. voiceAgent)
DB tables:       opc_<name>_<entity>  (snake_case)
REST routes:     /opc/<name>/<entity>
Port:            9310-9320           (通知/ERP/CRM/电商/内容/HR/社区/语音/市场)
Container:       aiopc-<name>
Nacos DataID:    opc-<name>-{dev,prod}.yml
Verification:    OPC-W<N>-VERIFICATION-<name>.md
```

## 附录 B: 端口分配

| 服务 | 端口 |
|------|:---:|
| opc-ai-core | 9301 |
| opc-user-center | 9302 |
| opc-agent-hub | 9303 |
| opc-billing | 9304 |
| opc-finance | 9305 |
| opc-insight | 9306 |
| **opc-notification** | **9310** |
| **opc-erp** | **9311** |
| **opc-crm** | **9312** |
| **opc-ecommerce** | **9313** |
| **opc-content** | **9314** |
| **opc-hr** | **9315** |
| **opc-community** | **9316** |
| **opc-voice-agent** | **9317** |
| **opc-marketplace** | **9318** |

## 附录 C: 文件命名快速参考

```
springboot3/ruoyi-modules/opc-<name>/
├── Dockerfile                    # W48 thin jar 模板
├── pom.xml                       # extends ruoyi-modules
└── src/main/java/com/ruoyi/opc/<name>/
    ├── Opc<Name>Application.java
    ├── controller/
    ├── service/
    ├── domain/
    ├── mapper/
    └── gateway/                  # if 需调外部
```

---

**文档结束。请用户 review 后,转入 writing-plans 阶段产出每服务的实现 plan。**
