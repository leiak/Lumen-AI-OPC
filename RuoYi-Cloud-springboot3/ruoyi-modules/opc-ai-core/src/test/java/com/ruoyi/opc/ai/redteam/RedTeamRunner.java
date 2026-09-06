package com.ruoyi.opc.ai.redteam;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 红队评测 Runner：从 JSON 加载攻击用例 → 经 MockAgentProvider 多层防御 → 输出 ASR 报告。
 *
 * <p>与 {@link com.ruoyi.opc.ai.eval.EvalRunner} 的关键区别：
 * <ul>
 *   <li>评测指标是 <b>ASR (Attack Success Rate)</b>，越低越好；EvalRunner 是 passRate，越高越好</li>
 *   <li>每条 case 记录 <b>defenseLayer</b>（在哪一层被拦下），便于识别薄弱环节</li>
 *   <li>按 category / severity / difficulty 三维度聚合</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
public class RedTeamRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 单次完整跑批
     *
     * @param jsonPath     classpath:eval/redteam-30.json 或绝对路径
     * @param provider     MockAgentProvider（含 promptVersion + complyRate）
     * @return RedTeamReport
     */
    public static RedTeamReport run(String jsonPath, MockAgentProvider provider) throws IOException {
        log.info("[RedTeamRunner] Loading {}", jsonPath);
        List<RedTeamCase> cases = loadCases(jsonPath);
        log.info("[RedTeamRunner] Loaded {} cases", cases.size());

        RedTeamReport report = new RedTeamReport();
        report.promptVersion = provider.getPromptVersion();
        report.complyRate = provider.getComplyRate();
        report.evalSet = jsonPath.substring(jsonPath.replace('\\', '/').lastIndexOf('/') + 1);

        Map<String, Bucket> byCategory = new LinkedHashMap<>();
        Map<String, Bucket> bySeverity = new LinkedHashMap<>();
        Map<String, Bucket> byDifficulty = new LinkedHashMap<>();
        Map<String, Integer> layerHits = new LinkedHashMap<>();
        int succeeded = 0;

        for (RedTeamCase c : cases) {
            MockAgentProvider.Verdict v = provider.defend(c.getAttackPrompt(), c.getCategory());

            // 按 category
            byCategory.computeIfAbsent(c.getCategory(), k -> new Bucket(k)).total++;
            // 按 severity
            bySeverity.computeIfAbsent(c.getSeverity(), k -> new Bucket(k)).total++;
            // 按 difficulty
            byDifficulty.computeIfAbsent(c.getDifficulty(), k -> new Bucket(k)).total++;
            // 防御层级分布
            layerHits.merge(v.getDefenseLayer(), 1, Integer::sum);

            AttackResult ar = new AttackResult();
            ar.caseCode = c.getCaseCode();
            ar.category = c.getCategory();
            ar.severity = c.getSeverity();
            ar.difficulty = c.getDifficulty();
            ar.defenseLayer = v.getDefenseLayer();
            ar.attackSucceeded = v.isAttackSucceeded();
            ar.reason = v.getReason();
            ar.attackGoal = c.getAttackGoal();
            report.results.add(ar);

            if (v.isAttackSucceeded()) {
                succeeded++;
                byCategory.get(c.getCategory()).attackSucceeded++;
                bySeverity.get(c.getSeverity()).attackSucceeded++;
                byDifficulty.get(c.getDifficulty()).attackSucceeded++;
            }
        }

        report.total = cases.size();
        report.attackSucceeded = succeeded;
        report.attackBlocked = cases.size() - succeeded;
        report.asr = (double) succeeded / cases.size();
        report.byCategory = new ArrayList<>(byCategory.values());
        report.bySeverity = new ArrayList<>(bySeverity.values());
        report.byDifficulty = new ArrayList<>(byDifficulty.values());
        report.layerDistribution = layerHits;

        log.info("[RedTeamRunner] ASR={}/{} ({})", succeeded, cases.size(),
                String.format("%.1f%%", report.asr * 100));
        return report;
    }

    public static List<RedTeamCase> loadCases(String jsonPath) throws IOException {
        if (jsonPath.startsWith("classpath:")) {
            String resource = jsonPath.substring("classpath:".length());
            try (InputStream is = RedTeamRunner.class.getClassLoader().getResourceAsStream(resource)) {
                if (is == null) throw new IOException("Resource not found: " + resource);
                return Arrays.asList(MAPPER.readValue(is, RedTeamCase[].class));
            }
        }
        return Arrays.asList(MAPPER.readValue(Files.readAllBytes(Paths.get(jsonPath)), RedTeamCase[].class));
    }

    /* ============== 数据模型 ============== */

    @lombok.Data
    public static class AttackResult {
        private String caseCode;
        private String category;
        private String severity;
        private String difficulty;
        private String defenseLayer;
        private boolean attackSucceeded;
        private String reason;
        private String attackGoal;
    }

    @lombok.Data
    public static class Bucket {
        private final String key;
        public int total;
        public int attackSucceeded;

        public Bucket(String key) { this.key = key; }
        public double getAsr() { return total == 0 ? 0 : (double) attackSucceeded / total; }
    }

    @lombok.Data
    public static class RedTeamReport {
        private String promptVersion;
        private double complyRate;
        private String evalSet;
        private int total;
        private int attackSucceeded;
        private int attackBlocked;
        private double asr;
        private List<AttackResult> results = new ArrayList<>();
        private List<Bucket> byCategory;
        private List<Bucket> bySeverity;
        private List<Bucket> byDifficulty;
        private Map<String, Integer> layerDistribution;
    }
}
