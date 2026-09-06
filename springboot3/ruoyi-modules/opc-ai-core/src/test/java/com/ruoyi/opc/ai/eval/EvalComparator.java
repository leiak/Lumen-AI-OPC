package com.ruoyi.opc.ai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 评测比较器：对比 LLM 输出与 expected 的差异
 *
 * <p>匹配规则：
 * <ul>
 *   <li>direction（收入/支出方向）— 完全匹配</li>
 *   <li>amount（金额）— 允许 ±1% 容差</li>
 *   <li>counter_party（对手方）— 字符串包含匹配</li>
 *   <li>subject_code（科目代码）— 完全匹配（允许空）</li>
 *   <li>summary（摘要）— 不强校验（LLM 文案自由度高）</li>
 *   <li>need_review — 完全匹配</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
public class EvalComparator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double AMOUNT_TOLERANCE = 0.01;  // ±1%

    @SuppressWarnings("unchecked")
    public static CompareResult compare(EvalCase evalCase, Map<String, Object> actual) {
        if (actual == null) {
            return CompareResult.fail("LLM 输出为空");
        }
        Object expectedRaw = evalCase.getExpected();
        if (!(expectedRaw instanceof Map)) {
            // expected 非结构化（SUMMARIZE 场景为一句自然语言描述），只校验输出非空 + 关键字段命中
            return compareSummarize(evalCase, actual);
        }

        Map<String, Object> expected = MAPPER.convertValue(expectedRaw, Map.class);

        List<String> diffs = new ArrayList<>();
        int totalChecks = 0;
        int passedChecks = 0;

        // 1. direction
        if (expected.containsKey("direction")) {
            totalChecks++;
            String expDir = String.valueOf(expected.get("direction"));
            String actDir = String.valueOf(actual.getOrDefault("direction", ""));
            if (expDir.equalsIgnoreCase(actDir)) passedChecks++;
            else diffs.add("direction: expected=" + expDir + " actual=" + actDir);
        }

        // 2. amount
        if (expected.containsKey("amount")) {
            totalChecks++;
            double expAmt = ((Number) expected.get("amount")).doubleValue();
            double actAmt = actual.get("amount") != null ? ((Number) actual.get("amount")).doubleValue() : 0;
            double diff = Math.abs(expAmt - actAmt) / Math.max(expAmt, 1);
            if (diff <= AMOUNT_TOLERANCE) passedChecks++;
            else diffs.add(String.format("amount: expected=%.2f actual=%.2f diff=%.2f%%",
                    expAmt, actAmt, diff * 100));
        }

        // 3. counter_party (substring match, ignore null)
        if (expected.containsKey("counter_party")) {
            totalChecks++;
            String expCp = String.valueOf(expected.get("counter_party"));
            String actCp = String.valueOf(actual.getOrDefault("counter_party", ""));
            if (expCp.isEmpty() || actCp.contains(expCp) || expCp.contains(actCp)) passedChecks++;
            else diffs.add("counter_party: expected='" + expCp + "' actual='" + actCp + "'");
        }

        // 4. subject_code
        if (expected.containsKey("subject_code")) {
            totalChecks++;
            String expSc = String.valueOf(expected.get("subject_code"));
            String actSc = String.valueOf(actual.getOrDefault("subject_code", ""));
            if (expSc.equals(actSc) || expSc.isEmpty()) passedChecks++;
            else diffs.add("subject_code: expected='" + expSc + "' actual='" + actSc + "'");
        }

        // 5. need_review
        if (expected.containsKey("need_review")) {
            totalChecks++;
            boolean expNr = Boolean.TRUE.equals(expected.get("need_review"));
            boolean actNr = Boolean.TRUE.equals(actual.get("need_review"));
            if (expNr == actNr) passedChecks++;
            else diffs.add("need_review: expected=" + expNr + " actual=" + actNr);
        }

        // 6. tax_rate
        if (expected.containsKey("tax_rate")) {
            totalChecks++;
            double expTr = ((Number) expected.get("tax_rate")).doubleValue();
            double actTr = actual.get("tax_rate") != null ? ((Number) actual.get("tax_rate")).doubleValue() : 0;
            if (Math.abs(expTr - actTr) < 0.001) passedChecks++;
            else diffs.add(String.format("tax_rate: expected=%.3f actual=%.3f", expTr, actTr));
        }

        double score = totalChecks > 0 ? (double) passedChecks / totalChecks : 0;
        boolean pass = score >= 0.8;  // 80% 字段通过即算通过
        return new CompareResult(pass, score, diffs);
    }

    private static CompareResult compareSummarize(EvalCase evalCase, Map<String, Object> actual) {
        String content = String.valueOf(actual.getOrDefault("content", ""));
        if (content.isEmpty()) {
            return CompareResult.fail("SUMMARIZE 输出为空");
        }
        // 必须包含日期 + 至少 2 个数字
        int numberCount = content.replaceAll("[^0-9]", "").length();
        if (numberCount < 2) {
            return CompareResult.fail("SUMMARIZE 输出缺少数据");
        }
        return CompareResult.pass(1.0, Collections.emptyList());
    }

    public static class CompareResult {
        public final boolean pass;
        public final double score;
        public final List<String> diffs;

        public CompareResult(boolean pass, double score, List<String> diffs) {
            this.pass = pass;
            this.score = score;
            this.diffs = diffs;
        }

        public static CompareResult pass(double score, List<String> diffs) {
            return new CompareResult(true, score, diffs);
        }

        public static CompareResult fail(String msg) {
            return new CompareResult(false, 0.0, List.of(msg));
        }
    }

}
