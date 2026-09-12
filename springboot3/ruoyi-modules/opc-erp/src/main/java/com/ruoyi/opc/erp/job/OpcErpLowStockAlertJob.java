package com.ruoyi.opc.erp.job;

import com.ruoyi.opc.erp.service.IOpcErpInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ERP 低库存告警 Job（Task 7）。
 *
 * <p>调度时间：每小时（由 ruoyi-job sys_job 或 inventory 内部触发端点调用）。
 *
 * <p>W72 Task 7 设计:
 * <ul>
 *   <li>当前只统计低库存 SKU 数量并写日志</li>
 *   <li>Task 8 接入 {@code OpcErpNotificationGateway.sendInbox} 给收件人发送通知</li>
 *   <li>未来可接 Redis 24h 去重避免轰炸</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpcErpLowStockAlertJob {

    /** Prototype 单公司 ID;多公司改造时改为循环 */
    private static final Long DEFAULT_COMPANY_ID = 1L;

    private final IOpcErpInventoryService inventoryService;
    // Task 8 接入:
    // @Autowired private OpcErpNotificationGateway notificationGateway;

    /**
     * Quartz 调用入口（ruoyi-job sys_job.invokeTarget 通过反射调用）。
     */
    public void execute() {
        runOnce(DEFAULT_COMPANY_ID);
    }

    /**
     * InventoryController 内部触发端点专用 — 返回扫描到的低库存数量。
     */
    public int executeAndReturnCount() {
        return runOnce(DEFAULT_COMPANY_ID);
    }

    /**
     * 对单公司跑一次扫描（共享内部逻辑，避免 execute() 二次调用 inventoryService）。
     */
    private int runOnce(Long companyId) {
        try {
            int count = inventoryService.notifyLowStock(companyId);
            // TODO Task 8: 遍历低库存 SKU → notificationGateway.sendInbox(recipientUserId, ...)
            log.info("[opc-erp] 低库存扫描完成 companyId={} count={}", companyId, count);
            return count;
        } catch (Exception e) {
            log.error("[opc-erp] 低库存扫描失败 companyId={}", companyId, e);
            throw new IllegalStateException("低库存扫描失败: " + e.getMessage(), e);
        }
    }
}
