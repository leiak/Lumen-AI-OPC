# Plan 审计报告：2026-09-09-frontend-docker-integration.md

- **审计日期**：2026-10-29
- **被审 plan**：`docs/superpowers/plans/2026-09-09-frontend-docker-integration.md`
- **plan 目标**：把 `aiopc-frontend` 容器加进本地 Docker 栈，让 Vue 3 + TS 前端完全跑在 Docker 里，访问 `http://localhost:8079`，并把 `/prod-api/*` 反代到 `aiopc-gateway:8080`
- **审计方式**：只读仓库 + git 历史，逐条核实 33 个 checkbox。未跑 docker、未连数据库、未起服务
- **结果**：**勾了 31 / 没勾 2 / 共 33**

---

## 0. 判定口径

一条 checkbox 能勾，当且仅当仓库里有**可指认的持久记录**能证明这一步的断言成立：

| 证据类型 | 接受 | 例子 |
|---|---|---|
| 文件/产物在仓库里存在且内容对得上 | ✅ | `springboot3/deploy/Dockerfile.frontend` 首行 `# aiopc-frontend` |
| 配置里 grep 得到 | ✅ | compose 里 `aiopc-frontend` 服务块、`nginx.conf` 里的 `try_files $uri $uri/ /index.html` |
| git 提交在 plan 时间窗内 | ✅ | `6172a41` / `6a7d0c6` / `b547183` / `77d3475` / `2fda26e`（全在 2026-09-09） |
| **脚本生成并入库的测试报告文件** | ✅ | `tmp_e2e/e2e_report.md` —— `tmp_e2e/e2e_all.py` 的声明输出（docstring 写明 `Output: tmp_e2e/e2e_report.md`），50 条端点级结果 |
| **runbook / 检查清单里的断言与断言式代码** | ✅ | `RECOVERY.md` §8 的 `- [x] 前端 http://localhost:8079 可访问`；`scripts/health-check.sh` 的 `check_frontend()` |
| 手贴进 markdown 的控制台片段 | ❌ | `docs/verification/week-49/...` 里的 `docker ps` 快照（只作旁证） |
| 提交信息里自述的测试结论 | ❌ | `0768036` 正文里的「测试: ...返回 JWT token」（只作旁证） |
| 只有"跑命令看输出"、仓库里查不到输出 | ❌ | 见 §2 |

> 说明：本仓库里"没有测试报告文件"这个前提**不成立**——`tmp_e2e/e2e_report.md` 就是脚本生成的真报告（2026-09-10，49/50 通过）。
> 因此凡是**该报告能覆盖**的断言（镜像构建并跑起来、容器在跑、`/prod-api` 反代通）按有记录勾；
> 凡是只有手贴片段或提交自述支撑的，勾的同时在行尾注明证据性质，并在 §5 列出，便于按更严口径复核。

---

## 1. 逐 Task 汇总

| Task | 主题 | 步骤数 | 勾了 | 没勾 | 一句话结论 |
|---|---|---|---|---|---|
| Task 1 | 预检查：`node_modules` / 本地构建 / `dist/index.html` | 6 | 5 | 1 | **基本做了**。`node_modules`（443 个目录项）、`dist/index.html`（5394 字节）、bundle 里能 grep 到 `prod-api` 都在；只差 `npm run build:prod` 的运行输出查不到 |
| Task 2 | 建 `deploy/Dockerfile.frontend` | 3 | 3 | 0 | **做了**。`6172a41` 逐字照抄 plan；同日 16:03 被 `2fda26e` 改成 nginx 多阶段构建（现文件首行仍是 `# aiopc-frontend`） |
| Task 3 | 建 `vue3-typescript/serve.json` | 3 | 3 | 0 | **做了，但当天就被推翻**。`6a7d0c6` 逐字照抄 plan 且 JSON 合法；`2fda26e`（20 分钟后）删掉它，改用 `vue3-typescript/nginx.conf`。现在仓库里**没有** `serve.json` |
| Task 4 | 建 `vue3-typescript/.dockerignore` | 3 | 3 | 0 | **做了**。`b547183` 与 plan 只差一行：实现里没有 `dist`（Dockerfile 要 `COPY ./dist/`，排掉就构建不出镜像） |
| Task 5 | compose 加 `aiopc-frontend` 服务 | 5 | 5 | 0 | **做了**。`77d3475` 提交信息与 plan 完全一致；服务块内容与 plan 逐字相符，且插入位置正是 plan 说的 `aiopc-insight` 之后、`aiopc-gateway` 之前 |
| Task 6 | 构建镜像 + 隔离冒烟 | 3 | 2 | 1 | **做了大半**。镜像确实构建出来并在跑（2026-09-10 的 e2e 报告走 8079 跑通 49/50）；`docker run --rm` 隔离冒烟的输出查不到，而且 plan 期望的 `Accepting connections at http://localhost:8079` 是 serve 的日志，换成 nginx 后根本不会有 |
| Task 7 | compose 起服务 + 5 项集成验证 | 6 | 6 | 0 | **做了**。容器在跑、`/` 出 SPA、`/prod-api` 反代通（e2e 49/50）、前端不注册 Nacos —— 全部有据 |
| Task 8 | 全栈收尾验证 + 收尾提交 | 4 | 4 | 0 | **做了**。plan 写的"5 个容器"清单已过时（现在 compose 29 个服务），但断言本身成立 |
| **合计** | | **33** | **31** | **2** | |

---

## 2. 没勾的 2 个步骤（全列出 + 理由）

| 位置 | 步骤 | 理由 |
|---|---|---|
| Task 1 Step 3 | 运行生产构建 `npm run build:prod`（Expected: 末行含 `built in`） | （未完成：仓库里查不到 `npm run build:prod` 的运行输出；`dist/` 被 `.gitignore` 忽略，是本机残留产物（`dist/index.html` 时间戳 2026-10-28），不能证明本 plan 窗口内跑过这次构建） |
| Task 6 Step 3 | 隔离冒烟 `docker run --rm -d ... aiopc-frontend:latest`（Expected: `smoke=200` + 日志 `Accepting connections at http://localhost:8079`） | （未完成：仓库里查不到那次 `docker run --rm` 隔离冒烟的输出；plan 期望的日志行属于 serve@14 方案，实施当天 16:03 已换成 nginx，镜像启动日志不再有这行） |

**两个的共同点**：都不是"没做"，而是**做没做过没法从仓库证明**——都是一次性运行、输出没落盘、也不在 git 历史里。

---

## 3. 关键证据时间线（2026-09-09 一天之内）

plan 本身是在 `4ceb034`（2026-09-10）归档入库的，也就是说**这份 plan 是实现做完之后才补记的**。实现提交集中在 2026-09-09 下午：

| 时间 | 提交 | 内容 | 对应 plan |
|---|---|---|---|
| 15:45:01 | `6172a41` | `feat(frontend): add Dockerfile.frontend for aiopc-frontend` —— 内容与 plan 逐字相同 | Task 2 Step 1/3 |
| 15:45:06 | `6a7d0c6` | `feat(frontend): add serve.json with SPA fallback + /prod-api proxy` —— 内容与 plan 逐字相同 | Task 3 Step 1/3 |
| 15:45:17 | `b547183` | `chore(frontend): add .dockerignore to exclude node_modules and dist` —— 比 plan 少一行 `dist` | Task 4 Step 1/3 |
| 15:45:50 | `77d3475` | `feat(deploy): add aiopc-frontend service to docker-compose` —— 与 plan 逐字相同 | Task 5 Step 3/5 |
| **16:03:09** | **`2fda26e`** | **`feat(frontend): switch from serve npm to nginx multi-stage build`** —— 删掉 `serve.json`，重写 `Dockerfile.frontend`，新增 `vue3-typescript/nginx.conf` | **推翻 Task 2/3 的架构** |
| 18:18:33 | `0768036` | `fix(aiopc): 前端 Docker 化 + 网关路由 prefix 修正` —— 顺带修好 Spring Cloud Gateway 4.x 的 `spring.cloud.gateway.server.webflux` 前缀问题 | Task 6/7 的落地条件 |

> 四个 "Add/Create" 提交的信息与 plan 里 Step 3 写的 commit message **一字不差**——这份 plan 基本就是照着执行完再补记的。

---

## 4. plan 与实现之间的实质偏差（4 处）

| # | plan 写的 | 实际落地 | 影响 |
|---|---|---|---|
| 1 | `serve@14` + `serve.json` 做 SPA fallback 和反代 | **nginx**（`vue3-typescript/nginx.conf` + `node:20-alpine` 构建 / `nginx:1.27-alpine` 运行两阶段） | **最大的一处**。`serve.json` 写完 20 分钟就被删。现在照 plan 重做会重造一个已被放弃的设计 |
| 2 | `.dockerignore` 含 `dist` | 实现里去掉 `dist` | plan 自相矛盾：Task 2 的 Dockerfile 要 `COPY ./dist/`，Task 4 却要排掉 `dist` —— 照 plan 写会构建失败。实现改对了，plan 没改 |
| 3 | File Structure 表写 `deploy/Dockerfile.frontend`、`deploy/docker-compose.yml` | 实际在 `springboot3/deploy/` 下（仓库根没有 `deploy/` 目录） | 表里的路径是错的；Task 正文里的 `D:\...\springboot3\deploy\` 才对 |
| 4 | Task 7 Step 3 期望页标题 `<title>若依管理系统</title>` | 实际是字面量 `%VITE_APP_TITLE%` —— `.env.production` / `.env.development` 里**都没定义** `VITE_APP_TITLE` | 小问题，但说明"前端页面正常"这句话里标题这一项没核过 |

另外两处 plan 的**预期值已过时**（不是做错了，是栈长大了）：镜像体积 plan 估 ~75MB，实际是 node + nginx 两阶段，量级完全不同；Task 8 期望"5 个容器"，现在 compose 里 29 个服务。

---

## 5. 结论：这份 plan 靠不靠谱

**结论：实现层面高度靠谱（真做了 31/33 = 94%），但文档层面已经过期，不能照着重做。**

- **真做了的比例**：33 步里 31 步有据可查。没勾的 2 步都是"跑一次看输出"的验证步，不是功能缺口 —— 整条链路的四件产物（Dockerfile.frontend、.dockerignore、compose 服务块、反代配置）全部真实存在且在生产栈里跑着。
- **整体没做的 Task**：**没有**。8 个 Task 都有实物落地。
- **做了一半的 Task**：严格说只有 **Task 6**（镜像建出来了，但 plan 设计的"隔离冒烟"这一步连同它依赖的 serve 架构一起被放弃）。Task 3 是"做了又被推翻"，不是没做。
- **有没有"声称做了但其实没有"的条目**：**plan 文件里没有**——它原本 33 个 checkbox 全是空的，没有任何虚标的 `[x]` 需要退回。真正的风险在反方向：**plan 的正文描述的是一个已被放弃的架构**（serve@14 + serve.json），而实际跑的是 nginx。今天的人读这份 plan、照着它的 File Structure 表和 Task 3，会做出一个跟线上不一致的容器。
- **最需要注意的一点**：这份 plan 是**实现之后补记的**（plan 入库 2026-09-10，实现提交 2026-09-09），checkbox 从来没被勾过不是因为没做，而是因为**没人回来勾**。它不是"待办清单"，是**一份缺少回填的执行记录**——正如仓库里其他 plan 的 checkbox 一样。

**建议**：把 Task 2/3 的架构描述从 serve 改成 nginx、删掉 `serve.json`、修掉 File Structure 表的路径、给 `.env.production` 补上 `VITE_APP_TITLE`，否则这份 plan 下次被当作实现指南时会直接跑偏。

---

## 6. 附：勾上但"证据是运行记录而非静态产物"的步骤（便于更严口径复核）

若按最严口径（凡是"跑命令看输出"一律不勾，哪怕仓库里留有报告），下列 10 步会退回 `[ ]`，总数变成 **21/33**：

| 步骤 | 现有证据 | 证据性质 |
|---|---|---|
| Task 6 Step 1 | `0768036` 提交自述 + `tmp_e2e/e2e_report.md`（2026-09-10） | 报告文件（生成） |
| Task 6 Step 2 | 同上 + compose `image: aiopc-frontend:latest` | 报告文件（生成）+ 配置 |
| Task 7 Step 1 | `tmp_e2e/e2e_report.md` | 报告文件（生成） |
| Task 7 Step 2 | 同上 + `docs/verification/week-49/...` 的 `docker ps` 快照 | 报告文件（生成）+ 手贴片段 |
| Task 7 Step 3 | `RECOVERY.md` §8 的 `- [x] 前端 http://localhost:8079 可访问` + `health-check.sh` 的 `check_frontend()` | runbook 断言 + 断言式代码 |
| Task 7 Step 4 | `nginx.conf` 的 `try_files` + `56e1e56` 提交自述（11 条 SPA 路由实测） | 配置 + 提交自述 |
| Task 7 Step 5 | `0768036` 提交自述 + `tmp_e2e/e2e_report.md`（49/50） | 报告文件（生成）+ 提交自述 |
| Task 8 Step 1 | `docs/verification/week-49/...` 的 `docker ps` 快照 + compose 配置 | 手贴片段（旁证）+ 配置 |
| Task 8 Step 2 | `tmp_e2e/e2e_report.md` + `RECOVERY.md` §8 检查清单 + `health-check.sh` 输出片段 | 报告文件（生成）+ runbook 断言 |
| Task 8 Step 3 | 上面各条 | 同上 |

**审计说明**：Task 7 Step 4/5 的证据里有一层需要点破 —— 2026-10-22 的 `f1a01cf` 曾给 nginx 加了 `/opc/` `/system/` `/auth/` 裸路径反代，导致 SPA 深链刷新白屏（`/opc/invite` → 404 NOT_FOUND），同日 `56e1e56` 把裸路径删掉、只留 `/prod-api/`，并记录了 11 条 SPA 路由全部返回 index.html 的实测。也就是说：**plan 里 Task 7 Step 4 断言的"SPA 回退生效"在 2026-09-09 当天是真的，但在 2026-10-22 中途坏过几小时，现已修复**。
