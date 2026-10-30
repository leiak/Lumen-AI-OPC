-- =============================================================================
-- opc_demo_ecommerce_seed.sql
--
-- 为「杭州明轩电子商务有限公司」造一套电商/零售批发的演示数据,覆盖 OPC 菜单
-- 下 13 个模块 / 33 个页面,使新账号登录后各页面均不为空。
--
-- 场景设定:
--   一人公司,主营家居用品与服饰的线上零售,自建抖音 + 小红书渠道。
--   年 GMV 约 800 万,有少量合伙员工(运营 + 客服),财务与进销存数据齐全。
--
-- 目标账号(非管理员):
--   user_id    = 101  (opctest,角色 opc_test = OPC 测试角色,admin = False)
--   company_id = 2
--   登录时公司由 opc_company_member 反查得到,故该表必须已有 (2,101) 的行。
--
-- 依赖账号/公司由以下流程产生(本脚本不负责):
--   1. sys.account.registerUser 临时置 true(改库后必须删 Redis 键
--      sys_config:sys.account.registerUser,否则配置缓存不生效)
--   2. POST /auth/register {username,password}
--   3. POST /opc/user/company  (必须显式传 companyType,否则 NOT NULL 报错)
--   4. 恢复 registerUser = false
--   5. 补 opc_company_member (role='OWNER') —— createCompany() 不会写这张表,
--      缺了它下次登录 companyId 解析为 null,marketplace/agent-hub 全线 400
--
-- 用法:
--   mysql -u<user> -p <opc_db> < sql/seed/opc_demo_ecommerce_seed.sql
--
-- 重复执行:会因主键/唯一键冲突报错并中止(整段在事务里,不会写脏数据)。
--          这是刻意的 —— 静默跳过比响亮报错更容易掩盖问题。
--          重灌请先把该公司的数据删掉,或改 @cid / @uid / 9xxxxx 号段。
--
-- ⚠️ 必须保留 SET NAMES utf8mb4 —— 缺了会重演双重编码乱码
--    (客户端默认 latin1 时,中文按 UTF-8 字节双重编码入库)
-- =============================================================================

SET NAMES utf8mb4;

SET @cid = 2;    -- 公司 ID
SET @uid = 101;  -- 用户 ID
SET @by  = 'admin';

START TRANSACTION;

-- =============================================================================
-- 1. 进销存(ERP)
--
-- 关系链(全部裸 BIGINT 外键,无 FK 约束):
--   supplier → purchase → purchase_item → product_sku ← sale_item ← sale
--   sale/purchase(ref_id) ← return
-- 看板(/report/daily、/report/monthly)的唯一数据源是 opc_erp_daily_snapshot,
-- 且没有任何 Controller 能生成指定日期的快照(job 硬编码 companyId=1 且只跑
-- 「昨天」),所以历史日报只能直接 INSERT。
--
-- 主键说明:ERP 与 HR 的 id 是应用层雪花 ID(非自增),故显式给 9xxxxx 号段,
-- 避开现有的 1~几十号数据。
-- =============================================================================

INSERT INTO `opc_erp_supplier`
  (`id`, `company_id`, `name`, `contact`, `phone`, `level`, `address`, `status`, `created_by`, `create_time`)
VALUES
  (900001, @cid, '杭州锦程服饰有限公司', '沈经理', '13905710001', 'PREFERRED', '浙江省杭州市余杭区服装产业园 12 幢', 'ACTIVE', @uid, NOW()),
  (900002, @cid, '义乌华美家居工厂',   '陆老板', '13905710002', 'PREFERRED', '浙江省金华市义乌市国际商贸城 F 区',     'ACTIVE', @uid, NOW()),
  (900003, @cid, '深圳智联数码科技',   '周主管', '13905710003', 'NORMAL',    '广东省深圳市宝安区科技园 8 栋',          'ACTIVE', @uid, NOW());

-- spec_attrs 是 JSON NOT NULL,存规格属性字典
INSERT INTO `opc_erp_product`
  (`id`, `company_id`, `sku_root`, `name`, `category`, `spec_attrs`, `status`, `description`, `created_by`, `create_time`)
VALUES
  (900101, @cid, 'TEE-1000', '纯棉圆领短袖T恤', '服饰', '{"颜色":"白色","材质":"100% 棉","克重":"220g"}',      'ACTIVE', '夏季基础款,自营品牌「明轩优选」主力单品', @uid, NOW()),
  (900102, @cid, 'PIL-2000', '北欧风棉麻抱枕套', '家居', '{"花色":"雾霾蓝/米白","材质":"棉麻混纺","尺寸":"40x40"}', 'ACTIVE', '沙发搭配款,月销约 800 件', @uid, NOW()),
  (900103, @cid, 'SPK-3000', '便携蓝牙音箱',     '数码', '{"版本":"标准版","续航":"12h","防水":"IPX5"}',          'ACTIVE', '自有品牌,客单价高,带动客单提升', @uid, NOW());

-- SKU 本身没有 name 列,展示名由 product.name + spec_json 拼出。
-- stock 与 threshold 必须显式给值:selectLowStock 的 threshold>0 条件恒不生效
-- (OpcErpProductSkuMapper.xml:88),不给值会导致所有 SKU 都被判成低库存
INSERT INTO `opc_erp_product_sku`
  (`id`, `company_id`, `product_id`, `sku_code`, `spec_json`, `price`, `cost`, `stock`, `threshold`, `status`, `create_time`)
VALUES
  (900201, @cid, 900101, 'TEE-1000-W-S',  '{"颜色":"白色","尺码":"S","克重":"220g"}',       59.00,  22.00,  420, 100, 'ACTIVE', NOW()),
  (900202, @cid, 900101, 'TEE-1000-W-M',  '{"颜色":"白色","尺码":"M","克重":"220g"}',       59.00,  22.00,  380, 100, 'ACTIVE', NOW()),
  (900203, @cid, 900101, 'TEE-1000-W-L',  '{"颜色":"白色","尺码":"L","克重":"220g"}',       59.00,  22.00,   35, 100, 'ACTIVE', NOW()),
  (900204, @cid, 900102, 'PIL-2000-B-40', '{"花色":"雾霾蓝","尺寸":"40x40","材质":"棉麻"}', 39.00, 13.50,  760, 200, 'ACTIVE', NOW()),
  (900205, @cid, 900102, 'PIL-2000-W-60', '{"花色":"米白","尺寸":"60x60","材质":"棉麻"}',   49.00, 16.80,  540, 200, 'ACTIVE', NOW()),
  -- 音箱故意低于阈值(12 < 50),让「低库存预警」页面有数
  (900206, @cid, 900103, 'SPK-3000-STD',  '{"版本":"标准版","续航":"12h","防水":"IPX5"}',  199.00, 118.00, 12,  50, 'ACTIVE', NOW());

-- 采购单。状态机 DRAFT → COMPLETED 单步(枚举里的 CONFIRMED 代码从不写入)
INSERT INTO `opc_erp_purchase`
  (`id`, `company_id`, `purchase_no`, `supplier_id`, `status`, `total_amount`, `remark`,
   `operator_id`, `created_by`, `create_time`, `completed_at`)
VALUES
  (900301, @cid, 'PO20261008001', 900001, 'COMPLETED', 28600.00, '夏款 T 恤首批进货',
   @uid, @uid, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
  (900302, @cid, 'PO20261012002', 900002, 'COMPLETED', 18240.00, '抱枕套补货',
   @uid, @uid, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (900303, @cid, 'PO20261018003', 900003, 'COMPLETED',  7080.00, '音箱首批进货',
   @uid, @uid, DATE_SUB(NOW(), INTERVAL  4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));

INSERT INTO `opc_erp_purchase_item`
  (`id`, `company_id`, `purchase_id`, `sku_id`, `quantity`, `unit_price`, `subtotal`, `batch_no`, `production_date`)
VALUES
  (900311, @cid, 900301, 900201, 600, 22.00, 13200.00, 'B20261008-01', '2026-09-20'),
  (900312, @cid, 900301, 900202, 400, 22.00,  8800.00, 'B20261008-01', '2026-09-20'),
  (900313, @cid, 900301, 900203, 200, 22.00,  4400.00, 'B20261008-01', '2026-09-20'),
  (900314, @cid, 900302, 900204, 900, 13.50, 12150.00, 'B20261012-01', '2026-10-05'),
  (900315, @cid, 900302, 900205, 480, 16.80,  8064.00, 'B20261012-02', '2026-10-05'),
  (900316, @cid, 900303, 900206,  60,118.00,  7080.00, 'B20261018-01', '2026-10-15');

-- 批次(FIFO)
INSERT INTO `opc_erp_batch`
  (`id`, `company_id`, `sku_id`, `batch_no`, `quantity`, `remaining`, `production_date`, `expiry_date`, `supplier_id`, `purchase_id`, `create_time`)
VALUES
  (900501, @cid, 900201, 'B20261008-01', 600, 300, '2026-09-20', '2028-09-20', 900001, 900301, NOW()),
  (900502, @cid, 900204, 'B20261012-01', 900, 540, '2026-10-05', NULL,          900002, 900302, NOW()),
  (900503, @cid, 900206, 'B20261018-01',  60,  42, '2026-10-15', '2028-10-15', 900003, 900303, NOW());

INSERT INTO `opc_erp_sale`
  (`id`, `company_id`, `sale_no`, `customer_name`, `customer_phone`, `status`, `total_amount`, `remark`,
   `operator_id`, `created_by`, `create_time`, `completed_at`)
VALUES
  (900401, @cid, 'SO20261010001', '抖音小店-明轩优选', NULL, 'COMPLETED', 35400.00, '直播带货专场',
   @uid, @uid, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (900402, @cid, 'SO20261015002', '小红书-家居日记',   NULL, 'COMPLETED', 19600.00, '家居品类专场',
   @uid, @uid, DATE_SUB(NOW(), INTERVAL  7 DAY), DATE_SUB(NOW(), INTERVAL  7 DAY)),
  (900403, @cid, 'SO20261021003', '微信私域复购客户',  NULL, 'COMPLETED',  3580.00, '老客复购',
   @uid, @uid, DATE_SUB(NOW(), INTERVAL  1 DAY), DATE_SUB(NOW(), INTERVAL  1 DAY));

INSERT INTO `opc_erp_sale_item`
  (`id`, `company_id`, `sale_id`, `sku_id`, `quantity`, `unit_price`, `subtotal`, `batch_id`)
VALUES
  (900411, @cid, 900401, 900201, 300,  59.00, 17700.00, 900501),
  (900412, @cid, 900401, 900202, 200,  59.00, 11800.00, NULL),
  (900413, @cid, 900401, 900204, 180,  33.00,  5940.00, 900502),
  (900414, @cid, 900402, 900204, 260,  39.00, 10140.00, 900502),
  (900415, @cid, 900402, 900205, 180,  49.00,  8820.00, NULL),
  (900416, @cid, 900403, 900206,  18, 199.00,  3582.00, 900503);

-- 退货单。ref_id 必须指向 status='COMPLETED' 的单据(业务硬校验)。
-- 注意:退货单没有明细表,items 只在 service 层算金额,不落库
INSERT INTO `opc_erp_return`
  (`id`, `company_id`, `return_no`, `return_type`, `ref_id`, `status`, `refund_amount`, `reason`,
   `operator_id`, `created_by`, `create_time`, `completed_at`)
VALUES
  (900701, @cid, 'RT20261020001', 'SALES_RETURN',    900402, 'COMPLETED', 2340.00, '客户反馈花色与图片不符,换货处理',
   @uid, @uid, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (900702, @cid, 'RT20261021002', 'SUPPLIER_RETURN', 900303, 'DRAFT',     2376.00, '音箱批次有 3 台充电异常,申请退回供应商',
   @uid, @uid, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

-- 库存流水。`change` 是 MySQL 保留字,必须加反引号。
-- change 正=入库 负=出库;created_by 是 bigint(用户 ID)不是字符串
INSERT INTO `opc_erp_inventory_log`
  (`id`, `company_id`, `sku_id`, `change`, `type`, `ref_type`, `ref_id`, `remark`, `created_by`, `create_time`)
VALUES
  (900601, @cid, 900201,  600, 'PURCHASE_IN',     'PURCHASE', 900301, '夏款 T 恤首批进货', @uid, DATE_SUB(NOW(), INTERVAL 14 DAY)),
  (900602, @cid, 900201, -300, 'SALE_OUT',        'SALE',     900401, '直播带货专场',     @uid, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (900603, @cid, 900202, -200, 'SALE_OUT',        'SALE',     900401, '直播带货专场',     @uid, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (900604, @cid, 900204,  900, 'PURCHASE_IN',     'PURCHASE', 900302, '抱枕套补货',       @uid, DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (900605, @cid, 900204, -440, 'SALE_OUT',        'SALE',     900402, '家居品类专场',     @uid, DATE_SUB(NOW(), INTERVAL  7 DAY)),
  (900606, @cid, 900205,  480, 'PURCHASE_IN',     'PURCHASE', 900302, '抱枕套补货',       @uid, DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (900607, @cid, 900205, -180, 'SALE_OUT',        'SALE',     900402, '家居品类专场',     @uid, DATE_SUB(NOW(), INTERVAL  7 DAY)),
  (900608, @cid, 900206,   60, 'PURCHASE_IN',     'PURCHASE', 900303, '音箱首批进货',     @uid, DATE_SUB(NOW(), INTERVAL  4 DAY)),
  (900609, @cid, 900206,  -18, 'SALE_OUT',        'SALE',     900403, '老客复购',         @uid, DATE_SUB(NOW(), INTERVAL  1 DAY)),
  (900610, @cid, 900203, -120, 'ADJUST',          'MANUAL',   NULL,   'L 码库存盘亏调整', @uid, DATE_SUB(NOW(), INTERVAL  3 DAY)),
  (900611, @cid, 900201,   60, 'PURCHASE_IN',     'PURCHASE', 900301, '夏款 T 恤到货',    @uid, DATE_SUB(NOW(), INTERVAL  6 DAY)),
  (900612, @cid, 900204,   60, 'SALES_RETURN_IN', 'RETURN',   900701, '客户退回 60 件',    @uid, DATE_SUB(NOW(), INTERVAL  2 DAY));

-- 每日库存快照。uk_company_date_sku(company_id, snapshot_date, sku_id) 唯一。
-- 7 天 × 3 个主力 SKU,期初由「期末 - 入 + 出」倒推
INSERT INTO `opc_erp_daily_snapshot`
  (`id`, `company_id`, `snapshot_date`, `sku_id`, `opening_stock`, `in_qty`, `out_qty`, `closing_stock`, `create_time`)
VALUES
  (900801, @cid, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 900201,  600, 600,   0, 1200, NOW()),
  (900802, @cid, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 900204,    0, 900,   0,  900, NOW()),
  (900803, @cid, DATE_SUB(CURDATE(), INTERVAL 6 DAY), 900206,    0,  60,   0,   60, NOW()),
  (900811, @cid, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 900201, 1200,   0, 120, 1080, NOW()),
  (900812, @cid, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 900204,  900,   0, 120,  780, NOW()),
  (900813, @cid, DATE_SUB(CURDATE(), INTERVAL 5 DAY), 900206,   60,   0,   0,   60, NOW()),
  (900821, @cid, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 900201, 1080,   0, 180,  900, NOW()),
  (900822, @cid, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 900204,  780,   0, 180,  600, NOW()),
  (900823, @cid, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 900206,   60,   0,   0,   60, NOW()),
  (900831, @cid, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 900201,  900,   0,  60,  840, NOW()),
  (900832, @cid, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 900204,  600,   0, 120,  480, NOW()),
  (900833, @cid, DATE_SUB(CURDATE(), INTERVAL 3 DAY), 900206,   60,   0,  18,   42, NOW()),
  (900841, @cid, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 900201,  840,   0, 120,  720, NOW()),
  (900842, @cid, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 900204,  480,   0,  60,  420, NOW()),
  (900843, @cid, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 900206,   42,   0,   0,   42, NOW()),
  (900851, @cid, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 900201,  720,   0, 180,  540, NOW()),
  (900852, @cid, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 900204,  420,   0, 100,  320, NOW()),
  (900853, @cid, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 900206,   42,   0,  18,   24, NOW());

-- =============================================================================
-- 2. 财务管理(Finance)
--
-- 借贷方向不是 direction 字段,而是两个金额列:
--   total_debit  = 借方 = 支出 / 进项税
--   total_credit = 贷方 = 收入 / 销项税
-- 聚合按 voucher_date / trade_time 过滤(不是 period 列),故两者都要落本月。
-- ⚠️ entries_json 是 TEXT NOT NULL 且 mapper 不校验,必须给值(用 '[]')
-- ⚠️ review-pass 之后状态是 REVIEW 不是 APPROVED,反直觉但属实
-- =============================================================================

INSERT INTO `opc_finance_voucher`
  (`voucher_code`, `company_id`, `voucher_date`, `period`, `summary`, `entries_json`,
   `total_debit`, `total_credit`, `source_type`, `status`, `create_by`, `create_time`)
VALUES
  ('VCH20261001', @cid, DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_FORMAT(CURDATE(),'%Y-%m'),
   '支付供应商货款-锦程服饰',   '[]', 13200.00,     0.00, 'PURCHASE', 'POSTED',   @by, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  ('VCH20261002', @cid, DATE_SUB(CURDATE(), INTERVAL 12 DAY), DATE_FORMAT(CURDATE(),'%Y-%m'),
   '抖音小店平台结算收入',     '[]',     0.00, 35400.00, 'INVOICE',  'POSTED',   @by, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  ('VCH20261003', @cid, DATE_SUB(CURDATE(), INTERVAL  7 DAY), DATE_FORMAT(CURDATE(),'%Y-%m'),
   '支付供应商货款-华美家居',   '[]', 12150.00,     0.00, 'PURCHASE', 'REVIEW',   @by, DATE_SUB(NOW(), INTERVAL 7 DAY)),
  ('VCH20261004', @cid, DATE_SUB(CURDATE(), INTERVAL  3 DAY), DATE_FORMAT(CURDATE(),'%Y-%m'),
   '计提本月平台服务费',       '[]',  2600.00,     0.00, 'MANUAL',   'DRAFT',    @by, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  ('VCH20261005', @cid, DATE_SUB(CURDATE(), INTERVAL  2 DAY), DATE_FORMAT(CURDATE(),'%Y-%m'),
   '报销直播间设备-已驳回',     '[]',  1800.00,     0.00, 'MANUAL',   'REJECTED', @by, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 列名是 bank_account / counter_party / memo(不是 account_no / counterparty / summary)
INSERT INTO `opc_finance_bank_flow`
  (`flow_code`, `company_id`, `bank_account`, `bank_name`, `trade_time`, `direction`,
   `amount`, `currency`, `counter_party`, `memo`, `extracted`, `status`, `create_by`, `create_time`)
VALUES
  ('BF2026100101', @cid, '6222020100000001', '招商银行杭州余杭支行', DATE_SUB(NOW(), INTERVAL 15 DAY), 'OUT', 13200.00, 'CNY', '杭州锦程服饰有限公司', '采购货款',     1, 'PROCESSED', @by, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  ('BF2026100102', @cid, '6222020100000001', '招商银行杭州余杭支行', DATE_SUB(NOW(), INTERVAL 12 DAY), 'IN',  38450.00, 'CNY', '抖音电商平台',         '小店结算款',   1, 'PROCESSED', @by, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  ('BF2026100103', @cid, '6222020100000001', '招商银行杭州余杭支行', DATE_SUB(NOW(), INTERVAL 10 DAY), 'OUT', 12150.00, 'CNY', '义乌华美家居工厂',     '采购货款',     1, 'PROCESSED', @by, DATE_SUB(NOW(), INTERVAL 10 DAY)),
  ('BF2026100104', @cid, '6222020100000001', '招商银行杭州余杭支行', DATE_SUB(NOW(), INTERVAL  7 DAY), 'IN',   9860.00, 'CNY', '小红书店铺',           '家居专场结算', 1, 'EXTRACTED', @by, DATE_SUB(NOW(), INTERVAL  7 DAY)),
  ('BF2026100105', @cid, '6222020100000001', '招商银行杭州余杭支行', DATE_SUB(NOW(), INTERVAL  4 DAY), 'OUT',  7080.00, 'CNY', '深圳智联数码科技',     '音箱采购货款', 0, 'PROCESSED', @by, DATE_SUB(NOW(), INTERVAL  4 DAY)),
  ('BF2026100106', @cid, '6222020100000001', '招商银行杭州余杭支行', DATE_SUB(NOW(), INTERVAL  1 DAY), 'IN',   3582.00, 'CNY', '微信支付-个人客户',    '零散订单',     0, 'IMPORTED',  @by, DATE_SUB(NOW(), INTERVAL  1 DAY));

-- 税表没有 input_tax / output_tax 列,只有 taxable_amount / tax_amount / pay_amount
INSERT INTO `opc_finance_tax_report`
  (`report_code`, `company_id`, `tax_type`, `period`, `taxable_amount`, `tax_amount`,
   `pay_amount`, `paid_amount`, `due_date`, `status`, `attachments`, `create_by`, `create_time`)
VALUES
  (CONCAT('TAX', DATE_FORMAT(CURDATE(),'%Y%m')), @cid, 'VAT', DATE_FORMAT(CURDATE(),'%Y-%m'),
   35400.00, 4602.00, 4602.00, 0.00, DATE_ADD(CURDATE(), INTERVAL 15 DAY), 'DRAFT',
   '数据由自动化聚合生成,提交前需人工复核进项发票原件。', @by, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- =============================================================================
-- 3. 计费中心(Billing)
--
-- /agg/recharge 是条件最严的 KPI:必须同时满足
--   order_type='RECHARGE' + pay_status='PAID' + create_time 在本月 + pay_method 有值
-- 聚合按 create_time 过滤(不是 pay_time),漏填会全部聚合不到。
-- =============================================================================

INSERT INTO `opc_wallet`
  (`company_id`, `user_id`, `balance`, `frozen`, `total_recharge`, `total_consume`, `status`, `create_by`, `create_time`)
VALUES (@cid, @uid, 2860.00, 0.00, 5000.00, 2140.00, 'ACTIVE', @by, NOW());

INSERT INTO `opc_billing_order`
  (`order_no`, `company_id`, `user_id`, `order_type`, `biz_type`, `title`, `amount`, `paid_amount`,
   `pay_method`, `pay_status`, `pay_trade_no`, `discount`, `refund_amount`, `pay_time`, `create_time`)
VALUES
  ('ORD20261010001', @cid, @uid, 'RECHARGE',     NULL,         '账户充值-1000 元',          1000.00, 1000.00, 'ALIPAY', 'PAID', '202610100001', 0.00, 0.00, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
  ('ORD20261012002', @cid, @uid, 'RECHARGE',     NULL,         '账户充值-2000 元',          2000.00, 2000.00, 'ALIPAY', 'PAID', '202610120002', 0.00, 0.00, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
  ('ORD20261015003', @cid, @uid, 'RECHARGE',     NULL,         '账户充值-2000 元',          2000.00, 2000.00, 'WECHAT', 'PAID', '202610150003', 0.00, 0.00, DATE_SUB(NOW(), INTERVAL  7 DAY), DATE_SUB(NOW(), INTERVAL  7 DAY)),
  ('ORD20261018004', @cid, @uid, 'SUBSCRIPTION', 'MODULE_SUB', '模块市场订阅-季度会员',    899.00,  899.00, 'ALIPAY', 'PAID', '202610180004', 0.00, 0.00, DATE_SUB(NOW(), INTERVAL  4 DAY), DATE_SUB(NOW(), INTERVAL  4 DAY)),
  ('ORD20261020005', @cid, @uid, 'CONSUME',      'TOKEN_PACK', '智能体调用包-10 万 token',  240.00,  240.00, 'WALLET', 'PAID', '202610200005', 0.00, 0.00, DATE_SUB(NOW(), INTERVAL  2 DAY), DATE_SUB(NOW(), INTERVAL  2 DAY));

-- 钱包流水。amount 恒为正,方向由 tx_type 决定;tx_code 是幂等键。
-- ⚠️ biz_id 是 bigint(指向 opc_billing_order.id),不是订单号
INSERT INTO `opc_transaction`
  (`tx_code`, `wallet_id`, `company_id`, `tx_type`, `amount`, `balance_before`, `balance_after`,
   `biz_type`, `biz_id`, `description`, `create_by`, `create_time`)
VALUES
  ('TX20261010001', (SELECT w.id FROM opc_wallet w WHERE w.company_id=@cid AND w.user_id=@uid),
   @cid, 'RECHARGE', 1000.00, 1860.00, 2860.00, 'WALLET_RECHARGE',
   (SELECT o.id FROM opc_billing_order o WHERE o.order_no='ORD20261010001'),
   '账户充值', @by, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  ('TX20261018004', (SELECT w.id FROM opc_wallet w WHERE w.company_id=@cid AND w.user_id=@uid),
   @cid, 'CONSUME',   899.00, 2860.00, 1961.00, 'MODULE_SUB',
   (SELECT o.id FROM opc_billing_order o WHERE o.order_no='ORD20261018004'),
   '模块订阅扣费', @by, DATE_SUB(NOW(), INTERVAL 4 DAY));

-- =============================================================================
-- 4. 人力资源(HR)
--
-- 流转链:job → candidate → application → interview → offer(1:1) + match_score
-- dashboard 只读 opc_hr_application 与 opc_hr_job;
-- avg_hire_days 需要 status='ACCEPTED' 的 offer 且 responded_at 在 30 天内,
-- 否则前端显示 "-"。本脚本刻意造了一条 ACCEPTED 的 offer。
--
-- 本模块时间/审计列因表而异,写之前务必对照真实 schema:
--   job/candidate  → created_by(bigint) + create_time
--   application    → applied_at(无 created_by)
--   interview      → create_time(无 created_by)
--   offer          → sent_at / responded_at / create_time(无 created_by、无 remark)
--   match_score    → computed_at(无 created_by)
-- =============================================================================

INSERT INTO `opc_hr_job`
  (`id`, `company_id`, `created_by`, `title`, `category`, `status`, `description`, `full_jd`,
   `location`, `salary_min`, `salary_max`, `skills_json`, `create_time`)
VALUES
  (900101, @cid, @uid, '电商运营专员(抖音)', 'OPERATION', 'OPEN',
   '负责抖音小店日常运营与直播排期',
   '1、负责店铺日常运营与数据复盘;2、策划直播场次并跟进执行;3、对接达人合作与选品。需有 1 年以上抖音电商经验,熟悉千川投放。',
   '杭州·余杭区(可远程)', 8, 12, '["抖音运营","千川投放","数据分析","直播策划"]', DATE_SUB(NOW(), INTERVAL 25 DAY)),
  (900102, @cid, @uid, '家居品类选品专员', 'OPERATION', 'OPEN',
   '负责家居类目选品与供应链对接',
   '1、负责家居类目选品与定价;2、维护供应商关系与到货周期;3、把控品质与售后。',
   '杭州·余杭区', 8, 11, '["选品","供应链","家居品类"]', DATE_SUB(NOW(), INTERVAL 20 DAY)),
  (900103, @cid, @uid, '客服/售后专员', 'SALES', 'OPEN',
   '承接抖音与私域客户咨询及售后',
   '1、响应店铺咨询与售前售后;2、处理退换货工单;3、沉淀常见问题话术库。',
   '杭州·可远程', 6, 8, '["客服","售后","沟通能力"]', DATE_SUB(NOW(), INTERVAL 18 DAY)),
  (900104, @cid, @uid, '内容策划(小红书)', 'MARKETING', 'PAUSED',
   '负责小红书种草内容产出',
   '1、月度选题与脚本产出;2、达人投放;3、店铺自然流量增长。',
   '杭州·可远程', 8, 12, '["小红书","内容策划","种草"]', DATE_SUB(NOW(), INTERVAL 15 DAY)),
  (900105, @cid, @uid, '兼职平面设计', 'MARKETING', 'DRAFT',
   '负责商品主图与详情页设计',
   '1、商品主图/详情页设计;2、大促视觉;3、日常素材维护。',
   '杭州·远程', 5, 8, '["平面设计","PS","电商视觉"]', DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (900106, @cid, @uid, '仓储打包兼配送', 'OPERATION', 'CLOSED',
   '负责仓储打包与同城配送',
   '岗位已招聘完成,转由外包承接。',
   '杭州·余杭区', 5, 7, '["仓储","打包","配送"]', DATE_SUB(NOW(), INTERVAL 40 DAY));

INSERT INTO `opc_hr_candidate`
  (`id`, `company_id`, `created_by`, `name`, `phone`, `email`, `resume_url`, `parsed_json`, `source`, `create_time`)
VALUES
  (900201, @cid, @uid, '林晓彤', '13800002001', 'linxt@test.com', '/resume/2026/linxiaotong.pdf',
   '{"工作年限":"3年","公司":"某服饰电商品牌","擅长":["抖音运营","千川投放"]}', 'BOSS直聘', DATE_SUB(NOW(), INTERVAL 24 DAY)),
  (900202, @cid, @uid, '赵子豪', '13800002002', 'zhaozh@test.com', '/resume/2026/zhaozihao.pdf',
   '{"工作年限":"2年","公司":"某家居电商","擅长":["选品","供应链"]}',         '猎聘',    DATE_SUB(NOW(), INTERVAL 21 DAY)),
  (900203, @cid, @uid, '王思远', '13800002003', 'wangsy@test.com', '/resume/2026/wangsiyuan.pdf',
   '{"工作年限":"1年","公司":"某私域电商","擅长":["客服","售后"]}',           '内推',    DATE_SUB(NOW(), INTERVAL 17 DAY)),
  (900204, @cid, @uid, '陈雨桐', '13800002004', 'chenyt@test.com', '/resume/2026/chenyutong.pdf',
   '{"工作年限":"2年","公司":"某MCN","擅长":["小红书","内容策划"]}',         '小红书',  DATE_SUB(NOW(), INTERVAL 13 DAY)),
  (900205, @cid, @uid, '刘志强', '13800002005', 'liuzq@test.com',  '/resume/2026/liuzhiqiang.pdf',
   '{"工作年限":"4年","公司":"某广告代理","擅长":["平面设计","电商视觉"]}',     'BOSS直聘', DATE_SUB(NOW(), INTERVAL 9 DAY));

-- status 流转:NEW→SCREENING→INTERVIEW→OFFER→HIRED,任意非终态可 →REJECTED
INSERT INTO `opc_hr_application`
  (`id`, `company_id`, `job_id`, `candidate_id`, `status`, `current_stage`, `score`, `score_reason`, `channel`, `applied_at`)
VALUES
  (900301, @cid, 900101, 900201, 'HIRED',     '已入职',    92.0, '抖音运营经验匹配,千川投放数据达标', 'BOSS直聘', DATE_SUB(NOW(), INTERVAL 22 DAY)),
  (900302, @cid, 900102, 900202, 'OFFER',     '已发Offer', 88.0, '家居选品经验对口,供应链资源可迁移', '猎聘',     DATE_SUB(NOW(), INTERVAL 19 DAY)),
  (900303, @cid, 900103, 900203, 'INTERVIEW', '面试中',    76.0, '客服经验合格,售后流程需进一步确认', '内推',     DATE_SUB(NOW(), INTERVAL 11 DAY)),
  (900304, @cid, 900104, 900204, 'SCREENING', '筛选中',    71.0, '内容能力不错,但小红书投放经验偏少', '小红书',   DATE_SUB(NOW(), INTERVAL  6 DAY)),
  -- score 是 NOT NULL DEFAULT 0,尚未评分的投递用 0 占位
  (900305, @cid, 900105, 900205, 'NEW',       '待筛选',    0.00, NULL,                             'BOSS直聘', DATE_SUB(NOW(), INTERVAL  2 DAY)),
  (900306, @cid, 900103, 900201, 'REJECTED',  '已拒绝',    58.0, '方向不符,候选人更倾向运营岗',       '内推',     DATE_SUB(NOW(), INTERVAL 14 DAY));

-- offer 与 application 是 1:1(uk_application)
INSERT INTO `opc_hr_offer`
  (`id`, `company_id`, `application_id`, `salary`, `status`, `start_date`, `expire_at`, `sent_at`, `responded_at`, `create_time`)
VALUES
  (900401, @cid, 900301, 10500.00, 'ACCEPTED', DATE_SUB(CURDATE(), INTERVAL 5 DAY),
   DATE_ADD(CURDATE(), INTERVAL 7 DAY),  DATE_SUB(NOW(), INTERVAL 21 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 21 DAY)),
  (900402, @cid, 900302,  9800.00, 'PENDING',  DATE_ADD(CURDATE(), INTERVAL 14 DAY),
   DATE_ADD(CURDATE(), INTERVAL 6 DAY),  DATE_SUB(NOW(), INTERVAL  5 DAY), NULL,                            DATE_SUB(NOW(), INTERVAL  5 DAY));

INSERT INTO `opc_hr_interview`
  (`id`, `company_id`, `application_id`, `round`, `type`, `interviewer_id`, `scheduled_at`, `duration_min`, `result`, `feedback`, `create_time`)
VALUES
  (900501, @cid, 900301, 1, 'PHONE',  101, DATE_SUB(NOW(), INTERVAL 20 DAY), 30, 'PASS',   '沟通表达清晰,有直播排期经验,可推进', DATE_SUB(NOW(), INTERVAL 20 DAY)),
  (900502, @cid, 900301, 2, 'VIDEO',  101, DATE_SUB(NOW(), INTERVAL 18 DAY), 45, 'PASS',   '千川投放实操细节答得不错,给 offer',   DATE_SUB(NOW(), INTERVAL 18 DAY)),
  (900503, @cid, 900302, 1, 'PHONE',  101, DATE_SUB(NOW(), INTERVAL 10 DAY), 25, 'PASS',   '选品判断准确',                        DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (900504, @cid, 900303, 1, 'ONSITE', 101, DATE_ADD(NOW(), INTERVAL  2 DAY), 60, 'PENDING', NULL,                                 DATE_SUB(NOW(), INTERVAL  4 DAY));

-- match_score 与 application 无外键,靠 (job_id, candidate_id) 对齐;uk_job_candidate 唯一
INSERT INTO `opc_hr_match_score`
  (`id`, `company_id`, `job_id`, `candidate_id`, `score`, `reason`, `computed_at`)
VALUES
  (900601, @cid, 900101, 900201, 92.0, '抖音运营与千川投放经验完全匹配', DATE_SUB(NOW(), INTERVAL 22 DAY)),
  (900602, @cid, 900102, 900202, 88.0, '家居类目选品经验对口',           DATE_SUB(NOW(), INTERVAL 19 DAY)),
  (900603, @cid, 900103, 900203, 76.0, '客服经验合格,售后流程待确认',     DATE_SUB(NOW(), INTERVAL 11 DAY)),
  (900604, @cid, 900104, 900204, 71.0, '内容能力不错,投放经验偏少',       DATE_SUB(NOW(), INTERVAL  6 DAY)),
  (900605, @cid, 900103, 900201, 58.0, '方向不符',                         DATE_SUB(NOW(), INTERVAL 14 DAY));

-- =============================================================================
-- 5. 内容创作(Content)
--
-- platform 只有 DOUYIN 一个枚举值;publish 前置要求 script.status='READY'
-- (脚本状态机 DRAFT→READY→PUBLISHED)。
-- ⚠️ 本模块时间列是 created_at / updated_at,没有 create_by / create_time。
-- ⚠️ dashboard 的 seven_day_trend 是代码硬编码 List.of(0,0,0,0,0,0,0)
--    (OpcContentScriptServiceImpl.java:293),造数据也救不了那 7 个点。
-- =============================================================================

INSERT INTO `opc_content_script`
  (`company_id`, `user_id`, `type`, `title`, `content_json`, `content_md`, `word_count`, `status`, `created_at`)
VALUES
  (@cid, @uid, 'VIDEO', '秋季家居焕新季-抖音短视频脚本',
   '{"场景":"客厅","镜头":["开场痛点","产品特写","场景演示","促销引导"]}',
   '【开场】用了三年都没换的抱枕套,是不是也该换了?\n【特写】100% 棉麻,亲肤透气,洗多少次都不变形。\n【演示】雾霾蓝和米白两个色,搭北欧风客厅刚刚好。\n【引导】今天下单立减 30,库存有限先到先得。',
   186, 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (@cid, @uid, 'ARTICLE', '一人公司如何同时管好 6 个线上渠道',
   '{"结构":["现状","痛点","工具链","节奏安排"]}',
   '很多一个人创业的朋友,同时做着抖音、小红书、微信私域三个渠道,最后哪个都没做起来。\n我的做法是:先定一个主渠道(我选抖音),其余两个只做承接。',
   420, 'READY', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (@cid, @uid, 'VIDEO', '纯棉T恤面料讲解(待完善)',
   '{"场景":"工厂","镜头":["面料特写","克重对比","上身效果"]}',
   '【面料】220 克重,不是那种一洗就薄的廉价料。\n【对比】手里这块和普通 120 克重的对比一下厚度。',
   92, 'DRAFT', DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO `opc_content_platform_account`
  (`company_id`, `platform`, `account_name`, `account_id`, `access_token_enc`, `refresh_token_enc`,
   `expires_at`, `refresh_at`, `status`, `created_at`)
VALUES
  (@cid, 'DOUYIN', '明轩优选官方旗舰店', 'DY-88273615',
   'ENC(mock-access-token-for-demo)', 'ENC(mock-refresh-token-for-demo)',
   DATE_ADD(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY), 'ACTIVE', DATE_SUB(NOW(), INTERVAL 8 DAY));

-- publish 要求 script.status='READY'
INSERT INTO `opc_content_publish`
  (`company_id`, `script_id`, `platform_account_id`, `platform`, `title`, `tags`,
   `status`, `external_post_id`, `external_url`, `published_at`, `created_at`)
SELECT s.company_id, s.id, a.id, 'DOUYIN', s.title, '["家居","一人公司","运营"]',
       'SUCCESS', 'mock_post_demo_001', 'https://mock.douyin.com/video/mock_post_demo_001',
       DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM opc_content_script s, opc_content_platform_account a
WHERE s.company_id=@cid AND s.status='READY' AND s.title='一人公司如何同时管好 6 个线上渠道'
  AND a.company_id=@cid AND a.account_id='DY-88273615'
LIMIT 1;

-- tone / hashtags 都是 VARCHAR(存 JSON 字符串),不是 JSON 列
INSERT INTO `opc_content_adapt`
  (`company_id`, `source_script_id`, `adapted_script_id`, `target_platform`, `tone`, `hashtags`, `note`, `created_at`)
SELECT s1.company_id, s1.id, s2.id, 'DOUYIN', '轻松幽默', '["#家居好物","#抱枕","#北欧风"]',
       '把长图文改写成短视频口播,前 3 秒加痛点钩子', DATE_SUB(NOW(), INTERVAL 4 DAY)
FROM opc_content_script s1, opc_content_script s2
WHERE s1.company_id=@cid AND s1.status='PUBLISHED'
  AND s2.company_id=@cid AND s2.status='READY'
  AND s1.title='秋季家居焕新季-抖音短视频脚本' AND s2.title='一人公司如何同时管好 6 个线上渠道'
LIMIT 1;

-- =============================================================================
-- 6. 数据洞察(Insight)
--
-- ⚠️ company_id 必须等于当前登录用户所属公司 —— 4 个 controller 的
--    resolveCompanyId() 原先错用了 getUserId(),已修为 getCompanyId()(commit 1124103)。
--    未重建 aiopc-insight 容器前,这里的数据只有 user_id 恰好等于 company_id 时才可见。
-- level 取 HIGH/MEDIUM/LOW(DDL 注释写的 INFO/WARN/ERROR 与代码枚举不符,勿用)。
-- uk_company_period 唯一 → period 每天一条。
-- =============================================================================

INSERT INTO `opc_insight_daily_report`
  (`company_id`, `period`, `summary_md`, `kpi_json`, `advice_md`, `llm_used`, `create_by`, `create_time`)
VALUES
  (@cid, DATE_SUB(CURDATE(), INTERVAL 6 DAY), '[自动聚合·未走 LLM] 本日销售 3 单,营收环比持平,库存周转正常。',
   '{"totalRevenue":9800.0,"totalExpense":3200.0,"voucherCount":2,"walletBalance":2860.0}', '建议维持当前直播频次。', 'FALLBACK', @by, DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (@cid, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '[自动聚合·未走 LLM] 家居专场销售 19600 元,创本周单日新高。',
   '{"totalRevenue":19600.0,"totalExpense":5100.0,"voucherCount":3,"walletBalance":2860.0}', '可加大家居品类备货深度。', 'FALLBACK', @by, DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (@cid, DATE_SUB(CURDATE(), INTERVAL 4 DAY), '[自动聚合·未走 LLM] 销售回落,数码类库存偏低。',
   '{"totalRevenue":6200.0,"totalExpense":2400.0,"voucherCount":1,"walletBalance":2860.0}', '蓝牙音箱建议补货。', 'FALLBACK', @by, DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (@cid, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '[自动聚合·未走 LLM] 平台服务费入账,支出占比上升。',
   '{"totalRevenue":4200.0,"totalExpense":2600.0,"voucherCount":2,"walletBalance":2860.0}', '复核平台费率。', 'FALLBACK', @by, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (@cid, DATE_SUB(CURDATE(), INTERVAL 2 DAY), '[自动聚合·未走 LLM] 退货 1 单,售后成本小幅上升。',
   '{"totalRevenue":3100.0,"totalExpense":1800.0,"voucherCount":1,"walletBalance":2860.0}', '核对花色描述避免退货。', 'FALLBACK', @by, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (@cid, DATE_SUB(CURDATE(), INTERVAL 1 DAY), '[自动聚合·未走 LLM] 老客复购贡献 3580 元,私域运营见效。',
   '{"totalRevenue":3580.0,"totalExpense":1200.0,"voucherCount":1,"walletBalance":2860.0}', '可扩大私域触达频次。', 'FALLBACK', @by, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@cid, CURDATE(), '[自动聚合·未走 LLM] 当日数据尚未结算,仅有零星订单。',
   '{"totalRevenue":890.0,"totalExpense":300.0,"voucherCount":0,"walletBalance":2860.0}', '晚间复盘今日转化率。', 'FALLBACK', @by, NOW());

-- status: OPEN / ACK / RESOLVED / IGNORED
INSERT INTO `opc_insight_anomaly`
  (`company_id`, `period`, `level`, `rule_code`, `description`, `status`, `llm_confidence`, `create_by`, `create_time`)
VALUES
  (@cid, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'HIGH',   'WALLET_BALANCE_LOW',      '账户余额低于 3000 元阈值,智能体续费可能中断', 'OPEN', 0.95, @by, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@cid, CURDATE(),                               'MEDIUM', 'TOKEN_USAGE_SPIKE',       '本周 token 消耗较上周上升 42%',              'OPEN', 0.88, @by, NOW()),
  (@cid, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'MEDIUM', 'EXPENSE_EXCEEDS_REVENUE', '单日支出超过收入,请复核采购节奏',            'ACK',  0.91, @by, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (@cid, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 'LOW',    'REVENUE_DROP',            '数码类目连续两日销量下滑',                    'OPEN', 0.72, @by, DATE_SUB(NOW(), INTERVAL 4 DAY));

-- topic 必须落在白名单内,否则 AdviceServiceImpl.generate() 直接抛异常。
-- 本表没有 create_by 列,只有 create_time
INSERT INTO `opc_insight_advice` (`company_id`, `topic`, `advice_md`, `llm_used`, `confidence`, `create_time`)
VALUES
  (@cid, 'cost_optimization', '[降级建议·未走 LLM] 平台服务费与达人佣金合计占营收约 22%,建议把中腰部达人换成纯佣合作。', 'FALLBACK', 0.50, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (@cid, 'revenue_growth',    '[降级建议·未走 LLM] 私域复购客单价是公域的 1.8 倍,建议每周固定两次私域推送。',     'FALLBACK', 0.50, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (@cid, 'cashflow_health',   '[降级建议·未走 LLM] 现金流健康,但平台结算 T+7 建议预留至少 2 周运营资金。',        'FALLBACK', 0.50, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (@cid, 'tax_planning',      '[降级建议·未走 LLM] 进项税已充分取得,注意 11 月底前完成季度申报材料整理。',     'FALLBACK', 0.50, DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (@cid, 'risk_warning',      '[降级建议·未走 LLM] 单一渠道(抖音)贡献超 70% 营收,建议分散流量结构。',               'FALLBACK', 0.50, DATE_SUB(NOW(), INTERVAL 2 DAY));

-- =============================================================================
-- 7. 智能体中心(Agent Hub)
--
-- /instances 依赖 opc_company_member 有行;token_usage 的 biz_date 必须回填,
-- 否则按日期筛选的 /usage/daily 会把它们全过滤掉。
-- 本模块的 instance/task 没有 user_id 列(靠 company_id 关联成员);
-- token 列是 token_input / token_output / token_total。
-- definition_id 复用现有的 4 条 agent_definition(1 财务 / 3 ERP)。
-- =============================================================================

INSERT INTO `opc_agent_instance`
  (`company_id`, `definition_id`, `instance_code`, `nickname`, `hire_type`, `hire_duration`,
   `start_time`, `expire_time`, `token_used`, `token_quota`, `task_count`, `status`,
   `memory_namespace`, `create_by`, `create_time`)
VALUES
  (@cid, 1, 'INST20261010001', '小财',        'MONTHLY', 3, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(CURDATE(), INTERVAL  90 DAY), 48200, 200000, 3, 'RUNNING', 'mem_INST20261010001', @by, DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (@cid, 3, 'INST20261012002', '小仓',        'YEARLY',  1, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(CURDATE(), INTERVAL 365 DAY), 12900, 300000, 2, 'RUNNING', 'mem_INST20261012002', @by, DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (@cid, 1, 'INST20261020003', '小财(试用)', 'TRIAL',   1, DATE_SUB(NOW(), INTERVAL  3 DAY), DATE_ADD(CURDATE(), INTERVAL   7 DAY),   900,  20000, 0, 'PAUSED',  'mem_INST20261020003', @by, DATE_SUB(NOW(), INTERVAL  3 DAY));

INSERT INTO `opc_agent_task`
  (`company_id`, `instance_id`, `definition_id`, `task_code`, `task_type`, `session_id`,
   `input`, `output`, `status`, `token_input`, `token_output`, `token_total`, `cost`,
   `start_time`, `end_time`, `duration_ms`, `create_by`, `create_time`)
SELECT i.company_id, i.id, i.definition_id, 'TASK20261015001', 'CHAT', 'sess_demo_001',
       '汇总本月抖音小店的销售数据,按周对比并指出下滑原因。',
       '本周销售 35400 元,环比下降 12%,主要受数码类目缺货影响。',
       'SUCCESS', 3200, 1620, 4820, 0.086,
       DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 2400, @by, DATE_SUB(NOW(), INTERVAL 5 DAY)
FROM opc_agent_instance i WHERE i.instance_code='INST20261010001';

INSERT INTO `opc_agent_task`
  (`company_id`, `instance_id`, `definition_id`, `task_code`, `task_type`, `session_id`,
   `input`, `output`, `status`, `token_input`, `token_output`, `token_total`, `cost`,
   `start_time`, `end_time`, `duration_ms`, `create_by`, `create_time`)
SELECT i.company_id, i.id, i.definition_id, 'TASK20261018002', 'CHAT', 'sess_demo_002',
       '帮我生成本周的进销存日报草稿。',
       '本周入库 2 批,出库 3 单,库存周转天数约 21 天,蓝牙音箱库存偏低建议补货。',
       'SUCCESS', 4100, 2080, 6180, 0.112,
       DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 3100, @by, DATE_SUB(NOW(), INTERVAL 4 DAY)
FROM opc_agent_instance i WHERE i.instance_code='INST20261010001';

INSERT INTO `opc_agent_task`
  (`company_id`, `instance_id`, `definition_id`, `task_code`, `task_type`, `session_id`,
   `input`, `output`, `status`, `token_input`, `token_output`, `token_total`, `cost`,
   `start_time`, `create_by`, `create_time`)
SELECT i.company_id, i.id, i.definition_id, 'TASK20261020003', 'CHAT', 'sess_demo_003',
       '核对本月银行流水与凭证是否一致。', NULL,
       'RUNNING', 5200, 2700, 7900, 0.134,
       DATE_SUB(NOW(), INTERVAL 2 DAY), @by, DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM opc_agent_instance i WHERE i.instance_code='INST20261012002';

-- biz_date 必填,否则 /opc/agent/usage/daily?bizDate=... 会把它们过滤掉
INSERT INTO `opc_agent_token_usage`
  (`company_id`, `user_id`, `instance_id`, `usage_code`, `model`, `biz_date`,
   `token_input`, `token_output`, `token_total`, `cost`, `latency_ms`, `success`, `create_by`, `create_time`)
SELECT i.company_id, @uid, i.id, 'USG20261015001', 'qwen-max',  DATE_SUB(CURDATE(), INTERVAL 5 DAY),
       3200, 1620, 4820, 0.086, 2400, 1, @by, DATE_SUB(NOW(), INTERVAL 5 DAY)
FROM opc_agent_instance i WHERE i.instance_code='INST20261010001';

INSERT INTO `opc_agent_token_usage`
  (`company_id`, `user_id`, `instance_id`, `usage_code`, `model`, `biz_date`,
   `token_input`, `token_output`, `token_total`, `cost`, `latency_ms`, `success`, `create_by`, `create_time`)
SELECT i.company_id, @uid, i.id, 'USG20261018002', 'qwen-max',  DATE_SUB(CURDATE(), INTERVAL 4 DAY),
       4100, 2080, 6180, 0.112, 3100, 1, @by, DATE_SUB(NOW(), INTERVAL 4 DAY)
FROM opc_agent_instance i WHERE i.instance_code='INST20261010001';

INSERT INTO `opc_agent_token_usage`
  (`company_id`, `user_id`, `instance_id`, `usage_code`, `model`, `biz_date`,
   `token_input`, `token_output`, `token_total`, `cost`, `latency_ms`, `success`, `create_by`, `create_time`)
SELECT i.company_id, @uid, i.id, 'USG20261020003', 'qwen-plus', DATE_SUB(CURDATE(), INTERVAL 2 DAY),
       5200, 2700, 7900, 0.048, 5200, 1, @by, DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM opc_agent_instance i WHERE i.instance_code='INST20261012002';

-- =============================================================================
-- 工作流运行记录 —— 这里**不造假**
--
-- 以前这里硬插了 2 条运行记录(RUN20261016001 SUCCESS / RUN20261017002 FAILED),
-- trigger_type 都写 'CRON', output_result 是编的("财务日报已生成"),
-- error_message 是编的("LLM 超时")。
--
-- 为什么删掉:写它们的时候,ruoyi-job 模块**根本没被部署过** ——
-- 没有容器、没有 Dockerfile、网关也没有路由。调度器代码在仓库里,但
-- 一次都没被装起来跑过。于是"CRON"这个类型字段是在**冒充事实**:
-- 表里说定时器跑过,而实际上连定时器都没有。
--
-- 现在 ruoyi-job 已经真的跑起来了(定时任务是真触发),
-- 运行记录由 WorkflowTriggerServiceImpl 落库 —— 那才是事实。
--
-- 要造历史数据的话,正确的做法是**真的把它跑一遍**:
--   PUT /schedule/job/run  (后台「执行一次」)
-- 那条链路的 trigger_source='quartz:finance_daily_report_v1',
-- 和每天 09:00 定时触发产生的记录**完全一样**。
-- =============================================================================

-- =============================================================================
-- 8. 智能体市场(Marketplace)
--
-- 本模块的租户列不叫 company_id,而是 author_company_id / tenant_company_id。
-- mp_publication 没有 downloads / audit_status / rating 列,对应的是
-- install_count / audit_message / avg_rating。
-- 现有 opc_agent_definition 只覆盖 FINANCE/ERP/CRM 三类,本脚本不补全
-- (分类目录属于全局产品数据,不是租户测试数据)。
--
-- ⚠️ 计数器口径(这一段以前全是编的,现在改成"从真实表派生"):
--   view_count       = 0    浏览量没有对应表,只能从 0 起步,随详情页 GET 自增
--   install_count    = COUNT(mp_install)          —— 下面装完再回填
--   subscription_count = 0    订阅这个产品动作当时还不存在
--   avg_rating/rating_count = VISIBLE 评价的 AVG/COUNT —— 下面评价完再回填
--   以前这里写的是 812/342/96/4.6/58 这种数字,演示环境里一个都验证不了,
--   页面却拿它们当"有多少人在用"展示。宁可显示 0,也不要显示假的。
--
-- category_code 用小写:mp_category 的 code 全是小写(finance/data/tool/...),
-- 而列表页的 categoryName 走 Java Map<productType|code> 查表(大小写敏感),
-- 大写会匹配不上 → 卡片上显示原始 code 而不是"财务助手"。详情页走 SQL
-- (排序规则不敏感)所以显示正常 —— 同一个商品两个页面不一致的根因就在这。
-- =============================================================================

INSERT INTO `mp_author`
  (`author_company_id`, `author_name`, `contact_email`, `contact_phone`, `intro`,
   `level`, `total_agents`, `total_subscribers`, `total_revenue`, `status`, `create_by`, `create_time`)
VALUES
  (@cid, '明轩优选', 'owner@mingxuan.test', '13900001234',
   '一人公司自营品牌,主营家居用品与基础服饰,自建抖音与小红书渠道。',
   -- level 只能是 NORMAL / PREMIUM(AuthorLevel 枚举),以前写的 'SILVER' 是编的
   'NORMAL', 2, 0, 0.00, 'ACTIVE', @by, DATE_SUB(NOW(), INTERVAL 30 DAY));

INSERT INTO `mp_publication`
  (`product_type`, `agent_definition_id`, `module_id`, `author_id`, `code`, `name`, `category_code`,
   `summary`, `long_description`, `tags`, `status`, `published_at`, `audit_message`,
   `view_count`, `install_count`, `subscription_count`, `avg_rating`, `rating_count`, `create_by`, `create_time`)
SELECT 'MODULE', NULL, m.id, a.id, 'PUB-ECOM-001', '电商运营助手(明轩版)', 'ecom',
       '专为一人公司设计的电商运营助手,覆盖选品、直播排期与数据复盘。',
       '基于一人公司真实经营数据调优,内置抖音千川投放与小红书种草两套话术模板。',
       '["电商","运营","一人公司"]', 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 25 DAY), NULL,
       0, 0, 0, 0.00, 0, @by, DATE_SUB(NOW(), INTERVAL 26 DAY)
-- module_id 必须指向一个真实存在、且确实是电商运营的模块。
-- 以前这里是 (SELECT MIN(id) FROM opc_community_module) —— 也就是 id=1 的
-- 'crm-starter CRM 入门套件':一个"电商运营助手"指着 CRM 模块,谁看了都迷糊。
FROM mp_author a JOIN opc_community_module m ON m.code = 'ecom-ops'
WHERE a.author_company_id=@cid
LIMIT 1;

INSERT INTO `mp_publication`
  (`product_type`, `agent_definition_id`, `module_id`, `author_id`, `code`, `name`, `category_code`,
   `summary`, `long_description`, `tags`, `status`, `published_at`, `audit_message`,
   `view_count`, `install_count`, `subscription_count`, `avg_rating`, `rating_count`, `create_by`, `create_time`)
-- 名字带"(试用版)"以前和平台自营的「财务数字员工-小财」几乎一样,两个市场页
-- 摆在一起看着像同一个东西。这里明说是平台小财的开发者版本。
SELECT 'AGENT', d.id, NULL, a.id, 'PUB-FIN-001', '小财·一人公司版', 'finance',
       '自动记账、生成凭证、对账与日报,一人公司的财务管家。',
       '支持银行流水自动解析、凭证自动生成、增值税测算与月度经营简报。',
       '["财务","记账","自动化"]', 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 20 DAY), NULL,
       0, 0, 0, 0.00, 0, @by, DATE_SUB(NOW(), INTERVAL 21 DAY)
FROM mp_author a JOIN opc_agent_definition d ON d.agent_code='finance-agent'
WHERE a.author_company_id=@cid
LIMIT 1;

INSERT INTO `mp_install`
  (`publication_id`, `tenant_company_id`, `user_id`, `install_source`, `status`, `installed_at`, `create_by`, `create_time`)
SELECT p.id, @cid, @uid, 'MARKET', 'ACTIVE', DATE_SUB(NOW(), INTERVAL 15 DAY), @by, DATE_SUB(NOW(), INTERVAL 15 DAY)
FROM mp_publication p WHERE p.code='PUB-FIN-001' LIMIT 1;

INSERT INTO `mp_review`
  (`publication_id`, `tenant_company_id`, `user_id`, `rating`, `content`, `status`, `create_by`, `create_time`)
-- status 必须是 'VISIBLE':ReviewServiceImpl 建评价时写死的就是这个值,
-- 而评分统计的 SQL(MpReviewMapper.averageForPublication / countForPublication)
-- 也只认 'VISIBLE'。以前这里插 'PUBLISHED',统计看不见它 → 卡片上写着
-- "4.8 分 127 条评价",而真实是 0 条 0.00 分。
SELECT p.id, @cid, @uid, 5, '用了两周,每天的经营简报确实省事,凭证基本不用手工录了。', 'VISIBLE', @by, DATE_SUB(NOW(), INTERVAL 12 DAY)
FROM mp_publication p WHERE p.code='PUB-FIN-001' LIMIT 1;

-- ---- 计数器回填:从刚写进去的真实行算,而不是再写一遍数字 ----
-- install_count 的不变量:恒等于该商品的 mp_install 行数
-- (InstallServiceImpl 只在「新插入行」时 +1,复活既有行不 +1,而行只在首次安装时插入)
UPDATE mp_publication p
   SET install_count = (SELECT COUNT(*) FROM mp_install i WHERE i.publication_id = p.id)
 WHERE p.code IN ('PUB-ECOM-001', 'PUB-FIN-001');

-- avg_rating / rating_count 的不变量:VISIBLE 评价的 AVG 与 COUNT
UPDATE mp_publication p
   SET rating_count = (SELECT COUNT(*) FROM mp_review r
                        WHERE r.publication_id = p.id AND r.status = 'VISIBLE' AND r.deleted = 0),
       avg_rating   = (SELECT COALESCE(AVG(r.rating), 0) FROM mp_review r
                        WHERE r.publication_id = p.id AND r.status = 'VISIBLE' AND r.deleted = 0)
 WHERE p.code IN ('PUB-ECOM-001', 'PUB-FIN-001');

-- =============================================================================
-- 9. 通知中心 / 个人中心
--
-- opc_user_profile 不会在注册时自动创建(GET 只返回内存空对象不落库),
-- 必须显式插入,否则个人中心页面空。
-- =============================================================================

INSERT INTO `opc_notification_inbox` (`user_id`, `type`, `title`, `body`, `link`, `read_at`, `created_at`, `create_by`)
VALUES
  (@uid, 'ORDER',  '采购单 PO20261018003 已入库', '本次采购入库 60 件蓝牙音箱,批次 B20261018-01,剩余 42 件。', '/erp/inventory',      NULL,                       DATE_SUB(NOW(), INTERVAL 3 DAY), @by),
  (@uid, 'SYSTEM', '本月税务申报材料待整理',     '距季度申报截止还有 15 天,请完成进项发票归集。',             '/finance/tax-reports', NULL,                       DATE_SUB(NOW(), INTERVAL 1 DAY), @by),
  (@uid, 'AGENT',  '智能体「小财」任务已完成',   '任务 TASK20261018002 执行成功,消耗 6180 token。',          '/agent/instances',     DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), @by);

INSERT INTO `opc_user_profile`
  (`user_id`, `user_type`, `real_name`, `mobile`, `industry`, `city`, `bio`,
   `invitation_code`, `verified`, `verified_time`, `status`, `create_by`, `create_time`)
VALUES
  (@uid, 'ENTREPRENEUR', '陈明轩', '13900001234', '电子商务/零售批发', '杭州市',
   '一人公司创始人,主营家居用品与服饰的线上零售。', 'MX7K2P9Q', 1, NOW(), 'ACTIVE', @by, DATE_SUB(NOW(), INTERVAL 20 DAY));

INSERT INTO `opc_invitation`
  (`invite_code`, `inviter_id`, `invitee_id`, `invitee_mobile`, `max_uses`, `used_count`,
   `expire_time`, `used_time`, `status`, `create_by`, `create_time`)
VALUES
  ('MX4T8R2K', @uid, NULL, '13600009901', 1, 0, DATE_ADD(NOW(), INTERVAL 90 DAY), NULL, 'ACTIVE', @by, DATE_SUB(NOW(), INTERVAL 10 DAY)),
  ('MX9W3H6D', @uid, NULL, NULL,          3, 0, DATE_ADD(NOW(), INTERVAL 90 DAY), NULL, 'ACTIVE', @by, DATE_SUB(NOW(), INTERVAL  3 DAY));

-- =============================================================================
-- 10. CRM
--
-- ⚠️ opc_crm_* 这批表没有 company_id,是全局共享的 —— 新账号会同时看到
--    admin 名下原有的 10 个客户/30 个联系人,这是预期行为。
--    这里补的记录 owner_id 全部指向新用户(101),用于测「我负责的客户」筛选。
--    子表用子查询取 customer.id,避免硬编码自增 id。
-- =============================================================================

INSERT INTO `opc_crm_customer` (`name`, `source`, `tags`, `owner_id`, `level`, `phone`, `email`, `address`, `remark`, `create_by`, `create_time`)
VALUES
  ('杭州拾光家居旗舰店', 'AD',        '家居,月结',    @uid, 'A', '13905720001', 'sgjj@test.com', '浙江省杭州市拱墅区',   '月结客户,单月采购额约 5 万', @by, DATE_SUB(NOW(), INTERVAL 20 DAY)),
  ('苏州悦享生活馆',     'REFERRAL',  '家居,直播',    @uid, 'B', '13905720002', 'yzsh@test.com', '江苏省苏州市工业园区', '家居专场长期合作',        @by, DATE_SUB(NOW(), INTERVAL 14 DAY)),
  ('南京简屿文化用品',   'EXHIBITION','办公,小批量', @uid, 'C', '13905720003', 'jysw@test.com', '江苏省南京市玄武区',   '展会认识,小批量试单',    @by, DATE_SUB(NOW(), INTERVAL  6 DAY));

INSERT INTO `opc_crm_contact` (`customer_id`, `name`, `phone`, `email`, `position`, `is_primary`, `remark`, `create_by`, `create_time`)
SELECT c.id, '张经理', '13905720011', 'zhang@test.com', '采购经理',   1, '对接日常补货', @by, NOW() FROM opc_crm_customer c WHERE c.name='杭州拾光家居旗舰店' AND c.owner_id=@uid
UNION ALL SELECT c.id, '李助理', '13905720012', 'li@test.com',   '采购助理',   0, '对接发票与账期', @by, NOW() FROM opc_crm_customer c WHERE c.name='杭州拾光家居旗舰店' AND c.owner_id=@uid
UNION ALL SELECT c.id, '王店长', '13905720021', 'wang@test.com', '门店负责人', 1, '对接专场排期',   @by, NOW() FROM opc_crm_customer c WHERE c.name='苏州悦享生活馆'     AND c.owner_id=@uid
UNION ALL SELECT c.id, '赵采购', '13905720022', 'zhao@test.com', '采购',       0, '对接价格',       @by, NOW() FROM opc_crm_customer c WHERE c.name='苏州悦享生活馆'     AND c.owner_id=@uid
UNION ALL SELECT c.id, '孙总',   '13905720031', 'sun@test.com',   '负责人',     1, '直接对接',       @by, NOW() FROM opc_crm_customer c WHERE c.name='南京简屿文化用品'   AND c.owner_id=@uid
UNION ALL SELECT c.id, '周助理', '13905720032', 'zhou@test.com', '行政',       0, '对接合同流程',   @by, NOW() FROM opc_crm_customer c WHERE c.name='南京简屿文化用品'   AND c.owner_id=@uid;

INSERT INTO `opc_crm_follow_up` (`customer_id`, `contact_id`, `type`, `content`, `next_at`, `owner_id`, `create_by`, `create_time`)
SELECT c.id, ct.id, 'PHONE', '沟通下季度补货计划,对方倾向先订 T 恤 500 件。', DATE_ADD(NOW(), INTERVAL 3 DAY), @uid, @by, DATE_SUB(NOW(), INTERVAL 5 DAY)
  FROM opc_crm_customer c JOIN opc_crm_contact ct ON ct.customer_id=c.id AND ct.name='张经理' WHERE c.name='杭州拾光家居旗舰店' AND c.owner_id=@uid
UNION ALL SELECT c.id, ct.id, 'EMAIL', '发送了月结账期说明,对方财务已确认。', DATE_ADD(NOW(), INTERVAL 7 DAY), @uid, @by, DATE_SUB(NOW(), INTERVAL 3 DAY)
  FROM opc_crm_customer c JOIN opc_crm_contact ct ON ct.customer_id=c.id AND ct.name='李助理' WHERE c.name='杭州拾光家居旗舰店' AND c.owner_id=@uid
UNION ALL SELECT c.id, ct.id, 'VISIT', '上门拜访,确认双十一专场参与意向。', DATE_ADD(NOW(), INTERVAL 5 DAY), @uid, @by, DATE_SUB(NOW(), INTERVAL 2 DAY)
  FROM opc_crm_customer c JOIN opc_crm_contact ct ON ct.customer_id=c.id AND ct.name='王店长' WHERE c.name='苏州悦享生活馆'     AND c.owner_id=@uid
UNION ALL SELECT c.id, ct.id, 'PHONE', '报价 39/49 两档,对方倾向 49 元款。', DATE_ADD(NOW(), INTERVAL 4 DAY), @uid, @by, DATE_SUB(NOW(), INTERVAL 2 DAY)
  FROM opc_crm_customer c JOIN opc_crm_contact ct ON ct.customer_id=c.id AND ct.name='赵采购' WHERE c.name='苏州悦享生活馆'     AND c.owner_id=@uid
UNION ALL SELECT c.id, ct.id, 'PHONE', '展会后首次跟进,对方要小批量试单。', DATE_ADD(NOW(), INTERVAL 6 DAY), @uid, @by, DATE_SUB(NOW(), INTERVAL 1 DAY)
  FROM opc_crm_customer c JOIN opc_crm_contact ct ON ct.customer_id=c.id AND ct.name='孙总'   WHERE c.name='南京简屿文化用品'   AND c.owner_id=@uid
UNION ALL SELECT c.id, ct.id, 'EMAIL', '发去公司介绍与报价单,待回复。', DATE_ADD(NOW(), INTERVAL 9 DAY), @uid, @by, DATE_SUB(NOW(), INTERVAL 1 DAY)
  FROM opc_crm_customer c JOIN opc_crm_contact ct ON ct.customer_id=c.id AND ct.name='周助理' WHERE c.name='南京简屿文化用品'   AND c.owner_id=@uid;

-- score / score_reason 故意不给(列默认值 0 / NULL)—— 「还没评过」。
--
-- 这两条原来写死了 82 分和 75 分,还配了像模像样的理由(「对方采购预算明确,决策链短」)。
-- 可那会儿 CRM 的「LLM 评分」按钮调的是一个**从没被实现过的地址** ——
-- 系统里没有任何一条代码路径能算出过任何分数。所以这两个分数(连同理由一起)
-- 是编的: 理由写得再像, 也只是一段手写的字符串。
--
-- 真实起点是「还没评过」: 点一次「LLM 评分」, 让模型给一个, 理由也跟着一起落库。
INSERT INTO `opc_crm_opportunity` (`customer_id`, `name`, `amount`, `stage`, `expected_close`, `owner_id`, `create_by`, `create_time`)
VALUES
  ((SELECT id FROM opc_crm_customer WHERE name='杭州拾光家居旗舰店' AND owner_id=@uid),
   '拾光家居-季度补货(1500 件)', 88500.00,  'NEGOTIATION', DATE_ADD(CURDATE(), INTERVAL 25 DAY), @uid, @by, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  ((SELECT id FROM opc_crm_customer WHERE name='苏州悦享生活馆' AND owner_id=@uid),
   '悦享生活-双十一专场备货',    118000.00, 'PROPOSAL',    DATE_ADD(CURDATE(), INTERVAL 35 DAY), @uid, @by, DATE_SUB(NOW(), INTERVAL 4 DAY));

COMMIT;

-- =============================================================================
-- 收尾自检
-- =============================================================================
SELECT 'ERP 进销存' AS 模块,
 (SELECT COUNT(*) FROM opc_erp_supplier      WHERE company_id=@cid) AS 供应商,
 (SELECT COUNT(*) FROM opc_erp_product       WHERE company_id=@cid) AS 商品,
 (SELECT COUNT(*) FROM opc_erp_product_sku   WHERE company_id=@cid) AS SKU,
 (SELECT COUNT(*) FROM opc_erp_sale          WHERE company_id=@cid) AS 销售单,
 (SELECT COUNT(*) FROM opc_erp_purchase      WHERE company_id=@cid) AS 采购单,
 (SELECT COUNT(*) FROM opc_erp_daily_snapshot WHERE company_id=@cid) AS 库存快照;
SELECT '财务管理' AS 模块,
 (SELECT COUNT(*) FROM opc_finance_voucher    WHERE company_id=@cid) AS 凭证,
 (SELECT COUNT(*) FROM opc_finance_bank_flow  WHERE company_id=@cid) AS 流水,
 (SELECT COUNT(*) FROM opc_finance_tax_report WHERE company_id=@cid) AS 税表;
SELECT '计费中心' AS 模块,
 (SELECT COUNT(*) FROM opc_wallet        WHERE company_id=@cid) AS 钱包,
 (SELECT COUNT(*) FROM opc_billing_order WHERE company_id=@cid) AS 订单,
 (SELECT COUNT(*) FROM opc_transaction    WHERE company_id=@cid) AS 交易流水;
SELECT '人力资源' AS 模块,
 (SELECT COUNT(*) FROM opc_hr_job         WHERE company_id=@cid) AS 职位,
 (SELECT COUNT(*) FROM opc_hr_candidate   WHERE company_id=@cid) AS 候选人,
 (SELECT COUNT(*) FROM opc_hr_application WHERE company_id=@cid) AS 投递,
 (SELECT COUNT(*) FROM opc_hr_interview   WHERE company_id=@cid) AS 面试,
 (SELECT COUNT(*) FROM opc_hr_offer       WHERE company_id=@cid) AS Offer;
SELECT '内容创作' AS 模块,
 (SELECT COUNT(*) FROM opc_content_script  WHERE company_id=@cid) AS 脚本,
 (SELECT COUNT(*) FROM opc_content_publish WHERE company_id=@cid) AS 发布,
 (SELECT COUNT(*) FROM opc_content_adapt   WHERE company_id=@cid) AS 适配;
SELECT '数据洞察' AS 模块,
 (SELECT COUNT(*) FROM opc_insight_daily_report WHERE company_id=@cid) AS 日报,
 (SELECT COUNT(*) FROM opc_insight_anomaly      WHERE company_id=@cid) AS 异常,
 (SELECT COUNT(*) FROM opc_insight_advice       WHERE company_id=@cid) AS 建议;
SELECT '智能体' AS 模块,
 (SELECT COUNT(*) FROM opc_agent_instance     WHERE company_id=@cid) AS 实例,
 (SELECT COUNT(*) FROM opc_agent_task         WHERE company_id=@cid) AS 任务,
 (SELECT COUNT(*) FROM opc_agent_token_usage  WHERE company_id=@cid) AS 用量,
 (SELECT COUNT(*) FROM opc_agent_workflow_run WHERE company_id=@cid) AS 工作流运行;
SELECT '市场/通知/个人' AS 模块,
 (SELECT COUNT(*) FROM mp_author WHERE author_company_id=@cid) AS 作者,
 (SELECT COUNT(*) FROM mp_publication WHERE author_id IN (SELECT id FROM mp_author WHERE author_company_id=@cid)) AS 作品,
 (SELECT COUNT(*) FROM opc_notification_inbox WHERE user_id=@uid) AS 站内信,
 (SELECT COUNT(*) FROM opc_user_profile       WHERE user_id=@uid) AS 用户画像,
 (SELECT COUNT(*) FROM opc_invitation         WHERE inviter_id=@uid) AS 邀请码;
SELECT 'CRM(全局表,owner=新用户)' AS 模块,
 (SELECT COUNT(*) FROM opc_crm_customer    WHERE owner_id=@uid) AS 客户,
 (SELECT COUNT(*) FROM opc_crm_contact     WHERE customer_id IN (SELECT id FROM opc_crm_customer WHERE owner_id=@uid)) AS 联系人,
 (SELECT COUNT(*) FROM opc_crm_follow_up   WHERE owner_id=@uid) AS 跟进,
 (SELECT COUNT(*) FROM opc_crm_opportunity WHERE owner_id=@uid) AS 商机;