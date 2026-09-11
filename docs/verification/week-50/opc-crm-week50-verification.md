# OPC W50 验收报告 — opc-crm 客户关系管理

> **迭代周期**: W50 (2026-09-11)
> **服务**: opc-crm (端口 9312)
> **依赖**: opc-notification (W49), opc-ai-core (9301), opc-user-center (9302)
> **被依赖**: opc-community, opc-ecommerce (Iter 2)
> **状态**: ✅ 全部 21 个 Task 完成
> **验证方式**: 真实执行 (`docker ps` + `health-check.sh` + `helm lint/template` + `crm_e2e.py` + `mvn test`),非静态分析

---

## 0. 验收结论

| 维度 | 期望 | 实测 | 状态 |
|------|------|------|------|
| 后端单元测试 | 全绿 | 51 测试 / 100% | ✅ |
| 前端构建 | pass | 4 views, all < 11 kB | ✅ |
| E2E 测试 | 全绿 | 23/23 PASS | ✅ |
| 健康检查 | 全绿 | 36/36 PASS | ✅ |
| Helm 模板 | lint pass | +Deployment/Service (per env) | ✅ |
| 容器运行 | healthy | `aiopc-crm Up` | ✅ |
| Nacos 注册 | 1 实例 | `opc-crm` 已注册 (opc-dev) | ✅ |
| Gateway 路由 | 已配置 | `/opc/crm/**` → `aiopc-crm:9312` | ✅ |

**整体**: 🟢 **W50 opc-crm 服务全栈交付完成**,从 scaffold 到 prod-ready Helm chart + E2E + 4 个 Vue 3 前端视图,3 天内 21 tasks / 33 commits 闭环。复用 W48 (thin-jar / vanilla mybatis / `@ComponentScan`) 和 W49 (gateway route / Nacos dev-prod) 的 patterns,期间 1 个 E2E-blocking bug 被现场修复。

---

## 一、交付总览 (21 Tasks)

| # | Task | 关键产出 | 验证日志 |
|---|------|---------|----------|
| 1 | Scaffold | pom + Dockerfile + Application + bootstrap | [log](opc-crm-task1-scaffold.md) |
| 2 | MySQL schema | V20260918__crm_schema.sql (6 表) + crm_seed.sql (10/30/50/20/8/12) | [log](opc-crm-task2-mysql-seed.md) |
| 3 | Enums | `OpportunityStage` (state machine) + `CustomerLevel` (A/B/C/D) + `CustomerSource` + `ContractStatus` + `OrderStatus` + `FollowUpType` | [log](opc-crm-task3-enums.md) |
| 4 | Entities | 6 POJOs (`@Data` + `@Builder` + `@JsonFormat`) | [log](opc-crm-task4-entities.md) |
| 5 | Mappers + XML | 6 MyBatis Mappers + 6 XML + `MybatisConfig` | [log](opc-crm-task5-mappers.md) |
| 6 | CustomerService | 8 `@Test` (create/list/get/update/delete + level filter) | [log](opc-crm-task6-customer-service.md) |
| 7 | ContactService | 6 `@Test` (create + `setPrimary` 2-step unmark) | [log](opc-crm-task7-contact-service.md) |
| 8 | FollowUpService | 6 `@Test` (CRUD + list upcoming + markCompleted) | [log](opc-crm-task8-followup-service.md) |
| 9 | OpportunityService | 10 `@Test` (state machine 6 transition + LLM score stub) | [log](opc-crm-task9-opportunity-service.md) |
| 10 | ContractService | 6 `@Test` (create/activate/expire/terminate) | [log](opc-crm-task10-contract-service.md) |
| 11 | OrderService | 6 `@Test` (pay/ship/complete/cancel state machine) | [log](opc-crm-task11-order-service.md) |
| 12 | DashboardService | 4 `@Test` (funnel/level/follow-ups) | [log](opc-crm-task12-dashboard-service.md) |
| 13 | Feign gateways | `NotificationGateway` + `AiCoreGateway` (含 default helper) + `UserCenterGateway` + `FeignConfig` + 4 `@Test` | [log](opc-crm-task13-feign-gateways.md) |
| 14 | REST Controllers | 7 controllers + `StageChangeRequest` + 41 endpoints | [log](opc-crm-task14-controllers.md) |
| 15 | Nacos + Deployment | opc-crm-dev.yml + opc-crm-prod.yml + aiopc-crm in compose + gateway route | [log](opc-crm-task15-deployment-files.md) |
| 16 | Build + Deploy | thin jar 94K + 204 deps + 容器启动 9.14s + smoke OK | [log](opc-crm-task16-deploy.md) |
| 17 | Health + RECOVERY + Helm | 8 新增健康检查 + RECOVERY §11 + Helm Deployment/Service/PDB | [log](opc-crm-task17-health-recovery-helm.md) |
| 18 | Frontend | `crm.ts` (41 fns) + CustomerList/Detail/OpportunityKanban/FollowUpTimeline + router (4 routes) | [log](opc-crm-task18-frontend.md) |
| 19 | E2E | `crm_e2e.py` 23 scenarios | [log](opc-crm-task19-e2e.md) |
| 20 | W50 验收报告 | 本文 | — |

(实际有 21 tasks — 包含 Task 21 final acceptance + memory 更新)

---

## 二、Spec 验收 (5 AC + 用户扩展)

原始 spec §5.3 (4 表、6 endpoints、48 unit tests、LLM opportunity scorer) 全部达成,用户后续扩展的合同 / 订单 / Dashboard 子资源也一并交付。

- [x] **AC1 — 客户档案 + 标签 + 来源**: `opc_crm_customer` + `CustomerLevel` (A/B/C/D) + `CustomerSource` (REFERRAL / AD / WEBSITE / COLD_CALL / OTHER) — 5 endpoints (list / get / create / update / delete)
- [x] **AC2 — 跟进记录时间线**: `opc_crm_follow_up` + `FollowUpType` (PHONE / EMAIL / MEETING / OTHER) + `nextAt` scheduled — 5 endpoints
- [x] **AC3 — 销售线索 (lead)**: 商机漏斗 `opc_crm_opportunity` + `OpportunityStage` state machine (LEAD → QUALIFIED → PROPOSAL → NEGOTIATION → WON/LOST) + LOST → LEAD reopen + LLM `/score` 端点 — 6 endpoints
- [x] **AC4 — 客户画像聚合**: 联系人 (`opc_crm_contact`) 1:N + 主联系人 setPrimary + 商机评分 (LLM via opc-ai-core Feign) — 6 endpoints (Contact)
- [x] **AC5 — 漏斗看板**: `GET /opc/crm/dashboard` funnel `{LEAD, QUALIFIED, PROPOSAL, NEGOTIATION, WON, LOST}` + 客户分级 A/B/C/D 统计 + top10 商机

**用户扩展 (超出 spec)**:

- [x] **AC6 — 合同管理**: `opc_crm_contract` + `ContractStatus` state machine (DRAFT / ACTIVE / EXPIRED / TERMINATED) — 7 endpoints
- [x] **AC7 — 订单管理**: `opc_crm_order` + `OrderStatus` state machine (PENDING / PAID / SHIPPED / COMPLETED / CANCELLED) — 8 endpoints
- [x] **AC8 — Dashboard 子资源**: `/dashboard/customers` + `/dashboard/follow-ups/upcoming` + `/dashboard/follow-ups/recent`

**最终接口统计**: 7 controllers / 41 endpoints / 51 unit tests / 23 E2E scenarios / 36 health checks。

---

## 三、架构亮点

### 3.1 状态机 (3 套独立 finite state machine)

`OpportunityStage`:
- LEAD → {QUALIFIED, LOST}
- QUALIFIED → {PROPOSAL, LOST}
- PROPOSAL → {NEGOTIATION, LOST}
- NEGOTIATION → {WON, LOST}
- WON → {} (terminal)
- LOST → {LEAD} (reopen)

`ContractStatus` (DRAFT / ACTIVE / EXPIRED / TERMINATED) 和 `OrderStatus` (PENDING / PAID / SHIPPED / COMPLETED / CANCELLED) 也各自有独立 state machine,在 service 层强制校验非法转换 (抛 `IllegalStateException`)。

### 3.2 OpenFeign 三方集成

| Gateway | 上游 | 用途 |
|---------|------|------|
| `NotificationGateway` | opc-notification:9310 | `pushInbox(userId, title, content)` — 关键节点通知 (合同生效 / 订单付款) |
| `AiCoreGateway` | opc-ai-core:9301 | `scoreOpportunity(opp, customer)` — LLM 商机评分 (W48.6 双 provider) |
| `UserCenterGateway` | opc-user-center:9302 | `getUser(userId)` — 跟进 / 商机 owner 名字回填 |

所有 baseUrl 通过 Nacos 配置注入,容器内用 Docker DNS 名直连 (`http://aiopc-notification:9310`,W48 教训: `lb://` 在容器 DNS 解析失败)。

`AiCoreGateway` 自带 default helper method `OpportunityScoreResponse scoreOpportunity(opp, customer)`,service 层不感知 Feign 细节。

### 3.3 LLM 评分流程

1. 前端 `POST /opc/crm/opportunity/{id}/score`
2. `OpportunityController.score()` → `OpportunityServiceImpl.score()`
3. 加载 `CrmOpportunity` + `CrmCustomer`,通过 Feign `AiCoreGateway.scoreOpportunity()`
4. 内部 `default` method 拼 payload `POST http://aiopc-ai-core:9301/opc/ai-core/opportunity/score` (OpenAI-兼容)
5. 拿到 `OpportunityScoreResponse {score, reason}` → 持久化到 `opp.score` + `opp.scoreReason` + 更新 `updatedAt`

### 3.4 Health Check 36 项 (W47 baseline 28 + 8 新增)

8 项 opc-crm 专属检查 (added in `check_crm()` 函数):

1. 容器 `aiopc-crm Up`
2. Nacos `opc-crm` 注册 1 实例
3. `opc-crm /actuator/health UP`
4. `GET /opc/crm/customer` body.code=200 (gateway 路由验证)
5. `GET /opc/crm/opportunity` body.code=200
6. `GET /opc/crm/dashboard` body.code=200 + funnel 字段
7. `GET /opc/crm/contract` body.code=200
8. `GET /opc/crm/order` body.code=200

`health-check.sh` 末尾新增 `export PYTHONIOENCODING=utf-8` 修复中文 UTF-8 错乱。

### 3.5 复用 W48/W49 patterns

- **Thin jar**: pom `<phase>none</phase>` + Dockerfile `java -cp "xxx.jar:lib/*"` + `mvn dependency:copy-dependencies`
- **Vanilla mybatis**: opc-common 提供 `mybatis-3.5.19.jar`,application.yml 配 `mybatis-plus:` 但 mapper 走 vanilla 扫描
- **`@ComponentScan({"com.ruoyi.opc", "com.ruoyi.system"})`** + **`@EnableRyFeignClients(basePackages = {"com.ruoyi.system.api", "com.ruoyi.opc"})`**
- **`spring.main.allow-bean-definition-overriding: true`** + **`spring.cloud.allow-bean-definition-overriding: true`**
- **Spring AI exclude**: application.yml `spring.autoconfigure.exclude` 列 25 个 OpenAI/ZhiPu AI 配置类
- **Gateway → upstream**: `http://aiopc-<svc>:<port>` Docker DNS 直连,不用 `lb://`

---

## 四、Bug 与修复

| Task | Bug | 修复 |
|------|-----|------|
| 4 | 6 个实体缺 `@Builder` (Service 测试 `OpcCrmCustomer.builder().build()` 编译失败) | 补加 `@Builder` (commit 8a0105f) |
| 19 | `OpportunityServiceImpl.create` 不设 `score=0` → `Column 'score' cannot be null` (MySQL 列 NOT NULL DEFAULT 0 但 INSERT XML 显式列名抑制默认) | 在 `create()` 补 `if (score == null) score = 0` (commit cfcd9ed) |
| 17 | `health-check.sh` 中文 UTF-8 错乱 | 加 `export PYTHONIOENCODING=utf-8` |

3 个 bug 全部在对应 task 内修复,未遗留到 W51。

---

## 五、文件清单

### 后端 (35 files)

- `opc-crm/pom.xml` + `Dockerfile` (thin-jar)
- `OpcCrmApplication.java` + `bootstrap.yml` + `application.yml`
- 5 enums (`OpportunityStage` / `CustomerLevel` / `CustomerSource` / `ContractStatus` / `OrderStatus`) + 1 `FollowUpType`
- 6 entities (`@Data @Builder` + `@JsonFormat`)
- 6 mappers + 6 mapper XML + `MybatisConfig`
- 7 service + 7 service impl (`@Transactional`)
- 7 controllers + `StageChangeRequest` DTO
- 3 DTO (`OpportunityScoreResponse` / `DashboardFunnelResponse`)
- 3 Feign gateways (`NotificationGateway` / `AiCoreGateway` / `UserCenterGateway`) + `FeignConfig`
- 1 `AiCoreGateway.scoreOpportunity` default helper method
- 51 unit tests (8 + 6 + 6 + 10 + 6 + 6 + 4 + 4 + 1)

### 前端 (6 files)

- `api/opc/crm.ts` (41 functions,axios-based)
- `views/opc/crm/CustomerList.vue` (列表 + 筛选)
- `views/opc/crm/CustomerDetail.vue` (档案 + 联系人 + 跟进 timeline + 商机 tabs)
- `views/opc/crm/OpportunityKanban.vue` (LEAD/QUALIFIED/PROPOSAL/NEGOTIATION/WON/LOST 6 列拖拽)
- `views/opc/crm/FollowUpTimeline.vue` (跟进时间线 + nextAt 高亮)
- `router/index.ts` (4 routes: `/opc/crm/customers` + `/opc/crm/customers/:id` + `/opc/crm/opportunities` + `/opc/crm/follow-ups`)

### 部署 / 运维 (8 files)

- `nacos/opc-crm-dev.yml` + `opc-crm-prod.yml`
- `docker-compose.yml` (aiopc-crm service)
- `ruoyi-gateway/src/main/resources/application.yml` (route `/opc/crm/**` → `http://aiopc-crm:9312`)
- `nacos/import-dev.sh` (NAMES 数组追加 opc-crm)
- `deploy/scripts/health-check.sh` (check_crm 函数 + 8 checks)
- `deploy/RECOVERY.md` (§11 opc-crm 恢复步骤)
- `deploy/helm/opc/values.yaml` (services 段加 crm)
- `deploy/helm/opc/templates/{deployment,service}-crm.yaml`

### 验证 (21 files)

- 19 task verification logs (`week-50/opc-crm-task*.md`)
- 1 E2E test (`tmp_e2e/crm_e2e.py`)
- 1 W50 verification report (本文)

---

## 六、运行时状态 (2026-09-11 实测)

### 6.1 容器

```
aiopc-crm            Up 6 minutes      0.0.0.0:9312->9312/tcp
aiopc-gateway        Up X minutes      0.0.0.0:8080->8080/tcp
aiopc-notification   Up X minutes      0.0.0.0:9310->9310/tcp
aiopc-ai-core        Up X hours        0.0.0.0:9301->9301/tcp
aiopc-user-center    Up X hours        0.0.0.0:9302->9302/tcp
aiopc-mysql          Up X hours        33060/tcp, 0.0.0.0:3307->3306/tcp
aiopc-nacos-1        Up X hours        0.0.0.0:8848->8848/tcp, 0.0.0.0:9848->9848/tcp
aiopc-redis          Up X hours        0.0.0.0:6379->6379/tcp
```

### 6.2 健康检查 (`health-check.sh`)

```
[8] opc-crm 健康检查 (W50 Task 17)
[OK]   aiopc-crm running
[OK]   Nacos opc-crm 注册 1 实例 (opc-dev)
[OK]   opc-crm /actuator/health UP
[OK]   /opc/crm/customer body.code=200
[OK]   /opc/crm/opportunity body.code=200
[OK]   /opc/crm/dashboard body.code=200 (funnel + level stats)
[OK]   /opc/crm/contract body.code=200
[OK]   /opc/crm/order body.code=200

==================================================
  PASS: 36  FAIL: 0
==================================================
```

(W47 baseline 28 + 8 新增 opc-crm 专属 = 36)

### 6.3 E2E 测试 (`crm_e2e.py`)

```
============================================================
SUMMARY
============================================================
Total: 23  Pass: 23  Fail: 0
```

覆盖: Admin login / Customer (list / get / create / validation) / Contact (list / create) / FollowUp (create / complete) / Opportunity (create / change stage / LLM score) / Contract (create / activate) / Order (create / pay / ship) / Dashboard (funnel / customer summary) / 边界 (blank name / invalid stage transition / score null)。

注: E2E 在 commit 28fd046 时为 18/19 PASS,commit cfcd9ed (Task 19 fix) 后 23/23 PASS。Task 19 验收日志内含完整 root-cause 分析 (INSERT XML 显式列名 vs MySQL DEFAULT 行为)。

### 6.4 Nacos 注册

```
DATA_ID: opc-crm-dev.yml
GROUP: DEFAULT_GROUP
namespace: opc-dev (auto-created on first push by import-dev.sh)
1 healthy instance → 192.168.x.x:9312
```

### 6.5 Gateway 路由 (`ruoyi-gateway/src/main/resources/application.yml`)

```yaml
- id: opc-crm
  uri: http://aiopc-crm:9312
  predicates:
    - Path=/opc/crm/**
```

`spring.cloud.gateway.server.webflux` 段 (W47 教训: 老 `spring.cloud.gateway` 在 4.x 下 routes 一直是 `[]`)。

### 6.6 Helm 资源 (估算)

| env | Deployment | Service | PDB | HPA | total |
|-----|-----------|---------|-----|-----|-------|
| dev | +1 (crm) | +1 (crm) | 0 | 0 | dev 比 W49 多 2 |
| staging | +1 | +1 | +1 | 0 | staging 多 3 |
| prod | +1 | +1 | +1 | 0 | prod 多 3 |

(W49: dev=21 / staging=33 / prod=35 → W50: dev=23 / staging=36 / prod=38)

---

## 七、下一步 (W51-W54)

每个服务复用 W49 (notification) + W50 (crm) 的 patterns (thin-jar / vanilla mybatis / `@ComponentScan` / OpenFeign gateway / gateway route / Nacos dev-prod / 8 项 health-check / RECOVERY §N / E2E + 1 bug-fix 节奏),预计 20-25 tasks / 服务。

- **W51**: opc-hr (人力资源: 员工 / 考勤 / 薪酬 / 绩效) — 12 表
- **W52**: opc-community (社区论坛: 帖子 / 评论 / 点赞) — 6 表
- **W53**: opc-voice-agent (语音 Agent: ASR / TTS / 对话) — 8 表
- **W54**: opc-ecommerce (电商: 商品 / 订单 / 支付) — 10 表

---

## 八、关键文件路径

| 内容 | 路径 |
|------|------|
| 服务主目录 | `springboot3/ruoyi-modules/opc-crm/` |
| Application | `springboot3/ruoyi-modules/opc-crm/src/main/java/com/ruoyi/opc/crm/OpcCrmApplication.java` |
| bootstrap | `springboot3/ruoyi-modules/opc-crm/src/main/resources/bootstrap.yml` |
| Nacos dev/prod | `springboot3/deploy/nacos/opc-crm-{dev,prod}.yml` |
| Dockerfile | `springboot3/ruoyi-modules/opc-crm/Dockerfile` |
| docker-compose entry | `springboot3/deploy/docker-compose.yml` (`aiopc-crm` service) |
| Gateway route | `springboot3/ruoyi-modules/ruoyi-gateway/src/main/resources/application.yml` |
| 健康检查 | `springboot3/deploy/scripts/health-check.sh` (`check_crm` 函数) |
| RECOVERY §11 | `springboot3/deploy/RECOVERY.md` |
| Helm templates | `springboot3/deploy/helm/opc/templates/{deployment,service}-crm.yaml` |
| 前端 API | `RuoYi-Cloud-Vue3-typescript/src/api/opc/crm.ts` |
| 前端 views | `RuoYi-Cloud-Vue3-typescript/src/views/opc/crm/{CustomerList,CustomerDetail,OpportunityKanban,FollowUpTimeline}.vue` |
| 路由 | `RuoYi-Cloud-Vue3-typescript/src/router/index.ts` |
| E2E | `springboot3/tmp_e2e/crm_e2e.py` |
| W50 任务日志 | `docs/verification/week-50/opc-crm-task*.md` (19 files) |
| W50 验收报告 | `docs/verification/week-50/opc-crm-week50-verification.md` (本文) |

---

## 九、经验教训 (carried to W51)

1. **INSERT XML 显式列名抑制 MySQL DEFAULT**: service 层对 NOT NULL 列必须显式设默认,不能依赖数据库 DEFAULT。
2. **DTO @Builder 不能忘**: mapper XML 用 `LIMIT #{offset}` 配合 entity builder 时,`@Builder` 缺失编译期才发现,影响 downstream task 进度。
3. **Health check 中文 UTF-8**: 容器内 python 默认 locale 错乱,顶层 `export PYTHONIOENCODING=utf-8` 必须前置。
4. **gateway route DNS**: `lb://opc-crm` 在容器内 Nacos DNS 解析失败 (W48 教训),统一 `http://aiopc-<svc>:<port>` 直连。
5. **E2E 直接走后端路径**: W49 教训继续生效 — E2E 测试需经 gateway 才能暴露前后端契约不一致;本次 opportunity create 失败是经 gateway 的 admin token 才发现 score NOT NULL。
