package com.ruoyi.opc.ai.eval;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EvalRunner JUnit5 测试
 *
 * <p>通过 Maven profile 控制运行：
 * <ul>
 *   <li>默认：跑 Mock LLM（无外部依赖）</li>
 *   <li>-Peval-live：跑真实 LLM（需配置 OPENAI_API_KEY 等环境变量）</li>
 * </ul>
 *
 * <p>运行命令：
 * <pre>
 *   mvn test -pl opc-ai-core -Dtest=EvalRunnerTest
 * </pre>
 *
 * @author OAC
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EvalRunnerTest {

    private static final String EVAL_JSON = "classpath:eval/finance-agent-v2.0.json";
    private static final String REPORT_DIR = System.getProperty("user.dir") + "/target/eval-reports";

    @BeforeAll
    static void setup() {
        new File(REPORT_DIR).mkdirs();
    }

    /**
     * 1. v0.1 baseline 跑分
     */
    @Test
    @Order(1)
    @DisplayName("v0.1 baseline — Mock LLM")
    void testV1Baseline() throws Exception {
        log.info("==== Running v0.1 baseline ====");
        MockFinanceLlmProvider provider = new MockFinanceLlmProvider("v0.1", false);
        EvalReportWriter.EvalReport report = EvalRunner.run(EVAL_JSON, provider, "v0.1");
        EvalReportWriter.write(report, REPORT_DIR + "/eval-baseline-v0.1.md");
        log.info("Baseline pass rate: {}", String.format("%.1f%%", report.passRate * 100));

        // baseline 不强求通过率（只是记录现状）
        assertTrue(report.total > 0, "Total cases > 0");
        assertTrue(report.passRate < 1.0, "Baseline 不应 100% 通过，否则说明 mock 写得太完美");
    }

    /**
     * 2. v0.2 改进版跑分（目标 ≥85%）
     */
    @Test
    @Order(2)
    @DisplayName("v0.2 改进版 — Mock LLM（目标 ≥85%）")
    void testV2Improved() throws Exception {
        log.info("==== Running v0.2 improved ====");
        MockFinanceLlmProvider provider = new MockFinanceLlmProvider("v0.2", true);
        EvalReportWriter.EvalReport report = EvalRunner.run(EVAL_JSON, provider, "v0.2");
        EvalReportWriter.write(report, REPORT_DIR + "/eval-result-v0.2.md");
        log.info("v0.2 pass rate: {}", String.format("%.1f%%", report.passRate * 100));

        // v0.2 目标 ≥ 85%
        assertTrue(report.passRate >= 0.85,
                String.format("v0.2 通过率应 ≥ 85%%, 实际 %.1f%%", report.passRate * 100));
    }

    /**
     * 3. 按难度分组检查（hard 场景至少 70%）
     */
    @Test
    @Order(3)
    @DisplayName("v0.2 困难场景通过率 ≥ 70%")
    void testHardScenarios() throws Exception {
        MockFinanceLlmProvider provider = new MockFinanceLlmProvider("v0.2", true);
        EvalReportWriter.EvalReport report = EvalRunner.run(EVAL_JSON, provider, "v0.2");

        EvalReportWriter.Bucket hardBucket = report.byDifficulty.get("HARD");
        assertNotNull(hardBucket, "应至少有 HARD 用例");
        assertTrue(hardBucket.total > 0);
        double hardRate = (double) hardBucket.passed / hardBucket.total;
        log.info("HARD pass rate: {}", String.format("%.1f%% (%d/%d)", hardRate * 100, hardBucket.passed, hardBucket.total));
        assertTrue(hardRate >= 0.70,
                String.format("HARD 场景应 ≥ 70%%, 实际 %.1f%%", hardRate * 100));
    }

    /**
     * 4. 陷阱场景通过率 ≥ 75%
     */
    @Test
    @Order(4)
    @DisplayName("v0.2 陷阱场景通过率 ≥ 75%")
    void testTrapScenarios() throws Exception {
        MockFinanceLlmProvider provider = new MockFinanceLlmProvider("v0.2", true);
        EvalReportWriter.EvalReport report = EvalRunner.run(EVAL_JSON, provider, "v0.2");

        EvalReportWriter.TagBucket trapBucket = report.byTag.stream()
                .filter(t -> "陷阱".equals(t.tag))
                .findFirst().orElse(null);
        if (trapBucket == null) {
            log.warn("未找到'陷阱' tag 的用例");
            return;
        }
        double trapRate = (double) trapBucket.passed / trapBucket.total;
        log.info("Trap pass rate: {}", String.format("%.1f%% (%d/%d)", trapRate * 100, trapBucket.passed, trapBucket.total));
        assertTrue(trapRate >= 0.75,
                String.format("陷阱场景应 ≥ 75%%, 实际 %.1f%%", trapRate * 100));
    }

}
