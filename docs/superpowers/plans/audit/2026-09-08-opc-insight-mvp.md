# Plan 审计报告：2026-09-08-opc-insight-mvp.md

- **审计日期**：2026-10-29
- **被审 plan**：`docs/superpowers/plans/2026-09-08-opc-insight-mvp.md`（写于 2026-09-08，20 个 Task + Day 8 最终检查点）
- **plan 目标**：新建 `opc-insight` Spring Boot 微服务（端口 9306），交付 4 个能力（日报 / 经营驾驶舱 / 异常预警 / 决策建议），8 天节奏
- **审计方式**：只读仓库 + git 历史，逐条核实 105 个 checkbox。判据：① 文件在且内容对得上 ② 代码里 grep 得到 ③ 测试文件与测试方法在 ④ 提交在 plan 时间窗（2026-09-08）内。**「跑 mvn test / npm run build / helm lint 确认全绿」这类步骤一律不勾** —— 仓库里没有 CI 记录、没有测试报告文件
- **结果**：**勾了 71 / 没勾 34 / 共 105**

---

## 1. 逐 Task 汇总

| Task | 主题 | 步骤数 | 勾了 | 没勾 | 一句话结论 |
|---|---|---|---|---|---|
| Task 1 | opc-finance 4 个聚合端点 | 6 | 4 | 2 | **做了**。`OpcFinanceController` 里 4 个 `@GetMapping("/agg/...")` 齐全；4 个 service 方法都在（voucher/flow 复用 `aggregateByPeriod`、`taxReportService.getByCompanyAndPeriod`、`tokenUsageService.aggregateByPeriod`）；测试 5 个 `@Test` |
| Task 2 | opc-billing 3 个聚合端点 | 1 | 0 | 1 | **做了，只是「跑一遍」查不到结果**。`/agg/wallet`、`/agg/orders`、`/agg/recharge` 3 个端点在，测试 4 个 `@Test`，commit `a5e9db2` |
| Task 3 | opc-insight Maven 模块骨架 | 6 | 5 | 1 | **做了**。`pom.xml` / `OpcInsightApplication.java` / `bootstrap.yml`（port 9306）/ `application.yml` 都在；`ruoyi-modules/pom.xml` 里已注册 `<module>opc-insight</module>` |
| Task 4 | SQL 3 张表 | 3 | 2 | 1 | **做了**。`V20260908__opc_insight_schema.sql` 里 `opc_insight_daily_report` / `opc_insight_anomaly` / `opc_insight_advice` 三张表 + 索引都在；`deploy/mysql-initdb.d/07-opc-insight-schema.sql` 是同一份的部署副本 |
| Task 5 | KpiService + 3 个 Feign + 12 测试 | 7 | 5 | 2 | **做了**。`KpiSnapshot` 字段逐字对得上；3 个 Feign client 的 `@GetMapping` 路径与 plan 逐字一致；`KpiServiceImplTest` 12 个 `@Test` |
| Task 6 | AnomalyRule + AnomalyService + 18 测试 | 9 | 7 | 2 | **做了**。8 条硬规则枚举齐全；mapper + XML 的 `selectOpenByCompany` / `updateStatus` 都在；测试 21 `@Test` + 2 `@ParameterizedTest`（比 plan 的 18 还多） |
| Task 7 | Quartz 日报 job | 6 | 3 | 3 | **job 做了，调度注册没做**。`InsightDailyReportJob` + 4 个 `@Test` 在；但 `sys_job` 表里没有这条记录 —— **定时任务实际不会自己跑** |
| Task 8 | DailyReportService + 10 测试 | 7 | 5 | 2 | **做了**。VO / domain / mapper（纯 MyBatis，非 plan 写的 MyBatis-Plus `BaseMapper`）/ XML 都在；`DailyReportServiceImplTest` 15 个 `@Test` |
| Task 9 | AdviceService + 8 测试 | 5 | 4 | 1 | **做了**。7 天缓存靠 `selectRecent(companyId, topic, sinceDays)` 实现，XML 里对得上；`AdviceServiceImplTest` 12 个 `@Test` |
| Task 10 | 4 个 Controller + 12 测试 | 8 | 6 | 2 | **做了**。4 个 controller、11 个端点方法（plan 列的 10 个路径全对得上，advice 另多一个 POST 根路径 `POST /opc/insight/advice?topic=`）；测试是 standalone MockMvc 而非 plan 写的 `@WebMvcTest` |
| Task 11 | Nacos 路由 + 白名单 | 4 | 2 | 2 | **路由做了，白名单没做**。路由现在落在 `ruoyi-gateway/application.yml`（`Path=/opc/insight/**` → `http://aiopc-insight:9306`）；`opc-common-prod.yml` 里没有 `/opc/insight/**` |
| Task 12 | 前端 api 封装 + 12 测试 | 5 | 3 | 2 | **做了**。`insight.ts` 9 个函数、`insight.spec.ts` 12 个 `it` 都在；`regenerateAdvice` 的签名与 plan 不同（按 id 重生成 `POST /advice/{id}/regenerate`，不是 `?topic=`），前端也没封装后端那个 `POST /advice?topic=` |
| Task 13 | 4 个 Vue 页 + 52 SFC 测试 | 5 | 4 | 1 | **做了**。4 个 `.vue` + 4 个 spec，测试数 18+12+12+10 = 52，与 plan 完全一致 |
| Task 14 | Router + 菜单 | 3 | 2 | 1 | **当时做了，后来被改掉**。`2be03bc`（2026-09-08）确实写进了 `router/index.ts`；2026-10-21 的 `362c308` 把 OPC 硬编码路由收敛，改由 `sys_menu` 2028-2032 动态菜单接管 |
| Task 15 | 3 个 insight prompt | 4 | 4 | 0 | **做了，但路径写错 + 没人加载**。文件实际在 `springboot3/ruoyi-modules/opc-ai-core/...`（plan 写的是 `springboot3/opc-ai-core/...`）；仓库里**没有任何代码加载这三个 .txt**，insight 服务自己内联拼 prompt |
| Task 16 | 80 条 eval + Mock provider | 4 | 3 | 1 | **做了**。`insight-agent-v1.0.json` 正好 80 条（KPI_SUMMARY / ANOMALY / ADVICE / TRAP 各 20）；`MockInsightLlmProvider` 在；`EvalInsightRunnerTest` 4 个 `@Test` |
| Task 17 | redteam 10 条 + PromptGuard 接线 | 4 | 2 | 2 | **数据集做了，安全接线没做**。10 条用例 + `InsightRedteamRunnerTest`（4 个 `@Test`）在；**insight 提示词里没有 8 条注入正则，也没有 `insight-system-safety-v0.3.txt`**（finance 有对应文件），且 insight 链路直接调 `llmGateway.chat(...)`，**没走 `PromptGuard.sanitize()`** |
| Task 18 | Helm chart + docker-compose | 6 | 5 | 1 | **做了**。`service-insight.yaml` / `deployment-insight.yaml` / 3 个 values（replica 1/2/3）/ compose 的 `aiopc-insight` 服务都在 |
| Task 19 | live LLM eval | 4 | 2 | 2 | **没做成**。报告文件在 commit `5674452` 里，但内容是 `Status: BLOCKED`；`-Peval-live` profile 在 `opc-ai-core/pom.xml` 里根本不存在，`EvalInsightRunnerTest` 硬编码 Mock |
| Task 20 | 验证报告 | 2 | 2 | 0 | **做了**。`OPC-M4-VERIFICATION-insight-mvp.md` 在（commit `44c637e`，2026-09-10 归档到 `docs/verification/milestones/m4-insight/`）—— 但报告里有 3 处声称与仓库对不上，见 §4 |
| 最终检查点 | Day 8 六项 | 6 | 1 | 5 | **基本没留痕**。5 项都是「跑一遍看输出」类，仓库里查不到运行结果；git tag 一个都没打 |
| **合计** | | **105** | **71** | **34** | |

---

## 2. 没勾的 34 个步骤（全部列出 + 理由）

### 2.1 「跑一次看输出」类（25 个）—— 仓库里查不到运行结果，一律不勾

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 1 Step 2 | 跑测试确认失败（`OpcFinanceAggControllerTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 1 Step 5 | 跑测试确认通过（`OpcFinanceAggControllerTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 3 Step 5 | `mvn clean install -pl ruoyi-modules/opc-insight -am` 验证 BUILD SUCCESS | （未完成：仓库里查不到运行结果） |
| Task 4 Step 2 | 用 `docker exec ... SOURCE` 验证 SQL 语法 | （未完成：仓库里查不到运行结果） |
| Task 5 Step 4 | 跑测试确认失败（`KpiServiceImplTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 5 Step 6 | 跑测试确认 12 passed（`KpiServiceImplTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 6 Step 6 | 跑测试确认失败（`AnomalyServiceImplTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 6 Step 8 | 跑测试确认 18 passed（`AnomalyServiceImplTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 7 Step 2 | 跑测试确认失败（`InsightDailyReportJobTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 7 Step 5 | 跑测试确认 4 passed（`InsightDailyReportJobTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 8 Step 4 | 跑测试确认失败（`DailyReportServiceImplTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 8 Step 6 | 跑测试确认 10 passed（`DailyReportServiceImplTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 9 Step 4 | 跑测试确认 8 passed（`AdviceServiceImplTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 10 Step 2 | 跑测试确认失败（`OpcInsightControllerMvcTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 10 Step 7 | 跑测试确认 12 passed（`OpcInsightControllerMvcTest`） | （未完成：仓库里查不到测试运行结果） |
| Task 11 Step 3 | 用 `apply-whitelist.sh` 验证白名单导入 | （未完成：仓库里查不到 Nacos 导入结果 —— `apply-whitelist.sh` 在，但没有 `/opc/insight/**` 的导入记录） |
| Task 12 Step 2 | 跑测试确认失败（`insight.spec.ts`） | （未完成：仓库里查不到测试运行结果） |
| Task 12 Step 4 | 跑测试确认 12 passed（`insight.spec.ts`） | （未完成：仓库里查不到测试运行结果） |
| Task 13 Step 6 | `npm run test:run` 跑全部（Expected 680 passed） | （未完成：仓库里查不到测试运行结果） |
| Task 14 Step 2 | `npm run build` 验证 BUILD SUCCESS | （未完成：仓库里查不到运行结果） |
| Task 18 Step 5 | `helm lint` + `helm template` 验证 prod 资源 | （未完成：仓库里查不到运行结果） |
| 最终检查点 1 | All 218 tests pass | （未完成：仓库里查不到测试运行结果；口径也对不上 —— plan 写 64 个后端测试，opc-insight 实际有 81 个 `@Test`） |
| 最终检查点 2 | `mvn clean install` all modules pass | （未完成：仓库里查不到运行结果） |
| 最终检查点 3 | `npm run build` frontend passes | （未完成：仓库里查不到运行结果） |
| 最终检查点 4 | `helm lint` + `helm template` for all 3 envs passes | （未完成：仓库里查不到运行结果） |

> 补充说明：`docs/verification/milestones/m4-insight/OPC-M4-VERIFICATION-insight-mvp.md` §7 里贴了 `mvn test` / `npm run test:run` / `helm lint` 的控制台输出。但那是**自己写的 markdown 里的粘贴片段**，不是 CI 记录、也不是测试报告文件。按判据 3，仓库里查不到运行结果，因此这一类 step 一律不勾。
>
> 审计时（2026-10-29）顺手实测了一下当前状态，供读者参考，**但不作为勾选依据**：`helm lint springboot3/deploy/helm/opc/` → `1 chart(s) linted, 0 chart(s) failed`；`helm template` 三个 env 也都能渲染出 `opc-insight` 的 Deployment/Service（本机 helm 默认 kube version 是 v1.23，chart 要求 `>=1.24.0-0`，需要加 `--kube-version 1.29.0`）。也就是说 Task 18 Step 5 / 最终检查点 4 现在大概率是过的 —— 但仓库里同样没有能证明它「当时过了」的记录。

### 2.2 「跑 + Commit」混合 step（3 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 2 Step 1-6 | 照 Task 1 的 TDD 套路做 3 个 billing 端点 | （未完成：仓库里查不到测试运行结果；3 个聚合端点、测试文件、commit `a5e9db2` 都在，缺的只是"跑一遍"这一步） |
| Task 16 Step 4 | Run + commit（80 条 eval） | （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit `a2f480c` 完成） |
| Task 17 Step 4 | Run + commit（redteam） | （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit `9dacb7e` 完成） |

### 2.3 文件/功能真的没做（6 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 7 Step 4 | 注册 Quartz job 到 `sys_job` seed | （未完成：`springboot3/sql/seed/sys_job_workflow_seed.sql` 全文 grep 不到 insight / insightDailyReportJob，`deploy/mysql-initdb.d/` 里也没有这条 sys_job INSERT —— Quartz job 从来没注册进调度表） |
| Task 11 Step 2 | 白名单加 `/opc/insight/**` | （未完成：`springboot3/deploy/nacos/opc-common-prod.yml` 里 grep 不到 `/opc/insight/**`，`deploy/nacos/` 下也没有 `opc-insight-*.yml` —— 白名单这条没落地） |
| Task 17 Step 3 | PromptGuard v0.3 接入 insight 提示词 | （未完成：`insight-system-v1.0.txt` 里只有 4 条泛化护栏、没有 8 条注入正则；仓库里也没有 finance 那样的 `insight-system-safety-v0.3.txt`，而且 opc-insight 直接调 `llmGateway.chat(...)`，没走 `PromptGuard.sanitize()`） |
| Task 19 Step 1 | 用真 LLM 跑 80 条 eval | （未完成：仓库里查不到运行结果 —— 仓库自带的 `target/eval-reports/insight-result-v0.1.md` 自己写着 `Status: BLOCKED`，`-Peval-live` profile 在 `opc-ai-core/pom.xml` 里根本不存在） |
| Task 19 Step 3 | pass rate < 85% 就迭代 prompt | （未完成：live eval 压根没跑，没有 pass rate 可迭代；3 个 insight prompt 自 `894c3d0` 之后没有再改过） |
| 最终检查点 6 | 打 git tag `v0.4.0-insight-mvp` | （未完成：仓库里一个 git tag 都没有，`git tag -l "v0.4.0-insight-mvp"` 返回空） |

### 2.4 勾上了但只做了一半（5 个，行尾已标「（部分：…）」）

| 位置 | 说明 |
|---|---|
| Task 10 Step 1 | 12 个测试的覆盖面在，但实现用的是 `MockMvcBuilders.standaloneSetup(...)`，不是 plan 写的 `@WebMvcTest`（plan 写的 `@Import({SecurityConfig.class, GlobalExceptionHandler.class})` 没出现） |
| Task 11 Step 1 | 路由最初写进 `opc-routes.json`（commit `6ed2da7`），该文件 2026-10-21 被当过时配置删掉（`0deaf75`），路由现由 `ruoyi-gateway/application.yml` 的 `Path=/opc/insight/**` 承担 —— 功能没丢，落点变了 |
| Task 14 Step 1 | `2be03bc` 当时确实写进了 `router/index.ts`；2026-10-21 的 `362c308` 把 OPC 硬编码路由收敛掉，改由 `sys_menu` 2028-2032 动态菜单接管。`router/index.spec.ts` 现在还专门断言「不存在 path 为 `/opc/insight` 的常量路由」 |
| Task 15 Step 1 | `insight-system-v1.0.txt` 只有 40 行（plan 写 ~80 行）；且仓库里没有任何代码加载这三个 prompt 文件 —— insight 服务在 `LlmSoftAnomalyDetector` / `DailyReportServiceImpl` / `AdviceServiceImpl` 里**内联拼 prompt** |
| Task 19 Step 2 | `target/eval-reports/insight-result-v0.1.md` 确实被提交了（commit `5674452`，路径逐字一致），但内容是 `Status: BLOCKED` 的阻塞说明，不是真实 live eval 结果 |

---

## 3. plan 自己的「New Files」清单与仓库对不上的地方

plan 开头锁定的文件清单有 2 处与仓库对不上（这不是 checkbox，是清单本身的错）：

| plan 写的 | 仓库实际 |
|---|---|
| `springboot3/ruoyi-modules/opc-insight/.../config/InsightFeignConfig.java` | **不存在**。`config/` 目录下只有 `NoOpTokenUsageRecorder.java` |
| `springboot3/opc-ai-core/src/main/resources/prompts/insight-*.txt`（3 个）<br>`springboot3/opc-ai-core/src/main/resources/eval/insight-*.json`（2 个）<br>`springboot3/opc-ai-core/src/test/java/com/ruoyi/opc/ai/eval/MockInsightLlmProvider.java` | 全部在 `springboot3/ruoyi-modules/opc-ai-core/...` 下 —— plan 少写了一层 `ruoyi-modules/`。文件都在，只是 plan 的路径写错 |

另外 plan 写「218 个测试（64 backend + 64 frontend + 80 eval + 10 redteam）」，实际 opc-insight 有 **81 个 `@Test`**（12 + 21 + 4 + 15 + 12 + 17），前端 64 个（12 + 52）确实对得上，eval 80 / redteam 10 也对得上 —— 只有后端那一栏对不上。

---

## 4. 「声称做了但其实没有」的条目

**plan 本身的 checkbox 里没有假账**（全空，没有一条声称做过却没做）。但它配套的验证报告 `docs/verification/milestones/m4-insight/OPC-M4-VERIFICATION-insight-mvp.md` 里有 6 处声称与仓库对不上 —— 这份报告 §10 的清单全是 `[x]`，§11 结论是「A+，任务范围内 100% 完成」：

| 报告里的声称 | 仓库实际 |
|---|---|
| §9.2 「`springboot3/sql/seed/sys_job_workflow_seed.sql` 追加 `insightDailyReport` Quartz job（`0 0 9 * * ?`）」 | **该文件里没有这一行**。全文 grep 不到 insight / insightDailyReportJob；里面注册的是 `workflowCronJob.execute('finance_daily_report_v1')`，那是 opc-agent-hub 的财务日报，不是 INSIGHT |
| §9.2 「`springboot3/deploy/nacos/opc-common-prod.yml` + `/opc/insight/**` 白名单」 | **该文件里没有 `/opc/insight/**`**（`opc.common.audit.include-uri-patterns` 只有 `/opc/finance/**` `/opc/billing/**` `/opc/user/**`） |
| §9.1 「`config/InsightFeignConfig.java` —— Feign 配置」 | **文件不存在** |
| §6.2 / §10「PromptGuard v0.3 复用：insight-system-v1.0 prompt 直接调用 `PromptGuard.sanitize()`」 | **没有任何代码加载这三个 prompt 文件**，更没有调用 `PromptGuard.sanitize()`。insight 走的是 `llmGateway.chat(...)` → `LlmGateway` 内部也没有 guard；`PromptGuard` 只挂在 `AgentRuntime` 和 `OpcLlmController` 上 |
| §9.3 三处 prompt/eval 路径写成 `springboot3/opc-ai-core/...` | 实际在 `springboot3/ruoyi-modules/opc-ai-core/...` |
| §3 矩阵 / §9.2 「Deploy：`opc-routes.json` + `opc-common-prod.yml`」 | `opc-routes.json` 已于 2026-10-21 删除（`0deaf75`，理由是「过时」），路由改由 gateway 自己的 `application.yml` 承担；`opc-common-prod.yml` 那条根本没加 |

另外两处是「当时为真、后来被改掉」，不算报告造假，但要记一笔：
- §9.4 「`vue3-typescript/src/router/index.ts` + `/opc/insight/*` 路由 + 菜单」—— 2026-09-08 属实，2026-10-21 的 `362c308` 把这些常量路由删了，改走 `sys_menu`。
- §4.5 「Redteam 10 cases，Mock ASR 0.0%（0/10）—— 全部由 PromptGuard v0.3 拦截」—— 数字是 mock 下的，且既然 insight 链路根本没接 PromptGuard，这个「拦截」在生产链路上并不成立。

---

## 5. 结论：这份 plan 靠不靠谱

**代码层面靠谱，交付声明层面不靠谱。**

- **真做了的比例很高**：105 个 checkbox 里 71 个勾得上，20 个 Task 里 18 个整体落地 —— 整个 `opc-insight` 模块（46 个 Java 文件 + 3 张表 + 4 个前端页 + Helm/compose + 80 条 eval + 10 条 redteam）确实在仓库里，而且不是空壳：`AnomalyRule` 的 8 条硬规则逐条对得上、3 个 Feign client 的 URL 逐字对得上、mapper XML 的 SQL 逐条对得上、SFC 测试数 18/12/12/10 与 plan 完全一致、opc-insight 有 81 个 `@Test`。**没有「把没做的写成做了」的 plan 条目。**
- **没做的 34 条里 28 条是同一个原因**：步骤本身写的是「跑一遍看输出」，仓库不留下运行痕迹。这类缺口是**记录问题，不是交付问题** —— 代码在、测试在、commit 在。
- **真没做的 6 条里，有 3 条会咬人**：
  1. **Quartz job 没注册进 `sys_job`**（Task 7 Step 4）—— 「每天 9 点自动出日报」这个能力**实际上不会自己触发**。Bean `@Component("insightDailyReportJob")` 在，但没有任何调度记录指向它。验证报告还把这行写进了「文件清单」，读的人会以为它接上了。
  2. **PromptGuard 没接进 insight 链路**（Task 17 Step 3）—— 10 条 redteam 用例和「0% ASR」都在，但 ASR 是 mock 打出来的；生产路径上 insight 的用户输入**不经过任何注入过滤**（`llmGateway.chat(...)` 不带 sanitize）。
  3. **live LLM eval 是 BLOCKED**（Task 19）—— `-Peval-live` profile 在 `opc-ai-core/pom.xml` 里不存在，`EvalInsightRunnerTest` 硬编码 Mock，所以「80 条 100% 通过」只是 mock 自己的复读。三个 prompt 从 `894c3d0` 之后再没改过，也没人真的用真模型调过。
- **另外 2 条不痛不痒**：Nacos 白名单没加（按设计 INSIGHT 端点本来就要登录，匿名白名单本就不该加；但 plan 写的是「opc-common-prod.yml 里加一条」，确实没做）、git tag 没打。
- **给后人的一句话**：**别信这份 plan 的 checkbox（它自己全是空的），也别全信它的验证报告（§9 的文件清单有 3 条对不上）** —— 唯一的查法是打开仓库看文件。这份 plan 里的「做了什么」基本属实，「怎么证明做了」基本不属实。

---

*审计只读了仓库和 git 历史，没有跑 docker、没有连 mysql、没有起服务。除本 plan 文件与本报告外未改动任何文件。*
