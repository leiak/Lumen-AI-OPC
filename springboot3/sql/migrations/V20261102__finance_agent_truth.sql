-- ============================================================
-- V20261102__finance_agent_truth.sql
--
-- 日报表的「谁写的 / 写到哪一步 / 能不能反查回那次运行」。
--
-- 背景:opc_insight_daily_report 有**三个写入方**——
--   1. 手工生成:公开接口 POST /opc/insight/daily/generate
--   2. 工作流:新增的内部端点 POST /opc/insight/internal/daily/upsert
--   3. 种子数据
-- 三者都往同一个 (company_id, period) 上写,互相覆盖(uk_company_period 唯一键),
-- 于是「事后谁写的这行日报」根本分不清 —— 已写进去的数字员工产出和
-- 手工产出长得一模一样。
--
-- 本次加三列,把这件事变成可断言的事实:
--   report_status          写到哪一步:FULL / PARTIAL / FALLBACK
--   report_source          谁写的:AGENT_WORKFLOW / MANUAL
--   source_workflow_run_id 产出该日报的工作流运行记录 id(能反查回那次运行)
--
-- 列名为什么用 report_source 而不是 source:
--   `source` 是 MySQL 的关键字(复制语句里的 CHANGE REPLICATION SOURCE TO),
--   CREATE TABLE 能过,但在 DDL/DML 里到处要加反引号,而且读代码的人第一眼
--   会以为是「数据来源」而不是「谁写的这行」。加前缀是最省事的避法。
--
-- 幂等:每条语句都可以重复执行(MySQL 8 不支持 ALTER TABLE ... ADD COLUMN
--       IF NOT EXISTS,那是 MariaDB 语法,所以用 information_schema 探测 +
--       动态 SQL 实现)。
--
-- 本文件分两段:
--   第 1 段（1~3):加三列
--   第 2 段（4~7):数据部分 —— 删掉编造的种子日报、回填历史行、
--                  把工作流改挂 company 2 / 换新 dag / cron 改成 UTC 01:00
-- 段落顺序就是执行顺序,**不许调换**(第 2 段要用到第 1 段加出来的列)。
-- ============================================================

SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. report_status —— 写到哪一步
-- ----------------------------------------------------------------------------
SET @exist := 0;
SELECT COUNT(*) INTO @exist FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opc_insight_daily_report' AND COLUMN_NAME = 'report_status';
SET @sql := IF(@exist = 0, 'ALTER TABLE `opc_insight_daily_report` ADD COLUMN `report_status` VARCHAR(16) DEFAULT NULL COMMENT ''FULL/PARTIAL/FALLBACK''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------------------------------------------------------
-- 2. report_source —— 谁写的
-- ----------------------------------------------------------------------------
SET @exist := 0;
SELECT COUNT(*) INTO @exist FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opc_insight_daily_report' AND COLUMN_NAME = 'report_source';
SET @sql := IF(@exist = 0, 'ALTER TABLE `opc_insight_daily_report` ADD COLUMN `report_source` VARCHAR(32) DEFAULT NULL COMMENT ''AGENT_WORKFLOW/MANUAL''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------------------------------------------------------
-- 3. source_workflow_run_id —— 从日报反查回产出它的那次工作流运行
-- ----------------------------------------------------------------------------
SET @exist := 0;
SELECT COUNT(*) INTO @exist FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opc_insight_daily_report' AND COLUMN_NAME = 'source_workflow_run_id';
SET @sql := IF(@exist = 0, 'ALTER TABLE `opc_insight_daily_report` ADD COLUMN `source_workflow_run_id` BIGINT DEFAULT NULL COMMENT ''产出该日报的工作流运行记录 id''', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- =============================================================================
-- 第 2 段:数据部分
--
-- 顺序是硬依赖,**不许调换**:
--   4) DELETE 编造的种子日报      ← 必须排在 1~3 的 ALTER 之后(要读 report_source)
--   5) UPDATE 回填历史行           ← 必须排在 4 之后(否则把要删的行标成 MANUAL 就删不掉了)
--   6) UPDATE opc_agent_workflow   ← 先让结构就位,再改数据
--   7) UPDATE sys_job
-- =============================================================================

-- ----------------------------------------------------------------------------
-- 4. 删掉 7 行**编造**的种子日报
--
--    那 7 行的 kpi_json 是编的:totalRevenue = 9800 / 19600 / 6200 / 4200 /
--    3100 / 3580 / 890,而真实流水是 3582 / 7080 / 9860 / 12150 / 13200 / 38450
--    —— 连 3580 和 3582 都差 2 块,一条都对不上。
--
--    为什么不用 `LIKE '%"totalRevenue":9800%'`:
--    kpi_json 是 **MySQL 原生 JSON 列**,取出来的文本是 MySQL 自己的打印格式
--    (冒号后有空格、浮点带 .0、键序被规范化)—— 那是**实现细节,不是数据契约**。
--    换个 MySQL 版本、或换个写法写进去,就悄悄失配(删不掉,或者删错)。
--    JSON_EXTRACT 走结构化路径,跟打印格式无关。
--
--    为什么不用「按日期范围删」:种子的 period 是 `DATE_SUB(CURDATE(), ...)`
--    相对算出来的,真实跑出来的日报也在同一张表里 —— 按日期删会连真的一起删掉。
--
--    为什么不用「create_by IS NULL」:这 7 行是种子写的,create_by='admin'。
--
--    四个指纹一起用(缺一不可):
--      report_source IS NULL   —— 只删还没标来源的行
--      create_by = 'admin'     —— 只删种子写的行
--      llm_used = 'FALLBACK'   —— 只删降级产出的行
--      totalRevenue ∈ 那 7 个编的值 + walletBalance = 2860(7 行都一样)
--    → 真实跑出来的日报的 kpi_json 形状是 {"query_bank_flow":{...},...},
--      根本没有 $.totalRevenue 这个键 → JSON_EXTRACT 返回 NULL → 不会被删。
-- ----------------------------------------------------------------------------
DELETE FROM `opc_insight_daily_report`
 WHERE `report_source` IS NULL
   AND `create_by` = 'admin'
   AND `llm_used` = 'FALLBACK'
   AND JSON_UNQUOTE(JSON_EXTRACT(`kpi_json`, '$.totalRevenue'))
       IN ('9800.0','19600.0','6200.0','4200.0','3100.0','3580.0','890.0')
   AND CAST(JSON_UNQUOTE(JSON_EXTRACT(`kpi_json`, '$.walletBalance')) AS DECIMAL(20,2)) = 2860;

-- ----------------------------------------------------------------------------
-- 5. 回填历史行 —— 剩下的行都是手工生成的
--
--    公开接口 POST /opc/insight/daily/generate 写出来的行,report_source 是 NULL
--    → 「手工拒绝覆盖数字员工行」这条规则对它们不生效 → 标成 MANUAL。
--    report_status 从 llm_used 推:FALLBACK → FALLBACK,其它 → FULL。
--    (PARTIAL 是 Task 5 之后才有的状态,历史行里不会有。)
--
--    只更新 report_source IS NULL 的行 → 已经标好的 AGENT_WORKFLOW 行不动 → 幂等。
-- ----------------------------------------------------------------------------
UPDATE `opc_insight_daily_report`
   SET `report_source` = 'MANUAL',
       `report_status` = CASE WHEN `llm_used` = 'FALLBACK' THEN 'FALLBACK' ELSE 'FULL' END
 WHERE `report_source` IS NULL;

-- ----------------------------------------------------------------------------
-- 6. 工作流改挂 company 2 + 换新 dag + cron 改成 UTC 01:00(北京 09:00)
--
--    6.1 company_id: 1 → 2
--        全部财务数据都在 company 2(opc_finance_bank_flow 6 行 / voucher 5 行,
--        company 1 是 0 行),工作流挂在 company 1 → 工具恒查空集 → 永远 0 行 →
--        「如实说无流水」分不出「链路是真的」和「工具完全坏掉」。
--    6.2 dag_json: 换成 TOOL(查库) → LLM(写) → persist(落库)
--        原来的 dag 是两个纯 LLM 节点,一个表都不查 —— 模型一直在编。
--    6.3 cron_expression: '0 0 9 * * ?' → '0 0 1 * * ?'
--        容器时区是 UTC → '0 0 9 * * ?' 实际是**北京 17:00**,
--        README 写的「每天 09:00」是假的。'0 0 1 * * ?' = UTC 01:00 = 北京 09:00。
--        (不改成给容器加 TZ:那会改动所有 LocalDateTime.now() 的落库时间。)
--    6.4 timezone: 'Asia/Shanghai' → 'UTC'
--        这列**不是调度时区,是展示时区**:真调度在 ruoyi-job 的 Quartz 里,
--        CronScheduleBuilder.cronSchedule(cron) 不带任何时区 → 用 JVM 默认时区
--        (容器 TZ = UTC)。展示在 WorkflowTriggerServiceImpl.nextRunAt(),
--        它按 timezone 列算「下次执行时间」。timezone 还留着 Asia/Shanghai 的话,
--        页面显示的是「北京 01:00」= UTC 17:00 → **比真实触发晚 16 小时**,
--        页面上的「下次执行时间」是假的。改成 UTC 后,显示和实际是同一个时刻。
--    6.5 trigger_config: 同一套 cron 的另一处副本,同步改(全仓没有代码读它,
--        但留着旧值就是两说)。
--
--    ⚠️ dag_json 里的换行必须写成 \\n(反斜杠 + n 两个字符):
--    MySQL 的字符串字面量会把 \n 解释成**真换行** → 存进 dag_json 后,
--    JSON 字符串里出现未转义的控制字符 → 不是合法 JSON →
--    WorkflowEngine.run() 里的 objectMapper.readTree(dagJson) 抛 JsonParseException
--    → 整条工作流直接死掉。写成 \\n,存进去的就是 JSON 里的 \n 转义 ✓。
-- ----------------------------------------------------------------------------
UPDATE `opc_agent_workflow`
   SET `company_id` = 2,
       `cron_expression` = '0 0 1 * * ?',
       `timezone` = 'UTC',
       `trigger_config` = '{"cron":"0 0 1 * * ?","timezone":"UTC"}',
       `dag_json` = '{"nodes":[{"id":"n1","type":"TOOL","tools":["query_bank_flow","query_voucher"],"args":{},"label":"取真实流水与凭证"},{"id":"n2","type":"LLM","stepTimeoutSeconds":60,"prompt":"你是企业财务助理。下面是系统从业务表里查出来的真实数据（JSON）。请据此写一份面向管理者的财务日报，包含【关键数字】【风险提示】【今日建议】三个小节。\\n\\n硬性要求：1) 所有数字必须直接引用给定数据里的值，不得推算或补充；2) 如果数据里昨日（${yesterday}）没有流水，必须明确写「昨日无资金流水」，不得写成「营收 0 元，运营平稳」；3) 不得出现给定数据里没有的数字。\\n\\n真实数据：\\n${n1}"}],"edges":[{"source":"n1","target":"n2"}],"persist":{"target":"daily_report","date":"${yesterday}","kpiFrom":"n1","summaryFrom":"n2"}}'
 WHERE `workflow_code` = 'finance_daily_report_v1';

-- ----------------------------------------------------------------------------
-- 7. sys_job 的 cron 同样改
--
--    同一个工作流的第二个调度入口(Quartz 调度表)。'0 0 9 * * ?' 也是北京 17:00。
--    concurrent 保持 '1'(禁止并发)—— 与服务端 30 秒去重窗口配合,是对的,不动。
-- ----------------------------------------------------------------------------
UPDATE `sys_job` SET `cron_expression` = '0 0 1 * * ?'
 WHERE `invoke_target` = 'workflowCronJob.execute(''finance_daily_report_v1'')';

-- ----------------------------------------------------------------------------
-- 8. opc_agent_workflow_run.status 的列注释要含 PARTIAL
--
--    PARTIAL 是**真的会被写进这一列的值**(Task 5 引入:LLM 降级但数据落表了),
--    Task 7 的 R-125 还专门为它加了一条处置(PARTIAL 不抛异常)。
--    但列注释里列的合法值清单少了它 → **schema 自己在说假话**:
--    读这份表结构的人(或工具)会以为 PARTIAL 这个状态不存在。
--    这正是本仓库要消灭的那类病,只不过这次长在表结构里,不在文档里。
--
--    只改**注释**: 类型 VARCHAR(16) / NOT NULL / DEFAULT 'PENDING' 一个字都不动。
--
--    幂等: 迁移已经在线上库跑过两次了,这一段会被再跑一次。
--    探测条件是「注释里**含 PARTIAL**」而不是「列存不存在」——
--    列早就存在了,缺的只是注释,所以第二次跑要走 'SELECT 1' 分支,
--    否则每次跑都白做一次 ALTER。
-- ----------------------------------------------------------------------------
SET @exist := 0;
SELECT COUNT(*) INTO @exist FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'opc_agent_workflow_run'
   AND COLUMN_NAME = 'status' AND COLUMN_COMMENT LIKE '%PARTIAL%';
SET @sql := IF(@exist = 0,
  'ALTER TABLE `opc_agent_workflow_run` MODIFY COLUMN `status` VARCHAR(16) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/RUNNING/SUCCESS/PARTIAL/FAILED/CANCELLED''',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
