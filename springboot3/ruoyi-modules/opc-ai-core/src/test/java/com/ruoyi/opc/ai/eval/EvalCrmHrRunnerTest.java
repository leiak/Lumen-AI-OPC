package com.ruoyi.opc.ai.eval;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRM/HR Agent 评测 Runner 测试 (W75-B)
 *
 * <p>覆盖 W75-B 新增的 130 条评测用例:
 * <ul>
 *   <li>crm-agent-v1.0.json: 50 条 (SCORE 30 + FOLLOWUP_SUGGEST 20)</li>
 *   <li>hr-agent-v1.0.json: 80 条 (JD_GENERATE 25 + RESUME_PARSE 30 + CANDIDATE_SCORE 25)</li>
 * </ul>
 *
 * <p>每个 agent 跑 1 个 "全量" + 每个 scene 跑 1 个 "分场景" 测试,共 8 个测试方法。
 *
 * <p>目标 (W75-B Roadmap): Mock 通过率 ≥ 85% (基线),真实 LLM ≥ 85% 通过 staging -Peval-live 验证。
 *
 * @author OAC
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EvalCrmHrRunnerTest {

    private static final String CRM_EVAL = "classpath:eval/crm-agent-v1.0.json";
    private static final String HR_EVAL  = "classpath:eval/hr-agent-v1.0.json";
    private static final String REPORT_DIR = System.getProperty("user.dir") + "/target/eval-reports";

    @BeforeAll
    static void setup() {
        new File(REPORT_DIR).mkdirs();
    }

    /* ============================================================
     *  CRM Agent — 全 50 条
     * ============================================================ */

    @Test
    @Order(1)
    @DisplayName("CRM 全 50 用例 — Mock 通过率 ≥ 85%")
    void crm_all50_cases_mockPassRate() throws Exception {
        List<EvalCase> cases = loadCases(CRM_EVAL);
        assertEquals(50, cases.size(), "CRM 评测集应包含 50 条用例");
        EvalResult r = runAll(cases, "crm-v1.0-baseline", MockCrmLlmProvider.MODEL_USED);
        writeReport(r, REPORT_DIR + "/crm-eval-baseline-v1.0.md", "crm-agent-v1.0.json", "CRM Agent");
        log.info("CRM eval pass rate: {}", String.format("%.1f%% (%d/%d)",
                r.passRate * 100, r.passed, r.total));
        assertTrue(r.passRate >= 0.85,
                "CRM Mock 通过率应 ≥ 85%,实际: " + String.format("%.1f%%", r.passRate * 100));
    }

    @Test
    @Order(2)
    @DisplayName("CRM SCORE 30 条 — Mock 全通过")
    void crm_score_30_cases() throws Exception {
        List<EvalCase> cases = loadCasesByScene(CRM_EVAL, "SCORE");
        assertEquals(30, cases.size(), "SCORE 应有 30 条");
        EvalResult r = runAll(cases, "crm-score", MockCrmLlmProvider.MODEL_USED);
        log.info("CRM SCORE pass rate: {}", String.format("%.1f%% (%d/%d)",
                r.passRate * 100, r.passed, r.total));
        assertEquals(30, r.passed, "SCORE 应 30/30 通过");
    }

    @Test
    @Order(3)
    @DisplayName("CRM FOLLOWUP_SUGGEST 20 条 — Mock 全通过")
    void crm_followup_20_cases() throws Exception {
        List<EvalCase> cases = loadCasesByScene(CRM_EVAL, "FOLLOWUP_SUGGEST");
        assertEquals(20, cases.size(), "FOLLOWUP_SUGGEST 应有 20 条");
        EvalResult r = runAll(cases, "crm-followup", MockCrmLlmProvider.MODEL_USED);
        log.info("CRM FOLLOWUP pass rate: {}", String.format("%.1f%% (%d/%d)",
                r.passRate * 100, r.passed, r.total));
        assertEquals(20, r.passed, "FOLLOWUP 应 20/20 通过");
    }

    /* ============================================================
     *  HR Agent — 全 80 条
     * ============================================================ */

    @Test
    @Order(4)
    @DisplayName("HR 全 80 用例 — Mock 通过率 ≥ 85%")
    void hr_all80_cases_mockPassRate() throws Exception {
        List<EvalCase> cases = loadCases(HR_EVAL);
        assertEquals(80, cases.size(), "HR 评测集应包含 80 条用例");
        EvalResult r = runAll(cases, "hr-v1.0-baseline", MockHrLlmProvider.MODEL_USED);
        writeReport(r, REPORT_DIR + "/hr-eval-baseline-v1.0.md", "hr-agent-v1.0.json", "HR Agent");
        log.info("HR eval pass rate: {}", String.format("%.1f%% (%d/%d)",
                r.passRate * 100, r.passed, r.total));
        assertTrue(r.passRate >= 0.85,
                "HR Mock 通过率应 ≥ 85%,实际: " + String.format("%.1f%%", r.passRate * 100));
    }

    @Test
    @Order(5)
    @DisplayName("HR JD_GENERATE 25 条 — Mock 全通过")
    void hr_jd_25_cases() throws Exception {
        List<EvalCase> cases = loadCasesByScene(HR_EVAL, "JD_GENERATE");
        assertEquals(25, cases.size(), "JD_GENERATE 应有 25 条");
        EvalResult r = runAll(cases, "hr-jd", MockHrLlmProvider.MODEL_USED);
        log.info("HR JD pass rate: {}", String.format("%.1f%% (%d/%d)",
                r.passRate * 100, r.passed, r.total));
        assertEquals(25, r.passed, "JD 应 25/25 通过");
    }

    @Test
    @Order(6)
    @DisplayName("HR RESUME_PARSE 30 条 — Mock 通过率 ≥ 85%")
    void hr_resume_30_cases() throws Exception {
        List<EvalCase> cases = loadCasesByScene(HR_EVAL, "RESUME_PARSE");
        assertEquals(30, cases.size(), "RESUME_PARSE 应有 30 条");
        EvalResult r = runAll(cases, "hr-resume", MockHrLlmProvider.MODEL_USED);
        log.info("HR RESUME pass rate: {}", String.format("%.1f%% (%d/%d)",
                r.passRate * 100, r.passed, r.total));
        // 简历解析容许一定失败 (mock 正则有限),但要 ≥ 85%
        assertTrue(r.passRate >= 0.85,
                "RESUME 应 ≥ 85%,实际: " + String.format("%.1f%%", r.passRate * 100));
    }

    @Test
    @Order(7)
    @DisplayName("HR CANDIDATE_SCORE 25 条 — Mock 通过率 ≥ 85%")
    void hr_candidate_25_cases() throws Exception {
        List<EvalCase> cases = loadCasesByScene(HR_EVAL, "CANDIDATE_SCORE");
        assertEquals(25, cases.size(), "CANDIDATE_SCORE 应有 25 条");
        EvalResult r = runAll(cases, "hr-candidate", MockHrLlmProvider.MODEL_USED);
        log.info("HR CANDIDATE pass rate: {}", String.format("%.1f%% (%d/%d)",
                r.passRate * 100, r.passed, r.total));
        assertTrue(r.passRate >= 0.85,
                "CANDIDATE 应 ≥ 85%,实际: " + String.format("%.1f%%", r.passRate * 100));
    }

    /* ============================================================
     *  聚合 — 130 条总览
     * ============================================================ */

    @Test
    @Order(8)
    @DisplayName("聚合 CRM+HR 130 条 — 总通过率 ≥ 85%")
    void crm_hr_aggregate_130() throws Exception {
        List<EvalCase> crmCases = loadCases(CRM_EVAL);
        List<EvalCase> hrCases = loadCases(HR_EVAL);
        List<EvalCase> all = new ArrayList<>();
        all.addAll(crmCases);
        all.addAll(hrCases);
        assertEquals(130, all.size(), "CRM+HR 合计应 130 条");

        // 复用两个 provider 不便,改成分别跑再聚合
        EvalResult crmR = runAll(crmCases, "aggregate-crm", MockCrmLlmProvider.MODEL_USED);
        EvalResult hrR  = runAll(hrCases, "aggregate-hr",  MockHrLlmProvider.MODEL_USED);
        int total = crmR.total + hrR.total;
        int passed = crmR.passed + hrR.passed;
        double rate = (double) passed / total;
        log.info("AGGREGATE pass rate: {} ({}/{})",
                String.format("%.1f%%", rate * 100), passed, total);
        assertTrue(rate >= 0.85,
                "CRM+HR 聚合应 ≥ 85%,实际: " + String.format("%.1f%%", rate * 100));
    }

    /* ============================================================
     *  引擎 + 报告
     * ============================================================ */

    private EvalResult runAll(List<EvalCase> cases, String tag, String model) {
        EvalResult r = new EvalResult();
        r.total = cases.size();
        Map<String, int[]> byScene = new LinkedHashMap<>();
        List<CaseFailure> failures = new ArrayList<>();

        MockCrmLlmProvider crmProvider = new MockCrmLlmProvider();
        MockHrLlmProvider  hrProvider  = new MockHrLlmProvider();

        for (EvalCase c : cases) {
            Map<String, Object> actual;
            try {
                String agent = c.getAgentCode() == null ? "" : c.getAgentCode();
                if (agent.startsWith("crm")) {
                    actual = crmProvider.extract(c.getInput());
                } else if (agent.startsWith("hr")) {
                    actual = hrProvider.extract(c.getInput());
                } else {
                    actual = Map.of("error", "unknown agent_code: " + agent);
                }
            } catch (Exception e) {
                actual = Map.of("error", e.getMessage());
            }
            List<String> diffs = CrmHrComparator.compare(c, actual);
            String scene = c.getScene() == null ? "UNKNOWN" : c.getScene();
            byScene.computeIfAbsent(scene, k -> new int[2])[0]++;
            if (diffs.isEmpty()) {
                r.passed++;
                byScene.get(scene)[1]++;
            } else {
                r.failed++;
                failures.add(new CaseFailure(c.getCaseCode(), scene, diffs));
            }
        }
        r.passRate = r.total == 0 ? 0 : (double) r.passed / r.total;
        r.byScene = byScene;
        r.failures = failures;
        r.tag = tag;
        r.model = model;
        return r;
    }

    private static List<EvalCase> loadCases(String jsonPath) throws IOException {
        String resource = jsonPath.replace("classpath:", "");
        try (InputStream is = EvalCrmHrRunnerTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) throw new IOException("Resource not found: " + resource);
            return Arrays.asList(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(is, EvalCase[].class));
        }
    }

    private static List<EvalCase> loadCasesByScene(String jsonPath, String scene) throws IOException {
        return loadCases(jsonPath).stream().filter(c -> scene.equals(c.getScene())).toList();
    }

    private void writeReport(EvalResult r, String path, String jsonName, String agentName) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(agentName).append(" 评测报告 (Mock 基线)\n\n");
        sb.append("> 生成时间: ").append(java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append("\n");
        sb.append("> 评测集: ").append(jsonName).append("\n");
        sb.append("> 使用模型: ").append(r.model).append("\n");
        sb.append("> W75-B 目标: 通过率 ≥ 85%\n\n");

        sb.append("## 概览\n\n");
        sb.append("| 指标 | 数值 |\n|------|------|\n");
        sb.append("| 总用例数 | ").append(r.total).append(" |\n");
        sb.append("| 通过 | ").append(r.passed).append(" |\n");
        sb.append("| 失败 | ").append(r.failed).append(" |\n");
        sb.append(String.format("| 通过率 | %.1f%% |%n", r.passRate * 100));

        sb.append("\n## 按场景分\n\n| 场景 | 总数 | 通过 | 通过率 |\n|------|------|------|--------|\n");
        r.byScene.forEach((k, v) -> {
            double rate = v[0] == 0 ? 0 : (double) v[1] / v[0];
            sb.append(String.format("| %s | %d | %d | %.1f%% |%n", k, v[0], v[1], rate * 100));
        });

        sb.append("\n## 失败用例明细\n\n");
        if (r.failures.isEmpty()) {
            sb.append("(无)\n");
        } else {
            sb.append("| Case | 场景 | 失败原因 |\n|------|------|----------|\n");
            r.failures.forEach(f -> {
                String diffs = String.join("; ", f.diffs);
                if (diffs.length() > 200) diffs = diffs.substring(0, 200) + "...";
                sb.append(String.format("| %s | %s | %s |%n", f.caseCode, f.scene, diffs));
            });
        }

        sb.append("\n---\n");
        sb.append("> 注: Mock 100% 通过 ≠ 真实 LLM 通过率;真实准确率需 staging 跑 -Peval-live 得到。\n");

        Files.writeString(Paths.get(path), sb.toString());
        log.info("[EvalCrmHrRunnerTest] Written to {}", path);
    }

    /* ============================================================
     *  数据类
     * ============================================================ */

    private static class EvalResult {
        int total;
        int passed;
        int failed;
        double passRate;
        Map<String, int[]> byScene;
        List<CaseFailure> failures;
        String tag;
        String model;
    }

    private static class CaseFailure {
        final String caseCode;
        final String scene;
        final List<String> diffs;
        CaseFailure(String caseCode, String scene, List<String> diffs) {
            this.caseCode = caseCode; this.scene = scene; this.diffs = diffs;
        }
    }
}
