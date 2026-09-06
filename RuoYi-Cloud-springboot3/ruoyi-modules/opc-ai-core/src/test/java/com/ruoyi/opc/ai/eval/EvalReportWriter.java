package com.ruoyi.opc.ai.eval;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评测报告生成器（Markdown）
 *
 * @author OAC
 */
@Slf4j
public class EvalReportWriter {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static void write(EvalReport report, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        String md = render(report);
        Files.writeString(path, md);
        log.info("[EvalReportWriter] Written to {}", outputPath);
    }

    public static String render(EvalReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Finance Agent 评测报告\n\n");
        sb.append("> 生成时间：").append(TS_FMT.format(LocalDateTime.now())).append("\n");
        sb.append("> Prompt 版本：").append(report.promptVersion).append("\n");
        sb.append("> 使用模型：").append(report.model).append("\n");
        sb.append("> 评测集：").append(report.evalSet).append("\n\n");

        // 概览
        sb.append("## 概览\n\n");
        sb.append("| 指标 | 数值 |\n");
        sb.append("|------|------|\n");
        sb.append("| 总用例数 | ").append(report.total).append(" |\n");
        sb.append("| 通过 | ").append(report.passed).append(" |\n");
        sb.append("| 失败 | ").append(report.failed).append(" |\n");
        sb.append(String.format("| 通过率 | %.1f%% |%n", report.passRate * 100));
        sb.append(String.format("| 平均字段分 | %.2f |%n", report.avgScore));
        sb.append(String.format("| P50 延迟 | %d ms |%n", report.p50Latency));
        sb.append(String.format("| P95 延迟 | %d ms |%n", report.p95Latency));
        sb.append("\n");

        // 按难度分
        sb.append("## 按难度\n\n");
        sb.append("| 难度 | 总数 | 通过 | 通过率 |\n");
        sb.append("|------|------|------|--------|\n");
        report.byDifficulty.forEach((k, v) -> {
            double r = v.total > 0 ? (double) v.passed / v.total : 0;
            sb.append(String.format("| %s | %d | %d | %.1f%% |%n", k, v.total, v.passed, r * 100));
        });
        sb.append("\n");

        // 按 Tag 分
        sb.append("## 按场景（Top 10）\n\n");
        sb.append("| 场景 | 总数 | 通过 | 通过率 |\n");
        sb.append("|------|------|------|--------|\n");
        report.byTag.stream()
                .sorted((a, b) -> Integer.compare(b.total, a.total))
                .limit(10)
                .forEach(s -> sb.append(String.format("| %s | %d | %d | %.1f%% |%n",
                        s.tag, s.total, s.passed, (double) s.passed / s.total * 100)));
        sb.append("\n");

        // Top 失败用例
        sb.append("## 失败用例明细\n\n");
        sb.append("| Case | 难度 | Tags | 失败字段 |\n");
        sb.append("|------|------|------|----------|\n");
        report.failures.forEach(f -> {
            String diffs = f.diffs.isEmpty() ? "-" : String.join("; ", f.diffs);
            if (diffs.length() > 200) diffs = diffs.substring(0, 200) + "...";
            sb.append(String.format("| %s | %s | %s | %s |%n",
                    f.caseCode, f.difficulty, f.tags, diffs));
        });
        sb.append("\n");

        // 失败模式分析
        sb.append("## 失败模式分析\n\n");
        Map<String, Integer> diffFreq = new HashMap<>();
        for (Failure f : report.failures) {
            for (String d : f.diffs) {
                String field = d.split(":")[0].trim();
                diffFreq.merge(field, 1, Integer::sum);
            }
        }
        if (!diffFreq.isEmpty()) {
            sb.append("| 字段 | 失败次数 |\n");
            sb.append("|------|----------|\n");
            diffFreq.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .forEach(e -> sb.append(String.format("| %s | %d |%n", e.getKey(), e.getValue())));
        }
        sb.append("\n");

        sb.append("---\n");
        sb.append("> 详细 diff 见 `eval-failures/` 目录\n");
        return sb.toString();
    }

    public static class EvalReport {
        public String promptVersion;
        public String model;
        public String evalSet;
        public int total;
        public int passed;
        public int failed;
        public double passRate;
        public double avgScore;
        public long p50Latency;
        public long p95Latency;
        public Map<String, Bucket> byDifficulty = new LinkedHashMap<>();
        public List<TagBucket> byTag = new ArrayList<>();
        public List<Failure> failures = new ArrayList<>();
    }

    public static class Bucket {
        public int total;
        public int passed;
    }

    public static class TagBucket {
        public String tag;
        public int total;
        public int passed;
    }

    public static class Failure {
        public String caseCode;
        public String difficulty;
        public String tags;
        public List<String> diffs;
    }
}
