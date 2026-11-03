package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.common.utils.OpcCodeGenerator;
import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.mapper.OpcFinanceVoucherMapper;
import com.ruoyi.opc.finance.service.IOpcFinanceVoucherService;
import com.ruoyi.opc.finance.vo.VoucherAggVo;
import com.ruoyi.opc.finance.vo.VoucherRangeVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpcFinanceVoucherServiceImpl implements IOpcFinanceVoucherService {

    /**
     * 业务时区 —— 「今天/昨天」一律按北京时间算。
     *
     * <p>容器 JVM 是 UTC：直接 {@code LocalDate.now()} 会算成 UTC 的「昨天」，
     * 和日报口径差 8 小时。</p>
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final OpcFinanceVoucherMapper mapper;

    @Override
    public OpcFinanceVoucher getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<OpcFinanceVoucher> listByCompany(Long companyId, String period, String status, Integer limit) {
        return mapper.selectByCompany(companyId, period, status, limit == null ? 20 : limit);
    }

    @Override
    public Long create(OpcFinanceVoucher voucher) {
        if (voucher.getVoucherCode() == null) {
            voucher.setVoucherCode(OpcCodeGenerator.voucherCode());
        }
        // W48.5: voucher_date / period NOT NULL, 前端不传,这里默认今天 / 当月
        if (voucher.getVoucherDate() == null) {
            voucher.setVoucherDate(new java.util.Date());
        }
        if (voucher.getPeriod() == null || voucher.getPeriod().isEmpty()) {
            // voucherDate 是 Date,转 LocalDate 取 YYYY-MM
            java.time.LocalDate ld = voucher.getVoucherDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            voucher.setPeriod(ld.toString().substring(0, 7));
        }
        if (voucher.getTotalDebit() == null) voucher.setTotalDebit(java.math.BigDecimal.ZERO);
        if (voucher.getTotalCredit() == null) voucher.setTotalCredit(java.math.BigDecimal.ZERO);
        voucher.setStatus("DRAFT");
        // W10.3 审计字段：初始 updateBy = createBy（同一人创建即最后更新）
        // updateTime 由 mapper XML 自动 NOW()
        if (voucher.getUpdateBy() == null) {
            voucher.setUpdateBy(voucher.getCreateBy());
        }
        mapper.insert(voucher);
        return voucher.getId();
    }

    @Override
    public int update(OpcFinanceVoucher voucher) {
        // W10.3 审计字段：updateBy 必传（controller 在 updateVoucher 注入 SecurityUtils.getUsername()）
        // updateTime 由 mapper XML 自动 NOW()
        return mapper.update(voucher);
    }

    @Override
    public int reviewPass(Long id, String reviewer) {
        OpcFinanceVoucher v = new OpcFinanceVoucher();
        v.setId(id);
        v.setStatus("REVIEW");
        v.setReviewedBy(reviewer);
        v.setReviewedTime(new Date());
        v.setUpdateBy(reviewer);
        return mapper.update(v);
    }

    @Override
    public int reviewReject(Long id, String reviewer, String opinion) {
        OpcFinanceVoucher v = new OpcFinanceVoucher();
        v.setId(id);
        v.setStatus("REJECTED");
        v.setReviewedBy(reviewer);
        v.setReviewedTime(new Date());
        v.setRemark(opinion);
        v.setUpdateBy(reviewer);
        return mapper.update(v);
    }

    @Override
    public int post(Long id, String operator) {
        OpcFinanceVoucher exist = mapper.selectById(id);
        if (exist == null) throw new OpcException("凭证不存在");
        if (!"REVIEW".equals(exist.getStatus())) throw new OpcException("凭证未通过审核，不能入账");

        OpcFinanceVoucher v = new OpcFinanceVoucher();
        v.setId(id);
        v.setStatus("POSTED");
        v.setPostedBy(operator);
        v.setPostedTime(new Date());
        v.setUpdateBy(operator);
        return mapper.update(v);
    }

    @Override
    public VoucherAggVo aggregateByPeriod(Long companyId, String period) {
        AggSupport.validate(companyId, period);
        // 复用 W1.4.2 为月度税报写的 aggregateByPeriod（同一条 SQL，避免重复聚合口径）
        Map<String, Object> agg = AggSupport.orEmpty(mapper.aggregateByPeriod(companyId, period));

        return VoucherAggVo.builder()
                .companyId(companyId)
                .period(period)
                // taxable_amount = SUM(total_credit) 贷方；input_tax = SUM(total_debit) 借方
                .creditTotal(AggSupport.amount(agg.get("taxable_amount")))
                .debitTotal(AggSupport.amount(agg.get("input_tax")))
                .voucherCount(AggSupport.count(agg.get("voucher_count")))
                .pendingCount(AggSupport.count(agg.get("pending_count")))
                .postedCount(AggSupport.count(agg.get("posted_count")))
                .rejectedCount(AggSupport.count(agg.get("rejected_count")))
                .build();
    }

    // ==================== 区间查询（数字员工工具端点） ====================

    /**
     * {@inheritDoc}
     *
     * <p>口径与流水一致：贷方 total_credit = 收入，借方 total_debit = 支出 ——
     * 凭证没有 amount 列，下游对账不必分两套算法。</p>
     */
    @Override
    public VoucherRangeVo queryRange(Long companyId, String fromDate, String toDate) {
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        // today() 只取一次：跨零点调用两次会得到不同的「昨天」
        LocalDate todayDate = today();
        LocalDate to = parseDate(toDate, "toDate", todayDate.minusDays(1));
        LocalDate from;
        if (fromDate == null || fromDate.isBlank()) {
            // 自有数据起算：该公司最早一张凭证；一张都没有就回落成 toDate
            String earliest = mapper.selectMinVoucherDate(companyId);
            from = (earliest == null || earliest.isBlank()) ? to : parseRequiredDate(earliest, "fromDate");
        } else {
            from = parseRequiredDate(fromDate, "fromDate");
        }
        if (from.isAfter(to)) {
            throw new OpcException("fromDate 不能晚于 toDate");
        }

        List<OpcFinanceVoucher> items = mapper.selectByCompanyAndDateRange(companyId, from, to);
        if (items == null) {
            items = List.of();
        }
        LocalDate yesterday = todayDate.minusDays(1);

        // 汇总在 Java 侧算：明细最多几十条，也避免 SQL 口径与 Java 口径两处漂移
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal yesterdayIn = BigDecimal.ZERO;
        BigDecimal yesterdayOut = BigDecimal.ZERO;
        int yesterdayCount = 0;
        for (OpcFinanceVoucher voucher : items) {
            BigDecimal credit = voucher.getTotalCredit() == null ? BigDecimal.ZERO : voucher.getTotalCredit();
            BigDecimal debit = voucher.getTotalDebit() == null ? BigDecimal.ZERO : voucher.getTotalDebit();
            totalIn = totalIn.add(credit);
            totalOut = totalOut.add(debit);
            if (yesterday.equals(wallDate(voucher.getVoucherDate()))) {
                yesterdayCount++;
                yesterdayIn = yesterdayIn.add(credit);
                yesterdayOut = yesterdayOut.add(debit);
            }
        }

        return VoucherRangeVo.builder()
                .companyId(companyId)
                .fromDate(from.toString())
                .toDate(to.toString())
                .count(items.size())
                .totalIn(totalIn)
                .totalOut(totalOut)
                .net(totalIn.subtract(totalOut))
                .yesterdayCount(yesterdayCount)
                .yesterdayIn(yesterdayIn)
                .yesterdayOut(yesterdayOut)
                .items(items)
                .build();
    }

    /**
     * 供测试覆盖的「今天」（北京时间）。
     *
     * <p>之所以做成可覆盖的方法而不是注入 {@code Clock}：注入 Clock 就要在每个模块
     * 各加一个 {@code @Bean}，为「一个日期」付出 4 处配置不划算。</p>
     */
    protected LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /**
     * 解析 yyyy-MM-dd；null/空串用缺省值；格式非法抛 {@link OpcException}。
     */
    private static LocalDate parseDate(String value, String field, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return parseRequiredDate(value, field);
    }

    /**
     * 解析必有的 yyyy-MM-dd；格式非法抛 {@link OpcException}。
     */
    private static LocalDate parseRequiredDate(String value, String field) {
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new OpcException(field + " 格式非法，应为 yyyy-MM-dd: " + value);
        }
    }

    /**
     * 把 DATE 列读出来的 {@link Date} 换算回库里存的那个日期。
     *
     * <p>两个坑：</p>
     * <ul>
     *   <li>用 {@code getTime()} 而不是 {@code toInstant()}：DATE 列可能读出
     *       {@code java.sql.Date}，它重写了 {@code toInstant()} 直接抛
     *       {@code UnsupportedOperationException}。</li>
     *   <li>换算回<b>业务时区</b>而不是 JVM 默认时区：容器 JVM 是 UTC，而 JDBC URL 里
     *       {@code serverTimezone=Asia/Shanghai} —— 驱动按上海时间解读库里的日期，
     *       得到的瞬间比墙上时间早 8 小时。用 UTC 去取日期可能差一天。</li>
     * </ul>
     */
    private static LocalDate wallDate(Date date) {
        if (date == null) {
            return null;
        }
        return Instant.ofEpochMilli(date.getTime()).atZone(BUSINESS_ZONE).toLocalDate();
    }

}
