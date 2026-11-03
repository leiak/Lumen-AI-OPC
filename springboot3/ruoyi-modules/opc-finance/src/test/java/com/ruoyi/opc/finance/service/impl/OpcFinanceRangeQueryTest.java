package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.mapper.OpcFinanceBankFlowMapper;
import com.ruoyi.opc.finance.mapper.OpcFinanceVoucherMapper;
import com.ruoyi.opc.finance.vo.FlowRangeVo;
import com.ruoyi.opc.finance.vo.VoucherRangeVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 财务侧「区间查询」单测 —— 数字员工拿真数据的入口。
 *
 * <p>覆盖 7 个口径：
 * <ul>
 *   <li>{@code fromDate} 缺省 → 该公司最早一条记录的日期（不是「30 天前」）</li>
 *   <li>{@code toDate} 缺省 → 昨天（北京时间）</li>
 *   <li>流水 in/out 按 {@code direction} 求和、net = in - out</li>
 *   <li>昨天子集只统计落在昨天那一天的明细</li>
 *   <li>凭证没有 amount 列：贷方 total_credit = 收入，借方 total_debit = 支出</li>
 *   <li>空区间返回 0 / 空 list，<b>不是 null</b></li>
 *   <li>{@code companyId} 为空 → 抛 {@link OpcException} 且不碰 mapper</li>
 * </ul>
 *
 * <p>fixture 是演示库的实测值（company 2）：6 条流水 / 5 张凭证。
 * 用 Mockito spy 覆盖 {@code today()} —— 生产口径固定北京时间，
 * 容器 JVM 是 UTC，直接用 {@code LocalDate.now()} 会让「昨天」差 8 小时。
 *
 * <p><b>为什么把测试 JVM 的默认时区钉成 UTC</b>：容器 JVM 就是 UTC，
 * 而「用业务时区换算日期」和「用 JVM 默认时区换算」这两种实现，只在
 * <b>凌晨 0~8 点</b>的记录上才分得出来。开发机时区一变，错误的实现可能侥幸通过 ——
 * 这就是「测试钉不住口径」的典型形态。所以这里显式钉 UTC（并在 {@code @AfterEach} 还原），
 * fixture 也特意用<b>北京时间 06:00</b>（= UTC 前一天 22:00）来保证两者一定会分叉。
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
class OpcFinanceRangeQueryTest {

    private static final Long COMPANY_ID = 2L;

    /** 测试里固定的「今天」（北京时间） */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    /** 由 {@link #TODAY} 推出的「昨天」 */
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 11, 1);

    /** 业务时区 —— 库里存的是北京时间 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 「鉴别性」小时：北京时间 06:00 = UTC 前一天 22:00。
     *
     * <p>用 10:00 之类的中午时间是<b>没有鉴别力</b>的 —— 北京时间 10:00 在 UTC 下
     * 还是同一天的 02:00，两种实现算出同一个日期，测试怎么写都绿。</p>
     */
    private static final int DISCRIMINATING_HOUR = 6;

    @Mock
    private OpcFinanceBankFlowMapper flowMapper;

    @Mock
    private OpcFinanceVoucherMapper voucherMapper;

    private OpcFinanceBankFlowServiceImpl flowService;

    private OpcFinanceVoucherServiceImpl voucherService;

    /** 进入测试前的 JVM 默认时区，{@code @AfterEach} 里还原 */
    private TimeZone originalTimeZone;

    @BeforeEach
    void setUp() {
        // 把测试 JVM 的默认时区钉成 UTC —— 与容器一致，且必须还原（这是全局状态）
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // spy + doReturn 覆盖 today()：测试不该随日期漂移
        flowService = spy(new OpcFinanceBankFlowServiceImpl(flowMapper));
        voucherService = spy(new OpcFinanceVoucherServiceImpl(voucherMapper));
        // lenient：companyId 为空那条用例走不到 today()，此时这个桩确实没被用到
        lenient().doReturn(TODAY).when(flowService).today();
        lenient().doReturn(TODAY).when(voucherService).today();
    }

    @AfterEach
    void tearDown() {
        // 全局状态必须还原 —— 否则会污染同 JVM 里的其他测试类
        TimeZone.setDefault(originalTimeZone);
    }

    // ==================== fixture（演示库实测值，company 2） ====================

    private OpcFinanceBankFlow flow(long id, String date, int hour, String direction, String amount,
                                    String status, String memo) {
        OpcFinanceBankFlow f = new OpcFinanceBankFlow();
        f.setId(id);
        f.setCompanyId(COMPANY_ID);
        // 库里存的是北京时间 —— 显式按业务时区构造，测试 JVM 在任何时区下结果都一样
        f.setTradeTime(Date.from(LocalDate.parse(date).atTime(hour, 0)
                .atZone(BUSINESS_ZONE).toInstant()));
        f.setDirection(direction);
        f.setAmount(new BigDecimal(amount));
        f.setStatus(status);
        f.setMemo(memo);
        return f;
    }

    /** company 2 的 6 条流水（2026-10-07 ~ 2026-10-21） */
    private List<OpcFinanceBankFlow> sampleFlows() {
        return List.of(
                flow(27L, "2026-10-07", 9, "OUT", "13200.00", "PROCESSED", "采购货款"),
                flow(28L, "2026-10-10", 14, "IN", "38450.00", "PROCESSED", "小店结算款"),
                flow(29L, "2026-10-12", 11, "OUT", "12150.00", "PROCESSED", "采购货款"),
                flow(30L, "2026-10-15", 16, "IN", "9860.00", "EXTRACTED", "家居专场结算"),
                flow(31L, "2026-10-18", 10, "OUT", "7080.00", "PROCESSED", "音箱采购货款"),
                flow(32L, "2026-10-21", 8, "IN", "3582.00", "IMPORTED", "零散订单")
        );
    }

    private OpcFinanceVoucher voucher(long id, String date, String debit, String credit, String status) {
        OpcFinanceVoucher v = new OpcFinanceVoucher();
        v.setId(id);
        v.setCompanyId(COMPANY_ID);
        // DATE 列同样是北京时间，显式按业务时区构造
        v.setVoucherDate(Date.from(LocalDate.parse(date).atStartOfDay(BUSINESS_ZONE).toInstant()));
        v.setTotalDebit(new BigDecimal(debit));
        v.setTotalCredit(new BigDecimal(credit));
        v.setStatus(status);
        return v;
    }

    /** company 2 的 5 张凭证（2026-10-07 ~ 2026-10-20） */
    private List<OpcFinanceVoucher> sampleVouchers() {
        return List.of(
                voucher(25L, "2026-10-07", "13200.00", "0.00", "POSTED"),
                voucher(26L, "2026-10-10", "0.00", "35400.00", "POSTED"),
                voucher(27L, "2026-10-15", "12150.00", "0.00", "REVIEW"),
                voucher(28L, "2026-10-19", "2600.00", "0.00", "REVIEW"),
                voucher(29L, "2026-10-20", "1800.00", "0.00", "REJECTED")
        );
    }

    // ==================== fromDate / toDate 缺省语义 ====================

    @Test
    @DisplayName("queryRange — fromDate 缺省 → 该公司最早一条流水的日期（不是「30 天前」）")
    void queryRange_fromDateOmitted_usesEarliestRecordDate() {
        when(flowMapper.selectMinTradeDate(COMPANY_ID)).thenReturn("2026-10-07");
        when(flowMapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(), any())).thenReturn(sampleFlows());

        FlowRangeVo vo = flowService.queryRange(COMPANY_ID, null, null);

        assertEquals("2026-10-07", vo.getFromDate(),
                "fromDate 缺省时用 mapper 查到的最早流水日期");
        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(flowMapper).selectByCompanyAndDateRange(eq(COMPANY_ID), from.capture(), to.capture());
        assertEquals(LocalDate.of(2026, 10, 7), from.getValue());
        assertEquals(YESTERDAY, to.getValue());
    }

    @Test
    @DisplayName("queryRange — toDate 缺省 → 昨天（北京时间，today=2026-11-02 时是 2026-11-01）")
    void queryRange_toDateOmitted_defaultsToYesterday() {
        when(flowMapper.selectMinTradeDate(COMPANY_ID)).thenReturn("2026-10-07");
        when(flowMapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(), any())).thenReturn(sampleFlows());

        FlowRangeVo vo = flowService.queryRange(COMPANY_ID, null, null);

        assertEquals("2026-11-01", vo.getToDate(),
                "toDate 缺省时是「昨天」——今天的数据不进日报");
        // 显式传 toDate 时以调用方为准
        FlowRangeVo explicit = flowService.queryRange(COMPANY_ID, "2026-10-07", "2026-10-31");
        assertEquals("2026-10-31", explicit.getToDate());
    }

    // ==================== 汇总口径 ====================

    @Test
    @DisplayName("queryRange — 流水按 direction 求和：IN=51892.00 OUT=32430.00 net=19462.00")
    void queryRange_sumsInAndOutByDirection() {
        when(flowMapper.selectMinTradeDate(COMPANY_ID)).thenReturn("2026-10-07");
        when(flowMapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(), any())).thenReturn(sampleFlows());

        FlowRangeVo vo = flowService.queryRange(COMPANY_ID, null, null);

        assertEquals(6, vo.getCount());
        assertEquals(new BigDecimal("51892.00"), vo.getTotalIn());
        assertEquals(new BigDecimal("32430.00"), vo.getTotalOut());
        assertEquals(new BigDecimal("19462.00"), vo.getNet());
        assertNotNull(vo.getItems());
        assertEquals(6, vo.getItems().size());
    }

    @Test
    @DisplayName("queryRange — 昨天子集只统计落在昨天那一天的明细（fixture 里昨天没数据 → 0）")
    void queryRange_yesterdaySubsetCountsOnlyYesterday() {
        // 昨天那天的两条也用「鉴别性小时」06:00 —— 实现一旦用错时区，这两条会掉出昨天
        List<OpcFinanceBankFlow> withYesterday = List.of(
                flow(27L, "2026-10-07", 9, "OUT", "13200.00", "PROCESSED", "采购货款"),
                flow(40L, "2026-11-01", DISCRIMINATING_HOUR, "IN", "500.00", "IMPORTED", "昨天的零散订单"),
                flow(41L, "2026-11-01", DISCRIMINATING_HOUR, "OUT", "120.00", "IMPORTED", "昨天的采购")
        );
        // 一次 stub、两次连续返回：避免「同一个桩 stub 两遍」让前一个变成没被用上的桩
        when(flowMapper.selectMinTradeDate(COMPANY_ID)).thenReturn("2026-10-07");
        when(flowMapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(), any()))
                .thenReturn(sampleFlows())
                .thenReturn(withYesterday);

        FlowRangeVo vo = flowService.queryRange(COMPANY_ID, null, null);

        assertEquals(6, vo.getCount(), "区间内共 6 条");
        assertEquals(0, vo.getYesterdayCount(), "fixture 里昨天（2026-11-01）没有流水");
        assertEquals(0, vo.getYesterdayIn().compareTo(BigDecimal.ZERO),
                "yesterdayIn 数值等于 0.00");
        assertEquals(0, vo.getYesterdayOut().compareTo(BigDecimal.ZERO),
                "yesterdayOut 数值等于 0.00");

        // 反向验证：昨天子集不是「恒等于 0」——放一条昨天的流水进去，它必须被单独算出来
        FlowRangeVo vo2 = flowService.queryRange(COMPANY_ID, null, null);

        assertEquals(3, vo2.getCount());
        assertEquals(2, vo2.getYesterdayCount(), "昨天那天的 2 条必须被单独计数");
        assertEquals(0, vo2.getYesterdayIn().compareTo(new BigDecimal("500.00")));
        assertEquals(0, vo2.getYesterdayOut().compareTo(new BigDecimal("120.00")));
        assertEquals(0, vo2.getTotalIn().compareTo(new BigDecimal("500.00")),
                "总额口径不受昨天子集影响");
    }

    @Test
    @DisplayName("queryRange — 凭证：贷方 total_credit=收入 35400.00，借方 total_debit=支出 29750.00")
    void queryRange_credentialUsesCreditAsInAndDebitAsOut() {
        when(voucherMapper.selectMinVoucherDate(COMPANY_ID)).thenReturn("2026-10-07");
        when(voucherMapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(), any())).thenReturn(sampleVouchers());

        VoucherRangeVo vo = voucherService.queryRange(COMPANY_ID, null, null);

        assertEquals(5, vo.getCount());
        assertEquals(new BigDecimal("35400.00"), vo.getTotalIn(), "贷方合计 = 收入");
        assertEquals(new BigDecimal("29750.00"), vo.getTotalOut(), "借方合计 = 支出");
        assertEquals(new BigDecimal("5650.00"), vo.getNet());
        assertEquals("2026-10-07", vo.getFromDate());
        assertEquals("2026-11-01", vo.getToDate());
    }

    @Test
    @DisplayName("queryRange — tradeTime 按北京时间取日期（凌晨的记录不会被算到前一天）")
    void queryRange_tradeTimeUsesBeijingWallDate() {
        // 库里存的是北京时间。2026-11-01 06:00（北京时间）= UTC 前一天 22:00，
        // 容器 JVM 是 UTC —— 用 JVM 默认时区取日期会算成 10-31，这条记录就掉出「昨天」了。
        OpcFinanceBankFlow early = flow(42L, "2026-11-01", DISCRIMINATING_HOUR,
                "IN", "300.00", "IMPORTED", "清晨的订单");
        when(flowMapper.selectMinTradeDate(COMPANY_ID)).thenReturn("2026-10-07");
        when(flowMapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(), any()))
                .thenReturn(List.of(early));

        FlowRangeVo vo = flowService.queryRange(COMPANY_ID, null, null);

        assertEquals(1, vo.getYesterdayCount(),
                "2026-11-01 06:00（北京时间）必须算在 11-01，不能被算到 10-31");
    }

    @Test
    @DisplayName("fixture 本身有鉴别力 —— 业务时区算出的日期与 UTC 算出的日期差一天")
    void fixture_isDiscriminatingBetweenBeijingAndUtc() {
        OpcFinanceBankFlow early = flow(43L, "2026-11-01", DISCRIMINATING_HOUR,
                "IN", "300.00", "IMPORTED", "清晨的订单");

        // 业务时区（北京）→ 11-01
        assertEquals(LocalDate.of(2026, 11, 1), RangeSupport.wallDate(early.getTradeTime()),
                "库里存的那一天是 11-01（北京时间）");
        // JVM 默认时区（测试里钉成 UTC）→ 10-31
        assertEquals(LocalDate.of(2026, 10, 31),
                Instant.ofEpochMilli(early.getTradeTime().getTime())
                        .atZone(ZoneId.systemDefault()).toLocalDate(),
                "同一条记录在 UTC 下会落到前一天 —— 这就是这条 fixture 的鉴别力来源");
        // 顺手钉住「测试 JVM 默认时区确实是 UTC」——不然上面这条断言没有意义
        assertEquals("UTC", ZoneId.systemDefault().getId(), "测试 JVM 默认时区必须是 UTC");
    }

    // ==================== 边界 ====================

    @Test
    @DisplayName("queryRange — 无数据 → count=0、金额=0、items=空 list（都不是 null）")
    void queryRange_noRows_returnsZeroesNotNull() {
        when(flowMapper.selectMinTradeDate(COMPANY_ID)).thenReturn("2026-10-07");
        when(flowMapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(), any())).thenReturn(List.of());

        FlowRangeVo vo = flowService.queryRange(COMPANY_ID, null, null);

        assertEquals(0, vo.getCount());
        assertNotNull(vo.getTotalIn(), "totalIn 不能是 null");
        assertNotNull(vo.getTotalOut(), "totalOut 不能是 null");
        assertNotNull(vo.getNet(), "net 不能是 null");
        assertEquals(BigDecimal.ZERO, vo.getTotalIn());
        assertEquals(BigDecimal.ZERO, vo.getTotalOut());
        assertEquals(BigDecimal.ZERO, vo.getNet());
        assertNotNull(vo.getItems(), "items 不能是 null");
        assertTrue(vo.getItems().isEmpty());
    }

    @Test
    @DisplayName("queryRange — companyId 为空 → 抛 OpcException，且一次都不碰 mapper")
    void queryRange_companyIdNull_throwsOpcException() {
        OpcException ex = assertThrows(OpcException.class, () -> flowService.queryRange(null, null, null));
        assertEquals("companyId 不能为空", ex.getMessage());
        verifyNoInteractions(flowMapper);

        // 凭证侧同一道防线
        OpcException voucherEx =
                assertThrows(OpcException.class, () -> voucherService.queryRange(null, null, null));
        assertEquals("companyId 不能为空", voucherEx.getMessage());
        verifyNoInteractions(voucherMapper);
    }

    // ==================== 非法入参 ====================

    @Test
    @DisplayName("queryRange — fromDate 格式非法 / fromDate 晚于 toDate → 抛 OpcException")
    void queryRange_invalidArguments_throwsOpcException() {
        assertThrows(OpcException.class, () -> flowService.queryRange(COMPANY_ID, "2026/10/07", null));
        assertThrows(OpcException.class, () -> flowService.queryRange(COMPANY_ID, "2026-10-07", "2026-10-01"));
        verify(flowMapper, never()).selectByCompanyAndDateRange(any(), any(), any());
    }

}
