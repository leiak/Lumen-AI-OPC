-- =============================================================================
-- sys_job_workflow_seed.sql
-- Task #3 Sub-task 3.1：向 Quartz 调度表注册「财务日报」定时任务
--
-- 目标库：RuoYi 系统库（sys_job 所在的 schema）
-- 前置条件：sql/quartz.sql + sql/ry_20260417.sql 已导入（sys_job / QRTZ_* 表存在）。
--
-- 字段说明：
--   invoke_target   由 JobInvokeUtil 反射解析；bean 名 workflowCronJob 必须位于
--                   com.ruoyi.job.task 包下（Constants.JOB_WHITELIST_STR 白名单）。
--                   SQL 字符串里的单引号需转义成两个单引号。
--   misfire_policy  3 = 放弃执行（服务重启错过的日报不补跑，避免堆积）
--   concurrent      1 = 禁止并发（与服务端 30 秒去重窗口配合，双保险）
--   status          0 = 正常（导入后立即生效）
--
-- 幂等：sys_job 无 invoke_target 唯一键，这里用 NOT EXISTS 保证可重复执行。
-- =============================================================================

INSERT INTO `sys_job` (
    `job_name`, `job_group`, `invoke_target`, `cron_expression`,
    `misfire_policy`, `concurrent`, `status`, `create_by`, `create_time`, `remark`
)
SELECT
    '财务日报',
    'SYSTEM',
    'workflowCronJob.execute(''finance_daily_report_v1'')',
    '0 0 9 * * ?',
    '3',
    '1',
    '0',
    'system',
    NOW(),
    'Task #3：每日 09:00 触发 opc-agent-hub 的 finance_daily_report_v1 工作流'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_job`
    WHERE `invoke_target` = 'workflowCronJob.execute(''finance_daily_report_v1'')'
);
