package com.ruoyi.opc.agent.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.ai.tools.ToolRegistry;
import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link FinanceTools} 单元测试 —— 数字员工的工具层真的在查库，不是写死的假返回。
 *
 * <p>覆盖 4 条主线：
 * <ul>
 *   <li><b>假工具下线</b>：注册表里只剩 3 个真工具，
 *       {@code query_daily_report} / {@code create_voucher} / {@code extract_voucher}
 *       一个都不在（健康检查第 26.5 条断言的就是这条）。</li>
 *   <li><b>跨租户护栏</b>：companyId 只从 context 取，args 里写 {@code companyId:999} 无效。</li>
 *   <li><b>不加工</b>：远端返回什么就透传什么，{@code source} 由工具自己写死。</li>
 *   <li><b>失败不冒充成功</b>：远端失败就抛 {@link OpcException}，不返回空 JSON。</li>
 * </ul>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
class FinanceToolsTest {

    /**
     * 测试里解析工具返回值用的 mapper —— <b>刻意开了 USE_BIG_DECIMAL_FOR_FLOATS</b>，
     * 与线上 application.yml 的配置一致（见 ToolJacksonBigDecimalTest）。
     * 这样断言「金额是 BigDecimal 且 scale=2」才立得住：
     * Double 会直接不相等，而不是被 {@code doubleValue()} 悄悄放过。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Mock
    private FinanceToolClient financeClient;

    @Mock
    private BillingToolClient billingClient;

    private ToolRegistry registry;
    private FinanceTools tools;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        tools = new FinanceTools(registry, financeClient, billingClient);
        tools.register();
    }

    // ==================== fixtures ====================

    /** finance 端 {@code FlowRangeVo} 的形状（金额一律 BigDecimal scale=2） */
    private static Map<String, Object> flowData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companyId", 2L);
        m.put("fromDate", "2026-10-07");
        m.put("toDate", "2026-11-01");
        m.put("count", 6);
        m.put("totalIn", new BigDecimal("51892.00"));
        m.put("totalOut", new BigDecimal("32430.00"));
        m.put("net", new BigDecimal("19462.00"));
        m.put("yesterdayCount", 0);
        m.put("yesterdayIn", new BigDecimal("0.00"));
        m.put("yesterdayOut", new BigDecimal("0.00"));
        m.put("items", List.of());
        // 模拟「远端乱说来源」—— 来源表名由工具自己写死,远端说了不算。
        // 这个键是 F1 的关键:没有它,「source 永远覆盖」这条语义就没测试守着。
        m.put("source", "opc_wrong_table");
        return m;
    }

    /** finance 端 {@code VoucherRangeVo} 的形状（totalIn = 贷方/收入合计） */
    private static Map<String, Object> voucherData() {
        Map<String, Object> m = flowData();
        m.put("totalIn", new BigDecimal("35400.00"));
        m.put("totalOut", new BigDecimal("12000.00"));
        m.put("net", new BigDecimal("23400.00"));
        return m;
    }

    /** billing 端 {@code WalletAggVo} 的形状 */
    private static Map<String, Object> walletData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companyId", 2L);
        m.put("balance", new BigDecimal("2860.00"));
        m.put("currency", "CNY");
        return m;
    }

    private static Map<String, Object> parse(String json) throws Exception {
        return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    // ==================== 注册表 ====================

    @Test
    @DisplayName("register — 只发布 3 个真工具，一个不多一个不少")
    void register_publishesExactlyThreeTools() {
        assertEquals(3, registry.names().size(), "注册表里应当恰好 3 个工具");
        assertEquals(
                java.util.Set.of("query_bank_flow", "query_voucher", "query_balance"),
                java.util.Set.copyOf(registry.names()),
                "注册表里的工具名应当恰好是这三个");
    }

    @Test
    @DisplayName("register — 假工具全部下线（query_daily_report / create_voucher / extract_voucher）")
    void register_doesNotPublishFakeTools() {
        for (String fake : List.of("query_daily_report", "create_voucher", "extract_voucher")) {
            assertFalse(registry.exists(fake), "假工具 " + fake + " 必须已经下线");
            assertFalse(registry.names().contains(fake));
        }
    }

    // ==================== query_bank_flow ====================

    @Test
    @DisplayName("queryBankFlow — companyId 只从 context 取，args 里写 companyId:999 无效")
    void queryBankFlow_companyIdComesFromContextOnly() {
        when(financeClient.flows(2L, null, null)).thenReturn(R.ok(flowData()));

        tools.queryBankFlow("{\"companyId\":999}", Map.of("companyId", 2L));

        verify(financeClient).flows(2L, null, null);
    }

    @Test
    @DisplayName("queryBankFlow — 返回带 source 的 JSON，汇总与区间原样透传（不加工）")
    void queryBankFlow_returnsSourceTaggedJson() throws Exception {
        when(financeClient.flows(2L, null, null)).thenReturn(R.ok(flowData()));

        String json = tools.queryBankFlow("{}", Map.of("companyId", 2L));
        Map<String, Object> out = parse(json);

        assertEquals("opc_finance_bank_flow", out.get("source"), "source 必须是来源表名");
        assertEquals(6, ((Number) out.get("count")).intValue());
        assertEquals(new BigDecimal("51892.00"), out.get("totalIn"),
                "金额必须是 BigDecimal 且 scale=2 —— Double / scale 退化都要被这条抓住");
        assertEquals(new BigDecimal("32430.00"), out.get("totalOut"));
        assertEquals("2026-10-07", out.get("fromDate"), "fromDate 原样透传，不重算");
        assertEquals("2026-11-01", out.get("toDate"), "toDate 原样透传，不重算");
        assertEquals(2L, ((Number) out.get("companyId")).longValue());
        assertTrue(json.contains("\"totalIn\":51892.00"),
                "序列化后的原文里金额必须带 2 位小数，不能变成 51892.0");
    }

    @Test
    @DisplayName("querySource — source 由工具写死，远端乱说的来源表名不算数（F1）")
    void querySource_toolAlwaysOverridesRemoteSource() throws Exception {
        // fixture 里 source = "opc_wrong_table" —— 模拟远端 VO 以后带上了 source 字段
        when(financeClient.flows(2L, null, null)).thenReturn(R.ok(flowData()));
        when(financeClient.vouchers(2L, null, null)).thenReturn(R.ok(voucherData()));

        Map<String, Object> flow = parse(tools.queryBankFlow("{}", Map.of("companyId", 2L)));
        Map<String, Object> voucher = parse(tools.queryVoucher("{}", Map.of("companyId", 2L)));

        assertEquals("opc_finance_bank_flow", flow.get("source"),
                "银行流水的来源表名由工具自己写死 —— 远端说的 opc_wrong_table 不算数");
        assertEquals("opc_finance_voucher", voucher.get("source"),
                "凭证的来源表名由工具自己写死 —— 远端说的 opc_wrong_table 不算数");
    }

    @Test
    @DisplayName("queryBankFlow — fromDate/toDate 原样传给 Feign；不传就不传")
    void queryBankFlow_passesFromToFromArgs() {
        when(financeClient.flows(2L, "2026-10-07", "2026-11-01")).thenReturn(R.ok(flowData()));
        when(financeClient.flows(2L, null, null)).thenReturn(R.ok(flowData()));

        tools.queryBankFlow("{\"fromDate\":\"2026-10-07\",\"toDate\":\"2026-11-01\"}",
                Map.of("companyId", 2L));
        verify(financeClient).flows(2L, "2026-10-07", "2026-11-01");

        // 不传区间 —— 工具层不补默认值，缺省由 finance 端算（最早一条 ~ 昨天）
        tools.queryBankFlow("{}", Map.of("companyId", 2L));
        verify(financeClient).flows(2L, null, null);
    }

    @Test
    @DisplayName("queryBankFlow — 远端返回 fail 时抛 OpcException，不返回空 JSON 冒充成功")
    void queryBankFlow_feignFailure_throws() {
        when(financeClient.flows(2L, null, null))
                .thenReturn(R.<Map<String, Object>>fail("opc-finance 不可用"));

        OpcException ex = assertThrows(OpcException.class,
                () -> tools.queryBankFlow("{}", Map.of("companyId", 2L)));
        assertTrue(ex.getMessage().contains("opc-finance 不可用"),
                "异常里应当带上远端的失败原因，实际: " + ex.getMessage());
    }

    // ==================== requireData 的三个失败分支 ====================
    // 「绝不返回空 JSON 冒充成功」是本 Task 的招牌承诺 ——
    // requireData() 有三个失败分支（r == null / code != SUCCESS / data == null），
    // 三条都得有测试守着，少一条这条承诺就只是嘴上说。

    @Test
    @DisplayName("requireData — 远端 code=200 但 data=null 时抛 OpcException（不冒充「查到了 0 条」）")
    void requireData_nullData_throws() {
        when(financeClient.flows(2L, null, null))
                .thenReturn(R.<Map<String, Object>>ok(null));

        OpcException ex = assertThrows(OpcException.class,
                () -> tools.queryBankFlow("{}", Map.of("companyId", 2L)));
        assertTrue(ex.getMessage().contains("数据为空"),
                "异常消息应当说明「数据为空」，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("requireData — 远端整个响应是 null 时抛 OpcException（不冒充成功）")
    void requireData_nullResponse_throws() {
        when(financeClient.flows(2L, null, null)).thenReturn(null);

        OpcException ex = assertThrows(OpcException.class,
                () -> tools.queryBankFlow("{}", Map.of("companyId", 2L)));
        assertTrue(ex.getMessage().contains("空响应"),
                "异常消息应当说明「空响应」，实际: " + ex.getMessage());
    }

    // ==================== requireCompanyId 的护栏 ====================

    @Test
    @DisplayName("requireCompanyId — companyId 是负数时抛 OpcException（不拿它去查库）")
    void requireCompanyId_negativeCompanyId_throws() {
        OpcException ex = assertThrows(OpcException.class,
                () -> tools.queryBankFlow("{}", Map.of("companyId", -1L)));
        assertTrue(ex.getMessage().contains("companyId"),
                "异常消息应当点名 companyId，实际: " + ex.getMessage());
        verifyNoInteractions(financeClient);
    }

    // ==================== query_voucher ====================

    @Test
    @DisplayName("queryVoucher — source = opc_finance_voucher，totalIn 是贷方（收入）口径")
    void queryVoucher_usesCreditAsIn() throws Exception {
        when(financeClient.vouchers(2L, null, null)).thenReturn(R.ok(voucherData()));

        String json = tools.queryVoucher("{}", Map.of("companyId", 2L));
        Map<String, Object> out = parse(json);

        assertEquals("opc_finance_voucher", out.get("source"));
        assertEquals(new BigDecimal("35400.00"), out.get("totalIn"),
                "凭证的 totalIn 是贷方合计，必须原样透传");
        assertEquals(6, ((Number) out.get("count")).intValue());
    }

    // ==================== query_balance ====================

    @Test
    @DisplayName("queryBalance — 调 billing 的 wallet，返回 {source, companyId, balance, currency}")
    void queryBalance_callsBillingWallet() throws Exception {
        when(billingClient.wallet(2L)).thenReturn(R.ok(walletData()));

        String json = tools.queryBalance("{}", Map.of("companyId", 2L));
        verify(billingClient).wallet(2L);

        Map<String, Object> out = parse(json);
        assertEquals("opc_wallet", out.get("source"));
        assertEquals(2L, ((Number) out.get("companyId")).longValue());
        assertEquals(new BigDecimal("2860.00"), out.get("balance"));
        assertEquals("CNY", out.get("currency"));
        assertFalse(out.containsKey("fromDate"), "余额没有区间概念，不该有 fromDate");
        assertFalse(out.containsKey("toDate"), "余额没有区间概念，不该有 toDate");
    }

}
