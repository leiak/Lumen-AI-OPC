package com.ruoyi.opc.finance.service.impl;

import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import com.ruoyi.opc.finance.mapper.OpcFinanceBankFlowMapper;
import com.ruoyi.opc.finance.service.IOpcFinanceBankFlowService;
import com.ruoyi.opc.finance.vo.FlowAggVo;
import com.ruoyi.opc.finance.vo.FlowRangeVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * {@link IOpcFinanceBankFlowService} 实现。
 *
 * <p>业务规则：
 * <ul>
 *   <li>uploadBatch 空/空集合 → 返回 0，不调 mapper</li>
 *   <li>uploadBatch 每条 flow 自动生成 flowCode（毫秒时间戳 + hashCode 后 4 位）+ extracted=0 +
 *       status=IMPORTED + createBy=operator</li>
 *   <li>listPending 强制 extracted=0，limit=null 时默认 20</li>
 *   <li>markExtracted 强制 extracted=1 + status=EXTRACTED；voucherId 可为 null（mapper XML 用
 *       {@code <if>} 跳过字段）</li>
 *   <li>queryRange 的「今天/昨天」按北京时间算（容器 JVM 是 UTC）</li>
 * </ul>
 *
 * <p>flowCode 时间戳在同一批次内可能相同（System.currentTimeMillis 精度 1ms），
 * 实际生产建议改为 {@code OpcCodeGenerator.flowCode()}，但当前需求保持与历史 controller 行为一致。
 *
 * @author OAC
 */
@Service
@RequiredArgsConstructor
public class OpcFinanceBankFlowServiceImpl implements IOpcFinanceBankFlowService {

    /** 默认 listPending 条数 */
    private static final int DEFAULT_LIMIT = 20;

    /** AI 提取后状态 */
    private static final String STATUS_EXTRACTED = "EXTRACTED";

    /** 上传后初始状态 */
    private static final String STATUS_IMPORTED = "IMPORTED";

    /** 收入方向 */
    private static final String DIRECTION_IN = "IN";

    /** 支出方向 */
    private static final String DIRECTION_OUT = "OUT";

    private final OpcFinanceBankFlowMapper flowMapper;

    @Override
    public OpcFinanceBankFlow getById(Long id) {
        return flowMapper.selectById(id);
    }

    @Override
    public List<OpcFinanceBankFlow> listPending(Long companyId, Integer limit) {
        return flowMapper.selectByCompany(companyId, 0, limit == null ? DEFAULT_LIMIT : limit);
    }

    @Override
    public int uploadBatch(List<OpcFinanceBankFlow> flows, String operator) {
        if (flows == null || flows.isEmpty()) {
            return 0;
        }
        long stamp = System.currentTimeMillis();
        for (int i = 0; i < flows.size(); i++) {
            OpcFinanceBankFlow flow = flows.get(i);
            // 加 i 防止同批次 hashCode 碰撞
            flow.setFlowCode("F" + (stamp + i) + Math.abs((flow.hashCode() + i) % 10000));
            flow.setExtracted(0);
            flow.setStatus(STATUS_IMPORTED);
            flow.setCreateBy(operator);
        }
        return flowMapper.insertBatch(flows);
    }

    @Override
    public int markExtracted(Long id, Long voucherId, String operator) {
        OpcFinanceBankFlow flow = new OpcFinanceBankFlow();
        flow.setId(id);
        flow.setExtracted(1);
        flow.setVoucherId(voucherId);
        flow.setStatus(STATUS_EXTRACTED);
        flow.setUpdateBy(operator);
        return flowMapper.update(flow);
    }

    @Override
    public FlowAggVo aggregateByPeriod(Long companyId, String period) {
        AggSupport.validate(companyId, period);
        Map<String, Object> agg = AggSupport.orEmpty(flowMapper.aggregateByPeriod(companyId, period));

        return FlowAggVo.builder()
                .companyId(companyId)
                .period(period)
                .inTotal(AggSupport.amount(agg.get("in_total")))
                .outTotal(AggSupport.amount(agg.get("out_total")))
                .flowCount(AggSupport.count(agg.get("flow_count")))
                .extractedCount(AggSupport.count(agg.get("extracted_count")))
                .build();
    }

    // ==================== 区间查询（数字员工工具端点） ====================

    /**
     * {@inheritDoc}
     *
     * <p>为什么 fromDate 缺省值是「最早一条记录的日期」而不是「30 天前」：
     * 演示库最后一笔流水是 2026-10-21，固定 30 天窗口过了 2026-11-21 就空了，
     * 下游的对账断言会退化成「两个空集相等」—— 永远绿，等于没有。</p>
     */
    @Override
    public FlowRangeVo queryRange(Long companyId, String fromDate, String toDate) {
        if (companyId == null) {
            throw new OpcException("companyId 不能为空");
        }
        // today() 只取一次：跨零点调用两次会得到不同的「昨天」
        LocalDate todayDate = this.today();
        LocalDate to = RangeSupport.parseDate(toDate, "toDate", todayDate.minusDays(1));
        LocalDate from;
        if (fromDate == null || fromDate.isBlank()) {
            // 自有数据起算：该公司最早一条流水；一条都没有就回落成 toDate
            String earliest = flowMapper.selectMinTradeDate(companyId);
            from = (earliest == null || earliest.isBlank())
                    ? to
                    : RangeSupport.parseRequiredDate(earliest, "最早记录日期");
        } else {
            from = RangeSupport.parseRequiredDate(fromDate, "fromDate");
        }
        if (from.isAfter(to)) {
            throw new OpcException("fromDate 不能晚于 toDate");
        }

        List<OpcFinanceBankFlow> items = flowMapper.selectByCompanyAndDateRange(companyId, from, to);
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
        for (OpcFinanceBankFlow flow : items) {
            BigDecimal amount = flow.getAmount() == null ? BigDecimal.ZERO : flow.getAmount();
            boolean in = DIRECTION_IN.equals(flow.getDirection());
            boolean out = DIRECTION_OUT.equals(flow.getDirection());
            if (in) {
                totalIn = totalIn.add(amount);
            } else if (out) {
                totalOut = totalOut.add(amount);
            }
            if (yesterday.equals(RangeSupport.wallDate(flow.getTradeTime()))) {
                yesterdayCount++;
                if (in) {
                    yesterdayIn = yesterdayIn.add(amount);
                } else if (out) {
                    yesterdayOut = yesterdayOut.add(amount);
                }
            }
        }

        return FlowRangeVo.builder()
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
     * <p>为什么保留成 <b>实例</b>方法而不是直接用 {@link RangeSupport#businessToday()}：
     * 测试用 Mockito spy + {@code doReturn(...)} 覆盖它 —— 一旦变成静态方法，
     * spy 就盖不住，测试会随日期漂移。所以 service 内部一律调 {@code this.today()}。</p>
     *
     * <p>之所以不注入 {@code Clock}：注入 Clock 就要在每个模块各加一个 {@code @Bean}，
     * 为「一个日期」付出 4 处配置不划算。</p>
     */
    protected LocalDate today() {
        return RangeSupport.businessToday();
    }

}
