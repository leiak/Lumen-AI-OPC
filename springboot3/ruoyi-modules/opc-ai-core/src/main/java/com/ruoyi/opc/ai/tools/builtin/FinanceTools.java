package com.ruoyi.opc.ai.tools.builtin;

import com.ruoyi.opc.ai.tools.ToolRegistry;
import com.ruoyi.opc.ai.tools.ToolSpec;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 财务 Agent 工具集（内置）
 *  - extract_voucher  从原始流水提取会计要素
 *  - query_voucher    查询凭证
 *  - create_voucher   创建凭证（带审核）
 *  - query_daily_report 查询日报
 *  - query_balance    查询账户余额
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceTools {

    private final ToolRegistry registry;

    @PostConstruct
    public void register() {
        registry.register("extract_voucher", this::extractVoucher);
        registry.register("query_voucher", this::queryVoucher);
        registry.register("create_voucher", this::createVoucher);
        registry.register("query_daily_report", this::queryDailyReport);
        registry.register("query_balance", this::queryBalance);
    }

    public String extractVoucher(String argsJson, Map<String, Object> context) {
        // 实际实现：通过 OpenFeign 调用 opc-finance 服务
        return "{\"status\":\"extracted\",\"need_review\":true}";
    }

    public String queryVoucher(String argsJson, Map<String, Object> context) {
        return "{\"vouchers\":[]}";
    }

    public String createVoucher(String argsJson, Map<String, Object> context) {
        return "{\"voucher_id\":\"V-PLACEHOLDER\",\"status\":\"DRAFT\",\"need_review\":true}";
    }

    public String queryDailyReport(String argsJson, Map<String, Object> context) {
        return "{\"date\":\"2026-09-03\",\"revenue\":0,\"expense\":0}";
    }

    public String queryBalance(String argsJson, Map<String, Object> context) {
        return "{\"balance\":0.00}";
    }

    /** 工具规格（供前端展示 / API 列表） */
    public static Map<String, ToolSpec> specs() {
        return Map.of(
                "extract_voucher", ToolSpec.of("extract_voucher",
                        "从原始银行/支付流水中提取会计要素，返回结构化 JSON",
                        Map.of("type", "object", "properties",
                                Map.of("raw_text", Map.of("type", "string", "description", "流水文本")),
                                "required", java.util.List.of("raw_text"))),
                "create_voucher", ToolSpec.of("create_voucher",
                        "创建一条会计凭证（默认 DRAFT 状态，需人工审核）",
                        Map.of("type", "object", "properties",
                                Map.of("date", Map.of("type", "string"),
                                        "summary", Map.of("type", "string"),
                                        "entries", Map.of("type", "array")))),
                "query_daily_report", ToolSpec.of("query_daily_report",
                        "查询指定日期的财务日报",
                        Map.of("type", "object", "properties",
                                Map.of("date", Map.of("type", "string"))))
        );
    }

}
