-- ============================================================
-- V20261022__opc_sys_menus.sql
-- ============================================================
-- P1 follow-up: register the 8 OPC business modules in sys_menu.
--
-- Discovery (2026-10-22): the 8 OPC modules (marketplace / community /
-- notification / crm / hr / erp / content / insight) all have a backend
-- service, a gateway route and frontend routes, but ry_20260417.sql seeds
-- only RuoYi's built-in menus (1-4, 100-116, 500-501, 1000-1060) --
-- ZERO rows for any OPC module. /getRouters therefore returns nothing for
-- OPC paths and the whole business sidebar is invisible to operators.
--
-- Route convention verified against
--   ruoyi-system .../service/impl/SysMenuServiceImpl.java
--     - getRouterPath:459  parent_id=0 + menu_type='M' + is_frame='1' -> '/'+path
--     - getComponent:487   parent_id=0, component 空        -> 'Layout'
--                         parent_id!=0 + menu_type='M'      -> 'ParentView'  (isParentView:523)
--     - leaf (menu_type='C')                                -> component 列原样取值
-- ⚠️ 本 fork 的 UserConstants.NO_FRAME = "1"(:46),与上游 RuoYi 的 "0" 相反 ——
--    is_frame='1' 才是「非外链」,下面所有行统一用 1,与 ry_20260417.sql:164-166
--    的内置目录 (系统管理/系统监控/系统工具) 完全一致。
-- 所以 child `path` MUST be relative to its parent dir (path 'index', NOT
-- 'marketplace/index', which would render /opc/marketplace/marketplace/index).
--
-- `component` must exactly equal a file under views/ -- the frontend
-- store/modules/permission.ts#loadView does an exact string compare on
-- 'views/<component>.vue'. All 24 leaf values below were checked to exist
-- in vue3-typescript/src/views/.
--
-- `route_name` is set explicitly on every row on purpose: when empty,
-- getRouteName() falls back to StringUtils.capitalize(path), and leaf path
-- 'index' yields route name 'Index' -- colliding with the built-in homepage
-- route `name: 'Index'` in router/index.ts. A duplicate route name makes
-- vue-router drop the previously registered route, which would break /index.
--
-- Icons are svg-icon names resolved from vue3-typescript/src/assets/icons/svg/
-- (SidebarItem.vue renders <svg-icon :icon-class="item.meta.icon"/>). Only
-- names that exist as .svg files are used -- an absent file renders a blank icon.
--
-- Perm strings: NO OPC controller declares @PreAuthorize (verified across all
-- 39 controllers in the 8 modules), so there is nothing to mirror. The strings
-- below follow RuoYi's <module>:<resource>:<action> convention and name each
-- page's owning controller resource; they are a naming convention, not an
-- enforced backend check.
--
-- Idempotent: INSERT IGNORE on both tables (PKs menu_id and (role_id, menu_id)).
-- Safe to re-apply after `flyway repair`. Id range 2000-2032 sits above the
-- highest existing menu_id (1060) and inside the reserved 2001-2099 block.
--
-- ⚠️ SET NAMES utf8mb4 is REQUIRED here: the client charset defaults to
-- latin1 on many setups, which reads these UTF-8 Chinese literals as latin1
-- and stores them double-encoded (menu_name bytes C3A4C2B8... instead of
-- E4B89A...). Verified on 2026-10-21 — without this line the whole menu tree
-- renders as mojibake in the sidebar. Matches V20261020, which sets it too.
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 1) OPC 业务 top-level directory (8 module dirs)
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  (2000, 'OPC业务',    0,    5, 'opc',         null, null, 'OpcBiz',                1, 0, 'M', '0', '0', '',            'star',     'admin', sysdate(), '', null, 'OPC 业务模块目录'),
  (2001, '智能体市场', 2000, 1, 'marketplace', null, null, 'OpcMarketplace',        1, 0, 'M', '0', '0', '',            'shopping', 'admin', sysdate(), '', null, '智能体市场目录'),
  (2005, '模块社区',   2000, 2, 'community',   null, null, 'OpcCommunity',          1, 0, 'M', '0', '0', '',            'client',   'admin', sysdate(), '', null, 'OPC 模块社区目录(浏览+评论;实际安装在 opc-marketplace 智能体市场)'),
  (2007, '通知中心',   2000, 3, 'notification',null, null, 'OpcNotification',       1, 0, 'M', '0', '0', '',            'message',  'admin', sysdate(), '', null, '通知中心目录'),
  (2009, '客户关系',   2000, 4, 'crm',         null, null, 'OpcCrm',                1, 0, 'M', '0', '0', '',            'user',     'admin', sysdate(), '', null, 'CRM 客户关系目录'),
  (2013, '人力资源',   2000, 5, 'hr',          null, null, 'OpcHr',                 1, 0, 'M', '0', '0', '',            'peoples',  'admin', sysdate(), '', null, '人力资源目录'),
  (2017, '进销存',     2000, 6, 'erp',         null, null, 'OpcErp',                1, 0, 'M', '0', '0', '',            'money',    'admin', sysdate(), '', null, 'ERP 进销存目录'),
  (2023, '内容创作',   2000, 7, 'content',     null, null, 'OpcContent',            1, 0, 'M', '0', '0', '',            'edit',     'admin', sysdate(), '', null, 'AI 内容创作目录'),
  (2028, '数据洞察',   2000, 8, 'insight',     null, null, 'OpcInsight',            1, 0, 'M', '0', '0', '',            'chart',    'admin', sysdate(), '', null, '数据洞察目录');

-- ------------------------------------------------------------
-- 2) Leaf pages (menu_type='C')
--    Detail pages reached by in-page clicks (marketplace/detail/:id,
--    crm/customers/:id, hr/job/:id, erp/purchase/new, ...) are NOT
--    registered -- they are `hidden: true` in router/index.ts and are
--    not standalone menu items.
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  -- marketplace (opc-marketplace: publication / author controllers)
  (2002, '智能体市场列表', 2001, 1, 'index',         'opc/marketplace/index',            null, 'OpcMarketplaceIndex',        1, 0, 'C', '0', '0', 'opc:marketplace:list',             'list',     'admin', sysdate(), '', null, '智能体市场首页'),
  (2003, '作者后台',       2001, 2, 'author-center', 'opc/marketplace/author-center',    null, 'OpcMarketplaceAuthorCenter',  1, 0, 'C', '0', '0', 'opc:marketplace:author:center',    'skill',    'admin', sysdate(), '', null, '作者作品与统计后台'),
  (2004, '申请成为作者',   2001, 3, 'author-apply',  'opc/marketplace/author-apply',     null, 'OpcMarketplaceAuthorApply',   1, 0, 'C', '0', '0', 'opc:marketplace:author:apply',     'form',     'admin', sysdate(), '', null, '企业入驻作者申请'),

  -- community (opc-community: module / comment / rating controllers)
  (2006, '模块社区',       2005, 1, 'market',        'opc/community/Market',             null, 'OpcCommunityMarket',          1, 0, 'C', '0', '0', 'opc:community:list',               'table',    'admin', sysdate(), '', null, '模块社区(浏览+评论;实际安装在 opc-marketplace 智能体市场)'),

  -- notification (opc-notification: inbox / sms / email controllers)
  (2008, '消息收件箱',     2007, 1, 'inbox',         'opc/inbox',                       null, 'OpcNotificationInbox',        1, 0, 'C', '0', '0', 'opc:notification:list',            'bell',     'admin', sysdate(), '', null, '站内通知收件箱'),

  -- crm (opc-crm: customer / opportunity / follow-up controllers)
  (2010, '客户管理',       2009, 1, 'customers',     'opc/crm/CustomerList',            null, 'OpcCrmCustomers',             1, 0, 'C', '0', '0', 'opc:crm:list',                   'client',   'admin', sysdate(), '', null, '客户列表与详情入口'),
  (2011, '商机看板',       2009, 2, 'opportunities', 'opc/crm/OpportunityKanban',       null, 'OpcCrmOpportunities',         1, 0, 'C', '0', '0', 'opc:crm:opportunity:list',         'chart',    'admin', sysdate(), '', null, '商机阶段看板'),
  (2012, '跟进记录',       2009, 3, 'follow-ups',    'opc/crm/FollowUpTimeline',        null, 'OpcCrmFollowUps',             1, 0, 'C', '0', '0', 'opc:crm:followup:list',            'time',     'admin', sysdate(), '', null, '客户跟进时间线'),

  -- hr (opc-hr: dashboard / job / candidate controllers)
  (2014, 'HR 仪表盘',      2013, 1, 'dashboard',     'opc/hr/dashboard',                 null, 'OpcHrDashboard',              1, 0, 'C', '0', '0', 'opc:hr:list',                    'dashboard','admin', sysdate(), '', null, '招聘数据仪表盘'),
  (2015, 'JD 管理',        2013, 2, 'job',           'opc/hr/job/index',                 null, 'OpcHrJob',                    1, 0, 'C', '0', '0', 'opc:hr:job:list',                'documentation', 'admin', sysdate(), '', null, '职位描述管理'),
  (2016, '候选人',         2013, 3, 'candidate',     'opc/hr/candidate/index',           null, 'OpcHrCandidate',              1, 0, 'C', '0', '0', 'opc:hr:candidate:list',          'clipboard','admin', sysdate(), '', null, '候选人简历管理'),

  -- erp (opc-erp: product / purchase / sale / return / inventory controllers)
  (2018, '商品管理',       2017, 1, 'product',       'opc/erp/product/index',           null, 'OpcErpProduct',               1, 0, 'C', '0', '0', 'opc:erp:list',                   'component','admin', sysdate(), '', null, '商品档案'),
  (2019, '采购单',         2017, 2, 'purchase',      'opc/erp/purchase/index',          null, 'OpcErpPurchase',              1, 0, 'C', '0', '0', 'opc:erp:purchase:list',          'zip',      'admin', sysdate(), '', null, '采购单管理'),
  (2020, '销售单',         2017, 3, 'sale',          'opc/erp/sale/index',              null, 'OpcErpSale',                  1, 0, 'C', '0', '0', 'opc:erp:sale:list',              'list',     'admin', sysdate(), '', null, '销售单管理'),
  (2021, '退货单',         2017, 4, 'return',        'opc/erp/return/index',            null, 'OpcErpReturn',                1, 0, 'C', '0', '0', 'opc:erp:return:list',            'tree-table','admin', sysdate(), '', null, '销售退货单'),
  (2022, '库存查询',       2017, 5, 'inventory',     'opc/erp/inventory/index',         null, 'OpcErpInventory',             1, 0, 'C', '0', '0', 'opc:erp:inventory:list',         'tree',     'admin', sysdate(), '', null, '库存台账查询'),

  -- content (opc-content: script / publish / platform-account controllers)
  (2024, '内容仪表盘',     2023, 1, 'dashboard',      'opc/content/dashboard',          null, 'OpcContentDashboard',         1, 0, 'C', '0', '0', 'opc:content:list',               'dashboard','admin', sysdate(), '', null, '内容创作概览'),
  (2025, '脚本管理',       2023, 2, 'script',         'opc/content/script/index',       null, 'OpcContentScript',            1, 0, 'C', '0', '0', 'opc:content:script:list',        'edit',     'admin', sysdate(), '', null, '内容脚本与平台适配'),
  (2026, '平台账号',       2023, 3, 'platform-account','opc/content/platform-account/index', null, 'OpcContentPlatformAccount', 1, 0, 'C', '0', '0', 'opc:content:platformaccount:list','client',   'admin', sysdate(), '', null, '发布平台账号管理'),
  (2027, '发布记录',       2023, 4, 'publish',        'opc/content/publish/index',      null, 'OpcContentPublish',           1, 0, 'C', '0', '0', 'opc:content:publish:list',       'upload',   'admin', sysdate(), '', null, '内容发布记录'),

  -- insight (opc-insight: dashboard / alerts / daily / advice controllers)
  (2029, '经营驾驶舱',     2028, 1, 'dashboard', 'opc/insight/dashboard', null, 'OpcInsightDashboard', 1, 0, 'C', '0', '0', 'opc:insight:list',        'chart',    'admin', sysdate(), '', null, '经营数据驾驶舱'),
  (2030, '异常预警',       2028, 2, 'alerts',    'opc/insight/alerts',    null, 'OpcInsightAlerts',    1, 0, 'C', '0', '0', 'opc:insight:alert:list',   'bell',     'admin', sysdate(), '', null, '经营异常预警'),
  (2031, '财务日报',       2028, 3, 'daily',     'opc/insight/daily',     null, 'OpcInsightDaily',     1, 0, 'C', '0', '0', 'opc:insight:daily:list',   'date',     'admin', sysdate(), '', null, '每日财务快报'),
  (2032, '决策建议',       2028, 4, 'advice',    'opc/insight/advice',    null, 'OpcInsightAdvice',    1, 0, 'C', '0', '0', 'opc:insight:advice:list',  'message',  'admin', sysdate(), '', null, '经营决策建议');

-- ------------------------------------------------------------
-- 3) Bind every new menu to the admin role (role_id=1).
--    userId=1 also short-circuits to selectMenuTreeAll(), but role_menu
--    rows are still required so the role's 菜单权限 tree lists them and
--    so any additional admin-role user resolves them via
--    selectMenuTreeByUserId(). Composite PK makes re-application a no-op.
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 2000),
  (1, 2001), (1, 2002), (1, 2003), (1, 2004),
  (1, 2005), (1, 2006),
  (1, 2007), (1, 2008),
  (1, 2009), (1, 2010), (1, 2011), (1, 2012),
  (1, 2013), (1, 2014), (1, 2015), (1, 2016),
  (1, 2017), (1, 2018), (1, 2019), (1, 2020), (1, 2021), (1, 2022),
  (1, 2023), (1, 2024), (1, 2025), (1, 2026), (1, 2027),
  (1, 2028), (1, 2029), (1, 2030), (1, 2031), (1, 2032);