package com.ruoyi.opc.erp.service;

import com.ruoyi.opc.erp.dto.DeductedBatch;

import java.util.List;

/**
 * ERP FIFO 批次扣减服务。
 *
 * <p>核心规则:
 * <ul>
 *   <li>按 batch.production_date ASC, 同日期按 id ASC 顺序扣减</li>
 *   <li>使用 SELECT ... FOR UPDATE 行级锁,保证并发安全</li>
 *   <li>必须在外层 @Transactional 中调用,锁随事务结束释放</li>
 *   <li>若总可用 remaining < quantity,抛出 ServiceException</li>
 * </ul>
 */
public interface IOpcErpFifoBatchService {

    /**
     * FIFO 扣减:从某公司某 SKU 的批次中按生产日期顺序扣减指定数量。
     *
     * <p>对每行被扣减的批次,调用 {@code batchMapper.updateRemaining(id, companyId, -take)},
     * 并在返回列表中记录 (batchId, take)。</p>
     *
     * @param companyId 租户 ID
     * @param skuId     SKU ID
     * @param quantity  扣减数量, 必须 > 0
     * @return FIFO 扣减明细列表,按扣减顺序排列
     * @throws com.ruoyi.common.core.exception.ServiceException 库存不足时
     */
    List<DeductedBatch> deductFifo(Long companyId, Long skuId, int quantity);
}
