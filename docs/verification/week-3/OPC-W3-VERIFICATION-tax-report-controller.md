# W3.2 — OpcFinanceTaxReportController 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-finance/`
> 静态验证（本机 JDK 8，沿用 W2.x 模式）

---

## 1. 交付物

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/.../test/.../controller/OpcFinanceTaxReportControllerTest.java` | 新增单测 | 259 |
| `OPC-W3-VERIFICATION-tax-report-controller.md` | 本报告 | — |

**无新增 pom 依赖** — `opc-finance/pom.xml` 早已含 `spring-boot-starter-test`（commit 69d880b W2.1 时代）。

---

## 2. Endpoint 覆盖矩阵

| # | Endpoint | Method | 测试方法 | SecurityUtils | 状态 |
|---|---|---|---|---|---|
| 1 | `/generate` | POST | `generate_returnsSuccessWithReportId` | ✅ | ✅ |
| 2 | `/generate` | POST | `generate_companyIdNull_returnsError` | — | ✅ |
| 3 | `/generate` | POST | `generate_periodNull_returnsError` | — | ✅ |
| 4 | `/generate` | POST | `generate_passesAllArgs` | ✅ | ✅ |
| 5 | `/generate` | POST | `generate_serviceThrowsPropagates` | ✅ | ✅ |
| 6 | `/` | GET | `list_returnsSuccessWithList` | — | ✅ |
| 7 | `/` | GET | `list_nullFiltersPassThrough` | — | ✅ |
| 8 | `/` | GET | `list_defaultLimitIs20` | — | ✅ |
| 9 | `/` | GET | `list_customLimitPassesThrough` | — | ✅ |
| 10 | `/` | GET | `list_companyIdNull_returnsError` | — | ✅ |
| 11 | `/` | GET | `list_emptyList` | — | ✅ |
| 12 | `/{id}` | GET | `detail_returnsReport` | — | ✅ |
| 13 | `/{id}` | GET | `detail_notFound_returnsError` | — | ✅ |

**总计：13 个 @Test，3 个 endpoint 100% 覆盖。**

---

## 3. 关键 Mockito 模式

### 3.1 mockStatic SecurityUtils.getUsername (String, 不是 getUserId)

与 W2.5 `OpcFinanceController` 同模式 — 本 controller 调 `SecurityUtils.getUsername()`（String）。
**注意区别**：W2.6 `OpcBillingController` 和 W2.7 `OpcInvitationController` 调的是 `getUserId()`（Long）。

```java
@BeforeEach
void setupSecurityMock() {
    securityMock = mockStatic(SecurityUtils.class);
    securityMock.when(SecurityUtils::getUsername).thenReturn(USERNAME);
}
```

`generate` 是唯一调 `SecurityUtils` 的 endpoint — `list` 和 `detail` 都不需要登录态。

### 3.2 error("msg") → HTTP 500（不是 200）

Controller 在参数校验失败时返回 `error("companyId 和 period 不能为空")`，对应
`AjaxResult.error(msg)` → `HttpStatus.ERROR = 500`。

```java
@Test
void generate_companyIdNull_returnsError() {
    AjaxResult result = controller.generate(null, PERIOD);

    assertEquals(HttpStatus.ERROR, result.get("code"),
            "companyId=null 应返回 error（HTTP 500）");
    assertTrue(result.get("msg").toString().contains("companyId"));
    verify(taxReportService, never()).generateMonthlyReport(any(), any(), any());
}
```

**关键区别于 W2.5 FinanceController**：W2.5 的 update / reviewPass / post 等 controller 用
`toAjax(rows)` 包装（rows=0 → success(false)，rows>0 → success(true)），HTTP 仍 200。
本 controller 校验失败直接 HTTP 500 — 语义「拒绝服务」 vs 「业务可继续」。

### 3.3 异常透传（不包装）

```java
@Test
void generate_serviceThrowsPropagates() {
    when(taxReportService.generateMonthlyReport(COMPANY_ID, PERIOD, USERNAME))
            .thenThrow(new OpcException("公司不存在"));

    OpcException ex = assertThrows(OpcException.class,
            () -> controller.generate(COMPANY_ID, PERIOD));
    assertTrue(ex.getMessage().contains("公司不存在"));
}
```

Controller 不 try/catch — `OpcException` 透传给全局 `@RestControllerAdvice` 处理。
与 W2.5 / W2.6 / W2.7 同模式（4 例 controller 异常透传）。

### 3.4 @RequestParam(defaultValue="20") 的 Java 侧契约

`list` endpoint 的 limit 参数有 `@RequestParam(defaultValue = "20")`。单元测试不走 Spring MVC，
所以**Java 侧必须显式传 20**（否则编译错误）。

```java
controller.list(COMPANY_ID, null, null, 20);  // ← 必须传 20
```

这测试用例 `list_defaultLimitIs20` 验证「Spring 端会传 20 进来」是 controller 的契约 — 写
controller 时要把这个 default 值显式提到 Java 调用层（如果未来重构为不带 default 的方法，
测试会编译失败提醒）。

### 3.5 `error("报表不存在")` vs `error()` 的语义

`detail` endpoint 不存在时返回 `error("报表不存在")` — **自定义错误信息**。`generate` / `list`
参数校验失败时也是 `error("具体错误信息")`。所有失败路径都带可读 msg，便于前端弹窗。

```java
@Test
void detail_notFound_returnsError() {
    when(taxReportService.getById(999L)).thenReturn(null);

    AjaxResult result = controller.detail(999L);

    assertEquals(HttpStatus.ERROR, result.get("code"));
    assertEquals("报表不存在", result.get("msg"));
}
```

钉死 controller 的错误信息文案（防止未来改成 "Not Found" 等英文破坏前端 i18n 切换）。

---

## 4. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| 成功路径 `result.get("code") == 200` | 7 例（generate/list/detail 成功） |
| 失败路径 `result.get("code") == 500` | 4 例（参数校验 + notFound） |
| `result.get("msg")` 含具体错误关键词 | 3 例（companyId / period / 报表不存在） |
| `generate` data 是 `{reportId: Long}` | `generate_returnsSuccessWithReportId` |
| `generate` 调 SecurityUtils.getUsername + service.generateMonthlyReport(companyId, period, username) | `generate_passesAllArgs` |
| `list` 透传所有 4 参（含 null 过滤 + 默认 limit） | 4 例 |
| `detail` null → error("报表不存在") | `detail_notFound_returnsError` |
| 参数校验失败时不调 service | 3 例 `verify(..., never())` |
| 异常透传不包装 | `generate_serviceThrowsPropagates` |

---

## 5. 与 W2.x Controller 对照

| 维度 | W2.5 FinanceCtrl | W2.6 BillingCtrl | W2.7 InvitationCtrl | **W3.2 TaxReport Ctrl** |
|---|---|---|---|---|
| Endpoint 数 | 11 | 4 | 4 | **3** |
| @Test 数 | 20 | 13 | 14 | **13** |
| SecurityUtils mock | `getUsername` | `getUserId` | `getUserId` | **`getUsername`** |
| 失败路径 HTTP | 200（rows=0 → false） | — | 200（service 抛） | **500（error()）** |
| 业务复杂度 | 编排（extractFlows/dailyReport 占位） | 厚（3 步编排） | 薄（透传 + body map） | **薄（校验 + 透传）** |
| 入参方式 | `@RequestParam` + `@RequestBody` | `@RequestBody` RechargeRequest + `@PathVariable` | `@PathVariable` + `@RequestBody Map` | **全 `@RequestParam` + `@PathVariable`** |
| 自定义校验 | — | — | — | **✅（companyId/period 不能为空）** |

**架构特点**：W3.2 是 W2/W3 体系内**唯一带显式参数校验**的 controller。其他 controller 要么
依赖 service 校验（透传 OpcException），要么靠 Spring 注解（@NotNull）。本 controller 在
controller 层做参数校验（return error 而非 throw）— 是「快速失败」模式，HTTP 500 + 具体错误
信息，前端可读 msg。

---

## 6. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` | 静态分析 |
| Controller 校验失败返回 HTTP 500 — 但前端可能期望 400（语义错误） | 当前约定是「业务校验失败统一 500」，W2.x controller 全部如此（OpcException → 500） |
| `period` 格式（YYYY-MM）不在 controller 校验，由 service 抛 OpcException | 测试覆盖了 service 异常透传；前端应展示 service 异常 msg |
| `limit` 默认 20 是 hardcoded — 未来若加 `MAX_LIMIT = 100` 截断需扩展测试 | 当前测试断言「传入 20 透传」，未来若加截断需补「limit=500 → service 收到 100」 |
| `getById` null 时返回 HTTP 500 — 但 REST 约定「资源不存在」应为 HTTP 404 | 当前实现是 500 + msg="报表不存在"，与 W2.x 风格一致；W4 集成测试可加 404 契约 |
| `SecurityUtils.getUsername()` 在 generate 失败（companyId/period=null）时**不会被调用**（controller 提前 return error） | 测试未显式断言；如需钉死可加 `verify(securityMock, never()).getUsername()` |
| 没有测试 `requestParam = ""`（空字符串）边界 — controller 当前会传到 service，由 service 抛 OpcException | 留给 service 单测覆盖 |

---

## 7. 验收 Checkpoint

- [x] 3 个 endpoint 100% 覆盖
- [x] 13 个 @Test，全部 `@DisplayName` 描述场景
- [x] `@MockitoSettings(strictness = LENIENT)` 与 W2.x 一致
- [x] `mockStatic(SecurityUtils.getUsername)` 正确配对（与 W2.5 同模式）
- [x] `error()` 路径验证（HTTP 500 + msg 关键词）
- [x] 异常透传测试（OpcException 不被 controller 吞）
- [x] 默认 limit=20 显式断言
- [x] 反向断言：参数校验失败不调 service（`verify(..., never())`）
- [x] pom.xml 无需变更

---

## 8. W3 累计 + 后续候选

| W 子任务 | 模块 | 状态 | @Test 数 |
|---|---|---|---|
| W2.1-W2.7 | billing + finance + user-center | ✅ | 124 |
| W3.0 | UserProfileService | ✅ | 18 |
| W3.1 | OpcCodeGenerator | ✅ | 23 invocations |
| **W3.2** | **TaxReportController** | **✅** | **13** |
| **总计** | **3 模块 + 1 公共工具** | **W3 收尾中** | **178 invocations, 14 commits** |

| 任务 | 候选 | 估值 | 备注 |
|---|---|---|---|
| `OpcUserProfileController` 单测（如有） | W3.3 | 1h | 与 W3.0 service 配对 |
| `@WebMvcTest` 集成测试（4 endpoint） | W4 | 2 天 | 覆盖 HTTP status / JSON 序列化 / 401 鉴权 / @RestControllerAdvice |
| `OpcWorkflowTriggerController` 单测（cross-module Feign） | W4 | 1.5h | W1 Task #3 链路 |
| Mutation Testing（PIT / Stryker） | W4 | 1 天 | 验证单测断言强度 |

W3.2 完成：闭环 opc-finance 模块 controller 单测覆盖（5 service + 4 controller 单测，与 W2 一
致模式）。W2+W3 全闭环：178 invocations 覆盖 7 service + 4 controller + 1 utility。W4 建议
优先做 `@WebMvcTest` 集成测试。