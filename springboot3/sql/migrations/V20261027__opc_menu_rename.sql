-- ============================================================
-- V20261027__opc_menu_rename.sql
--
-- 把两个市场页改成名副其实的名字,并让访客一眼分得出谁是谁:
--
--   2001 智能体市场(M)      → 开发者市场(M)   —— 开发者上架商品的目录
--   2002 智能体市场列表(C)  → 开发者集市(C)   —— /opc/marketplace/index
--   2035 智能体商店(C)      → 官方数字员工(C) —— /opc/agent/market
--                                                          平台自营的 opc_agent_definition
--
-- 改名的理由:这两个页面的数据来源完全不同,名字却都叫"智能体"——
--   /opc/marketplace/index  = mp_publication(开发者上架的商品,2 件)
--   /opc/agent/market       = opc_agent_definition(平台自营的智能体,4 个)
-- 访客(和演示的观众)此前无法从任何一处看出这个区别。
--
-- 幂等:每条 UPDATE 都带「旧名」条件,只命中还没改过的行。
-- 反复执行不会报错,也不会覆盖别人手工改过的名字。
--
-- 两条路径都收敛到同一结果:
--   · 新库:V20261022 / V20261023 里已就地改成新名,本文件轮到时 0 行命中
--   · 存量库:本文件把旧名改成新名
-- ============================================================

SET NAMES utf8mb4;

-- 目录:开发者市场
UPDATE sys_menu
   SET menu_name = '开发者市场',
       remark    = '开发者市场目录'
 WHERE menu_id = 2001
   AND menu_name = '智能体市场';

-- 页面:开发者集市
UPDATE sys_menu
   SET menu_name = '开发者集市',
       remark    = '开发者集市首页'
 WHERE menu_id = 2002
   AND menu_name = '智能体市场列表';

-- 页面:官方数字员工(平台自营,区别于开发者集市)
UPDATE sys_menu
   SET menu_name = '官方数字员工',
       remark    = '平台自营数字员工列表'
 WHERE menu_id = 2035
   AND menu_name = '智能体商店';

-- 模块社区的备注文案里也提到旧名,一并收口
UPDATE sys_menu
   SET remark = REPLACE(remark, 'opc-marketplace 智能体市场', 'opc-marketplace 开发者集市')
 WHERE menu_id IN (2005, 2006)
   AND remark LIKE '%opc-marketplace 智能体市场%';
