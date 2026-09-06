package com.ruoyi.opc.ai.redteam;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 红队报告 Markdown 写入器。
 *
 * <p>输出格式对齐 {@link com.ruoyi.opc.ai.eval.EvalReportWriter}：
 * <ul>
 *   <li>概览：total / attackSucceeded / attackBlocked / ASR</li>
 *   <li>按 category × severity × difficulty 三维度分桶</li>
 *   <li>防御层级分布（PromptGuard / SensitiveWordFilter / SystemPrompt / LLMRefused / NONE）</li>
 *   <li>失败用例明细（即攻击成功的 case）</li>
 *   <li>防御层覆盖率分析</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
public class RedTeamReportWriter {

    public static void write(RedTeamRunner.RedTeamReport report, String outputPath) throws IOException {
        StringBuilder sb = new StringBuilder(4096);

        sb.append("# RedTeam 安全评测报告\n\n");
        sb.append("> 生成时间：").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date())).append("\n");
        sb.append("> Prompt 版本：").append(report.getPromptVersion()).append("\n");
        sb.append("> 模型 / Provider：").append("mock-agent (offline SHA-256 deterministic)").append("\n");
        sb.append("> 评测集：").append(report.getEvalSet()).append("\n");
        if (!Double.isNaN(report.getComplyRate())) {
            sb.append("> Mock complyRate：").append(String.format("%.2f", report.getComplyRate())).append("\n");
        }
        sb.append("\n");

        // 概览
        sb.append("## 概览\n\n");
        sb.append("| 指标 | 数值 |\n");
        sb.append("|------|------|\n");
        sb.append("| 总用例数 | ").append(report.getTotal()).append(" |\n");
        sb.append("| 攻击成功 | ").append(report.getAttackSucceeded()).append(" |\n");
        sb.append("| 攻击被拦 | ").append(report.getAttackBlocked()).append(" |\n");
        sb.append("| **ASR** | **").append(String.format("%.1f%%", report.getAsr() * 100)).append("** (")
                .append(report.getAttackSucceeded()).append("/").append(report.getTotal()).append(") |\n\n");

        // 按 category
        appendBucketTable(sb, "按攻击类别", report.getByCategory());
        // 按 severity
        appendBucketTable(sb, "按严重程度", report.getBySeverity());
        // 按 difficulty
        appendBucketTable(sb, "按难度", report.getByDifficulty());

        // 防御层级分布
        sb.append("## 防御层级分布\n\n");
        sb.append("> ASR 越低、且 PromptGuard / SensitiveWordFilter 命中率越高 = 防御越前置（成本越低）。\n\n");
        sb.append("| 防御层 | 命中次数 | 占比 |\n");
        sb.append("|--------|---------|------|\n");
        Map<String, Integer> layers = report.getLayerDistribution();
        if (layers != null) {
            int total = report.getTotal();
            layers.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> sb.append("| ").append(e.getKey())
                            .append(" | ").append(e.getValue())
                            .append(" | ").append(String.format("%.1f%%", 100.0 * e.getValue() / total))
                            .append(" |\n"));
        }
        sb.append("\n");

        // 失败用例明细（攻击成功）
        sb.append("## 攻击成功明细（需修复）\n\n");
        if (report.getResults() == null) {
            sb.append("无数据\n\n");
        } else {
            List<RedTeamRunner.AttackResult> failed = report.getResults().stream()
                    .filter(RedTeamRunner.AttackResult::isAttackSucceeded)
                    .toList();
            if (failed.isEmpty()) {
                sb.append("**所有攻击均被拦截，ASR = 0%。**\n\n");
            } else {
                sb.append("| Case | 类别 | 严重度 | 难度 | 目标 | 防御层 |\n");
                sb.append("|------|------|--------|------|------|--------|\n");
                failed.forEach(r -> sb.append("| ").append(r.getCaseCode())
                        .append(" | ").append(nullSafe(r.getCategory()))
                        .append(" | ").append(nullSafe(r.getSeverity()))
                        .append(" | ").append(nullSafe(r.getDifficulty()))
                        .append(" | ").append(nullSafe(r.getAttackGoal()))
                        .append(" | ").append(nullSafe(r.getDefenseLayer()))
                        .append(" |\n"));
                sb.append("\n");
            }
        }

        // 全部明细（用于 debug）
        sb.append("## 全量明细\n\n");
        sb.append("<details><summary>点击展开 30 条用例结果</summary>\n\n");
        if (report.getResults() != null) {
            sb.append("| Case | 类别 | 严重度 | 难度 | 防御层 | 攻击成功 | 判定理由 |\n");
            sb.append("|------|------|--------|------|--------|----------|----------|\n");
            report.getResults().forEach(r -> sb.append("| ").append(r.getCaseCode())
                    .append(" | ").append(nullSafe(r.getCategory()))
                    .append(" | ").append(nullSafe(r.getSeverity()))
                    .append(" | ").append(nullSafe(r.getDifficulty()))
                    .append(" | ").append(nullSafe(r.getDefenseLayer()))
                    .append(" | ").append(r.isAttackSucceeded() ? "❌" : "✅")
                    .append(" | ").append(nullSafe(r.getReason()))
                    .append(" |\n"));
        }
        sb.append("\n</details>\n\n");

        // 写入文件
        Files.write(Paths.get(outputPath), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        log.info("[RedTeamReportWriter] Written to {}", outputPath);
    }

    private static void appendBucketTable(StringBuilder sb, String title, List<RedTeamRunner.Bucket> buckets) {
        if (buckets == null || buckets.isEmpty()) return;
        sb.append("## ").append(title).append("\n\n");
        sb.append("| 维度 | 总数 | 攻击成功 | ASR |\n");
        sb.append("|------|------|----------|-----|\n");
        for (RedTeamRunner.Bucket b : buckets) {
            sb.append("| ").append(b.getKey())
                    .append(" | ").append(b.getTotal())
                    .append(" | ").append(b.getAttackSucceeded())
                    .append(" | ").append(String.format("%.1f%%", b.getAsr() * 100))
                    .append(" |\n");
        }
        sb.append("\n");
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
