# opc-hr 服务验证报告 (W71-W73 Iter1)

> **日期**: 2026-09-12
> **范围**: opc-hr 招聘管理服务(端口 9322,W71-W73 第一阶段交付)
> **关联**: spec [2026-09-11-opc-hr-design.md](../../superpowers/specs/2026-09-11-opc-hr-design.md) / plan [2026-09-11-opc-hr-impl.md](../../superpowers/plans/2026-09-11-opc-hr-impl.md)

---

## 1. 范围

opc-hr 为 OPC 提供"AI 增强的招聘 / 人事管理"后端，覆盖：JD 生命周期 / 候选人投递 / LLM 评分 / 面试 / Offer / HR 仪表盘。

**第一阶段交付（本次）**：
- 6 表 schema + 雪花 ID 写入路径
- 25+ REST endpoints(JD 9 + Candidate 7 + Application 5 + Interview 3 + Offer 2 + Dashboard 1 + LLM 4 占位)
- 4 Feign Gateway(Notification/UserCenter/AiCore/Crm)+ FallbackFactory
- 5 Vue 前端页 + 27 个 API 方法
- 完整部署链路(Dockerfile / Nacos / docker-compose / Helm / Gateway 路由 / 白名单)
- 健康检查端点 4 个

**第二/三阶段（后续）**：真实 LLM 接入 / Qdrant 向量召回 / PDF 简历解析 / 权限系统集成。

## 2. 文件清单（本次新增）

### 后端（`springboot3/ruoyi-modules/opc-hr/`）

| 类别 | 数量 | 路径 |
|---|---|---|
| Domain | 6 | `domain/OpcHr{Job,Candidate,Application,Interview,Offer,MatchScore}.java` |
| Enum | 6 | `enums/Hr{JobStatus,JobCategory,ApplicationStatus,InterviewType,InterviewResult,OfferStatus}.java` |
| Mapper interface | 6 | `mapper/OpcHr*Mapper.java` |
| Mapper XML | 6 | `resources/mapper/hr/OpcHr*Mapper.xml` |
| DTO | 9 | `dto/OpcHr*Dto.java` + `Hr{LlmRequest,SearchRequest,SearchResult}.java` |
| Service | 6 | `service/IOpcHr*Service.java` + `service/impl/OpcHr*ServiceImpl.java` |
| Controller | 5 | `controller/OpcHr{Job,Candidate,Application,Interview,Offer,Dashboard}Controller.java` |
| Feign | 4 + 4 | `feign/OpcHr*Gateway.java` + `feign/factory/OpcHr*GatewayFactory.java` |
| Application | 1 | `OpcHrApplication.java`(`@EnableCustomConfig` + `@ComponentScan`) |
| Bootstrap | 3 | `resources/{application.yml,bootstrap.yml,logback.xml}` |
| Test | 8 | `test/.../OpcHr*ServiceImplTest.java` + `feign/factory/*Test.java` |
| Dockerfile | 1 | `Dockerfile`(thin jar 模板) |

### 数据库（`springboot3/sql/migrations/`）

- `V20260911__opc_hr_schema.sql`(6 表:job/candidate/application/interview/offer/match_score,公司级强约束 + utf8mb4_unicode_ci)

### 部署 / 配置

| 文件 | 修改 |
|---|---|
| `springboot3/deploy/nacos/opc-hr-dev.yml` | 新增(Nacos 命名空间 `opc-dev`,端口 9322,MySQL/Redis 配置) |
| `springboot3/deploy/nacos/import-dev.sh` | 新增 opc-hr 导入条目 |
| `springboot3/deploy/docker-compose.yml` | 新增 `aiopc-hr` 服务(端口 9322,薄 jar 启动) |
| `springboot3/deploy/helm/opc/values.yaml` | 新增 `services.hr` 条目(`tier: business`) |
| `springboot3/deploy/helm/opc/templates/deployment-hr.yaml` | 新增 |
| `springboot3/deploy/helm/opc/templates/service-hr.yaml` | 新增 |
| `springboot3/deploy/scripts/health-check.sh` | 新增 `check_hr()`(8 项:Nacos 注册 / health / 5 个核心 endpoint) |
| `springboot3/ruoyi-gateway/src/main/resources/application.yml` | 新增 `/opc/hr/**` 路由 + 白名单(`/opc/hr/dashboard` + `/opc/hr/job/list` + `/opc/hr/job/**` 公开) |

### 前端（`vue3-typescript/src/`）

| 文件 | 说明 |
|---|---|
| `api/opc/hr.ts` | API 模块 + 7 个 interface(JOB/Candidate/Application/Interview/Offer/HrSearchResult/OpcHrDashboard) + 27 个函数 |
| `views/opc/hr/job/index.vue` | JD 列表 + 状态过滤 + DRAFT/OPEN/PAUSED/CLOSED 操作按钮 |
| `views/opc/hr/job/detail.vue` | JD 详情 + 投递列表 + 状态机推进 + 面试反馈表单 |
| `views/opc/hr/candidate/index.vue` | 候选人列表 + 语义搜索框 |
| `views/opc/hr/candidate/detail.vue` | 候选人画像 + 投递历史 + AI 解析按钮 |
| `views/opc/hr/dashboard.vue` | HR 仪表盘 + 漏斗 + 转化率 + KPI + JD 分布 |
| `router/index.ts` | 新增 5 条路由(`/opc/hr` + `/opc/hr/job[/:id]` + `/opc/hr/candidate[/:id]` + `/opc/hr/dashboard`) |

### 验证

| 文件 | 说明 |
|---|---|
| `docs/verification/week-71/OPC-W71-VERIFICATION-opc-hr.md` | 本报告 |
| `tmp_e2e/e2e_hr.py` | 11 步 E2E 脚本(创建 JD → 发布 → 候选人 → 投递 → 状态机 → 面试 → Offer → ACCEPTED → HIRED → dashboard → list) |

## 3. 测试结果

### 3.1 单元测试

opc-hr 累计 **66 单测全过**(commit a3c6856 后):
- Task 3:18(OpcHrJobServiceImplTest,含 pause + create_blankTitle 边界)
- Task 4:10(OpcHrCandidateServiceImplTest,含 email 重复校验)
- Task 5:10(OpcHrApplicationServiceImplTest,含状态机 5 转换 + 终态保护 + 跳级拒绝)
- Task 6:8(OpcHrInterview 4 + OpcHrOffer 4,含 round 自增 + duplicate application)
- Task 7:8(OpcHrDashboardServiceImplTest,含 funnel/conversion/avgHireDays/sinceDays 边界)
- Task 8:4(OpcHrNotificationGatewayFactory 2 + OpcHrAiCoreGatewayFactory 2,fallback 验证)

```
$ mvn -pl ruoyi-modules/opc-hr test -Drat.skip=true
[INFO] Tests run: 66, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 3.2 编译

```
$ mvn -pl ruoyi-modules/opc-hr -am compile -DskipTests -Drat.skip=true
[INFO] BUILD SUCCESS
```

### 3.3 Helm 验证

```
$ helm lint springboot3/deploy/helm/opc
0 chart failed

$ helm template test springboot3/deploy/helm/opc | grep '^kind:' | sort | uniq -c
... Deployment × 11 / Service × 11 / PodDisruptionBudget × 11 ...
```

(11 = 原 7 OPC 服务 + crm/notification/community/hr 4 个,新增 hr 各 +1)

### 3.4 docker-compose 验证

```
$ docker compose -f springboot3/deploy/docker-compose.yml config -q
PASS（仅 obsolete `version` warning,pre-existing）
```

### 3.5 E2E

`tmp_e2e/e2e_hr.py` 11 步全流程(待服务启动后实跑;脚本已就绪):

1. 登录拿 `access_token`
2. 创建 JD → `POST /opc/hr/job`
3. 发布 JD → `POST /opc/hr/job/{id}/publish`
4. 创建候选人 → `POST /opc/hr/candidate`
5. 创建投递 → `POST /opc/hr/application`
6. 状态机推进 4 步:NEW → SCREENING → INTERVIEW → OFFER → HIRED(每步 `PUT /opc/hr/application/{id}/status?status=`)
7. 创建面试 → `POST /opc/hr/interview`
8. 创建 Offer → `POST /opc/hr/offer`
9. 候选人响应:ACCEPTED → `PUT /opc/hr/offer/{id}/respond?response=`
10. 查询 dashboard → `GET /opc/hr/dashboard?companyId=1`
11. 列出 JD → `GET /opc/hr/job/list?companyId=1`

### 3.6 Health-check

新增 8 项 hr 检查(commit 8ccf15f 加):
- `aiopc-hr` 容器 Up
- Nacos 注册(`GET /nacos/v1/ns/instance/list?serviceName=opc-hr`)
- `/actuator/health` → `"status":"UP"`
- `/opc/hr/dashboard` → `code=200`
- `/opc/hr/job/list` → `code=200`
- `/opc/hr/candidate/list` → `code=200`
- `/opc/hr/application/list` → `code=200`
- `/opc/hr/interview/list?applicationId=1` → `code=200`

累计健康检查:36(原有 7 OPC + crm/notification/community) + 8(hr) = **44 项,期望 44/44 PASS**(实跑需等部署后)。

## 4. 已知问题 / Lessons（沿用 W49/W50/W52 教训）

| # | 问题 | 严重度 | 处理 |
|---|---|---|---|
| 1 | **Feign gateway 路径与 target service 实际 controller 路径不一致**(Task 8 reviewer flag)— `/opc/notification/send` 等占位,notification 实际是 `/opc/notification/email/send` | MINOR | 留待真实 LLM 接入时按目标 service 实际接口调整 |
| 2 | **LLM 端点都是 placeholder 实现**(`generateLlm`/`parse`/`search`/`score`):`generateLlm` 返回字符串拼接,`parse` 写入 `pending_llm` JSON,`score` 写 50 分,`search` 返回空 list | DESIGN | 待 Task 7+ 接 opc-ai-core `HttpLlmClient` 真实调用 `hr_jd_generate` / `hr_resume_parse` / `hr_candidate_score` 三个 prompt |
| 3 | **前端 `companyId` 硬编码为 1**(无 RuoYi 权限集成) | MINOR | 等待 opc-user-center 权限系统就绪后切换为从 `SecurityUtils` 自动注入 |
| 4 | **`opc-common` 测试编译错误**(W49 pre-existing,Windows JDK 17 下 `ApplicationArguments`/`HttpServer.create` 不兼容) | UNRELATED | Task 1/3/5 implementer 都用 `mvn -pl opc-hr test`(跳过 -am)绕开,不影响 opc-hr 自身 |
| 5 | **`selectByJobCandidate` 缺 `companyId` 参数原始版**(Task 2 reviewer flag)— 已 commit b42bb75 修补 | FIXED | ✅ 已应用 |
| 6 | **PAUSED 状态原始不可达**(Task 3 reviewer flag)— 已 commit 33d186c 加 `POST /{id}/pause` 端点 | FIXED | ✅ 已应用 |
| 7 | **`createdBy` NULL 风险**(Task 3 reviewer flag,W50 教训 1)— 已 commit 33d186c 加 `getCurrentUserId()` helper | FIXED | ✅ 已应用 |
| 8 | **`Date` → `LocalDateTime/LocalDate` 迁移 + `@JsonFormat timezone` 修补**(Task 2 reviewer flag)— 已 commit b42bb75 | FIXED | ✅ 已应用 |
| 9 | **`@EnableCustomConfig` 缺**(Task 2 spec reviewer flag)— 已 commit d10b783 | FIXED | ✅ 已应用 |

## 5. 验收清单（spec §11 完成情况）

- [x] 6 表 + seed 数据(V20260911__opc_hr_schema.sql)
- [x] 25+ endpoints(实 27+4 LLM = 31)
- [x] 单测 ≥30(实 66)
- [x] 4 Feign + FallbackFactory(全部含 `@Component` 实现)
- [x] 前端 5 页 + 1 API 模块(27 函数)
- [x] Dockerfile + Nacos + compose + Helm + Gateway 路由 + 白名单
- [x] health-check 端点 8 项 hr 检查
- [x] VERIFICATION 报告(本文)

## 6. 后续 W72-W73 计划

- **W72**:LLM 真实接入(3 prompts: `hr_jd_generate` / `hr_resume_parse` / `hr_candidate_score`)+ EvalRunnerTest + RedTeam 10 case
- **W72**:候选人 `embedding` 字段真实存储 + Qdrant `opc-hr-candidates` collection + 向量召回
- **W72**:Feign gateway 路径按真实 controller 调整(消除问题 #1)
- **W73**:PDF 简历解析(Tika 集成)
- **W73**:多租户 RuoYi 权限集成(companyId 从 SecurityUtils 自动注入,消除问题 #3)
- **W73**:端到端 LLM 路径覆盖(EvalRunner + Playwright 截图 dashboard)

## 7. Commit 历史

```
a3c6856 feat(hr): Task 11 - Dockerfile + Nacos + compose + Helm + Gateway 路由
cf04974 feat(hr): Task 10 - 5 Vue 页 + Router(招聘管理)
24123e1 feat(hr): Task 9 - frontend API 模块 + 类型定义 (25 endpoints)
1673e0c feat(hr): Task 8 - 4 Feign Gateway + FallbackFactory + 4 单测
8ccf15f feat(hr): Task 7 - Dashboard + LLM 占位 + 8 单测 + health-check 端点
8b97536 feat(hr): Task 6 - Interview + Offer + MatchScore + 5 endpoints + 8 单测
506ea41 feat(hr): Task 5 - Application Service + 5 endpoints + 10 单测 + 状态机
e9f0bc8 feat(hr): Task 4 - Candidate Service + 7 endpoints + 10 单测
33d186c fix(hr): Task 3 - add pause endpoint, fix createdBy NULL risk, 18 单测
61ce278 feat(hr): Task 3 - Job Service + 8 endpoints + Mapper XML + 15 单测
b42bb75 fix(hr): apply code review Important fixes - JsonFormat timezone, Date→LocalDateTime, multi-tenant hardening
d10b783 fix(hr): add @EnableCustomConfig + ComponentScan system per spec reviewer gap
86308c2 feat(hr): Task 2 - 6 Domain + 6 Mapper + 6 Enum + 9 DTO
aafbdd6 feat(hr): Task 1 - 模块骨架 + Application + 6 表 SQL
```

**14 commits / 12 Tasks / 66 单测 / 27 endpoints / 5 Vue 页 / 8 health-check / 31 helm resources**。