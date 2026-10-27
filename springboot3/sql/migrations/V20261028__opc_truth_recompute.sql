-- ============================================================
-- V20261028__opc_truth_recompute.sql
--
-- 把演示环境里"编出来的数字"换成"数得出来的数字"。
--
-- 背景:商品卡上的浏览量 / 安装量 / 订阅数 / 评分,以前是种子文件里写死的
-- 一串大数字(812 / 342 / 96 / 4.6 / 58 …)。演示环境里这些数字一个都验证不了,
-- 页面却拿它们当"有多少人在用"展示 —— 这不是演示数据,是谎话。
--
-- 本文件的做法:能派生的全部从真实表派生,不能派生的(浏览量没有日志表)
-- 按"种子基线 + 真实增量"反推,推不出来的归 0。
--
-- 幂等:可重复执行。带 WHERE 的 UPDATE 只命中还没收敛的行。
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1) 类目:补 MODULE 的「电商运营」
--
-- marketplace 的 MODULE 商品 PUB-ECOM-001 的 category_code 以前是 'ECOM',
-- 而 mp_category 里只有 office/finance/sales/data/integration/tool ——
-- 'ECOM' 匹配不到任何一行,于是「功能模块」下点任何分类都是空的。
-- code 用小写:列表页的 categoryName 走 Java Map<productType|code> 查表
-- (大小写敏感),大写会匹配不上,卡片上显示的是原始 code 而不是中文类目名。
-- ------------------------------------------------------------
INSERT IGNORE INTO mp_category (product_type, code, name, sort_order, icon) VALUES
  ('MODULE', 'ecom', '电商运营', 4, 'ShoppingCart');

-- ------------------------------------------------------------
-- 2) 社区模块:补一个真实的电商模块
--
-- PUB-ECOM-001 以前挂的是 (SELECT MIN(id) FROM opc_community_module),
-- 也就是 id=1 的 'crm-starter CRM 入门套件' —— 一个「电商运营助手」指着
-- CRM 模块,谁看了都迷糊。这里补一个真的电商运营模块,作者与 marketplace
-- 的作者一致(明轩优选 / 公司 2)。
-- ------------------------------------------------------------
INSERT IGNORE INTO opc_community_module
  (code, name, category, description, owner_name, icon, rating, rating_count, install_count, comment_count, tags)
VALUES
  ('ecom-ops', '电商运营助手', 'ECOM', '选品 + 直播排期 + 数据复盘,一人公司开箱即用', '明轩优选', '🛒', 0.0, 0, 0, 0, '电商,运营,一人公司');

-- 把商品指到新模块上(按 code 定位,不写死 id)
UPDATE mp_publication p
   SET p.module_id = (SELECT m.id FROM opc_community_module m WHERE m.code = 'ecom-ops')
 WHERE p.code = 'PUB-ECOM-001'
   AND EXISTS (SELECT 1 FROM opc_community_module m WHERE m.code = 'ecom-ops')
   AND p.module_id <> (SELECT m.id FROM opc_community_module m WHERE m.code = 'ecom-ops');

-- ------------------------------------------------------------
-- 3) 社区模块的计数器:从真实评分 / 评论行派生
--
-- rating / rating_count / comment_count 有对应的行表,直接算。
-- install_count 没有安装日志表 —— 它以前写的是 156/89/234/… 这种编的数。
-- 其中 crm-starter 的线上值是 157(种子基线 156 + 1):那多出来的 1 是
-- 真的有人在社区点过一次安装(社区的安装端点每次 +1),所以它真实的值是 1。
-- 其余模块的线上值与种子基线完全相同 → 一次都没被装过 → 0。
-- ------------------------------------------------------------
UPDATE opc_community_module m
   SET m.rating       = (SELECT COALESCE(AVG(r.score), 0) FROM opc_community_rating r WHERE r.module_id = m.id),
       m.rating_count = (SELECT COUNT(*) FROM opc_community_rating r WHERE r.module_id = m.id),
       m.comment_count = (SELECT COUNT(*) FROM opc_community_comment c WHERE c.module_id = m.id)
 WHERE m.code <> 'ecom-ops';

UPDATE opc_community_module m
   SET m.install_count = 1
 WHERE m.code = 'crm-starter';

UPDATE opc_community_module m
   SET m.install_count = 0
 WHERE m.code <> 'crm-starter'
   AND m.code <> 'ecom-ops';

-- ------------------------------------------------------------
-- 4) 商品的类目 code 归一到小写
--
-- mp_category 的 code 全是小写。列表页的 categoryName 是
-- PublicationServiceImpl.loadJoins 建的 Java Map<productType|code> 查出来的,
-- 键大小写敏感 → 商品上存 'FINANCE' 就查不到 'finance' 这一行,
-- 卡片上只能显示原始 code "FINANCE" 而不是"财务助手";
-- 而详情页走 SQL(排序规则不敏感)显示正常 —— 同一个商品两个页面不一致。
-- ------------------------------------------------------------
UPDATE mp_publication
   SET category_code = LOWER(category_code)
 WHERE product_type = 'AGENT'
   AND category_code IN ('FINANCE', 'ERP', 'CRM', 'HR', 'ECOM', 'CONTENT', 'VOICE', 'INSIGHT', 'OFFICE', 'SALES')
   -- BINARY 是必须的:mp_publication 用 utf8mb4_unicode_ci(不区分大小写),
   -- 于是 'FINANCE' <> LOWER('FINANCE') 判定为 **false** —— 整条 UPDATE
   -- 一行都不命中,而且不报任何错。踩过一次:线上改完 category_code
   -- 仍然是 'FINANCE',因为 WHERE 永远不成立。逐字节比才比得出大小写。
   AND BINARY category_code <> BINARY LOWER(category_code);

UPDATE mp_publication
   SET category_code = LOWER(category_code)
 WHERE product_type = 'MODULE'
   AND category_code IN ('ECOM', 'DATA', 'INTEGRATION', 'TOOL')
   AND BINARY category_code <> BINARY LOWER(category_code);

-- ------------------------------------------------------------
-- 5) 评价状态:'PUBLISHED' → 'VISIBLE'
--
-- ReviewServiceImpl 建评价时写死的就是 'VISIBLE',而评分统计的 SQL
-- (MpReviewMapper.averageForPublication / countForPublication)也只认
-- 'VISIBLE'。种子插的 'PUBLISHED' 是应用自己永远写不出的状态 ——
-- 统计看不见它,于是 PUB-FIN-001 卡片上写着"4.8 分 127 条评价",
-- 真实是 0 条 0.00 分。
-- ------------------------------------------------------------
UPDATE mp_review
   SET status = 'VISIBLE'
 WHERE status = 'PUBLISHED'
   AND status <> 'VISIBLE';

-- ------------------------------------------------------------
-- 6) 作者档案
--
-- level 只能是 NORMAL / PREMIUM(AuthorLevel 枚举),'SILVER' 是编的;
-- total_subscribers / total_revenue 在 marketplace 与 opc-billing 之间
-- 没有写单路径,这两个数没有来源 → 归 0。
-- total_agents 由下面按真实商品数回填。
-- ------------------------------------------------------------
UPDATE mp_author
   SET level = 'NORMAL',
       total_subscribers = 0,
       total_revenue = 0.00
 WHERE level NOT IN ('NORMAL', 'PREMIUM')
    OR total_subscribers <> 0
    OR total_revenue <> 0;

UPDATE mp_author a
   SET a.total_agents = (SELECT COUNT(*) FROM mp_publication p WHERE p.author_id = a.id AND p.deleted = 0);

-- ------------------------------------------------------------
-- 7) 商品计数器:能从表里算的全部从表里算
--
-- install_count 的不变量:恒等于该商品的 mp_install 行数
--   (InstallServiceImpl 只在「新插入行」时 +1,复活既有行不 +1,
--    而行只在首次安装时插入;卸载是软删 status=UNINSTALLED,行还在)
-- rating_count / avg_rating 的不变量:VISIBLE 评价的 COUNT 与 AVG
--   (与 MpReviewMapper 的统计口径完全一致)
--
-- view_count 没有对应的行表,它是详情页每次 GET 自增的单调计数。
-- 种子基线是 812 / 2043(唯一一次提交 b7e113f,之后再没改过),
-- 线上的 823 / 2048 减去基线 = 11 / 5 次真实浏览。
-- 以前直接显示 823 / 2048 是把"编的基线 + 真的浏览"混在一起当总数报,
-- 拆开才是真的。
-- subscription_count 归 0:订阅这个产品动作当时还不存在
-- (MpPublicationMapper#incrementSubscriptionCount 零调用方)。
-- ------------------------------------------------------------
UPDATE mp_publication p
   SET p.install_count = (SELECT COUNT(*) FROM mp_install i WHERE i.publication_id = p.id),
       p.rating_count = (SELECT COUNT(*) FROM mp_review r
                         WHERE r.publication_id = p.id AND r.status = 'VISIBLE' AND r.deleted = 0),
       p.avg_rating   = (SELECT COALESCE(AVG(r.rating), 0) FROM mp_review r
                         WHERE r.publication_id = p.id AND r.status = 'VISIBLE' AND r.deleted = 0),
       p.subscription_count = 0
 WHERE p.deleted = 0;

UPDATE mp_publication
   SET view_count = 11
 WHERE code = 'PUB-ECOM-001';

UPDATE mp_publication
   SET view_count = 5
 WHERE code = 'PUB-FIN-001';

-- 商品名:以前叫「财务数字员工-小财(试用版)」,和平台自营的
-- 「财务数字员工-小财」几乎一模一样 —— 两个市场页摆在一起看着像同一个东西。
-- 明说是平台小财的开发者版本。
UPDATE mp_publication
   SET name = '小财·一人公司版'
 WHERE code = 'PUB-FIN-001'
   AND name = '财务数字员工-小财(试用版)';
