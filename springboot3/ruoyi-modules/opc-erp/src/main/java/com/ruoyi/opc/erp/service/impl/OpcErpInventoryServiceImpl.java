package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.mapper.OpcErpInventoryLogMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import com.ruoyi.opc.erp.service.IOpcErpInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ERP 库存查询服务实现（Task 7）。
 *
 * <p>W72 Task 7 设计:
 * <ul>
 *   <li>实时库存 = SKU.stock 字段直读（已由采购/销售 confirm 维护）</li>
 *   <li>低库存 SQL: stock &lt; threshold（已在 OpcErpProductSkuMapper.selectLowStock 实现）</li>
 *   <li>库存流水 30 天窗口: sinceDate = today - 30 days，按 create_time DESC</li>
 *   <li>notifyLowStock 占位: log.info 数量统计，Task 8 接入 NotificationGateway</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcErpInventoryServiceImpl implements IOpcErpInventoryService {

    /** 库存流水查询窗口：最近 N 天 */
    private static final int LOG_LOOKBACK_DAYS = 30;

    private final OpcErpProductSkuMapper skuMapper;
    private final OpcErpInventoryLogMapper inventoryLogMapper;

    @Override
    public OpcErpProductSku getRealtimeStock(Long skuId, Long companyId) {
        if (skuId == null || companyId == null) {
            throw new ServiceException("skuId/companyId 不能为空");
        }
        OpcErpProductSku sku = skuMapper.selectById(skuId, companyId);
        if (sku == null) {
            throw new ServiceException("SKU 不存在或无权访问 id=" + skuId);
        }
        return sku;
    }

    @Override
    public List<OpcErpProductSku> listLowStock(Long companyId) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        // mapper SQL: WHERE company_id=? AND stock < threshold AND threshold > 0
        return skuMapper.selectLowStock(companyId, null);
    }

    @Override
    public List<OpcErpInventoryLog> getInventoryLog(Long skuId, Long companyId) {
        if (skuId == null || companyId == null) {
            throw new ServiceException("skuId/companyId 不能为空");
        }
        LocalDateTime sinceDate = LocalDate.now()
                .minusDays(LOG_LOOKBACK_DAYS)
                .atStartOfDay();
        return inventoryLogMapper.selectBySkuId(companyId, skuId, sinceDate, 0, Integer.MAX_VALUE);
    }

    @Override
    public int notifyLowStock(Long companyId) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        List<OpcErpProductSku> lowSkus = listLowStock(companyId);
        int count = lowSkus.size();
        // Task 8 会通过 OpcErpNotificationGateway.sendInbox 推送每条 SKU 告警
        log.info("[opc-erp] 低库存扫描 companyId={} count={} (Task 8 接入通知)", companyId, count);
        return count;
    }
}
