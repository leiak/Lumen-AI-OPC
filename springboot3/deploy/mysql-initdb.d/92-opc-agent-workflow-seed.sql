-- =============================================================================
-- opc_agent_workflow_seed.sql
-- Task #3 Sub-task 3.4：注册「财务日报」工作流 finance_daily_report_v1
--
-- 目标库：OPC 业务库
-- 前置条件：
--   1. sql/opc_20260903.sql 已导入；
--   2. sql/migrations/V20260905__workflow_cron.sql 已执行（cron_expression 等 3 列存在）。
--
-- DAG 说明：n1 是 TOOL 节点（真的去查 opc_finance_bank_flow / opc_finance_voucher），
--          n2 是 LLM 节点（只负责把 n1 查出来的**真实数据**写成日报），
--          最后 persist 落进 opc_insight_daily_report。
--          n1 的输出通过 ${n1} 注入 n2 的 prompt。
--          （这里原来写着「TOOL / CONDITION / LOOP 节点尚为 stub，因此这里只用
--            LLM 节点」—— 那句话现在是假的：TOOL 节点已经真的调工具了。）
--
-- company_id = 2（不是 NULL）：
--   这个工作流的产出是**某一家企业**的财务日报 —— prompt 里明写着「汇总昨日的
--   收支流水、待处理凭证数量与异常项」，那些都是公司口径的数据。而 Quartz 触发时
--   （WorkflowCronJob → RemoteWorkflowService.trigger）**不带 companyId**，
--   归属只能从工作流行本身取。
--   以前这里是 NULL → 一路兜成 0（= 系统）→ 这条工作流跑掉的 token/成本
--   记在 company_id=0 上，而 opc-finance 是按 company_id 过滤的（WHERE company_id=#{id}），
--   于是**这笔钱在任何一家公司的账单里都看不见** —— 平台白花钱、客户看不到账。
--   现在指向**公司 2** —— 全部财务数据都在公司 2（bank_flow 6 行 / voucher 5 行，
--   公司 1 是 0 行）：挂在公司 1 的话 TOOL 节点恒查空集，日报永远是「无流水」，
--   分不清「链路是真的」和「工具完全坏掉」；用量也会落进公司 2 的 token-usage 面板。
--   （NULL 保留给真正的系统模板 —— 平台自用、不记到客户头上的工作流。）
--
-- cron_expression = '0 0 1 * * ?'：
--   **UTC 01:00 = 北京 09:00**。容器时区是 UTC，Quartz 的 CronScheduleBuilder
--   不带任何时区 → 直接用容器 TZ。原来写的 '0 0 9 * * ?' 实际是**北京 17:00**，
--   README 说的「每天 09:00」是假的。
--   timezone 列一并改成 'UTC'：它**不是调度时区，是展示时区**（nextRunAt 按它算
--   「下次执行时间」），留着 Asia/Shanghai 的话页面显示会比真实触发晚 16 小时。
--
-- 幂等：workflow_code 上有 uk_workflow_code，重复执行只刷新定义，不清零 run_count。
-- =============================================================================

INSERT INTO `opc_agent_workflow` (
    `workflow_code`, `name`, `company_id`, `definition_id`,
    `trigger_type`, `trigger_config`, `dag_json`, `enabled`,
    `cron_expression`, `timezone`, `status`, `create_by`, `create_time`, `remark`
) VALUES (
    'finance_daily_report_v1',
    '财务日报',
    2,
    NULL,
    'CRON',
    '{"cron":"0 0 1 * * ?","timezone":"UTC"}',
    '{"nodes":[{"id":"n1","type":"TOOL","tools":["query_bank_flow","query_voucher"],"args":{},"label":"取真实流水与凭证"},{"id":"n2","type":"LLM","stepTimeoutSeconds":60,"prompt":"你是企业财务助理。下面是系统从业务表里查出来的真实数据（JSON）。请据此写一份面向管理者的财务日报，包含【关键数字】【风险提示】【今日建议】三个小节。\\n\\n硬性要求：1) 所有数字必须直接引用给定数据里的值，不得推算或补充；2) 如果数据里昨日（${yesterday}）没有流水，必须明确写「昨日无资金流水」，不得写成「营收 0 元，运营平稳」；3) 不得出现给定数据里没有的数字。\\n\\n真实数据：\\n${n1}"}],"edges":[{"source":"n1","target":"n2"}],"persist":{"target":"daily_report","date":"${yesterday}","kpiFrom":"n1","summaryFrom":"n2"}}',
    1,
    '0 0 1 * * ?',
    'UTC',
    'PUBLISHED',
    'system',
    NOW(),
    'Task #3 种子数据：每日 09:00 自动生成财务日报'
)
ON DUPLICATE KEY UPDATE
    `name`            = VALUES(`name`),
    -- company_id 也要跟着刷：种子声明的值就是事实，
    -- 不刷的话「种子说 2、库里是 1」会长期分叉，而分叉出来的正是「工具恒查空集」。
    `company_id`      = VALUES(`company_id`),
    `trigger_type`    = VALUES(`trigger_type`),
    `trigger_config`  = VALUES(`trigger_config`),
    `dag_json`        = VALUES(`dag_json`),
    `enabled`         = VALUES(`enabled`),
    `cron_expression` = VALUES(`cron_expression`),
    `timezone`        = VALUES(`timezone`),
    `status`          = VALUES(`status`),
    `update_by`       = 'system',
    `update_time`     = NOW();
