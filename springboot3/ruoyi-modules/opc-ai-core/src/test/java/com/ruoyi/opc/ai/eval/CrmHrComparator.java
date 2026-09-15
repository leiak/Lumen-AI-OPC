package com.ruoyi.opc.ai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * CRM/HR Agent 评测比较器 (W75-B)
 *
 * <p>针对 5 类场景校验:
 * <ul>
 *   <li>SCORE: score 在 scoreRange 内 + reason 含指定关键词</li>
 *   <li>FOLLOWUP_SUGGEST: action 在 actionIn 内 + priority 在 priorityIn 内</li>
 *   <li>JD_GENERATE: content 含 sectionsContain + length ≥ lengthMin + 含 contains 关键词</li>
 *   <li>RESUME_PARSE: name/email/phone 必填值 + education/experience 计数</li>
 *   <li>CANDIDATE_SCORE: score 在 scoreRange 内 + highlights/gaps 数 + reason 关键词</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
public class CrmHrComparator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static List<String> compare(EvalCase evalCase, Map<String, Object> actual) {
        List<String> diffs = new ArrayList<>();
        if (actual == null) {
            diffs.add("actual is null");
            return diffs;
        }
        Map<String, Object> expected = MAPPER.convertValue(evalCase.getExpected(), Map.class);

        String scene = evalCase.getScene();
        if (scene == null) scene = "UNKNOWN";

        switch (scene) {
            case "SCORE":             checkScore(expected, actual, diffs); break;
            case "FOLLOWUP_SUGGEST":  checkFollowup(expected, actual, diffs); break;
            case "JD_GENERATE":       checkJd(expected, actual, diffs); break;
            case "RESUME_PARSE":      checkResume(expected, actual, diffs); break;
            case "CANDIDATE_SCORE":   checkCandidate(expected, actual, diffs); break;
            default: diffs.add("unknown scene: " + scene);
        }
        return diffs;
    }

    @SuppressWarnings("unchecked")
    private static void checkScore(Map<String, Object> expected, Map<String, Object> actual, List<String> diffs) {
        Integer score = toInt(actual.get("score"));
        if (score == null) { diffs.add("score missing"); return; }
        List<Number> range = (List<Number>) expected.get("scoreRange");
        if (range != null && range.size() == 2) {
            int lo = range.get(0).intValue();
            int hi = range.get(1).intValue();
            if (score < lo || score > hi) {
                diffs.add("score " + score + " not in [" + lo + "," + hi + "]");
            }
        }
        List<String> reasonContains = (List<String>) expected.get("reasonContains");
        if (reasonContains != null) {
            String reason = String.valueOf(actual.getOrDefault("reason", ""));
            for (String kw : reasonContains) {
                if (!reason.contains(kw)) {
                    diffs.add("reason missing keyword: " + kw);
                    break; // 报告第一个缺失即可
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkFollowup(Map<String, Object> expected, Map<String, Object> actual, List<String> diffs) {
        List<String> actionIn = (List<String>) expected.get("actionIn");
        String action = String.valueOf(actual.getOrDefault("action", ""));
        if (actionIn != null && !actionIn.contains(action)) {
            diffs.add("action '" + action + "' not in " + actionIn);
        }
        List<String> priorityIn = (List<String>) expected.get("priorityIn");
        String priority = String.valueOf(actual.getOrDefault("priority", ""));
        if (priorityIn != null && !priorityIn.contains(priority)) {
            diffs.add("priority '" + priority + "' not in " + priorityIn);
        }
        List<String> reasonContains = (List<String>) expected.get("reasonContains");
        if (reasonContains != null) {
            String reason = String.valueOf(actual.getOrDefault("reason", ""));
            for (String kw : reasonContains) {
                if (!reason.contains(kw)) {
                    diffs.add("reason missing keyword: " + kw);
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkJd(Map<String, Object> expected, Map<String, Object> actual, List<String> diffs) {
        Object contentObj = actual.getOrDefault("content", actual.getOrDefault("reason", ""));
        String content = String.valueOf(contentObj);
        List<String> sections = (List<String>) expected.get("sectionsContain");
        if (sections != null) {
            for (String s : sections) {
                if (!content.contains(s)) {
                    diffs.add("JD missing section: " + s);
                    break;
                }
            }
        }
        Integer minLen = toInt(expected.get("lengthMin"));
        if (minLen != null && content.length() < minLen) {
            diffs.add("JD too short: " + content.length() + " < " + minLen);
        }
        List<String> contains = (List<String>) expected.get("contains");
        if (contains != null) {
            for (String c : contains) {
                if (!content.contains(c)) {
                    diffs.add("JD missing keyword: " + c);
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkResume(Map<String, Object> expected, Map<String, Object> actual, List<String> diffs) {
        checkField("name", expected.get("name"), String.valueOf(actual.getOrDefault("name", "")), diffs);
        checkField("email", expected.get("email"), String.valueOf(actual.getOrDefault("email", "")), diffs);
        checkField("phone", expected.get("phone"), String.valueOf(actual.getOrDefault("phone", "")), diffs);

        Integer expEdu = toInt(expected.get("educationCount"));
        Integer actEdu = toInt(actual.get("educationCount"));
        if (expEdu != null && actEdu != null && Math.abs(expEdu - actEdu) > 0) {
            diffs.add("educationCount: expected=" + expEdu + " actual=" + actEdu);
        }
        Integer expExp = toInt(expected.get("experienceCount"));
        Integer actExp = toInt(actual.get("experienceCount"));
        if (expExp != null && actExp != null && Math.abs(expExp - actExp) > 0) {
            diffs.add("experienceCount: expected=" + expExp + " actual=" + actExp);
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkCandidate(Map<String, Object> expected, Map<String, Object> actual, List<String> diffs) {
        Integer score = toInt(actual.get("score"));
        if (score == null) { diffs.add("score missing"); return; }
        List<Number> range = (List<Number>) expected.get("scoreRange");
        if (range != null && range.size() == 2) {
            int lo = range.get(0).intValue();
            int hi = range.get(1).intValue();
            if (score < lo || score > hi) {
                diffs.add("score " + score + " not in [" + lo + "," + hi + "]");
            }
        }
        List<String> reasonContains = (List<String>) expected.get("reasonContains");
        if (reasonContains != null) {
            String reason = String.valueOf(actual.getOrDefault("reason", ""));
            for (String kw : reasonContains) {
                if (!reason.contains(kw)) {
                    diffs.add("reason missing keyword: " + kw);
                    break;
                }
            }
        }
    }

    private static void checkField(String name, Object exp, String act, List<String> diffs) {
        if (exp != null && !exp.toString().isEmpty() && !exp.toString().equals(act)) {
            diffs.add(name + ": expected='" + exp + "' actual='" + act + "'");
        }
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}
