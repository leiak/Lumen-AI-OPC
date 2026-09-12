package com.ruoyi.opc.erp.service;

import com.ruoyi.opc.erp.domain.OpcErpSale;
import com.ruoyi.opc.erp.dto.OpcErpSaleDto;

import java.util.List;

/**
 * ERP 销售单服务接口。
 *
 * <p>状态机: DRAFT → CONFIRMED → COMPLETED / CANCELLED。
 * <ul>
 *   <li>create(): 生成 SO-yyyyMMdd-NNNN 编号,落 DRAFT 状态, 不动库存</li>
 *   <li>confirm(): FIFO 扣减 + SKU 库存 -quantity + 写流水, 状态单步晋升到 COMPLETED</li>
 *   <li>cancel(): 仅 DRAFT 可取消, 确认后需走退货流程</li>
 * </ul>
 */
public interface IOpcErpSaleService {

    /**
     * 创建销售草稿。
     *
     * <p>校验: items 非空, customerName 非空, 每项 skuId/qty/unitPrice 合法,
     * 预检 sku.stock >= item.quantity（仅校验总库存, 不锁批次）。</p>
     *
     * @return 新销售单 ID
     */
    Long create(Long companyId, Long operatorId, OpcErpSaleDto dto);

    /**
     * 确认销售单: DRAFT → COMPLETED。
     *
     * <p>流程: 对每明细 FIFO 扣减 → 落 sale_item(batchId 指派) → SKU.stock -qty(乐观锁)
     * → 写库存流水 → 更新 sale 状态到 COMPLETED。</p>
     */
    void confirm(Long id, Long companyId, Long operatorId);

    /**
     * 取消销售单: 仅 DRAFT 可取消。
     */
    void cancel(Long id, Long companyId, Long operatorId);

    /** 查询详情 */
    OpcErpSale detail(Long id, Long companyId);

    /** 列表(可选 status 过滤) */
    List<OpcErpSale> list(Long companyId, String status, Integer offset, Integer limit);

    /** 按销售单号查询 */
    OpcErpSale detailBySaleNo(Long companyId, String saleNo);
}
