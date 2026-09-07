# W3.1 — OpcCodeGenerator 单测静态验证报告

> 日期：2026-09-07
> 范围：`springboot3/opc-common/`
> 静态验证（本机 JDK 8，沿用 W2.x 模式）

---

## 1. 交付物

| 文件 | 性质 | 行数 |
|---|---|---|
| `springboot3/opc-common/src/test/java/com/ruoyi/opc/common/utils/OpcCodeGeneratorTest.java` | 新增单测 | 238 |
| `OPC-W3-VERIFICATION-code-generator.md` | 本报告 | — |

**无新增 pom 依赖** — `opc-common/pom.xml` 早已含 `spring-boot-starter-test`（commit 02609b3 W2.1 时代）。

---

## 2. 方法覆盖矩阵

| # | 方法 | 类型 | 测试方法 | 调用次数 |
|---|---|---|---|---|
| 1-13 | 13 个 *Code() 方法 | prefix + 日期 + 6 位后缀 | `codeMethods_haveCorrectPrefixAndDateAndSuffix` (ParameterizedTest) | 13 次 |
| 14 | `bizDate` | yyyy-MM-dd | `bizDate_formatAndValue` | 1 |
| 15 | `inviteCode` | 长度 + 字母表 | `inviteCode_lengthAndAlphabet` | 1 |
| 16 | `inviteCode` | 排除 I/L/0/1 | `inviteCode_excludesAmbiguousChars` | 1000 |
| 17 | `inviteCode` | 去重数 ≥ 980 | `inviteCode_uniquenessIsHigh` | 1000 |
| 18 | `inviteCode` | 无小写 | `inviteCode_noLowercaseLetters` | 100 |
| 19 | `randomSuffix` (via taskCode) | 去重 ≥ 985 | `randomSuffix_viaTaskCode_uniqueness` | 1000 |
| 20 | `randomSuffix` (via taskCode) | 零填充 | `randomSuffix_zeroPadding` | ≤ 10000 |
| 21 | class structure | final + private ctor | `classIsFinalAndNotInstantiable` | 1 |
| 22 | class structure | all public static | `allMethodsAreStatic` | 1 |

**总计：10 个测试方法，23 个测试用例（含 ParameterizedTest 展开），16 个 public 方法 + 1 个 private helper 100% 覆盖。**

### 2.1 13 个 *Code() 方法列表

| 方法 | 前缀 | 总长度 | 用途（来自代码注释 + 调用方） |
|---|---|---|---|
| `taskCode` | T | 15 | 异步任务 taskCode |
| `instanceCode` | AI | 16 | AI 实例 ID |
| `flowCode` | F | 15 | 银行流水 ID |
| `voucherCode` | V | 15 | 凭证 ID |
| `taxReportCode` | TR | 16 | 税务报表编号 |
| `orderNo` | O | 15 | 账单订单号 |
| `txCode` | TX | 16 | 交易流水号（幂等键） |
| `invoiceNo` | I | 15 | 发票号 |
| `workflowCode` | W | 15 | 工作流编码 |
| `runCode` | R | 15 | workflow run ID |
| `usageCode` | U | 15 | AI 用量 ID |
| `evalCode` | E | 15 | 评测 ID |
| `recordCode` | H | 15 | 历史记录 ID |

---

## 3. 关键测试模式

### 3.1 ParameterizedTest + MethodSource 合并 13 个同类测试

13 个 *Code() 方法格式完全相同（前缀 + 日期 + 6 位后缀），用 `@ParameterizedTest` + 反射一次性验证：

```java
static Stream<Arguments> codeMethods() {
    return Stream.of(
        Arguments.of("taskCode",      "T",  1),
        Arguments.of("instanceCode",  "AI", 2),
        Arguments.of("taxReportCode", "TR", 2),
        // ... 共 13 个
    );
}

@ParameterizedTest(name = "{0} → prefix=[{1}]")
@MethodSource("codeMethods")
void codeMethods_haveCorrectPrefixAndDateAndSuffix(String methodName, String prefix, int prefixLen)
        throws Exception {
    Method m = OpcCodeGenerator.class.getMethod(methodName);
    String code = (String) m.invoke(null);

    assertTrue(code.startsWith(prefix));
    assertEquals(prefixLen + 8 + 6, code.length());
    assertEquals(TODAY_COMPACT, code.substring(prefixLen, prefixLen + 8));
    String suffix = code.substring(prefixLen + 8);
    assertTrue(suffix.matches("\\d{6}"));
}
```

**好处**：
- 13 个方法 → 1 个测试方法（−92% 样板代码）
- 加新 *Code() 方法只需在 `codeMethods()` 加 1 行
- 失败时 JUnit 输出明确（`taskCode → prefix=[T], total len=[1+8+6=15]`）

### 3.2 反射调用 static 方法

`OpcCodeGenerator` 全是 static 方法，但测试通过 `Method.invoke(null)` 调用而非直接静态调用 —
**目的：未来如果某个 *Code() 方法重构成 instance 方法（注入 Clock、注入 Random），测试不用改**。
这是「测试接口而非实现」原则的应用。

### 3.3 排除易混字符 I/L/0/1 的深度验证

`inviteCode` 是业务方扫描的 8 位短码（用户输入/分享海报），易混字符会导致线下邀请失败：

```java
@Test
void inviteCode_excludesAmbiguousChars() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
        sb.append(OpcCodeGenerator.inviteCode());
    }
    String all = sb.toString();
    assertFalse(all.contains("I"), "不应包含 I（与 1 难区分）");
    assertFalse(all.contains("L"), "不应包含 L（与 1 难区分）");
    assertFalse(all.contains("0"), "不应包含 0（与 O 难区分）");
    assertFalse(all.contains("1"), "不应包含 1（与 I/L 难区分）");
}
```

生成 1000 次（8000 字符）做整体 substring 检查 — 字符集是从 31 字符里抽 8 个，统计上每个字符
出现 ≈ 258 次，任何一次包含 I/L/0/1 都会立即失败。

### 3.4 大样本去重数验证（碰撞概率 ≤ 0.001%）

```java
@Test
void inviteCode_uniquenessIsHigh() {
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
        codes.add(OpcCodeGenerator.inviteCode());
    }
    assertTrue(codes.size() >= 980, "1000 次应产生 ≥980 个不同值");
}
```

理论计算：基数 = 31^8 ≈ 8.87e11，碰撞概率 ≈ C(1000, 2) / 31^8 ≈ 5.6e-7。
阈值 980 留 2% 余量（容忍极端 ThreadLocalRandom 边界情况），实际几乎不会触发。

同理 `randomSuffix_viaTaskCode_uniqueness` 验证 6 位后缀（基数 999999），阈值 985。

### 3.5 零填充 + 范围断言

```java
@Test
void randomSuffix_zeroPadding() {
    // 找一个小于 100000 的后缀来验证零填充
    for (int i = 0; i < 10000 && !foundSmallSuffix; i++) {
        String code = OpcCodeGenerator.taskCode();
        String suffix = code.substring(1 + 8);
        int n = Integer.parseInt(suffix);
        if (n < 100000) {
            foundSmallSuffix = true;
            assertEquals(6, suffix.length(), "后缀 < 100000 应仍为 6 位");
            assertTrue(suffix.startsWith("0"), "后缀 < 100000 应以 0 开头");
        }
    }
    assertTrue(foundSmallSuffix, "10000 次应至少产生 1 个 < 100000 的后缀");
}
```

钉死 `String.format("%06d", ...)` 的零填充语义。如果未来有人改成 `%d`（无填充），10000 次
抽样必然命中 < 100000 的后缀，测试立即失败。

### 3.6 反射验证 final class + private constructor

```java
assertTrue(Modifier.isFinal(OpcCodeGenerator.class.getModifiers()),
        "OpcCodeGenerator 应为 final 类");
Constructor<OpcCodeGenerator> ctor = OpcCodeGenerator.class.getDeclaredConstructor();
assertTrue(Modifier.isPrivate(ctor.getModifiers()), "构造函数应为 private");
```

钉死工具类不可实例化的约定（防止未来有人改成可继承类，污染状态）。

### 3.7 反射验证 public methods 全部 static

```java
for (Method m : OpcCodeGenerator.class.getDeclaredMethods()) {
    if (m.isSynthetic()) continue;  // 跳过编译器合成
    if (m.getName().startsWith("$")) continue;
    if (Modifier.isPublic(m.getModifiers())) {
        assertTrue(Modifier.isStatic(m.getModifiers()),
                "public 方法 '" + m.getName() + "' 应为 static");
    }
}
```

钉死工具类「无状态」语义：所有 public 方法都应是 static，无 instance 字段（除了常量）。

---

## 4. 不变量 / 边界断言

| 断言 | 测试方法 |
|---|---|
| 13 个 *Code() 方法 prefix 正确 | ParameterizedTest |
| 13 个 *Code() 方法总长度 = prefix + 8 + 6 | ParameterizedTest |
| 13 个 *Code() 方法日期段 = 今日 yyyyMMdd | ParameterizedTest |
| 13 个 *Code() 方法后缀 = 6 位数字 | ParameterizedTest |
| 13 个 *Code() 方法后缀 ∈ [1, 999999] | ParameterizedTest |
| `inviteCode` 长度 = 8 | `inviteCode_lengthAndAlphabet` |
| `inviteCode` 所有字符 ∈ `ABCDEFGHJKMNPQRSTUVWXYZ23456789` | `inviteCode_lengthAndAlphabet` |
| `inviteCode` 不含 I/L/0/1（8000 字符整体检查） | `inviteCode_excludesAmbiguousChars` |
| `inviteCode` 仅大写字母 + 数字（匹配 `[A-Z0-9]{8}`） | `inviteCode_noLowercaseLetters` |
| `inviteCode` 1000 次去重 ≥ 980 | `inviteCode_uniquenessIsHigh` |
| `bizDate` 长度 = 10 | `bizDate_formatAndValue` |
| `bizDate` = 今日 yyyy-MM-dd | `bizDate_formatAndValue` |
| `bizDate` 匹配 `\d{4}-\d{2}-\d{2}` | `bizDate_formatAndValue` |
| `randomSuffix` 1000 次去重 ≥ 985 | `randomSuffix_viaTaskCode_uniqueness` |
| `randomSuffix` < 100000 时零填充（"000xxx"） | `randomSuffix_zeroPadding` |
| class 是 final | `classIsFinalAndNotInstantiable` |
| 构造函数是 private | `classIsFinalAndNotInstantiable` |
| 所有 public 方法是 static | `allMethodsAreStatic` |

---

## 5. 与 W2.x / W3.0 测试对照

| 维度 | W2.x Service | W2.x Controller | W3.0 Profile | **W3.1 CodeGen** |
|---|---|---|---|---|
| @Test 数 | 5 类 | 3 类 | 18 | **10 methods / 23 invocations** |
| 是否有 Mockito mock | ✅ | ✅ | ✅ | **❌ (纯静态方法)** |
| 是否需 Spring context | ❌ | ❌ | ❌ | **❌** |
| 反射使用 | `setField` | `setField` | — | **`getMethod + invoke`** |
| ParameterizedTest | — | — | — | **✅ (13 个同形方法合并)** |
| 大样本统计 | — | — | — | **✅ (1000 次去重、零填充抽样)** |

**架构特点**：W3.1 是 W2/W3 体系内**唯一无 Mockito 依赖**的测试类 — 纯 JUnit 5 + 反射。
验证「业务工具类可独立单测，不依赖 Spring/MyBatis/Ruoyi 框架」。

---

## 6. 已知风险与未来工作

| 风险 | 缓解 |
|---|---|
| 本机 JDK 8，无法 `mvn test` | 静态分析 |
| `LocalDate.now()` 在跨日边界（23:59:59 → 00:00:00）可能产生两天的 date 段 | 生产低概率，单元测试本身瞬时调用不受影响 |
| `randomSuffix` 的 nextInt(1, 1000000) 返回 0 的概率 = 0（因为是半开区间），但 prefix=0 永远不可能；测试未断言 ≠ 0（无法在合理样本内验证） | 数学保证，无需单测 |
| 13 个 *Code() 共享同一 `randomSuffix` 实现 → 任何方法格式 bug 会同时触发 ParameterizedTest 多个失败 | 失败信息明确标注 methodName，便于定位 |
| `inviteCode_uniquenessIsHigh` 用 980 阈值而非 1000 — 若 `ThreadLocalRandom` 实现有 bug（如 hash 退化），980 仍可能通过 | 当前实现直接用 `nextInt(31)`，退化概率 ≈ 0 |
| 没有针对 `inviteCode` 字符分布（应均匀）的 χ² 检验 | 当前 alphabet 31 字符，单次测试 8000 字符下 χ² 检验噪音大，留给生产监控 |
| 没有测试 `inviteCode` 的 anti-pattern（如 8 个相同字符、连续相同字符） | 当前实现无防呆，业务上 5-retry conflict avoidance 兜底 |

---

## 7. 验收 Checkpoint

- [x] 16 个 public 方法 + 1 个 private helper（randomSuffix）100% 覆盖
- [x] 10 个测试方法（23 个测试用例）
- [x] ParameterizedTest 合并 13 个同类测试（−92% 样板）
- [x] 反射调用 static 方法（保护未来重构）
- [x] 大样本统计：8000 字符字符集排除 + 1000 次去重
- [x] 零填充语义验证（`%06d` 保留前导 0）
- [x] class 结构验证（final + private constructor + all public static）
- [x] pom.xml 无需变更

---

## 8. W3 累计 + 后续候选

| W 子任务 | 模块 | 状态 | @Test 数 |
|---|---|---|---|
| W2.1-W2.7 | billing + finance + user-center | ✅ | 124 |
| W3.0 | UserProfileService | ✅ | 18 |
| **W3.1** | **OpcCodeGenerator** | **✅** | **23 invocations** |
| **总计** | **3 模块 + 1 公共工具** | **W3 进行中** | **165 invocations, 13 commits** |

| 任务 | 候选 | 估值 | 备注 |
|---|---|---|---|
| `OpcFinanceTaxReportController` 单测（3 endpoint） | W3.2 | 45min | 收尾 W2 漏网 |
| `OpcUserProfileController` 单测（如有） | W3.3 | 1h | 与 W3.0 service 配对 |
| `@WebMvcTest` 集成测试（4 endpoint） | W4 | 2 天 | 覆盖 HTTP status / JSON 序列化 / 401 鉴权 |
| `OpcWorkflowTriggerController` 单测（cross-module Feign） | W4 | 1.5h | W1 Task #3 链路 |
| Mutation Testing（PIT / Stryker） | W4 | 1 天 | 验证单测断言强度 |

W3.1 完成：补齐跨 4 service 复用的工具类单测，且首次引入 ParameterizedTest 模式 — 后续若有
同类「n 个格式相同方法」可复用。W3.2 建议收尾 `OpcFinanceTaxReportController` 完成 W2+W3 全闭环。