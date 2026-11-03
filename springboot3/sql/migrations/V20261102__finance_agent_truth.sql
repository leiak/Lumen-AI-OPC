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
-- 本文件只做 ALTER(加列);历史数据回填(把已有的行标成 MANUAL)由 Task 8 补。
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
