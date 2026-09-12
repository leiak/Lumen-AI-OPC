package com.ruoyi.opc.erp.service;

import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;

import java.util.List;

/**
 * ERP 库存查询服务（Task 7）。
 *
 * <p>实时库存/低库存/库存流水三类查询，供前端库存页 + Quartz 预警 Job 调用。
 */
public interface IOpcErpInventoryService {

    /**
     * 单 SKU 实时库存：直接读取 SKU 当前 stock 字段。
     *
     * @throws com.ruoyi.common.core.exception.ServiceException SKU 不存在或无权访问
     */
    OpcErpProductSku getRealtimeStock(Long skuId, Long companyId);

    /**
     * 低库存列表：stock &lt; threshold 且 ACTIVE 状态的 SKU。
     */
    List<OpcErpProductSku> listLowStock(Long companyId);

    /**
     * SKU 库存流水（最近 30 天），按 create_time DESC 排序。
     */
    List<OpcErpInventoryLog> getInventoryLog(Long skuId, Long companyId);

    /**
     * 低库存通知处理（Task 7 占位实现：仅日志统计，Task 8 接入 NotificationGateway）。
     *
     * @return 低库存 SKU 数量
     */
    int notifyLowStock(Long companyId);
}
