# OPC 验证报告索引

> 48 个 `OPC-W*-VERIFICATION-*.md` + 1 个 M4 部署报告。按周次 + 里程碑归档。

## 周次速览

| 周次 | 范围 | 文件数 |
|------|------|-------|
| [W1](#week-1) | Helm / Nacos / 邀请 / 移动端 / 红队 / 税务 | 17 |
| [W2](#week-2) | service 层 (bankflow / billing / finance / invitation / voucher) | 6 |
| [W3](#week-3) | controller 层 (code-generator / tax-report / user) + user-profile | 4 |
| [W4](#week-4) | workflow-trigger | 1 |
| [W5](#week-5) | mutation testing + 修 P0 mutation + verify | 3 |
| [W6](#week-6) | WebMvc test | 1 |
| [W7](#week-7) | OPC 异常继承 | 1 |
| [W8](#week-8) | HTTP coverage complete | 1 |
| [W9](#week-9) | Gateway filter | 1 |
| [W10](#week-10) | all-remaining (其余单元测试补齐) | 1 |
| [W11](#week-11) | 前端单测 (W11) | 1 |
| [W12](#week-12) | 前端单测 (W12) | 1 |
| [W49](#week-49) | opc-notification 服务全栈交付 | 7 |
| [late/](#late) | W19-W22 api 契约 + SFC Logo | 1 |
| [M4](#m4-insight) | Insight MVP 部署 + 验证 | 2 |

---

## Week 1

17 个文件, 覆盖 Helm Chart、Nacos 配置、邀请功能、移动端适配、AI 红队。

| 文件 | 主题 |
|------|------|
| [OPC-W1-VERIFICATION-helm-chart.md](./week-1/OPC-W1-VERIFICATION-helm-chart.md) | Helm chart 结构 (`helm lint` + `helm template`) |
| [OPC-W1-VERIFICATION-helm-ci.md](./week-1/OPC-W1-VERIFICATION-helm-ci.md) | Helm CI 流水线 |
| [OPC-W1-VERIFICATION-helm-templates-v2.md](./week-1/OPC-W1-VERIFICATION-helm-templates-v2.md) | Helm templates 第二版 (dev/staging/prod 拆分) |
| [OPC-W1-VERIFICATION-invitation-api-7.1.md](./week-1/OPC-W1-VERIFICATION-invitation-api-7.1.md) | 邀请码生成/接受 API |
| [OPC-W1-VERIFICATION-invite-flow.md](./week-1/OPC-W1-VERIFICATION-invite-flow.md) | 邀请完整流程 |
| [OPC-W1-VERIFICATION-invite-landing-7.2.md](./week-1/OPC-W1-VERIFICATION-invite-landing-7.2.md) | 邀请落地页 |
| [OPC-W1-VERIFICATION-invite-poster-7.3.md](./week-1/OPC-W1-VERIFICATION-invite-poster-7.3.md) | 邀请分享海报 (Canvas 2D + QR) |
| [OPC-W1-VERIFICATION-mobile-drawer.md](./week-1/OPC-W1-VERIFICATION-mobile-drawer.md) | 移动端抽屉 (WIDTH=768) |
| [OPC-W1-VERIFICATION-nacos-prod.md](./week-1/OPC-W1-VERIFICATION-nacos-prod.md) | Nacos 6 个 prod 配置文件 |
| [OPC-W1-VERIFICATION-responsive-scss.md](./week-1/OPC-W1-VERIFICATION-responsive-scss.md) | responsive.scss (xs/sm/md/lg + touch-target) |
| [OPC-W1-VERIFICATION-responsive-table.md](./week-1/OPC-W1-VERIFICATION-responsive-table.md) | ResponsiveTable.vue (≥768px 表格 / <768px 卡片列表) |
| [OPC-W1-VERIFICATION-task1-eval-set.md](./week-1/OPC-W1-VERIFICATION-task1-eval-set.md) | Task #1 AI 评测集扩展 (10→100) |
| [OPC-W1-VERIFICATION-task3-workflow-cron.md](./week-1/OPC-W1-VERIFICATION-task3-workflow-cron.md) | Task #3 后端 B cron 触发器 |
| [OPC-W1-VERIFICATION-task8-redteam.md](./week-1/OPC-W1-VERIFICATION-task8-redteam.md) | Task #8 AI 红队测试 |
| [OPC-W1-VERIFICATION-tax-report-controller.md](./week-1/OPC-W1-VERIFICATION-tax-report-controller.md) | 税务报表 controller |
| [OPC-W1-VERIFICATION-tax-report-service.md](./week-1/OPC-W1-VERIFICATION-tax-report-service.md) | 税务报表 service |
| [OPC-W1-VERIFICATION-values-split.md](./week-1/OPC-W1-VERIFICATION-values-split.md) | Helm values dev/staging/prod 拆分 |

## Week 2

6 个 service 层实现验证。

| 文件 | 主题 |
|------|------|
| [OPC-W2-VERIFICATION-bankflow-service.md](./week-2/OPC-W2-VERIFICATION-bankflow-service.md) | 银行流水 service |
| [OPC-W2-VERIFICATION-billing-controller.md](./week-2/OPC-W2-VERIFICATION-billing-controller.md) | 计费 controller |
| [OPC-W2-VERIFICATION-finance-controller.md](./week-2/OPC-W2-VERIFICATION-finance-controller.md) | 财务 controller |
| [OPC-W2-VERIFICATION-invitation-controller.md](./week-2/OPC-W2-VERIFICATION-invitation-controller.md) | 邀请 controller |
| [OPC-W2-VERIFICATION-invitation-service.md](./week-2/OPC-W2-VERIFICATION-invitation-service.md) | 邀请 service |
| [OPC-W2-VERIFICATION-voucher-service.md](./week-2/OPC-W2-VERIFICATION-voucher-service.md) | 凭证 service |

## Week 3

4 个 controller + user-profile。

| 文件 | 主题 |
|------|------|
| [OPC-W3-VERIFICATION-code-generator.md](./week-3/OPC-W3-VERIFICATION-code-generator.md) | 代码生成器 |
| [OPC-W3-VERIFICATION-tax-report-controller.md](./week-3/OPC-W3-VERIFICATION-tax-report-controller.md) | 税务报表 controller (W3 重做) |
| [OPC-W3-VERIFICATION-user-controller.md](./week-3/OPC-W3-VERIFICATION-user-controller.md) | 用户 controller |
| [OPC-W3-VERIFICATION-user-profile-service.md](./week-3/OPC-W3-VERIFICATION-user-profile-service.md) | 用户档案 service |

## Week 4

| 文件 | 主题 |
|------|------|
| [OPC-W4-VERIFICATION-workflow-trigger-controller.md](./week-4/OPC-W4-VERIFICATION-workflow-trigger-controller.md) | workflow 触发 controller |

## Week 5

mutation testing 三部曲。

| 文件 | 主题 |
|------|------|
| [OPC-W5-VERIFICATION-mutation-testing.md](./week-5/OPC-W5-VERIFICATION-mutation-testing.md) | mutation testing 静态分析 (W2-W4 共 12 测试类 / 202 invocations) |
| [OPC-W5.1-VERIFICATION-p0-mutation-fix.md](./week-5/OPC-W5.1-VERIFICATION-p0-mutation-fix.md) | 修 P0 级 mutation |
| [OPC-W5.2-VERIFICATION-verify-no-more-interactions.md](./week-5/OPC-W5.2-VERIFICATION-verify-no-more-interactions.md) | 验证无多余 interaction |

## Week 6

| 文件 | 主题 |
|------|------|
| [OPC-W6-VERIFICATION-webmvc-test.md](./week-6/OPC-W6-VERIFICATION-webmvc-test.md) | WebMvc 测试 |

## Week 7

| 文件 | 主题 |
|------|------|
| [OPC-W7-VERIFICATION-opc-exception-inheritance.md](./week-7/OPC-W7-VERIFICATION-opc-exception-inheritance.md) | OPC 异常继承体系 |

## Week 8

| 文件 | 主题 |
|------|------|
| [OPC-W8-VERIFICATION-http-coverage-complete.md](./week-8/OPC-W8-VERIFICATION-http-coverage-complete.md) | HTTP 覆盖率补齐 |

## Week 9

| 文件 | 主题 |
|------|------|
| [OPC-W9-VERIFICATION-gateway-filter.md](./week-9/OPC-W9-VERIFICATION-gateway-filter.md) | Gateway filter (AuthFilter / BlackListUrl / ValidateCode / Xss) |

## Week 10

| 文件 | 主题 |
|------|------|
| [OPC-W10-VERIFICATION-all-remaining.md](./week-10/OPC-W10-VERIFICATION-all-remaining.md) | 剩余单元测试批量补齐 |

## Week 11

| 文件 | 主题 |
|------|------|
| [OPC-W11-VERIFICATION-frontend-tests.md](./week-11/OPC-W11-VERIFICATION-frontend-tests.md) | 前端单测 (W11) |

## Week 12

| 文件 | 主题 |
|------|------|
| [OPC-W12-VERIFICATION-frontend-tests.md](./week-12/OPC-W12-VERIFICATION-frontend-tests.md) | 前端单测 (W12) |

## Week 49

7 个文件, 覆盖 opc-notification 服务全栈交付 (后端 41 Java + 前端 Inbox + Helm + E2E + RECOVERY)。

| 文件 | 主题 |
|------|------|
| [opc-notification-task13-verify.md](./week-49/opc-notification-task13-verify.md) | Task 13: Nacos push + 容器启动 + smoke |
| [opc-notification-task14-health-check.md](./week-49/opc-notification-task14-health-check.md) | Task 14: health-check.sh 6 项 + 3 bug fixes (Nacos name / path prefix / static 判定) |
| [opc-notification-task15-recovery.md](./week-49/opc-notification-task15-recovery.md) | Task 15: RECOVERY.md §10 + import-dev.sh NAMES |
| [opc-notification-task16-helm.md](./week-49/opc-notification-task16-helm.md) | Task 16: Helm chart notification 服务 (4 values × 2 templates) |
| [opc-notification-task17-frontend.md](./week-49/opc-notification-task17-frontend.md) | Task 17: 前端 Inbox.vue + WS composable + API |
| [opc-notification-task18-e2e.md](./week-49/opc-notification-task18-e2e.md) | Task 18: notification_e2e.py 11/11 PASS |
| [opc-notification-week49-verification.md](./week-49/opc-notification-week49-verification.md) | W49 验收报告 (Task 19) |

## Late

W19-W22 批次验证 (api 契约 + SFC Logo)。

| 文件 | 主题 |
|------|------|
| [OPC-W19-W22-VERIFICATION-api-and-sfc.md](./late/OPC-W19-W22-VERIFICATION-api-and-sfc.md) | api 契约 + SFC Logo 单测批次验证 |

## M4 Insight

Insight MVP 部署 + 验证。

| 文件 | 主题 |
|------|------|
| [OPC-M4-DEPLOYMENT-REPORT.md](./milestones/m4-insight/OPC-M4-DEPLOYMENT-REPORT.md) | M4 Insight MVP 部署报告 |
| [OPC-M4-VERIFICATION-insight-mvp.md](./milestones/m4-insight/OPC-M4-VERIFICATION-insight-mvp.md) | M4 Insight MVP 验证 |

---

## 命名约定

```
OPC-<milestone|>-<scope>-VERIFICATION-<topic>.md

milestone: W1 ~ W12 / W19-W22 / M1 ~ M4
scope    : 任务编号, 如 task1/task3/task8, 或子模块 (invitation-api-7.1)
topic    : 简短描述 (kebab-case)
```

例: `OPC-W1-VERIFICATION-invitation-api-7.1.md` = W1 第 7.1 子任务 (邀请 API) 验证。
