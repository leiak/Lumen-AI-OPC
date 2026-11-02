# OPC 项目文档总索引

> 本目录按主题归类项目所有文档。验证报告 (`OPC-W*-VERIFICATION-*.md` ~40 个) 集中在 [`verification/`](./verification/README.md)。

## 快速入口

| 我想看... | 文档 |
|----------|------|
| **怎么跑 / 踩过的坑** | [`CLAUDE.md`](../CLAUDE.md) ← 先看这页 |
| **功能是不是真的在管事** | `bash deploy.sh health`（25 节 / 184 项断言）|
| 项目从 0 到 1 是什么 | [`init.md`](../init.md) |
| 13 周 MVP 怎么交付 | [`OPC-MVP-DELIVERY.md`](../OPC-MVP-DELIVERY.md) |
| W1 8 项任务的拆分 | [`OPC-W1-TASK-BREAKDOWN.md`](../OPC-W1-TASK-BREAKDOWN.md) |
| 邀请码怎么生成/接受/流转 | [`OPC-INVITATION-FLOW.md`](../OPC-INVITATION-FLOW.md) |
| AI 红队 + PromptGuard 战果 | [`OPC-SECURITY-REPORT-v0.1.md`](../OPC-SECURITY-REPORT-v0.1.md) |
| 怎么部署 / 怎么恢复 | [`springboot3/deploy/RECOVERY.md`](../springboot3/deploy/RECOVERY.md) |
| M4 Insight MVP 验收 | [`docs/verification/milestones/m4-insight/`](./verification/milestones/m4-insight/) |
| 历史验证报告索引 | [`docs/verification/README.md`](./verification/README.md) |
| 实施计划 (superpowers) | [`docs/plans/`](./superpowers/plans/) |
| 设计 spec (superpowers) | [`docs/specs/`](./superpowers/specs/) |

---

## 1. 项目根级文档 (`./`)

| 文件 | 主题 | 说明 |
|------|------|------|
| `README.md` | 项目门面 | 仓库入口 |
| `CLAUDE.md` | **项目说明 + 12 条踩过的坑** | 「不说假话」这条规矩写在这里 |
| `init.md` | 项目规格 | 13 周 MVP 原始需求, 业务场景/技术选型 |
| `OPC-MVP-DELIVERY.md` | 交付总览 | 13 周 MVP 落地清单 + 7 人团队接手指南 |
| `OPC-W1-TASK-BREAKDOWN.md` | W1 任务拆分 | 8 Task / 25 sub-task, W1 迭代排期 |
| `OPC-INVITATION-FLOW.md` | 邀请落地页 | Task #7 实现总览 (17 文件) |
| `OPC-SECURITY-REPORT-v0.1.md` | 安全评测 | 红队 / PromptGuard, ASR 30%→3.3% |

## 2. 验证报告 (`docs/verification/`)

**~40 个 `OPC-W*-VERIFICATION-*.md` + 1 个 M4**, 按周次 + 里程碑归档。

→ 详见 [`docs/verification/README.md`](./verification/README.md)

| 周次 | 文件数 | 主题 |
|------|-------|------|
| W1 | 17 | Helm/Nacos/邀请/移动端适配/红队/税务 等 |
| W2-W4 | 11 | service/controller 层实现验证 |
| W5-W9 | 8 | mutation testing / WebMvc / 异常 / HTTP coverage / Gateway filter |
| W10-W12 | 3 | 前端单测批量补齐 |
| W19-W22 (`late/`) | 1 | api 契约 + SFC Logo 批次验证 |
| M4 Insight (`milestones/`) | 2 | Insight MVP 部署 + 验证 |

## 3. 后端 (`springboot3/`)

| 文件 | 主题 |
|------|------|
| `springboot3/README.md` | 后端基座 README（RuoYi 上游原文 + OPC 定制说明） |
| `springboot3/deploy/README.md` | Docker 部署 README（31 服务清单 / **健康检查 25 节** / rebuild 入口） |
| `springboot3/deploy/RECOVERY.md` | 灾难恢复 + 各服务 runbook（17 节） |
| `springboot3/deploy/scripts/health-check.sh` | **25 节 / 184 项断言** —— 唯一被放行的线上验证入口 |
| `springboot3/deploy/nacos/README.md` | Nacos 配置操作（`tenant=` 参数陷阱 / 限流规则真相源） |
| `springboot3/deploy/helm/opc/README.md` | Helm chart README |
| `springboot3/deploy/runbooks/incident-response.md` | F-01..F-07 故障手册 |

## 4. 前端 (`vue3-typescript/`)

| 文件 | 主题 |
|------|------|
| `vue3-typescript/README.md` | Vue 3 + TS 前端基座 README |

## 5. 实施计划 + 设计 spec (`docs/superpowers/`)

| 文件 | 主题 |
|------|------|
| `plans/` | **9 份实施计划**（2026-09-08 ~ 2026-10-20）|
| `plans/audit/` | **9 份逐条审计报告 + 总索引** —— checkbox 已核实（565 勾 / 175 明确写没做）|
| `specs/` | 11 份设计 spec |

> ⚠️ **别把计划里没勾的 checkbox 当待办队列** —— 175 个没勾里有 127 个只是
> 「跑一次看输出」，证据不在仓库里。判定口径见 `plans/audit/README.md`。

## 6. E2E 测试报告 (`tmp_e2e/`)

| 文件 | 主题 |
|------|------|
| `tmp_e2e/e2e_report.md` | 端到端测试报告 (临时) |
| `tmp_e2e/e2e_all.py` | E2E 测试脚本 |
| `tmp_e2e/hire_req.json` | 测试 fixture |

---

## 文档维护约定

| 变更 | 同步到 |
|------|--------|
| 加 OPC 微服务 / 新端口 | `docker-compose.yml` + 网关 `application.yml` + **`nacos/sentinel-ruoyi-gateway.json` 加一条规则** + `springboot3/deploy/README.md` 服务清单 + 根 `README.md` 模块布局 + `RECOVERY.md` §1 |
| **加一条健康检查断言** | `scripts/health-check.sh`（加 `ok()`/`nok()` + `main()` 里加 `step N check_xxx`）+ `deploy/README.md`「健康检查的 25 节」表 |
| **修一个「说假话」的地方** | 根 `README.md`（说清现状）+ `CLAUDE.md` §四（记成一条坑）+ 健康检查（加一条会红的断言）|
| W*N* 完成验证 | 新增 `docs/verification/week-N/OPC-W*N*-VERIFICATION-*.md` + 在 `docs/verification/README.md` 索引 |
| 加新 OPC 里程碑 | `docs/verification/milestones/<name>/` 子目录 |
| 加新部署脚本 | `springboot3/deploy/RECOVERY.md` |
| 加新 AI 安全策略 | `OPC-SECURITY-REPORT-v*.md` (新建版本号) |
| 改 Nacos 配置 / 限流规则 | `deploy/nacos/*.yml` 或 `sentinel-ruoyi-gateway.json` + `import-dev.sh` 重推 |
