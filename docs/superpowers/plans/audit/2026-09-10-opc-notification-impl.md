# Plan 审计报告：2026-09-10-opc-notification-impl.md

- **审计日期**：2026-10-29
- **被审 plan**：`docs/superpowers/plans/2026-09-10-opc-notification-impl.md`
- **plan 目标**：建 `opc-notification` 统一通知中心（邮件 + 短信 + 站内收件箱 + WebSocket 实时推送），端口 9310，作为 OPC 扩展路线图的基础服务（W49-W50，2 周）
- **审计方式**：只读仓库 + git 历史，逐条核实 119 个 checkbox。判据：① 文件在且内容对得上（类名/方法名/字段名/常量）② 代码里 grep 得到 ③ 测试文件与测试方法在 ④ 提交在 plan 时间窗（2026-09-10 ~ 2026-09-14）内
- **结果**：**勾了 94 / 没勾 25 / 共 119**（审计前是 9 勾 / 110 空）
- **原已勾的 9 条**：**9 条全部站得住，0 条退回**（其中 2 条措辞/数字不准，已在行尾注明）

---

## 0. 关于「跑一下看输出」这类步骤的判定口径

这份 plan 有 30 个步骤的交付物是**运行结果**而不是文件。判定口径如下，先说清楚，免得下面 13 个勾和 17 个不勾看着像自相矛盾：

| 情形 | 判定 | 本 plan 的例子 |
|---|---|---|
| 仓库里有**专门记录该命令实际输出**的逐 Task 验证日志（带真实控制台输出） | **勾** | `docs/verification/week-49/opc-notification-task13/14/16/17/18-*.md` 共 6 份 |
| 仓库里**一份运行输出都没有** | **不勾**，理由写「仓库里查不到运行结果」 | `mvn compile` / `mvn test` / 灌库 `SHOW TABLES`（全仓库 grep 不到 opc-notification 的 `Tests run:` 或 `BUILD SUCCESS` 记录；`target/` 是 gitignored，不算仓库证据） |

也就是说：**「单测/编译跑没跑」一律不勾；「部署级命令跑没跑」在有逐 Task 实测日志时勾**。13 个被勾的运行步骤在 §4 逐条列了证据文件，想复核的人可以直接照着查。

---

## 1. 逐 Task 汇总

| Task | 主题 | 步骤数 | 勾了 | 没勾 | 一句话结论 |
|---|---|---|---|---|---|
| Task 1 | 模块脚手架（pom + 启动类 + 注册） | 5 | 4 | 1 | **做了**。pom/启动类/模块注册全在，只差「mvn compile 通过」无据可查 |
| Task 2 | 4 表 SQL + 4 个模板种子 | 6 | 4 | 2 | **做了**。4 表 + 4 种子 + initdb 副本全在；灌库执行记录缺失 |
| Task 3 | 4 个 Domain 实体 | 6 | 5 | 1 | **做了**。4 实体字段逐个对得上（实现改走 vanilla MyBatis，没用 `@TableName`） |
| Task 4 | 4 Mapper + 4 Mapper XML | 6 | 5 | 1 | **做了**。`selectUnreadCount` / `selectInboxPage` / `selectByCodeAndVersion` / `selectLatestEnabled` 全在 |
| Task 5 | Email Provider 抽象 + Smtp 实现 | 6 | 4 | 2 | **做了**。`EmailProvider` / `EmailSendException` / `SmtpEmailProvider` + 测试文件都在 |
| Task 6 | SMS Provider 抽象 + Aliyun 实现 | 6 | 4 | 2 | **做了**。`SmsProvider` / `SmsSendException` / `AliyunSmsProvider` / `ProviderConfig`（NoOp 兜底）都在 |
| Task 7 | EmailService / SmsService + retry | 7 | 5 | 2 | **做了**。3 次重试 + 线性 backoff + 落 log 表都在；但 `SmsServiceTest` 只 2 个 `@Test`（plan 写 6） |
| Task 8 | InboxService | 6 | 4 | 2 | **做了**。push/list/markRead/unreadCount 在，还多做了 `markAllRead` |
| Task 9 | WebSocket（handler + registry + 鉴权） | 7 | 5 | 2 | **做了**。`WsSessionRegistry` / `NotificationWsHandler` / `WebSocketConfig` / `AuthHandshakeInterceptor` 全在（JWT 走 `?token=`） |
| Task 10 | 3 个 REST Controller + 3 个 DTO | 7 | 6 | 1 | **做了**。端点路径与 plan 全对得上；但 plan 点名的 `SmsControllerTest.java` 没建 |
| Task 11 | bootstrap/application/Nacos/MailConfig | 7 | 7 | 0 | **做了**。4 份配置 + `MailConfig` 全在，启动有实测日志 |
| Task 12 | Dockerfile + docker-compose 接线 | 4 | 4 | 0 | **做了**。thin-jar Dockerfile + compose 条目在，镜像构建有实测日志 |
| Task 13 | 推 Nacos + 起容器 + smoke | 5 | 5 | 0 | **做了**。task13 验证日志逐条对得上（6.673s 启动 / Nacos 注册 / 端点 200） |
| Task 14 | health-check.sh 加 6 项 | 3 | 3 | 0 | **做了**。脚本里 6.1~6.6 齐全，日志 28/28 PASS |
| Task 15 | RECOVERY.md + import-dev.sh | 2 | 2 | 0 | **做了**。§10 在；`import-dev.sh` 的 NAMES 也加了 `opc-notification-dev.yml` |
| Task 16 | Helm chart 接入 | 4 | 4 | 0 | **做了**。4 份 values + deployment/service 两个 template，helm lint 输出在日志里 |
| Task 17 | 前端 Inbox + WS composable | 7 | 6 | 1 | **做了一半**。API + composable + 页面 + 菜单路由都在；`NotificationItem.vue` 没做 |
| Task 18 | E2E 脚本 | 3 | 3 | 0 | **做了**。`notification_e2e.py` 11 项检查，日志 11/11 PASS |
| Task 19 | 验证报告 | 2 + 模板内 9 | 6 | 5 | **报告做了，AC 没验**。报告存在（改了名），里面 5 条 AC 指标一条都不成立 |
| Task 20 | 收尾验收 + push | 6 | 3 | 3 | **做了一半**。health-check / E2E / push 有据；单测运行结果无记录，MEMORY.md 没写，仓库里没有 `memory/` 目录 |
| Self-Review | spec 覆盖自查（5 条） | 5 | 5 | 0 | **站得住**。5 条声明逐条对得上代码 |
| **合计** | | **119** | **94** | **25** | |

---

## 2. 没勾的 25 个步骤（全部列出 + 理由）

### 2.1 「跑一次看输出」类（17 个）—— 仓库里查不到运行结果

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 1 Step 4 | 验证 Maven 编译（`mvn -pl ruoyi-modules/opc-notification -am compile`） | （未完成：仓库里查不到运行结果 —— 没有提交过 mvn compile 的 BUILD SUCCESS 输出） |
| Task 2 Step 3 | 验证 SQL 干净落库（`SHOW TABLES LIKE 'opc_notification%'`） | （未完成：仓库里查不到运行结果 —— 没有提交过 SHOW TABLES 的执行记录） |
| Task 2 Step 4 | 灌种子（4 模板） | （未完成：仓库里查不到运行结果 —— 没有提交过 seed 灌库 4 行的执行记录） |
| Task 3 Step 5 | 验证编译 | （未完成：仓库里查不到运行结果 —— 没有提交过 mvn compile 的输出） |
| Task 4 Step 5 | 验证编译 | （未完成：仓库里查不到运行结果 —— 没有提交过 mvn compile 的输出） |
| Task 5 Step 2 | 跑测试验证失败（TDD 红灯） | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 5 Step 5 | 跑测试验证通过 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 6 Step 2 | 跑测试验证失败 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 6 Step 5 | 跑测试验证通过 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 7 Step 2 | 跑测试验证失败 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 7 Step 5 | 跑测试验证通过 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 8 Step 2 | 跑测试验证失败 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 8 Step 5 | 跑测试验证通过 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 9 Step 2 | 跑测试验证失败 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 9 Step 6 | 跑测试验证通过 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 10 Step 6 | 跑 controller 测试 | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕） |
| Task 20 Step 2 | 跑全部单测（Expected: 32+ tests） | （未完成：仓库里查不到运行结果 —— 没有 mvn test 的输出留痕；而且实际只有 23 个 `@Test`，达不到 plan 写的 32+） |

> 补充说明：全仓库 grep 不到 opc-notification 的 `Tests run:` 或 `BUILD SUCCESS` 输出（`docs/verification/week-49/opc-notification-week49-verification.md` §七 只有一行「23 测试 / 100%」的汇总，没有控制台原文）。`springboot3/ruoyi-modules/opc-notification/target/` 里有编译产物，但 `target/` 是 gitignored，不构成仓库证据。

### 2.2 文件真的没做（1 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 17 Step 3 | 创建 `NotificationItem.vue` | （未完成：`NotificationItem.vue` 没做 —— `vue3-typescript/src/views/opc/notification/` 这个目录整个不存在，实际实现改成了单文件 `views/opc/inbox.vue` + 复用 `ResponsiveTable`） |

### 2.3 Task 19 报告模板里的 5 条 AC 指标（5 个）

这 5 条写在 Task 19 的「验证报告」模板里，原文自带 ✅ 断言。逐条核对后**没有一条站得住**：

| 位置 | 原断言 | 核对结果 |
|---|---|---|
| Task 19 §1 AC1 | 邮件发送成功率 ≥ 99% | **不成立**。重试 3 次确实实现了（`EmailServiceImpl` 循环 + `logMapper.insert`），但「成功率」要真实 SMTP 统计，仓库里没有 |
| Task 19 §1 AC2 | SMS 模板支持变量替换：✅ SmsServiceImpl 渲染 Freemarker | **不成立**。`SmsServiceImpl` 不查 `opc_notification_template`、也没有 Freemarker，只是把 `vars` 原样 `objectMapper.writeValueAsString` 后交给 Aliyun 的 `TemplateParam`（变量替换发生在阿里云那边） |
| Task 19 §1 AC3 | 站内信分页查询 < 200ms | **不成立**。`idx_inbox_user_created (user_id, created_at)` 索引建了，但没有任何实测延迟数据 |
| Task 19 §1 AC4 | WS 推送延迟 < 1s（单房间 100 并发） | **不成立**。W49 验收报告自己写着「后端集成测试（WebSocket 100 并发）—— 留待 Iter2 验证」，压测压根没做 |
| Task 19 §1 AC5 | 通知模板版本管理（灰度） | **不成立**。`version` / `enabled` 字段和 `selectLatestEnabled` 有了，但「灰度发布」没实现 —— W49 报告遗留项 #4 明写「模板系统仅支持 version 字段，未实现灰度 / 灰度发布功能」 |

### 2.4 Task 20 收尾（2 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 20 Step 5 | 更新 MEMORY.md | （未完成：MEMORY.md 里没有任何 W49 / opc-notification 条目） |
| Task 20 Step 6 | 提交 memory 更新并 push | （未完成：仓库里没有 `memory/` 目录，这条 `git add memory` 无从谈起） |

> Task 20 Step 5 的路径写的是 Windows 路径 `C:\Users\wma19\.claude\projects\D--work-ai-0401-lumen-opc\memory/MEMORY.md`，本机对应 `/root/.claude/projects/-www-Lumen-AI-OPC/memory/MEMORY.md` —— 那个文件里确实一条 W49 通知中心的记忆都没有。

### 2.5 计数

- 「跑一次看输出」类：**17 个**
- 文件真的没做：**1 个**（Task 17 Step 3 `NotificationItem.vue`）
- Task 19 报告里的 AC 指标：**5 个**
- Task 20 收尾：**2 个**
- 合计：**25 个**

---

## 3. 原本就勾上的 9 条 —— 复核结果

**9 条全部站得住，0 条退回。** 但其中 2 条的措辞/数字不准，已在 plan 行尾注明：

| 位置 | 原条目 | 复核 | 证据 |
|---|---|---|---|
| Task 19 §3 | `[x] docker compose up -d aiopc-notification 成功` | **站得住** | `docs/verification/week-49/opc-notification-task13-verify.md`：「Container start: PASS」「Started OpcNotificationApplication in 6.673 seconds」「Tomcat started on port 9310」 |
| Task 19 §3 | `[x] Nacos 注册中心看到 opc-notification` | **站得住** | 同上：「Registered with Nacos: opc-notification 172.x.x.x:9310」；health-check 6.2 也查这一项 |
| Task 19 §3 | `[x] 健康检查 6 端点全 R200` | **结论对，措辞不准** | `opc-notification-task14-health-check.md`：6 项全 PASS、总数 28/28。但 6 项里只有 3 项是 HTTP 200（inbox / unread-count / actuator health），另 3 项是「容器在跑 / Nacos 有注册 / 网关路由能解析」 |
| Task 19 §3 | `[x] /tmp_e2e/notification_e2e.py 5/5 PASS` | **结论对，数字错** | `opc-notification-task18-e2e.md`：「Total: 11  Pass: 11  Fail: 0」—— 实际 11 项，不是 5 项 |
| Self-Review 1 | `[x] 邮件发送 + retry (Task 7)` | **站得住** | `EmailServiceImpl`：3 次重试 + 线性 backoff + 失败落 `opc_notification_email_log` |
| Self-Review 2 | `[x] SMS 模板变量 (Task 6 + Task 7)` | **站得住** | `SmsProvider.send(phone, templateCode, Map vars)` + `SmsServiceImpl` 把 vars JSON 化写 `vars_json` |
| Self-Review 3 | `[x] 站内信分页 (Task 8 + Task 10)` | **站得住** | `InboxServiceImpl.listInbox` + `InboxController.list`（`page`/`pageSize`） |
| Self-Review 4 | `[x] WS 推送 (Task 9)` | **站得住** | `WsSessionRegistry.sendToUser` + `NotificationWsHandler` + `InboxServiceImpl.push` 里 best-effort 推送 |
| Self-Review 5 | `[x] 模板版本管理 (Task 4 mapper selectByCodeAndVersion + selectLatestEnabled)` | **站得住** | `NotificationTemplateMapper.java:26,29` + 对应 XML `<select>` 两段都在 |

---

## 4. 被勾上的 13 个「运行类」步骤 —— 证据索引

| 位置 | 步骤 | 证据文件 |
|---|---|---|
| Task 11 Step 6 | 验证 Spring Boot 启动 | `docs/verification/week-49/opc-notification-task13-verify.md` |
| Task 12 Step 3 | 构建镜像 | `docs/verification/week-49/opc-notification-task13-verify.md`（「Docker image build: PASS (aiopc-notification:latest)」） |
| Task 13 Step 1 | 推 Nacos 配置 | `docs/verification/week-49/opc-notification-task13-verify.md` |
| Task 13 Step 2 | 起容器 | `docs/verification/week-49/opc-notification-task13-verify.md` |
| Task 13 Step 3 | 看启动日志 | `docs/verification/week-49/opc-notification-task13-verify.md` |
| Task 13 Step 4 | smoke 端点 | `docs/verification/week-49/opc-notification-task13-verify.md` + `opc-notification-task18-e2e.md` |
| Task 14 Step 2 | 跑 health-check | `docs/verification/week-49/opc-notification-task14-health-check.md`（28/28 PASS） |
| Task 16 Step 3 | 验证 Helm chart | `docs/verification/week-49/opc-notification-task16-helm.md`（helm lint 0 failed × 3 env） |
| Task 17 Step 6 | 验证前端构建 | `docs/verification/week-49/opc-notification-task17-frontend.md`（`npm run build:prod` pass, 34.63s） |
| Task 18 Step 2 | 跑 E2E | `docs/verification/week-49/opc-notification-task18-e2e.md`（11/11 PASS） |
| Task 20 Step 1 | 跑全量 health-check | `docs/verification/week-49/opc-notification-task14-health-check.md` |
| Task 20 Step 3 | 跑 E2E | `docs/verification/week-49/opc-notification-task18-e2e.md` |
| Task 20 Step 4 | push 到 origin | `git branch -r --contains 708d71a` → `origin/main` |

---

## 5. 结论：这份 plan 靠不靠谱

### 5.1 真做了的比例

- **checkbox 层面：94 / 119 = 79%。**
- 但这个 79% 要拆开看：
  - **交付物层面（75 个"建文件/写代码"类步骤）：74 / 75 真做了**（唯一例外是 `NotificationItem.vue`）。后端 34 个主源文件（+7 个测试文件 = 41）、4 个 Mapper XML、4 张表、2 份 Nacos 配置、Dockerfile、compose 条目、health-check 6 项、RECOVERY §10、Helm 4 values + 2 templates、前端 API + composable + 页面 + 菜单路由、E2E 脚本 —— 逐个点名的文件都在仓库里，方法名/字段名/常量都对得上。
  - **运行结果层面（30 个"跑一下看输出"类步骤）：13 勾 17 不勾。** 13 个有逐 Task 实测日志，17 个（mvn / SQL）一份输出都没留下。
  - **声明类（14 个：Task 19 模板里的 9 条 + Self-Review 5 条）：9 勾 5 不勾。** 5 条不勾的是纯指标断言。
- **换句话说：这份 plan 的代码部分是真做了的，空 checkbox 严重低估了实际进度。** 这也是本轮审计最大的发现 —— 审计前 110 个空框里，藏着 94 件已经做完的事。

### 5.2 哪些 Task 整体没做

**没有一个 Task 组整体没做。** 20 个 Task 组的交付物全部落地（Task 15 的 §10、Task 16 的 Helm、Task 18 的 E2E 都实打实在库里）。

### 5.3 哪些做了一半

| Task | 做了一半的部分 |
|---|---|
| Task 17 | `NotificationItem.vue` 拆组件没做，改成单文件 `views/opc/inbox.vue` + 复用 `ResponsiveTable`（功能等价，结构不同） |
| Task 20 | 收尾只做了 3/6：health-check / E2E / push 有据，单测运行结果没留痕，MEMORY.md 没写，仓库里也没有 `memory/` 目录 |
| Task 19 | 报告写了，报告里的 5 条 AC 指标一条都没验 |

### 5.4 这个 plan 里「声称做了但其实没有」的条目

**A. 会被 checkbox 抓到、已退回未勾的（5 条）** —— 全在 Task 19 验证报告模板的 AC 清单里：

1. `SMS 模板支持变量替换: ✅ SmsServiceImpl 渲染 Freemarker` —— **`SmsServiceImpl` 没有 Freemarker，也不查 `opc_notification_template`**。它只是把 `vars` 原样 JSON 化丢给阿里云的 `TemplateParam`，变量替换发生在阿里云那边。
2. `通知模板版本管理 (灰度): ✅ opc_notification_template has version + enabled` —— 字段有，**灰度发布功能没有**（W49 报告遗留项 #4 自己承认）。
3. `邮件发送成功率 ≥ 99%` —— 从来没有真实 SMTP，"成功率"这个数字没有出处。
4. `站内信分页查询 < 200ms` —— 只有索引，没有任何实测。
5. `WS 推送延迟 < 1s (单房间 100 并发)` —— 压测没做（W49 报告写明"留待 Iter2 验证"）。

**B. 藏在正文里、不是 checkbox、同样说过头的（4 处）**：

6. Task 19 报告正文「被以下 5 个下游服务依赖：opc-crm / opc-hr / opc-community / opc-voice-agent / opc-ecommerce」—— **今天只有 opc-hr / opc-erp / opc-content 三个服务真有 `NotificationGateway` Feign**；`opc-crm` 明确把 notification 的 Feign 下游**删掉了**（`opc-crm/src/main/resources/application.yml:45-46` 写着「notification 的 /opc/notification/inbox/push 在对端不存在」），`opc-community` 没有任何引用，而 `opc-voice-agent` / `opc-ecommerce` **这两个服务在仓库里根本不存在**。
7. Task 19 报告正文「新增 28 个文件（Java 18 + …）」+「总 39 项,全部通过」—— 实际后端 **41 个** Java 文件（`src/main` 34 个 + 测试 7 个）、单测 **23 个** `@Test`（不是 28）、E2E **11** 项（不是 5）。
8. Task 7 Step 6「Include test `SmsServiceTest.java` with 6 tests」—— 实际 2 个 `@Test`。
9. 文件结构块里的 `NotificationTemplateService.java + Impl`（CRUD + LLM generate）和 `dto/NotificationTemplateRequest.java` —— **两个文件仓库里都没有，而且 plan 的 20 个 Task 里没有任何一步覆盖它们**（plan 自己漏了这两块；同理 Architecture 段说的 "Optional LLM template generator via opc-common HttpLlmClient" 也没实现）。

**C. 与 plan 描述不符但功能等价、已勾并注明（不算"没做"）**：

- 模块注册写在 `springboot3/ruoyi-modules/pom.xml` 而不是 plan 说的 `springboot3/pom.xml`（RuoYi 布局如此，plan 写错了）
- initdb 副本叫 `08-opc-notification-schema.sql` 不是 `06-`（commit bf10f05 主动改名）
- WS composable 叫 `src/composables/useNotificationSocket.ts`，不在 `views/opc/notification/` 下
- 验证报告叫 `opc-notification-week49-verification.md` 不是 `OPC-W49-VERIFICATION-opc-notification.md`
- 页面在 `src/views/opc/inbox.vue` 不是 `src/views/opc/notification/Inbox.vue`；路由改由后端菜单 `V20261022__opc_sys_menus.sql:98` 注册
- `ProviderConfig` 的注释写 "3 email providers + 3 sms providers"，实际只有 1 个 sms bean（email 走 `MailConfig` + Spring Boot 自带的 `JavaMailSender`）
- Domain 实体没用 MyBatis-Plus（plan 的 pom 里带了 `mybatis-plus-boot-starter`，实现改成了 vanilla MyBatis + `@MapperScan`）
- plan 写 Spring Boot 3.2，仓库实际是 3.5.x

### 5.5 一句话

**这份 plan 的代码是真做了的（交付物层面 74/75），但它自己的验证报告和收尾清单在吹牛（5 条 AC 指标 + 4 处正文声明站不住）；空 checkbox 不是"没做"，而是"做完没人回填"—— 真正该补的是三样东西：`mvn test` 的运行结果留痕、AC 指标的真实压测数据、以及 MEMORY.md 的那条记录。**
