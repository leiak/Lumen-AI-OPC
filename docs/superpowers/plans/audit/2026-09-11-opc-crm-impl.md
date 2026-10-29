# Plan 审计报告：2026-09-11-opc-crm-impl.md

- **审计日期**：2026-10-29
- **被审 plan**：`docs/superpowers/plans/2026-09-11-opc-crm-impl.md`（2318 行，21 个 Task，119 个 checkbox）
- **plan 目标**：建 `opc-crm` 客户关系管理服务（客户档案 + 标签 + 联系人 + 跟进 + 商机 + 合同 + 订单 + LLM 商机打分），OPC 扩展路线图第二个服务（W55-W58，4 周压成单冲刺）
- **审计方式**：只读仓库 + git 历史，逐条核实 119 个 checkbox。判据见 `/tmp/opc-plan-audit-rubric.md`：① 文件在且内容对得上 ② 代码里 grep 得到 ③ 测试文件与测试方法在 ④ 该路径在时间窗内有提交 —— 四条至少命中一条才勾
- **审计口径**：CRM 这块 2026-10 有大改动（按人隔离 `fedbba9`、LLM 评分做实 `481d2de`），**凡是"仓库里现在有"的步骤一律照勾**，不管它是当时按原样做的还是后来补的
- **结果**：**勾了 86 / 没勾 33 / 共 119**

---

## 1. 逐 Task 汇总

| Task | 主题 | 步骤数 | 勾了 | 没勾 | 一句话结论 |
|---|---|---|---|---|---|
| Task 1 | Scaffold opc-crm 模块 | 7 | 6 | 1 | **做了**。pom（thin-jar `<phase>none</phase>` + vanilla MyBatis）/ Dockerfile / Application / bootstrap.yml 全在，只差"跑一次 mvn compile 看输出" |
| Task 2 | MySQL migration + seed SQL | 5 | 4 | 1 | **做了**。6 表 DDL 与 plan 逐字一致，seed 行数 10/30/50/20/8/12 一条不差，initdb.d 两份副本也在 |
| Task 3 | Domain enums | 4 | 3 | 1 | **做了**。`OpportunityStage` 的状态机与 plan 抄的完全相同，另外 4 个枚举也一字不差 |
| Task 4 | Domain entities（6 POJO） | 7 | 7 | 0 | **做了**。6 个实体字段与 plan 列的逐个对得上（还多加了 `@Builder`） |
| Task 5 | Mapper interfaces + XML | 6 | 5 | 1 | **做了**。`MybatisConfig` + 6 Mapper + 6 XML 全在，`selectList`/`countByOwner`/`countByStage`/`sumAmountByOwnerAndStages` 等自定义查询都在 |
| Task 6 | CustomerService + tests | 6 | 4 | 2 | **做了**。8 个 plan 点名的测试方法一个不缺（后来又加了 4 个越权用例） |
| Task 7 | ContactService + tests | 4 | 3 | 1 | **做了**。`setPrimary` 是 plan 说的 3 步（unset all → set new） |
| Task 8 | FollowUpService + tests | 4 | 3 | 1 | **做了一半**。Service 在，但 plan 要求的 NotificationGateway 自动通知**没有**（网关后来被删） |
| Task 9 | OpportunityService + tests | 4 | 3 | 1 | **做了一半**。状态机 + LLM 评分都在（评分后来做实了），但 plan 写的"阶段变更 best-effort 通知"没有 |
| Task 10 | ContractService + tests | 4 | 3 | 1 | **做了**。create/activate/expire/terminate/listByCustomer 齐全，`countByContractNo` 唯一性校验在 |
| Task 11 | OrderService + tests | 4 | 3 | 1 | **做了**。pay/ship/complete/cancel 状态机在 |
| Task 12 | DashboardService + tests | 4 | 3 | 1 | **做了**。funnel / byLevel / topOpportunities / activeAmount / wonAmount 字段全在 |
| Task 13 | OpenFeign gateways + 状态机 | 9 | 4 | 5 | **没做完**。3 个网关只剩 `AiCoreGateway` 1 个，`OpportunityStateMachine` 类和它的 8 个测试压根没建 |
| Task 14 | REST Controllers | 9 | 8 | 1 | **做了**。7 个 controller 全在，41 个端点，plan 点名的 `/score` `/stage` `/activate` `/pay` 等都在 |
| Task 15 | Nacos 配置 + 部署文件 | 6 | 6 | 0 | **做了**。dev/prod 两份 yml、compose 的 `aiopc-crm`、gateway 路由 `Path=/opc/crm/**`、Dockerfile 的 `EXPOSE 9312` 全在 |
| Task 16 | Push Nacos + build + 起容器 | 8 | 2 | 6 | **只有产物在**。thin jar 和 `target/dependency/` 确实存在，但推配置/重建镜像/起容器/冒烟全是运行时动作，仓库里查不到 |
| Task 17 | health-check + RECOVERY + Helm | 8 | 6 | 2 | **做了大半**。8 项 CRM 健康检查 + RECOVERY §11 + Helm Deployment/Service 都在，但 `values-{dev,staging,prod}.yaml` 里没有 crm 段 |
| Task 18 | 前端 4 个视图 + API | 9 | 8 | 1 | **做了**。`crm.ts`（41 函数）+ 4 个 Vue 视图 + 4 条路由（3 条走 sys_menu）+ 侧边栏全在；但**没有拖拽** |
| Task 19 | E2E 测试 | 3 | 2 | 1 | **做了**。`tmp_e2e/crm_e2e.py`（332 行 / 20 个场景）覆盖了 plan 列的全部场景 |
| Task 20 | W55 验收报告 | 3 | 3 | 0 | **做了**。报告 + `docs/verification/README.md` 的 W50 索引都在（文件名与 plan 写的不同） |
| Task 21 | Final acceptance + memory | 5 | 0 | 5 | **整个没做**。memory 文件、MEMORY.md 指针、收尾总结、最终报告一个都没有，git log 里也没有 Task 21 的提交 |
| **合计** | | **119** | **86** | **33** | |

---

## 2. 没勾的 33 个步骤（全部列出 + 理由）

### 2.1 "跑一次看输出"类（24 个）——仓库里查不到运行结果，一律不勾

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 1 Step 6 | 验证编译（`mvn -pl ruoyi-modules/opc-crm -am compile`） | （未完成：仓库里查不到运行结果，没有 CI 记录或构建日志文件能证明这次 mvn compile 跑过） |
| Task 2 Step 4 | 应用到 live MySQL（`docker exec aiopc-mysql mysql ...`） | （未完成：仓库里查不到运行结果；只有自述日志 docs/verification/week-50/opc-crm-task2-mysql-seed.md，没有 SHOW TABLES 的实际输出） |
| Task 3 Step 3 | 验证编译 | （未完成：仓库里查不到运行结果，没有 CI 记录或构建日志文件） |
| Task 5 Step 5 | 验证编译 | （未完成：仓库里查不到运行结果，没有 CI 记录或构建日志文件） |
| Task 6 Step 2 | 跑测试确认失败（Expected: 编译错误） | （未完成：仓库里查不到运行结果，opc-crm 下没有 surefire 报告文件） |
| Task 6 Step 5 | 跑测试确认通过（Expected: Tests run: 8, Failures: 0） | （未完成：仓库里查不到运行结果，opc-crm 下没有 surefire 报告文件） |
| Task 7 Step 3 | 跑测试 + verify 4/4 PASS | （未完成：仓库里查不到运行结果，opc-crm 下没有 surefire 报告文件） |
| Task 8 Step 3 | 跑测试 + verify 6/6 PASS | （未完成：仓库里查不到运行结果，opc-crm 下没有 surefire 报告文件） |
| Task 9 Step 3 | 跑测试 + verify 10/10 PASS | （未完成：仓库里查不到运行结果，opc-crm 下没有 surefire 报告文件） |
| Task 10 Step 3 | 跑测试 + verify 6/6 PASS | （未完成：仓库里查不到运行结果，opc-crm 下没有 surefire 报告文件） |
| Task 11 Step 3 | 跑测试 + verify 6/6 PASS | （未完成：仓库里查不到运行结果，opc-crm 下没有 surefire 报告文件） |
| Task 12 Step 3 | 跑测试 + verify 4/4 PASS | （未完成：仓库里查不到运行结果，opc-crm 下没有 surefire 报告文件） |
| Task 13 Step 8 | 跑 gateway + 状态机测试（Expected: 12 tests, 0 failures） | （未完成：仓库里查不到运行结果，而且被它测的 OpportunityStateMachineTest 本身不存在） |
| Task 14 Step 8 | 验证全部编译 | （未完成：仓库里查不到运行结果，没有 CI 记录或构建日志文件） |
| Task 16 Step 2 | 推送 Nacos 配置（`bash ./import-dev.sh`） | （未完成：Nacos 推送是运行时动作，仓库里查不到；按 opc-nacos-config-namespace 的记录，opc-dev 配置中心本来就是空的） |
| Task 16 Step 3 | 重建 gateway（fat-jar） | （未完成：仓库里查不到运行结果，没有 CI 记录或构建日志文件） |
| Task 16 Step 4 | 构建 Docker 镜像 | （未完成：仓库里查不到运行结果，没有镜像构建记录） |
| Task 16 Step 5 | 重启容器 | （未完成：仓库里查不到运行结果，容器运行态不属于仓库内容） |
| Task 16 Step 6 | 等 60s 验证启动（Expected: Started OpcCrmApplication） | （未完成：仓库里查不到运行结果） |
| Task 16 Step 7 | 冒烟测试（curl `/opc/crm/customer?keyword=北京`） | （未完成：仓库里查不到运行结果） |
| Task 17 Step 7 | 验证（health-check 36/36 + helm lint + diff-envs） | （未完成：仓库里查不到运行结果） |
| Task 18 Step 8 | 前端 build + type-check（`npm run build:prod`） | （未完成：仓库里查不到运行结果，没有 CI 记录或构建日志文件） |
| Task 19 Step 2 | 跑 E2E（Expected: ~11 tests pass） | （未完成：仓库里查不到运行结果，tmp_e2e/crm_e2e.py 没有对应的执行报告） |
| Task 21 Step 1 | 最终验收（docker ps + health-check + e2e + helm lint + git log） | （未完成：仓库里查不到运行结果） |

> 补充说明：`docs/verification/week-50/opc-crm-task16-deploy.md` 里贴了 `Started OpcCrmApplication in 10.546 seconds`、`BUILD SUCCESS`、`204 jars` 这样的控制台输出，`opc-crm-week50-verification.md` 里贴了"51 测试 / 23 E2E / 36 健康"的自述表格。但那是**自己写的 markdown 里的粘贴片段**，不是 CI 记录、也不是测试报告文件（opc-crm 下没有 `surefire-reports/` 目录，全仓只有 ruoyi-gateway 有）。按判据 3，仓库里查不到运行结果，因此这一类 step 一律不勾。

### 2.2 文件真的没做（5 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 13 Step 2 | 创建 `gateway/NotificationGateway.java` | （未完成：gateway/NotificationGateway.java 在仓库里不存在——b29ec10 建过，后来被删（对端 /opc/notification/inbox/push 根本不存在），全模块 grep 不到 notification） |
| Task 13 Step 5 | 创建 `gateway/UserCenterGateway.java` | （未完成：gateway/UserCenterGateway.java 在仓库里不存在） |
| Task 13 Step 6 | 创建 `statemachine/OpportunityStateMachine.java` + 8 @Test | （未完成：statemachine/OpportunityStateMachine.java 和 statemachine/OpportunityStateMachineTest.java 都不存在；状态机仍写在 OpportunityStage 枚举里，只有 OpportunityServiceTest 的 4 个 transition 用例覆盖） |
| Task 13 Step 7 | 创建 `NotificationGatewayTest.java`（4 @Test） | （未完成：src/test/java/com/ruoyi/opc/crm/gateway/NotificationGatewayTest.java 不存在；该目录下只有 AiCoreGatewayFallbackTest.java） |
| Task 17 Step 4 | 加到 `values-{env}.yaml`（3 个环境） | （未完成：values-dev.yaml / values-staging.yaml / values-prod.yaml 三个文件里都没有 crm 段） |

### 2.3 Task 21 收尾一步没做（4 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 21 Step 2 | Capture summary | （未完成：Task 21 在仓库里没有任何产出物，git log 里也没有对应提交） |
| Task 21 Step 3 | 写 memory 文件 `opc-crm-w55.md` | （未完成：memory 目录里没有 opc-crm-w55.md 这个文件） |
| Task 21 Step 4 | 给 MEMORY.md 加指针 | （未完成：MEMORY.md 里没有 CRM 指针） |
| Task 21 Step 5 | Final report | （未完成：仓库里没有 Task 21 的最终报告产物，git log 里也没有对应提交） |

### 2.4 计数
- "跑一次看输出"类：**24 个**
- 文件真的没做：**5 个**
- Task 21 收尾没做：**4 个**
- 合计：**33 个**

---

## 3. 勾上了但只做了一半的（文件在，行为/细节跟 plan 对不上）

这些是最容易被误读成"全做了"的条目，逐条列出来：

| 位置 | 做了什么 | 没做什么 / 变成了什么 |
|---|---|---|
| Task 8 Step 2 | `FollowUpServiceImpl` 在，校验 + 默认值 + 归属守卫都对 | plan 写的 **`notificationGateway.sendInbox(...)` 自动通知没有**——`NotificationGateway` 类被删了，`FollowUpServiceImpl` 里连 `notification` 字样都 grep 不到 |
| Task 9 Step 2 | `changeStage` 状态机校验（`Invalid stage transition: %s → %s`）+ `score` 调 LLM 都在，而且评分后来做实了（走 `POST /opc/llm/chat`，带 fallback，失败不写库） | plan 写的 **"阶段变更 best-effort 通知"没有**——`changeStage` 里没有 `notificationGateway` 调用 |
| Task 13 Step 3 | `AiCoreGateway` 在，并且是真能用的（`@FeignClient(name="opc-ai-core", path="/opc/llm")` + `AiCoreGatewayFallback`，对端不挂就返回 `R.fail`，绝不编分数） | **不是 plan 写的那一个**。plan 写的是 `@FeignClient(url="${opc.crm.ai-core.base-url}")` + `POST /opc/ai-core/opportunity/score`——这个端点在 opc-ai-core 里**从来不存在**（那边只有 `OpcLlmController` 的 `/opc/llm/chat`），而且 FeignClient 写了 `url` 会绕过 Nacos 发现。plan 的写法等于"点了 LLM 评分必定失败" |
| Task 13 Step 4 | `OpportunityScoreResponse` 在，`score` + `reason` 两个字段都在，实战中被 `score()` 返回 | plan 写的 **`risks`（风险列表）字段没有** |
| Task 15 Step 1 | `opc-crm-dev.yml` / `opc-crm-prod.yml` 在，端口 9312 + 命名空间 opc-dev/opc-prod 都对 | plan 里的 `opc.crm.notification / ai-core / user-center.base-url` **三段被删了**（配置里留了注释说明为什么：三个下游地址都指向不存在的端点） |
| Task 18 Step 2 | `CustomerList.vue` 在，列 name/level/source/tags/phone/owner 都有，action 按钮（查看/编辑/删除）在，筛选（关键词/等级/来源/标签）在 | 用的是共享组件 **`ResponsiveTable` 而不是 plan 写的 `el-table`**；筛选是**行内表单不是 plan 说的 filter drawer** |
| Task 18 Step 4 | `OpportunityKanban.vue` 在，6 个阶段列（LEAD/QUALIFIED/PROPOSAL/NEGOTIATION/WON/LOST）都在，卡片显示 amount + score，未评分显示"未评分"而不是画 0 分进度条 | **没有拖拽**——`drag`/`drop`/`vuedraggable` 一个字都 grep 不到，阶段推进改成了点"推进"弹窗 |
| Task 18 Step 6 / 7 | 4 条路由 + 侧边栏菜单都在（`/opc/crm/customers/:id` 在 `router/index.ts`，另外 3 条在 `V20261022__opc_sys_menus.sql` 的 `sys_menu` 里，父目录 menu_id=2009） | 路由**不是 plan 写的"在 `router/index.ts` 里加 4 条"**——`router/index.ts` 顶部长注释自己写着：sys_menu 是 OPC 侧边栏的唯一事实源，加 constantRoutes 会"菜单看得见、一点就 404" |
| Task 2 Step 2 | `crm_seed.sql` 行数 10/30/50/20/8/12 与 plan 一字不差 | 20 条商机的 **seed `score` 列被去掉了**（commit `481d2de` / `e70f7cc` 把编造的 85/70/95 归零），`V20261031` / `V20261032` 两个迁移补做这件事 |
| Task 7 Step 1 | `ContactServiceTest` 在，plan 点名的 4 个用例（create / listByCustomer / setPrimary / delete 不存在）都在 | （后来又加了 7 个越权用例） |
| Task 8 Step 1 | `FollowUpServiceTest` 在，create / listByCustomer / listByOwner / upcoming / markDone 都在 | **没有 `delete` 用例**，而且 `FollowUpService` 接口本身就没有 delete 方法（plan 列了 6 个场景，实际只有 5 个） |
| Task 10 Step 1 | `ContractServiceTest` 在，create / 唯一性校验 / activate / expire / terminate / listByCustomer 都在 | **没有 `update` 用例**（`ContractService.update` 方法本身是有的） |
| Task 11 Step 1 | `OrderServiceTest` 在，create / items_json 校验 / pay / ship / complete / cancel 都在 | **没有 `update` 用例**（`OrderService.update` 方法本身是有的） |
| Task 20 Step 1 | 验收报告在（`docs/verification/week-50/opc-crm-week50-verification.md`，16.7 KB），格式与 plan 要求的 6 段一致，外加 20 份 per-task 验证日志 | **文件名不是 plan 写的** `OPC-W55-VERIFICATION-opc-crm.md`；报告自称"W50"不是"W55"；报告里写的"51 测试 / 23 E2E / 36 健康"是当时的数字 |

---

## 4. plan 自己说的数字和代码对不上（口头承诺，不是 checkbox，没计入统计）

- plan 说 **"8 endpoints (was 6)"** → 实际 7 个 controller 一共 **41 个端点**（Customer 5 / Contact 6 / FollowUp 5 / Opportunity 6 / Contract 7 / Order 8 / Dashboard 4）。plan 顶部的 "Total 6 tables, 8 endpoints" 严重低估。
- plan 说 **"56 @Test"**（8+4+6+10+6+6+4+8+4）→ opc-crm 模块现在实际有 **87 个 @Test**（Customer 12 / Opportunity 23 / Contract 12 / Order 12 / Contact 11 / FollowUp 10 / Dashboard 4 / AiCoreGatewayFallback 2 / CrmMapperXmlParse 1）。
- plan 说 **"E2E: ~11 scenarios"** → `tmp_e2e/crm_e2e.py` 实际有 **20 个场景**（commit `28fd046` 的标题就写 "20 scenarios"）。
- plan 说 **"Health-check: 36 endpoints"** → `health-check.sh` 现在已经长到 **24 节**（`check_crm` 是第 7 节，`check_crm_ownership` 是第 14 节，`check_crm_ai_score` 是第 21 节）；plan 里的"28 → 36"是 2026-09-11 的数字。
- plan 说 **"6 tables"** → 对的，6 张表一张不多一张不少。
- plan 说 **"4 周压缩成单冲刺 (W55-W58)"** → 从第一个 CRM 提交 `af451af`（Task 1）到最后一个 W50 文档提交 `ceca3f1` 只用了 **3 天**（2026-09-11 → 09-23）。

---

## 5. 有没有"声称做了但其实没有"的条目？——有，而且不止一处

按严重程度排：

1. **Task 13 的 LLM 商机打分端点是假的（最严重）。** plan 写 `AiCoreGateway` 打 `POST /opc/ai-core/opportunity/score`。opc-ai-core 里从来没有这个端点（只有 `OpcLlmController` 的 `POST /opc/llm/chat` 和 `GET /opc/llm/models`），而且 plan 写的 `@FeignClient(url=...)` 会**绕过 Nacos 服务发现**、去连一个容器网里根本解析不了的主机名 `opc-ai-core`。也就是说 plan 交付时"LLM 商机打分"这个**核心卖点按钮点了必定失败**。直到 2026-10 的 `481d2de` 才改成按服务名发现 + `path=/opc/llm` + fallback 真正跑通。**plan 本身没有留下"这个端点需要先在对端实现"的任何提示。**
2. **Task 8 / Task 9 的自动通知从来不存在。** plan 两处都写了 Feign 调 `opc-notification` 推 inbox。对端的 `InboxController` 只有 `GET /`、`/read/{id}`、`/read-all`、`/unread-count`，**没有 `/push`**。这两个 gateway 后被删掉是对的——但 plan 写的时候并不知道。
3. **Task 13 的 `UserCenterGateway` 打的地址也是假的。** plan 写 `GET /opc/user-center/user/{id}`，对端（`OpcUserController`）的 `@RequestMapping` 是 `/opc/user`，且**根本没有 `/user/{id}` 这个方法**（只有 `/profile`、`/companies`、`/company-members`）。
4. **Task 13 Step 6 的"抽出来是为了可测"没做。** plan 说把状态机从枚举里抽成独立类 "extracted from enum for testability"，实际状态机**至今仍写在 `OpportunityStage` 枚举里**，`statemachine/` 这个包在仓库里根本不存在。测试也就没落到 plan 说的那个文件里（只有 `OpportunityServiceTest` 的 4 个 transition 用例）。
5. **Task 18 Step 4 的"拖拽看板"没有拖拽。** 6 个阶段列在、金额和分数在，但 `drag`/`drop`/`vuedraggable` 一个都搜不到。
6. **Task 17 Step 4 的 3 份 env values 没有 crm 段。** `values.yaml`（默认）里有 `services.crm`，但 `values-dev.yaml` / `values-staging.yaml` / `values-prod.yaml` 三个都没有——按 plan 自己的说法这 3 个都要加。
7. **File Structure 里列的 7 个 Request DTO 一个都没建**：`CustomerCreateRequest` / `CustomerUpdateRequest` / `ContactCreateRequest` / `FollowUpCreateRequest` / `OpportunityCreateRequest` / `ContractCreateRequest` / `OrderCreateRequest` 全都不在仓库里（controller 直接收 domain 对象）。File Structure 段本身没有 checkbox，所以不计入 33 个，但它是 plan 正文的一部分。
8. **仓库里现在还有一处"文档说假话"**（不是 plan 的锅，是 plan 落地后没人清理）：`springboot3/deploy/RECOVERY.md` §11 现在还写着"阶段变更通知通过 `opc-crm.opportunity.auto-notify-on-stage-change` 控制，dev 默认 `true`"——**这个开关在 `application.yml` 和 `opc-crm-dev.yml` 里都已经删掉了**，代码里也没有任何地方读它。同一节还把 initdb 文件名写成 `12-opc-crm-schema.sql` / `13-crm-seed.sql`，仓库里实际是 `09-opc-crm-schema.sql` / `95-opc-crm-seed.sql`。

---

## 6. 整体结论：这份 plan 靠不靠谱？

**真做了的比例：86 / 119 = 72%。** 在 9 份 plan 里属于中上——代码是真的写出来了，20 个 Task 全都有产物落地，没有一个是空中楼阁。

**逐 Task 看：**
- **整体没做的 Task：1 个** —— 只有 **Task 21（Final acceptance + memory update）** 一步没做（5 个 checkbox 全空），而且它确实是个"收尾"性质的任务，没做不影响服务本身。
- **做了一半的 Task：3 个** —— **Task 13（OpenFeign gateways，4/9）** 最明显：3 个网关只剩 1 个，状态机类和它的测试压根没建。**Task 8 / Task 9** 各缺一个 Feign 自动通知。
- **产物全在但只有"跑一次"没证的 Task：3 个** —— Task 16（6/8 只勾了 build 产物和 commit）、Task 17（6/8）、Task 19（2/3）。这些是运行环境相关的，代码侧看不出真假。
- **完全做透的 Task：4 个** —— Task 4（7/7）、Task 15（6/6）、Task 20（3/3）、Task 2（除"应用到 live MySQL"外全做，DDL 与 plan 逐字一致）。

**这份 plan 最要命的地方不是空 checkbox，而是它把"打通了"当成了"存在"。** Task 13 里三个 Feign gateway 被 plan 当成已完成项写进交付清单，实际上**三个地址里有两个端点在对端根本不存在、一个绕过了服务发现**——结果是"LLM 商机打分"这个 plan 标题里点名的核心功能，在交付当天是**点了必失败**的。同类问题还有两处自动通知。plan 用"Create XxxGateway"这种动词描述步骤，读起来像是"建好一个类就算完"，可对端有没有这个端点，plan 一个字都没提。

**另外，plan 顶部的几个数字（8 endpoints / 56 @Test / ~11 E2E）全都对不上实际**，唯一对得上的是"6 tables"。

**一句话**：这份 plan 的"做"是真的（20 个 Task 全有代码落地，72% 的 checkbox 站得住），可以当历史文档读；但**读 plan 会高估完成度**——它把三个打不通的 Feign 调用算成已交付，把一个不存在的拖拽交互算成已交付，把"建好网关类"等同于"链路通"。真正的差距集中在一处：**CRM 的跨服务调用（notification / ai-core / user-center）在整个 W50 期间是断的**，直到 2026-10 才由 `481d2de` 把 LLM 评分真正接通、并把另外两个死网关从配置里删干净。
