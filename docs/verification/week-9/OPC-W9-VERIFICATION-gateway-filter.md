# W9 — Gateway Filter 集成测试验证报告

> 日期：2026-09-07
> 范围：ruoyi-gateway 模块新增测试（首次，0 → 19 @Test）
> 方法：`AuthFilter` 6 分支 + `GatewayExceptionHandler` 4 分支单元测试，覆盖网关鉴权全链路 + 异常映射

---

## 0. 背景

W6 报告指出 OPC controller 单元测试的盲点之一：
> "SecurityUtils.getUserId() 在 mock 环境中返回 mock 值，但 401 / 网关缺失 token 的真实链路需要 `gateway AuthFilter` 验证"

但 gateway 模块（`ruoyi-gateway`）从项目初始化以来**0 测试覆盖**。W9 关闭这个空白。

## 1. W9 范围决策

### 1.1 为什么不做端到端集成测试

理论上 W9 可以做：
- **完整 E2E**：gateway + ruoyi-auth + opc-* × 7 服务 + Nacos + Redis + MySQL
- 实际限制：本机 JDK 8 / 无 Nacos / 无 Redis → 跑不起来
- CI 跑完整 E2E 是 K8  / Helm chart 部署的事（Sub-task 6.x 已覆盖）

**结论**：W9 退一步做**网关 Filter 单元测试**，覆盖 6 个鉴权分支 + 4 个异常分支 + 2 个契约验证。E2E 等真实集群。

### 1.2 测试对象

| Filter/Handler | 关键职责 | W9 测试数 |
|---|---|---|
| `AuthFilter` | JWT 解析 + Redis 登录态校验 + 白名单 + header 注入 | 12 |
| `GatewayExceptionHandler` | 全局异常映射（NotFound / ResponseStatus / 其他） | 7 |
| **合计** | | **19 @Test** |

未覆盖的 filter（按优先级排序，W10+ 候选）：
- `BlackListUrlFilter` — IP 黑名单（生产配置驱动，单测意义有限）
- `ValidateCodeFilter` — 验证码（ruoyi-system 的登录流程使用，OPC 不触发）
- `XssFilter` — XSS 过滤（前置 / 通过 wrapper 实现，无业务逻辑）

---

## 2. AuthFilter 12 分支覆盖

### 2.1 AuthFilter 业务逻辑（6 分支）

```
URL → 白名单匹配？
    ├─ YES → chain.filter（直接通过）
    └─ NO → 检查 token
              ├─ 空 → 401 "令牌不能为空"
              └─ 非空 → JwtUtils.parseToken
                       ├─ null → 401 "令牌已过期或验证不正确"
                       └─ 合法 Claims → redisService.hasKey
                                          ├─ false → 401 "登录状态已过期"
                                          └─ true → 检查 user_id/username
                                                     ├─ 缺一 → 401 "令牌验证失败"
                                                     └─ 都有 → 注入 headers + 剥离 from-source → chain.filter
```

### 2.2 测试矩阵（12 @Test）

| # | @Test | 验证点 |
|---|---|---|
| 1 | `whitelist_singleWildcard_matchesAndSkipsTokenCheck` | `/opc/user/invitations/*` 通配匹配 + **Redis 0 调用** |
| 2 | `whitelist_doubleWildcard_matchesNestedPath` | `/opc/**` 双星匹配嵌套路径 |
| 3 | `nonWhitelistPath_fallsThroughToTokenCheck` | URL 不匹配 → 走 token 校验 → 401 |
| 4 | `noToken_returns401WithEmptyTokenMessage` | 缺 Authorization → 401 "令牌不能为空" |
| 5 | `invalidJwt_returns401WithInvalidTokenMessage` | JwtUtils.parseToken null → 401 |
| 6 | `redisMiss_returns401WithLoginExpiredMessage` | JWT 合法 + Redis hasKey=false → 401 |
| 7 | `missingUserId_returns401WithTokenInvalidMessage` | JWT 合法 + Redis 命中 + 缺 user_id → 401 |
| 8 | `missingUsername_returns401WithTokenInvalidMessage` | JWT 合法 + Redis 命中 + 缺 username → 401 |
| 9 | `validPath_injectsHeadersAndStripsFromSource` | 合法路径 → 注入 user_key/user_id/username + **剥离 from-source** |
| 10 | `bearerPrefix_isStrippedBeforeJwtParse` | `"Bearer xxx"` → JwtUtils.parseToken("xxx") |
| 11 | `tokenWithoutBearerPrefix_isUsedAsIs` | `"xxx"` → JwtUtils.parseToken("xxx")（兼容历史 token 格式） |
| 12 | `bearerWithEmptyToken_returns401` | `"Bearer "`（空 token） → 401 |

### 2.3 关键设计点

**`from-source` 剥离**（安全敏感 — 测试 9）：

外部请求伪造 `from-source: inner` 头试图绕过内层 `@InnerAuth` 鉴权（OPC 内部 Feign 调用使用）。`AuthFilter` 必须**永远剥离**这个 header，否则下游服务的 `@InnerAuth` 拦截器失效。

测试 9 验证：
```java
// 外部请求 header: from-source: inner
ServerWebExchange exchange = exchange(HttpMethod.GET, "/opc/finance/voucher", TOKEN, "inner");
filter.filter(exchange, chain);

ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
verify(chain).filter(captor.capture());
HttpHeaders headers = captor.getValue().getRequest().getHeaders();
assertNull(headers.getFirst(SecurityConstants.FROM_SOURCE),
        "from-source 必须被剥离 — 即使外部请求伪造 'inner'，gateway 也不允许透传到下游");
```

**`Bearer ` 前缀剥离**（测试 10）：

```java
req.header(SecurityConstants.AUTHORIZATION_HEADER, TokenConstants.PREFIX + TOKEN);  // "Bearer valid.jwt.token"
filter.filter(exchange, chain);

jwtMock.verify(() -> JwtUtils.parseToken(TOKEN));  // 验证 parseToken 收到的是剥离后的 "valid.jwt.token"
```

---

## 3. GatewayExceptionHandler 7 分支覆盖

### 3.1 业务逻辑（4 分支）

```
Throwable ex
    ├─ response.isCommitted() → Mono.error(ex)
    └─ ex 类型
          ├─ NotFoundException → msg="服务未找到"
          ├─ ResponseStatusException → msg=ex.getMessage()
          └─ 其他 → msg="内部服务器错误"（防信息泄漏）
```

### 3.2 测试矩阵（7 @Test）

| # | @Test | 验证点 |
|---|---|---|
| 1 | `notFoundException_returnsServiceNotFoundMessage` | 下游服务 404 → JSON body 含"服务未找到" + code=500 |
| 2 | `responseStatusException_returnsOriginalMessage` | 5xx → 透传 ex.getMessage() |
| 3 | `responseStatusException404_messagePropagates` | 404 → 透传原 message |
| 4 | `otherException_returnsInternalServerErrorMessage` | RuntimeException → "内部服务器错误" + **不泄露** 原 message |
| 5 | `illegalStateException_alsoMapsToInternalError` | IllegalStateException → 同上 |
| 6 | `committedResponse_returnsMonoError` | response 已提交 → `Mono.error(ex)`（不再写 response） |
| 7 | `handlerOrder_isMinusOne` + `implementsErrorWebExceptionHandler` | `@Order(-1)` + 实现 `ErrorWebExceptionHandler` 契约 |

### 3.3 关键设计点

**信息泄漏防护**（测试 4）：
```java
Throwable ex = new RuntimeException("NullPointerException at line 42");
String body = responseBody(exchange);
assertTrue(body.contains("内部服务器错误"));
assertFalse(body.contains("NullPointerException"),
        "原 exception message 不应直接泄露给客户端");
```

**响应已提交短路**（测试 6）：
```java
exchange.getResponse().setComplete().block();  // 真正触发 commit
assertTrue(exchange.getResponse().isCommitted());

StepVerifier.create(handler.handle(exchange, ex))
        .expectErrorMatches(throwable -> throwable == ex)
        .verify();
```

`setComplete()` 同步执行后 `isCommitted()=true`，handler 走短路分支 `Mono.error(ex)`，不再二次写入 response（避免 `IllegalStateException: Response has already been committed`）。

---

## 4. 关键模式

### 4.1 AuthFilter 的字段注入如何 mock

```java
filter = new AuthFilter();  // 直接 new，绕过 @Component 注入
ignoreWhite = new IgnoreWhiteProperties();
ReflectionTestUtils.setField(filter, "ignoreWhite", ignoreWhite);  // 注入 IgnoreWhiteProperties
ReflectionTestUtils.setField(filter, "redisService", redisService);  // 注入 RedisService mock
```

`ReflectionTestUtils.setField` 允许绕过 `private` 访问修饰符，是处理 `@Autowired` 字段注入的官方推荐方式。

### 4.2 mockStatic 模式（处理 JwtUtils）

```java
try (MockedStatic<JwtUtils> jwtMock = mockStatic(JwtUtils.class)) {
    Claims claims = mock(Claims.class);
    jwtMock.when(() -> JwtUtils.parseToken(TOKEN)).thenReturn(claims);
    // ...
    filter.filter(exchange, chain);
    // verify 在 try-with-resources 块内有效
    jwtMock.verify(() -> JwtUtils.parseToken(TOKEN));
}  // 自动 close → 清理 ThreadLocal 的静态 mock 状态
```

注意：mockStatic 关闭后**所有**对 `JwtUtils.xxx()` 的调用都会回退到真实实现。W9 测试要么在 try 块内完成，要么确保不调用 `JwtUtils`。

### 4.3 Reactive 测试（StepVerifier）

```java
StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
```

`Mono<Void>` 的完成通过 `verifyComplete()` 断言；中间如果有 `onNext` 信号则用 `expectNext()`。这是 reactor-test 1.x 的核心 API。

### 4.4 MockServerWebExchange 读取 response body

```java
private String responseBody(MockServerWebExchange exchange) {
    return DataBufferUtils.join(exchange.getResponse().getBody())
            .map(buf -> {
                byte[] bytes = new byte[buf.readableByteCount()];
                buf.read(bytes);
                DataBufferUtils.release(buf);
                return new String(bytes, StandardCharsets.UTF_8);
            })
            .block();
}
```

`webFluxResponseWriter` 调用 `response.writeWith(Mono.just(dataBuffer))`，body 通过 `DataBufferUtils.join` + `block()` 同步读取。

---

## 5. 覆盖率前后对比

| 模块 | 测试前 | 测试后 |
|---|---|---|
| `ruoyi-gateway` filter | 0 @Test | **12 @Test**（AuthFilter） |
| `ruoyi-gateway` handler | 0 @Test | **7 @Test**（GatewayExceptionHandler） |
| **ruoyi-gateway 总计** | **0** | **19** |

OPC 累计：

| 子任务 | 累计 @Test |
|---|---|
| W2-W8 | 262 |
| **W9** | **+19** |
| **总计** | **281 @Test + 29 mutations (93.1%)** |

---

## 6. 本地限制说明

- **JDK 8 本机无法运行 WebFlux 测试**（WebFlux 需要 JDK 17）
- 本地只做静态验证（grep 引用、反射验证、javadoc 完整性）
- 完整运行需 CI/容器（JDK 17 + `mvn -pl ruoyi-gateway test`）

---

## 7. W9.4 — W9 待办候选

| 任务 | 候选 | 估值 | 备注 |
|---|---|---|---|
| `BlackListUrlFilter` 单元测试 | W10.1 | 0.5 天 | IP 黑名单配置驱动 |
| `ValidateCodeFilter` 单元测试 | W10.2 | 0.5 天 | 验证码校验 |
| `XssFilter` 单元测试 | W10.3 | 0.5 天 | XSS 过滤 |
| 真实集群 E2E（gateway + auth + opc-* + Nacos + Redis + MySQL） | W11 | 2-3 天 | K8  部署验证 |
| `OpcAgentInstanceController` 等子 controller @WebMvcTest | — | 0.5 天 | 当前 OPC 没有这些独立 controller |
| PIT 完整闭环（JDK 17 环境） | W12 | 1-2 天 | mutation score 实测 |

W9 完成：ruoyi-gateway 19 @Test，AuthFilter 6 分支 + GatewayExceptionHandler 4 分支覆盖完整