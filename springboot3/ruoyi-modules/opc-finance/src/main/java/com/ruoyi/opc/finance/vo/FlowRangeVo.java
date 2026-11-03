package com.ruoyi.opc.finance.vo;

import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 银行流水区间查询结果（闭区间 {@code [fromDate, toDate]}）
 *
 * <p>给「数字员工」用：它要能拿到「某段时间有哪些流水、收多少支多少」，
 * 而且每个数字都要能跟 {@code opc_finance_bank_flow} 的行对得上。
 *
 * <p><b>区间语义</b>（不是「近 30 天」）：
 * <ul>
 *   <li>{@code fromDate} 缺省 = 该公司<b>最早一条</b>流水的日期 —— 不是 30 天前。
 *       演示库最后一笔流水是 2026-10-21，固定 30 天窗口过了 2026-11-21 就空了，
 *       对账断言会退化成「两个空集相等」——永远绿，等于没有。</li>
 *   <li>{@code toDate} 缺省 = <b>昨天</b>（北京时间）—— 今天的数据不进日报。</li>
 *   <li>响应里回写<b>实际使用</b>的 fromDate / toDate，健康检查靠这两个值原样复算。</li>
 * </ul>
 *
 * <p><b>口径</b>：{@code totalIn} = {@code direction='IN'} 的 amount 之和；
 * {@code totalOut} = {@code direction='OUT'} 的 amount 之和；{@code net} = 两者之差。
 *
 * <p><b>金额的 scale 不统一（下游请用 compareTo，不要用 equals）</b>：区间为空时
 * 各合计是 {@link java.math.BigDecimal#ZERO}，JSON 里渲染成 {@code 0}；
 * 区间非空时是库里 amount 的 scale（2），JSON 里渲染成 {@code 51892.00}。
 * {@code BigDecimal.equals} 连 scale 一起比，{@code 0} 和 {@code 0.00} 不相等；
 * 要比数值请用 {@code compareTo(...)}。</p>
 *
 * <p><b>items 里两类时间戳口径不同</b>：{@code tradeTime} 是<b>业务发生时间</b>，
 * 渲染成北京时间墙上时间（和库里存的一致）；{@code createTime} / {@code updateTime}
 * 是系统写入的<b>UTC</b> 时间戳（MySQL 服务器时钟是 UTC），渲染出来就是 UTC 墙上时间。
 * 两者不是同一个时区，看的时候别混。</p>
 *
 * @author OAC
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowRangeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long companyId;

    /** 实际使用的起始日 yyyy-MM-dd（缺省时 = 该公司最早一条流水的日期） */
    private String fromDate;

    /** 实际使用的截止日 yyyy-MM-dd（缺省时 = 昨天，北京时间） */
    private String toDate;

    /** 区间内流水条数 */
    private int count;

    /** 收入合计：direction='IN' 的 amount 之和 */
    private BigDecimal totalIn;

    /** 支出合计：direction='OUT' 的 amount 之和 */
    private BigDecimal totalOut;

    /** 净流入 = totalIn - totalOut */
    private BigDecimal net;

    /** 昨天（北京时间今天的前一天）那一天的流水条数 */
    private int yesterdayCount;

    /** 昨天那天的收入合计 */
    private BigDecimal yesterdayIn;

    /** 昨天那天的支出合计 */
    private BigDecimal yesterdayOut;

    /** 区间内的流水明细（按 trade_time 升序、同日按 id 升序） */
    private List<OpcFinanceBankFlow> items;

}
