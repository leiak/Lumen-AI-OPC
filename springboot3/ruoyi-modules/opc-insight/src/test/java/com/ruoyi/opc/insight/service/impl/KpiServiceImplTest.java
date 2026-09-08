package com.ruoyi.opc.insight.service.impl;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.billing.vo.OrdersAggVo;
import com.ruoyi.opc.billing.vo.RechargeAggVo;
import com.ruoyi.opc.billing.vo.WalletAggVo;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.vo.FlowAggVo;
import com.ruoyi.opc.finance.vo.TokenUsageVo;
import com.ruoyi.opc.finance.vo.VoucherAggVo;
import com.ruoyi.opc.insight.client.RemoteBillingService;
import com.ruoyi.opc.insight.client.RemoteFinanceService;
import com.ruoyi.opc.insight.client.RemoteUserCenterService;
import com.ruoyi.opc.insight.vo.KpiSnapshot;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KpiServiceImpl 单元测试（M4 Task 5）。
 *
 * <p>覆盖分支：
 * <ol>
 *   <li>happy path：6 源全成功 → partial=false，所有字段填充</li>
 *   <li>单源降级（finance / billing / user 各 1 个） → partial=true，对应字段默认 0</li>
 *   <li>全降级 → 全 0，partial=true</li>
 *   <li>参数校验：null companyId / null period / 格式错误</li>
 *   <li>R 形状异常：null data / non-200 code → partial=true</li>
 *   <li>并发独立性：两次调用互不污染</li>
 * </ol>
 *
 * @author OAC
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KpiServiceImplTest {

    @Mock
    private RemoteFinanceService financeClient;

    @Mock
    private RemoteBillingService billingClient;

    @Mock
    private RemoteUserCenterService userClient;

    @InjectMocks
    private KpiServiceImpl kpiService;

    private static final Long COMPANY_ID = 1L;
    private static final String PERIOD = "2026-09";

    /**
     * OpcCompanyProfile 没有 @Builder，手写一个 builder-style helper。
     */
    private static OpcCompanyProfile buildCompanyProfile(Long id, Date createTime) {
        OpcCompanyProfile p = new OpcCompanyProfile();
        p.setId(id);
        if (createTime != null) {
            p.setCreateTime(createTime);
        }
        return p;
    }

    @BeforeEach
    void setUp() {
        // 默认：6 个调用全部成功（happy path），单测按需 override
        when(financeClient.voucherAgg(eq(COMPANY_ID), eq(PERIOD)))
                .thenReturn(R.ok(VoucherAggVo.builder()
                        .companyId(COMPANY_ID).period(PERIOD)
                        .creditTotal(new BigDecimal("10000.00")) // 收入
                        .debitTotal(new BigDecimal("3000.00"))  // 支出
                        .voucherCount(50L)
                        .pendingCount(5L)
                        .build()));
        when(financeClient.flowAgg(eq(COMPANY_ID), eq(PERIOD)))
                .thenReturn(R.ok(FlowAggVo.builder()
                        .companyId(COMPANY_ID).period(PERIOD)
                        .inTotal(new BigDecimal("8000.00"))
                        .outTotal(new BigDecimal("2500.00"))
                        .flowCount(20L)
                        .build()));
        when(financeClient.taxReport(eq(COMPANY_ID), eq(PERIOD)))
                .thenReturn(R.ok(new OpcFinanceTaxReport()));
        when(financeClient.tokenUsage(eq(COMPANY_ID), eq(PERIOD)))
                .thenReturn(R.ok(TokenUsageVo.builder()
                        .companyId(COMPANY_ID).period(PERIOD)
                        .totalTokens(12345L)
                        .build()));
        when(billingClient.wallet(eq(COMPANY_ID)))
                .thenReturn(R.ok(WalletAggVo.builder()
                        .companyId(COMPANY_ID)
                        .balance(new BigDecimal("500.00"))
                        .build()));
        when(billingClient.orders(eq(COMPANY_ID), eq(PERIOD)))
                .thenReturn(R.ok(OrdersAggVo.builder()
                        .companyId(COMPANY_ID).period(PERIOD)
                        .count(10L).build()));
        when(billingClient.recharge(eq(COMPANY_ID), eq(PERIOD)))
                .thenReturn(R.ok(RechargeAggVo.builder()
                        .companyId(COMPANY_ID).period(PERIOD)
                        .count(2L).build()));
        Date created = Date.from(LocalDate.now(ZoneId.systemDefault()).minusDays(30)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
        OpcCompanyProfile profile30d = new OpcCompanyProfile();
        profile30d.setId(COMPANY_ID);
        profile30d.setCreateTime(created);
        when(userClient.getCompanyProfile(eq(COMPANY_ID)))
                .thenReturn(R.ok(profile30d));
    }

    // --------------------------------------------------------------
    // 1. Happy path
    // --------------------------------------------------------------
    @Test
    void snapshot_allSourcesSucceed_returnsFullSnapshot_partialFalse() {
        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, PERIOD);

        assertNotNull(s);
        assertEquals(COMPANY_ID, s.getCompanyId());
        assertEquals(PERIOD, s.getPeriod());
        // voucherAgg → 4 fields
        assertEquals(new BigDecimal("10000.00"), s.getTotalRevenue());
        assertEquals(new BigDecimal("3000.00"), s.getTotalExpense());
        assertEquals(50L, s.getVoucherCount());
        assertEquals(5L, s.getPendingVoucherCount());
        // tokenUsage
        assertEquals(12345L, s.getTokenUsage());
        // wallet
        assertEquals(new BigDecimal("500.00"), s.getWalletBalance());
        // companyProfile
        assertEquals(30L, s.getCompanyActiveDays());
        assertFalse(s.isPartial());
    }

    // --------------------------------------------------------------
    // 2. 单源降级 - finance
    // --------------------------------------------------------------
    @Test
    void snapshot_financeFeignFails_returnsEmptyVoucherAndPartialTrue() {
        when(financeClient.voucherAgg(anyLong(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, PERIOD);

        assertNotNull(s);
        assertTrue(s.isPartial());
        // voucher 字段保留默认 0
        assertNull(s.getTotalRevenue());
        assertNull(s.getTotalExpense());
        assertNull(s.getVoucherCount());
        assertNull(s.getPendingVoucherCount());
        // 其他源正常
        assertEquals(12345L, s.getTokenUsage());
        assertEquals(new BigDecimal("500.00"), s.getWalletBalance());
    }

    // --------------------------------------------------------------
    // 3. 单源降级 - billing
    // --------------------------------------------------------------
    @Test
    void snapshot_billingFeignFails_returnsEmptyWalletAndPartialTrue() {
        when(billingClient.wallet(anyLong()))
                .thenThrow(new RuntimeException("billing down"));

        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, PERIOD);

        assertTrue(s.isPartial());
        assertNull(s.getWalletBalance());
        // voucher 正常
        assertEquals(50L, s.getVoucherCount());
    }

    // --------------------------------------------------------------
    // 4. 单源降级 - user
    // --------------------------------------------------------------
    @Test
    void snapshot_userFeignFails_returnsEmptyProfileAndPartialTrue() {
        when(userClient.getCompanyProfile(anyLong()))
                .thenThrow(new RuntimeException("user-center down"));

        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, PERIOD);

        assertTrue(s.isPartial());
        assertNull(s.getCompanyActiveDays());
        assertEquals(50L, s.getVoucherCount());
    }

    // --------------------------------------------------------------
    // 5. 全源降级
    // --------------------------------------------------------------
    @Test
    void snapshot_allFeignFail_returnsEmptySnapshotAndPartialTrue() {
        when(financeClient.voucherAgg(anyLong(), anyString()))
                .thenThrow(new RuntimeException("finance down"));
        when(financeClient.flowAgg(anyLong(), anyString()))
                .thenThrow(new RuntimeException("finance down"));
        when(financeClient.taxReport(anyLong(), anyString()))
                .thenThrow(new RuntimeException("finance down"));
        when(financeClient.tokenUsage(anyLong(), anyString()))
                .thenThrow(new RuntimeException("finance down"));
        when(billingClient.wallet(anyLong()))
                .thenThrow(new RuntimeException("billing down"));
        when(billingClient.orders(anyLong(), anyString()))
                .thenThrow(new RuntimeException("billing down"));
        when(billingClient.recharge(anyLong(), anyString()))
                .thenThrow(new RuntimeException("billing down"));
        when(userClient.getCompanyProfile(anyLong()))
                .thenThrow(new RuntimeException("user down"));

        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, PERIOD);

        assertTrue(s.isPartial());
        assertEquals(COMPANY_ID, s.getCompanyId());
        assertEquals(PERIOD, s.getPeriod());
        assertNull(s.getTotalRevenue());
        assertNull(s.getTotalExpense());
        assertNull(s.getVoucherCount());
        assertNull(s.getPendingVoucherCount());
        assertNull(s.getWalletBalance());
        assertNull(s.getTokenUsage());
        assertNull(s.getCompanyActiveDays());
    }

    // --------------------------------------------------------------
    // 6. period 默认当前月
    // --------------------------------------------------------------
    @Test
    void snapshot_nullPeriod_defaultsToCurrentMonth() {
        String expectedPeriod = YearMonth.now(ZoneId.systemDefault()).toString();
        // 让所有 stub 接受任何 period 匹配
        when(financeClient.voucherAgg(eq(COMPANY_ID), anyString()))
                .thenReturn(R.ok(VoucherAggVo.builder().voucherCount(1L).build()));
        when(financeClient.flowAgg(eq(COMPANY_ID), anyString()))
                .thenReturn(R.ok(FlowAggVo.builder().build()));
        when(financeClient.taxReport(eq(COMPANY_ID), anyString()))
                .thenReturn(R.ok(new OpcFinanceTaxReport()));
        when(financeClient.tokenUsage(eq(COMPANY_ID), anyString()))
                .thenReturn(R.ok(TokenUsageVo.builder().build()));
        when(billingClient.wallet(eq(COMPANY_ID)))
                .thenReturn(R.ok(WalletAggVo.builder().build()));
        when(billingClient.orders(eq(COMPANY_ID), anyString()))
                .thenReturn(R.ok(OrdersAggVo.builder().build()));
        when(billingClient.recharge(eq(COMPANY_ID), anyString()))
                .thenReturn(R.ok(RechargeAggVo.builder().build()));
        when(userClient.getCompanyProfile(eq(COMPANY_ID)))
                .thenReturn(R.ok(buildCompanyProfile(COMPANY_ID, null)));

        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, null);

        assertEquals(expectedPeriod, s.getPeriod());
        // 6 个按 period 的 Feign 调用都用 expectedPeriod
        verify(financeClient).voucherAgg(COMPANY_ID, expectedPeriod);
        verify(financeClient).flowAgg(COMPANY_ID, expectedPeriod);
        verify(financeClient).taxReport(COMPANY_ID, expectedPeriod);
        verify(financeClient).tokenUsage(COMPANY_ID, expectedPeriod);
        verify(billingClient).orders(COMPANY_ID, expectedPeriod);
        verify(billingClient).recharge(COMPANY_ID, expectedPeriod);
    }

    // --------------------------------------------------------------
    // 7. 校验：null companyId
    // --------------------------------------------------------------
    @Test
    void snapshot_nullCompanyId_throwsOpcException() {
        OpcException ex = assertThrows(OpcException.class,
                () -> kpiService.snapshot(null, PERIOD));
        assertTrue(ex.getMessage().contains("companyId"));
        // 不应触发任何 Feign 调用
        verify(financeClient, never()).voucherAgg(any(), any());
        verify(billingClient, never()).wallet(any());
        verify(userClient, never()).getCompanyProfile(any());
    }

    // --------------------------------------------------------------
    // 8. 校验：blank period 自动默认（不抛错）
    // --------------------------------------------------------------
    @Test
    void snapshot_blankPeriod_defaultsToCurrentMonth() {
        when(financeClient.voucherAgg(anyLong(), anyString()))
                .thenReturn(R.ok(VoucherAggVo.builder().voucherCount(1L).build()));
        when(financeClient.flowAgg(anyLong(), anyString()))
                .thenReturn(R.ok(FlowAggVo.builder().build()));
        when(financeClient.taxReport(anyLong(), anyString()))
                .thenReturn(R.ok(new OpcFinanceTaxReport()));
        when(financeClient.tokenUsage(anyLong(), anyString()))
                .thenReturn(R.ok(TokenUsageVo.builder().build()));
        when(billingClient.wallet(anyLong()))
                .thenReturn(R.ok(WalletAggVo.builder().build()));
        when(billingClient.orders(anyLong(), anyString()))
                .thenReturn(R.ok(OrdersAggVo.builder().build()));
        when(billingClient.recharge(anyLong(), anyString()))
                .thenReturn(R.ok(RechargeAggVo.builder().build()));
        when(userClient.getCompanyProfile(anyLong()))
                .thenReturn(R.ok(buildCompanyProfile(COMPANY_ID, null)));

        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, "   ");

        // 空白期 → 默认本月
        assertEquals(YearMonth.now(ZoneId.systemDefault()).toString(), s.getPeriod());
        assertFalse(s.isPartial());
    }

    // --------------------------------------------------------------
    // 9. R.getData() == null
    // --------------------------------------------------------------
    @Test
    void snapshot_nullRData_returnsZeroValuesAndPartialTrue() {
        when(financeClient.voucherAgg(anyLong(), anyString()))
                .thenReturn(R.ok(null));  // R.ok 显式 null data
        when(financeClient.flowAgg(anyLong(), anyString()))
                .thenReturn(R.ok(null));
        when(financeClient.taxReport(anyLong(), anyString()))
                .thenReturn(R.ok(null));
        when(financeClient.tokenUsage(anyLong(), anyString()))
                .thenReturn(R.ok(null));
        when(billingClient.wallet(anyLong()))
                .thenReturn(R.ok(null));
        when(billingClient.orders(anyLong(), anyString()))
                .thenReturn(R.ok(null));
        when(billingClient.recharge(anyLong(), anyString()))
                .thenReturn(R.ok(null));
        when(userClient.getCompanyProfile(anyLong()))
                .thenReturn(R.ok(null));

        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, PERIOD);

        assertTrue(s.isPartial());
        assertNull(s.getTotalRevenue());
        assertNull(s.getWalletBalance());
        assertNull(s.getCompanyActiveDays());
    }

    // --------------------------------------------------------------
    // 10. R.getCode() != 200
    // --------------------------------------------------------------
    @Test
    void snapshot_non200RCode_returnsZeroValuesAndPartialTrue() {
        when(financeClient.voucherAgg(anyLong(), anyString()))
                .thenReturn(R.fail("voucher 服务 503"));
        when(billingClient.wallet(anyLong()))
                .thenReturn(R.fail(503, "wallet 服务 503"));
        when(userClient.getCompanyProfile(anyLong()))
                .thenReturn(R.fail(500, "user 服务 500"));

        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, PERIOD);

        assertTrue(s.isPartial());
        assertNull(s.getTotalRevenue());
        assertNull(s.getWalletBalance());
        assertNull(s.getCompanyActiveDays());
    }

    // --------------------------------------------------------------
    // 11. 接受 YYYY-MM 格式
    // --------------------------------------------------------------
    @Test
    void snapshot_acceptsYyyyDashMM() {
        KpiSnapshot s = kpiService.snapshot(COMPANY_ID, "2026-09");
        assertEquals("2026-09", s.getPeriod());
        assertFalse(s.isPartial());
        assertEquals(50L, s.getVoucherCount());
    }

    // --------------------------------------------------------------
    // 12. 连续两次调用互不污染
    // --------------------------------------------------------------
    @Test
    void snapshot_twoSequentialCallsAreIndependent() {
        KpiSnapshot s1 = kpiService.snapshot(COMPANY_ID, PERIOD);
        KpiSnapshot s2 = kpiService.snapshot(COMPANY_ID, PERIOD);

        // 两个对象不同（不可变，但 builder 生成新实例）
        assertNotNull(s1);
        assertNotNull(s2);
        // 字段值一致 —— 证明 s1 的状态没有污染 s2
        assertEquals(s1.getTotalRevenue(), s2.getTotalRevenue());
        assertEquals(s1.getVoucherCount(), s2.getVoucherCount());
        assertEquals(s1.getWalletBalance(), s2.getWalletBalance());
        assertEquals(s1.getCompanyActiveDays(), s2.getCompanyActiveDays());
        // 两次调用 → 6 个 Feign 调用 × 2 = 12
        verify(financeClient, times(2)).voucherAgg(COMPANY_ID, PERIOD);
        verify(financeClient, times(2)).tokenUsage(COMPANY_ID, PERIOD);
        verify(billingClient, times(2)).wallet(COMPANY_ID);
        verify(userClient, times(2)).getCompanyProfile(COMPANY_ID);
    }
}
