package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpBatch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OpcErpBatchMapper {
    int insert(OpcErpBatch batch);

    /** FIFO 扣减: 减少 remaining 数量 */
    int updateRemaining(@Param("id") Long id,
                        @Param("companyId") Long companyId,
                        @Param("delta") int delta);

    OpcErpBatch selectById(@Param("id") Long id, @Param("companyId") Long companyId);

    /**
     * FIFO 顺序: remaining > 0 ORDER BY production_date ASC LIMIT N
     */
    List<OpcErpBatch> selectFifoOrder(@Param("companyId") Long companyId,
                                      @Param("skuId") Long skuId,
                                      @Param("limit") int limit);

    /**
     * FIFO 顺序 + 悲观锁: SELECT ... FOR UPDATE
     */
    List<OpcErpBatch> selectFifoOrderForUpdate(@Param("companyId") Long companyId,
                                               @Param("skuId") Long skuId,
                                               @Param("limit") int limit);
}
