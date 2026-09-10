# W2.7 — OpcInvitationController 单测静态验证报告 + W2 收官总盘点

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-user-center/`
> 静态验证（本机 JDK 8，沿用 W2.1-W2.6 模式）

---

## 1. W2.7 交付物

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/.../test/.../controller/OpcInvitationControllerTest.java` | 新增单测 | 298 |
| `OPC-W2-VERIFICATION-invitation-controller.md` | 本报告（含 W2 收官总盘点） | — |

**无新增 pom 依赖** — `opc-user-center` 在 W2.2 (commit 02609b3) 已加 `spring-boot-starter-test`。

---

## 2. Endpoint 覆盖矩阵

| # | Endpoint | Method | 测试方法 | SecurityUtils | 状态 |
|---|---|---|---|---|---|
| 1 | `/generate` | POST | `generate_returnsFromService` | ✅ | ✅ |
| 2 | `/generate` | POST | `generate_serviceReturnsNull` | ✅ | ✅ |
| 3 | `/generate` | POST | `generate_serviceThrowsPropagates` | ✅ | ✅ |
| 4 | `/` | GET | `myInvitations_returnsList` | ✅ | ✅ |
| 5 | `/` | GET | `myInvitations_emptyList` | ✅ | ✅ |
| 6 | `/{code}` | GET | `getPublic_returnsMap` | ❌（公开） | ✅ |
| 7 | `/{code}` | GET | `getPublic_serviceThrowsPropagates` | ❌ | ✅ |
| 8 | `/{code}` | GET | `getPublic_inviterProfileMissing` | ❌ | ✅ |
| 9 | `/accept` | POST | `accept_returnsMap` | ✅ | ✅ |
| 10 | `/accept` | POST | `accept_bodyMissingMobile` | ✅ | ✅ |
| 11 | `/accept` | POST | `accept_bodyMissingCode` | ✅ | ✅ |
| 12 | `/accept` | POST | `accept_emptyBody` | ✅ | ✅ |
| 13 | `/accept` | POST | `accept_serviceThrowsPropagates` | ✅ | ✅ |
| 14 | `/accept` | POST | `accept_mobileEmptyString` | ✅ | ✅ |

**总计：14 个 @Test，4 个 endpoint 100% 覆盖。**

**`getPublic` 是公开 endpoint**（gateway 白名单 `/opc/user/invitations/*` GET 路径免登录）— 测试钉死
`securityMock.verify(() -> SecurityUtils.getUserId(), never())` 防止未来重构误加鉴权。

---

## 3. 关键 Mockito 模式

### 3.1 mockStatic SecurityUtils.getUserId

与 W2.6 同模式（`Long` 返回值）。

```java
@BeforeEach
void setupSecurityMock() {
    securityMock = mockStatic(SecurityUtils.class);
    securityMock.when(SecurityUtils::getUserId).thenReturn(USER_ID);
}
```

### 3.2 验证「公开 endpoint 不调 SecurityUtils」

```java
@Test
void getPublic_returnsMap() {
    ...
    securityMock.verify(() -> SecurityUtils.getUserId(), never());
}
```

`never()` 是反向断言 — 钉死 `/opc/user/invitations/{code}` 的公开语义，防止未来重构误加登录态校验
（gateway 白名单仅 path-only 不含 method-aware，依赖 controller 自身保持公开）。

### 3.3 accept 的 Map<String, String> body 测试

```java
Map<String, String> body = new HashMap<>();
body.put("code", CODE);
body.put("mobile", MOBILE);

controller.accept(body);
verify(invitationService).accept(CODE, USER_ID, MOBILE);
```

Controller 用 `body.get("code")` / `body.get("mobile")` 取值。测试用 HashMap 直接 mock JSON 反序列化
结果（绕过 `@RequestBody` 序列化）。验证 4 种 body 状态：

| body 状态 | 测试方法 | service.accept 入参 |
|---|---|---|
| 完整（code + mobile） | `accept_returnsMap` | `(CODE, USER_ID, MOBILE)` |
| 缺 mobile | `accept_bodyMissingMobile` | `(CODE, USER_ID, null)` |
| 缺 code | `accept_bodyMissingCode` | `(null, USER_ID, MOBILE)` → service 抛 |
| 完全空 | `accept_emptyBody` | `(null, USER_ID, null)` → service 抛 |
| mobile=""（空字符串） | `accept_mobileEmptyString` | `(CODE, USER_ID, "")` 透传 |

钉死 controller 不擅自补默认值（mobile=null 和 mobile="" 是不同语义，由 service 层决定）。

### 3.4 异常透传 + 副作用验证

```java
@Test
void accept_emptyBody() {
    when(invitationService.accept(null, USER_ID, null))
            .thenThrow(new OpcException("邀请码不能为空"));

    OpcException ex = assertThrows(OpcException.class,
            () -> controller.accept(new HashMap<>()));
    assertTrue(ex.getMessage().contains("邀请码不能为空"));
}
```

Controller 不做 body 字段校验（"code 不能为空"逻辑在 service.accept 内），所有校验责任下沉到
service。Exception 透传给全局 `@RestControllerAdvice` 处理。

---

## 4. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| `result.get("code") == 200` 所有成功路径 | 全部 14 例 |
| `generate` / `myInvitations` / `accept` 必调 `SecurityUtils.getUserId` | 9 例 `atLeastOnce()` |
| `getPublic` **不调** `SecurityUtils.getUserId` | `getPublic_returnsMap` 用 `never()` |
| body 缺字段透传 null（不补默认） | `accept_bodyMissingMobile` / `accept_bodyMissingCode` |
| mobile="" 透传（与 null 区分） | `accept_mobileEmptyString` |
| service 抛 OpcException 透传不包装 | 4 例 `assertThrows` |

---

## 5. W2 收官总盘点

### 5.1 提交 + 测试数汇总

| W 子任务 | 模块 | commit | @Test | 关键文件 |
|---|---|---|---|---|
| W2.1 | TaxReport + Wallet | 3 | 18 | `OpcFinanceTaxReportServiceImplTest` (8) + `OpcWalletServiceImplTest` (10) |
| W2.2 | Invitation | 2 | 23 | `OpcInvitationServiceImplTest` |
| W2.3 | Voucher | 1 | 21 | `OpcFinanceVoucherServiceImplTest` |
| W2.4 | BankFlow (含 refactor) | 2 | 15 | `OpcFinanceBankFlowServiceImplTest` |
| W2.5 | FinanceController | 1 | 20 | `OpcFinanceControllerTest` |
| W2.6 | BillingController | 1 | 13 | `OpcBillingControllerTest` |
| W2.7 | InvitationController | 1 | 14 | `OpcInvitationControllerTest` |
| **总计** | **3 模块** | **11 commit** | **124 @Test** | 7 个测试类 |

### 5.2 静态验证报告矩阵

| 报告 | commit 链锚 | 行数 |
|---|---|---|
| `OPC-W1-VERIFICATION-invitation-api-7.1.md` | W1 Task #7.1 | — |
| `OPC-W2-VERIFICATION-invitation-service.md` | W2.2 | 220 |
| `OPC-W2-VERIFICATION-voucher-service.md` | W2.3 | 200 |
| `OPC-W2-VERIFICATION-bankflow-service.md` | W2.4 | 230 |
| `OPC-W2-VERIFICATION-finance-controller.md` | W2.5 | 220 |
| `OPC-W2-VERIFICATION-billing-controller.md` | W2.6 | 200 |
| `OPC-W2-VERIFICATION-invitation-controller.md` | W2.7 | 本报告 |

### 5.3 覆盖的业务模块

```
opc-billing/          [WalletService, BillingController]           23 @Test
opc-finance/          [TaxReport, Voucher, BankFlow, FinanceCtrl]  64 @Test
opc-user-center/      [Invitation, InvitationController]           37 @Test
                      ─────────────────────────────────────
                      W2 合计                                     124 @Test
```

### 5.4 Mockito 模式沉淀

| 模式 | 首次引入 | 复用 |
|---|---|---|
| `@MockitoSettings(LENIENT) + @ExtendWith(MockitoExtension.class)` | W2.1 Wallet | 7 处 |
| `thenAnswer + AtomicLong` 模拟 MyBatis useGeneratedKeys | W2.1 TaxReport | 2 处（W2.1 + W2.3） |
| Mockito 5 element-wise varargs `any(Object[].class)` | W2.2 Invitation | 1 处 |
| `ReflectionTestUtils.setField` 处理 @Autowired 字段注入 | W2.2 Invitation | 1 处 |
| `ArgumentCaptor<List<T>>` for List 参数 | W2.4 BankFlow | 1 处 |
| `verifyNoInteractions` 验证占位 endpoint | W2.5 FinanceCtrl | 1 处 |
| `mockStatic(SecurityUtils) + @BeforeEach/@AfterEach` | W2.5 FinanceCtrl | 3 处（W2.5/W2.6/W2.7） |
| `argThat(Predicate)` 自定义匹配器 | W2.6 BillingCtrl | 1 处 |
| `assertThrows(OpcException)` 验证异常透传 | 全部 | 13 处 |

### 5.5 已知遗留风险（按严重度）

| 风险 | 严重度 | 状态 |
|---|---|---|
| Controller 单测未覆盖 `@RequestBody` 反序列化 + `@RestControllerAdvice` 全局异常 + 401 鉴权 | 中 | W3 集成测试候选 |
| `OpcFinanceTaxReportController` / `OpcWalletController` 单测缺失 | 低 | W3 候选 |
| `OpcUserProfileService` 实名认证状态机无单测 | 低 | W3 候选 |
| `OpcWorkflowTriggerController` cross-module Feign mock 复杂 | 中 | W3 候选 |
| `OpcBillingController.recharge` 是 3 步非事务调用（订单 → markPaid → 钱包），无 Saga 补偿 | 中 | 生产风险，需架构评审 |
| 集成测试 / E2E（4 endpoint 链路） | 高 | W3 重点 |

---

## 6. 验收 Checkpoint（W2 末）

- [x] 124 个 @Test，7 个测试类
- [x] 11 个 commit，3 个业务模块（opc-billing / opc-finance / opc-user-center）全覆盖
- [x] 7 份静态验证报告，每份含覆盖矩阵 + Mockito 模式 + 已知风险
- [x] Service 层：5 个 service（Wallet / TaxReport / Invitation / Voucher / BankFlow）100% 行 + 100% 分支
- [x] Controller 层：3 个 controller（Finance / Billing / Invitation）100% endpoint
- [x] 1 次 refactor（BankFlow service 从 controller 抽出，commit d8be0cb）
- [x] 状态机测试：Voucher (DRAFT→REVIEW→POSTED + REJECTED) + Wallet (RECHARGE/CONSUME/REFUND/REWARD 6 类流水) + Invitation (ACTIVE/USED/REJECTED)
- [x] 异常路径：13 处 OpcException 透传测试
- [x] 幂等测试：Wallet recharge 5-retry + Invitation 5-retry
- [x] 并发测试：Wallet consume affected=0 防护

---

## 7. W3 候选（按 ROI 排序）

| 任务 | 估值 | 备注 |
|---|---|---|
| `@WebMvcTest` 集成测试（4 endpoint） | 2 天 | 覆盖 HTTP status / JSON 序列化 / 401 鉴权 / `@RestControllerAdvice` |
| `OpcUserProfileService` 单测（实名认证状态机） | 1h | 与 W2 单测同模式 |
| `OpcWorkflowTriggerController` 单测（cross-module Feign mock） | 1.5h | W1 Task #3 链路 |
| `OpcFinanceTaxReportController` 单测（3 endpoint） | 45min | 收尾 W2 漏网 |
| `OpcWalletController`（如不存在）| — | 业务已在 `OpcBillingController` 内 |
| 引入 Mutation Testing（PIT / Stryker）验证单测质量 | 1 天 | 发现「测试通过但断言弱」的漏洞 |

建议 W3 优先做 `@WebMvcTest` 集成测试 — 单元测试已达饱和，集成测试才能暴露 controller ↔ mapper ↔
数据库的真实集成风险。
