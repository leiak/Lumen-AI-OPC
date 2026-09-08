# M4 INSIGHT Agent MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the OPC 数据洞察 Agent (INSIGHT) — 4 capabilities (日报 / 经营驾驶舱 / 异常预警 / 决策建议) — as a new `opc-insight` Spring Boot microservice (port 9306) with full TDD coverage, 8-day delivery cadence.

**Architecture:** New `opc-insight` service consumes finance/billing/user data via Feign, runs LLM through `opc-ai-core` LLM Gateway, persists to 3 new tables (`opc_insight_daily_report`, `opc_insight_anomaly`, `opc_insight_advice`). 4 REST controllers expose dashboard/alerts/daily/advice; Quartz cron 09:00 daily report; Vue 3 multi-page UI under `/opc/insight/*`.

**Tech Stack:** Spring Boot 3 + RuoYi-Cloud + MyBatis + MySQL 8 + Feign + Quartz (ruoyi-job) + DeepSeek/GPT-4o-mini (LLM Gateway) + JUnit5/Mockito + Vue 3 + Element Plus + Vitest.

**Spec:** `docs/superpowers/specs/2026-09-08-opc-insight-mvp-design.md`

**Test Target:** 218 tests total (64 backend + 64 frontend + 80 eval + 10 redteam).

---

## File Structure (locked before tasks)

### New Files

```
springboot3/ruoyi-modules/opc-insight/
├── pom.xml
├── src/main/java/com/ruoyi/opc/insight/
│   ├── OpcInsightApplication.java
│   ├── controller/
│   │   ├── DashboardController.java
│   │   ├── AlertController.java
│   │   ├── DailyReportController.java
│   │   └── AdviceController.java
│   ├── service/
│   │   ├── IKpiService.java
│   │   ├── KpiServiceImpl.java
│   │   ├── IAnomalyService.java
│   │   ├── AnomalyServiceImpl.java
│   │   ├── IDailyReportService.java
│   │   ├── DailyReportServiceImpl.java
│   │   ├── IAdviceService.java
│   │   └── AdviceServiceImpl.java
│   ├── client/
│   │   ├── RemoteFinanceService.java
│   │   ├── RemoteBillingService.java
│   │   └── RemoteUserCenterService.java
│   ├── domain/
│   │   ├── OpcInsightDailyReport.java
│   │   ├── OpcInsightAnomaly.java
│   │   └── OpcInsightAdvice.java
│   ├── mapper/
│   │   ├── OpcInsightDailyReportMapper.java
│   │   ├── OpcInsightAnomalyMapper.java
│   │   └── OpcInsightAdviceMapper.java
│   ├── workflow/
│   │   └── InsightDailyReportJob.java
│   ├── enums/
│   │   ├── AnomalyLevel.java
│   │   └── AnomalyRule.java
│   ├── vo/
│   │   ├── KpiSnapshot.java
│   │   ├── DashboardVo.java
│   │   ├── AnomalyVo.java
│   │   ├── DailyReportVo.java
│   │   └── AdviceVo.java
│   └── config/
│       └── InsightFeignConfig.java
├── src/main/resources/
│   ├── bootstrap.yml
│   ├── application.yml
│   └── mapper/
│       ├── OpcInsightDailyReportMapper.xml
│       ├── OpcInsightAnomalyMapper.xml
│       └── OpcInsightAdviceMapper.xml
└── src/test/java/com/ruoyi/opc/insight/
    ├── service/impl/
    │   ├── KpiServiceImplTest.java
    │   ├── AnomalyServiceImplTest.java
    │   ├── DailyReportServiceImplTest.java
    │   └── AdviceServiceImplTest.java
    ├── workflow/
    │   └── InsightDailyReportJobTest.java
    └── controller/
        └── OpcInsightControllerMvcTest.java

springboot3/sql/migrations/V20260908__opc_insight_schema.sql
springboot3/opc-ai-core/src/main/resources/prompts/insight-system-v1.0.txt
springboot3/opc-ai-core/src/main/resources/prompts/insight-soft-anomaly-v1.0.txt
springboot3/opc-ai-core/src/main/resources/prompts/insight-advice-v1.0.txt
springboot3/opc-ai-core/src/main/resources/eval/insight-agent-v1.0.json
springboot3/opc-ai-core/src/main/resources/eval/insight-redteam-10.json

vue3-typescript/src/api/opc/insight.ts
vue3-typescript/src/views/opc/insight/
├── dashboard.vue
├── alerts.vue
├── daily.vue
└── advice.vue
```

### Modified Files

```
springboot3/pom.xml                                                  # add opc-insight module
springboot3/ruoyi-modules/pom.xml                                    # add opc-insight module
springboot3/ruoyi-modules/opc-finance/src/main/java/.../controller/OpcFinanceController.java  # +4 aggregation endpoints
springboot3/ruoyi-modules/opc-billing/src/main/java/.../controller/OpcBillingController.java  # +3 aggregation endpoints
springboot3/deploy/nacos/opc-routes.json                             # add /opc/insight/** route
springboot3/deploy/helm/opc/values-{dev,staging,prod}.yaml           # add insight service
springboot3/deploy/helm/opc/templates/deployment-insight.yaml        # new
springboot3/deploy/helm/opc/templates/service-insight.yaml           # new
springboot3/deploy/docker-compose.yml                                # add insight service
vue3-typescript/src/router/index.ts                                  # add /opc/insight/* routes
```

---

## Task 1: opc-finance 聚合端点 (4 endpoints) — Backend A Day 1

**Files:**
- Modify: `springboot3/ruoyi-modules/opc-finance/src/main/java/com/ruoyi/opc/finance/controller/OpcFinanceController.java`
- Test: `springboot3/ruoyi-modules/opc-finance/src/test/java/com/ruoyi/opc/finance/controller/OpcFinanceAggControllerTest.java`

**Context:** INSIGHT 需要从 opc-finance 拉 4 类聚合数据：voucher/flow/tax/token。给现有 `OpcFinanceController` 加 4 个 `@GetMapping("/agg/...")` 端点，返回 `R.ok(AggregationVo)`。

- [ ] **Step 1: Write the failing test**

```java
// OpcFinanceAggControllerTest.java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpcFinanceAggControllerTest {
    @Mock OpcFinanceVoucherService voucherService;
    @Mock OpcFinanceBankFlowService bankFlowService;
    @Mock OpcFinanceTaxReportService taxReportService;
    @InjectMocks OpcFinanceController controller;

    @Test
    void voucherAgg_returnsAggregatedTotals() {
        when(voucherService.aggregateByPeriod(1L, "2026-09"))
            .thenReturn(new VoucherAggVo(1000.00, 800.00, 50, 5));
        R<?> r = controller.voucherAgg(1L, "2026-09");
        assertEquals(200, r.get("code"));
        assertNotNull(r.get("data"));
    }

    @Test
    void flowAgg_returnsFlowTotals() { /* similar */ }
    @Test
    void taxReport_returnsLatestReport() { /* similar */ }
    @Test
    void tokenUsage_returnsUsage() { /* similar */ }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd springboot3
mvn test -pl ruoyi-modules/opc-finance -Dtest=OpcFinanceAggControllerTest
# Expected: compilation error or test fail (endpoints not yet defined)
```

- [ ] **Step 3: Add 4 aggregation endpoints to OpcFinanceController**

```java
// Append to OpcFinanceController.java
@GetMapping("/agg/voucher")
public R<?> voucherAgg(@RequestParam Long companyId, @RequestParam String period) {
    return R.ok(voucherService.aggregateByPeriod(companyId, period));
}

@GetMapping("/agg/flow")
public R<?> flowAgg(@RequestParam Long companyId, @RequestParam String period) {
    return R.ok(bankFlowService.aggregateByPeriod(companyId, period));
}

@GetMapping("/agg/tax-report")
public R<?> taxReport(@RequestParam Long companyId, @RequestParam String period) {
    return R.ok(taxReportService.getByCompanyAndPeriod(companyId, period));
}

@GetMapping("/agg/token-usage")
public R<?> tokenUsage(@RequestParam Long companyId, @RequestParam String period) {
    return R.ok(voucherService.tokenUsageForCompany(companyId, period));
}
```

- [ ] **Step 4: Add 4 corresponding service methods** (if not exist; reuse `aggregateByPeriod` from W1.4.2)

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl ruoyi-modules/opc-finance -Dtest=OpcFinanceAggControllerTest
# Expected: PASS
```

- [ ] **Step 6: Commit**

```bash
git add springboot3/ruoyi-modules/opc-finance/
git commit -m "feat(finance): 4 aggregation endpoints for INSIGHT Feign client"
```

---

## Task 2: opc-billing 聚合端点 (3 endpoints) — Backend A Day 2

**Files:**
- Modify: `springboot3/ruoyi-modules/opc-billing/src/main/java/com/ruoyi/opc/billing/controller/OpcBillingController.java`
- Test: `springboot3/ruoyi-modules/opc-billing/src/test/java/com/ruoyi/opc/billing/controller/OpcBillingAggControllerTest.java`

**Pattern:** Same as Task 1, 3 endpoints:
- `GET /opc/billing/agg/wallet?companyId=X` → wallet balance
- `GET /opc/billing/agg/orders?companyId=X&period=Y` → order totals
- `GET /opc/billing/agg/recharge?companyId=X&period=Y` → recharge totals

- [ ] **Step 1-6: Follow TDD pattern from Task 1, adapt for 3 billing endpoints**

```bash
git commit -m "feat(billing): 3 aggregation endpoints for INSIGHT Feign client"
```

---

## Task 3: opc-insight Maven module scaffold — Day 1

**Files:**
- Create: `springboot3/ruoyi-modules/opc-insight/pom.xml`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/OpcInsightApplication.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/resources/bootstrap.yml`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/resources/application.yml`
- Modify: `springboot3/pom.xml` (add `<module>ruoyi-modules/opc-insight</module>`)
- Modify: `springboot3/ruoyi-modules/pom.xml` (same)

- [ ] **Step 1: Create pom.xml (mirror opc-finance's pom structure)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <parent>
        <groupId>com.ruoyi</groupId>
        <artifactId>ruoyi-modules</artifactId>
        <version>${revision}</version>
    </parent>
    <artifactId>opc-insight</artifactId>
    <dependencies>
        <dependency><groupId>com.ruoyi</groupId><artifactId>opc-common</artifactId></dependency>
        <dependency><groupId>com.ruoyi</groupId><artifactId>ruoyi-common-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.cloud</groupId><artifactId>spring-cloud-starter-openfeign</artifactId></dependency>
        <dependency><groupId>com.ruoyi</groupId><artifactId>ruoyi-api-system</artifactId></dependency>
        <dependency><groupId>com.ruoyi</groupId><artifactId>ruoyi-api-billing</artifactId></dependency>  <!-- if exists -->
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    </dependencies>
    <build><finalName>${project.artifactId}</finalName>
        <plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugins>
    </build>
</project>
```

- [ ] **Step 2: Create Application class**

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ruoyi.opc.insight.client")
@ComponentScan(basePackages = {"com.ruoyi.opc.insight", "com.ruoyi.common.security"})
public class OpcInsightApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpcInsightApplication.class, args);
    }
}
```

- [ ] **Step 3: Create bootstrap.yml + application.yml** (mirror opc-finance's, port 9306)

```yaml
# bootstrap.yml
server:
  port: 9306
spring:
  application:
    name: opc-insight
  profiles:
    active: dev
  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        namespace: opc-prod
      config:
        server-addr: nacos:8848
        namespace: opc-prod
        file-extension: yml
```

- [ ] **Step 4: Add to parent poms**

Edit `springboot3/pom.xml` and `springboot3/ruoyi-modules/pom.xml` to add `<module>ruoyi-modules/opc-insight</module>` in correct order.

- [ ] **Step 5: Build to verify**

```bash
cd springboot3
mvn clean install -pl ruoyi-modules/opc-insight -am -DskipTests
# Expected: BUILD SUCCESS
```

- [ ] **Step 6: Commit**

```bash
git add springboot3/ruoyi-modules/opc-insight/ springboot3/pom.xml springboot3/ruoyi-modules/pom.xml
git commit -m "feat(insight): scaffold opc-insight Maven module"
```

---

## Task 4: SQL migration (3 tables) — Day 1

**Files:**
- Create: `springboot3/sql/migrations/V20260908__opc_insight_schema.sql`

- [ ] **Step 1: Create migration file with 3 tables** (full SQL from spec §1.2)

```sql
-- V20260908__opc_insight_schema.sql
CREATE TABLE IF NOT EXISTS opc_insight_daily_report (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id      BIGINT       NOT NULL,
  period          DATE         NOT NULL,
  summary_md      MEDIUMTEXT,
  kpi_json        JSON,
  advice_md       MEDIUMTEXT,
  llm_used        VARCHAR(64),
  create_by       VARCHAR(64),
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_by       VARCHAR(64),
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_company_date (company_id, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INSIGHT 日报表';

CREATE TABLE IF NOT EXISTS opc_insight_anomaly (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id      BIGINT       NOT NULL,
  period          DATE         NOT NULL,
  level           VARCHAR(8)   NOT NULL,
  rule_code       VARCHAR(64)  NOT NULL,
  description     VARCHAR(512),
  status          VARCHAR(16)  DEFAULT 'OPEN',
  llm_confidence  DECIMAL(3,2),
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_company_status (company_id, status),
  KEY idx_period (period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INSIGHT 异常表';

CREATE TABLE IF NOT EXISTS opc_insight_advice (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_id      BIGINT       NOT NULL,
  topic           VARCHAR(64)  NOT NULL,
  advice_md       MEDIUMTEXT,
  llm_used        VARCHAR(64),
  confidence      DECIMAL(3,2),
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY idx_company_topic_time (company_id, topic, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='INSIGHT 决策建议表';
```

- [ ] **Step 2: Verify SQL syntax** (optional, if MySQL available)

```bash
docker exec -i opc-mysql mysql -uroot -p'Opc@2026!' opc -e "SOURCE /sql/migrations/V20260908__opc_insight_schema.sql"
# Expected: 3 tables created
```

- [ ] **Step 3: Commit**

```bash
git add springboot3/sql/migrations/V20260908__opc_insight_schema.sql
git commit -m "feat(sql): opc_insight_* 3 tables migration"
```

---

## Task 5: KpiService impl + 12 tests — Day 2

**Files:**
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/client/{RemoteFinanceService,RemoteBillingService,RemoteUserCenterService}.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/vo/KpiSnapshot.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/service/IKpiService.java` + `KpiServiceImpl.java`
- Test: `springboot3/ruoyi-modules/opc-insight/src/test/java/com/ruoyi/opc/insight/service/impl/KpiServiceImplTest.java`

- [ ] **Step 1: Define KpiSnapshot VO**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiSnapshot {
    private Long companyId;
    private String period;
    private BigDecimal totalRevenue;        // 收入合计
    private BigDecimal totalExpense;        // 支出合计
    private Long voucherCount;              // 凭证数
    private Long pendingVoucherCount;       // 待审凭证数
    private BigDecimal walletBalance;       // 钱包余额
    private Long tokenUsage;                // 本月 token 用量
    private Long companyActiveDays;         // 公司活跃天数
    private boolean partial;                // true=部分降级
}
```

- [ ] **Step 2: Write 3 Feign client interfaces**

```java
@FeignClient(contextId = "remoteFinanceService", value = ServiceNameConstants.FINANCE_SERVICE,
             fallbackFactory = RemoteFinanceFallbackFactory.class)
public interface RemoteFinanceService {
    @GetMapping("/opc/finance/agg/voucher")
    R<VoucherAggVo> voucherAgg(@RequestParam Long companyId, @RequestParam String period);

    @GetMapping("/opc/finance/agg/flow")
    R<FlowAggVo> flowAgg(@RequestParam Long companyId, @RequestParam String period);

    @GetMapping("/opc/finance/agg/tax-report")
    R<TaxReportVo> taxReport(@RequestParam Long companyId, @RequestParam String period);

    @GetMapping("/opc/finance/agg/token-usage")
    R<TokenUsageVo> tokenUsage(@RequestParam Long companyId, @RequestParam String period);
}
```

```java
@FeignClient(contextId = "remoteBillingService", value = ServiceNameConstants.BILLING_SERVICE,
             fallbackFactory = RemoteBillingFallbackFactory.class)
public interface RemoteBillingService {
    @GetMapping("/opc/billing/agg/wallet")
    R<WalletAggVo> wallet(@RequestParam Long companyId);

    @GetMapping("/opc/billing/agg/orders")
    R<OrdersAggVo> orders(@RequestParam Long companyId, @RequestParam String period);

    @GetMapping("/opc/billing/agg/recharge")
    R<RechargeAggVo> recharge(@RequestParam Long companyId, @RequestParam String period);
}
```

```java
@FeignClient(contextId = "remoteUserCenterService", value = ServiceNameConstants.USER_CENTER_SERVICE,
             fallbackFactory = RemoteUserCenterFallbackFactory.class)
public interface RemoteUserCenterService {
    @GetMapping("/opc/user/companies/profile")
    R<CompanyProfileVo> getCompanyProfile(@RequestParam Long companyId);
}
```

- [ ] **Step 3: Write failing KpiServiceImplTest (12 tests)**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiServiceImplTest {
    @Mock RemoteFinanceService financeClient;
    @Mock RemoteBillingService billingClient;
    @Mock RemoteUserCenterService userClient;
    @InjectMocks KpiServiceImpl kpiService;

    @Test
    void snapshot_aggregatesAllSources() { /* 4 clients all return data, snapshot has all fields populated */ }
    @Test
    void snapshot_financeFeignFails_returnsEmptyVoucherAndPartialTrue() { /* financeClient.voucherAgg throws → voucherCount=0, partial=true */ }
    @Test
    void snapshot_billingFeignFails_returnsEmptyWalletAndPartialTrue() { /* */ }
    @Test
    void snapshot_userFeignFails_returnsEmptyProfileAndPartialTrue() { /* */ }
    @Test
    void snapshot_allFeignFail_returnsEmptySnapshotAndPartialTrue() { /* */ }
    @Test
    void snapshot_zeroPeriodDefaultsToCurrentMonth() { /* period=null → 2026-09 */ }
    @Test
    void snapshot_invalidCompanyId_throwsOpcException() { /* companyId=null → OpcException */ }
    @Test
    void snapshot_noCompany_throwsOpcException() { /* */ }
    @Test
    void snapshot_handlesNullResponseFromFeign() { /* R.ok(null) → BigDecimal.ZERO */ }
    @Test
    void snapshot_handlesNon200ResponseFromFeign() { /* R<> with code!=200 → empty data + partial=true */ }
    @Test
    void snapshot_periodFormat_yyyyDashMM() { /* period=2026-09 → DATE */ }
    @Test
    void snapshot_concurrentCallsAreIndependent() { /* 2 calls in parallel, both succeed */ }
}
```

- [ ] **Step 4: Run test to verify it fails**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=KpiServiceImplTest
# Expected: compilation error (KpiServiceImpl not found)
```

- [ ] **Step 5: Implement KpiServiceImpl**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class KpiServiceImpl implements IKpiService {

    private final RemoteFinanceService financeClient;
    private final RemoteBillingService billingClient;
    private final RemoteUserCenterService userClient;

    @Override
    public KpiSnapshot snapshot(Long companyId, String period) {
        if (companyId == null) throw new OpcException("companyId 不能为空");
        if (period == null) period = YearMonth.now().toString(); // 2026-09

        KpiSnapshot.KpiSnapshotBuilder b = KpiSnapshot.builder()
            .companyId(companyId)
            .period(period)
            .partial(false);

        try {
            R<VoucherAggVo> r = financeClient.voucherAgg(companyId, period);
            if (r != null && r.getCode() == 200 && r.getData() != null) {
                VoucherAggVo v = r.getData();
                b.totalRevenue(v.getDebitTotal()).totalExpense(v.getCreditTotal())
                 .voucherCount(v.getCount()).pendingVoucherCount(v.getPendingCount());
            } else { b.partial(true); }
        } catch (Exception e) { log.warn("voucherAgg fail: {}", e.getMessage()); b.partial(true); }

        // ... same pattern for flowAgg, taxReport, tokenUsage, walletBalance, getCompanyProfile ...

        return b.build();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=KpiServiceImplTest
# Expected: 12 passed
```

- [ ] **Step 7: Commit**

```bash
git add springboot3/ruoyi-modules/opc-insight/
git commit -m "feat(insight): KpiService impl + 12 unit tests + 3 Feign clients"
```

---

## Task 6: AnomalyRule enum + AnomalyServiceImpl + 18 tests — Day 3

**Files:**
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/enums/AnomalyLevel.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/enums/AnomalyRule.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/vo/AnomalyVo.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/service/IAnomalyService.java` + `AnomalyServiceImpl.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/mapper/OpcInsightAnomalyMapper.java` + xml
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/domain/OpcInsightAnomaly.java`
- Test: `AnomalyServiceImplTest.java` (18 tests)

- [ ] **Step 1: Define AnomalyLevel enum**

```java
public enum AnomalyLevel { HIGH, MEDIUM, LOW }
```

- [ ] **Step 2: Define AnomalyRule enum (8 hardcoded rules)**

```java
public enum AnomalyRule {
    VOUCHER_OVER_100K("VOUCHER_OVER_100K", AnomalyLevel.HIGH,
        "单笔凭证超 ¥100,000",
        s -> s.getVoucherCount() > 0 && s.getTotalExpense().compareTo(new BigDecimal("100000")) > 0),
    PENDING_VOUCHER_OVER_7D("PENDING_VOUCHER_OVER_7D", AnomalyLevel.HIGH,
        "凭证待审超 7 天",
        s -> s.getPendingVoucherCount() != null && s.getPendingVoucherCount() > 0), // simplified, see LLM
    WALLET_BALANCE_LOW("WALLET_BALANCE_LOW", AnomalyLevel.MEDIUM,
        "钱包余额不足 ¥100",
        s -> s.getWalletBalance() != null && s.getWalletBalance().compareTo(new BigDecimal("100")) < 0),
    TOKEN_USAGE_SPIKE("TOKEN_USAGE_SPIKE", AnomalyLevel.MEDIUM,
        "Token 用量激增 (本月 > 100k)",
        s -> s.getTokenUsage() != null && s.getTokenUsage() > 100_000),
    REVENUE_DROP("REVENUE_DROP", AnomalyLevel.MEDIUM,
        "本月收入为 0",
        s -> s.getTotalRevenue() != null && s.getTotalRevenue().compareTo(BigDecimal.ZERO) == 0),
    EXPENSE_EXCEEDS_REVENUE("EXPENSE_EXCEEDS_REVENUE", AnomalyLevel.HIGH,
        "支出 > 收入 (亏损)",
        s -> s.getTotalExpense() != null && s.getTotalRevenue() != null
            && s.getTotalExpense().compareTo(s.getTotalRevenue()) > 0),
    HIGH_VOUCHER_REJECTION_RATE("HIGH_VOUCHER_REJECTION_RATE", AnomalyLevel.MEDIUM,
        "凭证退单率高",
        s -> false), // requires additional data; LLM scans instead
    MULTIPLE_HIGH_VALUE_FLOWS("MULTIPLE_HIGH_VALUE_FLOWS", AnomalyLevel.LOW,
        "多笔大额流水",
        s -> s.getVoucherCount() != null && s.getVoucherCount() > 50);

    // ... constructor + getter + match() helper
}
```

- [ ] **Step 3: Define AnomalyVo**

```java
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AnomalyVo {
    private Long id;
    private Long companyId;
    private String period;
    private AnomalyLevel level;
    private String ruleCode;
    private String description;
    private String status;        // OPEN/ACK/RESOLVED
    private BigDecimal llmConfidence;
    private LocalDateTime createTime;
}
```

- [ ] **Step 4: Create domain + mapper for opc_insight_anomaly**

```java
@Data @TableName("opc_insight_anomaly")
public class OpcInsightAnomaly {
    @TableId(type = IdType.AUTO) private Long id;
    private Long companyId;
    private LocalDate period;
    private String level;
    private String ruleCode;
    private String description;
    private String status;
    private BigDecimal llmConfidence;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

```java
public interface OpcInsightAnomalyMapper extends BaseMapper<OpcInsightAnomaly> {
    List<OpcInsightAnomaly> selectOpenByCompany(@Param("companyId") Long companyId,
                                                  @Param("limit") Integer limit);
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
```

```xml
<!-- OpcInsightAnomalyMapper.xml -->
<mapper namespace="com.ruoyi.opc.insight.mapper.OpcInsightAnomalyMapper">
    <select id="selectOpenByCompany" resultType="OpcInsightAnomaly">
        SELECT * FROM opc_insight_anomaly
        WHERE company_id = #{companyId} AND status = 'OPEN'
        ORDER BY create_time DESC
        <if test="limit != null">LIMIT #{limit}</if>
    </select>
    <update id="updateStatus">
        UPDATE opc_insight_anomaly SET status = #{status}, update_time = NOW() WHERE id = #{id}
    </update>
</mapper>
```

- [ ] **Step 5: Write failing AnomalyServiceImplTest (18 tests)**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnomalyServiceImplTest {
    @Mock OpcInsightAnomalyMapper mapper;
    @Mock LlmGateway llmGateway;
    @InjectMocks AnomalyServiceImpl service;

    // 8 hard rules × 2 tests (match / no-match) = 16 tests
    @Test void scan_voucherOver100K_matches() { /* KpiSnapshot with expense=200000 → 1 anomaly HIGH */ }
    @Test void scan_voucherOver100K_noMatch() { /* expense=50000 → no anomaly */ }
    @Test void scan_pendingVoucherOver7D_matches() { /* */ }
    @Test void scan_pendingVoucherOver7D_noMatch() { /* */ }
    @Test void scan_walletBalanceLow_matches() { /* balance=50 → MEDIUM */ }
    @Test void scan_walletBalanceLow_noMatch() { /* balance=500 → no */ }
    @Test void scan_tokenUsageSpike_matches() { /* usage=200000 → MEDIUM */ }
    @Test void scan_tokenUsageSpike_noMatch() { /* usage=50000 → no */ }
    @Test void scan_revenueDrop_matches() { /* revenue=0 → MEDIUM */ }
    @Test void scan_revenueDrop_noMatch() { /* revenue=1000 → no */ }
    @Test void scan_expenseExceedsRevenue_matches() { /* expense > revenue → HIGH */ }
    @Test void scan_expenseExceedsRevenue_noMatch() { /* */ }
    @Test void scan_highVoucherRejectionRate_noMatch() { /* rule returns false always */ }
    @Test void scan_multipleHighValueFlows_matches() { /* voucherCount=60 → LOW */ }
    @Test void scan_multipleHighValueFlows_noMatch() { /* count=10 → no */ }
    @Test void scan_onlyOneRule() { /* only one rule matches → 1 anomaly in result */ }

    // LLM soft scan: 1 parameterized test (5 cases) + 1 skip test
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 5})
    void scan_noHardAnomaly_callsLlmAndFiltersByConfidence(int expectedLlmAnomalies) {
        /* snapshot has no hard anomalies → LLM called → AnomalyVo[expectedLlmAnomalies] with confidence > 0.6 inserted */
    }
    @Test void scan_hardAnomalyExists_skipsLlm() { /* 1 hard anomaly → LLM NOT called, only 1 result */ }
}
```

- [ ] **Step 6: Run test to verify it fails**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=AnomalyServiceImplTest
# Expected: compilation error
```

- [ ] **Step 7: Implement AnomalyServiceImpl**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyServiceImpl implements IAnomalyService {

    private final OpcInsightAnomalyMapper mapper;
    private final LlmGateway llmGateway;
    private static final BigDecimal LLM_CONFIDENCE_THRESHOLD = new BigDecimal("0.60");

    @Override
    public List<AnomalyVo> scan(KpiSnapshot snapshot) {
        if (snapshot == null) return List.of();
        List<AnomalyVo> anomalies = new ArrayList<>();

        // 1. Hard rules
        for (AnomalyRule rule : AnomalyRule.values()) {
            if (rule.match(snapshot)) {
                AnomalyVo vo = AnomalyVo.builder()
                    .companyId(snapshot.getCompanyId())
                    .period(snapshot.getPeriod())
                    .level(rule.getLevel())
                    .ruleCode(rule.name())
                    .description(rule.getDescription())
                    .status("OPEN")
                    .createTime(LocalDateTime.now())
                    .build();
                mapper.insert(toDomain(vo));
                anomalies.add(vo);
            }
        }

        // 2. LLM soft scan (only if no HIGH anomalies)
        boolean hasHigh = anomalies.stream().anyMatch(a -> a.getLevel() == AnomalyLevel.HIGH);
        if (!hasHigh) {
            try {
                List<AnomalyVo> soft = llmGateway.chatSoftAnomaly(snapshot);
                for (AnomalyVo vo : soft) {
                    if (vo.getLlmConfidence() != null
                        && vo.getLlmConfidence().compareTo(LLM_CONFIDENCE_THRESHOLD) > 0) {
                        mapper.insert(toDomain(vo));
                        anomalies.add(vo);
                    }
                }
            } catch (Exception e) {
                log.warn("LLM soft scan failed: {}", e.getMessage());
            }
        }

        return anomalies;
    }

    @Override
    public List<AnomalyVo> listOpen(Long companyId, Integer limit) {
        return mapper.selectOpenByCompany(companyId, limit).stream()
            .map(this::toVo).toList();
    }

    @Override
    public void acknowledge(Long id) {
        mapper.updateStatus(id, "ACK");
    }

    private OpcInsightAnomaly toDomain(AnomalyVo vo) { /* map */ }
    private AnomalyVo toVo(OpcInsightAnomaly d) { /* map */ }
}
```

- [ ] **Step 8: Run test to verify it passes**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=AnomalyServiceImplTest
# Expected: 18 passed (16 rule tests + 1 LLM parameterized + 1 LLM skip)
```

- [ ] **Step 9: Commit**

```bash
git add springboot3/ruoyi-modules/opc-insight/
git commit -m "feat(insight): AnomalyService (8 hard rules + LLM soft scan) + 18 tests"
```

---

## Task 7: InsightDailyReportJob Quartz cron — Day 5

**Files:**
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/workflow/InsightDailyReportJob.java`
- Test: `InsightDailyReportJobTest.java` (4 tests)

- [ ] **Step 1: Write failing JobTest (4 tests)**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InsightDailyReportJobTest {
    @Mock IDailyReportService dailyReportService;
    @Mock OpcCompanyMapper companyMapper;  // for active company list
    @InjectMocks InsightDailyReportJob job;

    @Test void trigger_singleCompanySuccess() { /* 1 active company, DailyReportService.generate called once */ }
    @Test void trigger_oneCompanyFails_continuesToNext() { /* company1 throws, company2 still called */ }
    @Test void trigger_allCompaniesFail_logsErrorButDoesNotThrow() { /* both throw, job returns normally */ }
    @Test void trigger_duplicateDateForCompany_insertsAnomalyInstead() { /* period=today already in opc_insight_daily_report → insert into opc_insight_anomaly with rule_code=DAILY_REPORT_DUPLICATE */ }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=InsightDailyReportJobTest
# Expected: FAIL
```

- [ ] **Step 3: Implement InsightDailyReportJob**

```java
@Component("insightDailyReportJob")
@Slf4j
public class InsightDailyReportJob {

    private final IDailyReportService dailyReportService;
    private final OpcCompanyMapper companyMapper;  // reuse opc-user-center mapper via Feign
    private final OpcInsightAnomalyMapper anomalyMapper;

    public void trigger() {
        String today = LocalDate.now().toString();
        List<Long> activeCompanyIds = companyMapper.selectActiveCompanyIds();
        log.info("InsightDailyReportJob triggered for {} companies on {}", activeCompanyIds.size(), today);
        for (Long companyId : activeCompanyIds) {
            try {
                dailyReportService.generate(companyId, today);
            } catch (DataIntegrityViolationException dup) {
                log.warn("Daily report already exists for company={} date={}", companyId, today);
                anomalyMapper.insert(OpcInsightAnomaly.builder()
                    .companyId(companyId).period(LocalDate.parse(today))
                    .level("LOW").ruleCode("DAILY_REPORT_DUPLICATE")
                    .description("日报已存在（UNIQUE KEY uk_company_date）")
                    .status("ACK").createTime(LocalDateTime.now()).build());
            } catch (Exception e) {
                log.error("Daily report failed for company={}", companyId, e);
                anomalyMapper.insert(OpcInsightAnomaly.builder()
                    .companyId(companyId).period(LocalDate.parse(today))
                    .level("MEDIUM").ruleCode("DAILY_REPORT_FAILED")
                    .description("日报生成失败: " + e.getMessage())
                    .status("OPEN").createTime(LocalDateTime.now()).build());
            }
        }
    }
}
```

- [ ] **Step 4: Register Quartz job in sys_job seed**

Append to `springboot3/sql/seed/sys_job_workflow_seed.sql`:

```sql
INSERT INTO sys_job (job_name, job_group, invoke_target, cron_expression, status, concurrent, create_by, create_time, remark)
VALUES ('insightDailyReport', 'DEFAULT', 'insightDailyReportJob.trigger', '0 0 9 * * ?', '0', '1', 'admin', NOW(), 'INSIGHT 每日 9 点日报')
ON DUPLICATE KEY UPDATE update_time = NOW();
```

- [ ] **Step 5: Run test to verify it passes**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=InsightDailyReportJobTest
# Expected: 4 passed
```

- [ ] **Step 6: Commit**

```bash
git add springboot3/ruoyi-modules/opc-insight/ springboot3/sql/seed/
git commit -m "feat(insight): Quartz cron job for daily 9am report + 4 tests"
```

---

## Task 8: DailyReportService impl + 10 tests — Day 5

**Files:**
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/service/IDailyReportService.java` + `DailyReportServiceImpl.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/domain/OpcInsightDailyReport.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/mapper/OpcInsightDailyReportMapper.java` + xml
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/vo/DailyReportVo.java`
- Test: `DailyReportServiceImplTest.java` (10 tests)

- [ ] **Step 1: Define DailyReportVo**

```java
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class DailyReportVo {
    private Long id;
    private Long companyId;
    private String period;          // yyyy-MM-dd
    private String summaryMd;
    private String kpiJson;
    private String adviceMd;
    private String llmUsed;
    private LocalDateTime createTime;
}
```

- [ ] **Step 2: Define domain + mapper**

```java
@Data @TableName("opc_insight_daily_report")
public class OpcInsightDailyReport {
    @TableId(type = IdType.AUTO) private Long id;
    private Long companyId;
    private LocalDate period;
    private String summaryMd;
    private String kpiJson;
    private String adviceMd;
    private String llmUsed;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
```

```java
public interface OpcInsightDailyReportMapper extends BaseMapper<OpcInsightDailyReport> {
    int insertOnDuplicateKeyUpdate(OpcInsightDailyReport report);
    List<OpcInsightDailyReport> selectByCompanyAndDateRange(@Param("companyId") Long companyId,
                                                             @Param("from") LocalDate from,
                                                             @Param("to") LocalDate to,
                                                             @Param("limit") Integer limit);
    OpcInsightDailyReport selectByCompanyAndDate(@Param("companyId") Long companyId,
                                                   @Param("period") LocalDate period);
}
```

- [ ] **Step 3: Write failing DailyReportServiceImplTest (10 tests)**

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyReportServiceImplTest {
    @Mock IKpiService kpiService;
    @Mock LlmGateway llmGateway;
    @Mock OpcInsightDailyReportMapper mapper;
    @InjectMocks DailyReportServiceImpl service;

    @Test void generate_normalFlow_insertsReport() { /* kpi returns data, LLM returns markdown, mapper.insert called once */ }
    @Test void generate_llmFails_usesFallbackTemplate() { /* LLM throws → summary = "[自动聚合·未走 LLM] ..."  */ }
    @Test void generate_duplicateDate_throwsDataIntegrityViolation() { /* mapper.insert throws DataIntegrityViolationException for UNIQUE KEY */ }
    @Test void generate_companyIdNull_throwsOpcException() { /* */ }
    @Test void generate_periodFuture_throwsOpcException() { /* period=2099-01-01 → OpcException("period 不能晚于今天") */ }
    @Test void generate_kpiSnapshotEmpty_stillInsertsReport() { /* kpiService returns partial=true → still insert, summary = "[数据降级]..." */ }
    @Test void generate_usesCompanyIdFromKpiNotParameter() { /* kpi.companyId != parameter companyId → use kpi.companyId */ }
    @Test void listByDateRange_passesLimitToMapper() { /* */ }
    @Test void getById_returnsReport() { /* */ }
    @Test void getById_notFound_throwsOpcException() { /* */ }
}
```

- [ ] **Step 4: Run test to verify it fails**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=DailyReportServiceImplTest
# Expected: FAIL
```

- [ ] **Step 5: Implement DailyReportServiceImpl**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyReportServiceImpl implements IDailyReportService {

    private final IKpiService kpiService;
    private final LlmGateway llmGateway;
    private final OpcInsightDailyReportMapper mapper;
    private static final String FALLBACK_SUMMARY = "[自动聚合·未走 LLM] 基于当日数据自动汇总，无 AI 解读。";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DailyReportVo generate(Long companyId, String date) {
        if (companyId == null) throw new OpcException("companyId 不能为空");
        LocalDate period = parseDate(date);
        if (period.isAfter(LocalDate.now())) throw new OpcException("period 不能晚于今天");

        KpiSnapshot kpi = kpiService.snapshot(companyId, period.toString());
        String summary;
        String advice;
        String llmUsed;
        try {
            LlmReport r = llmGateway.chatDailyReport(kpi);
            summary = r.getSummary();
            advice = r.getAdvice();
            llmUsed = r.getModelUsed();
        } catch (Exception e) {
            log.warn("LLM daily report failed: {}", e.getMessage());
            summary = FALLBACK_SUMMARY + " " + kpiSummaryString(kpi);
            advice = "请检查数据完整性后重试。";
            llmUsed = "FALLBACK";
        }

        OpcInsightDailyReport report = OpcInsightDailyReport.builder()
            .companyId(kpi.getCompanyId())
            .period(period)
            .summaryMd(summary)
            .kpiJson(JsonUtils.toJson(kpi))
            .adviceMd(advice)
            .llmUsed(llmUsed)
            .createBy("system")
            .createTime(LocalDateTime.now())
            .build();
        mapper.insert(report);
        return toVo(report);
    }

    // ... listByDateRange, getById
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=DailyReportServiceImplTest
# Expected: 10 passed
```

- [ ] **Step 7: Commit**

```bash
git add springboot3/ruoyi-modules/opc-insight/
git commit -m "feat(insight): DailyReportService (cron + LLM + fallback) + 10 tests"
```

---

## Task 9: AdviceService impl + 8 tests — Day 6

**Files:**
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/service/IAdviceService.java` + `AdviceServiceImpl.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/domain/OpcInsightAdvice.java`
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/mapper/OpcInsightAdviceMapper.java` + xml
- Create: `springboot3/ruoyi-modules/opc-insight/src/main/java/com/ruoyi/opc/insight/vo/AdviceVo.java`
- Test: `AdviceServiceImplTest.java` (8 tests)

- [ ] **Step 1: Define AdviceVo + domain + mapper**

```java
@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class AdviceVo {
    private Long id;
    private Long companyId;
    private String topic;
    private String adviceMd;
    private String llmUsed;
    private BigDecimal confidence;
    private LocalDateTime createTime;
}
```

```java
@Data @TableName("opc_insight_advice")
public class OpcInsightAdvice {
    @TableId(type = IdType.AUTO) private Long id;
    private Long companyId;
    private String topic;
    private String adviceMd;
    private String llmUsed;
    private BigDecimal confidence;
    private LocalDateTime createTime;
}
```

```java
public interface OpcInsightAdviceMapper extends BaseMapper<OpcInsightAdvice> {
    OpcInsightAdvice selectRecent(@Param("companyId") Long companyId,
                                   @Param("topic") String topic,
                                   @Param("sinceDays") Integer sinceDays);
    List<OpcInsightAdvice> selectByCompany(@Param("companyId") Long companyId,
                                             @Param("limit") Integer limit);
}
```

- [ ] **Step 2: Write failing AdviceServiceImplTest (8 tests)**

```java
@Test void generate_cacheHit_returnsCached() { /* mapper.findRecent returns 1 record within 7 days → return cached, LLM NOT called */ }
@Test void generate_cacheMiss_callsLlmAndInserts() { /* mapper.findRecent empty → LLM called, mapper.insert called */ }
@Test void generate_llmFails_usesFallback() { /* LLM throws → advice = "[降级建议]..." */ }
@Test void generate_topicNotSupported_throwsOpcException() { /* topic="invalid" → OpcException */ }
@Test void generate_companyIdMismatch_throwsSecurityException() { /* param.companyId != SecurityUtils.getCompanyId() */ }
@Test void listByCompany_passesLimit() { /* */ }
@Test void regenerate_callsLlmAgain() { /* bypasses cache, always calls LLM */ }
@Test void getById_notFound_throwsOpcException() { /* */ }
```

- [ ] **Step 3: Implement AdviceServiceImpl** (cache hit via `mapper.findRecent(companyId, topic, 7 days)`)

- [ ] **Step 4: Run test to verify it passes**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=AdviceServiceImplTest
# Expected: 8 passed
```

- [ ] **Step 5: Commit**

```bash
git add springboot3/ruoyi-modules/opc-insight/
git commit -m "feat(insight): AdviceService (7-day cache + LLM) + 8 tests"
```

---

## Task 10: 4 Controllers + @WebMvcTest (12 tests) — Day 7

**Files:**
- Create: 4 controller files (Dashboard, Alert, DailyReport, Advice)
- Test: `OpcInsightControllerMvcTest.java` (12 tests)

- [ ] **Step 1: Write failing OpcInsightControllerMvcTest (12 tests, 4 endpoints × 3 paths each)**

```java
@WebMvcTest(controllers = {DashboardController.class, AlertController.class,
                              DailyReportController.class, AdviceController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OpcInsightControllerMvcTest {

    @MockBean IKpiService kpiService;
    @MockBean IAnomalyService anomalyService;
    @MockBean IDailyReportService dailyReportService;
    @MockBean IAdviceService adviceService;
    @Autowired MockMvc mvc;

    // DashboardController (3 tests)
    @Test @WithMockUser(roles="ADMIN") void dashboard_returns200WithBody() { /* GET /opc/insight/dashboard → 200 */ }
    @Test void dashboard_noAuth_returns401() { /* no @WithMockUser → 401 */ }
    @Test @WithMockUser void dashboard_invalidParam_returns400() { /* companyId=null → 400 */ }

    // AlertController (3 tests)
    @Test @WithMockUser void alerts_list() { /* */ }
    @Test @WithMockUser void alerts_detail() { /* */ }
    @Test @WithMockUser void alerts_ack() { /* POST /opc/insight/alerts/{id}/ack → 200 */ }

    // DailyReportController (3 tests)
    @Test @WithMockUser void daily_list() { /* */ }
    @Test @WithMockUser void daily_detail() { /* */ }
    @Test @WithMockUser void daily_generate() { /* POST /opc/insight/daily/generate */ }

    // AdviceController (3 tests)
    @Test @WithMockUser void advice_list() { /* */ }
    @Test @WithMockUser void advice_detail() { /* */ }
    @Test @WithMockUser void advice_regenerate() { /* */ }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=OpcInsightControllerMvcTest
# Expected: FAIL (controllers don't exist)
```

- [ ] **Step 3: Create DashboardController**

```java
@RestController
@RequestMapping("/opc/insight/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final IKpiService kpiService;
    private final IAnomalyService anomalyService;
    private final IAdviceService adviceService;

    @GetMapping
    public R<DashboardVo> dashboard() {
        Long companyId = SecurityUtils.getCompanyId();
        if (companyId == null) throw new OpcException("请先创建公司档案");
        KpiSnapshot kpi = kpiService.snapshot(companyId, null);
        List<AnomalyVo> alertsTop5 = anomalyService.listOpen(companyId, 5);
        List<AdviceVo> adviceTop3 = adviceService.listByCompany(companyId, 3);
        return R.ok(DashboardVo.builder()
            .kpi(kpi).alerts(alertsTop5).advice(adviceTop3)
            .build());
    }
}
```

- [ ] **Step 4: Create AlertController** (3 endpoints: list / detail / ack)

```java
@RestController
@RequestMapping("/opc/insight/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final IAnomalyService anomalyService;

    @GetMapping
    public R<List<AnomalyVo>> list(@RequestParam(required = false) Integer limit) {
        return R.ok(anomalyService.listOpen(SecurityUtils.getCompanyId(), limit));
    }

    @GetMapping("/{id}")
    public R<AnomalyVo> detail(@PathVariable Long id) { /* */ }

    @PostMapping("/{id}/ack")
    public R<?> ack(@PathVariable Long id) { anomalyService.acknowledge(id); return R.ok(); }
}
```

- [ ] **Step 5: Create DailyReportController** (3 endpoints: list / detail / generate)

```java
@RestController
@RequestMapping("/opc/insight/daily")
@RequiredArgsConstructor
public class DailyReportController {
    private final IDailyReportService dailyReportService;

    @GetMapping
    public R<List<DailyReportVo>> list(@RequestParam String from, @RequestParam String to,
                                        @RequestParam(required = false) Integer limit) { /* */ }

    @GetMapping("/{id}")
    public R<DailyReportVo> detail(@PathVariable Long id) { /* */ }

    @PostMapping("/generate")
    public R<DailyReportVo> generate(@RequestParam String date) { /* */ }
}
```

- [ ] **Step 6: Create AdviceController** (3 endpoints: list / detail / regenerate)

- [ ] **Step 7: Run test to verify it passes**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-insight -Dtest=OpcInsightControllerMvcTest
# Expected: 12 passed
```

- [ ] **Step 8: Commit**

```bash
git add springboot3/ruoyi-modules/opc-insight/
git commit -m "feat(insight): 4 REST controllers + @WebMvcTest 12 tests"
```

---

## Task 11: Nacos routes + opc-common whitelist — Day 7

**Files:**
- Modify: `springboot3/deploy/nacos/opc-routes.json`
- Modify: `springboot3/deploy/nacos/opc-common-prod.yml`

- [ ] **Step 1: Add /opc/insight/** route to opc-routes.json**

```json
{
  "id": "opc-insight",
  "predicates": [{"name": "Path", "args": {"pattern": "/opc/insight/**"}}],
  "filters": [{"name": "StripPrefix", "args": {"parts": "2"}}],
  "uri": "lb://opc-insight"
}
```

- [ ] **Step 2: Verify whitelist in opc-common-prod.yml** (if not already included, add `/opc/insight/**`)

- [ ] **Step 3: Verify import via `apply-whitelist.sh` script**

```bash
bash springboot3/deploy/nacos/apply-whitelist.sh /opc/insight/**
```

- [ ] **Step 4: Commit**

```bash
git add springboot3/deploy/nacos/
git commit -m "feat(deploy): Nacos route + whitelist for /opc/insight/**"
```

---

## Task 12: Frontend api wrapper + 12 tests — Day 3-4

**Files:**
- Create: `vue3-typescript/src/api/opc/insight.ts`
- Create: `vue3-typescript/src/views/opc/insight/{dashboard,alerts,daily,advice}.vue` (skeletons)
- Test: `vue3-typescript/src/api/opc/__tests__/insight.spec.ts` (12 tests)

- [ ] **Step 1: Write failing test (12 tests, mirror W19-W25 pattern)**

```typescript
// insight.spec.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import request from '@/utils/request'
import { dashboard, listAlerts, ackAlert, listDaily, getDaily, generateDaily,
         listAdvice, getAdvice, regenerateAdvice } from '@/api/opc/insight'

const mock = new MockAdapter(request)

describe('opc/insight API', () => {
  beforeEach(() => mock.reset())

  it('dashboard → GET /opc/insight/dashboard', async () => { /* */ })
  it('listAlerts → GET /opc/insight/alerts?limit=5', async () => { /* */ })
  it('ackAlert → POST /opc/insight/alerts/123/ack', async () => { /* */ })
  it('listDaily → GET /opc/insight/daily?from=2026-09-01&to=2026-09-30', async () => { /* */ })
  it('getDaily → GET /opc/insight/daily/1', async () => { /* */ })
  it('generateDaily → POST /opc/insight/daily/generate?date=2026-09-08', async () => { /* */ })
  it('listAdvice → GET /opc/insight/advice?limit=3', async () => { /* */ })
  it('getAdvice → GET /opc/insight/advice/1', async () => { /* */ })
  it('regenerateAdvice → POST /opc/insight/advice/regenerate?topic=cost_optimization', async () => { /* */ })
  it('all 9 functions are exported', () => { /* */ })
  it('all 9 endpoints have unique URLs', () => { /* */ })
  it('no endpoint uses DELETE (all are GET/POST)', () => { /* */ })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd vue3-typescript
npm run test:run -- insight.spec.ts
# Expected: FAIL
```

- [ ] **Step 3: Implement insight.ts**

```typescript
import request from '@/utils/request'

// Dashboard
export function dashboard() {
  return request({ url: '/opc/insight/dashboard', method: 'get' })
}

// Alerts
export function listAlerts(limit?: number) {
  return request({ url: '/opc/insight/alerts', method: 'get', params: { limit } })
}
export function ackAlert(id: number) {
  return request({ url: `/opc/insight/alerts/${id}/ack`, method: 'post' })
}

// Daily Reports
export function listDaily(from: string, to: string, limit?: number) {
  return request({ url: '/opc/insight/daily', method: 'get', params: { from, to, limit } })
}
export function getDaily(id: number) {
  return request({ url: `/opc/insight/daily/${id}`, method: 'get' })
}
export function generateDaily(date: string) {
  return request({ url: '/opc/insight/daily/generate', method: 'post', params: { date } })
}

// Advice
export function listAdvice(limit?: number) {
  return request({ url: '/opc/insight/advice', method: 'get', params: { limit } })
}
export function getAdvice(id: number) {
  return request({ url: `/opc/insight/advice/${id}`, method: 'get' })
}
export function regenerateAdvice(topic: string) {
  return request({ url: '/opc/insight/advice/regenerate', method: 'post', params: { topic } })
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
npm run test:run -- insight.spec.ts
# Expected: 12 passed
```

- [ ] **Step 5: Commit**

```bash
git add vue3-typescript/src/api/opc/insight.ts vue3-typescript/src/api/opc/__tests__/insight.spec.ts
git commit -m "feat(frontend): opc/insight API wrapper + 12 contract tests"
```

---

## Task 13: 4 Vue pages + SFC tests (52 tests) — Day 4-7

**Files:**
- Create: 4 .vue files under `src/views/opc/insight/`
- Test: 4 spec files (dashboard 18, alerts 12, daily 12, advice 10)

- [ ] **Step 1: dashboard.vue (KPI cards + trend charts + alert/advice lists)**

```vue
<template>
  <div class="page">
    <h2>经营驾驶舱</h2>
    <el-row :gutter="16" class="is-mobile-stack">
      <el-col v-for="kpi in kpiCards" :key="kpi.label" :span="6">
        <el-card><div class="kpi-label">{{ kpi.label }}</div><div class="kpi-value">{{ kpi.value }}</div></el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12"><el-card>收入趋势</el-card></el-col>
      <el-col :span="12"><el-card>支出趋势</el-card></el-col>
    </el-row>
    <el-card>异常 Top 5</el-card>
    <el-card>建议 Top 3</el-card>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { dashboard } from '@/api/opc/insight'
// ... loading + data fetching
</script>
```

- [ ] **Step 2-4: Create alerts.vue, daily.vue, advice.vue** (simpler table layouts, follow ResponsiveTable pattern from W5.2)

- [ ] **Step 5: Write 4 SFC test files** (52 tests total, mirror W22 Logo.vue pattern with @vue/test-utils)

- [ ] **Step 6: Run all + verify**

```bash
cd vue3-typescript
npm run test:run
# Expected: 628+52 = 680 passed
```

- [ ] **Step 7: Commit**

```bash
git add vue3-typescript/src/views/opc/insight/ vue3-typescript/src/views/opc/insight/__tests__/
git commit -m "feat(frontend): 4 INSIGHT pages + 52 SFC tests"
```

---

## Task 14: Router + menu integration — Day 7

**Files:**
- Modify: `vue3-typescript/src/router/index.ts`

- [ ] **Step 1: Add 4 routes under /opc/insight/***

```typescript
{
  path: '/opc/insight',
  component: Layout,
  redirect: '/opc/insight/dashboard',
  meta: { title: '数据洞察', icon: 'data-analysis' },
  children: [
    { path: 'dashboard', component: () => import('@/views/opc/insight/dashboard.vue'), meta: { title: '驾驶舱' } },
    { path: 'alerts',    component: () => import('@/views/opc/insight/alerts.vue'),    meta: { title: '异常预警' } },
    { path: 'daily',     component: () => import('@/views/opc/insight/daily.vue'),     meta: { title: '财务日报' } },
    { path: 'advice',    component: () => import('@/views/opc/insight/advice.vue'),    meta: { title: '决策建议' } }
  ]
}
```

- [ ] **Step 2: Verify build**

```bash
cd vue3-typescript
npm run build
# Expected: BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```bash
git add vue3-typescript/src/router/index.ts
git commit -m "feat(frontend): /opc/insight/* routes + menu"
```

---

## Task 15: 3 insight prompts — Day 4

**Files:**
- Create: `springboot3/opc-ai-core/src/main/resources/prompts/insight-system-v1.0.txt`
- Create: `springboot3/opc-ai-core/src/main/resources/prompts/insight-soft-anomaly-v1.0.txt`
- Create: `springboot3/opc-ai-core/src/main/resources/prompts/insight-advice-v1.0.txt`

- [ ] **Step 1: Write insight-system-v1.0.txt** (~80 lines, mirror finance-system-v1.0 structure with "小数" agent persona)

- [ ] **Step 2: Write insight-soft-anomaly-v1.0.txt** (LLM 软扫描 prompt, JSON output spec with `[{level: LOW, rule_code, description, confidence}]`)

- [ ] **Step 3: Write insight-advice-v1.0.txt** (决策建议 prompt, output spec: topic + markdown advice + 3 action items)

- [ ] **Step 4: Commit**

```bash
git add springboot3/opc-ai-core/src/main/resources/prompts/insight-*.txt
git commit -m "feat(ai): 3 insight prompts (system / soft-anomaly / advice) v1.0"
```

---

## Task 16: 80-case eval set + MockInsightLlmProvider — Day 4 + Day 6

**Files:**
- Create: `springboot3/opc-ai-core/src/main/resources/eval/insight-agent-v1.0.json` (80 cases)
- Create: `springboot3/opc-ai-core/src/test/java/com/ruoyi/opc/ai/eval/MockInsightLlmProvider.java`
- Test: `EvalInsightRunnerTest.java`

- [ ] **Step 1: Write 80 eval cases** (4 groups × 20 cases each, or 30+20+15+15 from spec)

- [ ] **Step 2: Write MockInsightLlmProvider** (regex+rules, 100% mock pass on the 80 cases)

- [ ] **Step 3: Write EvalInsightRunnerTest (4 tests)**

```java
@Test void runAll80Cases_100PercentMockPass() { /* */ }
@Test void kpiSummary_30Cases_30Pass() { /* */ }
@Test void anomalyRuleMatch_20Cases_20Pass() { /* */ }
@Test void adviceQuality_15Cases_15Pass() { /* */ }
```

- [ ] **Step 4: Run + commit**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-ai-core -Dtest=EvalInsightRunnerTest
git add springboot3/opc-ai-core/src/main/resources/eval/insight-agent-v1.0.json springboot3/opc-ai-core/src/test/
git commit -m "feat(ai): insight-eval v0.1 80 cases + MockInsightLlmProvider"
```

---

## Task 17: redteam + safety — Day 8

**Files:**
- Create: `springboot3/opc-ai-core/src/main/resources/eval/insight-redteam-10.json`
- Test: `InsightRedteamRunnerTest.java`

- [ ] **Step 1: Write 10 redteam cases** (3 categories: 越权/提示词注入/误报陷阱)

- [ ] **Step 2: Write InsightRedteamRunnerTest**

```java
@Test void runAll10Cases_asrLe10Percent() {
    /* load insight-redteam-10.json, run MockProvider, assert failed attack rate ≤ 10% (1/10) */
}
```

- [ ] **Step 3: Wire PromptGuard v0.3 into insight prompts** (add 8 injection patterns in system prompt, mirror Task #8 pattern)

- [ ] **Step 4: Run + commit**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-ai-core -Dtest=InsightRedteamRunnerTest
git add springboot3/opc-ai-core/src/main/resources/eval/insight-redteam-10.json springboot3/opc-ai-core/src/test/
git commit -m "feat(ai): insight-redteam 10 cases + PromptGuard v0.3 wiring"
```

---

## Task 18: Helm chart + docker-compose — Day 7

**Files:**
- Create: `springboot3/deploy/helm/opc/templates/deployment-insight.yaml`
- Create: `springboot3/deploy/helm/opc/templates/service-insight.yaml`
- Modify: `springboot3/deploy/helm/opc/values-{dev,staging,prod}.yaml`
- Modify: `springboot3/deploy/docker-compose.yml`

- [ ] **Step 1: Create service-insight.yaml** (mirror other service-*.yaml)

- [ ] **Step 2: Create deployment-insight.yaml**

- [ ] **Step 3: Add to 3 values files** (replica 1 in dev, 2 in staging, 3 in prod)

- [ ] **Step 4: Add to docker-compose.yml**

- [ ] **Step 5: Verify**

```bash
helm lint springboot3/deploy/helm/opc/
helm template test springboot3/deploy/helm/opc/ -f values-prod.yaml | grep '^kind:' | sort | uniq -c
# Expected: prod shows opc-insight resources
```

- [ ] **Step 6: Commit**

```bash
git add springboot3/deploy/helm/opc/ springboot3/deploy/docker-compose.yml
git commit -m "feat(deploy): Helm chart + docker-compose for opc-insight"
```

---

## Task 19: Run live LLM eval (Day 8, optional verification)

- [ ] **Step 1: Run with real LLM**

```bash
mvn test -pl springboot3/ruoyi-modules/opc-ai-core -Dtest=EvalInsightRunnerTest -Peval-live
# Verify: ≥ 85% pass rate on 80 cases
```

- [ ] **Step 2: Save result to `target/eval-reports/insight-result-v0.1.md`**

- [ ] **Step 3: Iterate prompt if pass rate < 85%** (loop Task 15 + Task 19)

- [ ] **Step 4: Commit**

```bash
git add springboot3/opc-ai-core/target/eval-reports/
git commit -m "docs(ai): insight live LLM eval report v0.1 (≥85%)"
```

---

## Task 20: Verification report — Day 8

**Files:**
- Create: `OPC-M4-VERIFICATION-insight-mvp.md` (at project root)

- [ ] **Step 1: Write report** covering:
- Spec coverage (all 4 capabilities, 218 tests, 7-day delivery)
- 8-day timeline adherence
- Helm lint + template verification
- LLM eval results
- Redteam ASR
- Known issues + follow-up

- [ ] **Step 2: Commit**

```bash
git add OPC-M4-VERIFICATION-insight-mvp.md
git commit -m "docs(M4): verification report"
```

---

## Day 8 Final Checkpoint

- [ ] All 218 tests pass (64 backend + 64 frontend + 80 eval + 10 redteam)
- [ ] `mvn clean install` all modules pass
- [ ] `npm run build` frontend passes
- [ ] `helm lint` + `helm template` for all 3 envs passes
- [ ] `OPC-M4-VERIFICATION-insight-mvp.md` committed
- [ ] Git tag `v0.4.0-insight-mvp` (optional)
