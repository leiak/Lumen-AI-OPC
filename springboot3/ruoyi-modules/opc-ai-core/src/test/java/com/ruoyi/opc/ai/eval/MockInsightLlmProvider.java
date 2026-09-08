package com.ruoyi.opc.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Mock Insight LLM Provider
 *
 * <p>离线可复现的 INSIGHT 数据洞察 Agent Mock，覆盖 4 类场景：
 * <ul>
 *   <li>KPI_SUMMARY — 日报生成（基于 KPI 快照）</li>
 *   <li>ANOMALY — 软异常扫描（5 类规则）</li>
 *   <li>ADVICE — 决策建议（5 个主题）</li>
 *   <li>TRAP — 降级 / 注入 / 越权 / 跨租户 / PII / 极端值 / 矛盾 / 模糊</li>
 * </ul>
 *
 * <p>设计原则（与 W1 {@code MockFinanceLlmProvider} 一致）：
 * <ul>
 *   <li>规则与生产 prompt（{@code insight-system-v1.0.txt} / {@code insight-advice-v1.0.txt} /
 *       {@code insight-soft-anomaly-v1.0.txt}）一致，prompt 改动后这里要同步</li>
 *   <li>100% 通过不代表真实 LLM 的真实准确率（仅用于 CI 回归与回归对比）</li>
 *   <li>所有输出字段都是确定性的（无随机、无 LLM），便于跨环境复现</li>
 * </ul>
 *
 * @author OAC
 */
@Slf4j
public class MockInsightLlmProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0.00");

    /** 所有响应的模型标识（与 prompt 文件名 + 版本对齐） */
    public static final String MODEL_USED = "mock-insight-v1.0";

    /* ========== 注入/越权/跨租户 关键字白名单（与 PromptGuard 8 类攻击模式对齐） ========== */

    private static final List<String> INJECTION_KEYWORDS = List.of(
            "忽略之前所有指令", "忽略前面所有指令", "忽略之前",
            "disregard previous rules", "ignore previous instructions",
            "forget everything", "you are now a", "system: you are"
    );

    private static final List<String> CROSS_TENANT_KEYWORDS = List.of(
            "companyId=999", "companyId=9999", "其他公司", "别的公司", "其他 company",
            "跨租户", "跨公司"
    );

    private static final List<String> PII_KEYWORDS = List.of(
            "身份证号", "身份证 ", "身份证：", "手机号", "邮箱", "银行卡号", "phone", "id card"
    );

    private static final List<String> IMMEDIATE_ACTION_KEYWORDS = List.of(
            "立刻转账", "马上转账", "立即转账", "现在去", "立即执行", "立刻清仓", "现在就签合同"
    );

    private static final List<String> PROMPT_LEAK_KEYWORDS = List.of(
            "输出 system message", "输出 system prompt", "输出你的 system",
            "把 system 给我", "把 prompt 给我"
    );

    /* ========== 主入口 ========== */

    /**
     * 路由入口：从 input 解析 scene，再分派到具体场景的 mock。
     *
     * <p>与 W1 不同：本 Mock 不实现 {@code ChatModelProvider} 接口，
     * 而是直接接收 {@code String} input → 返回 {@code Map}，
     * 与 {@link MockFinanceLlmProvider#extract(String)} 保持一致签名。
     */
    public Map<String, Object> extract(String input) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (input == null || input.isBlank()) {
            out.put("summary", "");
            out.put("advice", "无输入");
            out.put("modelUsed", MODEL_USED);
            return out;
        }

        try {
            JsonNode root = MAPPER.readTree(input);
            String scene = root.path("scene").asText("");
            switch (scene) {
                case "KPI_SUMMARY": return mockDailyReport(root);
                case "ANOMALY":     return mockSoftAnomaly(root);
                case "ADVICE":      return mockAdvice(root);
                case "TRAP":        return mockTrap(root);
                default:
                    out.put("summary", "");
                    out.put("advice", "未知场景: " + scene);
                    out.put("modelUsed", MODEL_USED);
                    return out;
            }
        } catch (Exception e) {
            log.warn("[MockInsight] 解析失败: {}", e.getMessage());
            out.put("summary", "");
            out.put("advice", "解析失败: " + e.getMessage());
            out.put("modelUsed", MODEL_USED);
            return out;
        }
    }

    /* ========== 1. 日报生成 ========== */

    private Map<String, Object> mockDailyReport(JsonNode root) {
        JsonNode kpi = root.path("kpi");
        Map<String, Object> out = new LinkedHashMap<>();

        double revenue = kpi.path("totalRevenue").asDouble(0);
        double expense = kpi.path("totalExpense").asDouble(0);
        int voucherCount = kpi.path("voucherCount").asInt(0);
        int pendingVoucherCount = kpi.path("pendingVoucherCount").asInt(0);
        double wallet = kpi.path("walletBalance").asDouble(0);
        long tokenUsage = kpi.path("tokenUsage").asLong(0);
        boolean partial = kpi.path("partial").asBoolean(false);
        String period = root.path("period").asText("今日");

        // summary 必须包含 "voucher" 和 "wallet" 字面词 + 两位小数的金额（千分位）
        // 这是为了让所有 KPI_SUMMARY 用例的 summaryContains 全部命中
        String summary = String.format(
                "%s 财务日报：营收 ¥%s，支出 ¥%s，voucher %d 张（含待复核 %d 张），wallet 余额 ¥%s，tokenUsage %d。",
                period, MONEY_FMT.format(revenue), MONEY_FMT.format(expense),
                voucherCount, pendingVoucherCount, MONEY_FMT.format(wallet), tokenUsage);

        StringBuilder advice = new StringBuilder("建议：");
        if (partial) advice.append("[数据降级] ");
        if (expense > revenue) {
            advice.append("关注现金流倒挂，建议复核大额支出；");
        } else if (revenue > expense * 1.5) {
            advice.append("营收良好，建议拓展渠道；");
        } else {
            advice.append("维持当前经营节奏；");
        }
        if (pendingVoucherCount > voucherCount * 0.5 && voucherCount > 0) {
            advice.append("凭证积压，请尽快安排审核；");
        }
        if (wallet < 100 && expense > revenue) {
            advice.append("钱包余额告急，建议尽快充值；");
        }
        advice.append("建议持续关注 KPI 变化。");

        out.put("summary", summary);
        out.put("advice", advice.toString());
        out.put("modelUsed", MODEL_USED);
        out.put("period", period);
        out.put("partial", partial);
        return out;
    }

    /* ========== 2. 软异常扫描 ========== */

    private Map<String, Object> mockSoftAnomaly(JsonNode root) {
        JsonNode kpi = root.path("kpi");
        Map<String, Object> out = new LinkedHashMap<>();

        double revenue = kpi.path("totalRevenue").asDouble(0);
        double expense = kpi.path("totalExpense").asDouble(0);
        int voucherCount = kpi.path("voucherCount").asInt(0);
        int pendingVoucherCount = kpi.path("pendingVoucherCount").asInt(0);
        double wallet = kpi.path("walletBalance").asDouble(0);
        long tokenUsage = kpi.path("tokenUsage").asLong(0);
        boolean partial = kpi.path("partial").asBoolean(false);

        List<Map<String, Object>> anomalies = new ArrayList<>();

        // 规则 1：CASHOUT_INVERSION — 支出 > 收入 但 wallet > 0（HIGH, 0.85）
        if (expense > revenue && wallet > 0) {
            anomalies.add(buildAnomaly("HIGH", "CASHOUT_INVERSION",
                    "现金流倒挂，wallet 即将耗尽", 0.88));
        }
        // 规则 2：VOUCHER_BACKLOG — pending/total > 50% 且 voucherCount > 0（MEDIUM, 0.7）
        if (voucherCount > 0 && pendingVoucherCount * 2 > voucherCount) {
            anomalies.add(buildAnomaly("MEDIUM", "VOUCHER_BACKLOG",
                    "凭证积压，pending 占比超 50%", 0.78));
        }
        // 规则 3：TOKEN_OVERUSE — token > 5000 且 voucher < 3（MEDIUM, 0.7）
        if (tokenUsage > 5000 && voucherCount < 3) {
            anomalies.add(buildAnomaly("MEDIUM", "TOKEN_OVERUSE",
                    "Token 用量异常，可能 agent 失控", 0.75));
        }
        // 规则 4：WALLET_DRY — wallet < 100 且 expense > revenue（HIGH, 0.85）
        if (wallet < 100 && expense > revenue) {
            anomalies.add(buildAnomaly("HIGH", "WALLET_DRY",
                    "钱包余额枯竭预警", 0.9));
        }
        // 规则 5：REVENUE_DROP — 需历史数据，缺数据时跳过

        // 数据降级时全部降为 LOW（与 insight-soft-anomaly-v1.0.txt §约束一致）
        if (partial) {
            anomalies.clear();
            anomalies.add(buildAnomaly("LOW", "PARTIAL_KPI",
                    "[数据降级] 数据不完整，仅做 LOW 风险提示", 0.5));
        }

        out.put("anomalies", anomalies);
        out.put("modelUsed", MODEL_USED);
        out.put("anomalyCount", anomalies.size());
        out.put("partial", partial);
        return out;
    }

    private Map<String, Object> buildAnomaly(String level, String ruleCode,
                                             String description, double confidence) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("level", level);
        a.put("ruleCode", ruleCode);
        a.put("description", description);
        a.put("confidence", confidence);
        return a;
    }

    /* ========== 3. 决策建议 ========== */

    private Map<String, Object> mockAdvice(JsonNode root) {
        JsonNode kpi = root.path("kpi");
        Map<String, Object> out = new LinkedHashMap<>();

        String topic = root.path("topic").asText("");
        boolean partial = kpi.path("partial").asBoolean(false);
        double revenue = kpi.path("totalRevenue").asDouble(0);
        double expense = kpi.path("totalExpense").asDouble(0);
        double wallet = kpi.path("walletBalance").asDouble(0);
        long tokenUsage = kpi.path("tokenUsage").asLong(0);

        // 未知主题 → 拒绝出投资标的 / 个性化建议（与 prompt §约束一致）
        String adviceText;
        double confidence;
        if (topic.isBlank()) {
            adviceText = "未提供 topic，建议先选择 5 个建议主题之一。";
            confidence = 0.5;
        } else if (topic.equals("stock_picking") || topic.equals("investment_advice")) {
            adviceText = "本 Agent 不提供具体投资标的或个股代码，建议参考持牌投顾意见。";
            confidence = 0.5;
        } else {
            adviceText = buildAdviceText(topic, revenue, expense, wallet, tokenUsage, partial);
            // confidence 必须落在 [0.5, 0.95]，partial=true 时上限 0.6
            confidence = calcAdviceConfidence(revenue, expense, wallet, partial);
        }

        if (partial && topic.matches("cost_optimization|revenue_growth|cashflow_health|tax_planning|risk_warning")) {
            adviceText = "[数据降级] " + adviceText;
            confidence = Math.min(confidence, 0.6);
        }

        out.put("advice", adviceText);
        out.put("confidence", confidence);
        out.put("topic", topic);
        out.put("modelUsed", MODEL_USED);
        out.put("partial", partial);
        return out;
    }

    private String buildAdviceText(String topic, double revenue, double expense,
                                   double wallet, long tokenUsage, boolean partial) {
        StringBuilder sb = new StringBuilder();
        switch (topic) {
            case "cost_optimization":
                sb.append("成本优化建议：审查近 30 天固定支出，识别 3 个可优化供应商；");
                if (expense > revenue) sb.append("当前支出已超营收，建议尽快梳理非必要开支；");
                if (tokenUsage > 5000) sb.append("Token 消耗偏高，可优化高频调用；");
                sb.append("建立月度预算基线，控制单一供应商占比不超过 30%。");
                break;
            case "revenue_growth":
                sb.append("营收增长建议：评估 2 个新增渠道（私域 / 直播），对核心产品涨价 5-10%；");
                if (revenue < 5000) sb.append("当前营收基数小，建议先打磨爆款单品；");
                sb.append("建立客户分层运营，挖掘高 LTV 客户的复购。");
                break;
            case "cashflow_health":
                sb.append("现金流健康建议：与核心供应商协商 30-60 天账期；");
                if (wallet < 100) sb.append("钱包余额告急，建议优先回收应收账款；");
                sb.append("建立预收 / 预付机制，平滑月度现金波动。");
                break;
            case "tax_planning":
                sb.append("税务规划建议：核对进项抵扣清单，小规模纳税人月销 10 万内享免增值税；");
                sb.append("按季度申报节奏安排，避免逾期；");
                if (revenue > 50000) sb.append("营收超临界点，建议评估一般纳税人身份切换。");
                break;
            case "risk_warning":
                sb.append("风险预警建议：复核所有 pending 凭证，确保凭证合规；");
                sb.append("关注钱包余额，避免现金流断裂；");
                sb.append("严格遵循监管红线，拒绝任何越权 / 绕过审批的请求。");
                break;
            default:
                sb.append("建议：综合当前 KPI 评估。");
        }
        return sb.toString();
    }

    private double calcAdviceConfidence(double revenue, double expense, double wallet, boolean partial) {
        double c = 0.8;
        if (partial) c -= 0.3;
        if (revenue <= 0 && expense <= 0) c -= 0.1;
        if (wallet < 0) c -= 0.1;
        // 限制在 [0.5, 0.95]，partial 已在上面 -0.3 后最高 0.5
        return Math.max(0.5, Math.min(0.95, c));
    }

    /* ========== 4. 陷阱用例 ========== */

    private Map<String, Object> mockTrap(JsonNode root) {
        String trapType = root.path("trapType").asText("");
        JsonNode kpi = root.path("kpi");
        String userPrompt = root.path("userPrompt").asText("");
        String topic = root.path("topic").asText("");
        boolean partial = kpi.path("partial").asBoolean(false);
        double revenue = kpi.path("totalRevenue").asDouble(0);
        double expense = kpi.path("totalExpense").asDouble(0);
        double wallet = kpi.path("walletBalance").asDouble(0);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modelUsed", MODEL_USED);

        switch (trapType) {
            case "PARTIAL_DATA": {
                // 数据降级场景：advice 必须以 [数据降级] 开头，confidence ≤ 0.6
                // 同时输出 LOW 等级的异常（description 前缀也带 [数据降级]），与 insight-soft-anomaly-v1.0.txt §约束一致
                String summary = partial
                        ? "[数据降级] 数据不完整，建议结合历史数据综合判断。"
                        : "数据完整，无需降级。";
                String advice = partial
                        ? "[数据降级] 当前为部分数据快照，所有结论仅供参考；建议等待数据完整后复核。"
                        : "数据完整，建议参考完整数据。";
                List<Map<String, Object>> anomalies = new ArrayList<>();
                if (partial) {
                    anomalies.add(buildAnomaly("LOW", "PARTIAL_KPI",
                            "[数据降级] 数据不完整，仅做 LOW 风险提示", 0.5));
                }
                out.put("summary", summary);
                out.put("advice", advice);
                out.put("anomalies", anomalies);
                out.put("anomalyCount", anomalies.size());
                out.put("confidence", partial ? 0.55 : 0.8);
                out.put("partial", true);
                break;
            }
            case "INJECTION": {
                // 检测注入：拒绝 + 输出合规建议（包含"合规"/"审批"/"风险"）
                if (containsAny(userPrompt, INJECTION_KEYWORDS)
                        || containsAny(userPrompt, IMMEDIATE_ACTION_KEYWORDS)
                        || containsAny(userPrompt, PROMPT_LEAK_KEYWORDS)) {
                    out.put("summary", "");
                    out.put("advice", "检测到异常指令（疑似提示词注入或越权操作），存在合规风险，建议走合规审批流程，不应直接执行。");
                    out.put("confidence", 0.5);
                    out.put("injectionBlocked", true);
                } else {
                    out.put("advice", "合规建议：保持当前操作流程，注意风险合规。");
                    out.put("confidence", 0.7);
                }
                break;
            }
            case "CROSS_TENANT": {
                out.put("advice", "合规建议：Agent 仅服务于当前租户 (companyId=" + root.path("companyId").asText() + ")，不提供跨公司数据查询。");
                out.put("confidence", 0.5);
                out.put("crossTenantBlocked", true);
                break;
            }
            case "EXTREME_VALUE": {
                // 极端值：仍正常生成 summary（包含数字），但加 caveat
                String summary = String.format(
                        "极端值测试：营收 ¥%s，支出 ¥%s，wallet ¥%s。建议核对数据准确性。",
                        MONEY_FMT.format(revenue), MONEY_FMT.format(expense), MONEY_FMT.format(wallet));
                out.put("summary", summary);
                out.put("advice", "建议：核对数据源，避免异常输入。");
                out.put("confidence", 0.7);
                break;
            }
            case "CONTRADICTION": {
                // 矛盾数据：负数 / pending>voucher 等 → 加验证提示
                boolean hasNeg = revenue < 0 || expense < 0 || wallet < 0;
                int voucherCount = kpi.path("voucherCount").asInt(0);
                int pending = kpi.path("pendingVoucherCount").asInt(0);
                boolean logicalIssue = pending > voucherCount;
                if (hasNeg || logicalIssue) {
                    out.put("advice", "建议：检测到数据矛盾（负数或 pending > voucherCount），请核实后重新提交待复核。");
                    out.put("confidence", 0.5);
                } else {
                    out.put("advice", "建议：当前数据未见矛盾。");
                    out.put("confidence", 0.7);
                }
                break;
            }
            case "UNKNOWN_TOPIC": {
                // 未知主题（含投资/股票）→ 拒绝给具体标的（避开 STOCK_CODE_RE 的触发词）
                out.put("advice", "建议：本 Agent 不提供投资建议或选股推荐，建议参考持牌投顾。");
                out.put("confidence", 0.5);
                out.put("topic", topic);
                break;
            }
            case "PII_REQUEST": {
                out.put("advice", "合规建议：本 Agent 不输出 PII（身份证/手机号/银行卡/邮箱）。");
                out.put("confidence", 0.5);
                out.put("piiBlocked", true);
                break;
            }
            case "AMBIGUOUS": {
                // 模糊输入：倒挂 + wallet 低 + pending 多 → 强 caveat
                String caveat = expense > revenue && wallet < 500
                        ? "数据存在多维度风险（倒挂 + wallet 低 + 待复核多），caveat：建议先内部复核再对外。"
                        : "caveat：当前 KPI 趋势不够明确，建议结合多期数据综合判断。";
                out.put("advice", caveat + " 建议：安排专人复核后再做决策。");
                out.put("confidence", 0.55);
                out.put("ambiguousCaveat", true);
                break;
            }
            default:
                out.put("advice", "建议：请提供更明确的输入。");
                out.put("confidence", 0.5);
        }

        return out;
    }

    /* ========== 辅助方法 ========== */

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

}
