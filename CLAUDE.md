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
- 限流规则表「有 17 条」—— 实际 resource 挂在 4 条不存在的路由上，一条都没生效；
- 被限流的请求「返回 429」—— 实际返回 HTTP 200；
- 记忆系统「能记事」—— 实际 `embed()` 返回 `Math.random()`；
- 「两个市场页」都叫智能体市场 —— 一个是平台自营，一个是开发者上架；
- 卡片上 823 次浏览 —— 编的。

**所以：凡是你要说「这个功能能用 / 这个数据是真的」，必须有一条可复跑的检查证明它。**
没有证据就说没有，别用「应该」「大概」「理论上」。

### 落地办法：把断言写进健康检查

`springboot3/deploy/scripts/health-check.sh` 现在有 **24 节**，每节只做一件事：
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
规则表真相源是 `springboot3/deploy/nacos/sentinel-ruoyi-gateway.json`（17 条 ↔ 网关 17 条路由）。
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

### 8. 「定时任务」目前一次都没自动跑过

`ruoyi-job` 是全仓**唯一**引 quartz 的地方，`WorkflowCronJob` 也在里面 ——
但**没有任何模块依赖 `ruoyi-job`**，compose 里**也没有它的容器**。
Spring 那边同样空：**全仓 0 个 `@Scheduled`、0 个 `@EnableScheduling`**。

所以 `sys_job` 表里的注册没人消费，日报快照 / 低库存告警 / Workflow cron
全都是**写好了但没人叫醒**。根 README 的部署图里那个「agent-hub (Quartz cron)」
是画的，不是真的。

### 9. 手搓 curl 会被分类器拦

线上验证走仓库自带入口：`bash deploy.sh health` / `bash deploy.sh rebuild`。
直接 `bash scripts/health-check.sh` 会被判成 Production Reads 拒掉 —— 同一个脚本，
加一层 `deploy.sh` 就放行。**被拒不代表线上有问题，别据此改结论。**

### 10. 生产操作要点名授权

拉镜像 / 推配置 / 停容器 / 改历史 —— 每样都要用户**单独明确点名**。
「重建镜像」不等于授权重启。分类器拒了就停下问，不要绕。

### 11. 口令不上命令行

- 查库：`mysql -t ry-vue-opc -e '...'`（走 `/root/.my.cnf`，600）
- 控制台口令：放在 `springboot3/deploy/sentinel-dashboard/application.yml`，
  健康检查**从文件读**，不落命令行、不打日志
- API key：只看有没有 / 多长，**不要打印值**；凭据只经应用自己的配置注入

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
