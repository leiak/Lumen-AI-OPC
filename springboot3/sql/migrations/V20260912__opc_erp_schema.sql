-- =============================================================================
-- V20260912__opc_erp_schema.sql
-- OPC ERP 进销存 (W72 / opc-erp) · 10 张业务表 + 5 条 seed 供应商
--
-- 目标库：OPC 业务库（与 opc_crm / opc_hr / opc_community 同 schema）
-- 执行方式：
--     1) Flyway 启用前：mysql -u<user> -p <opc_db> < sql/migrations/V20260912__opc_erp_schema.sql
--     2) Flyway 启用后：放入 Flyway classpath:db/migration/ 自动按版本号执行
--
-- 表清单：
--   1. opc_erp_supplier          供应商（NORMAL/PREFERRED/BLOCKED）
--   2. opc_erp_product           商品（规格 root,描述）
--   3. opc_erp_product_sku       SKU（动态笛卡尔积规格,@Version 乐观锁）
--   4. opc_erp_batch             批次（FIFO 按 production_date ASC 扣减）
--   5. opc_erp_inventory_log     库存流水（PURCHASE_IN/SALE_OUT/...）
--   6. opc_erp_purchase          采购单（DRAFT/CONFIRMED/COMPLETED/CANCELLED）
--   7. opc_erp_purchase_item     采购明细（批次关联）
--   8. opc_erp_sale              销售单（同采购状态机）
--   9. opc_erp_sale_item         销售明细（batch_id FIFO 指派）
--  10. opc_erp_return            退货单（SALES_RETURN/SUPPLIER_RETURN）
--
-- 设计约束：
--   * company_id NOT NULL 强约束,所有查询必须带 companyId(W50 / W52 教训)
--   * utf8mb4_unicode_ci 全表统一
--   * 所有表 KEY idx_company_xxx (company_id, ...) 复合索引
--   * 大金额 DECIMAL(18,2),主键 BIGINT(雪花 ID)
--   * 幂等性: MySQL 8 原生支持 CREATE TABLE IF NOT EXISTS,无需 stored procedure
--     (stored procedure 仅在 ALTER TABLE ADD COLUMN 时需要,见 V20260905)
-- =============================================================================

USE ry-vue-opc;

-- -----------------------------------------------------------------------------
-- 1) 供应商
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_supplier` (
  `id`          BIGINT       NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`  BIGINT       NOT NULL                                COMMENT '所属公司',
  `name`        VARCHAR(128) NOT NULL                                COMMENT '供应商名称',
  `contact`     VARCHAR(64)                                                   COMMENT '联系人',
  `phone`       VARCHAR(32)                                                   COMMENT '电话',
  `email`       VARCHAR(128)                                                  COMMENT '邮箱',
  `address`     VARCHAR(256)                                                  COMMENT '地址',
  `level`       VARCHAR(16)  NOT NULL DEFAULT 'NORMAL'                 COMMENT 'NORMAL/PREFERRED/BLOCKED',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'                 COMMENT 'ACTIVE/INACTIVE',
  `created_by`  BIGINT       NOT NULL                                COMMENT '创建人',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_company_name` (`company_id`, `name`),
  UNIQUE KEY `uk_company_supplier_name` (`company_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 供应商';

-- -----------------------------------------------------------------------------
-- 2) 商品
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_product` (
  `id`          BIGINT       NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`  BIGINT       NOT NULL                                COMMENT '所属公司',
  `sku_root`    VARCHAR(64)  NOT NULL                                COMMENT 'SKU 根编码(前缀)',
  `name`        VARCHAR(128) NOT NULL                                COMMENT '商品名称',
  `category`    VARCHAR(64)                                                   COMMENT '商品分类',
  `brand`       VARCHAR(64)                                                   COMMENT '品牌',
  `unit`        VARCHAR(16)  NOT NULL DEFAULT '件'                    COMMENT '计量单位',
  `description` TEXT                                                       COMMENT '商品描述',
  `spec_attrs`  JSON         NOT NULL                                COMMENT '规格属性 JSON:[{"name":"颜色","values":["黑","白"]}]',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'                COMMENT 'ACTIVE/INACTIVE',
  `created_by`  BIGINT       NOT NULL                                COMMENT '创建人',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_company_name` (`company_id`, `name`),
  UNIQUE KEY `uk_company_sku_root` (`company_id`, `sku_root`),
  KEY `idx_company_category` (`company_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 商品';

-- -----------------------------------------------------------------------------
-- 3) 商品 SKU（动态笛卡尔积规格）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_product_sku` (
  `id`          BIGINT       NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`  BIGINT       NOT NULL                                COMMENT '所属公司',
  `product_id`  BIGINT       NOT NULL                                COMMENT '所属商品',
  `sku_code`    VARCHAR(128) NOT NULL                                COMMENT 'SKU 编码(=sku_root + spec values)',
  `spec_json`   JSON                                                      COMMENT '规格组合 JSON:{"颜色":"黑","尺码":"M"}',
  `price`       DECIMAL(18,2) NOT NULL DEFAULT 0                      COMMENT '售价',
  `cost`        DECIMAL(18,2)          DEFAULT 0                      COMMENT '成本价',
  `stock`       INT          NOT NULL DEFAULT 0                      COMMENT '当前库存',
  `threshold`   INT          NOT NULL DEFAULT 10                     COMMENT '低库存阈值',
  `version`     BIGINT       NOT NULL DEFAULT 0                      COMMENT '@Version 乐观锁',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'                COMMENT 'ACTIVE/INACTIVE',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_company_product` (`company_id`, `product_id`),
  UNIQUE KEY `uk_company_sku_code` (`company_id`, `sku_code`),
  KEY `idx_company_stock` (`company_id`, `stock`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 商品 SKU';

-- -----------------------------------------------------------------------------
-- 4) 批次（FIFO 按 production_date ASC 扣减）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_batch` (
  `id`              BIGINT       NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`      BIGINT       NOT NULL                                COMMENT '所属公司',
  `sku_id`          BIGINT       NOT NULL                                COMMENT '所属 SKU',
  `batch_no`        VARCHAR(64)  NOT NULL                                COMMENT '批次号',
  `quantity`        INT          NOT NULL                                COMMENT '原始入库数量',
  `remaining`       INT          NOT NULL                                COMMENT '剩余可售数量(FIFO 扣减)',
  `production_date` DATE                                                      COMMENT '生产日期',
  `expiry_date`     DATE                                                      COMMENT '过期日期',
  `supplier_id`     BIGINT                                                    COMMENT '供应商 ID',
  `purchase_id`     BIGINT                                                    COMMENT '关联采购单 ID',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_company_sku` (`company_id`, `sku_id`),
  KEY `idx_company_sku_prod` (`company_id`, `sku_id`, `production_date`),
  KEY `idx_company_sku_remaining_fifo` (`company_id`, `sku_id`, `remaining`, `production_date`),
  UNIQUE KEY `uk_company_batch_no` (`company_id`, `batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 批次(FIFO)';

-- -----------------------------------------------------------------------------
-- 5) 库存流水
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_inventory_log` (
  `id`          BIGINT       NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`  BIGINT       NOT NULL                                COMMENT '所属公司',
  `sku_id`      BIGINT       NOT NULL                                COMMENT 'SKU ID',
  `batch_id`    BIGINT                                                    COMMENT '批次 ID(可空,非批次场景)',
  `change`      INT          NOT NULL                                COMMENT '变化数量(正入库/负出库)',
  `type`        VARCHAR(32)  NOT NULL                                COMMENT 'PURCHASE_IN/SALE_OUT/SALES_RETURN_IN/SUPPLIER_RETURN_OUT/ADJUST',
  `ref_type`    VARCHAR(32)                                                   COMMENT '引用类型:PURCHASE/SALE/RETURN/MANUAL',
  `ref_id`      BIGINT                                                    COMMENT '引用单据 ID',
  `remark`      VARCHAR(512)                                                  COMMENT '备注',
  `created_by`  BIGINT       NOT NULL                                COMMENT '创建人',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  KEY `idx_company_sku_time` (`company_id`, `sku_id`, `create_time`),
  KEY `idx_company_ref` (`company_id`, `ref_type`, `ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 库存流水';

-- -----------------------------------------------------------------------------
-- 6) 采购单
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_purchase` (
  `id`            BIGINT        NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`    BIGINT        NOT NULL                                COMMENT '所属公司',
  `purchase_no`   VARCHAR(64)   NOT NULL                                COMMENT '采购单号:PO-yyyyMMdd-XXXX',
  `supplier_id`   BIGINT        NOT NULL                                COMMENT '供应商 ID',
  `total_amount`  DECIMAL(18,2) NOT NULL DEFAULT 0                      COMMENT '采购总金额',
  `status`        VARCHAR(16)   NOT NULL DEFAULT 'DRAFT'                 COMMENT 'DRAFT/CONFIRMED/COMPLETED/CANCELLED',
  `operator_id`   BIGINT        NOT NULL                                COMMENT '经办人',
  `confirmed_by`  BIGINT                                                     COMMENT '确认人',
  `confirmed_at`  DATETIME                                                  COMMENT '确认时间',
  `completed_at`  DATETIME                                                  COMMENT '入库完成时间',
  `cancelled_at`  DATETIME                                                  COMMENT '取消时间',
  `remark`        VARCHAR(512)                                             COMMENT '备注',
  `created_by`    BIGINT        NOT NULL                                COMMENT '创建人',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_company_status` (`company_id`, `status`),
  KEY `idx_company_supplier` (`company_id`, `supplier_id`),
  UNIQUE KEY `uk_company_purchase_no` (`company_id`, `purchase_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 采购单';

-- -----------------------------------------------------------------------------
-- 7) 采购明细
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_purchase_item` (
  `id`              BIGINT        NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`      BIGINT        NOT NULL                                COMMENT '所属公司',
  `purchase_id`     BIGINT        NOT NULL                                COMMENT '采购单 ID',
  `sku_id`          BIGINT        NOT NULL                                COMMENT 'SKU ID',
  `quantity`        INT           NOT NULL                                COMMENT '采购数量',
  `unit_price`      DECIMAL(18,2) NOT NULL                                COMMENT '单价',
  `subtotal`        DECIMAL(18,2) NOT NULL                                COMMENT '小计 = qty * unit_price',
  `batch_no`        VARCHAR(64)                                               COMMENT '批次号',
  `production_date` DATE                                                      COMMENT '生产日期',
  `expiry_date`     DATE                                                      COMMENT '过期日期',
  KEY `idx_company_purchase` (`company_id`, `purchase_id`),
  KEY `idx_company_sku` (`company_id`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 采购明细';

-- -----------------------------------------------------------------------------
-- 8) 销售单
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_sale` (
  `id`             BIGINT        NOT NULL PRIMARY KEY                   COMMENT '雪花 ID',
  `company_id`     BIGINT        NOT NULL                                COMMENT '所属公司',
  `sale_no`        VARCHAR(64)   NOT NULL                                COMMENT '销售单号:SO-yyyyMMdd-XXXX',
  `customer_name`  VARCHAR(128)  NOT NULL                                COMMENT '客户名称',
  `customer_phone` VARCHAR(32)                                            COMMENT '客户电话(销退通知)',
  `total_amount`   DECIMAL(18,2) NOT NULL DEFAULT 0                      COMMENT '销售总金额',
  `status`         VARCHAR(16)   NOT NULL DEFAULT 'DRAFT'                 COMMENT 'DRAFT/CONFIRMED/COMPLETED/CANCELLED',
  `operator_id`    BIGINT        NOT NULL                                COMMENT '经办人',
  `confirmed_by`   BIGINT                                                     COMMENT '确认人',
  `confirmed_at`   DATETIME                                                  COMMENT '确认时间',
  `completed_at`   DATETIME                                                  COMMENT '出库完成时间',
  `cancelled_at`   DATETIME                                                  COMMENT '取消时间',
  `remark`         VARCHAR(512)                                             COMMENT '备注',
  `created_by`     BIGINT        NOT NULL                                COMMENT '创建人',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_company_status` (`company_id`, `status`),
  UNIQUE KEY `uk_company_sale_no` (`company_id`, `sale_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 销售单';

-- -----------------------------------------------------------------------------
-- 9) 销售明细（batch_id FIFO 指派）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_sale_item` (
  `id`          BIGINT        NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`  BIGINT        NOT NULL                                COMMENT '所属公司',
  `sale_id`     BIGINT        NOT NULL                                COMMENT '销售单 ID',
  `sku_id`      BIGINT        NOT NULL                                COMMENT 'SKU ID',
  `quantity`    INT           NOT NULL                                COMMENT '销售数量',
  `unit_price`  DECIMAL(18,2) NOT NULL                                COMMENT '单价',
  `subtotal`    DECIMAL(18,2) NOT NULL                                COMMENT '小计',
  `batch_id`    BIGINT                                                     COMMENT 'FIFO 指派的批次 ID',
  KEY `idx_company_sale` (`company_id`, `sale_id`),
  KEY `idx_company_sku` (`company_id`, `sku_id`),
  KEY `idx_company_batch` (`company_id`, `batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 销售明细';

-- -----------------------------------------------------------------------------
-- 10) 退货单
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_return` (
  `id`            BIGINT        NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`    BIGINT        NOT NULL                                COMMENT '所属公司',
  `return_no`     VARCHAR(64)   NOT NULL                                COMMENT '退货单号:RT-yyyyMMdd-XXXX',
  `return_type`   VARCHAR(32)   NOT NULL                                COMMENT 'SALES_RETURN/SUPPLIER_RETURN',
  `ref_id`        BIGINT        NOT NULL                                COMMENT '引用单据 ID(sale 或 purchase)',
  `refund_amount` DECIMAL(18,2) NOT NULL DEFAULT 0                      COMMENT '退款金额',
  `status`        VARCHAR(16)   NOT NULL DEFAULT 'DRAFT'                 COMMENT 'DRAFT/CONFIRMED/COMPLETED/CANCELLED',
  `operator_id`   BIGINT        NOT NULL                                COMMENT '经办人',
  `confirmed_by`  BIGINT                                                     COMMENT '确认人',
  `confirmed_at`  DATETIME                                                  COMMENT '确认时间',
  `completed_at`  DATETIME                                                  COMMENT '退货完成时间',
  `reason`        VARCHAR(256)                                             COMMENT '退货原因',
  `created_by`    BIGINT        NOT NULL                                COMMENT '创建人',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY `idx_company_status` (`company_id`, `status`),
  KEY `idx_company_type_status` (`company_id`, `return_type`, `status`),
  UNIQUE KEY `uk_company_return_no` (`company_id`, `return_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 退货单';

-- -----------------------------------------------------------------------------
-- 11) 库存每日快照（Task 7 — Quartz 每日 23:55 落库，供日报/月报查询）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `opc_erp_daily_snapshot` (
  `id`             BIGINT       NOT NULL PRIMARY KEY                    COMMENT '雪花 ID',
  `company_id`     BIGINT       NOT NULL                                COMMENT '所属公司',
  `snapshot_date`  DATE         NOT NULL                                COMMENT '快照日期',
  `sku_id`         BIGINT       NOT NULL                                COMMENT 'SKU ID',
  `opening_stock`  INT          NOT NULL DEFAULT 0                      COMMENT '期初库存',
  `in_qty`         INT          NOT NULL DEFAULT 0                      COMMENT '当日入库合计',
  `out_qty`        INT          NOT NULL DEFAULT 0                      COMMENT '当日出库合计',
  `closing_stock`  INT          NOT NULL DEFAULT 0                      COMMENT '期末库存',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  KEY `idx_company_date` (`company_id`, `snapshot_date`),
  KEY `idx_company_date_sku` (`company_id`, `snapshot_date`, `sku_id`),
  UNIQUE KEY `uk_company_date_sku` (`company_id`, `snapshot_date`, `sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ERP 库存每日快照';

-- -----------------------------------------------------------------------------
-- Seed: 5 个默认供应商
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `opc_erp_supplier` (`id`, `company_id`, `name`, `contact`, `phone`, `level`, `created_by`)
VALUES
  (1, 1, '默认供应商',     '王经理', '13800000001', 'PREFERRED', 0),
  (2, 1, '深圳电子供应商', '李总',   '13800000002', 'NORMAL',    0),
  (3, 1, '上海服装批发',   '张总',   '13800000003', 'NORMAL',    0),
  (4, 1, '华东物流仓',     '赵总',   '13800000004', 'PREFERRED', 0),
  (5, 1, '临时供应商',     NULL,     NULL,          'NORMAL',    0);
