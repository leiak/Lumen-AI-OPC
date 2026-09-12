package com.ruoyi.opc.erp.service;

import com.ruoyi.opc.erp.domain.OpcErpReturn;
import com.ruoyi.opc.erp.dto.OpcErpReturnDto;

import java.util.List;

/**
 * ERP 退货单服务接口。
 *
 * <p>双路径退货:
 * <ul>
 *   <li>SALES_RETURN: 客户退货 → 入库 → SKU 库存 +item.qty → 批次 remaining +item.qty
 *       → 库存流水 SALES_RETURN_IN(+)</li>
 *   <li>SUPPLIER_RETURN: 供应商退货 → 出库 → SKU 库存 -item.qty → 批次 remaining -item.qty
 *       → 库存流水 SUPPLIER_RETURN_OUT(-)</li>
 * </ul>
 */
public interface IOpcErpReturnService {

    /**
     * 创建退货草稿(支持 SALES_RETURN / SUPPLIER_RETURN)。
     *
     * @return 新退货单 ID
     */
    Long create(Long companyId, Long operatorId, OpcErpReturnDto dto);

    /**
     * 确认退货单(DRAFT → COMPLETED):
     * <ul>
     *   <li>对每条 item,根据 return_type 走库存增减路径</li>
     *   <li>写库存流水</li>
     *   <li>更新退货单 status=COMPLETED, confirmedBy/at, completedAt</li>
     * </ul>
     */
    void confirm(Long id, Long companyId, Long operatorId);

    /**
     * 取消退货单(仅 DRAFT → CANCELLED)。
     */
    void cancel(Long id, Long companyId, Long operatorId);

    /**
     * 查询详情。
     */
    OpcErpReturn detail(Long id, Long companyId);

    /**
     * 列出退货单(可选 returnType + status 过滤)。
     */
    List<OpcErpReturn> list(Long companyId, String returnType, String status,
                            Integer offset, Integer limit);
}