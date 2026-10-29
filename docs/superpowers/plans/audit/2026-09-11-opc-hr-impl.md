# Plan 审计报告：2026-09-11-opc-hr-impl.md

- **审计日期**：2026-10-29
- **被审 plan**：`docs/superpowers/plans/2026-09-11-opc-hr-impl.md`
- **plan 目标**：交付 `opc-hr` 微服务（端口 9322），含 6 表 / 25 REST / 4 Feign / 3 LLM prompt / 5 Vue 页 / Dashboard，3 周交付
- **审计方式**：只读仓库 + git 历史，逐条核实 69 个 checkbox。判据：① 文件在且内容对得上 ② 代码里 grep 得到 ③ 测试文件与测试方法在 ④ 提交在 plan 时间窗（2026-09-11 ~ 2026-09-14）内
- **结果**：**勾了 54 / 没勾 15 / 共 69**

---

## 1. 逐 Task 汇总

| Task | 主题 | 步骤数 | 勾了 | 没勾 | 一句话结论 |
|---|---|---|---|---|---|
| Task 1 | 模块骨架 + Application + 6 表 SQL | 9 | 8 | 1 | **做了**。pom/yml/logback/Application/6 表 SQL/pom 注册全部落地，只差"跑一次 mvn compile 看输出" |
| Task 2 | 6 Domain + 6 Mapper + 6 枚举 + DTO | 6 | 5 | 1 | **做了**。枚举逐字照抄 plan，DTO 从 7 个做到 11 个 |
| Task 3 | Job Service + 8 endpoints + 单测 | 6 | 5 | 1 | **做了**。9 个端点 / 20 个单测；`publish()` 少了一步 LLM 生成 JD |
| Task 4 | Candidate Service + 7 endpoints + 单测 | 6 | 5 | 1 | **做了一半**。解析接通了，`search()` 是空列表占位，embedding 整条链没人写 |
| Task 5 | Application Service + 5 endpoints + 状态机 | 5 | 4 | 1 | **做了**。状态机 `canTransitionTo()` 与 plan 写的完全一致 |
| Task 6 | Interview + Offer + MatchScore | 5 | 4 | 1 | **做了一半**。5 端点齐，但两处状态推进 + 两个 Feign 回调没实现 |
| Task 7 | Dashboard + 1 endpoint | 4 | 3 | 1 | **做了**。4 段聚合齐全，8 个单测 |
| Task 8 | 4 个 Feign Gateway + Fallback | 4 | 2 | 2 | **基本做了**。4 网关 + 4 FallbackFactory 都在，但 `OpcHrFeignConfig.java` 压根没建 |
| Task 9 | 前端 API 模块 | 2 | 2 | 0 | **做了**。27 个函数 / URL 全对得上，只是函数改了名 |
| Task 10 | 5 个 Vue 页 + Router | 8 | 7 | 1 | **做了**。5 页都在；但 dashboard 没用 ECharts、candidate 解析按钮不在 index 页 |
| Task 11 | Dockerfile + Nacos + compose + Helm | 8 | 6 | 2 | **做了大半**。prod 配置和 whitelist 文件没建，其余全落地 |
| Task 12 | health-check + E2E + VERIFICATION | 6 | 3 | 3 | **半做**。脚本和报告都写了，但 E2E 明确"待实跑"，health-check 没跑过 |
| **合计** | | **69** | **54** | **15** | |

---

## 2. 没勾的 15 个步骤（全部列出 + 理由）

### 2.1 "跑一次看输出"类（14 个）——仓库里查不到运行结果，一律不勾

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 1 Step 8 | 验证编译（`mvn -pl ruoyi-modules/opc-hr -am compile`） | （未完成：仓库里查不到运行结果） |
| Task 2 Step 5 | 验证编译 | （未完成：仓库里查不到运行结果） |
| Task 3 Step 5 | 跑单测（Expected: 15/15 PASS） | （未完成：仓库里查不到运行结果） |
| Task 4 Step 5 | 跑单测（Expected: 10/10 PASS） | （未完成：仓库里查不到运行结果） |
| Task 5 Step 4 | 跑单测（Expected: 10/10 PASS） | （未完成：仓库里查不到运行结果） |
| Task 6 Step 4 | 跑单测（Expected: 8/8 PASS） | （未完成：仓库里查不到运行结果） |
| Task 7 Step 4 | 跑单测 + Commit | （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit `8ccf15f` 完成） |
| Task 8 Step 3 | 验证编译 | （未完成：仓库里查不到运行结果） |
| Task 10 Step 7 | 验证 type-check + build（`npm run build:prod`） | （未完成：仓库里查不到运行结果） |
| Task 11 Step 6 | 验证 helm lint | （未完成：仓库里查不到运行结果） |
| Task 11 Step 7 | 验证 docker compose（`docker compose config -q`） | （未完成：仓库里查不到运行结果） |
| Task 12 Step 2 | 重 build 前端 + 启容器 | （未完成：仓库里查不到运行结果） |
| Task 12 Step 3 | 跑 health-check（Expected: PASS 36→44） | （未完成：仓库里查不到运行结果） |
| Task 12 Step 4 | E2E 测试（Expected: ≥15/15 PASS） | （未完成：仓库里查不到运行结果；脚本 `tmp_e2e/e2e_hr.py` 只有 11 步，plan 要求 ≥15 步，且 VERIFICATION 报告 §3.5 自己写着「待服务启动后实跑；脚本已就绪」，即确实没跑） |

> 补充说明：`docs/verification/week-71/OPC-W71-VERIFICATION-opc-hr.md` §3.1~§3.4 里贴了 `mvn test` / `helm lint` / `docker compose config` 的控制台输出。但那是**自己写的 markdown 里的粘贴片段**，不是 CI 记录、也不是测试报告文件。按判据 3，仓库里查不到运行结果，因此这一类 step 一律不勾。

### 2.2 文件真的没做（1 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 8 Step 2 | 创建 `OpcHrFeignConfig.java` | （未完成：`OpcHrFeignConfig.java` 在仓库里没有；opc-hr 下没有 `config/` 目录，全仓库只有 opc-crm 的 `config/FeignConfig.java`） |

### 2.3 计数
- "跑一次看输出"类：**14 个**（其中 13 个纯验证 + Task 7 Step 4「跑单测 + Commit」这个混合 step）
- 文件真的没做：**1 个**（Task 8 Step 2 `OpcHrFeignConfig.java`）
- 合计：**15 个**

---

## 3. 勾上了但只做了一半的 9 个（行尾已标「（部分：…）」）

这些是最容易被误读成"全做了"的条目，逐条列出来：

| 位置 | 做了什么 | 没做什么 |
|---|---|---|
| Task 3 Step 2 | Service/Impl 齐全，create/update/delete/publish/close 都在 | `publish()` **没有**按 plan 调 LLM 生成 `fullJd` + `skillsJson`，只改状态和 `publish_at`；接口名是 `IOpcHrJobService` 不是 `OpcHrJobService` |
| Task 4 Step 2 | `parse()` 已实接 opc-ai-core 的 `hr_resume_parse` 场景 | `create()` 不调 `parseResume`、**`embedding` 字段整条链没人写**（domain 有、mapper XML 有、service 不写）；`search()` 是**占位实现，直接返回空列表**，没有 Qdrant 向量召回、没有 LLM 重排 |
| Task 6 Step 1 | 3 端点 + round 自增 + application 存在性/同租户校验 | `create()` 没校验 application 状态必须是 INTERVIEW/SCREENING；`updateFeedback()` 只更新面试行——**PASS 不推进 application、FAIL 不推 REJECTED、也不强制填 reason** |
| Task 6 Step 2 | 2 端点 + `uk_application` 唯一性校验 | `expire_at` 由调用方传入，不是 plan 写的 NOW()+7 天；**`respond()` 只改 offer 状态**——不把 application 推到 HIRED/REJECTED，也不触发 NotificationGateway 通知 HR、不触发 UserCenterGateway 建员工档案 |
| Task 10 Step 2 | JD 详情 + 状态徽章 + 投递表格 + 面试反馈 | 没有 plan 说的「发布/关闭」按钮（这两个按钮在 `job/index.vue` 列表行上） |
| Task 10 Step 3 | 语义搜索框 + 列表表格 + 新建候选人 | 没有文件上传按钮；「解析简历」按钮在 `candidate/detail.vue` 上，index 页没有 |
| Task 10 Step 4 | 基本信息 + 简历解析 tab + 投递历史 tab | `parsed_json` 是整段 JSON 原文用 `<pre>` 展示，没拆成 plan 说的教育/工作/技能/项目字段 |
| Task 10 Step 5 | 漏斗 + 转化率 + 平均招聘时长 + JD 状态分布 4 块都有 | 漏斗是 `el-table` + 进度条画的，**没用 ECharts**；没有 JD 状态分布饼图；没有 ≥768 横排 / <768 纵排的 media query |
| Task 11 Step 2 | `opc-hr-dev.yml` 在，端口 9322 + 命名空间 opc-dev 一致 | `opc-hr-prod.yml` 没有、`import-prod.sh` 里也没有 opc-hr 条目；`whitelist-opc-hr.yml` 没有——白名单写在 `ruoyi-gateway/application.yml` 里 |

---

## 4. 整体结论：这份 plan 靠不靠谱？

**真做了的比例：54 / 69 = 78%。** 这是 9 份 plan 里"空 checkbox 骗人"程度偏高的一份——代码是真的写出来了，不是 plan 凭空列的。

**逐 Task 看：**
- **整体没做的 Task：0 个。** 12 个 Task 全都有产物落地，没有一个是纯空中楼阁。
- **做了一半的 Task：2 个**（Task 4 Candidate、Task 6 Interview/Offer）——共同特征是「Service 层骨架有了，但跨对象的状态推进 / 外部回调没接」。
- **半成品 step 分布在 5 个 Task**（Task 3 / 4 / 6 / 10 / 11，共 9 个 step），文件在但行为/命名/路径跟 plan 对不上。
- **整个 plan 唯一一件从头到尾没碰过的东西**：`OpcHrFeignConfig.java`（Task 8 Step 2）。

**有没有"声称做了但其实没有"的条目？——有，而且不止一处。** 值得点名的是：

1. **Task 6 Step 2 的 `Offer.respond()`**：plan 写"ACCEPTED → application → HIRED + 触发 NotificationGateway 通知 HR + UserCenterGateway 创建员工档案"。实际代码里 `OpcHrOfferServiceImpl.respond()` 只做了一件事——把 offer 的 status 改成 ACCEPTED/REJECTED。**application 状态没推进，两个 Feign 网关一个都没调**。也就是说 plan 描述的整个业务闭环（Offer → 入职 → 通知 HR → 建员工档案）在代码里是断的。
2. **Task 6 Step 1 的 `Interview.updateFeedback()`**：plan 写"result=PASS 自动推进 application → INTERVIEW/OFFER；result=FAIL → application → REJECTED，必填 reason"。实际只更新面试自己那一行。
3. **Task 4 Step 2 的 `Candidate.search()`**：plan 写"embedding 相似度检索 + LLM 重排序"。实际是 `return Collections.emptyList();`，注释里自己写着"占位实现:Task 8+ 实接 Qdrant 向量召回"。plan 顶部"候选人向量复用 Qdrant 已有 collection"这句话在代码里没有任何对应物。
4. **Task 4 Step 2 的 `Candidate.create()`**：plan 写"调 AiCoreGateway.parseResume(resumeUrl) → parsedJson + embedding 落库"。实际 `create()` 直接把 DTO 里的 `parsedJson` 抄进库，**embedding 这一列从头到尾没有人写过**。
5. **Task 3 Step 2 的 `Job.publish()`**：plan 写"**调用 AiCoreGateway.llmGenerate(jd_generate_prompt)** 写入 fullJd + skillsJson"。实际 publish 只翻状态位，LLM 挪到了另一个不落库的 `/generate-llm` 端点上。
6. **Task 10 Step 5 的 dashboard**：plan 写"漏斗图（ECharts）+ ... + JD 状态分布饼图"，实际是一张 `el-table`。

另外，**plan 自己写的数字和代码对不上**（这是 plan 里的口头承诺，不是 checkbox，所以没在上面计入）：

- plan 说 "25 endpoints"，实际控制器里点得着的是 **27 个**（Job 9 + Candidate 7 + Application 5 + Interview 3 + Offer 2 + Dashboard 1）——VERIFICATION 报告后来自己也改口成 27。
- plan 说 "48 单测（15+10+10+8+5）"，实际 opc-hr 模块现在是 **77 个 @Test**（58 个在 2026-09-14 写报告时，之后 873ea07 等又加了一批）。
- plan 说 "3 周交付"，实际从 plan 提交到最后一个 HR 提交只用了 **4 天**（2026-09-11 → 09-14）。
- plan 说 "25 endpoints" 的 `Interview` 模块是 3 端点、`Offer` 2 端点，加起来 26（Job 9 / Candidate 7 / Application 5 / Interview 3 / Offer 2 / Dashboard 1 = 27），plan 自己在 Self-Review 里算的就是 "Task 3 (8) + Task 4 (7) + Task 5 (5) + Task 6 (5) + Task 7 (1) = 26"，也不是 25。

**一句话**：这份 plan 的"做"是真的（12 个 Task 全有代码落地，78% 的 checkbox 站得住），可以放心当历史文档读；但它的 checkbox 空着、以及几处关键业务链路（Offer→入职通知、面试结果推进、候选人向量检索、JD 生成落库）在 plan 里被描述得比代码里实际实现的更完整，**读 plan 会高估完成度**——这正是这次审计要解决的问题。
