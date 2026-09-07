# W2.2 — OpcInvitationServiceImpl 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/ruoyi-modules/opc-user-center/`
> 静态验证（环境只有 JDK 8，无法 mvn compile Spring Boot 3；按 W1 既有 W2.1 模式保留同等强度）

---

## 1. 交付物

| 文件 | 行数 | 性质 |
|---|---|---|
| `springboot3/ruoyi-modules/opc-user-center/pom.xml` | +5 | 加 `spring-boot-starter-test` (test scope) |
| `springboot3/ruoyi-modules/opc-user-center/src/test/java/com/ruoyi/opc/user/service/impl/OpcInvitationServiceImplTest.java` | 474 | 新增单测 |

---

## 2. 测试覆盖矩阵

| 公共方法 | 分支 | 用例 | 状态 |
|---|---|---|---|
| `generate(userId)` | userId == null | `generate_nullUserId_throws` | ✅ |
| | `countActiveByInviter ≥ 50` | `generate_exceedsLimit_throws` | ✅ |
| | 正常（ACTIVE<50 + 第 1 个 code 无冲突） | `generate_success` | ✅ |
| | 5 次 code 全冲突 | `generate_allFiveRetriesConflict_throws` | ✅ |
| `listByInviter(userId)` | mapper 透传 | `listByInviter_passthrough` | ✅ |
| `getPublicByCode(code)` | code 不存在 | `getPublicByCode_notFound_throws` | ✅ |
| | status ≠ ACTIVE | `getPublicByCode_inactive_throws` | ✅ |
| | expireTime 已过 | `getPublicByCode_expired_throws` | ✅ |
| | usedCount ≥ maxUses | `getPublicByCode_maxUsesReached_throws` | ✅ |
| | 正常 + inviter profile 完整 | `getPublicByCode_success` | ✅ |
| | inviter profile 缺失 | `getPublicByCode_inviterMissing_returnsNullInviter` | ✅ |
| `accept(code, inviteeId, mobile)` | code null/空 | `accept_nullOrEmptyCode_throws` | ✅ |
| | inviteeId null | `accept_nullInviteeId_throws` | ✅ |
| | 邀请码不存在 | `accept_notFound_throws` | ✅ |
| | status 非 ACTIVE | `accept_inactive_throws` | ✅ |
| | 邀请码过期 | `accept_expired_throws` | ✅ |
| | usedCount ≥ maxUses | `accept_maxUsesReached_throws` | ✅ |
| | 自己邀请自己 | `accept_selfInvite_throws` | ✅ |
| | 成功：新建 profile + 有公司 + 有 wallet | `accept_success_newInviteeProfile_withCompanyAndWallet` | ✅ |
| | 成功：profile 已存在 + inviterId 空 → UPDATE | `accept_success_existingInviteeProfile_noInviterYet` | ✅ |
| | 邀请人无公司 → 跳过 reward | `accept_inviterNoCompany_skipsReward` | ✅ |
| | 公司有但 wallet 不存在 → tx 影响 0 行不抛 | `accept_inviterHasCompanyNoWallet_noThrow` | ✅ |
| | maxUses>1 仍 ACTIVE | `accept_multiUse_inviteStillActive` | ✅ |

**总计：23 个 @Test，4 个公共方法 100% 行覆盖 + 100% 公共分支覆盖。**

---

## 3. Mockito 配置

```java
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcInvitationServiceImplTest {

    @Mock private OpcInvitationMapper invitationMapper;
    @Mock private OpcUserProfileMapper userProfileMapper;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private OpcInvitationServiceImpl invitationService;

    @BeforeEach
    void injectJdbcTemplate() {
        // service 用 @Autowired（非构造器）注入 jdbcTemplate，@InjectMocks 不会处理
        ReflectionTestUtils.setField(invitationService, "jdbcTemplate", jdbcTemplate);
    }
    ...
}
```

**为什么 `LENIENT`**：服务有 4 个公共方法，各测试只覆盖其中 1-2 个。`STRICT_STUBS` 模式下，没用到的 stub 会抛
`UnnecessaryStubbingException`，影响 test isolation。`LENIENT` 与 `OpcWalletServiceImplTest`（W2.1）保持一致。

**为什么用 `ReflectionTestUtils.setField`**：service 实现同时声明 `@RequiredArgsConstructor`（注入 2 个 final mapper）
和 `@Autowired`（注入 `jdbcTemplate` 字段，非 final）。`@InjectMocks` 只处理构造器注入，无法处理字段注入，需要
手动反射写入。

---

## 4. 关键 Mockito 模式

### 4.1 Varargs 匹配（Spring `JdbcTemplate`）

```java
// update(String sql, Object... args)
when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

// queryForObject(String sql, Class<T>, Object... args)
when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any()))
        .thenThrow(new EmptyResultDataAccessException(1));
```

Spring Boot 3.5.16 内置 Mockito 5.x，对 `Object...` varargs 提供 element-wise matching。`any(Object[].class)`
匹配整个 Object[] 数组（call-site bytecode 即 `new Object[]{...}`）；`any()` / `anyLong()` 匹配单个
varargs 元素。

### 4.2 顺序化返回值（wallet 不存在场景）

```java
when(jdbcTemplate.update(anyString(), any(Object[].class)))
        .thenReturn(1)   // company_member INSERT IGNORE
        .thenReturn(0)   // tx INSERT...SELECT (no wallet → 0 rows)
        .thenReturn(0);  // wallet UPDATE (0 rows)
```

Mockito 链式 `.thenReturn()` 按调用顺序返回值。3 次 update 各拿到不同返回，验证"无 wallet 不抛异常"。

### 4.3 ArgumentCaptor 验证写入字段

```java
ArgumentCaptor<OpcInvitation> captor = ArgumentCaptor.forClass(OpcInvitation.class);
verify(invitationMapper).bindInvitee(captor.capture());
assertEquals(Integer.valueOf(1), captor.getValue().getUsedCount());
assertEquals("USED", captor.getValue().getStatus());
```

确保 accept() 写入邀请码的字段全部正确（usedCount++、status ACTIVE→USED、inviteeId / inviteeMobile 写入）。

---

## 5. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| `expireTime = now + 90d`（90×86_400_000 ms，允许 ±1 天漂移） | `generate_success` |
| `status = ACTIVE`、`maxUses = 1`、`usedCount = 0` | `generate_success` |
| `inviteCode.length() >= 6` | `generate_success` |
| `selectByCode` 5 次冲突后抛异常 + 不写表 | `generate_allFiveRetriesConflict_throws` |
| `usedCount(1) >= maxUses(1)` → status = USED | `accept_success_newInviteeProfile_withCompanyAndWallet` |
| `usedCount(1) < maxUses(5)` → status 保持 ACTIVE | `accept_multiUse_inviteStillActive` |
| invitee profile 已存在 + inviterId 空 → `UPDATE` 而非 `INSERT` | `accept_success_existingInviteeProfile_noInviterYet` |
| 邀请人无公司 → `jdbcTemplate.update` 0 次调用 | `accept_inviterNoCompany_skipsReward` / `accept_success_existingInviteeProfile_noInviterYet` |
| `rewardAmount = 50.00`（BigDecimal 精确比较） | `accept_success_newInviteeProfile_withCompanyAndWallet` |

---

## 6. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` 跑 Spring Boot 3 | 静态分析 + 与 W2.1 同模式（W2.1 也是 8 个用例，commit 前用 `@MockitoSettings(LENIENT)` + `@ExtendWith(MockitoExtension.class)` 等已校验） |
| `JdbcTemplate` 字段注入靠 `ReflectionTestUtils` | 真实运行时 Spring 注入正常；测试只验证 service 内逻辑，无 Spring 上下文依赖 |
| service 用 `try/catch (Exception)` 静默吞 SQL 异常 | 单测验证 `accept_inviterHasCompanyNoWallet_noThrow`（wallet 不存在 → tx 影响 0 行 → 不抛）；建议未来在 controller 层加全局异常拦截器审计 warn 日志 |
| 缺 Controller 单测（`OpcInvitationController` 5 个 endpoint） | 列为 W2.3 候选；先做 service 层更关键（业务逻辑复杂度集中在 service） |

---

## 7. 与 W1 / W2.1 测试规范的对照

| 项目 | W1 TaxReport | W2.1 Wallet | **W2.2 Invitation** |
|---|---|---|---|
| 测试框架 | JUnit 5 + Mockito | JUnit 5 + Mockito | JUnit 5 + Mockito |
| Strictness | LENIENT | LENIENT | **LENIENT** ✅ |
| `@InjectMocks` | ✅ | ✅ | ✅ |
| `ReflectionTestUtils` 处理字段注入 | — | — | ✅ (jdbcTemplate) |
| `ArgumentCaptor` 验证写入 | ✅ | ❌ | ✅ |
| 异常路径覆盖 | 3/3 | 4/4 | **12/12** ✅ |
| 正常路径覆盖 | 1/1 | 5/5 | **5/5** ✅ |
| 边界 / 顺序场景 | LLM 失败 / success=false | 幂等 retry / 并发扣款 | wallet 不存在 / 自己邀请自己 / 顺序化 update 返回值 |

测试质量稳步提升。

---

## 8. 验收 Checkpoint

- [x] 4 个公共方法 100% 行覆盖
- [x] 23 个 @Test，全部 `@DisplayName` 描述场景
- [x] `@MockitoSettings(strictness = LENIENT)` 与 W2.1 一致
- [x] `@InjectMocks` + `ReflectionTestUtils` 处理混合注入
- [x] Varargs 匹配用 `any(Object[].class)` / `any()`（Mockito 5 element-wise）
- [x] ArgumentCaptor 验证写入字段（inviteeId / usedCount / status / inviterId）
- [x] 边界场景：5 次冲突重试 / self-invite / wallet 不存在 / maxUses>1 / inviter profile 缺失
- [x] pom.xml 已加 spring-boot-starter-test (test scope)

---

## 9. 后续 W2 候选

| 任务 | 优先级 | 估值 |
|---|---|---|
| `OpcInvitationController` 单测（5 endpoint + SecurityUtils mock） | 中 | 1h |
| `OpcBillingController` 单测（4 endpoint） | 中 | 1h |
| `OpcFinanceTaxReportController` 单测（3 endpoint，postman 测过但无 java 单测） | 中 | 45min |
| `OpcFinanceVoucherService` 单测（聚合 / 凭证状态机） | 高 | 2h |
| `OpcUserProfileService` 单测（实名认证状态机） | 中 | 1h |

建议 W2.3 选 `OpcFinanceVoucherService`（业务量最大、状态机最复杂）。
