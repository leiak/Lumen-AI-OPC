package com.ruoyi.opc.erp.service;

import com.ruoyi.opc.erp.domain.OpcErpPurchase;
import com.ruoyi.opc.erp.dto.OpcErpPurchaseDto;

import java.util.List;

/**
 * ERP 采购单服务接口
 *
 * <p>负责采购单的 DRAFT → CONFIRMED → COMPLETED / CANCELLED 状态机迁移,
 * 以及入库时自动创建批次 + 增加 SKU 库存 + 写库存流水。
 */
public interface IOpcErpPurchaseService {

    /**
     * 创建草稿采购单（包含明细）。
     *
     * @param companyId  租户 ID
     * @param operatorId 操作员 ID（写入 created_by / operator_id）
     * @param dto        入参（必填 supplierId + items 非空）
     * @return 新采购单 ID（雪花算法）
     */
    Long create(Long companyId, Long operatorId, OpcErpPurchaseDto dto);

    /**
     * 确认采购单：DRAFT → CONFIRMED
     * <ul>
     *   <li>为每个明细创建批次（remaining = quantity）</li>
     *   <li>SKU.stock += quantity（乐观锁）</li>
     *   <li>写库存流水（PURCHASE_IN）</li>
     *   <li>status = CONFIRMED（同时设置 confirmed_by/at）</li>
     *   <li>当所有明细入库后 → status 自动晋升为 COMPLETED</li>
     * </ul>
     */
    void confirm(Long id, Long companyId, Long operatorId);

    /**
     * 取消采购单：DRAFT → CANCELLED。
     * <p>仅允许 DRAFT → CANCELLED（CONFIRMED 之后需走退货流程，不能直接取消）。</p>
     */
    void cancel(Long id, Long companyId, Long operatorId);

    /**
     * 查询采购单详情。
     */
    OpcErpPurchase detail(Long id, Long companyId);

    /**
     * 列出采购单（按 status 可选过滤）。
     */
    List<OpcErpPurchase> list(Long companyId, String status, Integer offset, Integer limit);

    /**
     * 按采购单号查询。
     */
    OpcErpPurchase detailByPurchaseNo(Long companyId, String purchaseNo);
}
