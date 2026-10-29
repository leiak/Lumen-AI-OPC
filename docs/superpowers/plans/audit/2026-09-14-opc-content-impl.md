# Plan 审计报告：2026-09-14-opc-content-impl.md

- **审计日期**：2026-10-29
- **被审 plan**：`docs/superpowers/plans/2026-09-14-opc-content-impl.md`
- **plan 目标**：交付 `opc-content` 微服务（端口 9325），含 4 个 LLM scene 接入 opc-ai-core + 抖音 sandbox OAuth/上传/发布，4 张表 + 22 REST 端点 + 6 Vue 页面 + Helm + docker-compose
- **审计方式**：只读仓库 + git 历史，逐条核实 78 个 checkbox。判据：① 文件在且内容对得上 ② 代码里 grep 得到（端点 / 表 / 字段 / 路由）③ 测试文件与测试方法在 ④ 提交在 plan 时间窗（2026-09-14 前后）内
- **结果**：**勾了 61 / 没勾 17 / 共 78**
- **审计报告**：本文件；plan 本身已就地更新（checkbox 状态 + 行尾理由 + 开头审计说明段），正文一字未改

---

## 1. 逐 Task 汇总

| Task | 主题 | 步骤数 | 勾了 | 没勾 | 一句话结论 |
|---|---|---|---|---|---|
| Task 1 | 项目骨架 + pom + 启动类 + bootstrap | 6 | 5 | 1 | **做了**。pom / Application / application.yml / bootstrap.yml / 父 pom 注册全落地，只差"跑一次 mvn compile 看输出" |
| Task 2 | Domain 4 实体 + Enum 4 枚举 | 4 | 3 | 1 | **做了**。4 实体字段齐、4 枚举齐，`ContentScriptStatus.canTransitionTo()` 在 |
| Task 3 | DTO 7 + Mapper 4 + XML 4 | 6 | 5 | 1 | **做了**。7 DTO / 4 mapper 接口 / 4 mapper XML，statement id 与 plan 写的对得上 |
| Task 4 | Service 4 接口 + impl | 6 | 5 | 1 | **做了，但 publish() 少一步**。4 个 service impl 都在；`publish()` 的第 5 步「发通知」没做，只留了一行 TODO |
| Task 5 | Controller 3 个 + 端点 | 6 | 5 | 1 | **做了，但端点数是 20 不是 22**。Script 11 + Platform 5 + Publish 4 = 20；plan 通篇写的 22 虚高 2 个 |
| Task 6 | ContentLlmPrompts + ContentLlmClient | 4 | 3 | 1 | **做了**。4 个 scene 常量 + 4 个 system prompt + 4 个方法，Feign → `POST /opc/llm/chat` 是真的 |
| Task 7 | PlatformClient + Douyin + Mock | 6 | 5 | 1 | **代码做了，真实凭据没有**。DouyinClient 是真的 HTTP 实现，但凭据是占位符、每条异常路径都降级 mock |
| Task 8 | Feign 3 网关 + FallbackFactory 3 | 4 | 3 | 1 | **做了**。3 interface + 3 factory，fallback 一律 `R.fail(...)` 不静默吞错 |
| Task 9 | Unit tests | 5 | 3 | 2 | **大半做了**。4 个 service test + LLM test + Token test 都在（plan 时代正好 60 个 @Test）；**DouyinClientTest / MockPlatformClientTest 两个文件从来没写** |
| Task 10 | 前端 types + API 模块 | 3 | 2 | 1 | **做了，20 个函数不是 22**。`content.ts` 文件头自己写的注释就是「11 + 5 + 4 = 20 endpoints」 |
| Task 11 | Vue 6 页 + 路由 | 9 | 6 | 3 | **页面做了，路由没做**。6 个 .vue 都在；**plan 写的 6 条 router 子路由从未加进 `router/index.ts`**，`generate.vue` 也没有 |
| Task 12 | Nacos 配置 + ENC secret | 4 | 3 | 1 | **配置做了，secret 没加密**。`opc-content-dev.yml` 在、也真的推进了 Nacos；**client-secret 是明文占位符，不是 `ENC(...)`** |
| Task 13 | Dockerfile + compose + initdb | 4 | 4 | 0 | **做了**。thin jar Dockerfile + `aiopc-content` 服务 + 4 张表 DDL + seed 数据 |
| Task 14 | Helm chart | 4 | 3 | 1 | **做了**。deployment-content.yaml + service-content.yaml + values.yaml `services.content` |
| Task 15 | health-check | 4 | 3 | 1 | **做了，但里面有一条恒失败的检查**（10.6 查的 `/opc/content/script/recent` 从来没有 handler） |
| Task 16 | VERIFICATION 报告 + e2e | 3 | 3 | 0 | **做了**。`e2e_content.py` 328 行 10 步 + week-74 VERIFICATION 报告 |
| **合计** | | **78** | **61** | **17** | |

---

## 2. 没勾的 17 个步骤（全部列出 + 理由）

### 2.1 "跑一次看输出"类（13 个）——仓库里查不到运行结果，一律不勾

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 1 Step 5 | 编译验证（`mvn compile -DskipTests -o`） | （未完成：仓库里查不到编译运行结果） |
| Task 2 Step 3 | 编译 | （未完成：仓库里查不到编译运行结果） |
| Task 3 Step 5 | 编译 | （未完成：仓库里查不到编译运行结果） |
| Task 4 Step 5 | 编译 | （未完成：仓库里查不到编译运行结果） |
| Task 5 Step 5 | 编译 + 启动器 smoke test | （未完成：仓库里查不到编译运行结果，也没有启动器 smoke 的运行记录） |
| Task 6 Step 3 | 编译 | （未完成：仓库里查不到编译运行结果） |
| Task 7 Step 5 | 编译 | （未完成：仓库里查不到编译运行结果） |
| Task 8 Step 3 | 编译 | （未完成：仓库里查不到编译运行结果） |
| Task 9 Step 4 | 运行所有测试（Expected: `Tests run: 60, Failures: 0`） | （未完成：仓库里查不到测试运行结果） |
| Task 10 Step 2 | TypeScript 编译验证（`npx tsc --noEmit`） | （未完成：仓库里查不到 TypeScript 编译运行结果） |
| Task 11 Step 8 | Vite build smoke（Expected: 6 个 chunk 输出） | （未完成：仓库里查不到构建运行结果；工作区里只有 gitignore 的 `dist/` 产物可作旁证，没有日志） |
| Task 14 Step 3 | helm lint + template verify（Expected: 18/28/29） | （未完成：仓库里查不到 `helm lint` / `helm template` 的运行结果） |
| Task 15 Step 3 | 本地验证（`bash health-check.sh`，Expected: opc-content PASS） | （未完成：仓库里查不到 health-check 的运行结果；VERIFICATION 报告自称 10/10 PASS，但那是自己写的 markdown，不是 CI 记录） |

> 补充说明：`docs/verification/week-74/OPC-W74-VERIFICATION-opc-content.md` 与 `docs/verification/week-75/OPC-W75-VERIFICATION-opc-content-e2e.md` 里贴了"10/10 health PASS""e2e 10/10 PASS"的说法。但那是**实现者自己写的 markdown**，不是 CI 记录、也不是测试报告文件。按判据 3，这一类 step 一律不勾。

### 2.2 文件/功能真的没做（4 个）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 9 Step 3 | 写 `DouyinClientTest` + `MockPlatformClientTest` | （未完成：`DouyinClientTest.java` 和 `MockPlatformClientTest.java` 在仓库里都不存在；平台层的测试实际落在 `OAuthStateCodecTest` / `ContentTokenEncryptorTest` 上） |
| Task 11 Step 4 | 生成页 `/opc/content/generate` | （未完成：`views/opc/content/generate.vue` 不存在 —— 生成功能做成了 `script/index.vue` 里的「AI 生成」弹窗，没有独立的生成页/路由） |
| Task 11 Step 7 | `router/index.ts` 加 6 路由 | （未完成：`router/index.ts` 里只有 2 条 `/opc/content` 隐藏详情路由（`script/:id` 与 `script/:id/adapt`），plan 写的 6 条子路由从未加进去；4 个菜单页改由 sys_menu 数据库菜单承载，见 `springboot3/sql/migrations/V20261022__opc_sys_menus.sql`，commit `08305b8` 自己也写着「Task 11 漏掉菜单配置」） |
| Task 12 Step 2 | 加密 secret（`ENC(...)`） | （未完成：`opc-content-dev.yml` 里 `douyin.client-secret` 是明文占位符 `dev_client_secret_placeholder`，不是 `ENC(...)` —— 真实 client_secret 从来没被加密写进去） |

### 2.3 计数

- 没勾 **17** 个 = "跑一次看输出" **13** 个 + "东西真的没做" **4** 个
- 勾 **61** 个 = 纯文件/代码落地 **61** 个（其中 **18** 个勾上了但行尾标了偏差或"部分"）

---

## 3. 重点核实：真实数字 vs plan 声称

### 3.1 REST 端点：plan 说 22，**实际 20**

后端 3 个 controller 逐条 grep：

| Controller | 文件 | 端点数 |
|---|---|---|
| `OpcContentScriptController` | `springboot3/ruoyi-modules/opc-content/src/main/java/com/ruoyi/opc/content/controller/OpcContentScriptController.java` | **11** |
| `OpcContentPlatformController` | 同目录 `OpcContentPlatformController.java` | **5** |
| `OpcContentPublishController` | 同目录 `OpcContentPublishController.java` | **4** |
| **合计** | | **20** |

Script 的 11 条：`POST /script`、`GET /script/list`、`GET|PUT|DELETE /script/{id}`、`POST /script/{id}/generate`、`POST /script/{id}/refine`、`POST /script/{id}/ready`、`POST /script/adapt`、`GET /script/{id}/publish-history`、`GET /script/dashboard`。

**三处独立证据都指向 20，不是 22：**

1. 前端 `vue3-typescript/src/api/opc/content.ts` 文件头注释自己写着：`// 11 + 5 + 4 = 20 endpoints (对齐 backend ...)`；实际导出 **20** 个 API 函数
2. 仓库自带的 `docs/verification/week-74/OPC-W74-VERIFICATION-opc-content.md` §3「端点清单 (20)」逐条列了 20 条
3. commit `dcb788d` 的合并说明写的是「20 endpoints」；只有 commit `4532347`（Task 5）和 commit `409d6ad`（Task 10）分别写「22 端点」和「20 API endpoints」——**同一批提交里两个说法互相矛盾**

**plan 自己的加法也对不上**：Task 16 的 VERIFICATION 模板写「22 REST 端点 (Script 11 + Platform 5 + Publish 4 + Dashboard 1 + Adapt 0 复用 Script)」—— 11+5+4+1 = **21**，不是 22。

→ **「22 REST 端点」是「声称做了但其实没有」的条目。**

### 3.2 `@Test` 数：plan 说 ~60，**plan 时代正好 60，现在 72**

| 测试文件 | plan 时代（commit `8ccaf1b`, 2026-09-14） | 现在 |
|---|---|---|
| `OpcContentScriptServiceImplTest` | 12 | 12 |
| `OpcContentPlatformAccountServiceImplTest` | 10 | 12 |
| `OpcContentPublishServiceImplTest` | 12 | 12 |
| `OpcContentAdaptServiceImplTest` | 8 | 8 |
| `ContentLlmClientTest` | 10 | 12 |
| `ContentTokenEncryptorTest` | 8 | 8 |
| `OAuthStateCodecTest` | —（plan 没这个文件） | 8 |
| `DouyinClientTest` | **plan 要求，从没写** | **不存在** |
| `MockPlatformClientTest` | **plan 要求，从没写** | **不存在** |
| **合计 @Test** | **60** | **72** |

→ **单测数量这条声称是准确的**（plan 时代确实 60 个，现在 72 个更多）；但 **plan 点名的 7 个测试文件里，有 2 个从来没写**。

### 3.3 抖音 sandbox "真实 OAuth/上传/发布"：**没真打通**

| 证据 | 位置 | 说明 |
|---|---|---|
| 代码是真的 | `service/platform/DouyinClient.java` | 4 个方法都写了真的 HTTP 调用：`POST /oauth/access_token/`、`POST /oauth/refresh_token/`、`POST /video/upload/`（multipart）、`POST /video/create/` |
| **凭据是占位符** | `springboot3/deploy/nacos/opc-content-dev.yml` + `src/main/resources/application.yml` | `douyin.client-key: "dev_client_key_placeholder"`、`client-secret: "dev_client_secret_placeholder"` |
| **永远走 mock** | 同上两份配置 | `opc.content.platform.mock: true` / `platform.mock: true` |
| **异常全被吞** | `DouyinClient.java` | 4 个方法每个都 `catch (Exception e) → return fallback.xxx(...)`，失败**静默降级**，调用方看不出自己拿到的是 mock 数据 |
| **secret 从没加密** | `opc-content-dev.yml` | 计划写 `client-secret: "ENC(ENC_SECRET_VALUE)"`，实际是明文占位符 |

→ **plan Goal 里「抖音 sandbox 真实 OAuth/上传/发布集成」这句话是不成立的**。有骨架、有降级、没有任何一次真实调用成功过。按规约，Task 7 Step 3「写 DouyinClient (sandbox)」这一步本身**做了**（文件在、内容对得上），所以勾上了，但行尾写明了「真实 sandbox 从未打通」。

### 3.4 4 个 LLM 场景接 opc-ai-core：**这条是真的**

- `ContentLlmPrompts.java` 里 4 个 scene 常量 + 4 个 system prompt：`content_short_drama` / `content_video_script` / `content_article` / `content_platform_adapter`
- `ContentLlmClient.java` 4 个方法 `generateDrama / generateVideo / generateArticle / adapt`，都走 `OpcContentAiCoreGateway.chat(payload)`
- `OpcContentAiCoreGateway` 的 `@FeignClient(name = "opc-ai-core", path = "/opc/llm")` + `@PostMapping("/chat")` → 与对端 `opc-ai-core` 的 `@RequestMapping("/opc/llm")` + `@PostMapping("/chat")` **对得上**
- 对端 `OpcLlmController.chat()` 确实接收 `scene` 并透传进 `LlmGateway.ChatContext.scene`（用于计量）

→ **这条没有虚高。**

---

## 4. 其他"声称做了但其实没有"的条目

| # | 条目 | 事实 |
|---|---|---|
| 1 | **plan Goal / Task 5 Step 6 / Task 10 Step 1 的「22 REST 端点」** | 实际 20 个（见 3.1） |
| 2 | **plan Goal 的「抖音 sandbox 真实 OAuth/上传/发布集成」** | 只有骨架 + mock 降级，零真实调用（见 3.3） |
| 3 | **Task 4 Step 3「5. 发通知 (NotificationGateway)」** | `OpcContentPublishServiceImpl.java` 没有任何 `NotificationGateway` 注入，代码里留着 `// 7) TODO Task 8: NotificationGateway.send(...) 发通知` |
| 4 | **Task 9 Step 3 的两个平台层测试文件** | `DouyinClientTest.java` / `MockPlatformClientTest.java` 在仓库里不存在 |
| 5 | **Task 11 Step 7 的 6 条 router 路由** | `router/index.ts` 只有 2 条隐藏详情路由；菜单路由是后来用 sys_menu 数据库菜单补的（`08305b8` 自称「Task 11 漏掉菜单配置」） |
| 6 | **Task 15 Step 1 里 health-check 的 10.6 条** | 检查 `/opc/content/script/recent`，但这个 handler **从来没有存在过**（`git log --all -S '"/recent"' -- springboot3/ruoyi-modules/opc-content` 无输出；gateway 白名单里也有注释「原 `/opc/content/script/recent` 没有任何 handler … 已删」）。`nok` 会让 `health-check.sh` 整体 `exit 1` |
| 7 | **Task 12 Step 2 的 `ENC(...)` secret** | 从来没有真实 secret 被加密过 |
| 8 | **Task 16 Step 2 模板里的「e2e: 10 步全过 / 端点: 22/22」** | W74 报告自己写的是 **8/10 PASS**、**20 端点**；W75 报告改成 10/10。两份都是自述，无运行记录 |

---

## 5. 命名/路径偏差（不影响勾否，但会让人找不到文件）

| plan 写的 | 仓库里实际的 |
|---|---|
| `RuoYi-Cloud-Vue3-typescript/` | `vue3-typescript/` |
| `springboot3/deploy/mysql-initdb.d/11-opc-content-schema.sql` | `springboot3/deploy/mysql-initdb.d/16-opc-content-schema.sql` |
| `springboot3/ruoyi-modules/opc-content/Dockerfile.opc-content` | `springboot3/ruoyi-modules/opc-content/Dockerfile` |
| `views/opc/content/index.vue`（Dashboard） | `views/opc/content/dashboard.vue` |
| `views/opc/content/generate.vue` | 不存在（生成功能并进 `script/index.vue` 的弹窗） |
| `views/opc/content/platform-account.vue` | `views/opc/content/platform-account/index.vue` |
| `views/opc/content/publish.vue` | `views/opc/content/publish/index.vue` |
| Task 11 的 6 个 Vue 页构成 | 实际是 dashboard / script/{list,detail,adapt} / platform-account / publish —— `adapt.vue` 是 plan 没写的 |
| `springboot3/deploy/nacos/opc-content-dev.yml` 的 `opc.content.*.base-url` | nacos 里的这三项**被删了**（文件里有注释说明「没有任何代码读」） |
| seed 数据「7 脚本 + 2 账号 + 2 发布」 | 实际 3 脚本 + 1 平台账号 + 1 发布 + 1 适配 |

---

## 6. 结论：这份 plan 整体靠不靠谱

**靠得住的部分是真靠得住**（61/78 = 78% 的步骤有硬证据）：

- 后端骨架完整：`opc-content` 模块 **46 个 main java 文件 + 7 个测试文件 + 4 个 mapper XML**，`domain/` 4 个、`dto/` 7 个、`enums/` 4 个、`mapper/` 4 个、`service/impl/` 4 个、`controller/` 3 个、`feign/` 3 个 interface + 3 个 factory、`service/llm/` 2 个、`service/platform/` 4 个、`config/` 2 个、`util/` 1 个 —— **plan 里 Files 块点名的文件一个不缺**
- 4 张表的 DDL 在 `springboot3/deploy/mysql-initdb.d/16-opc-content-schema.sql`，字段与 mapper XML 完全对齐
- 4 个 LLM 场景真接了 opc-ai-core（见 3.4），这条没有虚高
- 部署链路完整：Dockerfile（thin jar）+ `docker-compose.yml` 的 `aiopc-content` + `helm/opc/templates/deployment-content.yaml` & `service-content.yaml` + `values.yaml` 的 `services.content` + gateway 路由 `opc-content → http://aiopc-content:9325`
- Nacos 配置真进了配置中心（commit `48abbee` 明确点名 opc-content-dev.yml）
- e2e 脚本 + VERIFICATION 报告都在，提交历史 25 个 commit 逐 Task 对应得上

**不靠谱的部分集中在"数字"和"外部集成"上**：

- **所有"端点数"都不准**。plan 说 22，实际 20，连 plan 自己的加法都是 21。这不是笔误级别的偏差 —— 是从 Goal 一路抄到 commit message、连仓库自带的 VERIFICATION 报告都被带偏的地方（报告写 20 反而是对的）
- **所有需要外部凭据的事都没成**。抖音 sandbox 是"写了接口 + 配了 mock 降级"，secret 连加密都没做；这类工作在 plan 里被写成"真实集成"，在仓库里表现为"骨架齐全"，两者差着一整个外部依赖的距离
- **17 个没勾的里 13 个是"跑一次看输出"**。这不是"没做"，是"查不到"——仓库里没有 CI 记录、没有测试报告文件，plan 里的 "Expected: BUILD SUCCESS / Tests run: 60 / 10/10 PASS" 全部无从复核。**这个比例本身说明：这份 plan 的验证步骤是"写下来给人看的"，不是"跑了留痕的"**

**哪些 Task 整体没做**：没有。16 个 Task 每一个都至少有一半步骤落地。

**哪些 Task 做了一半**：
- Task 4 —— `publish()` 少"发通知"
- Task 5 —— 端点数差 2 个，且没有独立的 DashboardController
- Task 7 —— 真实凭据 + 真实 sandbox 调用没做
- Task 9 —— 两个平台层测试文件没写
- Task 11 —— 路由没做、生成页被合并掉
- Task 12 —— secret 没加密
- Task 13 —— seed 数据量只有 plan 的三分之一
- Task 15 —— health-check 里有一条查不存在端点的恒失败检查

**一句话**：这份 plan 的**结构性工作量是真的、对外交付的说法是虚的**。拿它当"opc-content 交付清单"会多算 2 个端点、多算一次抖音集成、多算 6 条前端路由；拿它当"还差什么"的清单，则会漏掉"抖音从来没真跑通"和"secret 从没加密"这两件真事。**代码层面可以信任，数字和外部集成层面不能信任。**
