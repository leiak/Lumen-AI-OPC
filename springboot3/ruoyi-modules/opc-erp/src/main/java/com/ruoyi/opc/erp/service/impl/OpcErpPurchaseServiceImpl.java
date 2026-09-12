package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.erp.domain.OpcErpBatch;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.domain.OpcErpPurchase;
import com.ruoyi.opc.erp.domain.OpcErpPurchaseItem;
import com.ruoyi.opc.erp.dto.OpcErpPurchaseDto;
import com.ruoyi.opc.erp.dto.OpcErpPurchaseItemDto;
import com.ruoyi.opc.erp.enums.ErpInventoryLogType;
import com.ruoyi.opc.erp.enums.ErpPurchaseStatus;
import com.ruoyi.opc.erp.mapper.OpcErpBatchMapper;
import com.ruoyi.opc.erp.mapper.OpcErpInventoryLogMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import com.ruoyi.opc.erp.mapper.OpcErpPurchaseItemMapper;
import com.ruoyi.opc.erp.mapper.OpcErpPurchaseMapper;
import com.ruoyi.opc.erp.service.IOpcErpPurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ERP 采购单服务实现
 *
 * <p>核心逻辑:
 * <ul>
 *   <li>create(): 生成 PO-yyyyMMdd-NNNN 编号,落 DRAFT 状态</li>
 *   <li>confirm(): 自动建批次 + SKU 入库 + 写流水,状态 CONFIRMED → COMPLETED</li>
 *   <li>cancel(): 仅 DRAFT 可取消,CONFIRMED 之后走退货流程</li>
 * </ul>
 *
 * <p>W50/W71 教训:
 * <ul>
 *   <li>NOT NULL DEFAULT 列必须在 service 层显式兜底</li>
 *   <li>状态机非法迁移抛 ServiceException,带"期望/实际"提示</li>
 *   <li>SKU.stock 增减用 updateByIdWithVersion（乐观锁）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcErpPurchaseServiceImpl implements IOpcErpPurchaseService {

    /** purchase_no 日期格式: yyyyMMdd */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OpcErpPurchaseMapper purchaseMapper;
    private final OpcErpPurchaseItemMapper purchaseItemMapper;
    private final OpcErpBatchMapper batchMapper;
    private final OpcErpProductSkuMapper skuMapper;
    private final OpcErpInventoryLogMapper inventoryLogMapper;

    // ============================================================
    // create
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long companyId, Long operatorId, OpcErpPurchaseDto dto) {
        // 1) 入参校验
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (operatorId == null) {
            throw new ServiceException("operatorId 不能为空");
        }
        if (dto == null) {
            throw new ServiceException("采购单 DTO 不能为空");
        }
        if (dto.getSupplierId() == null) {
            throw new ServiceException("supplierId 不能为空");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new ServiceException("采购明细不能为空,至少 1 个 SKU");
        }

        // 2) 计算 totalAmount + 校验明细字段
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OpcErpPurchaseItem> items = new ArrayList<>(dto.getItems().size());
        Long purchaseId = SnowflakeIdGenerator.nextId();
        for (OpcErpPurchaseItemDto itemDto : dto.getItems()) {
            if (itemDto.getSkuId() == null) {
                throw new ServiceException("明细 skuId 不能为空");
            }
            if (itemDto.getQuantity() == null || itemDto.getQuantity() <= 0) {
                throw new ServiceException("明细 quantity 必须 > 0");
            }
            if (itemDto.getUnitPrice() == null || itemDto.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("明细 unitPrice 必须 >= 0");
            }
            BigDecimal subtotal = itemDto.getUnitPrice().multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            items.add(OpcErpPurchaseItem.builder()
                    .id(SnowflakeIdGenerator.nextId())
                    .companyId(companyId)
                    .purchaseId(purchaseId)
                    .skuId(itemDto.getSkuId())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(itemDto.getUnitPrice())
                    .subtotal(subtotal)
                    .batchNo(itemDto.getBatchNo())
                    .productionDate(itemDto.getProductionDate())
                    .expiryDate(itemDto.getExpiryDate())
                    .build());
        }

        // 3) 生成 purchase_no
        LocalDate today = LocalDate.now();
        String purchaseNo = generatePurchaseNo(companyId, today);

        // 4) 落库 purchase
        OpcErpPurchase purchase = OpcErpPurchase.builder()
                .id(purchaseId)
                .companyId(companyId)
                .purchaseNo(purchaseNo)
                .supplierId(dto.getSupplierId())
                .totalAmount(totalAmount)
                .status(ErpPurchaseStatus.DRAFT.getCode())
                .operatorId(operatorId)
                .remark(dto.getRemark())
                .createdBy(operatorId)
                .build();
        purchaseMapper.insert(purchase);

        // 5) 批量落库 items
        purchaseItemMapper.insertBatch(items);

        log.info("创建采购草稿 id={} purchaseNo={} supplierId={} items={} totalAmount={} operator={}",
                purchaseId, purchaseNo, dto.getSupplierId(), items.size(), totalAmount, operatorId);
        return purchaseId;
    }

    // ============================================================
    // confirm
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Long companyId, Long operatorId) {
        if (id == null || companyId == null || operatorId == null) {
            throw new ServiceException("id/companyId/operatorId 不能为空");
        }

        // 1) 加载 + 状态校验
        OpcErpPurchase purchase = purchaseMapper.selectById(id, companyId);
        if (purchase == null) {
            throw new ServiceException("采购单不存在或无权访问 id=" + id);
        }
        ErpPurchaseStatus current = parseStatus(purchase.getStatus());
        if (current != ErpPurchaseStatus.DRAFT) {
            throw new ServiceException(
                    "采购单状态非法: 期望 DRAFT,实际 " + current.getCode());
        }

        // 2) 加载明细
        List<OpcErpPurchaseItem> items = purchaseItemMapper.selectByPurchaseId(companyId, id);
        if (items == null || items.isEmpty()) {
            throw new ServiceException("采购单无明细,无法确认 id=" + id);
        }

        // 3) 对每个明细: 创建批次 + 增加 SKU 库存 + 写库存流水
        LocalDateTime now = LocalDateTime.now();
        for (OpcErpPurchaseItem item : items) {
            // 3.1) 创建批次
            OpcErpBatch batch = OpcErpBatch.builder()
                    .id(SnowflakeIdGenerator.nextId())
                    .companyId(companyId)
                    .skuId(item.getSkuId())
                    .batchNo(item.getBatchNo())
                    .quantity(item.getQuantity())
                    .remaining(item.getQuantity())  // 初始 remaining = quantity
                    .productionDate(item.getProductionDate())
                    .expiryDate(item.getExpiryDate())
                    .supplierId(purchase.getSupplierId())
                    .purchaseId(purchase.getId())
                    .build();
            batchMapper.insert(batch);

            // 3.2) 增加 SKU 库存（乐观锁）
            OpcErpProductSku sku = skuMapper.selectById(item.getSkuId(), companyId);
            if (sku == null) {
                throw new ServiceException("SKU 不存在或无权访问 id=" + item.getSkuId());
            }
            int newStock = (sku.getStock() == null ? 0 : sku.getStock()) + item.getQuantity();
            sku.setStock(newStock);
            int affected = skuMapper.updateByIdWithVersion(sku);
            if (affected == 0) {
                throw new ServiceException(
                        "SKU 库存更新失败（乐观锁冲突）skuId=" + item.getSkuId());
            }

            // 3.3) 写库存流水
            OpcErpInventoryLog logRow = OpcErpInventoryLog.builder()
                    .id(SnowflakeIdGenerator.nextId())
                    .companyId(companyId)
                    .skuId(item.getSkuId())
                    .batchId(batch.getId())
                    .change(item.getQuantity())  // 正数入库
                    .type(ErpInventoryLogType.PURCHASE_IN.getCode())
                    .refType("PURCHASE")
                    .refId(purchase.getId())
                    .remark("采购入库: " + purchase.getPurchaseNo())
                    .createdBy(operatorId)
                    .build();
            inventoryLogMapper.insert(logRow);
        }

        // 4) 更新 purchase 状态: CONFIRMED,自动晋升 COMPLETED
        purchase.setStatus(ErpPurchaseStatus.COMPLETED.getCode());
        purchase.setConfirmedBy(operatorId);
        purchase.setConfirmedAt(now);
        purchase.setCompletedAt(now);  // 单步确认 + 入库 + 完成
        purchaseMapper.updateById(purchase);

        log.info("确认采购单 id={} purchaseNo={} items={} operator={}",
                purchase.getId(), purchase.getPurchaseNo(), items.size(), operatorId);
    }

    // ============================================================
    // cancel
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long companyId, Long operatorId) {
        if (id == null || companyId == null || operatorId == null) {
            throw new ServiceException("id/companyId/operatorId 不能为空");
        }

        OpcErpPurchase purchase = purchaseMapper.selectById(id, companyId);
        if (purchase == null) {
            throw new ServiceException("采购单不存在或无权访问 id=" + id);
        }
        ErpPurchaseStatus current = parseStatus(purchase.getStatus());
        // 仅 DRAFT 可取消,CONFIRMED/COMPLETED 需走退货流程
        if (current != ErpPurchaseStatus.DRAFT) {
            throw new ServiceException(
                    "采购单状态非法: 期望 DRAFT,实际 " + current.getCode());
        }

        LocalDateTime now = LocalDateTime.now();
        purchase.setStatus(ErpPurchaseStatus.CANCELLED.getCode());
        purchase.setOperatorId(operatorId);
        purchase.setCancelledAt(now);
        purchaseMapper.updateById(purchase);

        log.info("取消采购单 id={} purchaseNo={} operator={}",
                purchase.getId(), purchase.getPurchaseNo(), operatorId);
    }

    // ============================================================
    // detail / list
    // ============================================================

    @Override
    public OpcErpPurchase detail(Long id, Long companyId) {
        if (id == null || companyId == null) {
            throw new ServiceException("id/companyId 不能为空");
        }
        OpcErpPurchase purchase = purchaseMapper.selectById(id, companyId);
        if (purchase == null) {
            throw new ServiceException("采购单不存在或无权访问 id=" + id);
        }
        return purchase;
    }

    @Override
    public List<OpcErpPurchase> list(Long companyId, String status, Integer offset, Integer limit) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        int off = offset == null ? 0 : offset;
        int lim = limit == null || limit <= 0 ? Integer.MAX_VALUE : limit;
        if (status == null || status.isBlank()) {
            return purchaseMapper.selectList(companyId, null, null, off, lim);
        }
        return purchaseMapper.selectList(companyId, status, null, off, lim);
    }

    @Override
    public OpcErpPurchase detailByPurchaseNo(Long companyId, String purchaseNo) {
        if (companyId == null || purchaseNo == null || purchaseNo.isBlank()) {
            throw new ServiceException("companyId/purchaseNo 不能为空");
        }
        OpcErpPurchase purchase = purchaseMapper.selectByPurchaseNo(companyId, purchaseNo);
        if (purchase == null) {
            throw new ServiceException("采购单不存在 purchaseNo=" + purchaseNo);
        }
        return purchase;
    }

    // ============================================================
    // 内部辅助
    // ============================================================

    /**
     * 生成 purchase_no: PO-yyyyMMdd-NNNN（NNNN = 当日序号,从 1 开始 4 位补零）。
     * <p>序号基于"今日已创建数 + 1",并发场景下不保证严格连续（极小窗口可能跳号）。</p>
     */
    private String generatePurchaseNo(Long companyId, LocalDate today) {
        String dateStr = today.format(DATE_FORMAT);
        Integer todayCount = purchaseMapper.countTodayPurchases(companyId, today);
        int seq = (todayCount == null ? 0 : todayCount) + 1;
        return String.format("PO-%s-%04d", dateStr, seq);
    }

    private ErpPurchaseStatus parseStatus(String code) {
        if (code == null || code.isBlank()) {
            throw new ServiceException("采购单状态为空");
        }
        try {
            return ErpPurchaseStatus.of(code);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("未知采购单状态: " + code);
        }
    }
}
