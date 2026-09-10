-- =============================================================================
-- opc_agent_workflow_seed.sql
-- Task #3 Sub-task 3.4：注册「财务日报」工作流 finance_daily_report_v1
--
-- 目标库：OPC 业务库
-- 前置条件：
--   1. sql/opc_20260903.sql 已导入；
--   2. sql/migrations/V20260905__workflow_cron.sql 已执行（cron_expression 等 3 列存在）。
--
-- DAG 说明：WorkflowEngine v0.2 中 TOOL / CONDITION / LOOP 节点尚为 stub，
--          因此这里只用 LLM 节点，避免踩空实现。
--          n1 的输出通过 ${n1} 注入 n2 的 prompt。
-- 幂等：workflow_code 上有 uk_workflow_code，重复执行只刷新定义，不清零 run_count。
-- =============================================================================

INSERT INTO `opc_agent_workflow` (
    `workflow_code`, `name`, `company_id`, `definition_id`,
    `trigger_type`, `trigger_config`, `dag_json`, `enabled`,
    `cron_expression`, `timezone`, `status`, `create_by`, `create_time`, `remark`
) VALUES (
    'finance_daily_report_v1',
    '财务日报',
    NULL,
    NULL,
    'CRON',
    '{"cron":"0 0 9 * * ?","timezone":"Asia/Shanghai"}',
    '{"nodes":[{"id":"n1","type":"LLM","prompt":"你是企业财务助理。请汇总昨日的收支流水、待处理凭证数量与异常项，用简洁的要点列出。"},{"id":"n2","type":"LLM","prompt":"基于以下汇总内容生成一份面向管理者的财务日报，包含【关键数字】【风险提示】【今日建议】三个小节：\\n${n1}"}],"edges":[{"source":"n1","target":"n2"}]}',
    1,
    '0 0 9 * * ?',
    'Asia/Shanghai',
    'PUBLISHED',
    'system',
    NOW(),
    'Task #3 种子数据：每日 09:00 自动生成财务日报'
)
ON DUPLICATE KEY UPDATE
    `name`            = VALUES(`name`),
    `trigger_type`    = VALUES(`trigger_type`),
    `trigger_config`  = VALUES(`trigger_config`),
    `dag_json`        = VALUES(`dag_json`),
    `enabled`         = VALUES(`enabled`),
    `cron_expression` = VALUES(`cron_expression`),
    `timezone`        = VALUES(`timezone`),
    `status`          = VALUES(`status`),
    `update_by`       = 'system',
    `update_time`     = NOW();
