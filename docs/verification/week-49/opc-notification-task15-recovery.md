# Task 15: RECOVERY.md opc-notification section

Date: 2026-09-11

## Changes

- Added §10 "opc-notification 服务 (9310)" to RECOVERY.md
- Added `opc-notification-dev.yml` to `nacos/import-dev.sh` NAMES array

## Sections Covered (in RECOVERY.md §10)

- Service purpose (统一通知中心 / 邮件 / 短信 / 站内信 / WebSocket)
- Port: 9310
- Tables: `opc_notification_email_log` / `opc_notification_sms_log` / `opc_notification_inbox` / `opc_notification_template` (前缀修正, 实际为 `opc_notification_*` 而非 `opc_*`)
- Schema files: `08-opc-notification-schema.sql` + `94-opc-notification-template-seed.sql`
- Nacos config DataID: `opc-notification-dev.yml` / `opc-notification-prod.yml`
- Build + start commands (with `-Dmaven.test.skip=true` workaround)
- Known caveats (mail/sms provider NoOp, gateway route, thin jar `dependency:copy-dependencies`)
- Verification: `health-check.sh` 28/28 PASS
- Smoke test: login + `/opc/notification/inbox` + `/opc/notification/inbox/unread-count`

## Verification

- RECOVERY.md renders correctly (新增 §10, 紧跟 §9 文档维护)
- import-dev.sh now pushes opc-notification-dev.yml:
  ```
  ==================================================
  OPC dev Nacos 配置批量导入
    地址   : http://127.0.0.1:8848
    命名空间: opc-dev
    文件   : 2
  ==================================================
  [INFO] 导入 application-dev.yml (group=DEFAULT_GROUP, namespace=opc-dev)
  [OK]   application-dev.yml
  [INFO] 导入 opc-notification-dev.yml (group=DEFAULT_GROUP, namespace=opc-dev)
  [OK]   opc-notification-dev.yml
  --------------------------------------------------
  导入完成，失败数：0 / 2

  验证阶段：
    [OK  ] application-dev.yml (md5=bd3db90d48, nacos=2481B repo=2481B)
    [OK  ] opc-notification-dev.yml (md5=58ce4defdc, nacos=1697B repo=1697B)
  ```
- Commit: `b7cd868 docs(notification): Task 15 — RECOVERY.md section + import-dev.sh NAMES fix`
- Pushed: `96833c1..b7cd868  main -> main`

## Notes

- 任务描述中表格名 `opc_email_log` 等为简写, 实际为 `opc_notification_email_log`, 已在文档中纠正
- gateway 路由 `opc-notification` → `http://aiopc-notification:9310` 已确认存在于 `ruoyi-gateway/src/main/resources/application.yml:87-88`
- "6.7" 任务编号假设不存在, RECOVERY.md 当前结构 §1-§9 均不包含 per-service 详细文档; 故新增 §10 作为首个 OPC 服务专用 section, 为未来 opc-insight 等服务预留位置
