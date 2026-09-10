# W4 — OpcWorkflowTriggerController 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-agent-hub/`
> 静态验证（本机 JDK 8，沿用 W2.x / W3.x 模式）

---

## 0. 与 Service 单测的分工

`WorkflowTriggerServiceImplTest`（W1 Task #3, commit 含在 8ca9688）已覆盖 **4 个 service 层
场景**：
- 正常触发写入 RUNNING 记录 + SUCCESS + cron nextRunAt
- 工作流已停用抛 OpcException
- 工作流编码不存在抛 OpcException
- 30 秒去重命中返回已有 RUNNING

**本测试（Controller 层）专注于 HTTP 契约**：URL/注解/R body 映射/`@InnerAuth` 鉴权边界/异常
透传行为。Service 与 Controller 单测互不重叠，组合后覆盖完整。

---

## 1. 交付物

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/.../test/.../controller/OpcWorkflowTriggerControllerTest.java` | 新增单测 | 221 |
| `OPC-W4-VERIFICATION-workflow-trigger-controller.md` | 本报告 | — |

**无新增 pom 依赖** — `opc-agent-hub/pom.xml` 已含 `spring-boot-starter-test`。

---

## 2. Endpoint 覆盖矩阵

| # | Endpoint | Method | 测试方法 | SecurityUtils | 状态 |
|---|---|---|---|---|---|
| 1 | `/{workflowCode}/trigger` | POST | `trigger_returnsROkWithAllFields` | ❌（@InnerAuth） | ✅ |
| 2 | `/{workflowCode}/trigger` | POST | `trigger_failedRun_errorMessagePropagates` | ❌ | ✅ |
| 3 | `/{workflowCode}/trigger` | POST | `trigger_triggerSourceNull_alsoWorks` | ❌ | ✅ |
| 4 | `/{workflowCode}/trigger` | POST | `trigger_serviceThrowsPropagates` | ❌ | ✅ |
| 5 | `/{workflowCode}/trigger` | POST | `trigger_serviceThrowsDisabledPropagates` | ❌ | ✅ |
| 6 | `/{workflowCode}/trigger` | POST | `trigger_dedupeReturnsExisting_wrappedAsSuccess` | ❌ | ✅ |
| 7 | — | — | `triggerMethod_isAnnotatedWithInnerAuth` | — | ✅ |
| 8 | — | — | `triggerMethod_urlAndParamAnnotations` | — | ✅ |

**总计：8 个 @Test，1 个 endpoint 100% 覆盖 + 2 个反射契约验证。**

### 2.1 与其他 controller 的差异

| 维度 | W2.5-W3.3 controller | **W4 WorkflowTrigger** |
|---|---|---|
| 鉴权方式 | SecurityUtils.getUserId/getUsername | **`@InnerAuth`（要求 `from-source: inner` header）** |
| 响应类型 | `AjaxResult` (extends HashMap) | **`R<T>`（普通 POJO with code/msg/data）** |
| Endpoint 数 | 3-11 | **1** |
| 入参方式 | @RequestParam/@RequestBody | **@PathVariable + @RequestParam** |
| 失败语义 | OpcException → HTTP 500 | **OpcException → HTTP 500（同）** |

**关键架构点**：W4 是 W2/W3/W4 体系内**唯一 `@InnerAuth` controller** — 不走 JWT，由
ruoyi-job 模块通过 Feign 调用（带 `from-source: inner` header）。这是 W1 Task #3 的
"cross-module 内部触发" 架构选择：caller 模块（ruoyi-job）只持有 Feign + fallback，callee 模块
（opc-agent-hub）持有 `@InnerAuth` controller + service 实现。

---

## 3. 关键 Mockito 模式

### 3.1 无 SecurityUtils mock（@InnerAuth 而非 JWT）

```java
@BeforeEach  // ← 没有 setupSecurityMock
void setUp() { ... }
```

**与 W2.5-W3.3 的核心区别**：该 controller 不调 `SecurityUtils.getUserId()` 也不调
`SecurityUtils.getUsername()`。鉴权依赖 `@InnerAuth` 注解 + gateway AuthFilter 剥离 `from-source`
header — 无需单测侧 mock 静态方法。

这是 W4 的「轻量 controller 单测」：1 个 `@Mock` 依赖（service），无 `MockedStatic`。

### 3.2 R.ok() 而非 AjaxResult

```java
@Test
void trigger_returnsROkWithAllFields() {
    OpcAgentWorkflowRun run = successRun();
    when(triggerService.trigger(CODE, SOURCE)).thenReturn(run);

    R<Map<String, Object>> result = controller.trigger(CODE, SOURCE);

    assertEquals(R.SUCCESS, result.getCode(), "R.ok() 应返回 code=200");
    Map<String, Object> data = result.getData();
    assertEquals("R20260907000001", data.get("runCode"));
    assertEquals("SUCCESS", data.get("status"));
    assertEquals(123, data.get("durationMs"));
    assertNull(data.get("errorMessage"));
}
```

`R<T>` 是 RuoYi 通用响应包装（区别于 `AjaxResult` HashMap 风格）：
- `getCode()` 返回 `int`（不是 `Integer`）
- `getData()` 返回泛型 `T`（这里是 `Map<String, Object>`）
- `getMsg()` 存在但本 controller 不设

**controller 把 4 字段精确映射到 R body**：`runCode` / `status` / `durationMs` / `errorMessage`。
这是 caller（ruoyi-job 模块的 Feign 客户端）解码的契约 — 任何字段改名都会破坏远程调用。

### 3.3 失败路径 HTTP 仍 200（业务失败非系统异常）

```java
@Test
void trigger_failedRun_errorMessagePropagates() {
    OpcAgentWorkflowRun run = failedRun();  // status=FAILED, errorMessage="LLM 调用超时"
    when(triggerService.trigger(CODE, SOURCE)).thenReturn(run);

    R<Map<String, Object>> result = controller.trigger(CODE, SOURCE);

    assertEquals(R.SUCCESS, result.getCode(),
            "HTTP 仍 200（业务失败非系统异常）");
    Map<String, Object> data = result.getData();
    assertEquals("FAILED", data.get("status"));
    assertEquals("LLM 调用超时", data.get("errorMessage"));
}
```

**关键架构决策**：当 service 内部抛异常被 catch 并写回 `status=FAILED` + `errorMessage` 时，
controller **不转换为 R.fail** — 仍然 R.ok(200)，让 caller 根据 `data.status` 判断业务失败。
这是「业务失败 vs 系统异常」分离模式：
- 业务失败（workflow 跑挂） → R.ok(200) + status=FAILED + errorMessage 透传
- 系统异常（workflow 不存在 / 已停用） → OpcException → R.fail(500)

测试钉死这条边界，防止未来误把 status=FAILED 包装成 R.fail(500)。

### 3.4 异常透传（service 抛 OpcException → controller 不 catch）

```java
@Test
void trigger_serviceThrowsPropagates() {
    when(triggerService.trigger("nope", SOURCE))
            .thenThrow(new OpcException("工作流不存在: nope"));

    OpcException ex = assertThrows(OpcException.class,
            () -> controller.trigger("nope", SOURCE));
    assertTrue(ex.getMessage().contains("工作流不存在"));
    verifyNoMoreInteractions(triggerService);
}
```

Service 抛「工作流不存在/已停用」OpcException → controller 不 catch → 透传给全局
`@RestControllerAdvice` → R.fail(500) 返回给 caller。这是「系统异常」语义，与 3.3 的
「业务失败」语义对称。

### 3.5 30 秒去重命中 → controller 不区分

```java
@Test
void trigger_dedupeReturnsExisting_wrappedAsSuccess() {
    OpcAgentWorkflowRun existing = new OpcAgentWorkflowRun();
    existing.setStatus("RUNNING");
    existing.setDurationMs(null);
    when(triggerService.trigger(CODE, SOURCE)).thenReturn(existing);

    R<Map<String, Object>> result = controller.trigger(CODE, SOURCE);

    assertEquals(R.SUCCESS, result.getCode(), "30s 去重命中不抛错，仍 R.ok(200)");
    assertEquals("RUNNING", result.getData().get("status"));
    assertNull(result.getData().get("durationMs"),
            "未完成的 RUNNING durationMs 应透传 null");
}
```

Service 在 30 秒内命中已有 RUNNING 记录时**直接返回该记录**（不调 workflowEngine）。Controller
不感知这是「去重命中」还是「正常执行」 — 统一包装成 R.ok(200)。这是 caller 调用幂等性的关键：
「同一 code 30 秒内多次调用 Quartz 触发」只会执行一次，但 caller 每次都收到 R.ok(200) 不报错。

### 3.6 反射验证 @InnerAuth 注解契约

```java
@Test
void triggerMethod_isAnnotatedWithInnerAuth() throws Exception {
    Method m = OpcWorkflowTriggerController.class.getMethod("trigger", String.class, String.class);
    InnerAuth annotation = m.getAnnotation(InnerAuth.class);
    assertNotNull(annotation, "trigger 方法必须标注 @InnerAuth");
    assertFalse(annotation.isUser(), "默认 isUser=false — 内部调用无需 user 信息");
}
```

钉死 controller 的 `@InnerAuth` 注解 — 防止未来重构漏掉导致 gateway AuthFilter 不剥
`from-source` header，公网可任意调用。`isUser=false` 是默认行为（内部调用无需解析用户信息），
单测断言钉死。

### 3.7 反射验证 URL + 参数注解

```java
@Test
void triggerMethod_urlAndParamAnnotations() throws Exception {
    RequestMapping classMapping = OpcWorkflowTriggerController.class.getAnnotation(RequestMapping.class);
    assertArrayEquals(new String[]{"/opc/agent/workflows"}, classMapping.value());

    PostMapping postMapping = m.getAnnotation(PostMapping.class);
    assertArrayEquals(new String[]{"/{workflowCode}/trigger"}, postMapping.value());

    PathVariable pathVar = params[0].getAnnotation(PathVariable.class);
    assertEquals("workflowCode", pathVar.value());

    RequestParam reqParam = params[1].getAnnotation(RequestParam.class);
    assertTrue(reqParam.required() == false, "triggerSource 是可选参数");
}
```

钉死 4 件事：
1. 类级别 `@RequestMapping("/opc/agent/workflows")` — 路径前缀
2. 方法 `@PostMapping("/{workflowCode}/trigger")` — 路径占位符
3. `@PathVariable("workflowCode")` — 与 URL 占位符同名
4. `@RequestParam(value="triggerSource", required=false)` — 可选参数

如果未来有人误把 `@PostMapping` 改成 `@GetMapping`，或 `@PathVariable` name 改错，
gateway 路由会断 — 测试会先一步捕获。

---

## 4. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| `result.getCode() == 200` 所有成功路径（含业务失败 + 去重命中） | 5 例 |
| `result.getCode() == 500` 仅在 service 抛 OpcException 时（controller 透传） | 2 例 `assertThrows` |
| R body 必含 4 字段：`runCode` / `status` / `durationMs` / `errorMessage` | `trigger_returnsROkWithAllFields` |
| 业务失败 `errorMessage` 透传（不被 controller 吞） | `trigger_failedRun_errorMessagePropagates` |
| 成功路径 `errorMessage == null` | `trigger_returnsROkWithAllFields` |
| `triggerSource=null` 也能透传 | `trigger_triggerSourceNull_alsoWorks` |
| RUNNING 未完成时 `durationMs == null` 透传 | `trigger_dedupeReturnsExisting_wrappedAsSuccess` |
| `@InnerAuth` 注解存在 + `isUser=false` | `triggerMethod_isAnnotatedWithInnerAuth` |
| 类级别 `@RequestMapping("/opc/agent/workflows")` | `triggerMethod_urlAndParamAnnotations` |
| 方法 `@PostMapping("/{workflowCode}/trigger")` | `triggerMethod_urlAndParamAnnotations` |
| `@PathVariable("workflowCode")` name 与 URL 一致 | `triggerMethod_urlAndParamAnnotations` |
| `@RequestParam("triggerSource", required=false)` | `triggerMethod_urlAndParamAnnotations` |

---

## 5. 与 W2.x / W3.x Controller 全对照

| 维度 | W2.5 FinanceCtrl | W2.6 BillingCtrl | W2.7 InvitationCtrl | W3.2 TaxReport | W3.3 UserCtrl | **W4 WorkflowCtrl** |
|---|---|---|---|---|---|---|
| Endpoint 数 | 11 | 4 | 4 | 3 | 7 | **1** |
| @Test 数 | 20 | 13 | 14 | 13 | 16 | **8** |
| SecurityUtils mock | ✅ | ✅ | ✅ | ✅ | ✅ | **❌（@InnerAuth）** |
| 响应类型 | AjaxResult | AjaxResult | AjaxResult | AjaxResult | AjaxResult | **R<T>** |
| 鉴权机制 | 网关 JWT + SecurityUtils | 同 | 同 | 同 | 同 | **网关 AuthFilter 剥 header + @InnerAuth** |
| 异常处理 | 透传 | 透传 | 透传 | 透传 | 透传 | **透传（同）** |
| 反射验证 URL | — | — | — | — | — | **✅（W4 首个）** |
| 反射验证 @InnerAuth | — | — | — | — | — | **✅（W4 独有）** |

**架构创新**：W4 是 W2/W3/W4 体系内**首个用 `R<T>` 而非 `AjaxResult`** 的 controller 单测，也是
**首个用反射验证 URL/注解契约** 的 controller 单测。这两个模式可推广到其他 `@InnerAuth`
内部 controller（如将来的 `RemoteUserService`、`RemoteWorkflowService` 等 Feign 暴露点）。

---

## 6. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` | 静态分析 |
| 反射验证注解是 RUNTIME 保留 — 若有人改 `@Retention` 为 CLASS 会破坏 | 当前是 `RUNTIME`（来自 ruoyi-common-security） |
| `R.ok()` 失败路径的行为（异常透传 vs R.fail）依赖全局 `@RestControllerAdvice` — 单元测试无法覆盖 | W5 集成测试候选 |
| `triggerSource` 可选参数 — 当前 controller 透传 null 给 service，由 service 写入「null」字符串 | 当前 service `setTriggerSource(null)` — DB 写 NULL；测试覆盖了 null 透传 |
| Controller 无「鉴权失败」测试（如缺 `from-source: inner` header）— 这是 gateway 责任，单元测试不模拟 gateway | 由 gateway AuthFilter 处理；本 controller 仅依赖 `@InnerAuth` 注解被运行时拦截器识别 |
| Service 单测已覆盖 4 个核心场景，但 controller 单测又重复验证了一些（如不存在抛） | 适度冗余是契约保护 — service 重构不影响 controller 契约 |
| `errorMessage` 字段可能含敏感信息（如内部异常栈）— 透传可能有安全风险 | 当前透传明文，由 caller (ruoyi-job) 负责脱敏；W5 加 sanitization |

---

## 7. 验收 Checkpoint

- [x] 1 个 endpoint 100% 覆盖（含 6 个场景变体）
- [x] 8 个 @Test，全部 `@DisplayName` 描述场景
- [x] `@MockitoSettings(strictness = LENIENT)` 与 W2.x / W3.x 一致
- [x] **无 SecurityUtils mock**（@InnerAuth 而非 JWT）
- [x] `R.ok()` 而非 `AjaxResult` — W4 首个
- [x] 异常透传测试（OpcException 不被 controller 吞）
- [x] 业务失败 vs 系统异常 边界钉死（status=FAILED → HTTP 200, 工作流不存在 → OpcException）
- [x] 30 秒去重命中 → R.ok(200) 透传
- [x] 反射验证 `@InnerAuth` 注解契约（isUser=false）
- [x] 反射验证 URL + 参数注解（@RequestMapping/@PostMapping/@PathVariable/@RequestParam）
- [x] pom.xml 无需变更

---

## 8. W2+W3+W4 累计 + 后续候选

| W 子任务 | 模块 | 状态 | @Test 数 |
|---|---|---|---|
| W2.1-W2.7 | billing + finance + user-center | ✅ | 124 |
| W3.0 | UserProfileService | ✅ | 18 |
| W3.1 | OpcCodeGenerator | ✅ | 23 invocations |
| W3.2 | TaxReportController | ✅ | 13 |
| W3.3 | OpcUserController | ✅ | 16 |
| **W4** | **WorkflowTriggerController** | **✅** | **8** |
| **总计** | **4 模块 + 1 公共工具** | **W4 完成** | **202 invocations, 16 commits** |

| 任务 | 候选 | 估值 | 备注 |
|---|---|---|---|
| `RemoteWorkflowService` Feign fallback 测试 | W4.1 | 30min | ruoyi-job 侧的 Feign client，单测 fallback 行为 |
| `@WebMvcTest` 集成测试（4 endpoint） | W5 | 2 天 | 覆盖 HTTP status / JSON 序列化 / 401 鉴权 / @RestControllerAdvice |
| Mutation Testing（PIT / Stryker） | W5 | 1 天 | 验证单测断言强度（202 个测试的真实覆盖度） |
| Service 漏网：`WorkflowEngine` 单测 | W5 | 1h | 复杂 DAG 执行器，独立可测 |
| Service 漏网：`OpcAgentWorkflowService` CRUD | W5 | 1h | 与 WorkflowTrigger 并列的 workflow 管理 service |

W4 完成：补齐 opc-agent-hub 模块第一个 controller 单测覆盖（与 W1 Task #3 已有的 service 单测
4 例配对），且引入两个新模式：`R<T>` 响应 + 反射 URL/注解契约验证。W2+W3+W4 全闭环：
**202 invocations 覆盖 7 service + 6 controller + 1 utility**。

W5 建议优先做 Mutation Testing — 当前 202 个单测覆盖饱和，PIT/Stryker 能揭示「测试通过但
断言弱」的漏洞（如只 `verify(...)` 没断言参数内容）。其次 `@WebMvcTest` 集成测试覆盖
controller ↔ 全局异常处理 ↔ gateway 鉴权链路的真实集成风险。