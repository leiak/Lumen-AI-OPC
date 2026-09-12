package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpDailySnapshot;
import com.ruoyi.opc.erp.dto.OpcErpReportDto;
import com.ruoyi.opc.erp.mapper.OpcErpDailySnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpcErpReportServiceImpl 单测 (10 cases, Task 7)
 *
 * <p>覆盖:
 * <ul>
 *   <li>getDailyReport (3): 有数据 / 无数据 / null 入参</li>
 *   <li>getMonthlyReport (5): 按日聚合 / 无数据 / month 非法 / year 非法 / null companyId</li>
 *   <li>边界 (2): month=12 合法 / month=0 非法</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OpcErpReportService 单测 (10 cases)")
class OpcErpReportServiceImplTest {

    private static final Long COMPANY_ID = 1L;

    @Mock
    private OpcErpDailySnapshotMapper snapshotMapper;

    @InjectMocks
    private OpcErpReportServiceImpl service;

    @BeforeEach
    void setUp() {
        // 默认所有 mapper 调用返回空
        when(snapshotMapper.selectByCompanyAndDate(anyLong(), any())).thenReturn(Collections.emptyList());
        when(snapshotMapper.selectByCompanyAndDateRange(anyLong(), any(), any()))
                .thenReturn(Collections.emptyList());
    }

    // ============================================================
    // getDailyReport (3)
    // ============================================================

    /** Test 1 */
    @Test
    @DisplayName("getDailyReport - 找到 2 个 SKU 快照, skuCount=2, totalClosingStock 累加")
    void getDailyReport_foundReturnsSnapshots() {
        LocalDate date = LocalDate.of(2026, 9, 11);
        OpcErpDailySnapshot s1 = OpcErpDailySnapshot.builder()
                .id(1L).companyId(COMPANY_ID).snapshotDate(date).skuId(500L)
                .openingStock(10).inQty(5).outQty(3).closingStock(12).build();
        OpcErpDailySnapshot s2 = OpcErpDailySnapshot.builder()
                .id(2L).companyId(COMPANY_ID).snapshotDate(date).skuId(501L)
                .openingStock(20).inQty(0).outQty(7).closingStock(13).build();
        when(snapshotMapper.selectByCompanyAndDate(COMPANY_ID, date))
                .thenReturn(Arrays.asList(s1, s2));

        OpcErpReportDto.DailyReport report = service.getDailyReport(COMPANY_ID, date);

        assertThat(report).isNotNull();
        assertThat(report.getDate()).isEqualTo(date);
        assertThat(report.getSkuCount()).isEqualTo(2);
        assertThat(report.getTotalClosingStock()).isEqualTo(25);  // 12 + 13
        assertThat(report.getDetails()).hasSize(2);
        assertThat(report.getDetails().get(0).getSkuId()).isEqualTo(500L);
        assertThat(report.getDetails().get(0).getOpening()).isEqualTo(10);
        assertThat(report.getDetails().get(0).getInQty()).isEqualTo(5);
        assertThat(report.getDetails().get(0).getClosing()).isEqualTo(12);
    }

    /** Test 2 */
    @Test
    @DisplayName("getDailyReport - 无快照返回 skuCount=0, details=[], totalClosingStock=0")
    void getDailyReport_noData_returnsEmpty() {
        LocalDate date = LocalDate.of(2026, 9, 11);
        when(snapshotMapper.selectByCompanyAndDate(COMPANY_ID, date))
                .thenReturn(Collections.emptyList());

        OpcErpReportDto.DailyReport report = service.getDailyReport(COMPANY_ID, date);

        assertThat(report.getDate()).isEqualTo(date);
        assertThat(report.getSkuCount()).isZero();
        assertThat(report.getTotalClosingStock()).isZero();
        assertThat(report.getDetails()).isEmpty();
    }

    /** Test 3 */
    @Test
    @DisplayName("getDailyReport - null 入参抛异常")
    void getDailyReport_nullParams_throws() {
        assertThatThrownBy(() -> service.getDailyReport(null, LocalDate.now()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
        assertThatThrownBy(() -> service.getDailyReport(COMPANY_ID, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("date");
        verify(snapshotMapper, never()).selectByCompanyAndDate(anyLong(), any());
    }

    // ============================================================
    // getMonthlyReport (5)
    // ============================================================

    /** Test 4 */
    @Test
    @DisplayName("getMonthlyReport - 按日聚合, 每日 in/out/closing 求和")
    void getMonthlyReport_aggregatesDays() {
        LocalDate d1 = LocalDate.of(2026, 9, 1);
        LocalDate d2 = LocalDate.of(2026, 9, 2);
        // 第 1 天: 2 个 SKU 各入库 5 出库 3
        OpcErpDailySnapshot day1SkuA = OpcErpDailySnapshot.builder()
                .snapshotDate(d1).skuId(500L).inQty(5).outQty(3).closingStock(12).build();
        OpcErpDailySnapshot day1SkuB = OpcErpDailySnapshot.builder()
                .snapshotDate(d1).skuId(501L).inQty(10).outQty(2).closingStock(18).build();
        // 第 2 天: 1 个 SKU 入库 0 出库 5
        OpcErpDailySnapshot day2SkuA = OpcErpDailySnapshot.builder()
                .snapshotDate(d2).skuId(500L).inQty(0).outQty(5).closingStock(7).build();
        when(snapshotMapper.selectByCompanyAndDateRange(eq(COMPANY_ID), any(), any()))
                .thenReturn(Arrays.asList(day1SkuA, day1SkuB, day2SkuA));

        OpcErpReportDto.MonthlyReport report = service.getMonthlyReport(COMPANY_ID, 2026, 9);

        assertThat(report.getYear()).isEqualTo(2026);
        assertThat(report.getMonth()).isEqualTo(9);
        assertThat(report.getDaily()).hasSize(2);
        // 第 1 天: in=15, out=5, closing=30
        OpcErpReportDto.DailySnapshot day1 = report.getDaily().get(0);
        assertThat(day1.getDate()).isEqualTo(d1);
        assertThat(day1.getInQty()).isEqualTo(15);
        assertThat(day1.getOutQty()).isEqualTo(5);
        assertThat(day1.getClosing()).isEqualTo(30);
        // 第 2 天: in=0, out=5, closing=7
        OpcErpReportDto.DailySnapshot day2 = report.getDaily().get(1);
        assertThat(day2.getDate()).isEqualTo(d2);
        assertThat(day2.getInQty()).isZero();
        assertThat(day2.getOutQty()).isEqualTo(5);
        assertThat(day2.getClosing()).isEqualTo(7);
    }

    /** Test 5 */
    @Test
    @DisplayName("getMonthlyReport - 月份 < 1 抛异常")
    void getMonthlyReport_invalidMonthLow_throws() {
        assertThatThrownBy(() -> service.getMonthlyReport(COMPANY_ID, 2026, 0))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("月份必须 1-12");
        verify(snapshotMapper, never()).selectByCompanyAndDateRange(anyLong(), any(), any());
    }

    /** Test 6 */
    @Test
    @DisplayName("getMonthlyReport - 月份 > 12 抛异常")
    void getMonthlyReport_invalidMonthHigh_throws() {
        assertThatThrownBy(() -> service.getMonthlyReport(COMPANY_ID, 2026, 13))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("月份必须 1-12");
    }

    /** Test 7 */
    @Test
    @DisplayName("getMonthlyReport - 无数据返回 daily=[]")
    void getMonthlyReport_noData() {
        OpcErpReportDto.MonthlyReport report = service.getMonthlyReport(COMPANY_ID, 2026, 9);

        assertThat(report.getYear()).isEqualTo(2026);
        assertThat(report.getMonth()).isEqualTo(9);
        assertThat(report.getDaily()).isEmpty();
    }

    /** Test 8 */
    @Test
    @DisplayName("getMonthlyReport - 边界: month=12 合法")
    void getMonthlyReport_december_isValid() {
        OpcErpReportDto.MonthlyReport report = service.getMonthlyReport(COMPANY_ID, 2026, 12);

        assertThat(report.getMonth()).isEqualTo(12);
        // 验证 mapper 收到 year-month 的 [12-01, 12-31] 区间
        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(snapshotMapper).selectByCompanyAndDateRange(eq(COMPANY_ID),
                startCaptor.capture(), endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    /** Test 9 */
    @Test
    @DisplayName("getMonthlyReport - 边界: month=1 合法")
    void getMonthlyReport_january_isValid() {
        OpcErpReportDto.MonthlyReport report = service.getMonthlyReport(COMPANY_ID, 2026, 1);

        assertThat(report.getMonth()).isEqualTo(1);
        assertThat(report.getYear()).isEqualTo(2026);
    }

    /** Test 10 */
    @Test
    @DisplayName("getMonthlyReport - null companyId 抛异常")
    void getMonthlyReport_nullCompanyId_throws() {
        assertThatThrownBy(() -> service.getMonthlyReport(null, 2026, 9))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("companyId");
        verify(snapshotMapper, never()).selectByCompanyAndDateRange(anyLong(), any(), any());
    }
}
