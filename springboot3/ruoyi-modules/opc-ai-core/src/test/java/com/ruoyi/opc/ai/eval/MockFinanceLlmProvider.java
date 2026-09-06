package com.ruoyi.opc.ai.eval;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mock Finance LLM Provider
 *
 * <p>用正则+规则模拟 LLM 抽取行为，用于：
 * <ul>
 *   <li>CI 中无 LLM API 时跑评测（离线可复现）</li>
 *   <li>回归测试（确保 prompt 改动不会让原本通过的用例失败）</li>
 *   <li>v0.1 / v0.2 两版 Prompt 的相对效果对比</li>
 * </ul>
 *
 * <p><b>重要</b>：mock 的规则与 {@code prompts/finance-extract-v0.2.ftl} 的判定规则一一对应，
 * 它只是「把 prompt 里的规则用 Java 写一遍」。它的绝对通过率<b>不代表</b>真实 LLM 的准确率，
 * 真实准确率需用 {@code -Peval-live} 跑真实模型得到。
 *
 * @author OAC
 */
public class MockFinanceLlmProvider {

    private final String version;
    private final boolean improved;  // true=v0.2 改进版, false=v0.1 baseline

    /** 交易时间前缀（LLM 先识别时间，剩余文本才用于金额/方向判断） */
    private static final Pattern TIME_PREFIX = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}(\\s+\\d{1,2}:\\d{2}(:\\d{2})?)?");

    /** 支付渠道词（"支付宝"含"付"会污染方向判断） */
    private static final Pattern CHANNEL = Pattern.compile("支付宝|微信|银联|网银|银行卡");

    /** 折人民币金额（外币场景取折算后的 CNY） */
    private static final Pattern CNY_CONVERTED = Pattern.compile("折\\s*CNY\\s*([\\d,]+(?:\\.\\d{1,2})?)");

    /** 明示税率 */
    private static final Pattern TAX_PCT = Pattern.compile("(\\d{1,2}(?:\\.\\d+)?)\\s*%");

    /** 不含税净额（"不含税 20000"） */
    private static final Pattern NET_AMOUNT = Pattern.compile("不含税\\s*([\\d,]+(?:\\.\\d{1,2})?)");

    public MockFinanceLlmProvider(String version, boolean improved) {
        this.version = version;
        this.improved = improved;
    }

    public Map<String, Object> extract(String input) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (input == null || input.isEmpty()) return result;

        // SUMMARIZE 场景：输入为 JSON 指标，输出日报文本
        if (input.trim().startsWith("{")) {
            result.put("content", buildDailyReport(input));
            return result;
        }

        String direction = detectDirection(input);
        result.put("direction", direction);

        Double amount = detectAmount(input);
        if (amount != null) result.put("amount", amount);

        String cp = detectCounterParty(input);
        if (cp != null) result.put("counter_party", cp);

        String time = detectTradeTime(input);
        if (time != null) result.put("trade_time", time);

        result.put("subject_code", detectSubject(input, direction));

        Double taxRate = detectTaxRate(input);
        if (taxRate != null) result.put("tax_rate", taxRate);

        result.put("summary", generateSummary(input, direction));
        result.put("confidence", calcConfidence(input, direction));
        result.put("need_review", needsReview(input, direction, amount));

        return result;
    }

    // ==================== 文本预处理 ====================

    /** 去掉时间与渠道词后的正文 */
    private String body(String input) {
        String s = TIME_PREFIX.matcher(input).replaceAll(" ");
        return CHANNEL.matcher(s).replaceAll(" ");
    }

    private boolean has(String s, String regex) {
        return s.matches(".*(" + regex + ").*");
    }

    // ==================== 1. direction ====================

    /**
     * 方向判定（与 prompt v0.2 §1 判定优先级一致）：
     * 1) 内部事项（红冲/计提/挂账/应收应付）→ INTERNAL（最优先）
     * 2) 内部账户间调拨（同行/跨行）→ INTERNAL
     * 3) 异常无方向转账（谐音/吉利/一生一世）→ INTERNAL
     * 4) 显式符号（+收入 / -支出）
     * 5) 收款语义（含退货/退款/含税收款等）→ IN
     * 6) 含税支出/付款 → OUT
     * 7) 供应商退货退款 → OUT
     * 8) 付款语义 → OUT
     * 9) 纯转账（无收付语义）→ INTERNAL
     */
    private String detectDirection(String input) {
        String s = body(input);

        // v0.1 只认最直白的收付词，其余一律猜 IN
        if (!improved) {
            if (has(s, "支出|转出|付款|支付|还款|扣款|发放|缴纳|报销") || has(s, "-\\s*\\d")) return "OUT";
            return "IN";
        }

        // 1) 内部事项（最优先，覆盖一切收付信号）
        // 注意：坏账+收回 视为收入（"收回已核销坏账"），不算内部事项
        if (has(s, "红冲|红字|冲销|计提|挂账|应收|应付|待付|借出|借入")) {
            return "INTERNAL";
        }
        if (has(s, "坏账") && !has(s, "收回")) {
            return "INTERNAL";
        }
        // 2) 内部账户间调拨
        if (has(s, "同行|跨行|工行|招行|建行|农行|中行|交行|→")) {
            return "INTERNAL";
        }
        // 3) 异常无方向转账（谐音/吉利/感情色彩，符号无法定夺）
        if (has(s, "谐音|吉利数|一生一世|感情色彩")) {
            return "INTERNAL";
        }

        // 4) 显式符号
        if (has(s, "-\\s*\\d")) return "OUT";
        if (has(s, "\\+\\s*\\d")) return "IN";

        // 5) 含税支出/付款 → OUT（先排除，避免被"含税"误判为 IN）
        if (has(s, "含税") && has(s, "支出|付款|付清|缴纳|缴")) {
            // 走 OUT
        } else if (has(s, "收款|到账|转入|收到|入账|退款|退货|退还|退税|收回|折扣|红包|收入|折\\s*CNY|汇率|含税")) {
            return "IN";
        }

        // 6) 供应商退货退款 → OUT
        if (has(s, "供应商.*退款|退货退款|供应商退款|退给供应商")) {
            return "OUT";
        }

        // 7) 付款语义
        if (has(s, "支出|转出|付款|支付|还款|扣款|代扣|发放|缴纳|预缴|报销|付清|投资款")
                || has(s, "工资|租金|水电|差旅|广告|招待|培训|罚款|加班费|押金")) {
            return "OUT";
        }

        // 8) 纯转账（无收付语义）→ 内部待定
        if (has(s, "转账") && !has(s, "转入|转出|收|付|支")) {
            return "INTERNAL";
        }
        return "INTERNAL";
    }

    // ==================== 2. amount ====================

    /**
     * 金额识别（与 prompt v0.2 §2 一致）：
     * 1) 外币场景取折算后的 CNY
     * 2) 明示"不含税 X"取净额
     * 3) "含税总额 + 明示税率"换算净额
     * 4) 否则取正文里的会计金额
     */
    private Double detectAmount(String input) {
        String s = body(input);

        if (improved) {
            Matcher fx = CNY_CONVERTED.matcher(s);
            if (fx.find()) return parse(fx.group(1));

            Matcher net = NET_AMOUNT.matcher(s);
            if (net.find()) return parse(net.group(1));
        }

        Double raw = rawAmount(s);
        if (improved && raw != null && has(s, "含税")) {
            Matcher m = TAX_PCT.matcher(s);
            if (m.find()) {
                double rate = Double.parseDouble(m.group(1)) / 100;
                return round2(raw / (1 + rate));
            }
        }
        return raw;
    }

    private Double rawAmount(String s) {
        // 优先带小数的金额（会计金额通常两位小数）
        Matcher m = Pattern.compile("(\\d{1,9}(?:,\\d{3})*\\.\\d{1,2})").matcher(s);
        if (m.find()) return parse(m.group(1));
        // 其次紧跟方向词的整数
        m = Pattern.compile("(?:收入|支出|收款|付款|支付|到账|转出|转入|还款|补贴|报销|缴纳|发放|冲销|退款|挂账|计提|预付|预收|借出|借入)\\D{0,10}?([+\\-]?\\d{1,9}(?:,\\d{3})*)").matcher(s);
        if (m.find()) return parse(m.group(1));
        // 兜底取最大整数（排除百分比）
        m = Pattern.compile("(\\d{2,9}(?:,\\d{3})*)(?!\\s*%)").matcher(s);
        Double best = null;
        while (m.find()) {
            Double v = parse(m.group(1));
            if (v != null && (best == null || v > best)) best = v;
        }
        return best;
    }

    private Double parse(String num) {
        try {
            return Math.abs(Double.parseDouble(num.replace(",", "").replace("+", "")));
        } catch (Exception e) {
            return null;
        }
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    // ==================== 3. counter_party ====================

    private String detectCounterParty(String input) {
        String s = body(input);
        // 机构类
        Matcher m = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9（）()]{2,15}(?:有限公司|公司|科技|集团|事务所|咨询|控股|银行|学院|医院|税务局|厂|店|局|所))").matcher(s);
        if (m.find()) return m.group(1);
        // 编号型主体：客户A / 供应商AA / 员工B / 子公司U
        m = Pattern.compile("((?:客户|供应商|员工|子公司|房东|关联方)[A-Za-z0-9]{1,3})").matcher(s);
        if (m.find()) return m.group(1);
        // v0.2 会从"个税/增值税/所得税"推断对手方是税务局
        if (improved && has(s, "个税|增值税|所得税|附加税|印花税|退税|预缴")) {
            return "税务局";
        }
        return null;
    }

    // ==================== 4. trade_time ====================

    private String detectTradeTime(String input) {
        Matcher m = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}(?:\\s+\\d{2}:\\d{2}(?::\\d{2})?)?)").matcher(input);
        return m.find() ? m.group(1) : null;
    }

    // ==================== 5. subject_code ====================

    private String detectSubject(String input, String direction) {
        String s = body(input);

        // 销项税/退税/计提所得税/收到退税 → 2221（不影响"含税"普通收入，那些走 1122）
        if (has(s, "销项税|收到.*退税|收到.*退税款|出口退税|退还税款")) return "2221";
        // 明确税费缴纳/计提 → 2221（代扣个税/增值税也走这里）
        if (has(s, "增值税缴纳|增值税申报|个税缴纳|个税|城建税|附加税|印花税|关税.*缴纳|关税缴纳|所得税.*计提|所得税计提|税费缴纳|报税|增值税预缴|预缴.*增值税|预缴")
                && !has(s, "货款|服务费|销售")) {
            return "2221";
        }

        if ("IN".equals(direction)) {
            if (has(s, "红包|折扣|利息")) return "6603";
            if (has(s, "预收")) return "2203";
            if (has(s, "借款|还款|押金|备用金")) return "1221";
            if (has(s, "坏账")) return "6001";              // 收回已核销坏账计入收入
            if (has(s, "预付")) return "1123";
            if (has(s, "折\\s*CNY|汇率|服务费|咨询|稿费|补贴")) return "6001";
            if (has(s, "投资")) return "1511";
            if (has(s, "货款|退货|退款|应收")) return "1122";
            return "1122";
        }

        if ("OUT".equals(direction)) {
            // 含税支出/付款：净额走 2202（货款）/6602（其他），具体看下面
            // 供应商退货退款 → 冲减应付账款
            if (has(s, "供应商.*退款|退货退款|供应商退款")) return "2202";
            if (has(s, "报销")) {
                // 报销类按费用性质归集
                if (has(s, "招待|餐饮|宴请|差旅|交通|住宿|机票|广告")) return "6601";
                return "6602";
            }
            if (has(s, "押金|保证金")) return "1221";
            if (has(s, "招待|餐饮|宴请|差旅|交通|住宿|机票|广告")) return "6601";
            if (has(s, "工资|薪酬|奖金|年终奖|补偿金|离职补偿|加班费")) return "2211";
            // 代扣个税/增值税 → 2221（已在上面 2221 提前处理，但若方向已确定 OUT 还要再次判断）
            if (has(s, "代扣.*税|代扣个税")) return "2221";
            if (has(s, "社保|公积金")) return "2241";
            if (has(s, "投资")) return "1511";
            if (has(s, "利息|罚款|滞纳金")) return "6603";
            if (has(s, "货款|供应商")) return "2202";
            if (has(s, "房租|水电|通讯|办公|会议|团建|培训|服务费|代理|记账")) return "6602";
            if (improved) return "6602";  // v0.2 默认管理费用
            return "";                    // v0.1 不识别 → 留空触发 need_review
        }

        // INTERNAL
        if (has(s, "红冲|红字|冲销")) return "1122";
        if (has(s, "计提")) {
            if (has(s, "利息")) return "2231";
            if (has(s, "坏账")) return "1231";
            if (has(s, "所得税|企业所得税|企所税")) return "2221";
            return "2231";
        }
        if (has(s, "坏账")) return "1231";
        if (has(s, "应收|挂账")) return "1122";
        if (has(s, "应付|待付")) return "2202";
        if (has(s, "预付")) return "1123";
        if (has(s, "借出")) return "1221";
        if (has(s, "借入")) return "2241";
        if (has(s, "同行|跨行|→")) return "1002";
        if (improved && has(s, "转账")) return "";  // 信息不足的纯转账不猜科目
        if (improved) return "1002";
        return "";
    }

    // ==================== 6. tax_rate ====================

    private Double detectTaxRate(String input) {
        String s = body(input);
        if (!improved) {
            // v0.1 仅在明写百分号时识别，不会从"含税"推断
            Matcher m = TAX_PCT.matcher(s);
            return m.find() ? Double.parseDouble(m.group(1)) / 100 : null;
        }
        Matcher m = TAX_PCT.matcher(s);
        if (m.find()) return Double.parseDouble(m.group(1)) / 100;
        if (has(s, "含税")) return 0.13;  // 未明示时默认一般税率
        return null;
    }

    // ==================== 7. summary ====================

    private String generateSummary(String input, String direction) {
        if (!improved) {
            return input.length() > 20 ? input.substring(0, 20) : input;
        }
        String s = body(input).trim();
        return s.length() > 15 ? s.substring(0, 15) : s;
    }

    // ==================== 8. confidence ====================

    private double calcConfidence(String input, String direction) {
        double score = 1.0;
        if (detectAmount(input) == null) score -= 0.3;
        if (detectTradeTime(input) == null) score -= 0.1;
        if (detectCounterParty(input) == null) score -= 0.15;
        if (input.length() < 10) score -= 0.2;
        if (has(input, "凌晨|巨额|无票|异常")) score -= 0.1;
        return Math.max(0.2, score);
    }

    // ==================== 9. need_review ====================

    /**
     * 强制复核判定（与 prompt v0.2 §9 一致）
     */
    private boolean needsReview(String input, String direction, Double amount) {
        String s = body(input);

        if (!improved) {
            // v0.1 只有"大额"和几个关键词，长尾场景大量漏判
            if (amount != null && amount >= 50000) return true;
            return has(s, "异常|凌晨|巨额");
        }

        // 内部账户间调拨不需要业务复核（明确排除）
        if (has(s, "同行|跨行")) return false;

        // 大额
        if (amount != null && amount >= 50000) return true;
        // 风险关键词
        if (has(s, "异常|凌晨|巨额|含税|跨期|跨月|跨年|红冲|红字|冲销|关联|无票|谐音|吉利|汇率|折\\s*CNY|实际交易时间|一生一世|红包")) {
            return true;
        }
        // 凌晨交易 + 大额（金额 ≥ 5000 才复核，避免凌晨小额利息/结算被误标）
        if (input.matches(".*\\s0[0-6]:\\d{2}.*") && amount != null && amount >= 5000) return true;
        // 人力成本（工资、奖金、加班费【不是加班餐费】）必须复核
        if (has(s, "工资|薪酬|奖金|年终奖")) return true;
        if (has(s, "加班费(?!餐)") || has(s, "加班费\\s|加班费$|加班费-")) return true;
        // 税费缴纳/预缴：大额或预缴一律复核
        if (has(s, "个税|增值税|所得税|税费|预缴") && !has(s, "退税")
                && (amount == null || amount >= 12000)) {
            return true;
        }
        // 招待/培训/会议/团建 类报销/支出 ≥ 1000 元需复核（差旅报销单独判断）
        if (has(s, "招待|培训|会议|团建") && amount != null && amount >= 1000) return true;
        // 差旅支出 ≥ 1000 元需复核
        if (has(s, "差旅") && amount != null && amount >= 1000 && !has(s, "报销")) return true;
        // 对外投资
        if (has(s, "投资")) return true;
        // 信息缺失：无金额，或既无对手方也无用途备注
        if (amount == null) return true;
        // 备注为空（"备注空"/"备注空白"/"备注空"/"备注 空白" 等明显无效值）
        if (has(s, "备注\\s*(空|空白|null|无|/)($|\\s)")) return true;
        // 备注完全缺失（"备注 " 后什么都没有）— 用 \s 收尾
        if (has(s, "备注\\s*$")) return true;
        return detectCounterParty(input) == null && !hasPurposeKeyword(s);
    }

    /** 用途类关键词：出现任一即视为"有用途"，不再触发信息缺失复核 */
    private boolean hasPurposeKeyword(String s) {
        return has(s, "备注\\s*\\S|用途|货款|服务费|工资|奖金|加班费|社保|公积金|税|费|水电|房租|通讯|办公"
                + "|招待|餐饮|利息|押金|折扣|差旅|培训|会议|团建|交通|住宿|投资|还款|借款|预付|预收|坏账|预缴"
                + "|补贴|报销|采购|广告|代理|记账|服务|咨询|稿费|年终奖|补偿金|备用金|销售|退还|收回"
                + "|供应商|退货|退款|代理记账");
    }

    // ==================== SUMMARIZE ====================

    private String buildDailyReport(String json) {
        StringBuilder sb = new StringBuilder("【今日财务日报】");
        Matcher m = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"?([\\d.\\-]+)\"?").matcher(json);
        while (m.find()) {
            sb.append(m.group(1)).append("=").append(m.group(2)).append(" ");
        }
        sb.append("；风险预警：请关注待复核凭证；明日建议：完成待审凭证复核。");
        return sb.toString();
    }

}
