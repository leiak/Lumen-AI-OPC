# W2.6 — OpcBillingController 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-billing/`
> 静态验证（本机 JDK 8，沿用 W2.1-W2.5 模式）

---

## 1. 交付物

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/.../test/.../controller/OpcBillingControllerTest.java` | 新增单测 | 322 |
| `OPC-W2-VERIFICATION-billing-controller.md` | 本报告 | — |

**无新增 pom 依赖** — `opc-billing` 在 W2.1 (commit 69d880b) 已加 `spring-boot-starter-test`。

---

## 2. Endpoint 覆盖矩阵

| # | Endpoint | Method | 测试方法 | 状态 |
|---|---|---|---|---|
| 1 | `/wallet` | GET | `wallet_returnsFromService` | ✅ |
| 2 | `/wallet` | GET | `wallet_nullWallet` | ✅ |
| 3 | `/wallet/recharge` | POST | `recharge_setsAllFieldsOnOrder` | ✅ |
| 4 | `/wallet/recharge` | POST | `recharge_payMethodNull_defaultsToAlipay` | ✅ |
| 5 | `/wallet/recharge` | POST | `recharge_payMethodProvided_passesThrough` | ✅ |
| 6 | `/wallet/recharge` | POST | `recharge_marksPaidAndCallsWalletRecharge` | ✅ |
| 7 | `/wallet/recharge` | POST | `recharge_returnsOrderNoAndPaidStatus` | ✅ |
| 8 | `/wallet/recharge` | POST | `recharge_serviceThrowsPropagates` | ✅ |
| 9 | `/wallet/recharge` | POST | `recharge_titleIncludesAmount` | ✅ |
| 10 | `/orders` | GET | `orders_passesAllArgs` | ✅ |
| 11 | `/orders` | GET | `orders_payStatusNullPassesThrough` | ✅ |
| 12 | `/order/{orderNo}` | GET | `orderDetail_returnsOrder` | ✅ |
| 13 | `/order/{orderNo}` | GET | `orderDetail_notFound` | ✅ |

**总计：13 个 @Test，4 个 endpoint 100% 覆盖。**

---

## 3. 关键 Mockito 模式

### 3.1 mockStatic SecurityUtils.getUserId（不是 getUsername）

与 W2.5 `OpcFinanceController` 的区别：本 controller 调 `SecurityUtils.getUserId()`（返回 `Long`）
而非 `getUsername()`（返回 `String`）。

```java
@BeforeEach
void setupSecurityMock() {
    securityMock = mockStatic(SecurityUtils.class);
    securityMock.when(SecurityUtils::getUserId).thenReturn(USER_ID);  // Long，不是 String
}
```

`securityMock.verify(() -> SecurityUtils.getUserId(), atLeastOnce())` 验证静态调用。

### 3.2 ArgumentCaptor 验证 order 8 字段

```java
ArgumentCaptor<OpcBillingOrder> captor = ArgumentCaptor.forClass(OpcBillingOrder.class);
verify(orderMapper).insert(captor.capture());
OpcBillingOrder order = captor.getValue();
assertTrue(order.getOrderNo().startsWith("O"));     // 自动生成的 orderNo 前缀
assertEquals(COMPANY_ID, order.getCompanyId());
assertEquals(USER_ID, order.getUserId());
assertEquals("RECHARGE", order.getOrderType());
assertEquals("钱包充值 ¥" + AMOUNT, order.getTitle());
assertEquals(0, order.getDiscount().compareTo(BigDecimal.ZERO));  // 无优惠
assertEquals("PENDING", order.getPayStatus());      // insert 时是 PENDING（markPaid 前）
```

钉死 controller 的「订单字段初始化」语义，防止未来重构改字段顺序或丢失默认值。

### 3.3 `argThat` 验证 MOCK- 前缀

```java
verify(orderMapper).markPaid(anyString(), eq("ALIPAY"), argThat(s ->
        s != null && s.startsWith("MOCK-")));
```

`argThat(Predicate)` 自定义匹配器。Controller 用 `"MOCK-" + System.currentTimeMillis()` 模拟即时
到账，测试用 `argThat(s -> s.startsWith("MOCK-"))` 验证前缀正确而不绑死时间戳。

### 3.4 异常透传验证（recharge 多步调用）

```java
@Test
void recharge_serviceThrowsPropagates() {
    when(walletService.recharge(...))
            .thenThrow(new OpcException("余额更新失败"));

    OpcException ex = assertThrows(OpcException.class,
            () -> controller.recharge(req));

    // 验证前面两步（insert + markPaid）已经执行
    verify(orderMapper).insert(any(OpcBillingOrder.class));
    verify(orderMapper).markPaid(anyString(), anyString(), anyString());
}
```

Controller 的 recharge 流程：`insert → markPaid → walletService.recharge`。如果 service.recharge
抛异常，前两步已经执行（mock 已经记录），验证这点很重要 — 否则 controller 可能错误地「事务回滚」
导致部分写入（实际生产靠 `@Transactional` 兜底）。

### 3.5 payMethod 默认 ALIPAY

```java
@Test
void recharge_payMethodNull_defaultsToAlipay() {
    RechargeRequest req = rechargeRequest();  // payMethod 默认 null

    controller.recharge(req);

    ArgumentCaptor<OpcBillingOrder> captor = ArgumentCaptor.forClass(OpcBillingOrder.class);
    verify(orderMapper).insert(captor.capture());
    assertEquals("ALIPAY", captor.getValue().getPayMethod());
}
```

钉死 controller 的三目表达式 `req.payMethod == null ? "ALIPAY" : req.payMethod` 默认行为。

---

## 4. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| `result.get("code") == 200` 所有成功路径 | 全部 13 例 |
| `order.orderNo.startsWith("O")` 自动生成 | `recharge_setsAllFieldsOnOrder` |
| `order.discount == 0`（无优惠） | `recharge_setsAllFieldsOnOrder` |
| `order.paidAmount == order.amount`（无优惠时） | `recharge_setsAllFieldsOnOrder` |
| `order.payStatus == "PENDING"` insert 时 | `recharge_setsAllFieldsOnOrder` |
| `req.payMethod == null → "ALIPAY"` | `recharge_payMethodNull_defaultsToAlipay` |
| `req.payMethod == "WECHAT" → 透传` | `recharge_payMethodProvided_passesThrough` |
| markPaid 调 1 次 + walletService.recharge 调 1 次 | `recharge_marksPaidAndCallsWalletRecharge` |
| 返回 data 含 `orderNo + status:"PAID"` | `recharge_returnsOrderNoAndPaidStatus` |
| `payStatus=null` orders 透传 | `orders_payStatusNullPassesThrough` |
| `orderDetail` 不存在 → success(null) 不抛 | `orderDetail_notFound` |

---

## 5. 与 W2.5 FinanceController 的对照

| 维度 | Finance Ctrl (W2.5) | **Billing Ctrl (W2.6)** |
|---|---|---|
| Endpoint 数 | 11 | **4** |
| @Test 数 | 20 | **13** |
| 依赖 service 数 | 2 (Voucher + BankFlow) | **1 service + 1 mapper** |
| SecurityUtils mock | `getUsername` (String) | **`getUserId` (Long)** |
| 业务逻辑密度 | 薄（基本透传 + 状态码包装） | **厚**（recharge 编排 3 步：insert + markPaid + walletService.recharge） |
| `verifyNoInteractions` 测占位 | ✅（extractFlows/dailyReport） | **N/A**（无占位 endpoint） |
| `argThat` 自定义匹配器 | — | **✅**（验证 MOCK- 前缀） |

---

## 6. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` | 静态分析 |
| Controller recharge 有「先 insert 订单再 markPaid 再 walletService.recharge」三步非事务调用，部分失败可能订单已 insert 但钱包未到账 | 生产应在 `@Transactional` 方法内调用或加 Saga 补偿。W2.6 测试钉死「service 抛异常时前面两步已执行」的事实，但不解决生产风险 |
| `MOCK-` 前缀是硬编码（生产应走支付宝回调） | 测试仅验证前缀格式，不绑死具体时间戳 |
| `discount` / `paidAmount` 写死等于 amount（无优惠路径） | 未来加优惠功能时需扩展测试 |
| `OpcBillingOrder` 没有 lifecycle（订单状态机） | 当前只有 PENDING → PAID，未来加 REFUND / CLOSED 状态需补测试 |

---

## 7. 验收 Checkpoint

- [x] 4 个 endpoint 100% 覆盖
- [x] 13 个 @Test，全部 `@DisplayName` 描述场景
- [x] `@MockitoSettings(strictness = LENIENT)` 与 W2.x 一致
- [x] `mockStatic(SecurityUtils.getUserId)` 正确配对
- [x] ArgumentCaptor 验证 8 字段写入（含 payMethod 默认 ALIPAY）
- [x] `argThat` 自定义匹配器（MOCK- 前缀）
- [x] 异常透传测试（recharge 多步调用部分失败时不吞异常）
- [x] title 动态包含金额测试（防止未来删改 `¥` 前缀）
- [x] pom.xml 无需变更

---

## 8. W2 累计 + 后续候选

| W2 子任务 | 状态 | @Test 数 |
|---|---|---|
| W2.1: TaxReport + Wallet | ✅ | 8 + 10 = 18 |
| W2.2: Invitation | ✅ | 23 |
| W2.3: Voucher | ✅ | 21 |
| W2.4: BankFlow | ✅ | 15 |
| W2.5: FinanceController | ✅ | 20 |
| W2.6: BillingController | ✅ | 13 |
| **W2 累计** | — | **110 @Test, 12 commit** |

| 任务 | 候选 | 估值 |
|---|---|---|
| `OpcInvitationController` 单测（5 endpoint + InvitationService） | W2.7 | 1h |
| `OpcFinanceTaxReportController` 单测（3 endpoint） | W2.7 | 45min |
| `OpcUserProfileService` 单测（实名认证状态机） | W3 | 1h |
| 集成测试（@WebMvcTest + Spring context） | W3 | 2 天 |
| `OpcWorkflowTriggerController` 单测（任务 #3，cross-module Feign mock） | W3 | 1.5h |

W2 已交付 110 个单测覆盖核心 service + controller 层。建议 W2.7 收尾 `OpcInvitationController`，
闭环 opc-user-center 测试覆盖。
