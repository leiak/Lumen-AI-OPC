package com.ruoyi.opc.ai.security;

import com.ruoyi.opc.common.exception.OpcException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt 注入防护
 *  - 检测常见注入关键词
 *  - 工具调用白名单
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
            Pattern.compile("(?i)forget\\s+everything")
    );

    /** 工具调用白名单 */
    private static final List<String> ALLOWED_TOOLS = List.of(
            "extract_voucher", "create_voucher", "query_voucher",
            "query_daily_report", "query_balance",
            "search_product", "query_inventory",
            "query_customer", "create_follow_up",
            "parse_resume", "schedule_interview"
    );

    public String sanitize(String input) {
        if (input == null) return "";
        String result = input;
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(result).find()) {
                log.warn("[PromptGuard] 检测到注入模式：{}", p.pattern());
                throw new OpcException(403, "检测到潜在的 Prompt 注入", "SECURITY");
            }
        }
        return result;
    }

    public void validateToolName(String toolName) {
        if (!ALLOWED_TOOLS.contains(toolName)) {
            log.warn("[PromptGuard] 工具调用被拒绝：{}", toolName);
            throw new OpcException(403, "工具未授权：" + toolName, "SECURITY");
        }
    }

}
