package com.ruoyi.opc.erp.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.job.OpcErpDailySnapshotJob;
import com.ruoyi.opc.erp.job.OpcErpLowStockAlertJob;
import com.ruoyi.opc.erp.service.IOpcErpInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ERP 库存 Controller（Task 7）。
 *
 * <p>5 endpoints:
 * <ul>
 *   <li>GET  /sku/{skuId}?companyId=  单 SKU 实时库存</li>
 *   <li>GET  /low-stock?companyId=    低库存预警列表</li>
 *   <li>GET  /log/{skuId}?companyId=  SKU 库存流水（最近 30 天）</li>
 *   <li>POST /internal/snapshot       手动触发每日快照（Quartz 也可调）</li>
 *   <li>POST /internal/low-stock-alert 手动触发低库存告警扫描（Quartz 也可调）</li>
 * </ul>
 */
@RestController
@RequestMapping("/opc/erp/inventory")
@RequiredArgsConstructor
public class OpcErpInventoryController {

    private final IOpcErpInventoryService inventoryService;
    private final OpcErpDailySnapshotJob dailySnapshotJob;
    private final OpcErpLowStockAlertJob lowStockAlertJob;

    @GetMapping("/sku/{skuId}")
    public R<OpcErpProductSku> getSku(@PathVariable Long skuId,
                                      @RequestParam Long companyId) {
        return R.ok(inventoryService.getRealtimeStock(skuId, companyId));
    }

    @GetMapping("/low-stock")
    public R<List<OpcErpProductSku>> lowStock(@RequestParam Long companyId) {
        return R.ok(inventoryService.listLowStock(companyId));
    }

    @GetMapping("/log/{skuId}")
    public R<List<OpcErpInventoryLog>> getLog(@PathVariable Long skuId,
                                              @RequestParam Long companyId) {
        return R.ok(inventoryService.getInventoryLog(skuId, companyId));
    }

    /**
     * 手动触发每日快照（Task 7 + Task 11 Quartz 调度也可调用）。
     */
    @PostMapping("/internal/snapshot")
    public R<Void> runSnapshot() {
        dailySnapshotJob.execute();
        return R.ok();
    }

    /**
     * 手动触发低库存告警扫描（Task 7 + Task 11 Quartz 调度也可调用）。
     */
    @PostMapping("/internal/low-stock-alert")
    public R<Integer> runLowStockAlert() {
        return R.ok(lowStockAlertJob.executeAndReturnCount());
    }
}
