# W3 — OpcUserProfileService 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-user-center/`
> 静态验证（本机 JDK 8，沿用 W2.x 模式）

---

## 1. 交付物

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/.../test/.../service/impl/OpcUserProfileServiceImplTest.java` | 新增单测 | 378 |
| `OPC-W3-VERIFICATION-user-profile-service.md` | 本报告 | — |

**无新增 pom 依赖** — `opc-user-center` 在 W2.2 (commit 02609b3) 已加 `spring-boot-starter-test`。

---

## 2. 方法覆盖矩阵

| # | 方法 | 类型 | 测试方法 | 状态 |
|---|---|---|---|---|
| 1 | `getByUserId` | passthrough | `getByUserId_returnsProfile` | ✅ |
| 2 | `getByUserId` | null 透传 | `getByUserId_returnsNull` | ✅ |
| 3 | `getByInvitationCode` | passthrough | `getByInvitationCode_returnsProfile` | ✅ |
| 4 | `getByInvitationCode` | null 透传 | `getByInvitationCode_returnsNull` | ✅ |
| 5 | `createOrUpdate` | 新用户 insert 路径 | `createOrUpdate_newProfile_inserts` | ✅ |
| 6 | `createOrUpdate` | 新用户但 invitationCode 已提供 | `createOrUpdate_newProfile_keepsProvidedCode` | ✅ |
| 7 | `createOrUpdate` | 已存在用户 update 路径 | `createOrUpdate_existingProfile_updates` | ✅ |
| 8 | `createOrUpdate` | userId=null 抛 | `createOrUpdate_userIdNull_throws` | ✅ |
| 9 | `update` | passthrough | `update_returnsRows` | ✅ |
| 10 | `listCompaniesByOwner` | 透传 List | `listCompaniesByOwner_returnsList` | ✅ |
| 11 | `listCompaniesByOwner` | 空 list | `listCompaniesByOwner_emptyList` | ✅ |
| 12 | `createCompany` | 全字段自动初始化 | `createCompany_autoFillsFields` | ✅ |
| 13 | `createCompany` | companyCode 已提供不覆盖 | `createCompany_keepsProvidedCompanyCode` | ✅ |
| 14 | `createCompany` | scale 已提供不覆盖 | `createCompany_keepsProvidedScale` | ✅ |
| 15 | `createCompany` | ownerUserId=null 抛 | `createCompany_ownerUserIdNull_throws` | ✅ |
| 16 | `updateCompany` | passthrough | `updateCompany_returnsRows` | ✅ |
| 17 | `getCompany` | passthrough | `getCompany_returnsCompany` | ✅ |
| 18 | `getCompany` | null 透传 | `getCompany_returnsNull` | ✅ |

**总计：18 个 @Test，8 个 public 方法 100% 覆盖。**

---

## 3. 关键 Mockito 模式

### 3.1 `@RequiredArgsConstructor` + 2 个 `@Mock` → constructor injection

`OpcUserProfileServiceImpl` 用 Lombok `@RequiredArgsConstructor` 注入两个 final mapper：

```java
@Service
@RequiredArgsConstructor
public class OpcUserProfileServiceImpl implements IOpcUserProfileService {
    private final OpcUserProfileMapper userMapper;
    private final OpcCompanyProfileMapper companyMapper;
```

与 W2.2 InvitationServiceImpl 不同（那里混用 `@RequiredArgsConstructor` + `@Autowired`，需要
`ReflectionTestUtils.setField`），本 service **纯构造器注入**，`@InjectMocks` 自动选 constructor
injection，无需额外手动注入。

### 3.2 `thenAnswer + AtomicLong` 模拟 useGeneratedKeys

`createOrUpdate` 的新用户路径 和 `createCompany` 都依赖 MyBatis `useGeneratedKeys="true"
keyProperty="id"` 把生成的主键回写到对象。本机静态测试无 MySQL，需要 mock 这个副作用：

```java
private static final AtomicLong NEXT_USER_ID = new AtomicLong(100L);
private static final AtomicLong NEXT_COMPANY_ID = new AtomicLong(500L);

when(userMapper.insert(any(OpcUserProfile.class))).thenAnswer(inv -> {
    OpcUserProfile arg = inv.getArgument(0);
    arg.setId(NEXT_USER_ID.getAndIncrement());
    return 1;
});
```

模式与 W2.1 TaxReport + W2.3 Voucher 完全一致。两个 service 共用 `AtomicLong`，各取各的命名空间，
避免 ID 冲突。

### 3.3 ArgumentCaptor 验证字段初始化

`createOrUpdate_newProfile_inserts` 用 `ArgumentCaptor<OpcUserProfile>` 抓取实际 insert 的对象，
验证 4 个字段：

```java
ArgumentCaptor<OpcUserProfile> captor = ArgumentCaptor.forClass(OpcUserProfile.class);
verify(userMapper).insert(captor.capture());
OpcUserProfile inserted = captor.getValue();
assertNotNull(inserted.getInvitationCode(), "invitationCode 应自动生成");
assertEquals(8, inserted.getInvitationCode().length(), "邀请码长度应为 8");
assertEquals("ACTIVE", inserted.getStatus(), "新用户 status 应为 ACTIVE");
assertEquals(0, inserted.getVerified(), "新用户 verified 应为 0");
assertEquals(String.valueOf(USER_ID), inserted.getCreateBy(),
        "createBy 应为当前 userId（无登录态时降级）");
```

钉死 controller 的「新用户字段初始化」语义，防止未来重构改默认状态或丢失 createBy。

### 3.4 状态机分支：insert vs update

`createOrUpdate` 是典型的「if exists update else insert」模式：

| userMapper.selectByUserId 返回 | 路径 | 验证 |
|---|---|---|
| null | insert（设置 status/verified/invitationCode/createBy） | `createOrUpdate_newProfile_inserts` + `..._keepsProvidedCode` |
| non-null | update（保留 exist.id + 设置 updateBy） | `createOrUpdate_existingProfile_updates` |

`createOrUpdate_newProfile_inserts` 用 `verify(userMapper, never()).update(any())` 反向断言
**insert 路径绝对不能误调 update**。`createOrUpdate_existingProfile_updates` 反之亦然。

### 3.5 异常透传（OpcException 不被 service 吞）

```java
@Test
void createOrUpdate_userIdNull_throws() {
    OpcUserProfile p = new OpcUserProfile();
    p.setUserId(null);

    OpcException ex = assertThrows(OpcException.class,
            () -> service.createOrUpdate(p));
    assertTrue(ex.getMessage().contains("userId"));
    verify(userMapper, never()).selectByUserId(any());
    verify(userMapper, never()).insert(any());
    verify(userMapper, never()).update(any());
}
```

Service 抛 `OpcException`（业务校验失败）→ 透传给调用方，**不调任何 mapper**（防止部分写入）。

### 3.6 createCompany 的双重覆盖保护

`createCompany` 有两个 `setX(company.getX() == null ? DEFAULT : company.getX())` 三目表达式：

| 字段 | 默认值 | 已提供时 | 测试方法 |
|---|---|---|---|
| `companyCode` | `"C" + currentTimeMillis + random` | 保留 | `createCompany_autoFillsFields` + `createCompany_keepsProvidedCompanyCode` |
| `scale` | `"SMALL"` | 保留 | `createCompany_autoFillsFields` + `createCompany_keepsProvidedScale` |

`autoFillsFields` 测试钉死默认值，`..._keepsProvidedX` 测试钉死「已提供时不覆盖」。两个测试组合
覆盖三目表达式的两个分支。

---

## 4. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| `getByUserId`/`getByInvitationCode`/`getCompany` passthrough（返回 mapper 返回的对象） | 4 例 |
| passthrough 方法返回 null 时不抛 | 4 例 |
| 新用户 `invitationCode.length() == 8` | `createOrUpdate_newProfile_inserts` |
| 新用户 `status == "ACTIVE"` | `createOrUpdate_newProfile_inserts` |
| 新用户 `verified == 0` | `createOrUpdate_newProfile_inserts` |
| 新用户 `createBy == String.valueOf(userId)` | `createOrUpdate_newProfile_inserts` |
| 已存在用户 `id == exist.id`（不创建新记录） | `createOrUpdate_existingProfile_updates` |
| 已存在用户 `updateBy == String.valueOf(userId)` | `createOrUpdate_existingProfile_updates` |
| insert 路径不调 update / update 路径不调 insert | 2 例 `never()` |
| companyCode 默认以 `C` 开头 | `createCompany_autoFillsFields` |
| 已提供 companyCode/scale 不覆盖 | 2 例 |
| scale=null → `"SMALL"`，scale=MEDIUM → `"MEDIUM"` | 2 例 |
| `userId==null` 抛 OpcException，不调 mapper | `createOrUpdate_userIdNull_throws` |
| `ownerUserId==null` 抛 OpcException，不调 mapper | `createCompany_ownerUserIdNull_throws` |
| `update` / `updateCompany` 透传 rows | 2 例 |

---

## 5. 与 W2.x 的对照

| 维度 | W2.1 TaxReport | W2.2 Invitation | W2.3 Voucher | W2.4 BankFlow | **W3 Profile** |
|---|---|---|---|---|---|
| @Test 数 | 8 | 23 | 21 | 15 | **18** |
| 依赖 mapper 数 | 1 | 1 | 1 | 1 | **2** |
| 注入方式 | `@Autowired` → `ReflectionTestUtils.setField` | 混合 → `ReflectionTestUtils.setField` | `@Autowired` → `ReflectionTestUtils.setField` | `@Autowired` → `ReflectionTestUtils.setField` | **`@RequiredArgsConstructor` → 自动 constructor injection** |
| useGeneratedKeys mock | ✅ | — | ✅ | — | **✅** |
| 状态机分支 | — | INSERT/UPDATE 双路 | DRAFT/REVIEW/POSTED 三态 | — | **INSERT/UPDATE 双路（user + company）** |
| 字段初始化自动填充 | — | — | — | `flowCode` 生成 | **invitationCode + companyCode + scale/status/verified/createBy** |
| 异常路径 | 4 例 | 4 例 | 3 例 | 2 例 | **2 例** |

**架构改进**：W3 是 W2.x 以来第一个**纯 `@RequiredArgsConstructor` 注入**的 service，省去了
`ReflectionTestUtils.setField` 的样板代码。新代码应优先采用此模式。

---

## 6. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` | 静态分析 |
| `OpcCodeGenerator.inviteCode()` 在测试中是真实调用 — 验证 `length()==8` 但不验证字符集分布 | 集成测试可加字符分布断言 |
| `createCompany` 的 `companyCode` 生成依赖 `System.currentTimeMillis()` — 单测无法断言唯一性 | 生产靠 `UNIQUE KEY` 兜底（已知 `opc_invitation` 有 5-retry 防冲突，公司档案应有类似机制待验证） |
| `createOrUpdate` 的「事务边界」无法在 unit test 验证 — `@Transactional(rollbackFor = Exception.class)` 是注解 | 需要 Spring 集成测试或 AOP 测试 |
| `verified`/`status` 字段是 Integer/String，未来若加状态机（DRAFT→PENDING→VERIFIED→ACTIVE）需要扩展测试 | 当前 2 值（0/1 + ACTIVE），状态机简单 |
| `OpcCompanyProfile.scale` 默认 `SMALL` 是写死的 — 未来多枚举（SMALL/MEDIUM/LARGE/ENTERPRISE）需要枚举常量测试 | 当前隐式约定 |
| 没有专门测试 `OpcCodeGenerator.inviteCode()` 的字符集与冲突概率 | 应单独写 `OpcCodeGeneratorTest`（独立工具类） |

---

## 7. 验收 Checkpoint

- [x] 8 个 public 方法 100% 覆盖（4 个 user + 4 个 company）
- [x] 18 个 @Test，全部 `@DisplayName` 描述场景
- [x] `@MockitoSettings(strictness = LENIENT)` 与 W2.x 一致
- [x] 纯 `@RequiredArgsConstructor` 注入，`@InjectMocks` 自动 constructor injection
- [x] `thenAnswer + AtomicLong` 模拟 MyBatis useGeneratedKeys（user + company 各一个）
- [x] `ArgumentCaptor` 验证字段初始化（5 字段：invitationCode/status/verified/createBy + companyCode/scale/status/verified/createBy）
- [x] 状态机分支：insert vs update 用 `verify(..., never())` 反向断言
- [x] 三目表达式两个分支：默认值 vs 已提供值 各覆盖
- [x] 异常透传测试（OpcException 不被 service 吞，失败时 mapper 不被调）
- [x] pom.xml 无需变更

---

## 8. W3 累计 + 后续候选

| W 子任务 | 模块 | 状态 | @Test 数 |
|---|---|---|---|
| W2.1 | TaxReport + Wallet | ✅ | 18 |
| W2.2 | Invitation | ✅ | 23 |
| W2.3 | Voucher | ✅ | 21 |
| W2.4 | BankFlow | ✅ | 15 |
| W2.5 | FinanceController | ✅ | 20 |
| W2.6 | BillingController | ✅ | 13 |
| W2.7 | InvitationController | ✅ | 14 |
| **W3** | **UserProfileService** | **✅** | **18** |
| **总计** | **3 模块** | **W2+W3 完成** | **142 @Test, 12 commit** |

| 任务 | 候选 | 估值 | 备注 |
|---|---|---|---|
| `OpcCodeGeneratorTest`（字符集 + 冲突概率） | W3.1 | 30min | 独立工具类，跨 4 service 复用 |
| `OpcFinanceTaxReportController` 单测（3 endpoint） | W3.2 | 45min | 收尾 W2 漏网 |
| `OpcUserProfileController` 单测（如有） | W3.3 | 1h | 与本 service 配对的 controller |
| `@WebMvcTest` 集成测试（4 endpoint） | W4 | 2 天 | 覆盖 HTTP status / JSON 序列化 / 401 鉴权 |
| `OpcWorkflowTriggerController` 单测（cross-module Feign） | W4 | 1.5h | W1 Task #3 链路 |
| Mutation Testing（PIT / Stryker） | W4 | 1 天 | 验证单测断言强度 |

W3 完成：补齐 opc-user-center 最后一个核心 service 的单测覆盖，且通过纯构造器注入省去了
`ReflectionTestUtils` 样板。W4 建议优先做 `@WebMvcTest` 集成测试 — 当前 142 个单测覆盖
业务逻辑层饱和，集成测试才能暴露 controller ↔ mapper ↔ 数据库的真实集成风险。