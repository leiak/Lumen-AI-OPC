package com.ruoyi.opc.erp.job;

import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.erp.domain.OpcErpDailySnapshot;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.mapper.OpcErpDailySnapshotMapper;
import com.ruoyi.opc.erp.mapper.OpcErpInventoryLogMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * ERP 库存每日快照 Job（Task 7）。
 *
 * <p>调度时间：每日 23:55（由 ruoyi-job sys_job 或 inventory 内部触发端点调用）。
 *
 * <p>核心算法（对单租户 prototype，按 companyId=1 单公司循环）：
 * <ol>
 *   <li>遍历公司下所有 SKU</li>
 *   <li>读取当日 {@code opc_erp_inventory_log} 计算 in_qty / out_qty</li>
 *   <li>closing_stock = sku.stock（业务上即昨日期末）</li>
 *   <li>opening_stock = closing - in + out（保证 opening+in-out = closing）</li>
 *   <li>写入 {@code opc_erp_daily_snapshot}</li>
 * </ol>
 *
 * <p>W72 教训:
 * <ul>
 *   <li>{@code uk_company_date_sku} 唯一约束防重跑,二次运行同一天会 DB 报错 → service 层不需显式判重</li>
 *   <li>{@code opening_stock} 必须用 closing-in+out 倒推,不能直接读昨日 closing(否则跨日重算会漂移)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpcErpDailySnapshotJob {

    /** Prototype 单公司 ID;后续接多公司改造时改为循环 */
    private static final Long DEFAULT_COMPANY_ID = 1L;

    private final OpcErpProductSkuMapper skuMapper;
    private final OpcErpInventoryLogMapper inventoryLogMapper;
    private final OpcErpDailySnapshotMapper snapshotMapper;

    /**
     * Quartz 调用入口（ruoyi-job sys_job.invokeTarget 通过反射调用）。
     * 也可被 InventoryController 内部端点触发。
     */
    public void execute() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        try {
            int count = executeForCompany(DEFAULT_COMPANY_ID, yesterday);
            log.info("[opc-erp] 每日快照完成 date={} companyId={} snapshots={}",
                    yesterday, DEFAULT_COMPANY_ID, count);
        } catch (Exception e) {
            log.error("[opc-erp] 每日快照失败 date={} companyId={}", yesterday, DEFAULT_COMPANY_ID, e);
            throw new IllegalStateException("每日快照失败: " + e.getMessage(), e);
        }
    }

    /**
     * 对指定公司 + 指定日期生成所有 SKU 的快照。
     *
     * @return 写入条数
     */
    public int executeForCompany(Long companyId, LocalDate date) {
        if (companyId == null || date == null) {
            throw new IllegalArgumentException("companyId/date 不能为空");
        }

        List<OpcErpProductSku> skus = skuMapper.selectByCompany(companyId);
        int written = 0;
        for (OpcErpProductSku sku : skus) {
            List<OpcErpInventoryLog> logs =
                    inventoryLogMapper.selectBySkuIdAndDate(companyId, sku.getId(), date);

            int inQty = 0;
            int outQty = 0;
            for (OpcErpInventoryLog logRow : logs) {
                int change = logRow.getChange() == null ? 0 : logRow.getChange();
                if (change > 0) {
                    inQty += change;
                } else if (change < 0) {
                    outQty += -change;  // 转为正数
                }
            }

            int closingStock = sku.getStock() == null ? 0 : sku.getStock();
            int openingStock = closingStock - inQty + outQty;
            if (openingStock < 0) {
                // 极端情况:历史 stock 字段被外部改过,opening 为负 → 兜底为 0,记 warn
                log.warn("[opc-erp] opening 计算为负 skuId={} closing={} in={} out={}, 兜底 0",
                        sku.getId(), closingStock, inQty, outQty);
                openingStock = 0;
            }

            OpcErpDailySnapshot snap = OpcErpDailySnapshot.builder()
                    .id(SnowflakeIdGenerator.nextId())
                    .companyId(companyId)
                    .snapshotDate(date)
                    .skuId(sku.getId())
                    .openingStock(openingStock)
                    .inQty(inQty)
                    .outQty(outQty)
                    .closingStock(closingStock)
                    .build();
            snapshotMapper.insert(snap);
            written++;
        }
        return written;
    }
}
