package com.ruoyi.opc.erp.mapper;

import com.ruoyi.opc.erp.domain.OpcErpDailySnapshot;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * ERP 库存每日快照 Mapper（Task 7）
 */
public interface OpcErpDailySnapshotMapper {

    /**
     * 插入一条快照。{@code (company_id, snapshot_date, sku_id)} 唯一约束冲突时由数据库报错。
     */
    int insert(OpcErpDailySnapshot snapshot);

    /**
     * 按公司 + 日期查询当日所有 SKU 快照。
     */
    List<OpcErpDailySnapshot> selectByCompanyAndDate(@Param("companyId") Long companyId,
                                                      @Param("date") LocalDate date);

    /**
     * 按公司 + 日期范围（含两端）查询快照，供月报聚合使用。
     */
    List<OpcErpDailySnapshot> selectByCompanyAndDateRange(@Param("companyId") Long companyId,
                                                           @Param("startDate") LocalDate start,
                                                           @Param("endDate") LocalDate end);
}
