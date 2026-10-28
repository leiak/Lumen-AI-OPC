-- ============================================================
-- V20261032 · 电商种子写死的那两个商机分数归零
--
-- V20261031 清掉的是「有分没理由」的那 20 条(CRM 种子的 85 / 70 / 95 …)。
-- 电商种子(sql/seed/opc_demo_ecommerce_seed.sql)里还有两条是**带理由**的:
--
--   '拾光家居-季度补货(1500 件)'  score=82  reason='对方采购预算明确,决策链短'
--   '悦享生活-双十一专场备货'     score=75  reason='已确认参与专场,待确认备货量'
--
-- 理由写得跟真的一样, 但那也是编的 —— 写它的时候, CRM 的「LLM 评分」调的是一个
-- 从没被实现过的地址(POST /opc/ai-core/opportunity/score 在对端不存在),
-- 系统里**没有任何一条代码路径**算出过任何分数。理由只是一段手写的字符串。
--
-- 怎么认出「种子写的分数」而不动真分数:
--   CRM 的每次写入都会带上 update_by(OpportunityServiceImpl.score 里
--   opp.setUpdateBy(String.valueOf(operatorId))), mapper 的 updateById 还会把
--   update_time 刷成 NOW()。而种子 INSERT 从不写 update_by → 那两行的 update_by
--   是 NULL。
--   于是「有分数 + update_by IS NULL」= 这个分数**不是应用算出来的**。
--   真正算出来的分数永远带着 update_by, 所以这条规则以后再跑也只碰该碰的行。
--
-- 电商种子同步改成不写 score / score_reason(见那个文件里的同名段落),
-- 新环境从一开始就是「还没评过」。
--
-- 幂等:可重复执行(归零后 score=0, 不再命中 WHERE)。
-- ============================================================

UPDATE opc_crm_opportunity
SET score = 0,
    score_reason = NULL
WHERE score <> 0
  AND update_by IS NULL;
