-- ============================================================
-- V20261030 · opc_community_module.install_count 回填
--
-- 背景(W80 P2):
--   marketplace 的 MODULE 商品装完之后会通过 Feign 通知社区给模块 +1。
--   但在这条通知路径存在之前(2026-10-23),已经有一次真实的 MODULE 安装
--   写进了 mp_install —— 社区那边一个字都没收到。
--   结果:mp_publication.install_count = 1,而 opc_community_module.install_count = 0。
--   同一个模块、同一次安装,两个页面显示出两个对不上的数字。
--
-- 口径(必须与市场的 install_count 严格一致,否则两个页面永远对不上):
--   opc_community_module.install_count
--     = COUNT(mp_install JOIN mp_publication ON p.id = i.publication_id
--              WHERE p.module_id = m.id AND p.product_type = 'MODULE')
--   注意**不过滤 deleted** —— 与健康检查 19.2 的
--   "mp_publication.install_count == COUNT(mp_install)" 同一个口径:
--   卸载是软删(行还在),计数不减。
--
-- 用 GREATEST 而不是直接赋值:
--   社区自己的页面上那个「安装」按钮也会给同一个计数 +1,而那次安装
--   **不留任何安装记录**(ModuleController#install → incrementInstall,没有表)。
--   所以社区计数天然可能 ≥ 可推导的行数。直接赋值会把这些无记录的事件抹掉,
--   反而把数字改成另一个说不清来源的值。
--
-- 幂等:可重复执行(GREATEST 只增不减)。
-- ============================================================

UPDATE opc_community_module m
SET m.install_count = GREATEST(
        m.install_count,
        (SELECT COUNT(*)
           FROM mp_install i
           JOIN mp_publication p ON p.id = i.publication_id
          WHERE p.module_id = m.id
            AND p.product_type = 'MODULE')
    )
WHERE EXISTS (
        SELECT 1
          FROM mp_publication p
         WHERE p.module_id = m.id
           AND p.product_type = 'MODULE'
    );
