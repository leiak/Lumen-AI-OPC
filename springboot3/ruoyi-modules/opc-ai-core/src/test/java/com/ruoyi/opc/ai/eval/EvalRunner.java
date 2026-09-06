package com.ruoyi.opc.ai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评测 Runner：从 JSON 加载用例 → 调用 Provider → 比较 → 输出报告
 *
 * @author OAC
 */
@Slf4j
public class EvalRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 跑一遍评测
     */
    public static EvalReportWriter.EvalReport run(
            String jsonPath,
            MockFinanceLlmProvider provider,
            String promptVersion
    ) throws IOException {
        log.info("[EvalRunner] Loading {}", jsonPath);
        List<EvalCase> cases = loadCases(jsonPath);
        log.info("[EvalRunner] Loaded {} cases", cases.size());

        EvalReportWriter.EvalReport report = new EvalReportWriter.EvalReport();
        report.promptVersion = promptVersion;
        report.model = "mock-finance-llm";
        report.evalSet = jsonPath.substring(jsonPath.replace('\\', '/').lastIndexOf('/') + 1);

        Map<String, EvalReportWriter.Bucket> byDiff = new LinkedHashMap<>();
        Map<String, EvalReportWriter.TagBucket> byTag = new LinkedHashMap<>();
        List<Long> latencies = new ArrayList<>();

        int passed = 0, failed = 0;
        double totalScore = 0;

        for (EvalCase c : cases) {
            long start = System.currentTimeMillis();
            Map<String, Object> actual;
            try {
                actual = provider.extract(c.getInput());
            } catch (Exception e) {
                actual = Map.of("error", e.getMessage());
            }
            long cost = System.currentTimeMillis() - start;
            latencies.add(cost);

            EvalComparator.CompareResult result = EvalComparator.compare(c, actual);

            // 难度桶
            byDiff.computeIfAbsent(c.getDifficulty(), k -> new EvalReportWriter.Bucket()).total++;
            // Tag 桶（取第一个 tag）
            String firstTag = c.getTags() == null ? "其他" : c.getTags().split(",")[0];
            byTag.computeIfAbsent(firstTag, k -> {
                EvalReportWriter.TagBucket tb = new EvalReportWriter.TagBucket();
                tb.tag = k;
                return tb;
            }).total++;

            if (result.pass) {
                passed++;
                byDiff.get(c.getDifficulty()).passed++;
                byTag.get(firstTag).passed++;
            } else {
                failed++;
                EvalReportWriter.Failure f = new EvalReportWriter.Failure();
                f.caseCode = c.getCaseCode();
                f.difficulty = c.getDifficulty();
                f.tags = c.getTags();
                f.diffs = result.diffs;
                report.failures.add(f);
            }
            totalScore += result.score;
        }

        report.total = cases.size();
        report.passed = passed;
        report.failed = failed;
        report.passRate = (double) passed / cases.size();
        report.avgScore = totalScore / cases.size();
        report.byDifficulty = byDiff;
        report.byTag = new ArrayList<>(byTag.values());

        Collections.sort(latencies);
        report.p50Latency = latencies.get(latencies.size() / 2);
        report.p95Latency = latencies.get((int) (latencies.size() * 0.95));

        log.info("[EvalRunner] {}", String.format("PASS=%d/%d (%.1f%%)", passed, cases.size(), report.passRate * 100));
        return report;
    }

    public static List<EvalCase> loadCases(String jsonPath) throws IOException {
        if (jsonPath.startsWith("classpath:")) {
            String resource = jsonPath.substring("classpath:".length());
            try (InputStream is = EvalRunner.class.getClassLoader().getResourceAsStream(resource)) {
                if (is == null) throw new IOException("Resource not found: " + resource);
                return Arrays.asList(MAPPER.readValue(is, EvalCase[].class));
            }
        }
        return Arrays.asList(MAPPER.readValue(Files.readAllBytes(Paths.get(jsonPath)), EvalCase[].class));
    }

}
