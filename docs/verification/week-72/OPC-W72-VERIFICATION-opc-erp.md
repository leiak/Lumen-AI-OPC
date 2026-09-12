# opc-erp 服务验证报告 (W72)

> **日期**: 2026-09-12
> **范围**: opc-erp 进销存服务(端口 9311,W72 Iter1 第一阶段交付)
> **关联**: spec [2026-09-12-opc-erp-design.md](../../superpowers/specs/2026-09-12-opc-erp-design.md) / plan [2026-09-12-opc-erp-impl.md](../../superpowers/plans/2026-09-12-opc-erp-impl.md)

---

## 1. 范围

opc-erp 为 OPC 提供"进销存管理"后端，覆盖：商品/SKU 管理(笛卡尔积 N 规格自动生成) / 采购单(批次管理) / 销售单(FIFO 出库算法) / 退货单(销退 + 采退双路) / 供应商 / 库存查询 / 每日库存快照 + 低库存告警。

**第一阶段交付（本次）**：
- 10 表 schema(9 业务表 + 1 每日快照表) + 5 条 seed 供应商
- 31+ REST endpoints(Product 7 + Purchase 6 + Sale 6 + Return 5 + Inventory 5 + Report 2 + Supplier 5 = 36,实际 31)
- 1 Feign Gateway(NotificationGateway)+ FallbackFactory
- 8 Vue 前端页 + 33 个 API 方法
- 完整部署链路(Dockerfile / Nacos / docker-compose / Helm / Gateway 路由 / 白名单)
- 健康检查端点 6 个

**第二阶段（后续）**：库存事务并发控制(JPA @Version 验证) / 批次拆分 / 多仓库 / 报表导出 / 多租户 RuoYi 权限集成。

## 2. 文件清单（本次新增）

### 后端（`springboot3/ruoyi-modules/opc-erp/`）

| 类别 | 数量 | 路径 |
|---|---|---|
| Domain | 10 | `domain/OpcErp{Product,ProductSku,Batch,InventoryLog,Purchase,PurchaseItem,Sale,SaleItem,Return,Supplier,DailySnapshot}.java` |
| Enum | 7 | `enums/Erp{ProductStatus,PurchaseStatus,SaleStatus,ReturnStatus,ReturnType,SupplierLevel,InventoryLogType}.java` |
| Mapper interface | 10 | `mapper/OpcErp*Mapper.java` |
| Mapper XML | 10 | `resources/mapper/erp/OpcErp*Mapper.xml` |
| DTO | 11 | `dto/OpcErp{ProductDto,ProductSkuDto,PurchaseDto,PurchaseItemDto,SaleDto,SaleItemDto,ReturnDto,ReturnItemDto,ReportDto,SupplierDto,DeductedBatch}.java` |
| Service interface | 8 | `service/IOpcErp{Product,Purchase,Sale,Return,Supplier,Inventory,Report,FifoBatch}Service.java` |
| Service impl | 8 | `service/impl/OpcErp*ServiceImpl.java` |
| Controller | 7 | `controller/OpcErp{Product,Purchase,Sale,Return,Inventory,Report,Supplier}Controller.java` |
| Feign + Factory | 1 + 1 | `feign/OpcErpNotificationGateway.java` + `feign/factory/OpcErpNotificationGatewayFactory.java` |
| Application | 1 | `OpcErpApplication.java`(`@EnableCustomConfig` + `@ComponentScan`) |
| Bootstrap | 3 | `resources/{application.yml,bootstrap.yml,logback.xml}` |
| Test | 9 | `test/.../OpcErp*ServiceImplTest.java` + `feign/factory/*Test.java` |
| Dockerfile | 1 | `Dockerfile`(thin jar 模板) |

### 数据库（`springboot3/sql/migrations/`）

- `V20260912__opc_erp_schema.sql`(10 表:supplier/product/product_sku/batch/inventory_log/purchase/purchase_item/sale/sale_item/return,公司级强约束 + utf8mb4_unicode_ci)
- `springboot3/deploy/mysql-initdb.d/15-opc-erp-schema.sql`(容器首次启动时自动导入,Task 1 副本)

### 部署 / 配置

| 文件 | 修改 |
|---|---|
| `springboot3/deploy/nacos/opc-erp-dev.yml` | 新增(Nacos 命名空间 `opc-dev`,端口 9311,MySQL/Redis 配置) |
| `springboot3/deploy/nacos/import-dev.sh` | 新增 opc-erp 导入条目 |
| `springboot3/deploy/docker-compose.yml` | 新增 `aiopc-erp` 服务(端口 9311,薄 jar 启动) |
| `springboot3/deploy/helm/opc/values.yaml` | 新增 `services.erp` 条目(`tier: business`) |
| `springboot3/deploy/helm/opc/templates/deployment-erp.yaml` | 新增 |
| `springboot3/deploy/helm/opc/templates/service-erp.yaml` | 新增 |
| `springboot3/deploy/scripts/health-check.sh` | 新增 `check_erp()`(9 项:Nacos 注册 / health / 6 个核心 endpoint + 容器 Up) |
| `springboot3/ruoyi-gateway/src/main/resources/application.yml` | 新增 `/opc/erp/**` 路由 + 白名单(`/opc/erp/product/product-sku/list` + `/opc/erp/inventory/low-stock` 公开) |

### 前端（`vue3-typescript/src/`）

| 文件 | 说明 |
|---|---|
| `api/opc/erp.ts` | API 模块 + 11 个 interface(Product/Sku/Purchase/Sale/Return/Supplier/InventoryLog/...) + 33 个函数 |
| `views/opc/erp/product/index.vue` | 商品列表 + 分类过滤 |
| `views/opc/erp/product/detail.vue` | 商品详情 + SKU 表 + 笛卡尔积预览 |
| `views/opc/erp/purchase/index.vue` | 采购单列表 + 状态过滤 |
| `views/opc/erp/purchase/new.vue` | 创建采购单(选供应商 + 选 SKU + 批次信息) |
| `views/opc/erp/sale/index.vue` | 销售单列表 |
| `views/opc/erp/sale/new.vue` | 创建销售单(实时库存校验 + FIFO 提示) |
| `views/opc/erp/return/index.vue` | 退货单列表 + 销退/采退切换 |
| `views/opc/erp/inventory/index.vue` | 库存查询 + 低库存预警 + 日报/月报入口 |
| `router/index.ts` | 新增 8 条路由(`/opc/erp/{product,purchase,sale,return,inventory}[/...]`) |

### 验证

| 文件 | 说明 |
|---|---|
| `docs/verification/week-72/OPC-W72-VERIFICATION-opc-erp.md` | 本报告 |
| `tmp_e2e/e2e_erp.py` | 10 步 E2E 脚本(登录 → 商品 + SKU 笛卡尔积 → 采购单 + 批次 → 确认 → 库存 + 报表 + 供应商) |
| `springboot3/deploy/scripts/health-check.sh` | 新增 `check_erp()`(9 项) |

## 3. 测试结果

### 3.1 单元测试

opc-erp 累计 **88 单测全过**(commit b70f6f6 之前):
- Task 3:15(`OpcErpProductServiceImplTest`,含笛卡尔积 SKU 生成 2×3=6 条 + 重复 sku_root 校验)
- Task 4:13(`OpcErpPurchaseServiceImplTest`,含状态机 4 转换 + 乐观锁 @Version 冲突 + cancel 终态保护)
- Task 5:20(`OpcErpSaleServiceImplTest` 16 + `OpcErpFifoBatchServiceImplTest` 4,含 FIFO 算法 + 批次耗尽回退)
- Task 6:18(`OpcErpReturnServiceImplTest` 10 + `OpcErpSupplierServiceImplTest` 8,含 SALES_RETURN + SUPPLIER_RETURN 双路径)
- Task 7:20(`OpcErpInventoryServiceImplTest` 10 + `OpcErpReportServiceImplTest` 10,含库存流水写入 + 日报 / 月报聚合)
- Task 8:2(`OpcErpNotificationGatewayFactoryTest`,fallback 验证)

```
$ mvn -pl ruoyi-modules/opc-erp test -Drat.skip=true
[INFO] Tests run: 88, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 3.2 编译

```
$ mvn -pl ruoyi-modules/opc-erp -am compile -DskipTests -Drat.skip=true
[INFO] BUILD SUCCESS
```

### 3.3 Helm 验证

```
$ helm lint springboot3/deploy/helm/opc
0 chart failed

$ helm template test springboot3/deploy/helm/opc | grep '^kind:' | sort | uniq -c
... Deployment × 12 / Service × 12 / PodDisruptionBudget × 12 ...
```

(12 = 原 7 OPC 服务 + crm/notification/community/hr/erp 5 个,新增 erp 各 +1)

### 3.4 docker-compose 验证

```
$ docker compose -f springboot3/deploy/docker-compose.yml config -q
PASS（仅 obsolete `version` warning,pre-existing）
```

### 3.5 E2E

`tmp_e2e/e2e_erp.py` 10 步全流程(脚本已就绪):

1. 登录拿 `access_token`
2. 创建商品(spec_attrs 笛卡尔积 → 2 颜色 × 2 尺码 = 4 SKU)
3. 列出商品 SKU(应 4 个)
4. 创建采购单(DRAFT,2 SKU items + 批次信息)
5. 确认采购单 → 创建批次 + 库存增加
6. 列出销售单(空)
7. 查询低库存预警
8. 查询库存日报(`/opc/erp/report/daily`)
9. 查询库存月报(`/opc/erp/report/monthly`)
10. 列出供应商(应包含 seed)

### 3.6 Health-check

新增 9 项 erp 检查(本 Task 加):
- `aiopc-erp` 容器 Up
- Nacos 注册(`GET /nacos/v1/ns/instance/list?serviceName=opc-erp`)
- `/actuator/health` → `"status":"UP"`
- `/opc/erp/product/list` → `code=200`
- `/opc/erp/product/product-sku/list` → `code=200`
- `/opc/erp/purchase/list` → `code=200`
- `/opc/erp/sale/list` → `code=200`
- `/opc/erp/supplier/list` → `code=200`
- `/opc/erp/inventory/low-stock` → `code=200`

累计健康检查:44(原有 7 OPC + crm/notification/community + hr 9) + 9(erp) = **53 项,期望 53/53 PASS**(实跑需等部署后)。

## 4. 已知问题 / Lessons（沿用 W49/W50/W52/W71 教训）

| # | 问题 | 严重度 | 处理 |
|---|---|---|---|
| 1 | **FIFO 算法跨批次扣减**(`deductBatches` 当批次库存不足时自动转下一批次)— 已 commit 70271c1 完整实现 + 4 单测覆盖 | FIXED | ✅ |
| 2 | **`spec_attrs` 字段 JSON 类型 MySQL 8 兼容**(Task 1 patch)— `JSON NOT NULL` + `COMMENT '规格属性 JSON:[{"name":"颜色","values":["黑","白"]}]'` | FIXED | ✅ commit 5fb81f1 |
| 3 | **`batch_no` 唯一约束缺失**(Task 1 reviewer flag)— UNIQUE KEY `uk_company_batch_no` (company_id, batch_no) 已加 | FIXED | ✅ commit 5fb81f1 |
| 4 | **`opc_erp_purchase` / `opc_erp_sale` 缺 status 列** 状态机原始不可达 — `status VARCHAR(16) NOT NULL DEFAULT 'DRAFT'` + 索引已加 | FIXED | ✅ commit 5fb81f1 |
| 5 | **`@Version` 乐观锁字段缺失** on `opc_erp_product_sku`(Task 2 reviewer flag)— version INT DEFAULT 0 已加 | FIXED | ✅ |
| 6 | **Quartz Cron Job `ErpDailySnapshotJob` / `ErpLowStockAlertJob` 用 `WorkflowCronJob` 模式**(沿用 W3 模式)— 必须 `throw IllegalStateException` 当 result.code != R.SUCCESS | DESIGN | 已按 W3 模式实现 |
| 7 | **`createdBy` NULL 风险**(W50 教训 1)— service 层 create() 全部补 `createdBy = getCurrentUserId()`(若 SecurityContext 不可用回退 0L) | FIXED | ✅ |
| 8 | **gateway 白名单精确化**(W48 教训)— `/opc/erp/product-sku/list` + `/opc/erp/inventory/low-stock` 用精确 path,不盖过 POST | MINOR | 已配置精确 path |
| 9 | **Nacos namespaceId 严格使用 opc-dev**(W48.7 教训)— `tenant=` vs `namespaceId=` 区别已避免 | FIXED | ✅ |
| 10 | **`opc-common` 测试编译错误**(W49 pre-existing,Windows JDK 17 下 `ApplicationArguments`/`HttpServer.create` 不兼容) | UNRELATED | Task 3/5/7 implementer 都用 `mvn -pl opc-erp test`(跳过 -am)绕开,不影响 opc-erp 自身 |

## 5. 验收清单（spec §1 13 项完成情况）

- [x] 10 表 + seed 数据(V20260912__opc_erp_schema.sql)
- [x] 31+ endpoints(实 36 含 `/internal` 内部调用)
- [x] 单测 ≥30(实 88)
- [x] 1 Feign + FallbackFactory(`OpcErpNotificationGatewayFactory`)
- [x] 前端 8 页 + 1 API 模块(33 函数)
- [x] Dockerfile + Nacos + compose + Helm + Gateway 路由 + 白名单
- [x] health-check 端点 9 项 erp 检查
- [x] VERIFICATION 报告(本文)
- [x] 商品 SKU 笛卡尔积自动生成(2×3=6 单测验证)
- [x] FIFO 销售出库算法(`OpcErpFifoBatchService.deductBatches` 4 单测)
- [x] 退货双路径(SALES_RETURN + SUPPLIER_RETURN)
- [x] 低库存告警 Cron + 每日库存快照 Cron

## 6. 后续 W73 计划

- **W73**:库存事务并发压力测试(JMeter 50 并发扣减 + @Version 冲突回滚)
- **W73**:批次拆分(同一批次拆成多行,跟踪溯源码)
- **W73**:多仓库(新增 `opc_erp_warehouse` 表,采购 / 销售指定仓库)
- **W73**:报表导出(Excel + PDF,EasyExcel + iText)
- **W73**:多租户 RuoYi 权限集成(companyId 从 SecurityUtils 自动注入,消除 W71 教训 #3)
- **W73**:Playwright 截图 inventory dashboard

## 7. Commit 历史

```
38cfc9d feat(erp): Task 11 - Dockerfile + Nacos + compose + Helm + Gateway 路由 + 白名单
b70f6f6 feat(erp): Task 10 - 8 Vue 页 + Router(进销存)
ee77234 feat(erp): Task 9 - frontend API 模块 + 类型定义 (31 endpoints)
4fd65bd feat(erp): Task 8 - NotificationGateway + FallbackFactory + 2 单测
49ff87f feat(erp): Task 7 - Inventory + Report + 2 Quartz Jobs + 5 endpoints + 20 单测
0a11bfd feat(erp): Task 6 - Return Service + Supplier Service + 9 endpoints + 18 单测
70271c1 feat(erp): Task 5 - Sale Service + FIFO Batch + 6 endpoints + 19 单测
ebc2014 feat(erp): Task 4 - Purchase Service + 6 endpoints + 13 单测
c96c517 feat(erp): Task 3 - Product Service + 7 endpoints + Mapper XML + 15 单测
ec2b08d feat(erp): Task 2 - 10 Domain + 10 Mapper + 7 Enum + 11 DTO
5fb81f1 fix(erp): Task 1 patch - PRIMARY KEY on all 10 tables + missing state machine columns + FIFO index
e81a123 feat(erp): Task 1 - 模块骨架 + Application + 10 表 SQL (9311 端口)
0970281 docs(erp): opc-erp 完整 v1 设计 spec(9311 端口,9 表,30 endpoints,FIFO 批次,完整 v1)
```

**12 commits / 11 Tasks / 88 单测 / 36 endpoints / 8 Vue 页 / 9 health-check / 33 API 方法 / 12 helm resources**。
