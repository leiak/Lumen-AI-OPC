package com.ruoyi.opc.finance.service;

import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;

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

}
