# 审计报告：2026-09-22-remote-ssh-opc-deploy

> **审计日期**：2026-10-29
> **被审 plan**：`docs/superpowers/plans/2026-09-22-remote-ssh-opc-deploy.md`（commit `e72b840`，2026-09-22，此后从未改动）
> **规约**：`/tmp/opc-plan-audit-rubric.md`
> **一句话目标**：把 OPC 全栈 22 容器（6 基建 + 3 平台 + 13 业务）通过一键 `bootstrap-remote.sh` 部署到 Rocky Linux 8.10 云 VM，并把本机 `backup-mysql-local.sh` 导出的 MySQL dump 迁过去。

## 0. 结论速览

**勾了 42 / 没勾 18 / 共 60。**

- 18 条没勾里，**没有一条是"东西没做出来"**。
  - 12 条卡在「仓库里查不到运行结果」（9 条 `bash -n` 语法校验 + 1 条 shellcheck + 1 条 dry-run `--help` + 1 条 Self-Review 复合项）。仓库里**没有 `.github/` 目录、没有测试报告文件、没有任何产物**，全仓库只有 plan 正文自己提到 shellcheck。
  - 6 条是 **Task 11 模板代码块里预勾的云 VM 实跑结论**，全部退回 `[ ]`（详见 §3.2）。
- 所有"写文件 / 写文档 / 提交"的实质步骤都有仓库级硬证据。

## 1. 关键证据

| 证据 | 内容 |
|---|---|
| 脚本存在 | `springboot3/deploy/scripts/backup-mysql-local.sh`（113 行）、`springboot3/deploy/scripts/bootstrap-remote.sh`（552 行） |
| 阶段实现 | `bootstrap-remote.sh` 里 `stage_0` … `stage_5` 全部是真实实现；`grep ': "placeholder"'` → **0 命中** |
| 退出码 | 脚本含 `exit 1 / 2 / 3 / 4`（第 444 / 458 / 472 行起），与 spec `docs/superpowers/specs/2026-09-22-remote-ssh-opc-deploy-design.md` §4.2 表格逐条一致 |
| 提交史 | W79 共 **17 个实现 commit** 落在 2026-09-22：<br>`cd694b9` `9b834dd` `5e1fb44`（Task 1，3 个）<br>`1467690` `ed2cedf` `7b9fd1c` `a9f9579` `7b140d6` `5f66e91` `235f6be` `e2f0911` `89337b1` `5e2cbb3` `932341e`（Task 2–8，11 个）<br>`49f0780`（Task 9）`fda7b5e`（Task 10）`588a1ca`（Task 11） |
| 文档 | `springboot3/deploy/RECOVERY.md` §15「Remote SSH Bootstrap (W79)」在第 618–736 行（8 个子节）；`.env.example` 尾部有「W79 Rocky 8.10 VM Bootstrap」块；`docs/verification/late/W79-REMOTE-SSH-DEPLOY-VERIFICATION.md` 存在 |
| push | `git branch -a --contains 588a1ca` → `remotes/origin/main`；`git status --short` 为空 |
| **云 VM 实跑** | **没有任何证据**。仓库自带的 `W79-REMOTE-SSH-DEPLOY-VERIFICATION.md` 第 4 行原文：「**状态**: ✅ 设计 + 计划 + 实施全部完成; VM 实跑需用户 SSH 验证」—— 报告里也没有任何一条 `- [x]` 结论 |

## 2. 逐 Task 结论

| Task | 步骤数 | 勾了几个 | 一句话结论 |
|---|---|---|---|
| Task 1 `backup-mysql-local.sh` | 6 | **5** | 脚本 113 行与 plan 代码块逐行对得上（骨架/前置检查/导出函数/main 全在），另有 2 个 fix commit；**只差 chmod +x**（git 里仍是 100644）和一次语法校验运行 |
| Task 2 `bootstrap-remote.sh` 骨架 | 7 | **6** | 脚本头/变量、log+fail+require、arg 解析+帮助、stage 调度骨架、main 循环全部落地；preflight 后来被 `ed2cedf` 重排（更合理） |
| Task 3 Stage 0 Pre-flight | 3 | **2** | `stage_0` 真实实现（Rocky 8.10 / 磁盘 / .env / 迁移包 / sha256 / 两个 LLM 键）与 plan 一致 |
| Task 4 Stage 1 Install Docker | 3 | **2** | `stage_1` 装 Docker CE（docker-ce repo + dnf + systemctl + compose 插件校验）已写 |
| Task 5 Stage 2 deps + clone | 3 | **2** | `stage_2`（git/python3/rsync/jq + 关 SELinux/firewalld + clone + 校验 compose.yml + 同步 .env）已写 |
| Task 6 Stage 3 起基建 | 3 | **2** | `stage_3`（compose up 6 基建 + 等 MySQL + 等 Nacos）已写；fix 把超时文案 60s→120s |
| Task 7 Stage 4 数据迁移 | 4 | **3** | `stage_4`（解压/校验 dump/幂等守卫/pre-restore 备份/restore 失败自动回滚 exit 2/推 Nacos/起业务）已写；**Step 2 的 exit 3 也落地**（脚本第 458 行 `docker compose up -d \|\| { warn ...; exit 3; }`） |
| Task 8 Stage 5 Verification | 3 | **2** | `stage_5` 是真实实现 + 5 项 smoke + exit 4，但 **plan 里的 5 条业务路径被换成了基建探针**（见 §4.2） |
| Task 9 `RECOVERY.md` §15 | 3 | **3** | §15 已写（8 子节 + 阶段表 + 失败恢复 + 端口转发 + 常见错误表）；`Last updated` 行在 `49f0780` 里改过（后被 2026-10-20 的 W80.1 覆盖，属正常演进） |
| Task 10 `.env.example` | 3 | **3** | W79 块已追加并提交；但 **两个 LLM key 留空，与 `stage_0` 的"非空"门槛冲突**（见 §4.1） |
| Task 11 VERIFICATION 报告 | 2（+6 内嵌） | **2** | 报告文件已建并提交，但内容**不是 plan 的模板**，是另一版 150 行报告，且如实写明"VM 实跑需用户 SSH 验证"；模板里预勾的 6 条 `[x]` 全部退回 |
| Task 12 收尾 | 5 | **2** | commit 干净 + push 有据（17 个 commit ≥ 期望的 12 个）；语法校验 / shellcheck / dry-run 三条查不到运行结果 |
| Self-Review Checklist | 9 | **8** | 8 条可 grep 核对；1 条把"bash 语法校验通过"和"commit 干净 + push 成功"捆在一起，前半段无据 |
| **合计** | **60** | **42** | |

> 行号均指**审计后**的 plan 文件。

## 3. 所有没勾的步骤（18 条）及理由

### 3.1 「仓库里查不到运行结果」（12 条）

plan 里凡是 "Run: `bash -n …` && echo `SYNTAX OK`" / "Expected: 两条 SYNTAX OK" / "Expected: shellcheck 0 error" / "Expected: bootstrap 输出 … 帮助" 的，措辞都是"跑一下看输出"。仓库里没有 CI 配置、没有测试报告文件、没有产物，**按规约一律不准勾**。

| 行 | 步骤 | 理由 |
|---|---|---|
| 174 | Task 1 Step 5「加执行权限 + bash 语法校验」 | **git 里 `backup-mysql-local.sh` 仍是 100644，chmod +x 这一半确实没做**；bash -n 的运行结果仓库里查不到 |
| 417 | Task 2 Step 6「bash 语法校验」 | 仓库里查不到运行结果 |
| 490 | Task 3 Step 2「bash 语法校验」 | 仓库里查不到运行结果 |
| 549 | Task 4 Step 2「bash 语法校验」 | 仓库里查不到运行结果 |
| 628 | Task 5 Step 2「bash 语法校验」 | 仓库里查不到运行结果 |
| 710 | Task 6 Step 2「bash 语法校验」 | 仓库里查不到运行结果 |
| 812 | Task 7 Step 3「bash 语法校验」 | 仓库里查不到运行结果 |
| 898 | Task 8 Step 2「bash 语法校验」 | 仓库里查不到运行结果 |
| 1245 | Task 12 Step 2「跑最终 bash 语法校验」 | 仓库里查不到运行结果（`W79-REMOTE-SSH-DEPLOY-VERIFICATION.md` §2 表格自称 SYNTAX_OK，但那是手写的自述，不是产物） |
| 1255 | Task 12 Step 3「shellcheck 静态分析」 | 仓库里查不到运行结果（`grep -rl shellcheck` 全仓库只命中 plan 自己） |
| 1269 | Task 12 Step 4「dry-run --help 校验两个脚本都正常」 | 仓库里查不到运行结果（`cmd_help` 的 heredoc 确实在代码里，但"跑起来输出对不对"是运行态） |
| 1296 | Self-Review「Task 12: 12 个 commit 干净 + bash 语法校验通过 + push 成功」 | commit 数和 push 有据，但「bash 语法校验通过」在仓库里查不到运行结果 —— 复合项里裹着无据的一句，整条退回 |

### 3.2 Task 11 模板里的 6 条云 VM 实跑结论（6 条，全部由 `[x]` 退回）

这 6 行位于 Task 11 Step 1 的 markdown 代码块内，是"填进 VERIFICATION 报告的结论清单"。它们原本就是 `[x]`，但**没有一条有证据**：

| 行 | 原 checkbox | 退回理由 |
|---|---|---|
| 1208 | 21 个容器 Up | 云 VM 实跑没做过，仓库里查不到容器清单的运行结果 |
| 1209 | health-check 通过 (XX/XX PASS) | 仓库里查不到在目标 VM 上跑通 health-check 的运行结果 |
| 1210 | 5 项 smoke test 全绿 | 仓库里查不到 5 项 smoke test 全绿的运行结果 |
| 1211 | AI 接口预期失败 (placeholder key) | 仓库里查不到 AI 接口 401 验证的运行结果 |
| 1212 | MySQL 数据迁移成功 (行数与 dump 前一致) | 仓库里查不到 MySQL 数据迁移成功的运行结果 |
| 1213 | SSH 端口转发可访问前端/Nacos | 仓库里查不到 SSH 端口转发可访问的运行结果 |

**反证**：W79 自己写的 `docs/verification/late/W79-REMOTE-SSH-DEPLOY-VERIFICATION.md` 第 4 行是：
> **状态**: ✅ 设计 + 计划 + 实施全部完成; VM 实跑需用户 SSH 验证

那份报告里**一条 `- [x]` 结论都没有**，而且 §9「下一步（用户操作）」把 7 步全列给用户自己去跑。也就是说：**代码交付完了，VM 一次都没跑过**。plan 模板里预勾的 6 个 `[x]` 与之直接冲突。

## 4. 做了一半 / 明显有偏差的地方

### 4.1 Task 10 Step 2：`.env.example` 的 placeholder 值留空，会卡死 Stage 0

- plan 要求追加 `DEEPSEEK_API_KEY=sk-placeholder-deepseek` / `MINIMAX_API_KEY=ey-placeholder`。
- 实际 `fda7b5e` 把两个键改成了**空值**（`DEEPSEEK_API_KEY=` / `MINIMAX_API_KEY=`）。
- 而 `bootstrap-remote.sh` 的 `stage_0` 是硬门槛：
  ```bash
  for key in DEEPSEEK_API_KEY MINIMAX_API_KEY; do
    val="$(grep -E "^${key}=" "${PROJECT_ROOT}/.env" | cut -d= -f2- || true)"
    if [[ -z "${val}" ]]; then fail ".env 缺 ${key}"; fi
  done
  ```
- **后果**：用户照 `.env.example` 原样 `cp .env.example .env`，Stage 0 直接 `fail`，bootstrap 起不来。plan 原本设计的 placeholder 值（`sk-placeholder-deepseek`）恰好能过这道门 —— 实现把它改成空值，等于自己给自己挖了个坑。**这条是本 plan 里唯一一处"实现偏离 plan 且偏离得不巧"的实质缺陷。**

### 4.2 Task 8 Step 1：5 项 smoke test 换了内容

- plan / spec §5.2 要求打 5 条业务路径：`/opc/erp/product/list`、`/opc/crm/customer`、`/opc/hr/job/list`、`/opc/community/module/list`、`/opc/notification/inbox/unread-count`，外加 `/opc/ai-core/chat` 预期 401。
- 实际 `stage_5` 的 5 项 smoke 是：网关 `/login` → Nacos `/v1/cs/health` → MySQL `opc_*` 表数 → 前端 `/` → 网关 `/actuator/health`。**6 条业务路径一条都没进脚本**（逐条 grep 全部 NOT FOUND）。
- 换成基建探针未必是错（RECOVERY.md §16 记录了 W80.1 上线 `companyId` 后旧 token 打业务路径会 400），但这是**实现与 plan/spec 的实质偏离**，不能算"照着做完了"。

### 4.3 plan 自身的内部数字不一致

- Goal 写「22 容器: 6 基建 + 3 平台 + 13 业务」；
- Task 11 模板里写「预期: 21 个 (6 基建 + 3 平台 + 12 OPC 业务)」；
- W79 时点 `springboot3/deploy/docker-compose.yml` 实有 **28 个 service**（含 elasticsearch / prometheus / grafana / skywalking-oap / skywalking-ui / aiopc-insight / aiopc-net）。
- 三个数字互不相同，plan 本身没对齐。

### 4.4 Task 12 Step 1 的期望反而偏保守

plan 期望「12 个新 commit」，实际 W79 落地 **17 个**实现 commit（多出来的是每个阶段的 fix commit），另加 plan 自身 1 个、spec 3 个。数量超预期，不是问题。

## 5. 整体结论：这份 plan 靠不靠谱？

**靠得住，而且比它自己表现出来的样子靠谱得多 —— 但只有"实现"这一半靠得住，"验证"那一半从来没发生过。**

- **真做了的比例**：60 条 checkbox 里 42 条（70%）有仓库级硬证据。如果只看"产出类"步骤（写脚本、写文档、提交），比例接近 **100%** —— 两个脚本、RECOVERY §15、.env.example、VERIFICATION 报告 5 个交付物全部在仓库里，17 个 commit 全部落在 2026-09-22 并已 push 到 `origin/main`。**没有一个 Task 是整体没做的。**
- **做了一半的**：只有两处 —— Task 8（smoke test 换了内容，业务路径没采纳）、Task 10（placeholder 值留空，跟 `stage_0` 的门槛自相矛盾，会让用户第一次跑就卡住）。前者是取舍，后者是实打实的 bug。
- **没做的**：**云 VM 实跑从来没做过**。这不是疏漏，是设计如此（spec §0.1「执行模型」就写明"用户自己 SSH 进 VM 跑脚本，Claude 不需要 SSH 凭据"），实现者也很诚实 —— VERIFICATION 报告第 4 行直接写"VM 实跑需用户 SSH 验证"。
- **有没有"声称做了但其实没有"的条目**：**有，且只有一处** —— Task 11 模板代码块里预勾的 6 个 `- [x]`（21 容器 Up / health-check 通过 / 5 smoke 全绿 / AI 401 / MySQL 迁移成功 / SSH 端口转发可访问）。它们是全 plan 里唯一 6 个"已经打勾"的框，也是全 plan 里唯一 6 个**纯属虚构**的框：它们把一份"待执行的验证清单"写成了"已完成的验证结论"。这正是本轮审计要清掉的东西 —— 已全部退回 `[ ]` 并附理由。
- **一句话**：**代码交付是真的，"已验证"是假的**。谁再拿这份 plan 的 checkbox 当"云 VM 部署已完成"的依据，谁就会踩坑 —— 那个 VM 上可能一个容器都还没起来过。
