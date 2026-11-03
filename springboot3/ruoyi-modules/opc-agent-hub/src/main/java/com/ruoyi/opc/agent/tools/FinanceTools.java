package com.ruoyi.opc.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.ai.tools.ToolRegistry;
import com.ruoyi.opc.ai.tools.ToolSpec;
import com.ruoyi.opc.common.exception.OpcException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 财务数字员工的工具集 —— <b>真查库</b>，不再是写死的假返回。
 *
 * <p><b>为什么住在 agent-hub 而不是 opc-ai-core</b>：
 * 旧版 {@code FinanceTools} 住在 {@code com.ruoyi.opc.ai.tools.builtin}，而 opc-finance /
 * opc-insight / opc-ai-core 三个进程都依赖 opc-ai-core，也都扫得到那个包 ——
 * finance 会自己 Feign 调自己，insight 会因为新的 {@code @FeignClient} 注册不进来直接启动失败。
 * 搬到 {@code com.ruoyi.opc.agent.tools} 之后，opc-agent-hub 的 pom 里没有 opc-finance /
 * opc-billing 的依赖，另外三个进程的 classpath 上根本没有这些类，扫不到。
 *
 * <p><b>只留 3 个工具</b>：{@code query_bank_flow} / {@code query_voucher} / {@code query_balance}。
 * 旧版的 {@code extract_voucher} / {@code create_voucher} / {@code query_daily_report}
 * 全部下线 —— 它们返回的是编出来的值（{@code revenue:0}、{@code V-PLACEHOLDER}），
 * 数字员工拿去写日报，写出来的就是模型现编的数字。
 *
 * <p><b>跨租户护栏</b>：{@code companyId} <b>只</b>从 {@code ToolExecutor} 注入的 context 取，
 * args 里写什么都不影响。args 里就算塞了 {@code "companyId": 999} 也一样被忽略。
 *
 * <p><b>区间缺省不在这层补</b>：{@code fromDate} / {@code toDate} 不传就不传，
 * 缺省由 finance 端算（最早一条 ~ 昨天）。固定「近 30 天」过了 2026-11-21 就空了，
 * 对账断言会退化成「两个空集相等」—— 永远绿，等于没有。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinanceTools {

    /** 来源表名 —— 工具自己写死，远端说了不算（工具知道自己查了哪张表） */
    public static final String SOURCE_BANK_FLOW = "opc_finance_bank_flow";
    public static final String SOURCE_VOUCHER = "opc_finance_voucher";
    public static final String SOURCE_WALLET = "opc_wallet";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final ToolSpec SPEC_BANK_FLOW = ToolSpec.of("query_bank_flow",
            "查询指定公司在一段时间内的银行流水：条数、收入合计、支出合计、净流入与明细。"
                    + "fromDate/toDate 可选，缺省由服务端计算（最早一条 ~ 昨天）。",
            Map.of("type", "object",
                    "properties", Map.of(
                            "fromDate", Map.of("type", "string",
                                    "description", "起始日 yyyy-MM-dd（可选，缺省=该公司最早一条流水）"),
                            "toDate", Map.of("type", "string",
                                    "description", "截止日 yyyy-MM-dd（可选，缺省=昨天）"))));

    public static final ToolSpec SPEC_VOUCHER = ToolSpec.of("query_voucher",
            "查询指定公司在一段时间内的会计凭证：条数、贷方合计（收入）、借方合计（支出）与明细。"
                    + "fromDate/toDate 可选，缺省由服务端计算（最早一条 ~ 昨天）。",
            Map.of("type", "object",
                    "properties", Map.of(
                            "fromDate", Map.of("type", "string",
                                    "description", "起始日 yyyy-MM-dd（可选，缺省=该公司最早一条凭证）"),
                            "toDate", Map.of("type", "string",
                                    "description", "截止日 yyyy-MM-dd（可选，缺省=昨天）"))));

    public static final ToolSpec SPEC_BALANCE = ToolSpec.of("query_balance",
            "查询指定公司的钱包余额与币种（没有区间概念）。",
            Map.of("type", "object", "properties", Map.of()));

    private final ToolRegistry registry;
    private final FinanceToolClient financeClient;
    private final BillingToolClient billingClient;

    @PostConstruct
    public void register() {
        registry.register("query_bank_flow", SPEC_BANK_FLOW, this::queryBankFlow);
        registry.register("query_voucher", SPEC_VOUCHER, this::queryVoucher);
        registry.register("query_balance", SPEC_BALANCE, this::queryBalance);
        log.info("[FinanceTools] 已注册 3 个真工具: query_bank_flow / query_voucher / query_balance");
    }

    // ==================== 3 个工具的实现 ====================

    /**
     * 银行流水区间查询。
     *
     * @param argsJson 仅认 fromDate / toDate（其它键一律忽略，包括 companyId）
     * @param context  由 ToolExecutor 注入，companyId 只从这里取
     */
    public String queryBankFlow(String argsJson, Map<String, Object> context) {
        Long companyId = requireCompanyId(context);
        Args args = Args.parse(argsJson);
        Map<String, Object> data = requireData(financeClient.flows(companyId, args.fromDate(), args.toDate()));
        return toJson(SOURCE_BANK_FLOW, companyId, data);
    }

    /**
     * 会计凭证区间查询（totalIn = 贷方/收入合计，totalOut = 借方/支出合计）。
     */
    public String queryVoucher(String argsJson, Map<String, Object> context) {
        Long companyId = requireCompanyId(context);
        Args args = Args.parse(argsJson);
        Map<String, Object> data = requireData(financeClient.vouchers(companyId, args.fromDate(), args.toDate()));
        return toJson(SOURCE_VOUCHER, companyId, data);
    }

    /**
     * 钱包余额查询 —— 没有区间概念，返回 {source, companyId, balance, currency}。
     */
    public String queryBalance(String argsJson, Map<String, Object> context) {
        Long companyId = requireCompanyId(context);
        Map<String, Object> data = requireData(billingClient.wallet(companyId));
        return toJson(SOURCE_WALLET, companyId, data);
    }

    // ==================== 关键实现点 ====================

    /**
     * 取 companyId —— <b>只从 context 取</b>，args 里的 companyId 一律不读。
     *
     * <p>context 缺失 / companyId &lt;= 0（含负数）→ 抛异常。宁可工具失败，也不能在
     * 租户不明的情况下拿一个「大概是 0」的 companyId 去查库；负数同理 ——
     * 放行的话查不到记录会表现为「查到了 0 条」，而不是明确失败。
     */
    private Long requireCompanyId(Map<String, Object> context) {
        if (context == null) {
            throw new OpcException("工具上下文缺少 companyId");
        }
        Object raw = context.get("companyId");
        if (raw == null) {
            throw new OpcException("工具上下文缺少 companyId");
        }
        Long companyId;
        if (raw instanceof Number n) {
            companyId = n.longValue();
        } else {
            try {
                companyId = Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                throw new OpcException("工具上下文的 companyId 非法: " + raw);
            }
        }
        if (companyId == null || companyId <= 0L) {
            throw new OpcException("工具上下文缺少 companyId");
        }
        return companyId;
    }

    /**
     * 校验远端响应 —— <b>绝不返回空 Map 冒充成功</b>。
     *
     * <p>远端失败就抛 {@link OpcException}，让工作流这次运行明确失败；
     * 返回一个空 JSON 的话，下游 LLM 会把「没查到」写成「查到了，没有流水」。
     */
    private Map<String, Object> requireData(R<Map<String, Object>> r) {
        if (r == null) {
            throw new OpcException("远端服务返回失败: 空响应");
        }
        if (r.getCode() != R.SUCCESS) {
            throw new OpcException("远端服务返回失败: " + r.getMsg());
        }
        if (r.getData() == null) {
            throw new OpcException("远端服务返回失败: 数据为空");
        }
        return r.getData();
    }

    /**
     * 拼装工具返回值。
     *
     * <ul>
     *   <li>{@code source} —— <b>永远覆盖</b>。来源表名由工具自己写死，远端说了不算。</li>
     *   <li>{@code companyId} —— <b>putIfAbsent 语义</b>：远端返回的是权威值，保留；
     *       远端没返回才用 context 的补。</li>
     *   <li>其余字段原样透传，<b>不加工</b> —— fromDate/toDate 也一样，
     *       「哪段区间」只有 finance 一个说了算。</li>
     * </ul>
     */
    private String toJson(String source, Long companyId, Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(data);
        // source 永远由工具自己写死 —— 远端响应里就算带了 source 也不算数。
        // 这一行是【唯一】覆盖 source 的地方：放在 putAll 之后，远端说了不算。
        out.put("source", source);
        // companyId：远端返回的优先（putIfAbsent 语义）
        out.putIfAbsent("companyId", companyId);
        try {
            return MAPPER.writeValueAsString(out);
        } catch (Exception e) {
            throw new OpcException("工具结果序列化失败: " + e.getMessage());
        }
    }

    /**
     * 工具入参 —— 只认 {@code fromDate} / {@code toDate} 两个可选字符串。
     *
     * <p><b>忽略任何其它键</b>（包括 companyId）：companyId 只从 context 取，
     * args 里写了也改不了租户。
     */
    private record Args(String fromDate, String toDate) {

        static Args parse(String argsJson) {
            if (argsJson == null || argsJson.isBlank()) {
                return new Args(null, null);
            }
            Map<String, Object> map;
            try {
                map = MAPPER.readValue(argsJson, new TypeReference<LinkedHashMap<String, Object>>() {
                });
            } catch (Exception e) {
                throw new OpcException("工具参数解析失败: " + e.getMessage());
            }
            if (map == null) {
                return new Args(null, null);
            }
            return new Args(text(map.get("fromDate")), text(map.get("toDate")));
        }

        private static String text(Object v) {
            if (v == null) {
                return null;
            }
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : s;
        }
    }

}
