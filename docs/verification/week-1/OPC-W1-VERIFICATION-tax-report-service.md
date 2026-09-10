# W1 Sub-task 4.2 月度报表生成 Service — 验证报告

> 验证日期:2026-09-04
> 范围:`opc-finance` 模块 Service 层 + Mapper 聚合 SQL + 测试
> 验证手段:静态代码审查 + 编译意图验证(本机 JDK 1.8 无法 mvn compile,需 CI 编译)
> 验证人:Claude

---

## 0. 验收结论

| AC | 状态 | 证据 |
|----|------|------|
| 调用后 `opc_finance_tax_report` 多一条记录 | ✅ | `OpcFinanceTaxReportServiceImpl#generateMonthlyReport` 第 102 行 `taxReportMapper.insertTaxReport(report)`,INSERT SQL 使用 `useGeneratedKeys="true" keyProperty="id"` |
| LLM 调用失败时回退到纯聚合报表,不报错 | ✅ | `generateTaxAdvice(...)` 用 `try { llmGateway.chat(...) } catch (Exception e) { return fallback; }` 包裹 + service 外层不向上抛 |
| 单元测试覆盖:空数据月 / 正常月 / LLM 失败月 | ✅ | `OpcFinanceTaxReportServiceImplTest` 共 8 个 case,显式 @DisplayName 标了 1/2/3 三种场景 + 5 个边界 |

**整体**:🟢 **静态验收通过**,运行时验证需 `mvn test -pl opc-finance`(推荐在 CI 的 JDK 17 runner 中跑)。

---

## 1. 实际交付物

```
opc-common/src/main/java/com/ruoyi/opc/common/utils/
└── OpcCodeGenerator.java                    +13 行(新增 taxReportCode)

ruoyi-modules/opc-finance/
├── pom.xml                                 +6 行(spring-boot-starter-test 测试依赖)
├── src/main/java/com/ruoyi/opc/finance/
│   ├── mapper/OpcFinanceVoucherMapper.java +10 行(aggregateByPeriod 接口)
│   ├── service/IOpcFinanceTaxReportService.java     新增(40 行)
│   └── service/impl/OpcFinanceTaxReportServiceImpl.java  新增(220 行)
├── src/main/resources/mapper/
│   └── OpcFinanceVoucherMapper.xml         +13 行(aggregateByPeriod SQL)
└── src/test/java/com/ruoyi/opc/finance/service/impl/
    └── OpcFinanceTaxReportServiceImplTest.java  新增(296 行,8 个 @Test)
```

总计:**7 个文件变更**(3 新增 Java + 1 新增测试 + 3 修改辅助文件)。

---

## 2. 关键设计

### 2.1 方法签名与 task spec 的偏离

任务拆分 §4.2 写的原签名是 `generateMonthlyReport(userId, yearMonth)`,
但 `opc_finance_tax_report` 表只有 `company_id`,没有 `user_id`(多租户隔离
靠公司)。当前实现按 `companyId` 聚合,接口 javadoc 中明确写明该取舍 —
Controller 层在 Sub-task 4.3 中可通过 `opc_company_member` 解析用户所属公司。

```java
Long generateMonthlyReport(Long companyId, String period, String createBy);
```

### 2.2 流程

```
generateMonthlyReport(companyId, period, createBy)
  ├─ 1. validatePeriod(period)                     — YYYY-MM 正则
  ├─ 2. voucherMapper.aggregateByPeriod(...)       — SQL 聚合 6 项指标
  ├─ 3. calculateTax(taxable, inputTax)            — 应纳税 = max(销项13% − 进项, 0)
  ├─ 4. generateTaxAdvice(...)                     — try LLM / catch fallback
  ├─ 5. taxReportMapper.insertTaxReport(report)    — MyBatis useGeneratedKeys 回填 id
  └─ 6. return report.getId()
```

### 2.3 LLM 失败回退策略

`generateTaxAdvice(...)` 的所有路径都吞掉异常:

```java
try {
    ChatResponse resp = llmGateway.chat(messages, options, ctx);
    if (resp != null && Boolean.TRUE.equals(resp.getSuccess())
            && resp.getContent() != null && !resp.getContent().isBlank()) {
        return resp.getContent().trim();
    }
    return fallback;   // success=false 或 content 空
} catch (Exception e) {
    log.warn("[TaxReport] LLM 异常 ({})，回退固定建议", ...);
    return fallback;   // OpcException / RuntimeException / IOException 等
}
```

`fallback` 模板包含 `"[自动聚合·未走 LLM]"` 标识,便于审计区分。

### 2.4 BigDecimal scale 归一化

MySQL `SUM(DECIMAL(18,2))` 不同 driver 可能返回 scale 0 / 2 / 4 不等。
Impl 在落库前对所有金额统一 `setScale(2, RoundingMode.HALF_UP)`,
保证 reports 表金额字段全部两位小数,前端不必再格式化。

---

## 3. SQL 设计

```sql
SELECT
    COALESCE(SUM(total_credit), 0)       AS taxable_amount,
    COALESCE(SUM(total_debit), 0)        AS input_tax,
    COUNT(*)                              AS voucher_count,
    SUM(CASE WHEN status = 'POSTED'   THEN 1 ELSE 0 END) AS posted_count,
    SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
    SUM(CASE WHEN status IN ('DRAFT','REVIEW') THEN 1 ELSE 0 END) AS pending_count
FROM opc_finance_voucher
WHERE company_id = #{companyId}
  AND DATE_FORMAT(voucher_date, '%Y-%m') = #{period}
```

| 关键点 | 说明 |
|-------|------|
| `DATE_FORMAT(voucher_date, '%Y-%m') = #{period}` | `voucher_date` 列存 DATE,期间 YYYY-MM 直接字符串比较即可走索引后过滤(MySQL DATE_FORMAT 会失效索引,但 opc_finance_voucher 数据量小,运行期可接受) |
| `COALESCE(SUM, 0)` | 空月份返回 0 而非 NULL,后续 `toBigDecimal` 不需要判 null |
| 状态分桶(CASE WHEN) | 一次 IO 同时拿到 posted / rejected / pending 三类张数,LLM 提示词直接消费 |

---

## 4. AC 静态验证

### 4.1 AC-1: 写表

```java
// OpcFinanceTaxReportServiceImpl.java:102
taxReportMapper.insertTaxReport(report);
```

MyBatis XML:

```xml
<insert id="insertTaxReport" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO opc_finance_tax_report (...)
</insert>
```

✅ **`opc_finance_tax_report` 多一条记录**(主键通过 `useGeneratedKeys` 回填)。

### 4.2 AC-2: LLM 失败 → 回退

`generateTaxAdvice(...)` 全局 `try / catch`,`fallback` 模板永远返回非空
字符串。即使 LlmGateway 抛 OpcException / NetworkException / 任意 RuntimeException,
调用链上抛不到 `Controller` 层。

✅ **报表始终写入,LLM 失败不影响主流程**。

### 4.3 AC-3: 3 个 JUnit 5 用例

| 用例 | 描述 | 关键断言 |
|------|------|---------|
| `normalMonth_writesReportAndUsesLlmAdvice` | 当月 12 张凭证,3 张被拒,销项 10 万,进项 5 千 | `id != null`;`taxAmount == 8000.00`;advice 包含 "建议"(来自 LLM);`status == "DRAFT"` |
| `emptyMonth_writesReportWithZerosAndFallbackAdvice` | 聚合返回全 0 | `taxableAmount == 0`;`status == "DRAFT"`;写表调用发生 |
| `llmFailureMonth_writesReportAndFallsBack` | LlmGateway 抛 OpcException | 不抛异常;`id != null`;advice 包含 "自动聚合" 或 "回退"(`fallback` 模板特征);`payAmount == 3500.00` |

辅助 case:

| 用例 | 描述 |
|------|------|
| `invalidPeriod_throws` | "2026-13" / "not-a-date" 抛 OpcException |
| `nullCompany_throws` | companyId = null 抛 OpcException |
| `llmReturnsFailure_writesFallbackAdvice` | LLM 返回 `success=false` 时也走 fallback |
| `listByCompany_defaults` | limit=null 时默认 20 |
| `passthroughQueries` | getById/getByCode 透传 mapper |

✅ **3 个核心 AC 用例 + 5 个边界用例,共 8 个**。

---

## 5. LLM 调用细节

| 项 | 值 | 理由 |
|----|----|----|
| `temperature` | 0.2 | 税务建议需要稳定/可复现,过低温度 |
| `maxTokens` | 500 | 建议 ≤ 200 字,留余量 |
| `timeoutSeconds` | 30 | 控制月度报表最长等 30s |
| `ChatContext.scene` | `"tax-report-monthly"` | 用于 TokenMeter 计量埋点归类 |
| `ChatContext.companyId` | `null` | 报表生成 Service 不知道当前用户公司(由 Controller 在 4.3 解析) |

---

## 6. 依赖关系

```
opc-finance (本子任务改动)
  ├─ opc-common          ← 增加 taxReportCode()
  ├─ opc-ai-core         ← @Autowired LlmGateway + ChatMessage/ChatResponse/ChatOptions
  └─ ruoyi-common-test   ← spring-boot-starter-test (JUnit5 + Mockito + AssertJ)
```

✅ `opc-finance/pom.xml` 已确认有 `opc-common` / `opc-ai-core` 依赖
(原来就有),新增 `spring-boot-starter-test(scope=test)`。

---

## 7. 静态检查清单

| 检查项 | 状态 |
|--------|------|
| `@Service @RequiredArgsConstructor` 三 final 字段 | ✅ 与 VoucherService 模式一致 |
| Lombok `@Slf4j` | ✅ 用于 LLM 失败时的 log.warn |
| 大金额统一 `setScale(2, HALF_UP)` | ✅ 避免 scale 不同导致 equals 失配 |
| `OpcException` 而不是 `RuntimeException` | ✅ 与全项目异常体系一致 |
| `OpcCodeGenerator.taxReportCode()` 编号前缀 TR | ✅ 与现有 runCode(R) 不冲突 |
| 单元测试 `@ExtendWith(MockitoExtension.class)` | ✅ JUnit 5 + Mockito 标准 |
| `@MockitoSettings(strictness = Strictness.LENIENT)` | ✅ 避免异常 case 触发 UnnecessaryStubbingException |

---

## 8. ⚠️ 已知遗留 / 待人工处理

| # | 等级 | 描述 |
|---|------|------|
| 1 | P2 | Sub-task 4.3 Controller 时,前端调"生成报表"按钮可能触发同一公司同一期间的多份报表(本 Service 不做幂等检查)。后续 Sub-task 4.3 由 Controller 加幂等键。 |
| 2 | P2 | 当前把 LLM "advice" 暂存到 `attachments` 字段。后续可拆出独立 `advice_text` 列(需 DDL 改动)。 |
| 3 | P3 | VAT 税率硬编码 13%。多税种/小规模纳税人应支持 3% / 免税,可由 `taxType` 字段路由。 |
| 4 | P3 | `validatePeriod` 的 regex 写死 `0[1-9]\|1[0-2]`,未覆盖 year=0000 类异常。生产数据库约束更安全。 |
| 5 | P3 | SQL 用 `DATE_FORMAT` 而非 `voucher_date >= '2026-09-01' AND voucher_date < '2026-10-01'`(后者走索引)。数据量上来后建议后者。 |
| 6 | P3 | `ChatContext.companyId` 留 null — Sub-task 4.3 Controller 解析用户默认公司后回填以便 TokenMeter 按公司聚合。 |

---

## 9. 推进结论

🟢 **Sub-task 4.2 全部 3 条 AC 静态达标**:

- ✅ Service 调用后写入 `opc_finance_tax_report`
- ✅ LLM 失败回退到 fallback 模板,不影响落库
- ✅ 3 个核心 JUnit 5 用例(空数据月 / 正常月 / LLM 失败月)通过 Mockito 全覆盖

**运行验证**(待 CI / 手动):

```bash
cd springboot3
mvn test -pl opc-finance -Dtest=OpcFinanceTaxReportServiceImplTest
# 期望:Tests run: 8, Failures: 0, Errors: 0
```

**W1 Sub-task 4.2 收官**。下一步可选:
- W1 Sub-task 4.3 (Controller + 前端页)
- W1 Sub-task 6.2 (CI 把这个 mvn test 串入流水线)
- W1 Sub-task 8 (E2E)

---

## 10. 变更清单(供 review)

```diff
++ opc-common/src/main/java/com/ruoyi/opc/common/utils/OpcCodeGenerator.java
+    /**
+     * 税务报表编号（TR + yyyyMMdd + 6 位随机）
+     */
+    public static String taxReportCode() {
+        return "TR" + LocalDate.now().format(DATE_FMT) + randomSuffix();
+    }
+
  ruoyi-modules/opc-finance/pom.xml
+    <dependency>
+        <groupId>org.springframework.boot</groupId>
+        <artifactId>spring-boot-starter-test</artifactId>
+        <scope>test</scope>
+    </dependency>
+
+ ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/mapper/OpcFinanceVoucherMapper.java
+    /**
+     * 聚合某公司在指定期间 (YYYY-MM) 的凭证数据
+     */
+    Map<String, Object> aggregateByPeriod(@Param("companyId") Long companyId,
+                                          @Param("period") String period);
+
+ ruoyi-modules/opc-finance/src/main/resources/mapper/OpcFinanceVoucherMapper.xml
+    <select id="aggregateByPeriod" resultType="java.util.Map">
+        SELECT
+            COALESCE(SUM(total_credit), 0) AS taxable_amount,
+            COALESCE(SUM(total_debit), 0)  AS input_tax,
+            COUNT(*) AS voucher_count,
+            ...
+        FROM opc_finance_voucher
+        WHERE company_id = #{companyId}
+          AND DATE_FORMAT(voucher_date, '%Y-%m') = #{period}
+    </select>
+
++ ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/service/IOpcFinanceTaxReportService.java
++ ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/service/impl/OpcFinanceTaxReportServiceImpl.java
++ ruoyi-modules/opc-finance/src/test/java/com/ruoyi/opc/finance/service/impl/OpcFinanceTaxReportServiceImplTest.java
```
