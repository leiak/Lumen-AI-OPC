# Plan 审计报告：2026-10-20-opc-marketplace-p1-impl.md

- **审计日期**：2026-10-29
- **被审 plan**：`docs/superpowers/plans/2026-10-20-opc-marketplace-p1-impl.md`
- **plan 目标**：交付 `opc-marketplace` 微服务（端口 9317）P1 MVP —— 作者入驻 + Agent/Module 上架 + 安装（只支持免费 Agent）+ 评价 + 类目 + 标签，配 Vue 3 公开店面。plan 自称：6 张新表 / 6 个 controller / 28 个端点 / 60+ 单测 / 12+ e2e。
- **审计方式**：只读仓库 + git 历史，逐条核实 85 个 checkbox（14 个 Task 组）。判据：① 文件在且内容对得上 ② 代码里 grep 得到 ③ 测试文件与测试方法在 ④ 提交在 plan 时间窗（2026-10-20 ~ 2026-10-22）内
- **结果**：**勾了 66 / 没勾 19 / 共 85**

---

## 0. 实际数字（plan 声称 vs 仓库实测）

| 指标 | plan 声称 | 实测（2026-10-29） | 判定 |
|---|---|---|---|
| **REST 端点（P1 范围）** | **28**（Goal 行）；plan 自己的 File Structure 逐个 controller 加总是 **29** | **29**（6 个 controller：Author 6 / Install 5 / Publication 10 / Review 4 / Category 3 / Tag 1） | plan 自相矛盾，仓库按 **29** 交付 |
| **REST 端点（今天全部）** | — | **34**（7 个 controller）—— P2 又加了 `SubscriptionController` 5 个 | 超出 P1 范围 |
| **单测（`@Test` 方法数）** | **60+**（Goal）；**63**（验收标准表）；**68**（Task 8.5 Step 5）；逐文件 14/8/10/8/5/4/8/5/1 = 63 | **172** 个 `@Test`，14 个测试类 | "60+" 这个下限成立；63/68 两个具体数字**严重过期**（W80 P1 交付当时是 148，见验证报告 §1.1） |
| 表 | 6 张 | **6 张**（`mp_author` / `mp_publication` / `mp_install` / `mp_review` / `mp_category` / `mp_tag`）+ P2 的 `mp_subscription` = 7 | ✅ 达标 |
| controller | 6 个 | P1 交付 6 个（今天 7 个） | ✅ 达标 |
| e2e 步数 | 12 步 | `tmp_e2e/e2e_marketplace.py` 实际 12 步 + 1 个 Step 4.5 | ✅ 达标（**但从未运行过**） |
| 前端 API 模块 | 28 endpoints（File Structure 注释） | 28 个导出函数 / 24 个去重 URL | ✅ 函数数对上了（4 个 subscription 函数是 P2 加的） |
| health-check 新增 | 21 个端点 | P1 时 **10 项**（`check_marketplace()` 挂 step 11）；今天 step 19/20 又各加一段，marketplace 断言合计 ~70 条 | 数字口径不同，见 §4.3 |
| Helm kind 数 | dev=18 / staging=28 / prod=29 | 同一份验证报告写的是 **dev 31 / staging 48 / prod 50** | plan 的期望值过期 |

---

## 1. 逐 Task 汇总

| Task | 主题 | 步骤数 | 勾了 | 没勾 | 一句话结论 |
|---|---|---|---|---|---|
| Task 1 | 模块骨架 + Application + 6 表 SQL | 9 | 7 | 2 | **做了**。pom/yml/logback/Application/6 表 SQL 全落地；差"跑一次看输出" |
| Task 2 | 6 实体 + 6 Mapper + 6 XML + 3 枚举 | 6 | 5 | 1 | **做了**。12 个文件全在，statement 齐全（`MpMapperXmlParseTest` 锁着这件事） |
| Task 3 | 10 个 DTO | 4 | 3 | 1 | **做了**。请求 DTO 7 个 + 响应 VO 3 个，一个不多一个不少 |
| Task 4 | AuthorService + Controller + 测试 | 6 | 5 | 1 | **做了**。6 端点 + `toPublicVO` 脱敏 + fail-closed 三处守卫都在 |
| Task 5 | PublicationService + Controller + 测试 | 7 | 6 | 1 | **做了**。10 端点 + `getPublishedById` 硬过滤 + 批量 VO 装配（N+1 已修） |
| Task 6 | InstallService + Feign + Controller + 测试 | 7 | 6 | 1 | **做了**。5 端点 + `reactivate` 复活分支（唯一键与软删的坑填了） |
| Task 7 | ReviewService + Controller + 测试 | 6 | 5 | 1 | **做了**。4 端点 + avg_rating 回写 |
| Task 8 | Category + Tag + Controllers + 测试 | 7 | 6 | 1 | **做了**。3 + 1 端点，9 个测试用例全在 |
| Task 8.5 | 补服务测试（凑 60+） | 6 | 5 | 1 | **做了，而且超了**。4 个测试文件全在，实际单测从 63 涨到 148（当时）→ 今天 172 |
| Task 9 | 应用启动 + 健康检查 + Dockerfile | 4 | 2 | 2 | **做了一半**。Dockerfile 有了；"启动起来"和"smoke 一下"从没发生过 |
| Task 10 | Gateway + Nacos + Helm + Compose | 7 | 6 | 1 | **做了**。路由 + 10 条白名单 + Nacos + 2 个 Helm 模板 + values + compose 全在；只差 `helm lint` 实跑 |
| Task 11 | 前端 4 个 Vue 页 + API + 路由 | 7 | 6 | 1 | **做了**。4 页 + 24 条 API + 4 条路由全在；只差 `npm run build` 实跑 |
| Task 12 | health-check + e2e + VERIFICATION 报告 | 6 | 4 | 2 | **做了，但没跑**。脚本和报告都写了，health-check 与 e2e **一次都没执行过** |
| Task 14 | Memory 文件 + 最终提交 | 3 | 0 | 3 | **整组没做**。memory 文件、MEMORY.md 条目、最终提交三样全缺 |
| **合计** | | **85** | **66** | **19** | |

---

## 2. 没勾的 19 个步骤（全部列出 + 理由）

### 2.1 "跑一次看输出"类（16 个）——仓库里查不到运行结果，一律不勾

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 1 Step 7 | 验证 SQL 跑通（`docker exec ... mysql < V20261020...`） | （未完成：仓库里查不到运行结果；旁证是 `springboot3/deploy/mysql-initdb.d/17-opc-marketplace-schema.sql` 把同一份 DDL 放进 MySQL 容器首启目录，但没有执行记录） |
| Task 1 Step 8 | 验证模块编译（`mvn -pl opc-marketplace -am clean compile`） | （未完成：仓库里查不到运行结果；旁证是 gitignore 的 `target/classes` 有 68 个 `.class`、`target/opc-marketplace.jar` 存在，但不是仓库文件也没有构建日志） |
| Task 2 Step 5 | 验证编译 | （未完成：仓库里查不到运行结果） |
| Task 3 Step 3 | 验证编译 | （未完成：仓库里查不到运行结果） |
| Task 4 Step 5 | 跑 `AuthorControllerTest`（Expected: 3 tests pass） | （未完成：仓库里查不到运行结果；`target/` 下没有 surefire-reports，也没有 CI 记录） |
| Task 5 Step 6 | 跑 publication 测试（Expected: 18 tests pass） | （未完成：仓库里查不到运行结果；同上） |
| Task 6 Step 6 | 跑 install 测试（Expected: 8 tests pass） | （未完成：仓库里查不到运行结果；同上） |
| Task 7 Step 5 | 跑 review 测试（Expected: 8 tests pass） | （未完成：仓库里查不到运行结果；同上） |
| Task 8 Step 6 | 跑 category/tag 测试（Expected: 9 tests pass） | （未完成：仓库里查不到运行结果；同上） |
| Task 8.5 Step 5 | 跑全量单测（Expected: 68 tests pass） | （未完成：仓库里查不到运行结果；同上） |
| Task 9 Step 1 | 验证应用本地启动（"OPC MARKETPLACE 启动成功" + 9317 监听） | （未完成：仓库里查不到运行结果；`docs/verification/week-80/OPC-W80-VERIFICATION-opc-marketplace.md` §0.2 明确写「未部署」） |
| Task 9 Step 2 | smoke 测 3 个端点（`curl` category/tree、tag/list） | （未完成：仓库里查不到运行结果；同一份验证报告 §0.2 明确写「未部署」/「未跑 e2e」） |
| Task 10 Step 6 | `helm lint` / `helm template`（Expected: dev=18 / staging=28 / prod=29） | （未完成：仓库里查不到运行结果；plan 期望的三个数字已过期，同一份验证报告写的是 dev 31 / staging 48 / prod 50） |
| Task 11 Step 6 | `npm run build`（Expected: BUILD SUCCESS） | （未完成：仓库里查不到运行结果；旁证是 gitignore 的 `vue3-typescript/dist/static/js/marketplace-DCPNsj__.js` 存在） |
| Task 12 Step 2 | 跑 health-check（Expected: 43 PASS） | （未完成：仓库里查不到运行结果；验证报告 §4.2 明确写 `health-check.sh` 从未执行） |
| Task 12 Step 4 | 跑 e2e（Expected: 12 步 code=200） | （未完成：仓库里查不到运行结果；验证报告 §4.1 明确写 e2e 从未运行） |

> 补充说明：`docs/verification/week-80/OPC-W80-VERIFICATION-opc-marketplace.md` §1.1 / §6 里贴了 `mvn test`（148 tests / 0 failures，连跑 8 次）、`helm lint`、`vite build` 的控制台输出。但那是**自己写的 markdown 里的粘贴片段**，不是 CI 记录、也不是测试报告文件。按判据 3，仓库里查不到运行结果，因此这一类 step 一律不勾。
>
> 反过来说：**这 16 条不勾不代表代码不行**，只代表"跑没跑过"这件事在仓库里留不下证据。真正的运行时证据在验证报告 §4 里被诚实地列为"未验证"——这份 plan 的验证报告没有说谎。

### 2.2 文件真的没做（3 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 14 Step 1 | 写 memory 文件 `opc-marketplace-w80.md` | （未完成：memory 目录 `/root/.claude/projects/-www-Lumen-AI-OPC/memory/` 下没有 `opc-marketplace-w80.md`） |
| Task 14 Step 2 | 更新 `MEMORY.md` 加一行 | （未完成：`/root/.claude/projects/-www-Lumen-AI-OPC/memory/MEMORY.md` 里没有 marketplace 相关的行） |
| Task 14 Step 3 | 最终 commit + push | （未完成：`git log --all --grep=marketplace` 里没有 `docs(memory): opc-marketplace W80 P1 记忆` 这个提交） |

### 2.3 计数
- "跑一次看输出"类：**16 个**
- 文件真的没做：**3 个**（全部在 Task 14）
- 合计：**19 个**

---

## 3. 勾上了但和 plan 写法有出入的 10 个（行尾已标「（部分：…）」）

这些是最容易被误读成"完全照 plan 做了"的条目：

| 位置 | 做了什么 | 和 plan 的出入 |
|---|---|---|
| Task 1 Step 1 | pom.xml 存在，artifactId=opc-marketplace，`<phase>none</phase>` 在 | **没有加 `opc-agent-hub` 依赖**，也没有 `ruoyi-job`；pom 内写了注释说明 Agent 侧全走 Feign HTTP——这是有意的设计变更，不是漏做 |
| Task 4 Step 1 | `AuthorService` 接口齐全（`apply` / `getByCompanyId` / `getById` / `toVO` / `toPublicVO` / `list` / `stats`） | `apply()` 实际签名是 **3 参** `apply(req, companyId, operatorId)`；plan 代码块写的 2 参 `apply(req, operatorId)` 已过期——`authorCompanyId` 早改成从 `SecurityUtils.getCompanyId()` 取（commit `56815d4`） |
| Task 5 Step 4 | `PublicationControllerTest` 在，plan 列的 11 个用例里 10 个在 | `get_returnsVO` 改名成 `get_usesPublishedOnlyLookup`（语义更强）；该文件实际 **28 个 @Test** |
| Task 5 Step 5 | `PublicationServiceTest` 在，plan 列的 10 个用例里 9 个在 | `edit_success` 改名成 `edit_success_onOwnDraft`；该文件实际 **42 个 @Test** |
| Task 6 Step 1 | `AgentHubClient` + `AgentHubClientFallback` 都在 | 返回类型是 `R<Map<String,Object>>` 不是 `Map`、`@FeignClient` 用 `contextId` 不是 `name`、body 里没有 `from-source` 头——plan 的写法后来在 commit `e701ca7` 修过 |
| Task 8.5 Step 4 | `AuthorControllerTest` 扩充的用例都在 | plan 列的 8 个用例里 7 个在，`me_returnsNull_whenNotAuthor` 改名成 `me_returnsNull_whenNotAuthorYet` |
| Task 9 Step 3 | Dockerfile 存在，thin jar + `EXPOSE 9317` + `-cp opc-marketplace.jar:lib/*` 都在 | 基础镜像用 `eclipse-temurin:17-jre-jammy` 而非 plan 写的 `:21-jre-alpine`，COPY 路径按本仓库实际布局改了 |
| Task 10 Step 1 | 网关路由 `/opc/marketplace/**` → `http://aiopc-marketplace:9317` 在；plan 列的 10 条白名单路径全部在 | 实际白名单用 **`GET:` method 前缀**收紧（更安全），且 category 详情从 `**` 收成 `{id:[0-9]+}`；另多一条 `GET:/opc/marketplace/category/{id:[0-9]+}` |
| Task 11 Step 5 | 4 条路由都能 resolve（`router/index.spec.ts` 有断言） | **只有 `detail/:id` 在 `router/index.ts`**，另外 3 条走 sys_menu（`V20261022__opc_sys_menus.sql`）——commit `08305b8` 的提交信息自己写着「P1 follow-up — **Task 11 漏掉菜单配置**」 |
| Task 12 Step 1 | marketplace 确实接进 health-check（`check_marketplace()` 挂 step 11，另有 step 19/20 两段 marketplace 检查） | plan 说的 `HEALTH_ENDPOINTS+=` 数组在 `health-check.sh` 里**根本不存在**；脚本自己的表头写的是「opc-marketplace **10 项**健康检查」，不是 21 |

---

## 4. 整体结论：这份 plan 靠不靠谱？

**真做了的比例：66 / 85 = 78%。** 加上 16 条"跑一次看输出"类里大部分在实现时确实跑过（只是仓库留不下证据）的那部分，**实际完成度高于 78%**——但**其中至少 4 条是真的从未跑过**（应用启动、smoke、health-check、e2e）。这份 plan 在 9 份里属于"空 checkbox 骗人"程度**较低**的一类：**14 个 Task 组里 13 个都有实打实的代码落地**。

**逐 Task 看：**
- **整体没做的 Task：1 个**——Task 14（Memory 文件 + MEMORY.md + 最终提交），三样东西一件都没有。这是整份 plan 里唯一从头到尾没碰过的东西。
- **做了一半的 Task：2 个**——Task 9（Dockerfile 做了，"启动起来"和"smoke 一下"没做）、Task 12（脚本和报告都写了，但 health-check 和 e2e 一次都没跑）。
- **半成品 step 分布在 9 个 Task**（共 10 个 step，见 §3），共同特征是"文件在，但命名/签名/路径跟 plan 对不上"——**不是没做，是实现过程中做了更好的选择或改了设计**。

### 4.1 有没有"声称做了但其实没有"的条目？

**有，3 处点名：**

1. **Task 12 的 e2e 与 health-check —— 声称"12 步 e2e PASS"和"43 项健康检查 PASS"，实际一次都没跑过。** plan 验收标准表把这两行写成达成项，但同一份验证报告 §4.1/§4.2 亲口写着"e2e 从未运行"、"health-check.sh 从未执行"、§4.3"从未部署"。这是 plan 与实现之间的**真·说谎**（不是代码问题，是交付状态问题）。
2. **plan Goal 行写"28 endpoints"，实际 P1 交付 29 个，今天 34 个。** 更值得一提的是：**plan 自己内部就是矛盾的**——Goal 行写 28，File Structure 里逐个 controller 加总是 29，Task 14 的 memory 正文又写"## 29 个 endpoint"。仓库交付的是 29，与 File Structure 和 memory 正文一致；**28 是 Goal 行一家的笔误**。同仓库的验证报告 §1.3 已经把这事订正了，还专门写了一句「早期口径写作『29 endpoints across 5 controllers』是笔误，此处更正为 6 controllers / 29 endpoints」。
3. **Task 12 Step 1 的"21 marketplace health endpoints"。** `HEALTH_ENDPOINTS+=` 数组在 `health-check.sh` 里不存在，P1 交付时 marketplace 只有 **10 项**健康检查（脚本表头自己写的数）。今天 marketplace 相关的断言合计约 70 条（step 11 + step 19 + step 20），远超 21，但**这 21 这个数字从来不是任何一个中间态的真实数字**。

### 4.2 plan 里其他过期的数字

- **"63 全 PASS" / "68 tests pass"**（验收标准表 / Task 8.5 Step 5）：W80 P1 交付当时是 **148**，今天是 **172**。plan 的数字是**低报**，不是虚高。
- **"60+ 单测"**：作为下限成立。逐文件声称的 14/8/10/8/5/4/8/5/1（合计 63）对应的实际是 13/15/11/6/4/42/28/5/1（部分 P1 文件后来被拆细），量级完全不是问题。
- **"Helm dev=18 / staging=28 / prod=29"**：验证报告实测 dev 31 / staging 48 / prod 50 —— 仓库在 plan 之后又加了别的服务，plan 的期望值过期。
- **"12+ e2e 测试"**：`tmp_e2e/e2e_marketplace.py` 实际 12 步 + Step 4.5，达标。

### 4.3 plan 里描述得比代码更漂亮的地方（读 plan 会高估完成度）

1. **Task 12 的运行时验证整段是空的。** plan 的验收标准表把「健康检查 43 项 PASS」「e2e 12 步 PASS」写成两条交付指标，实际上**这两条从未兑现过**。验证报告 §4.5 还补了一刀：13 处租户 fail-closed 守卫**只由单测覆盖，未在真实登录态下跑过**。
2. **Task 9 Step 1/2 的"启动成功 + Nacos 连上 + 9317 监听"**：没发生过。plan 里写的 `java -cp ... OpcMarketplaceApplication` 那条命令连 Spring Boot 3 / Spring Cloud 2025 下必需的 `config.import` 都没覆盖（仓库的 `bootstrap.yml` 后来才补上）。
3. **Task 1 Step 1 的 pom 依赖**：`opc-agent-hub` 根本没加进 pom——plan 说加，实际用 Feign HTTP 打。这不是问题，但**照 plan 抄 pom 会抄错**。
4. **File Structure 里列的 `util/AuthorGuard.java`（"验 author_company_id"）在仓库里不存在**——整个模块连 `util/` 目录都没有。归属校验后来落在 `AuthorController.currentAuthorId()` → `requireCompanyId()` 这条链上（fail-closed 抛 400），功能在，但**不是 plan 描述的那个文件**。

### 4.4 一句话

**这份 plan 的"做"是真的**（12/13 个 Task 组有代码落地，6 表 / 6 controller / 29 端点 / 172 单测全部实打实存在，git 历史里每个 Task 都有对应的 `feat(marketplace)` 提交），**可以放心当历史文档读**；但它的 checkbox 空着、Goal 行的端点数写错、以及"运行时验证"整段从未兑现，**读 plan 会高估完成度**——尤其容易让人以为"市场跑通了、e2e 过了、健康检查全绿"。这正是这次审计要解决的问题：**代码是真的，运行时是空的，而 plan 把两者混在一起写。**

---

## 5. 复现命令

```bash
cd /www/Lumen-AI-OPC

# 端点数（7 个 controller / 34 个端点；P1 范围 6 个 / 29 个）
ls springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/controller/
grep -cE '^\s*@(Get|Post|Put|Delete|Patch)Mapping' \
  springboot3/ruoyi-modules/opc-marketplace/src/main/java/com/ruoyi/opc/marketplace/controller/*.java

# @Test 数（172；注意 @TestPropertySource 不算）
grep -rc '@Test' springboot3/ruoyi-modules/opc-marketplace/src/test/ | grep -v ':0'

# 6 张表
grep -E '^CREATE TABLE mp_' springboot3/sql/migrations/V20261020__opc_marketplace_schema.sql

# 提交（Task 4/5/6/7/8/8.5/9/10/11/12 各一条）
git log --oneline --since=2026-10-19 --until=2026-10-23 --grep='marketplace'

# Task 14 三样东西确实没有
ls /root/.claude/projects/-www-Lumen-AI-OPC/memory/ | grep -i marketplace || echo NONE
git log --oneline --all --grep='opc-marketplace W80 P1 记忆' || echo NONE
```
