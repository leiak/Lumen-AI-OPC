package com.ruoyi.opc.finance.service;

import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import com.ruoyi.opc.finance.vo.FlowAggVo;
import com.ruoyi.opc.finance.vo.FlowRangeVo;

import java.util.List;

/**
 * 银行流水 Service
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #uploadBatch} — 上传前初始化 flowCode / extracted=0 / status=IMPORTED / createBy</li>
 *   <li>{@link #listPending} — 待 AI 提取的流水（extracted=0）</li>
 *   <li>{@link #getById} — 详情</li>
 *   <li>{@link #markExtracted} — AI 提取完成后标记（写 voucher_id + status=EXTRACTED）</li>
 * </ul>
 *
 * @author OAC
 */
public interface IOpcFinanceBankFlowService {

    OpcFinanceBankFlow getById(Long id);

    /**
     * 待处理流水（extracted=0），按交易时间倒序。
     *
     * @param limit null 时默认 20
     */
    List<OpcFinanceBankFlow> listPending(Long companyId, Integer limit);

    /**
     * 批量上传银行流水（生成 flowCode + 初始化状态）。
     *
     * @return 实际写入条数（mapper.insertBatch 返回值）
     */
    int uploadBatch(List<OpcFinanceBankFlow> flows, String operator);

    /**
     * AI 提取完成后标记（写 extracted=1 + voucher_id + status=EXTRACTED + update_by）。
     *
     * @return mapper.update 影响行数
     */
    int markExtracted(Long id, Long voucherId, String operator);

    /**
     * 按期间聚合银行流水（M4 Task 1，供 opc-insight 经 Feign 拉取）。
     *
     * @param companyId 公司 ID
     * @param period    所属期 YYYY-MM
     * @throws com.ruoyi.opc.common.exception.OpcException 当 companyId 为空 / period 格式非法
     */
    FlowAggVo aggregateByPeriod(Long companyId, String period);

    /**
     * 区间查询银行流水（供数字员工经工具端点调用）。
     *
     * <p>闭区间 {@code [fromDate, toDate]}，日期格式 yyyy-MM-dd：
     * <ul>
     *   <li>{@code fromDate} 缺省 = 该公司最早一条流水的日期</li>
     *   <li>{@code toDate} 缺省 = 昨天（北京时间）</li>
     * </ul>
     *
     * @return 明细 + 汇总（IN 合计 / OUT 合计 / 净额）+ 昨天子集，
     *         并回写实际使用的 fromDate / toDate
     * @throws com.ruoyi.opc.common.exception.OpcException 当 companyId 为空 /
     *         日期格式非法 / fromDate 晚于 toDate
     */
    FlowRangeVo queryRange(Long companyId, String fromDate, String toDate);

}
