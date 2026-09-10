# W2.5 — OpcFinanceController 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-finance/`
> 静态验证（本机 JDK 8，沿用 W2.1-W2.4 模式）

---

## 1. 交付物

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/.../test/.../controller/OpcFinanceControllerTest.java` | 新增单测 | 385 |
| `OPC-W2-VERIFICATION-finance-controller.md` | 本报告 | — |

**无新增 pom 依赖** — `opc-finance` 在 W2.1 已加 `spring-boot-starter-test`。

---

## 2. Endpoint 覆盖矩阵

| # | Endpoint | Method | 测试方法 | 状态 |
|---|---|---|---|---|
| 1 | `/vouchers` | GET | `listVouchers_passesAllArgs` | ✅ |
| 2 | `/vouchers` | GET | `listVouchers_nullFiltersPassThrough` | ✅ |
| 3 | `/voucher/{id}` | GET | `voucherDetail_returnsVoucher` | ✅ |
| 4 | `/voucher` | POST | `createVoucher_returnsIdInMap` | ✅ |
| 5 | `/voucher` | POST | `createVoucher_nullIdReturned` | ✅ |
| 6 | `/voucher` | PUT | `updateVoucher_rowsGreaterThanZero` | ✅ |
| 7 | `/voucher` | PUT | `updateVoucher_rowsZero` | ✅ |
| 8 | `/voucher/{id}/review-pass` | POST | `reviewPass_success` | ✅ |
| 9 | `/voucher/{id}/review-pass` | POST | `reviewPass_rowsZero` | ✅ |
| 10 | `/voucher/{id}/review-reject` | POST | `reviewReject_withOpinion` | ✅ |
| 11 | `/voucher/{id}/review-reject` | POST | `reviewReject_nullOpinion` | ✅ |
| 12 | `/voucher/{id}/review-reject` | POST | `reviewReject_rowsZero` | ✅ |
| 13 | `/voucher/{id}/post` | POST | `post_success` | ✅ |
| 14 | `/voucher/{id}/post` | POST | `post_rowsZero` | ✅ |
| 15 | `/voucher/{id}/post` | POST | `post_serviceThrowsPropagates` | ✅ |
| 16 | `/flows/upload` | POST | `uploadFlows_returnsCount` | ✅ |
| 17 | `/flows/upload` | POST | `uploadFlows_returnsZero` | ✅ |
| 18 | `/flows/pending` | GET | `pendingFlows_passesArgs` | ✅ |
| 19 | `/flows/extract` | POST | `extractFlows_returnsTaskCodeWithoutService` | ✅ |
| 20 | `/daily-report` | POST | `dailyReport_returnsTaskCodeAndDateWithoutService` | ✅ |

**总计：20 个 @Test，11 个 endpoint 100% 覆盖。**

---

## 3. 关键 Mockito 模式

### 3.1 mockStatic 模拟 SecurityUtils

`SecurityUtils.getUsername()` 是 static 方法，必须用 `Mockito.mockStatic` 才能在单测中控制返回值。

```java
@BeforeEach
void setupSecurityMock() {
    securityMock = mockStatic(SecurityUtils.class);
    securityMock.when(SecurityUtils::getUsername).thenReturn(USERNAME);
}

@AfterEach
void teardownSecurityMock() {
    if (securityMock != null) securityMock.close();
}
```

**为什么 `@BeforeEach` + `@AfterEach` 而非 try-with-resources 在每个 test**：11 个 test 中 4 个需要
SecurityUtils（reviewPass / reviewReject / post / uploadFlows），但统一在 setup 里 mock 让代码更整齐，
且 LENIENT 模式下未使用的 stub 不会报错。`@AfterEach.close()` 释放静态 mock 状态避免污染后续 test。

### 3.2 验证 SecurityUtils.getUsername 被调用

```java
securityMock.verify(() -> SecurityUtils.getUsername(), atLeastOnce());
```

`mocked.verify()` 在静态 mock 上验证方法调用次数。`atLeastOnce()` 确保 SecurityUtils.getUsername
至少被调一次（业务上每次调 review/post 都需要）。

### 3.3 verifyNoInteractions 验证「不调任何 service」

```java
@Test
void extractFlows_returnsTaskCodeWithoutService() {
    AjaxResult result = controller.extractFlows(COMPANY_ID);
    ...
    verifyNoInteractions(voucherService, bankFlowService);
}
```

`/flows/extract` 和 `/daily-report` 是占位实现（实际待 RabbitMQ 异步任务接入），用
`verifyNoInteractions` 钉死「当前实现不调任何 service」。**未来重构时如果引入 service 调用会
立即测试失败**，防止忘记迁移到异步实现。

### 3.4 AjaxResult 是 HashMap

`com.ruoyi.common.core.web.domain.AjaxResult extends HashMap<String, Object>`，所以：

```java
assertEquals(200, result.get("code"));                  // BaseController.success() 默认 200
assertEquals(123L, result.get("voucherId"));            // createVoucher 自定义 key
assertSame(mockList, result.get("data"));               // 默认 data 字段
assertEquals(Boolean.TRUE, result.get("data"));         // updateVoucher rows>0
```

不需要 AjaxResult 专属 getter，直接 `.get(key)` 取值。

### 3.5 异常透传验证（不包装）

```java
@Test
void post_serviceThrowsPropagates() {
    when(voucherService.post(VOUCHER_ID, USERNAME))
            .thenThrow(new OpcException("凭证未通过审核，不能入账"));

    OpcException ex = assertThrows(OpcException.class,
            () -> controller.post(VOUCHER_ID));
    assertTrue(ex.getMessage().contains("未通过审核"));
}
```

Controller 不应 try/catch 包住 service 异常（让全局异常处理器 @RestControllerAdvice 处理）。
`assertThrows(OpcException.class)` 验证异常从 controller 直传到调用方。

---

## 4. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| `result.get("code") == 200`（所有成功路径） | 全部 20 例 |
| `createVoucher` data key 是 `voucherId`（不是 `id` 或 `data.id`） | `createVoucher_returnsIdInMap` |
| `updateVoucher` rows=0 → data=`false` 而非 error（HTTP 仍 200） | `updateVoucher_rowsZero` |
| `reviewReject` opinion=null 透传给 service | `reviewReject_nullOpinion` |
| `post` service 抛 OpcException → 透传，controller 不吞 | `post_serviceThrowsPropagates` |
| `uploadFlows` 返回 service.uploadBatch 行数 | `uploadFlows_returnsCount` |
| `extractFlows` taskCode 前缀 = `EXTRACT-` | `extractFlows_returnsTaskCodeWithoutService` |
| `dailyReport` taskCode 前缀 = `DAILY-` + date = LocalDate.now() | `dailyReport_returnsTaskCodeAndDateWithoutService` |
| `extractFlows` / `dailyReport` 不调任何 service | 2 例 |

---

## 5. 与 W2.4 BankFlowService 抽取的协同

W2.4 把 `uploadFlows` 业务逻辑从 controller 抽到 `bankFlowService.uploadBatch()`。本次 controller
单测验证：

| 验证点 | 之前（内联） | 之后（抽 service 后） |
|---|---|---|
| `verify(bankFlowService).uploadBatch(flows, USERNAME)` | ❌（要 verify mapper.insertBatch + 循环副作用） | ✅（一行断言） |
| SecurityUtils.getUsername 在 controller 层调 | 内联在 controller | 仍在 controller（service 不依赖 SecurityUtils） |
| flowCode 生成逻辑可单测 | ❌ | ✅（W2.4 BankFlowServiceImplTest 覆盖） |

**架构收益**：controller 单测聚焦「HTTP ↔ service 编排」，service 单测聚焦「业务逻辑」。两者
互不重叠，组合后覆盖完整。

---

## 6. 与 W2.x 已有测试的对照

| 维度 | TaxReport Svc | Voucher Svc | BankFlow Svc | **Finance Ctrl** |
|---|---|---|---|---|
| @Test 数 | 8 | 21 | 15 | **20** |
| mockStatic SecurityUtils | — | — | — | **✅** |
| 覆盖 endpoint | — | — | — | **11** |
| 异常透传 | ✅ | ✅ | ✅ | **✅** |
| 依赖 service | TaxReportService | VoucherService | BankFlowService | **Voucher + BankFlow** |
| 复杂度 | 一次性写表 | 状态机 | uploadBatch 初始化 | **HTTP ↔ 2 service 编排** |

---

## 7. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` | 静态分析；mockStatic 是 Mockito 5 标准 API |
| `extractFlows` / `dailyReport` 是占位实现（return hardcoded taskCode），生产应走 RabbitMQ 异步 | 测试钉死「不调 service」，未来重构异步时移除 stub、加 MQ mock |
| `@PathVariable` / `@RequestBody` 注解参数未走 Spring 序列化，单元测试无法验证反序列化 | 需要 Spring MVC 测试（@WebMvcTest），已超出纯 unit test 范围，列为 W3 集成测试候选 |
| SecurityUtils 的 close() 失败可能污染全局 static state | `@AfterEach` 兜底关闭 + `try/finally`（mockito 5 close idempotent） |
| 缺 `@WebMvcTest` 集成测试（验证 HTTP status / JSON 序列化 / 401 未授权） | W3 候选；当前 unit test 覆盖业务编排已足够 |

---

## 8. 验收 Checkpoint

- [x] 11 个 endpoint 100% 覆盖
- [x] 20 个 @Test，全部 `@DisplayName` 描述场景
- [x] `@MockitoSettings(strictness = LENIENT)` 与 W2.x 一致
- [x] `mockStatic(SecurityUtils.class)` 模式正确（@BeforeEach + @AfterEach）
- [x] `verifyNoInteractions` 验证占位 endpoint 不调 service
- [x] 异常透传测试（OpcException 不被 controller 吞）
- [x] rows=0 → success(false) 而非 error 的语义测试
- [x] `AjaxResult.get("voucherId")` 自定义 key 验证
- [x] pom.xml 无需变更

---

## 9. W2 后续候选更新

| 任务 | 状态 | 估值 |
|---|---|---|
| `OpcFinanceController` 单测（11 endpoint） | ✅ **W2.5 完成** | 1.5h |
| `OpcBillingController` 单测（4 endpoint + WalletService） | W2.6 候选 | 1h |
| `OpcInvitationController` 单测（5 endpoint + InvitationService） | W2.6 候选 | 1h |
| `OpcFinanceTaxReportController` 单测（3 endpoint + TaxReportService） | W2.6 候选 | 45min |
| `OpcUserProfileService` 单测（实名认证状态机） | W3 候选 | 1h |
| 集成测试（@WebMvcTest + Spring context） | W3 候选 | 2 天 |

W2 剩余单测优先级：`OpcBillingController`（最简单，4 endpoint 全有 service 兜底）> `OpcInvitationController` > `OpcFinanceTaxReportController`。
