package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface OpcErpInventoryLogMapper {
    int insert(OpcErpInventoryLog log);

    /**
     * 按 sku 查询库存流水（sinceDate 之后）
     */
    List<OpcErpInventoryLog> selectBySkuId(@Param("companyId") Long companyId,
                                           @Param("skuId") Long skuId,
                                           @Param("sinceDate") LocalDateTime sinceDate,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    int countBySkuId(@Param("companyId") Long companyId,
                     @Param("skuId") Long skuId,
                     @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * 按 SKU + 单日查询流水（Task 7 Quartz 每日快照 Job 用：扫描当日 in/out 合计）。
     * <p>实现为 [date 00:00:00, date+1 00:00:00) 半开区间。</p>
     */
    List<OpcErpInventoryLog> selectBySkuIdAndDate(@Param("companyId") Long companyId,
                                                  @Param("skuId") Long skuId,
                                                  @Param("date") LocalDate date);
}
