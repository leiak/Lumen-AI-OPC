# opc-erp (进销存) 服务设计 Spec

> **日期**: 2026-09-12
> **状态**: draft v1（待用户审批）
> **关联**: roadmap §5.2 [[opc-roadmap-expansion-design]] / [[opc-hr-w71]] 模板 / [[aiopc-llm-providers]] LLM
> **后续**: 实施计划由 writing-plans skill 生成

---

## 1. Goal

为 OPC 用户提供 **「商品 + 库存 + 采购 + 销售 + 退货」** 一站式进销存管理后端。**完整 v1** 范围包含:

1. 商品主档 + 动态 N 规格 SKU 自动生成
2. 多供应商采购单(批次号 + 生产日期 + 过期日期)
3. 销售单(FIFO 批次扣减,自动选最早生产批次)
4. 销退 + 采退双路退货单
5. 库存实时计算 + 库存预警(低于阈值触发通知)
6. 库存报表(实时查询 + 日报 + 月报)

**Acceptance Criteria (6 条)**

- AC-1：商品主档创建(动态规格) → 自动生成 N 个 SKU(笛卡尔积) → 采购入库 1 个 SKU → 库存增加 + 批次号记录 → 销售单出库自动 FIFO 选最早批次
- AC-2：销售单创建时校验库存,库存不足抛 `BizException("库存不足")`;销售单 COMPLETED 时实际扣减批次库存 + 写 inventory_log
- AC-3：销退单(SALES_RETURN)走「销售退货入库」路径,回滚批次库存 + 生成退货 inventory_log(SALES_RETURN_IN 类型)
- AC-4：采退单(SUPPLIER_RETURN)走「采购退货出库」路径,扣减批次库存 + 生成 inventory_log
- AC-5：库存预警 cron 每小时扫所有 SKU,`available_stock < threshold` 时通过 opc-notification Feign 发送预警通知
- AC-6：库存报表日报(每日 23:55 快照) + 月报(每月 1 号 00:05 聚合) + 实时库存查询 < 500ms(单 SKU)

## 2. 关键约束 / 范围

**In**(完整 v1):

- 9 张新表(supplier / product / product_sku / inventory_log / purchase / purchase_item / sale / sale_item / return_order)
- ~30 REST endpoints
- 1 个 Feign 调用(opc-notification,库存预警 + 单据状态通知)
- 1 个 LLM prompt(`erp-auto-category`,商品自动分类辅助)
- Vue 3 前端 8 页(商品/采购/销售/退货/库存预警/库存查询/日报/月报)
- 端口 **9311**(roadmap 保留)
- 批次 FIFO(按 `production_date ASC` 优先出库)
- 多供应商(单 SKU 多供应商,price 不同)
- 日报 + 月报(Quartz 定时聚合)

**Out**(不做):

- 与外部 WMS/ERP 对接(用 OPC 自家实现)
- 多币种(单 CNY)
- 多仓库(单 company 单仓库,v2 扩展)
- 财务凭证自动生成(由 opc-finance 自己抓数据)
- 销售单审批流(creator 直接 CONFIRMED)
- 批次预留/锁定(下单时直接扣减)
- 采购退货以外的供应商对账(由 opc-billing 接管)
- 移动端(RN 推迟到 v2)
- 多语言(只中文)

## 3. 数据模型（9 表）

```sql
-- 供应商主档
CREATE TABLE opc_erp_supplier (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  name            VARCHAR(128) NOT NULL,
  contact         VARCHAR(64),
  phone           VARCHAR(32),
  email           VARCHAR(128),
  address          VARCHAR(256),
  level           VARCHAR(16) NOT NULL DEFAULT 'NORMAL',  -- NORMAL/PREFERRED/BLOCKED
  status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE/INACTIVE
  created_by      BIGINT NOT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_name (company_id, name),
  UNIQUE KEY uk_company_supplier_name (company_id, name)
);

-- 商品主档(动态 N 规格)
CREATE TABLE opc_erp_product (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  sku_root        VARCHAR(64) NOT NULL,        -- SKU 前缀,如 "TSHIRT-001"
  name            VARCHAR(128) NOT NULL,
  category        VARCHAR(64),                 -- LLM 自动分类填充
  brand           VARCHAR(64),
  unit            VARCHAR(16) NOT NULL DEFAULT '件',
  description     TEXT,
  spec_attrs      JSON NOT NULL,               -- [{"name":"颜色","values":["黑","白"]},{"name":"尺码","values":["M","L"]}]
  status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_by      BIGINT NOT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_category (company_id, category),
  UNIQUE KEY uk_company_sku_root (company_id, sku_root)
);

-- SKU(规格组合 = spec_attrs 笛卡尔积)
CREATE TABLE opc_erp_product_sku (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  product_id      BIGINT NOT NULL,
  sku_code        VARCHAR(128) NOT NULL,       -- "TSHIRT-001-黑-M"
  spec_json       JSON NOT NULL,                -- {"颜色":"黑","尺码":"M"}
  price           DECIMAL(12,2) NOT NULL,
  cost            DECIMAL(12,2),
  stock           INT NOT NULL DEFAULT 0,       -- 当前可用库存(下单直接扣减,无 lock 字段)
  threshold       INT NOT NULL DEFAULT 10,       -- 预警阈值
  status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_product (company_id, product_id),
  UNIQUE KEY uk_company_sku_code (company_id, sku_code)
);

-- 库存变动流水
CREATE TABLE opc_erp_inventory_log (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  sku_id          BIGINT NOT NULL,
  batch_id        BIGINT,                      -- 批次 ID(可空,非批次场景)
  change          INT NOT NULL,                -- 正数入库,负数出库
  type            VARCHAR(16) NOT NULL,          -- PURCHASE_IN / SALE_OUT / SALES_RETURN_IN / SUPPLIER_RETURN_OUT / ADJUST
  ref_type        VARCHAR(16),                  -- PURCHASE / SALE / RETURN / ADJUST
  ref_id          BIGINT,                       -- 关联单据 ID
  remark          VARCHAR(256),
  created_by      BIGINT NOT NULL,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_company_sku_time (company_id, sku_id, create_time)
);

-- 批次表(采购入库时创建)
CREATE TABLE opc_erp_batch (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  sku_id          BIGINT NOT NULL,
  batch_no        VARCHAR(64) NOT NULL,         -- 批次号
  quantity        INT NOT NULL,                  -- 批次初始数量
  remaining       INT NOT NULL,                  -- 批次剩余(FIFO 出库时扣减)
  production_date DATE,                          -- 生产日期
  expiry_date     DATE,                          -- 过期日期(可空)
  supplier_id     BIGINT,                        -- 供应商
  purchase_id     BIGINT,                        -- 来源采购单
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_sku_batch (company_id, sku_id, batch_no),
  KEY idx_company_sku_remaining_fifo (company_id, sku_id, remaining, production_date)
);

-- 采购单主表
CREATE TABLE opc_erp_purchase (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  purchase_no     VARCHAR(32) NOT NULL,          -- 业务单号,如 "PO-20260912-0001"
  supplier_id     BIGINT NOT NULL,
  total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0,
  status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/CONFIRMED/COMPLETED/CANCELLED
  remark          VARCHAR(256),
  created_by      BIGINT NOT NULL,
  confirmed_by    BIGINT,                        -- 确认人(creator 直接确认 = 同人)
  confirmed_at    DATETIME,
  completed_at    DATETIME,                      -- 入库完成时间
  cancelled_at    DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_status_time (company_id, status, create_time),
  UNIQUE KEY uk_company_purchase_no (company_id, purchase_no)
);

-- 采购单明细
CREATE TABLE opc_erp_purchase_item (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  purchase_id     BIGINT NOT NULL,
  sku_id          BIGINT NOT NULL,
  quantity        INT NOT NULL,
  unit_price      DECIMAL(12,2) NOT NULL,
  subtotal        DECIMAL(12,2) NOT NULL,
  batch_no        VARCHAR(64),                   -- 批次号
  production_date DATE,
  expiry_date     DATE,
  KEY idx_company_purchase (company_id, purchase_id)
);

-- 销售单主表
CREATE TABLE opc_erp_sale (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  sale_no         VARCHAR(32) NOT NULL,           -- "SO-20260912-0001"
  customer_name   VARCHAR(128) NOT NULL,          -- 简化:不接 CRM,直接录入
  customer_phone  VARCHAR(32),
  total_amount    DECIMAL(12,2) NOT NULL DEFAULT 0,
  status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  remark          VARCHAR(256),
  created_by      BIGINT NOT NULL,
  confirmed_by    BIGINT,
  confirmed_at    DATETIME,
  completed_at    DATETIME,
  cancelled_at    DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_status_time (company_id, status, create_time),
  UNIQUE KEY uk_company_sale_no (company_id, sale_no)
);

-- 销售单明细
CREATE TABLE opc_erp_sale_item (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  sale_id         BIGINT NOT NULL,
  sku_id          BIGINT NOT NULL,
  quantity        INT NOT NULL,
  unit_price      DECIMAL(12,2) NOT NULL,
  subtotal        DECIMAL(12,2) NOT NULL,
  batch_id        BIGINT,                        -- FIFO 选中的批次
  KEY idx_company_sale (company_id, sale_id)
);

-- 退货单(销退 + 采退双路)
CREATE TABLE opc_erp_return (
  id              BIGINT PRIMARY KEY,
  company_id      BIGINT NOT NULL,
  return_no       VARCHAR(32) NOT NULL,           -- "RT-20260912-0001"
  return_type     VARCHAR(16) NOT NULL,           -- SALES_RETURN / SUPPLIER_RETURN
  ref_id          BIGINT NOT NULL,                -- 关联原单(sale_id 或 purchase_id)
  refund_amount   DECIMAL(12,2) NOT NULL DEFAULT 0,
  reason          VARCHAR(256),
  status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/CONFIRMED/COMPLETED/CANCELLED
  created_by      BIGINT NOT NULL,
  confirmed_by    BIGINT,
  confirmed_at    DATETIME,
  completed_at    DATETIME,
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_type_status (company_id, return_type, status),
  UNIQUE KEY uk_company_return_no (company_id, return_no)
);
```

**多租户**: 所有表 `company_id NOT NULL` + 复合索引;Service 层 `WHERE company_id = ?` 强约束。

## 4. REST API（~30 endpoints）

```
商品 + SKU (7)
POST   /opc/erp/product                     创建商品(spec_attrs 自动生成 SKU 笛卡尔积)
GET    /opc/erp/product/list                商品列表
GET    /opc/erp/product/{id}                商品详情(含 SKU 列表)
PUT    /opc/erp/product/{id}                更新商品
DELETE /opc/erp/product/{id}                删除商品(无库存时可删)
POST   /opc/erp/product/auto-category       LLM 自动分类(输入 name + description,输出 category)
GET    /opc/erp/product-sku/list            SKU 列表(支持 product_id / sku_code 过滤)

采购 (6)
POST   /opc/erp/purchase                    创建采购单(DRAFT)
POST   /opc/erp/purchase/{id}/confirm       确认(写入批次 + 入库)
POST   /opc/erp/purchase/{id}/cancel        取消(仅 DRAFT)
GET    /opc/erp/purchase/list               采购单列表
GET    /opc/erp/purchase/{id}               采购单详情(含 items)
GET    /opc/erp/purchase/no/{purchaseNo}    按单号查询

销售 (6)
POST   /opc/erp/sale                        创建销售单(DRAFT,校验库存)
POST   /opc/erp/sale/{id}/confirm           确认(FIFO 选批次 + 实际扣减)
POST   /opc/erp/sale/{id}/cancel            取消(仅 DRAFT)
GET    /opc/erp/sale/list                   销售单列表
GET    /opc/erp/sale/{id}                   销售单详情(含 items + 选中批次)
GET    /opc/erp/sale/no/{saleNo}            按单号查询

退货 (5)
POST   /opc/erp/return                      创建退货单(SALES_RETURN / SUPPLIER_RETURN)
POST   /opc/erp/return/{id}/confirm         确认(回滚/扣减批次库存)
POST   /opc/erp/return/{id}/cancel          取消
GET    /opc/erp/return/list                 退货单列表
GET    /opc/erp/return/{id}                 详情

供应商 (4)
POST   /opc/erp/supplier                    创建供应商
GET    /opc/erp/supplier/list               列表
PUT    /opc/erp/supplier/{id}               更新
DELETE /opc/erp/supplier/{id}               删除(无关联单据)

库存查询 + 报表 (4)
GET    /opc/erp/inventory/sku/{skuId}       单 SKU 实时库存
GET    /opc/erp/inventory/low-stock         低库存预警列表(available < threshold)
GET    /opc/erp/report/daily?date=YYYY-MM-DD 库存日报
GET    /opc/erp/report/monthly?year=YYYY&month=MM 库存月报
```

## 5. 状态机

```
采购单:  DRAFT → CONFIRMED → COMPLETED
                  ↓
              CANCELLED (仅 DRAFT 可取消)

销售单:  DRAFT → CONFIRMED → COMPLETED
                  ↓
              CANCELLED (仅 DRAFT 可取消)

退货单:  DRAFT → CONFIRMED → COMPLETED
                  ↓
              CANCELLED
```

**转换规则**:
- `CONFIRM` 时写入 inventory_log + (采购时)创建 batch 记录 / (销售时)FIFO 扣减 batch.remaining + (退货时)按 type 反向操作
- `CONFIRM` 是终态 → 不可再 cancel
- DRAFT 状态可编辑 / 取消

## 6. FIFO 批次扣减算法

销售单 `CONFIRM` 时,系统按 SKU 查询所有 `batch.remaining > 0` 的批次,按 `production_date ASC` 排序,优先扣减最早批次:

```
按需量 quantity 累加扣减:
  for batch in batches_sorted_by_production_date_asc:
    take = min(batch.remaining, quantity)
    batch.remaining -= take
    quantity -= take
    记录 sale_item.batch_id += take
  if quantity > 0:
    throw BizException("库存不足,需 X 单位")
```

**特殊场景**: 无批次记录(批次表为空)的库存,直接扣减 `product_sku.stock`(兼容非批次商品)。

## 7. Feign 依赖（1 个）

| 被调服务 | 用途 | Header |
|---|---|---|
| opc-notification (9310) | 库存预警通知 + 单据状态通知 | `@InnerAuth` + `from-source: INNER` |

**FallbackFactory**: 通知失败不影响业务主流程,降级仅写日志。

## 8. LLM 集成（1 个 prompt）

| Prompt 名 | 输入 | 输出 | 模型 |
|---|---|---|---|
| `erp_auto_category_v1.0` | product_name + description | category(标准分类,如"服装/鞋帽/上衣") | DeepSeek |

**触发场景**: `POST /opc/erp/product` 时,如果前端没传 category,自动调 LLM 补全。失败则 category=null(允许)。

**PromptGuard**: 所有 ERP LLM 调用前置 `PromptGuard.validate(input)`(沿用)。

## 9. Quartz 定时任务（2 个）

| Job 名 | Cron | 用途 |
|---|---|---|
| `OpcErpDailySnapshotJob` | `0 55 23 * * ?`(每日 23:55) | 生成当日库存日报快照(每 SKU 进/出/库存结余) |
| `OpcErpLowStockAlertJob` | `0 0 * * * ?`(每小时) | 扫描所有 SKU,`stock < threshold` 时通过 Feign 发送预警通知(去重:同 SKU 24h 内只发一次) |

**注**: Quartz job 复用 opc-job 模块(沿用 W3 的 WorkflowCronJob 模式)。

## 10. 前端（8 页）

| 路由 | 文件 | 说明 |
|---|---|---|
| `/opc/erp/product` | `views/opc/erp/product/index.vue` | 商品列表 + 「AI 自动分类」按钮 + 规格编辑 |
| `/opc/erp/product/:id` | `views/opc/erp/product/detail.vue` | 商品详情 + SKU 列表 |
| `/opc/erp/purchase` | `views/opc/erp/purchase/index.vue` | 采购单列表 + 状态过滤 |
| `/opc/erp/purchase/new` | `views/opc/erp/purchase/new.vue` | 创建采购单(选供应商 + 选 SKU + 批次) |
| `/opc/erp/sale` | `views/opc/erp/sale/index.vue` | 销售单列表 |
| `/opc/erp/sale/new` | `views/opc/erp/sale/new.vue` | 创建销售单(实时库存校验) |
| `/opc/erp/return` | `views/opc/erp/return/index.vue` | 退货单列表 + 销退/采退切换 |
| `/opc/erp/inventory` | `views/opc/erp/inventory/index.vue` | 库存查询 + 低库存预警 + 日报 / 月报入口 |

**响应式**: 复用 [[aiopc-deployment-persistence]] 的 ResponsiveTable + responsive.scss。

## 11. 测试策略

- **单测(≥80)**:`OpcErpProductServiceImplTest`(15)+ `OpcErpPurchaseServiceImplTest`(12)+ `OpcErpSaleServiceImplTest`(15)+ `OpcErpReturnServiceImplTest`(10)+ `OpcErpInventoryServiceImplTest`(10)+ `OpcErpReportServiceImplTest`(10)+ `OpcErpSupplierServiceImplTest`(8)
- **MockedStatic**:`SecurityUtils.getUserId()` + `OpcErpNotificationGateway`(参考 opc-crm W50)
- **FIFO 单测**:验证同 SKU 多批次扣减顺序(3 批次,quantity 跨批次时正确)
- **E2E(≥10)**:`tmp_e2e/e2e_erp.py` 全链路:商品创建 → 采购入库 → 销售出库(FIFO)→ 销退 → 库存预警 → 日报查询
- **库存并发**:乐观锁(`@Version`)防止超卖(实际扣减时 `WHERE stock >= quantity`)
- **LLM 评测**:EvalRunnerTest 10 case 商品分类
- **健康检查**:health-check.sh 加 6 个端点

## 12. 部署

- **端口**:9311
- **Docker**:thin jar 模板(参考 opc-hr Task 11)
- **Nacos**:`opc-erp-dev.yml` + `opc-erp-prod.yml` + `share-application-erp.yml`
- **DB 迁移**:`V20260912__opc_erp_schema.sql` + 5 条 supplier seed
- **Helm**:加 `erp` service 到 `deploy/helm/opc/values.yaml` 的 services 列表
- **Gateway**:`/opc/erp/**` → `http://aiopc-erp:9311`(无 StripPrefix)
- **白名单**:`/opc/erp/product-sku/list` GET 公开(对外展示商品 SKU)

## 13. 验收清单

- [ ] 9 表 + seed 数据
- [ ] 30 endpoints + 单测 ≥80 + E2E ≥10
- [ ] 1 Feign(含 Fallback)
- [ ] 1 prompt + EvalRunnerTest
- [ ] 2 Quartz 定时任务(日报 + 预警)
- [ ] FIFO 算法单测覆盖
- [ ] 乐观锁防超卖
- [ ] 8 Vue 前端页(响应式)
- [ ] Dockerfile thin jar + Nacos 2 yml + 1 SQL
- [ ] Helm chart 更新
- [ ] Gateway 路由 + 白名单
- [ ] health-check.sh 端点 + 全栈 44→50 PASS
- [ ] VERIFICATION 报告:`docs/verification/week-XX/OPC-WXX-VERIFICATION-opc-erp.md`

## 14. 风险

- R-1：FIFO 跨批次并发扣减需事务 + 行锁(`SELECT ... FOR UPDATE`)
- R-2：库存预警 24h 去重需内存缓存(Caffeine)避免重复通知
- R-3：日报/月报大量 SKU 时聚合慢 → 走 `INSERT INTO daily_snapshot SELECT ...` 单 SQL
- R-4：退货单与原单状态联动(SALE_COMPLETED → RETURN_COMPLETED)需事务一致性
- R-5：采购单 supplier_id 可空(临时供应商)→ 采购表允许 nullable,业务层校验

## 15. 不在 v1

- 多仓库 / 多币种 / 多语言
- 财务凭证自动生成(由 opc-finance 接管)
- 销售审批流
- 批次预留 / 锁定(下单直接扣减)
- 采购对账(由 opc-billing)
- 移动端 RN
- 复杂报表(PivotTable / 多维度分析)