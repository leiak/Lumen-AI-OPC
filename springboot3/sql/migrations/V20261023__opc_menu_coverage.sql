-- ============================================================
-- V20261023__opc_menu_coverage.sql
-- ============================================================
-- 补齐 sys_menu 对 OPC 前端的**完整**覆盖:15 行(6 目录 + 9 页面)。
--
-- 起因(「菜单全平铺」修复的前半段):
--   W80 P1 的 V20261022 注册了 OPC业务 → 8 模块 → 24 页。修层级的方式是让
--   sys_menu 成为**唯一**事实源 —— 删掉 vue3-typescript/src/router/index.ts 里
--   硬编码的 `path: '/opc'` Layout parent。但那个 parent 下面除了那 24 页,
--   还挂着 11 个 sys_menu **没注册**的条目,直接删会功能回退:
--
--     · OPC 首页            /opc/index
--     · 智能体商店/我的实例 /opc/agent/{market,instances}
--     · 财务三页            /opc/finance/{vouchers,flows,tax-reports}
--     · 钱包与账单          /opc/billing/wallet
--     · AI 对话             /opc/llm/chat
--     · 用户画像/我的邀请   /opc/user/profile, /opc/invitations
--
--   这 9 个页面 + 配套的 5 个目录在本文件里注册。它们对应 9304 billing /
--   9305 finance / 9301 ai-core / 9303 agent-hub 四个服务 —— 这些服务一直有
--   后端、有网关路由、有前端页面,只是从来没进过菜单。
--
-- 11 个**详情页 / 新建页**不进菜单(它们靠页内跳转到达,侧边栏不该出现),
--   留在 constantRoutes 里 hidden,由同文件的 router 重构接管。
--
-- 为什么单独一个迁移而不是改 V20261022__opc_sys_menus.sql:
--   V20261022 已在环境跑过,Flyway 按 checksum 校验已应用的迁移文件,
--   改动会让下次启动 `flyway validate` 直接失败。已发布的迁移只读。
--
-- 约定(与 V20261022 一致,且都与 ry_20260417.sql:164-166 的内置目录对齐):
--   is_frame='1'  本 fork 的 UserConstants.NO_FRAME 就是 "1"(与上游相反)
--   二级目录 component 为空 → getComponent 走 isParentView → 'ParentView'
--   route_name 全部显式给出:留空会回退 capitalize(path),叶子 path='index'
--                  会撞内置首页路由 name='Index'(router/index.ts:66),
--                  vue-router 遇重名会让先注册的那条被丢弃 → /index 打不开
--   component 必须是 views/ 下真实 .vue 的相对路径:loadView
--                  (store/modules/permission.ts:116) 做的是精确字符串比较
--
-- Idempotent: INSERT IGNORE,PK 是 menu_id / (role_id, menu_id)。
-- ============================================================

-- ------------------------------------------------------------
-- 1) 5 个新模块目录 + OPC 首页
-- ------------------------------------------------------------
-- ⚠️ SET NAMES utf8mb4 是必需的:客户端字符集默认 latin1 时,
--    下面这些 UTF-8 中文字面量会被双重编码入库
--    (utf8 字节 E4B89A… 存成 C3A4C2B8…),页面显示为乱码。
--    参照 V20261022__opc_sys_menus.sql 的处理。
SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  (2033, 'OPC 首页',     2000,  0, 'index',     null, null, 'OpcHome',     1, 0, 'C', '0', '0', '',                      'guide',     'admin', sysdate(), '', null, 'OPC 门户首页'),
  (2034, '智能体中心',   2000,  9, 'agent',     null, null, 'OpcAgent',    1, 0, 'M', '0', '0', '',                      'component', 'admin', sysdate(), '', null, 'Agent Hub 目录'),
  (2037, '财务管理',     2000, 10, 'finance',   null, null, 'OpcFinance',  1, 0, 'M', '0', '0', '',                      'money',     'admin', sysdate(), '', null, '财务目录'),
  (2041, '计费中心',     2000, 11, 'billing',   null, null, 'OpcBilling',  1, 0, 'M', '0', '0', '',                      'star',      'admin', sysdate(), '', null, '计费/钱包目录'),
  (2043, 'AI 中台',      2000, 12, 'llm',       null, null, 'OpcLlm',      1, 0, 'M', '0', '0', '',                      'system',    'admin', sysdate(), '', null, 'AI 能力目录'),
  (2045, '个人中心',     2000, 13, 'user',      null, null, 'OpcUser',     1, 0, 'M', '0', '0', '',                      'user',      'admin', sysdate(), '', null, '当前用户相关目录');

-- ------------------------------------------------------------
-- 2) 9 个页面
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_menu
  (menu_id, menu_name, parent_id, order_num, path, component, `query`, route_name,
   is_frame, is_cache, menu_type, visible, status, perms, icon,
   create_by, create_time, update_by, update_time, remark)
VALUES
  -- agent-hub (9303)
  (2035, '智能体商店',   2034, 1, 'market',       'opc/agent/market',       null, 'AgentMarket',      1, 0, 'C', '0', '0', 'opc:agent:list',            'shopping',     'admin', sysdate(), '', null, '可安装的智能体列表'),
  (2036, '我的实例',     2034, 2, 'instances',    'opc/agent/instances',    null, 'AgentInstances',   1, 0, 'C', '0', '0', 'opc:agent:instance:list',   'list',         'admin', sysdate(), '', null, '已部署的智能体实例'),

  -- finance (9305)
  (2038, '凭证中心',     2037, 1, 'vouchers',     'opc/finance/vouchers',   null, 'FinanceVouchers',  1, 0, 'C', '0', '0', 'opc:finance:voucher:list',  'documentation','admin', sysdate(), '', null, '会计凭证与过账'),
  (2039, '银行流水',     2037, 2, 'flows',        'opc/finance/flows',      null, 'FinanceFlows',     1, 0, 'C', '0', '0', 'opc:finance:flow:list',     'list',         'admin', sysdate(), '', null, '银行流水对账'),
  (2040, '税务报表',     2037, 3, 'tax-reports',  'opc/finance/tax-reports',null, 'FinanceTaxReports',1, 0, 'C', '0', '0', 'opc:finance:tax:list',      'date',         'admin', sysdate(), '', null, '税务申报报表'),

  -- billing (9304)
  (2042, '钱包与账单',   2041, 1, 'wallet',       'opc/billing/wallet',     null, 'BillingWallet',    1, 0, 'C', '0', '0', 'opc:billing:wallet:list',   'money',        'admin', sysdate(), '', null, '余额与消费账单'),

  -- ai-core (9301)
  (2044, 'AI 对话',      2043, 1, 'chat',         'opc/llm/chat',           null, 'LlmChat',         1, 0, 'C', '0', '0', 'opc:llm:chat',              'message',      'admin', sysdate(), '', null, '多模型对话入口'),

  -- user-center (9302)
  (2046, '用户画像',     2045, 1, 'profile',      'opc/user/profile',       null, 'OpcUserProfile',  1, 0, 'C', '0', '0', 'opc:user:profile',          'user',         'admin', sysdate(), '', null, '当前用户档案'),
  (2047, '我的邀请',     2045, 2, 'invitations',  'opc/invitations',        null, 'OpcInvitations',  1, 0, 'C', '0', '0', 'opc:user:invitation:list',  'star',         'admin', sysdate(), '', null, '邀请记录与邀请码');

-- ------------------------------------------------------------
-- 3) 绑定到 admin 角色(role_id=1)。与 V20261022 同样只为 role 1,
--    但其他角色需要在「系统管理 → 角色管理 → 菜单权限」里自行勾选。
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES
  (1, 2033),
  (1, 2034), (1, 2035), (1, 2036),
  (1, 2037), (1, 2038), (1, 2039), (1, 2040),
  (1, 2041), (1, 2042),
  (1, 2043), (1, 2044),
  (1, 2045), (1, 2046), (1, 2047);
