package com.ruoyi.opc.erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * FIFO 扣减结果: 表示 (batchId, 该批次被扣减的数量)。
 *
 * <p>当一个 SKU 需要跨多个批次扣减时,返回多个 DeductedBatch, 按 FIFO 顺序排列。</p>
 */
@Data
@AllArgsConstructor
public class DeductedBatch {
    private Long batchId;
    private Integer quantity;
}
