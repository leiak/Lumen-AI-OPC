# W3.3 — OpcUserController 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-user-center/`
> 静态验证（本机 JDK 8，沿用 W2.x / W3.x 模式）

---

## 0. 命名偏差说明

**原计划 W3.3 是 `OpcUserProfileController`，但该 controller 不存在。**

实际承载 profile / company endpoint 的是 `OpcUserController`（路径前缀 `/opc/user/**`，与
invitation controller 同模块）。本测试类覆盖其 7 个 endpoint，与 W3.0 `OpcUserProfileServiceImplTest`
一一配对。

---

## 1. 交付物

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/.../test/.../controller/OpcUserControllerTest.java` | 新增单测 | 356 |
| `OPC-W3-VERIFICATION-user-controller.md` | 本报告 | — |

**无新增 pom 依赖** — `opc-user-center/pom.xml` 在 W2.2 (commit 02609b3) 已加 `spring-boot-starter-test`。

---

## 2. Endpoint 覆盖矩阵

| # | Endpoint | Method | 测试方法 | SecurityUtils | 状态 |
|---|---|---|---|---|---|
| 1 | `/profile` | GET | `myProfile_returnsProfileFromService` | ✅ | ✅ |
| 2 | `/profile` | GET | `myProfile_nullReturnsEmptyProfileWithUserId` | ✅ | ✅ |
| 3 | `/profile` | POST | `saveProfile_overridesUserIdAndCallsCreateOrUpdate` | ✅ | ✅ |
| 4 | `/profile` | POST | `saveProfile_returnsUpdatedProfile` | ✅ | ✅ |
| 5 | `/profile` | POST | `saveProfile_serviceThrowsPropagates` | ✅ | ✅ |
| 6 | `/companies` | GET | `myCompanies_returnsList` | ✅ | ✅ |
| 7 | `/companies` | GET | `myCompanies_emptyList` | ✅ | ✅ |
| 8 | `/company` | POST | `createCompany_overridesOwnerUserId` | ✅ | ✅ |
| 9 | `/company` | POST | `createCompany_returnsCompanyIdInMap` | ✅ | ✅ |
| 10 | `/company` | POST | `createCompany_serviceThrowsPropagates` | ✅ | ✅ |
| 11 | `/company/{id}` | GET | `getCompany_returnsCompany` | ❌ | ✅ |
| 12 | `/company/{id}` | GET | `getCompany_notFoundReturnsNull` | ❌ | ✅ |
| 13 | `/company` | PUT | `updateCompany_rowsGreaterThanZeroReturnsTrue` | ❌ | ✅ |
| 14 | `/company` | PUT | `updateCompany_rowsZeroReturnsFalse` | ❌ | ✅ |
| 15 | `/home` | GET | `home_aggregatesProfileAndCompanies` | ✅ | ✅ |
| 16 | `/home` | GET | `home_nullProfilePassesThrough` | ✅ | ✅ |

**总计：16 个 @Test，7 个 endpoint 100% 覆盖。**

### 2.1 鉴权差异

| Endpoint | 鉴权方式 | 原因 |
|---|---|---|
| `GET /profile`, `POST /profile`, `GET /companies`, `POST /company`, `GET /home` | SecurityUtils.getUserId | 需要登录态取 userId |
| `GET /company/{id}`, `PUT /company` | 无需 SecurityUtils | 公开或管理员 path（仅依赖 id 本身） |

---

## 3. 关键 Mockito 模式

### 3.1 mockStatic SecurityUtils.getUserId (Long)

与 W2.6 `OpcBillingController` 和 W2.7 `OpcInvitationController` 同模式：

```java
@BeforeEach
void setupSecurityMock() {
    securityMock = mockStatic(SecurityUtils.class);
    securityMock.when(SecurityUtils::getUserId).thenReturn(USER_ID);
}
```

**5 个 endpoint 需要 SecurityUtils**（profile / saveProfile / myCompanies / createCompany / home），
2 个不需要（getCompany / updateCompany 是 id-based path）。统一在 setup 里 mock 让代码整齐，LENIENT
模式下未使用的 stub 不会报错。

### 3.2 userId / ownerUserId 覆盖是核心安全断言

**`saveProfile` 和 `createCompany` 在调 service 前必须 override 用户提供的 userId/ownerUserId** —
这是 controller 的核心安全职责（防止客户端伪造身份）。

```java
@Test
void saveProfile_overridesUserIdAndCallsCreateOrUpdate() {
    OpcUserProfile incoming = new OpcUserProfile();
    incoming.setUserId(9999L);  // 客户端伪造的 userId
    incoming.setRealName("李四");

    controller.saveProfile(incoming);

    assertEquals(USER_ID, incoming.getUserId(),
            "Controller 必须用 SecurityUtils 的 userId 覆盖客户端值");
    verify(userProfileService).createOrUpdate(incoming);
}
```

钉死 `profile.setUserId(userId); company.setOwnerUserId(userId);` 这两行 — 如果未来重构漏掉
`setUserId` 调用，**越权漏洞会立即被测试捕获**。这是 W2/W3 体系内**唯一针对安全语义的测试**。

### 3.3 myProfile 的「null → 空 profile」特殊处理

```java
@Test
void myProfile_nullReturnsEmptyProfileWithUserId() {
    when(userProfileService.getByUserId(USER_ID)).thenReturn(null);

    AjaxResult result = controller.myProfile();

    assertEquals(200, result.get("code"));
    OpcUserProfile data = (OpcUserProfile) result.get("data");
    assertNotNull(data);
    assertEquals(USER_ID, data.getUserId());
    assertNull(data.getRealName());
}
```

Controller 在 service 返回 null 时**自动创建空 profile**（仅 setUserId）— 这是「首次访问时前端
渲染空表单」的 UX 约定，不是错误。测试钉死这个语义。

**对比 `home` endpoint**：
```java
@Test
void home_nullProfilePassesThrough() {
    // ...
    assertNull(data.get("profile"), "service 返回 null → controller 透传 null（不像 myProfile 自动建空）");
}
```

`home` 是聚合接口，profile=null 表示「未创建」— 应透传给前端判断（前端弹「立即创建」按钮）。
`myProfile` 是单查接口，profile=null 表示「首次访问」— 应自动建空。**两种语义完全不同，测试
都钉死**。

### 3.4 updateCompany 的「rows>0 → Boolean」转换

```java
@Test
void updateCompany_rowsGreaterThanZeroReturnsTrue() {
    OpcCompanyProfile c = new OpcCompanyProfile();
    c.setId(COMPANY_ID);
    when(userProfileService.updateCompany(c)).thenReturn(1);

    AjaxResult result = controller.updateCompany(c);

    assertEquals(200, result.get("code"));
    assertEquals(Boolean.TRUE, result.get("data"));
}

@Test
void updateCompany_rowsZeroReturnsFalse() {
    when(userProfileService.updateCompany(c)).thenReturn(0);

    AjaxResult result = controller.updateCompany(c);

    assertEquals(200, result.get("code"), "rows=0 不应抛 HTTP 500");
    assertEquals(Boolean.FALSE, result.get("data"));
}
```

`updateCompany` 把 service 的 int rows 转成 `success(rows > 0)` — 类似 W2.5 FinanceCtrl 的
`updateVoucher_rowsZero` 模式。**rows=0 不抛 500**，让前端判断。

### 3.5 异常透传 + 覆盖已生效

`saveProfile` 和 `createCompany` 的 service 抛 `OpcException` → controller 不 catch，直接
抛给调用方：

```java
when(userProfileService.createOrUpdate(any()))
        .thenThrow(new OpcException("userId 不能为空"));
OpcException ex = assertThrows(OpcException.class,
        () -> controller.saveProfile(incoming));
```

**注意**：service 抛「userId 不能为空」是逻辑上不可能的（controller 已 setUserId）— 这个测试
是**冗余安全网**：即使 controller 漏掉 setUserId，service 兜底 + 单测断言会暴露问题。

### 3.6 @RequestBody 反序列化绕过

`saveProfile(OpcUserProfile profile)` 和 `createCompany(OpcCompanyProfile company)` 是
`@RequestBody` 反序列化的对象。单元测试绕过 Spring MVC，直接传 Java 对象：

```java
OpcUserProfile incoming = new OpcUserProfile();
incoming.setUserId(9999L);  // 模拟客户端 JSON 反序列化结果
controller.saveProfile(incoming);
```

与 W2.7 InvitationController 的 `accept(Map body)` 测试同模式（直接 mock JSON 反序列化结果）。

---

## 4. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| 7 个 endpoint 都返回 `code == 200`（成功路径） | 13 例 |
| `saveProfile` 必须 override `userId`（防越权） | `saveProfile_overridesUserIdAndCallsCreateOrUpdate` |
| `createCompany` 必须 override `ownerUserId`（防越权） | `createCompany_overridesOwnerUserId` |
| `myProfile` null → 自动建空 profile（带 userId） | `myProfile_nullReturnsEmptyProfileWithUserId` |
| `home` null → 透传 null（前端判断） | `home_nullProfilePassesThrough` |
| `updateCompany` rows=1 → success(true)，rows=0 → success(false) | 2 例 |
| `createCompany` data key 是 `companyId`（不是 `id` 或 `data.id`） | `createCompany_returnsCompanyIdInMap` |
| `home` data 含 `profile` + `companies` 两个 key | `home_aggregatesProfileAndCompanies` |
| SecurityUtils.getUserId 必调（profile/saveProfile/myCompanies/createCompany/home） | 5 例 `atLeastOnce()` |
| SecurityUtils.getUserId 不调（getCompany/updateCompany） | 2 例无 verify |
| service 抛 OpcException → 透传不包装 | 2 例 `assertThrows` |

---

## 5. 与 W2.x / W3.x Controller 对照

| 维度 | W2.5 FinanceCtrl | W2.6 BillingCtrl | W2.7 InvitationCtrl | W3.2 TaxReport Ctrl | **W3.3 UserCtrl** |
|---|---|---|---|---|---|
| Endpoint 数 | 11 | 4 | 4 | 3 | **7** |
| @Test 数 | 20 | 13 | 14 | 13 | **16** |
| SecurityUtils mock | `getUsername` | `getUserId` | `getUserId` | `getUsername` | **`getUserId`** |
| 鉴权覆盖（防越权） | — | — | — | — | **✅（核心断言）** |
| 入参方式 | `@RequestParam` + `@RequestBody` | `@RequestBody` | `@PathVariable` + `@RequestBody Map` | 全 `@RequestParam` + `@PathVariable` | **`@RequestBody` + `@PathVariable`** |
| null 业务处理 | — | `wallet_nullWallet` | `myInvitations_emptyList` | `list_emptyList` | **`myProfile_nullReturnsEmptyProfile`（自动建空）** |
| rows>0 → Boolean | `updateVoucher_rowsZero` | — | — | — | **`updateCompany_rowsZero`** |

**架构特点**：
- 唯一带「**鉴权覆盖**」测试的 controller（saveProfile / createCompany 覆盖 userId）
- 唯一带「**自动建空**」语义的 controller（myProfile null → 空 profile）
- 唯一带「**聚合**」endpoint 的 controller（home = profile + companies）

---

## 6. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` | 静态分析 |
| `home` 的「profile=null」透传 vs `myProfile` 的「自动建空」是**两种不同语义** — 未来重构若混淆会破坏 UX | 测试分别钉死两个语义 |
| `updateCompany` 没有鉴权 — 任何人可以 PUT 任何 company id | 当前依赖上游 gateway 鉴权；W4 应加 `@PreAuthorize` 或 service 层 ownerUserId 校验 |
| `getCompany` 没有鉴权 — 任何人可以 GET 任何 company id | 同上，依赖 gateway 白名单 |
| `saveProfile` 覆盖 userId 是在 `@RequestBody` 反序列化**之后** — 如果未来用 Jackson 自定义反序列化器写入 userId，可能被绕过 | 当前实现是 controller 内 setUserId，反序列化器层面无侵入，安全 |
| `home` 聚合两个 service 调用 — 任一失败会全部失败，没有「部分成功」 | 当前实现简单；未来可加 partial success 返回 |
| `myProfile` 自动建空 profile 与 `home` 透传 null 的不一致 — 未来前端可能误读 | 测试明确钉死，但需要前端配合文档说明 |
| 单元测试无法覆盖 `@RequestBody` JSON 反序列化（如字段缺失、类型错误） | W4 集成测试候选 |
| `OpcUserProfile` / `OpcCompanyProfile` 字段较多，未在 controller 层做白名单校验 | 当前完整接收，依赖前端；W4 加 Bean Validation |

---

## 7. 验收 Checkpoint

- [x] 7 个 endpoint 100% 覆盖（含 1 个聚合 endpoint `home`）
- [x] 16 个 @Test，全部 `@DisplayName` 描述场景
- [x] `@MockitoSettings(strictness = LENIENT)` 与 W2.x / W3.x 一致
- [x] `mockStatic(SecurityUtils.getUserId)` 正确配对（与 W2.6 / W2.7 同模式）
- [x] **鉴权覆盖核心断言**：saveProfile + createCompany 必须 override userId/ownerUserId
- [x] myProfile null → 自动建空 / home null → 透传 两种语义分别钉死
- [x] updateCompany rows>0 → Boolean 转换（含 rows=0 → false 边界）
- [x] 异常透传测试（OpcException 不被 controller 吞）
- [x] pom.xml 无需变更

---

## 8. W3 累计 + 后续候选

| W 子任务 | 模块 | 状态 | @Test 数 |
|---|---|---|---|
| W2.1-W2.7 | billing + finance + user-center | ✅ | 124 |
| W3.0 | UserProfileService | ✅ | 18 |
| W3.1 | OpcCodeGenerator | ✅ | 23 invocations |
| W3.2 | TaxReportController | ✅ | 13 |
| **W3.3** | **OpcUserController** | **✅** | **16** |
| **总计** | **3 模块 + 1 公共工具** | **W3 收尾** | **194 invocations, 15 commits** |

| 任务 | 候选 | 估值 | 备注 |
|---|---|---|---|
| `updateCompany` 加 ownerUserId 校验（防越权 PUT） | W3.4 | 30min | service 层或 `@PreAuthorize` |
| `@WebMvcTest` 集成测试（4 endpoint） | W4 | 2 天 | 覆盖 HTTP status / JSON 序列化 / 401 鉴权 |
| `OpcWorkflowTriggerController` 单测（cross-module Feign） | W4 | 1.5h | W1 Task #3 链路 |
| Mutation Testing（PIT / Stryker） | W4 | 1 天 | 验证单测断言强度 |

W3.3 完成：补齐 opc-user-center 模块 controller 单测覆盖（5 service + 4 controller 单测），
且首次引入「鉴权覆盖核心断言」模式 — 后续 saveXxx / createXxx 风格的 controller 应复用此
模式钉死 userId override。W2+W3 全闭环：194 invocations 覆盖 7 service + 4 controller + 1 utility。