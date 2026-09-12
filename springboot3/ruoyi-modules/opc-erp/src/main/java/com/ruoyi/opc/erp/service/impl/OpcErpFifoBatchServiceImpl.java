package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpBatch;
import com.ruoyi.opc.erp.dto.DeductedBatch;
import com.ruoyi.opc.erp.mapper.OpcErpBatchMapper;
import com.ruoyi.opc.erp.service.IOpcErpFifoBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * ERP FIFO 批次扣减实现。
 *
 * <p>算法:
 * <ol>
 *   <li>SELECT ... FOR UPDATE 锁定批次(行级锁,事务结束释放)</li>
 *   <li>汇总 totalRemaining,与请求 quantity 比较</li>
 *   <li>按 FIFO 顺序扣减,逐行调用 updateRemaining(id, companyId, -take)</li>
 *   <li>返回 (batchId, take) 列表给上层(销售出库/退货入库使用)</li>
 * </ol>
 *
 * <p>并发安全:
 * <ul>
 *   <li>行锁保证两个并发扣减按顺序排队,不会出现负库存</li>
 *   <li>批次数 LIMIT 100,防止一次扣减跨过多批次</li>
 *   <li>扣减数量 > 100 时应在应用层分单</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcErpFifoBatchServiceImpl implements IOpcErpFifoBatchService {

    /** 单次 FIFO 拉取最大批次数,避免超长事务 */
    private static final int FIFO_BATCH_LIMIT = 100;

    private final OpcErpBatchMapper batchMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DeductedBatch> deductFifo(Long companyId, Long skuId, int quantity) {
        // 1) 入参校验
        if (quantity <= 0) {
            throw new ServiceException("扣减数量必须 > 0");
        }

        // 2) FOR UPDATE 锁定批次(行锁)
        List<OpcErpBatch> batches = batchMapper.selectFifoOrderForUpdate(companyId, skuId, FIFO_BATCH_LIMIT);
        if (batches == null || batches.isEmpty()) {
            throw new ServiceException(String.format("库存不足: 没有可用批次 skuId=%d", skuId));
        }

        // 3) 汇总可用库存
        int totalRemaining = batches.stream()
                .mapToInt(b -> b.getRemaining() == null ? 0 : b.getRemaining())
                .sum();
        if (totalRemaining < quantity) {
            throw new ServiceException(String.format(
                    "库存不足: 需要 %d, 可用 %d, skuId=%d", quantity, totalRemaining, skuId));
        }

        // 4) FIFO 扣减: 顺序遍历,逐行扣减
        List<DeductedBatch> result = new ArrayList<>();
        int remaining = quantity;
        for (OpcErpBatch batch : batches) {
            if (remaining <= 0) {
                break;
            }
            int available = batch.getRemaining() == null ? 0 : batch.getRemaining();
            int take = Math.min(available, remaining);
            if (take <= 0) {
                continue;
            }
            // updateRemaining 使用 delta = -take (SQL: remaining = remaining + #{delta})
            int affected = batchMapper.updateRemaining(batch.getId(), companyId, -take);
            if (affected == 0) {
                throw new ServiceException(
                        "批次扣减失败(乐观锁冲突或行不存在) batchId=" + batch.getId());
            }
            result.add(new DeductedBatch(batch.getId(), take));
            remaining -= take;
        }

        log.info("FIFO 扣减完成 companyId={} skuId={} quantity={} batches={}",
                companyId, skuId, quantity, result.size());
        return result;
    }
}
