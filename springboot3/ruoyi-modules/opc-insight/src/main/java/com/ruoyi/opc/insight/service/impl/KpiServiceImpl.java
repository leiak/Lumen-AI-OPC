package com.ruoyi.opc.insight.service.impl;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.vo.FlowAggVo;
import com.ruoyi.opc.finance.vo.TokenUsageVo;
import com.ruoyi.opc.finance.vo.VoucherAggVo;
import com.ruoyi.opc.billing.vo.OrdersAggVo;
import com.ruoyi.opc.billing.vo.RechargeAggVo;
import com.ruoyi.opc.billing.vo.WalletAggVo;
import com.ruoyi.opc.insight.client.RemoteBillingService;
import com.ruoyi.opc.insight.client.RemoteFinanceService;
import com.ruoyi.opc.insight.client.RemoteUserCenterService;
import com.ruoyi.opc.insight.service.IKpiService;
import com.ruoyi.opc.insight.vo.KpiSnapshot;
import com.ruoyi.opc.user.domain.OpcCompanyProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * INSIGHT KPI 聚合服务实现（M4 Task 5）。
 *
 * <p>设计原则：
 * <ol>
 *   <li><b>部分降级</b>：每个 Feign 调用独立 try/catch，任何一个失败仅把自己那段字段
 *       留空 + partial=true，不中断其他源的拉取。</li>
 *   <li><b>调用解耦</b>：6 个数据源通过 3 个 Feign 客户端（opc-finance/opc-billing/opc-user-center）
 *       并行按需拉取；Feign 客户端的 fallbackFactory 已经返回 R.fail()，所以 try/catch 只会捕
 *       到反序列化错误或 NPE（兜底）。</li>
 *   <li><b>会计语义</b>：贷方(credit) = 收入(revenue)，借方(debit) = 支出(expense)，
 *       来自 opc-finance 凭证聚合的字段命名约定。</li>
 *   <li><b>活跃天数</b>：公司档案的 createTime 到今天的自然日差；createTime 为空时返回 0
 *       而非抛错（前端可显示「未知」）。</li>
 * </ol>
 *
 * <p>字段来源映射：
 * <pre>
 *   voucherAgg    → totalRevenue / totalExpense / voucherCount / pendingVoucherCount
 *   flowAgg       → (no direct field, only success check)
 *   taxReport     → (no direct field, only success check — used by DailyReportService)
 *   tokenUsage    → tokenUsage
 *   wallet        → walletBalance
 *   orders        → (no direct field, only success check)
 *   recharge      → (no direct field, only success check)
 *   companyProfile → companyActiveDays
 * </pre>
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiServiceImpl implements IKpiService {

    private final RemoteFinanceService financeClient;
    private final RemoteBillingService billingClient;
    private final RemoteUserCenterService userClient;

    /** KpiServiceImpl 静态日志对象，方便子方法引用。 */
    private static final String LOG_PREFIX = "[KpiService] ";

    @Override
    public KpiSnapshot snapshot(Long companyId, String period) {
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        if (period == null || period.isBlank()) {
            period = YearMonth.now(ZoneId.systemDefault()).toString(); // e.g. "2026-09"
        }

        KpiSnapshot.KpiSnapshotBuilder b = KpiSnapshot.builder()
                .companyId(companyId)
                .period(period)
                .partial(false);

        // ----- 1. 凭证聚合（opc-finance） -----
        try {
            R<VoucherAggVo> r = financeClient.voucherAgg(companyId, period);
            if (r != null && r.getCode() == R.SUCCESS && r.getData() != null) {
                VoucherAggVo v = r.getData();
                // 会计语义：贷方(credit) = 收入 / 借方(debit) = 支出
                b.totalRevenue(v.getCreditTotal())
                        .totalExpense(v.getDebitTotal())
                        .voucherCount(v.getVoucherCount())
                        .pendingVoucherCount(v.getPendingCount());
            } else {
                log.warn("{} voucherAgg 非 200 响应: code={} msg={}",
                        LOG_PREFIX, r == null ? "null" : r.getCode(), r == null ? "" : r.getMsg());
                b.partial(true);
            }
        } catch (Exception e) {
            log.warn("{} voucherAgg 失败: {}", LOG_PREFIX, e.getMessage());
            b.partial(true);
        }

        // ----- 2. 流水聚合（opc-finance，目前无直接字段，仅校验成功） -----
        try {
            R<FlowAggVo> r = financeClient.flowAgg(companyId, period);
            if (r == null || r.getCode() != R.SUCCESS) {
                log.warn("{} flowAgg 非 200: code={} msg={}",
                        LOG_PREFIX, r == null ? "null" : r.getCode(), r == null ? "" : r.getMsg());
                b.partial(true);
            }
        } catch (Exception e) {
            log.warn("{} flowAgg 失败: {}", LOG_PREFIX, e.getMessage());
            b.partial(true);
        }

        // ----- 3. 税务报表（opc-finance，仅校验存在 —— DailyReportService 会拉详情） -----
        try {
            R<OpcFinanceTaxReport> r = financeClient.taxReport(companyId, period);
            if (r == null || r.getCode() != R.SUCCESS) {
                log.warn("{} taxReport 非 200: code={}",
                        LOG_PREFIX, r == null ? "null" : r.getCode());
                b.partial(true);
            }
        } catch (Exception e) {
            log.warn("{} taxReport 失败: {}", LOG_PREFIX, e.getMessage());
            b.partial(true);
        }

        // ----- 4. Token 消耗 -----
        try {
            R<TokenUsageVo> r = financeClient.tokenUsage(companyId, period);
            if (r != null && r.getCode() == R.SUCCESS && r.getData() != null) {
                TokenUsageVo v = r.getData();
                b.tokenUsage(v.getTotalTokens());
            } else {
                log.warn("{} tokenUsage 非 200 或 data=null", LOG_PREFIX);
                b.partial(true);
            }
        } catch (Exception e) {
            log.warn("{} tokenUsage 失败: {}", LOG_PREFIX, e.getMessage());
            b.partial(true);
        }

        // ----- 5. 钱包余额（opc-billing） -----
        try {
            R<WalletAggVo> r = billingClient.wallet(companyId);
            if (r != null && r.getCode() == R.SUCCESS && r.getData() != null) {
                b.walletBalance(r.getData().getBalance());
            } else {
                log.warn("{} wallet 非 200 或 data=null", LOG_PREFIX);
                b.partial(true);
            }
        } catch (Exception e) {
            log.warn("{} wallet 失败: {}", LOG_PREFIX, e.getMessage());
            b.partial(true);
        }

        // ----- 6. 订单（opc-billing，无直接字段） -----
        try {
            R<OrdersAggVo> r = billingClient.orders(companyId, period);
            if (r == null || r.getCode() != R.SUCCESS) {
                log.warn("{} orders 非 200", LOG_PREFIX);
                b.partial(true);
            }
        } catch (Exception e) {
            log.warn("{} orders 失败: {}", LOG_PREFIX, e.getMessage());
            b.partial(true);
        }

        // ----- 7. 充值（opc-billing，无直接字段） -----
        try {
            R<RechargeAggVo> r = billingClient.recharge(companyId, period);
            if (r == null || r.getCode() != R.SUCCESS) {
                log.warn("{} recharge 非 200", LOG_PREFIX);
                b.partial(true);
            }
        } catch (Exception e) {
            log.warn("{} recharge 失败: {}", LOG_PREFIX, e.getMessage());
            b.partial(true);
        }

        // ----- 8. 公司档案（opc-user-center）→ 算 activeDays -----
        try {
            R<OpcCompanyProfile> r = userClient.getCompanyProfile(companyId);
            if (r != null && r.getCode() == R.SUCCESS && r.getData() != null) {
                Date createTime = r.getData().getCreateTime();
                long days = activeDaysFromCreateTime(createTime);
                b.companyActiveDays(days);
            } else {
                log.warn("{} companyProfile 非 200 或 data=null", LOG_PREFIX);
                b.partial(true);
            }
        } catch (Exception e) {
            log.warn("{} companyProfile 失败: {}", LOG_PREFIX, e.getMessage());
            b.partial(true);
        }

        return b.build();
    }

    /**
     * 公司活跃天数 = {@code createTime} 到今天（自然日）。
     * {@code createTime} 为 {@code null} 时返回 0 —— 前端显示「未知」，不抛错。
     */
    private static long activeDaysFromCreateTime(Date createTime) {
        if (createTime == null) {
            return 0L;
        }
        try {
            LocalDate created = createTime.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            return ChronoUnit.DAYS.between(created, LocalDate.now(ZoneId.systemDefault()));
        } catch (Exception e) {
            log.warn("{} activeDays 计算失败: {}", LOG_PREFIX, e.getMessage());
            return 0L;
        }
    }

}
