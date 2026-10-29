# Plan 审计报告：2026-09-12-opc-erp-impl.md

- **审计日期**：2026-10-29
- **被审 plan**：`docs/superpowers/plans/2026-09-12-opc-erp-impl.md`
- **plan 目标**：建 `opc-erp` 进销存服务（端口 9311）——10 张表的采购/库存/销售/退货后端，带 FIFO 批次跟踪、多供应商、Quartz 日/月快照、8 个前端页面
- **审计方式**：只读仓库 + git 历史，逐条核实 72 个 checkbox。判据：① 文件在且内容对得上 ② 代码里 grep 得到 ③ 测试文件与测试方法在 ④ 提交在 plan 时间窗（2026-09-12 ~ 2026-09-14）内
- **结果**：**勾了 60 / 没勾 12 / 共 72**

---

## 1. 逐 Task 汇总

| Task | 主题 | 步骤数 | 勾了 | 没勾 | 一句话结论 |
|---|---|---|---|---|---|
| Task 1 | 模块骨架 + Application + 10 表 SQL | 8 | 7 | 1 | **做了**。pom / yml / logback / Application / 11 表 SQL 全部落地（比 plan 多一张日报快照表），只差"跑一次 mvn compile 看输出" |
| Task 2 | 10 Domain + 10 Mapper + 7 Enum + 9 DTO | 6 | 5 | 1 | **做了**。7 个枚举逐个带 `code`/`desc`/`of(String)`，DTO 做到 11 个，乐观锁改手写 `version` |
| Task 3 | Product Service + 7 endpoints + 15 单测 | 7 | 6 | 1 | **做了一半**。CRUD / 笛卡尔积 SKU / 15 个单测全对得上；`autoCategory()` 是关键词启发式占位，**不是** plan 写的 LLM 调用 |
| Task 4 | Purchase Service + 6 endpoints + 12 单测 | 5 | 4 | 1 | **做了**。`confirm()` 建批次 + 加库存 + 写流水 + 状态机 + `PO-yyyyMMdd-NNNN` 编号全对，13 个单测 |
| Task 5 | Sale Service + FIFO + 6 endpoints + 19 单测 | 6 | 5 | 1 | **做了**。FIFO 扣减带 `SELECT ... FOR UPDATE` 行锁，sale_item 按批次拆行并落 `batch_id` |
| Task 6 | Return + Supplier + 9 endpoints + 18 单测 | 6 | 5 | 1 | **做了**。销退回加批次库存 / 采退扣批次库存双路都实现，18 个单测 |
| Task 7 | Inventory + Report + 2 Quartz Jobs + 20 单测 | 7 | 6 | 1 | **做了一半**。Service / Controller / 20 个单测都对，**但两个 Job 没人调度**，低库存告警也不发通知 |
| Task 8 | Feign NotificationGateway + FallbackFactory + 2 单测 | 4 | 3 | 1 | **做了一半**。接口 + 降级工厂都在；但 `sendInbox` 打的路径在 opc-notification 里**根本不存在** |
| Task 9 | 前端 API 模块 + 类型定义 | 3 | 2 | 1 | **做了**。36 个函数 + 13 个 interface，比 plan 说的 30 还多（另有一份 plan 没提的 `erp.spec.ts`） |
| Task 10 | 8 Vue 页 + Router | 3 | 3 | 0 | **做了**。8 个页面的内容逐条对得上（AI 自动分类按钮 / 批次号输入 / FIFO 提示 / 销退采退切换 / 日报月报入口） |
| Task 11 | Dockerfile + Nacos + compose + Helm + Gateway 路由 + 白名单 | 8 | 7 | 1 | **做了**。Dockerfile 与 opc-hr 逐行同构（只换 jar 名 / 端口 / main class），只差"跑一次 helm lint / compose config" |
| Task 12 | health-check + E2E + VERIFICATION 报告 | 4 | 4 | 0 | **做了**。`check_erp()` 9 项、E2E 脚本、VERIFICATION 报告都在；但 E2E **没覆盖**销售 / 退货链路 |
| Self-Review | plan 末尾自查清单 | 5 | 3 | 2 | **2 条被退回**。`Spec coverage` 和 `No placeholders` 撑不住证据 |
| **合计** | | **72** | **60** | **12** | |

---

## 2. 没勾的 12 个步骤（全部列出 + 理由）

### 2.1 "跑一次看输出"类（10 个）——仓库里查不到运行结果，一律不勾

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 1 Step 7 | 验证编译（`mvn -pl ruoyi-modules/opc-erp -am compile`） | （未完成：仓库里查不到运行结果） |
| Task 2 Step 5 | 验证编译 | （未完成：仓库里查不到运行结果） |
| Task 3 Step 6 | 跑单测（Expected: 15/15 Tests passed） | （未完成：仓库里查不到运行结果） |
| Task 4 Step 5 | 跑单测 + Commit | （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit `ebc2014` 完成） |
| Task 5 Step 6 | 跑单测 + Commit | （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit `70271c1` 完成） |
| Task 6 Step 6 | 跑单测 + Commit | （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit `0a11bfd` 完成） |
| Task 7 Step 7 | 跑单测 + Commit | （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit `49ff87f` 完成） |
| Task 8 Step 4 | 跑单测 + Commit | （未完成：仓库里查不到测试运行结果；该 step 的 Commit 部分已由 commit `4fd65bd` 完成） |
| Task 9 Step 2 | 验证 TypeScript 类型（`npx vue-tsc --noEmit`） | （未完成：仓库里查不到运行结果） |
| Task 11 Step 7 | 验证（`docker compose config -q` + `helm lint .`） | （未完成：仓库里查不到运行结果） |

> 补充说明：`docs/verification/week-72/OPC-W72-VERIFICATION-opc-erp.md` §3.1~§3.4 里贴了 `mvn test` / `helm lint` / `docker compose config` 的控制台输出。但那是**自己写的 markdown 里的粘贴片段**，不是 CI 记录、也不是测试报告文件。按判据 3，仓库里查不到运行结果，因此这一类 step 一律不勾。

### 2.2 原本就勾上、这次被退回的 2 个

| 位置 | 步骤 | 理由 |
|---|---|---|
| Self-Review 第 1 条 | `**Spec coverage**: All 13 acceptance criteria from spec §1 → mapped to Tasks 3-7` | （未完成：spec §1 只有 6 条 AC（AC-1~AC-6），13 条验收清单在 spec §13 而不是 §1；且 §13 那 13 条里「1 prompt + EvalRunnerTest」「2 Quartz 定时任务」「Nacos 2 yml」在仓库里都没实现——全仓 grep 不到 `erp_auto_category_v1.0`、opc-erp 下没有 EvalRunnerTest、OpcErpApplication 里没有 `@PostConstruct` 注册 cron、opc-erp-prod.yml 不存在；spec AC-5 要求的「低库存预警经 opc-notification Feign 发通知」也没接上） |
| Self-Review 第 2 条 | `**No placeholders**: No "TBD" / "implement later" / "fill in details". All code blocks are complete.` | （未完成：plan 自己的代码块里有占位省略——`@Test ... (5 more)`（974/983 行）、`// ... 29 more functions` 与 `export interface OpcErpPurchase { ... }`（1063~1067 行）、`-- (重复上述模式 create 9 张表)`（266 行）、`mapper/{...}Mapper.java`（299 行）都不是「完整代码块」） |

### 2.3 计数
- "跑一次看输出"类：**10 个**（2 个纯编译 + 6 个 mvn test + 1 个 vue-tsc + 1 个 compose/helm lint；其中 5 个是「Verify + Commit」混合 step）
- 原本勾上但撑不住证据被退回：**2 个**
- 合计：**12 个**

---

## 3. 勾上了但只做了一半的 10 个（行尾已标「（部分：…）」）

这些是最容易被误读成"全做了"的条目，逐条列出来：

| 位置 | 做了什么 | 没做什么 |
|---|---|---|
| Task 1 Step 1 | pom.xml 在，artifactId `opc-erp`、`<phase>none</phase>` 薄包、`opc-common` 依赖都对 | **没加** plan 说的 `opc-notification` 依赖——pom 注释写明加了会让 `opc-notification-3.6.8.jar` 进 `lib/` 触发 `EmailController / MailConfig` 启动失败（属有意为之，但 plan 没记录这个决定）；pom 里也没有 9311 端口注释 |
| Task 1 Step 2 | application.yml 在，mybatis / type-aliases / map-underscore-to-camel-case 都在 | plan 写的 `server.port: 9311` **不在 application.yml**（挪到了 bootstrap.yml）；plan 写的 `jackson.date-format / time-zone` 块没有（改由 domain 上的 `@JsonFormat` 逐字段承担）；多出 datasource / redis / jasypt / `opc.erp` 段 |
| Task 1 Step 6 | `V20260912__opc_erp_schema.sql` 在**准确路径**上，11 张表、`company_id BIGINT NOT NULL` 强约束、`utf8mb4_unicode_ci`、5 条 seed 供应商全对 | 用的是 MySQL 8 原生 `CREATE TABLE IF NOT EXISTS`，不是 plan 写的 stored procedure（文件头注释解释了原因：MySQL 8 原生支持，stored procedure 只有 ALTER 时才需要） |
| Task 2 Step 1 | Lombok 4 件套 / `@JsonProperty` / LocalDateTime+LocalDate / GMT+8 / `specAttrs` 是 String / Batch 的 4 个字段 / InventoryLog 的 `change·type·ref_type·ref_id` 全对 | 乐观锁是**手写** `updateByIdWithVersion`（`version = version + 1` + `WHERE version = ?`），没用 plan 写的 `@Version` 注解；`version` 字段本身在（Long） |
| Task 3 Step 1 | `create()` 的 spec_attrs JSON → 笛卡尔积 → 批量插 SKU、`delete()` 有库存不许删、6 个接口方法签名都对 | `autoCategory()` **不是** plan 写的「Feign 调 opc-ai-core HttpLlmClient + prompt `erp_auto_category_v1.0`」，实际是关键词启发式（源码注释 `TODO Task 8`）；**全仓 grep 不到 `erp_auto_category_v1.0`** |
| Task 7 Step 4 | 2 个 Job 类都在，`OpcErpDailySnapshotJob` 的 opening/in/out/closing 倒推算法（`closing - in + out`）和 `OpcErpLowStockAlertJob` 的扫描都在 | **没实现 `org.quartz.Job`**、**没有 plan 写的 `OpcErpApplication.@PostConstruct registerJobs`**（整个 opc-erp 模块 grep 不到 `@PostConstruct`）、**没有 cron 表达式**、**`sys_job` 种子表里没有 opc-erp 的行**；低库存告警**没调 `notificationGateway`**（源码里是注释掉的 TODO），24h Redis 去重也没有 |
| Task 8 Step 1 | `@FeignClient(contextId="opcErpNotification", name="opc-notification", fallbackFactory=...)` 和两个方法都在，`sendEmail` 的 `/opc/notification/email/send` 与 opc-notification 的 `EmailController` 对得上 | `sendInbox` 打的 `/opc/notification/inbox/send` **在 opc-notification 里不存在**——`InboxController` 只有 `GET /inbox`、`POST /read/{id}`、`POST /read-all`、`GET /unread-count`。plan 明写「用真实路径，别用想象出来的」，这条恰恰是想象出来的 |
| Task 10 Step 2 | 8 个页面都可达——5 个主路由走 sys_menu 后端动态路由（`V20261022__opc_sys_menus.sql` 的 menu_id 2017~2022），3 个隐藏路由写在 `router/index.ts` | plan 写的 Element Plus 图标名 `Goods`/`ShoppingCart`/`Sell`/`Refresh`/`Box` **没落地**，sys_menu 用的是 RuoYi 图标名 `product`/`zip`/`list`/`tree-table`/`tree` |
| Task 11 Step 6 | 路由 `id: opc-erp` → `http://aiopc-erp:9311` + `Path=/opc/erp/**` 都在 | plan 写的白名单 `/opc/erp/product-sku/list` **路径写错了**——实际控制器路径是 `/opc/erp/product/product-sku/list`；实际放行的是 `GET:/opc/erp/product/list`、`GET:/opc/erp/product/**`、`GET:/opc/erp/inventory/low-stock`，且 2026-10-24 被 `baac599` 收紧成**只放行 GET**；plan 写的 `filters: StripPrefix=0` 没有（无 filter 与 StripPrefix=0 等价，仓库约定不 StripPrefix） |
| Task 12 Step 2 | `tmp_e2e/e2e_erp.py` 在，确实是 10 步脚本 | 第 6~10 步是「列销售单 / 查低库存 / 查日报 / 查月报 / 列供应商」——plan 要求的「**建销售单 → FIFO 确认 → 建销退单 → 确认销退**」整条链路**没跑**，也就是说 FIFO 出库和销退入库在 E2E 里一次都没被端到端验证过 |

---

## 4. 整体结论：这份 plan 靠不靠谱？

**真做了的比例：60 / 72 = 83%。** 这是 9 份 plan 里完成度最高的一份——**12 个 Task 全部有产物落地**，没有一个是空中楼阁。代码不是 plan 凭空列的：`git log` 里从 `e81a123`(Task 1) 到 `7eb5bae`(Task 12) 一路排下来，2026-09-12 一天之内 12 个 Task 全部提交。

**逐 Task 看：**
- **整体没做的 Task：0 个。**
- **做了一半的 Task：3 个**（Task 3 Product、Task 7 Inventory/Report、Task 8 Feign）——共同特征是「Service / 端点 / 单测都在，但**跨进程的外部依赖没接**」：`autoCategory()` 没接 LLM、`LowStockAlertJob` 没接 `notificationGateway`、两个 Job 没人调度。
- **半成品 step 分布在 7 个 Task**（Task 1 / 2 / 3 / 7 / 8 / 10 / 11 / 12，共 10 个 step），文件在但行为 / 命名 / 路径跟 plan 对不上。
- **从没碰过的东西**：spec §13 验收清单里的「1 个 LLM prompt + EvalRunnerTest」——opc-erp 模块里既没有 prompt（`erp_auto_category_v1.0` 全仓 grep 不到），也没有 EvalRunnerTest（全仓只有 opc-ai-core 有一个）。

**有没有"声称做了但其实没有"的条目？——有，而且这几处比代码本身更值得点名：**

1. **`autoCategory()` 是假实现，且单测名在撒谎。** plan 写「Feign 调 opc-ai-core HttpLlmClient，prompt `erp_auto_category_v1.0`」。实际实现是 `if (productName.contains("手机"))` 这种关键词匹配，源码里自己写着 `TODO Task 8`。更过分的是那个单测叫 **`autoCategory_llmSuccess_returnsCategory`**——但测试体里 `productService.autoCategory("小米手机", ...)` 调的是同一个启发式方法，**一个 LLM client 都没 mock**。测试名宣称了它没有验证的东西。
2. **两个 Quartz Job 永远不会自己跑。** plan 白纸黑字写「Register jobs in `OpcErpApplication.java` via `@PostConstruct`」，仓库里 grep 不到 `@PostConstruct`；`sys_job` 种子表里也没有 opc-erp 的 invokeTarget 行。日报快照和低库存告警目前只有两条路能触发：`POST /opc/erp/inventory/internal/snapshot` 和 `/internal/low-stock-alert` 这两个内部端点（靠人调）。spec AC-6 要求的「每日 23:55 快照 + 每月 1 号 00:05 聚合」**没有任何调度器在跑**。
3. **低库存告警不发通知。** spec AC-5 要求「通过 opc-notification Feign 发送预警通知」，plan Task 7 Step 4 也写 `notificationGateway.send(...) (24h dedup via Redis)`。实际 `OpcErpLowStockAlertJob` 里那行是注释掉的 `// @Autowired private OpcErpNotificationGateway notificationGateway;`，方法体里只有 `// TODO Task 8` 和一行 `log.info`。**`OpcErpNotificationGateway` 这个接口在整个 opc-erp 模块里除了自身定义和 FallbackFactory，没有第二处引用。**
4. **就算把通知接上也是 404。** `sendInbox` 打的 `/opc/notification/inbox/send` 在 opc-notification 里不存在（`InboxController` 只有 read / read-all / unread-count）。plan Step 1 自己写着「Use real notification controller paths (not aspirational)」，实现照抄了 plan 里那个想象出来的路径。
5. **VERIFICATION 报告 §5 的验收清单有一项是虚的。** `docs/verification/week-72/OPC-W72-VERIFICATION-opc-erp.md` §5 写着 `- [x] 低库存告警 Cron + 每日库存快照 Cron`——**Cron 不存在**。同一份报告 §3.1 贴的 `Tests run: 88, Failures: 0` 也只是 markdown 里的粘贴片段，仓库里没有 surefire 报告或 CI 记录。
6. **plan 末尾 Self-Review 的 "Spec coverage" 把节号写错了。** 「All 13 acceptance criteria from spec §1」——spec §1 只有 **6** 条 AC（AC-1~AC-6），13 条在 **§13**。而且 §13 那 13 条里的「1 prompt + EvalRunnerTest」「2 Quartz 定时任务」「Nacos 2 yml」（只有 dev 没有 prod）三条都没实现。

**plan 自己写的数字和代码对不上**（这些是 plan 里的口头承诺，不是 checkbox，没在上面计入）：

| plan 说的 | 实际 |
|---|---|
| 10 张表 | **11** 张（多一张 `opc_erp_daily_snapshot`，Task 7 Step 1 追加的） |
| 30 个 REST endpoint | **36** 个（Product 7 + Purchase 6 + Sale 6 + Return 5 + Supplier 5 + Inventory 5 + Report 2） |
| 8 个测试类 / 80+ 单测 | **9** 个测试类 / **88** 个 `@Test`（这个 plan 没说错，是它少算了） |
| Task 2 标题「5 Enum」 | **7** 个枚举（plan 自己的 Step 3 也列了 7 个，标题和正文自相矛盾） |
| Task 2 标题「9 DTOs」 | **11** 个 DTO（多 `DeductedBatch`、`OpcErpReturnItemDto`） |
| Task 9「30 functions」 | **36** 个函数 + 13 个 interface |
| Task 7「4 endpoints」 | **7** 个（Inventory 5 + Report 2；plan 自己的 Step 5 就列了 5 个，标题写 4 —— plan 内部就不一致） |
| Task 6「9 endpoints」 | **10** 个（Supplier 有 5 个不是 4 个） |
| File Structure 里 `OpcErpFifoBatchServiceTest.java` | 实际叫 `OpcErpFifoBatchServiceImplTest.java` |
| VERIFICATION 报告写的 `deductBatches` | 实际方法名是 `deductFifo`（plan 里是对的，是报告抄错了） |

**做得比 plan 更多的（plan 没写、但真做了）**：`springboot3/deploy/mysql-initdb.d/15-opc-erp-schema.sql` + `96-opc-erp-seed.sql`（容器首次启动自动建表灌 seed）、`springboot3/deploy/scripts/ensure-opc-erp-schema.sh`（幂等建表脚本）、`vue3-typescript/src/api/opc/erp.spec.ts`（324 行前端单测）、`opc_erp_daily_snapshot` 表、Inventory 的 2 个 `/internal/*` 触发端点。

**一句话**：这份 plan 的"做"是真的——12 个 Task 全有代码落地、83% 的 checkbox 站得住、单测 88 个比它自己承诺的还多，可以放心当历史文档读；但它对**跨进程的那几根线**（LLM 分类、Quartz 调度、通知推送）描述得比代码里实际实现的完整得多，而且它末尾的 Self-Review 有两条（Spec coverage / No placeholders）自己给自己打了假的勾——**读这份 plan 会高估"自动化"和"已验收"的程度**：日报快照和低库存告警这两件"定时任务"，至今没有任何调度器在跑。
