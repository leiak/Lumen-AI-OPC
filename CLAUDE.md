# CLAUDE.md

给 Claude Code 看的项目说明。**先读这一页，再动代码。**

---

## 一、这个仓库是什么

OPC（One-Person Company）= 一人公司 + N 个 AI 数字员工。
基于 RuoYi-Cloud-Vue-Plus（Spring Boot 3.5.16 / Spring Cloud 2025.0.2 / Java 17）改的微服务栈，
前端 Vue 3.5 + TS 5.6 + Element Plus 2.13。

```
springboot3/           后端（Maven 多模块）
  ruoyi-gateway/       网关 8080          ruoyi-auth/      认证 9200
  ruoyi-system/        系统 9201          ruoyi-modules/   13 个 OPC 业务模块
                                                   + ruoyi-job(调度器 9203，2026-10-30 才真正部署，见坑 #9)
  ruoyi-common/        共享库（含租户守卫） opc-common/     OPC 共享库
  deploy/              compose + helm + 健康检查 + 运维脚本
  sql/                 迁移与种子
vue3-typescript/      前端
docs/superpowers/      9 份实施计划 + 逐份审计报告（plans/audit/）
```

**端口以 `springboot3/deploy/docker-compose.yml` 为准**，根 README 的模块布局已按它校对过。
业务端口只绑 `127.0.0.1`，对外只有前端 8079。

---

## 二、最重要的一条规矩：不说假话

这个项目的主题就是**让演示环境不再对外说它并不支持的事**。
过去踩的坑几乎全是这一类：

- 配置中心「在管事」—— 实际 9 份配置躺在没人读的 tenant 里；
- 限流规则表「有 17 条」—— 实际 resource 挂在 4 条不存在的路由上，一条都没生效
  （**这条已修好**：现在是 18 条 ↔ 网关 18 条路由，2026-10-30 接 `ruoyi-job` 时补的第 18 条）；
- 被限流的请求「返回 429」—— 实际返回 HTTP 200；
- 记忆系统「能记事」—— 实际 `embed()` 返回 `Math.random()`；
- 「两个市场页」都叫智能体市场 —— 一个是平台自营，一个是开发者上架；
- 卡片上 823 次浏览 —— 编的。

**所以：凡是你要说「这个功能能用 / 这个数据是真的」，必须有一条可复跑的检查证明它。**
没有证据就说没有，别用「应该」「大概」「理论上」。

### 落地办法：把断言写进健康检查

`springboot3/deploy/scripts/health-check.sh` 现在有 **26 节**，每节只做一件事：
把「这个功能是不是真的在管事」变成一条会红的断言。新功能上线时：

1. 在 `health-check.sh` 里加一节（`ok()` / `nok()` / `warn()`）；
2. 在 `main()` 里加一行 `step N check_xxx`；
3. 跑 `bash deploy.sh health`，**必须全绿**才算做完。

调单节：`HEALTH_ONLY=N bash deploy.sh health`。

---

## 三、怎么跑

### 本地栈

```bash
cd springboot3/deploy
[ -f .env ] || cp .env.example .env
docker compose up -d nacos1 mysql redis
sleep 60
cd nacos && bash import-dev.sh && cd ..
docker compose up -d aiopc-gateway aiopc-auth aiopc-system ...   # 见 deploy/README.md
bash deploy.sh health        # ← 唯一被放行的线上验证入口
```

### 重建某个服务

```bash
cd springboot3/deploy
bash deploy.sh rebuild opc-crm       # 模块名带 opc- 前缀
bash deploy.sh rebuild gateway
bash deploy.sh rebuild frontend
bash deploy.sh rebuild all
```

### 后端测试

```bash
cd springboot3
JAVA_HOME=/usr/lib/jvm/java-17-openjdk mvn -pl ruoyi-modules/opc-marketplace -am test
```

**本机默认 `java` 是 1.8，手搓 `mvn` 必须显式带 `JAVA_HOME`**（`deploy.sh` 自己会探测，手搓不会）。
`package` 只写 `target/`，`install` 才写 `~/.m2` —— 共享库改动后必须 `install`。

### 前端测试

```bash
cd vue3-typescript
npx vitest run              # 66 文件 / 1294 用例
npx vitest run src/views/opc/marketplace/detail.spec.ts
```

---

## 四、踩过的坑（别再踩第二遍）

### 1. 共享库改了代码，镜像里还是旧包

`dependency:copy-dependencies` 不带 `-am`，依赖只能从 `~/.m2` 拿快照。
`opc-ai-core` 曾因此一直是 9 月 23 日的快照，**生产上一个月没生效过任何一次修复，
而 deploy.sh 照样报成功**。→ 共享库（`ruoyi-common-security` / `opc-common` / `opc-ai-core`）
改完必须 `install`。

### 2. Nacos 配置 API 的参数名

查配置用 **`tenant=`**，不是 `namespaceId=`。
`namespaceId=opc-dev` 会**静默回落到默认命名空间**，回读 md5 全 `[OK]` 是假阳性。
配置在 `tenant=opc-dev`，用 `namespaceId=` 查会拿到「config data not exist」。

### 3. Sentinel 限流规则挂的是路由 id

`resourceMode=0` 对的是 `route.getId()`（比如 `opc-ai-core`），**不是 URL 路径**。
规则表真相源是 `springboot3/deploy/nacos/sentinel-ruoyi-gateway.json`（**18 条 ↔ 网关 18 条路由**）。
加一条网关路由就**必须**在规则表里加对应的一条 —— 健康检查第 23.4 节会把这张表和
`ruoyi-gateway/src/main/resources/application.yml` 里真实存在的路由 id 逐条对表，缺一条报 FAIL。
网关命中限流时报 `ParamFlowException`，**返回 429**（以前返回的是 200）。

### 4. Sentinel 控制台不连 Nacos

它的 jar 里**没有 Nacos 客户端**。规则是**回头问网关要的**：
`GET http://<网关ip>:8719/gateway/getRules`。数据流是
**Nacos → 网关内存（GatewayRuleManager）→ 控制台**。
控制台上改规则只改到网关内存，不落 Nacos，网关一重启就没。

**心跳地址只在启动那一刻解析一次，失败不重试** —— 网关比控制台晚起时要重启**网关**，
不是重启控制台。症状是控制台永远「零节点零规则」，而它自己看着一切正常。

### 5. Spring Boot 的列表属性是替换，不是合并

覆盖 `auth.filter.exclude-urls`（在 jar 的 classpath application.yml 里）会让控制台
静态资源全部要求登录，页面直接打不开。

### 6. 挂载路径写错是**静默失败**

`bladex/sentinel-dashboard` 的 WORKDIR 是 `/bladex/sentinel`。
挂到 `/home/admin/sentinel-dashboard/` → 目录不存在 → 挂载失败 → **镜像自带配置照旧生效，
一个异常都不报**。挂载路径要从镜像里查，别猜。

### 7. 声明了不等于接上了

- compose 里有 `rabbitmq` 容器、有服务拿到 `RABBITMQ_HOST`，
  但**全仓没有一个 pom 引入 amqp 客户端，也没有一行 Java 代码用它**；
- `opc_prompt_template` 表建了（带 `version` / `published` / `test_score` 字段），
  但**没有一行代码读写它** —— 「Prompt 版本化 / 红队评测 / AB 测试」目前是表结构，不是功能；
- README 里写过的「Caffeine 双层缓存」「RabbitMQ 异步事件」都不成立。

**加基础设施之前先确认有代码用它。**

### 8. HTTP 200 不代表成功 —— RuoYi 的错误在 `body.code` 里

- 后端业务错误全是 **HTTP 200 + `body.code=500`**：
  `GlobalExceptionHandler`（`ruoyi-common-security`，经 `AutoConfiguration.imports` 注册）
  把**所有**异常都转成 `AjaxResult.error(...)` 返回，**没有 `@ResponseStatus`** ——
  缺必填参数、路径变量类型不匹配、业务异常，全都长这样。
- 网关的拒绝也是 **HTTP 200 + `body.code=401`**：
  `AuthFilter.unauthorizedResponse` → `ServletUtils.webFluxResponseWriter(response, msg, 401)`
  → 里面传的是 `HttpStatus.OK`。
- 所以 **`curl -sf` 判不出成败** —— 它只看 HTTP 码，200 就是成功。
  写检查必须看 `body.code`（`health-check.sh` 里的 `body_code()` 助手）。
- 这坑已经吃过一次：第 10 节有条检查查 `/opc/content/script/recent` —— 那个 handler
  从来不存在，撞上 `@GetMapping("/{id}")` → `"recent"` 转不成 Long → `body.code=500`
  —— 而 HTTP 仍是 200 → 那条检查**一直报 ok，却什么都没断言**。2026-10-29 已重写。

### 9. 「定时任务」现在真的在跑 —— 但它**曾经**一次都没跑过

`ruoyi-job` 是全仓**唯一**引 quartz 的地方，`WorkflowCronJob` 也在里面。
2026-10-30 之前它**从来没有被部署过**：没有 `application.yml`（→ 没有 DataSource
→ `SysJobServiceImpl.init()` 这个 `@PostConstruct` 启动就失败）、没有 Dockerfile、
compose 里没有容器、网关没有 `/schedule` 路由。`sys_job_log` 表 0 行。

现在它被接上了（`aiopc-job` 容器，9203）：

- **Quartz 用 RAMJobStore**（`spring.quartz.job-store-type: memory`）——
  `ruoyi-job/config/ScheduleConfig.java` 里那份 JDBC 方案**整个被注释掉了**，
  注释里写的也是「单机部署建议删掉此类和 qrtz 数据库表」。所以那 11 张
  `QRTZ_*` 表一直是空的，**这是预期状态，不是调度器坏了**。
- 触发链：`sys_job` → Quartz → `WorkflowCronJob.execute()` → OpenFeign
  (`RemoteWorkflowService`) → `opc-agent-hub` 的
  `/opc/agent/workflows/{code}/trigger`（`@InnerAuth` 内部端点）。
- `scheduler.triggerJob()` 是**异步**的：HTTP 立刻返回，工作流在后台跑。
  写检查不能只看接口返回，得等落库。
- `WorkflowTriggerServiceImpl` 有 **30 秒去重窗口**（正在跑的不会重复触发），
  连续触发两次可能只产生一条运行记录。

> ⚠️ 「Spring 那边 0 个 `@Scheduled`」这句话**依然成立**，但它不再是结论的根据 ——
> 调度靠的是 Quartz，不是 Spring 的定时器。

### 9b. 网关路由 / 前端路径：少一个前缀就对不上

- 前端 axios 的 `baseURL` 是 `/prod-api`，但 nginx 的 `proxy_pass` 末尾带斜杠
  → **会把 `/prod-api/` 整段剥掉**再转给网关（`vue3-typescript/nginx.conf`）。
  所以**直接问网关时 URL 不带 `/prod-api`**：`/schedule/job/list`，
  不是 `/prod-api/schedule/job/list`。
- 网关路由 `/schedule/**` + `StripPrefix=1` → `ruoyi-job` 的 `/job/**`
  （`SysJobController` 是 `@RequestMapping("/job")`，`SysJobLogController` 是 `/job/log`）。
- `ruoyi-job` 的 `bootstrap.yml` **必须写 `namespace: opc-dev`**：
  Feign 靠 Nacos 发现解析 `opc-agent-hub`，不写就落默认 tenant（public）→ 找不到对端。
  `spring.config.import` 必须带 `optional:` —— opc-dev 下没有 `ruoyi-job-dev.yml`，
  不带 optional 拉不到配置会直接让应用启动失败。
- `ruoyi-job` 的 pom **缺 `ruoyi-common-datasource`** 就起不来（没有 DataSource）。
  而 `org.mybatis:mybatis` 不能直接声明 —— 根 pom 没管它的版本，会报
  "version is missing"；只声明 `mybatis-spring` 就够（mybatis 本体由它传递带进来）。

### 10. 手搓 curl 会被分类器拦

线上验证走仓库自带入口：`bash deploy.sh health` / `bash deploy.sh rebuild`。
直接 `bash scripts/health-check.sh` 会被判成 Production Reads 拒掉 —— 同一个脚本，
加一层 `deploy.sh` 就放行。**被拒不代表线上有问题，别据此改结论。**

### 11. 生产操作要点名授权

拉镜像 / 推配置 / 停容器 / 改历史 —— 每样都要用户**单独明确点名**。
「重建镜像」不等于授权重启。分类器拒了就停下问，不要绕。

### 12. 口令不上命令行

- 查库：`mysql -t ry-vue-opc -e '...'`（走 `/root/.my.cnf`，600）
- 控制台口令：放在 `springboot3/deploy/sentinel-dashboard/application.yml`，
  健康检查**从文件读**，不落命令行、不打日志
- API key：只看有没有 / 多长，**不要打印值**；凭据只经应用自己的配置注入

### 13. 「每天 09:00」是北京时间的假话 —— 容器时区是 **UTC**

Quartz 按**容器时区**求值，而 `aiopc-job` 容器里的 JVM 时区是 **UTC**：

| cron 表达式 | UTC | 北京时间 | 对不对 |
|---|---|---|---|
| `0 0 9 * * ?` | 09:00 | **17:00** | ❌ 差 8 小时 |
| `0 0 1 * * ?` | 01:00 | **09:00** | ✅ 现在用的就是这个 |

`sys_job` 里那条「财务日报」现在是 `0 0 1 * * ?`（迁移
`springboot3/sql/migrations/V20261102__finance_agent_truth.sql` 改的），
README 里写「每天 09:00」时**必须写明是北京时间**。

同一个坑在**查库**时也会咬人：`opc_agent_workflow_run.start_time` 存的是
**北京墙上时间**，而 MySQL 的 `NOW()` 是 **UTC**（`SELECT NOW(), UTC_TIMESTAMP()`
两个值相同）。所以「最近 7 天」这种窗口不能写
`start_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)` —— 那是拿北京值比 UTC 时刻，
**窗口会少 8 小时**，在 7 天边界上**假红**。要写成
`DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 8 HOUR`（健康检查 26.6 就是这么写的）。

### 14. 「调用成功」不等于「有正文」 —— 强制思考模型会把 `max_tokens` 吃光

MiniMax 把 `MiniMax-Text-01` 下线后换成 `MiniMax-M3.1-Flash`。它是**强制思考**模型：
思考 token **计入 `max_tokens`**，而且 `reasoning_effort` 不传时厂商默认按 `max` 思考。
调用方给的额度是按「正文长度」定的（insight 的建议 600、AgentRuntime 2048）→ 思考把额度吃光 →

```
HTTP 200，usage 里有 token，choices[0].message 里**没有 content 字段**
finish_reason = "length"
```

解析层把「没有 content」读成 `null`，一路当成功传下去，于是：

- 降级链（DeepSeek）**根本不触发**（失败被藏在「成功」里）；
- `null` 被**写进 LLM 缓存** —— 一小时内每次同样提问都拿到那份空；
- `null` 漏到工作流 persist 层才炸成一句看不懂的「产出不是字符串: n2」，
  **锅甩给了一个无辜的节点**（真凶是 provider 报成功但内容是空的）。

现在的三层防线（`MiniMaxProvider` / `LlmGateway` / `WorkflowEngine`+`WorkflowPersistHandler`）：

- provider：响应里没有正文 = **失败**（错误信息里带 `finish_reason`），不是「成功但 content 是 null」；
- 网关：provider 报成功但正文为空 → 当成失败换下一个 provider，且**不写缓存**；
- 引擎/persist：正文为空 → 明确说「上游 LLM 没返回正文」，落到 PARTIAL + 引擎的**确定性**聚合摘要。

请求侧也要留出空间：`opc.llm.minimax.min-max-tokens`（默认 4096，给思考留位置）
+ `opc.llm.minimax.reasoning-effort`（默认 medium）。这两个键写在**四份 yml** 里 ——
健康检查 26.11 要求它们逐行一致。

> 换模型名之前先去看厂商的 `/v1/models`；**任何**「返回了但正文是空的」响应，
> 都要当成失败处理，否则它会一路伪装成成功，直到某个下游节点炸出一句不相干的话。

---

## 五、文档与计划

- `docs/superpowers/plans/` 下 9 份实施计划。**它们的 checkbox 已在 2026-10-29 逐条审计过**
  （9 份审计报告 + 一份总索引在 `docs/superpowers/plans/audit/`，索引里有统一的判定口径）。
  审计口径：能指认产物才勾；只有「跑命令看输出」而仓库里查不到输出的一律不勾。
  **别把没勾的当待办队列** —— 175 个没勾里有 127 个只是「跑一次看输出」的证据不在仓库里。
- 计划里的数字（端点数、测试数、容器数）经常和最终实现对不上。**以代码为准**。
- 写 VERIFICATION 报告时，**贴真实运行输出**，别写「已验证」了事。

---

## 六、提交规范

- 提交信息用中文，把「**为什么**」写清楚 —— 这个仓库的提交历史本身就是一份
  「我们踩过什么坑」的账本。
- 结尾加：

```
Co-Authored-By: Claude Code <noreply@anthropic.com>
```

- 公开仓库，推 `main` 前确认没有把真凭据推进去。
- 改已推送的历史（amend / rebase / filter-branch）**必须先拿到用户点名授权**。
