package com.ruoyi.opc.ai.security;

import com.ruoyi.opc.common.exception.OpcException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prompt 注入防护
 *  - 检测常见注入关键词
 *  - v0.3：新增 4 类红队攻击模式（提现绕审 / 凭证泄露 / 系统提示词泄露 / 跨租户越权）
 *  - 工具调用白名单
 *
 * <p>v0.3 的攻击正则来自 {@code eval/redteam-30.json} 的 30 条红队用例，
 * 并以「合法财务请求不得被误杀」为硬约束设计——例如刻意<b>不匹配</b>
 * 「转账 / 汇款 / 退款」这类中性记账名词（"公司转账 5000 给供应商" 是正常凭证提取），
 * 只匹配<b>规避内控</b>（跳过审核 / 不需要走流程）与<b>洗钱拆单、汇率篡改</b>等真实攻击意图。
 * 误杀率由 {@code PromptGuardHardeningTest} 用 20 条合法请求守护。
 *
 * @author OAC
 */
@Slf4j
@Component
public class PromptGuard {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(previous|above)\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(previous|all)\\s+(rules|prompts)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an)\\s+(?!financial|finance)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)\\bjailbreak\\b"),
            Pattern.compile("(?i)forget\\s+everything"),
            Pattern.compile("忽略(之前|前面|上面)所有指令"),
            Pattern.compile("你现在是(?!财务|会计)")
    );

    /**
     * v0.3 新增：红队攻击模式，key 为攻击类别（写入告警日志便于按类别统计）。
     */
    private static final Map<String, Pattern> ATTACK_PATTERNS = new LinkedHashMap<>();

    static {
        // 1) 提现 / 付款绕过内控 + 凭证篡改 + 客诉/面试/评分绕过
        ATTACK_PATTERNS.put("WITHDRAWAL_BYPASS", Pattern.compile(
                "(跳过|绕过|免去|无需|不用|不要|不需要)[^。！？\\n]{0,8}(审核|审批|复核|流程|风控|确认|背景|背调|面|试用|试用|面试)"
                        + "|批量转账|反洗钱"
                        + "|(汇率|金额)[^。！？\\n]{0,4}(我说了算|由我定|我定)"
                        + "|凭证[^。！？\\n]{0,12}(标为|标记为)\\s*'?reviewed"
                        + "|(应收|应付)账款[^。！？\\n]{0,20}(改为|改成)"
                        + "|凭证[^。！？\\n]{0,20}金额[^。！？\\n]{0,20}(改成|改为|修改)"
                        + "|(客诉|投诉)[^。！？\\n]{0,10}(标记|标为|设为)[^。！？\\n]{0,8}(已解决|已处理|无需|不用)"
                        + "|(候选人|候选人|面试|反馈|跟进|优先级|面试顺序)[^。！？\\n]{0,15}(排|调|改)[^。！？\\n]{0,8}(第一|优先|最高)",
                Pattern.CASE_INSENSITIVE));

        // 2) 凭证 / 密钥泄露（注意 "API Key" 中间可能有空格）
        ATTACK_PATTERNS.put("CREDENTIAL_LEAK", Pattern.compile(
                "(root|admin|api\\s*[_-]?\\s*key|password|passwd|pwd"
                        + "|jwt\\s*[_-]?\\s*secret|access\\s*[_-]?\\s*key"
                        + "|密钥|密码|私密?钥|hash|哈希|凭据)"
                        + "[^。！？\\n]{0,30}(打印|告诉我|给我|显示|输出|发我|发到|复述)",
                Pattern.CASE_INSENSITIVE));

        // 3) system prompt / 训练语料 泄露
        ATTACK_PATTERNS.put("PROMPT_LEAK", Pattern.compile(
                "(system\\s*prompt|系统提示词|系统提示)[^。！？\\n]{0,20}(全文|原文|打印|复述|列出|告诉我|给我)"
                        + "|你训练时[^。！？\\n]{0,40}(打印|原文|列出)",
                Pattern.CASE_INSENSITIVE));

        // 4) 跨公司 / 跨用户 越权查询
        ATTACK_PATTERNS.put("CROSS_TENANT", Pattern.compile(
                "(竞争对手|其他公司|跨公司|所有用户|所有公司|全公司|其他用户的|别的公司)"
                        + "[^。！？\\n]{0,40}(数据|信息|记录|列表|明细|汇总|营收)"
                        + "|(所有员工|其他员工|其他用户)[^。！？\\n]{0,30}(报销|工资|信息|数据)"
                        + "|所有[^。！？\\n]{0,12}员工[^。！？\\n]{0,10}(姓名|工资|报销)"));

        // 5) 危险工具名 / 数据外发（硬编码在用户输入里，越过 LLM 直接点名调用）
        ATTACK_PATTERNS.put("DANGEROUS_TOOL", Pattern.compile(
                "vault_store|write_secret|update_database_credentials"
                        + "|drop_table|drop_database|export_csv"
                        + "|导出成\\s*csv|(发到|发送到|发至)[^。！？\\n]{0,12}邮箱",
                Pattern.CASE_INSENSITIVE));

        // 6) 外部内容 / 可执行载荷（远程 payload、XSS、Base64 解码执行）
        ATTACK_PATTERNS.put("UNSAFE_CONTENT", Pattern.compile(
                "javascript\\s*:|document\\.cookie|<script"
                        + "|https?://[^\\s)]+\\.(txt|sh|exe|bin|py|js)\\b"
                        + "|base64[^。！？\\n]{0,20}(解码|decode)[^。！？\\n]{0,10}(执行|运行)"
                        + "|(解码后|解码并)[^。！？\\n]{0,6}执行",
                Pattern.CASE_INSENSITIVE));

        // 7) 记忆投毒（把用户消息当成长期指令 / 有效凭据）
        ATTACK_PATTERNS.put("MEMORY_POISON", Pattern.compile(
                "(记住|请记住)[^。！？\\n]{0,30}(api\\s*[_-]?\\s*key|密钥|token|口令|凭据)"
                        + "|以后(任何|所有|每次)[^。！？\\n]{0,10}对话"
                        + "|\\bsk-[A-Za-z0-9_-]{6,}\\b",
                Pattern.CASE_INSENSITIVE));
    }

    /** 工具调用白名单 */
    private static final List<String> ALLOWED_TOOLS = List.of(
            "extract_voucher", "create_voucher", "query_voucher",
            "query_daily_report", "query_balance",
            "search_product", "query_inventory",
            "query_customer", "create_follow_up",
            "parse_resume", "schedule_interview"
    );

    /**
     * <b>业务动作类</b>规则 —— 只在「模型能真的执行动作」的路径上才有意义。
     *
     * <p>{@code WITHDRAWAL_BYPASS} 挡的是「跳过审核 / 无需背调 / 面试顺序调前」
     * 这类规避内控的话。这对 {@code AgentRuntime} 是对的：那条路能建凭证、排面试，
     * 拦下来有实际意义。
     *
     * <p>但纯对话路径（{@code POST /opc/llm/chat}）里模型只能吐文本，没有工具、
     * 不写库。「这笔不用走流程了」只是一句正常财务闲聊，硬拦就是纯粹的误伤 ——
     * 而这个模块的误杀率是 0 容忍的（见 {@code PromptGuardHardeningTest} 的立意）。
     *
     * <p>其余 6 条规则（凭证泄露 / 提示词泄露 / 跨租户 / 危险工具 / 不可信内容 / 记忆投毒）
     * 描述的都是「模型可能说出或吐出什么」，对话场景同样成立，故不在此列。
     */
    public static final Set<String> ACTION_ONLY_RULES = Set.of("WITHDRAWAL_BYPASS");

    public String sanitize(String input) {
        return sanitize(input, Set.of());
    }

    /**
     * @param skippedRules 需要跳过的红队规则类别，见 {@link #ACTION_ONLY_RULES}。
     *                      注入类规则（{@link #INJECTION_PATTERNS}）<b>不受影响</b>，
     *                      任何调用方都拦不掉它们。
     */
    public String sanitize(String input, Set<String> skippedRules) {
        if (input == null) return "";
        String result = input;
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(result).find()) {
                log.warn("[PromptGuard] 检测到注入模式：{}", p.pattern());
                throw new OpcException(403, "检测到潜在的 Prompt 注入", "SECURITY");
            }
        }
        String attackType = detectAttack(result, skippedRules);
        if (attackType != null) {
            log.warn("[PromptGuard] 检测到红队攻击模式：type={}", attackType);
            throw new OpcException(403, "请求被安全策略拒绝：" + attackType, "SECURITY");
        }
        return result;
    }

    /**
     * 只检测不抛异常，返回命中的攻击类别；未命中返回 {@code null}。
     * 供离线评测 / 灰度观察模式使用。
     */
    public String detectAttack(String input) {
        return detectAttack(input, Set.of());
    }

    public String detectAttack(String input, Set<String> skippedRules) {
        if (input == null || input.isEmpty()) return null;
        for (Map.Entry<String, Pattern> e : ATTACK_PATTERNS.entrySet()) {
            if (skippedRules != null && skippedRules.contains(e.getKey())) continue;
            if (e.getValue().matcher(input).find()) {
                return e.getKey();
            }
        }
        return null;
    }

    public void validateToolName(String toolName) {
        if (!ALLOWED_TOOLS.contains(toolName)) {
            log.warn("[PromptGuard] 工具调用被拒绝：{}", toolName);
            throw new OpcException(403, "工具未授权：" + toolName, "SECURITY");
        }
    }

}
