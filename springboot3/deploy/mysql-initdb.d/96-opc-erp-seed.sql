-- ============================================================
-- opc-erp seed (W72 · opc-erp 9311)
-- 5 个默认供应商 (id 1-5, 覆盖 3 等级 NORMAL/PREFERRED)
-- 用 INSERT IGNORE 幂等
-- ============================================================

INSERT IGNORE INTO `opc_erp_supplier` (`id`, `company_id`, `name`, `contact`, `phone`, `level`, `created_by`)
VALUES
  (1, 1, '默认供应商',     '王经理', '13800000001', 'PREFERRED', 0),
  (2, 1, '深圳电子供应商', '李总',   '13800000002', 'NORMAL',    0),
  (3, 1, '上海服装批发',   '张总',   '13800000003', 'NORMAL',    0),
  (4, 1, '华东物流仓',     '赵总',   '13800000004', 'PREFERRED', 0),
  (5, 1, '临时供应商',     NULL,     NULL,          'NORMAL',    0);
