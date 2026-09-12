package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.erp.domain.OpcErpInventoryLog;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.domain.OpcErpSale;
import com.ruoyi.opc.erp.domain.OpcErpSaleItem;
import com.ruoyi.opc.erp.dto.DeductedBatch;
import com.ruoyi.opc.erp.dto.OpcErpSaleDto;
import com.ruoyi.opc.erp.dto.OpcErpSaleItemDto;
import com.ruoyi.opc.erp.enums.ErpInventoryLogType;
import com.ruoyi.opc.erp.enums.ErpSaleStatus;
import com.ruoyi.opc.erp.mapper.OpcErpInventoryLogMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import com.ruoyi.opc.erp.mapper.OpcErpSaleItemMapper;
import com.ruoyi.opc.erp.mapper.OpcErpSaleMapper;
import com.ruoyi.opc.erp.service.IOpcErpFifoBatchService;
import com.ruoyi.opc.erp.service.IOpcErpSaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ERP 销售单服务实现。
 *
 * <p>核心逻辑:
 * <ul>
 *   <li>create(): 生成 SO-yyyyMMdd-NNNN 编号, 预检总库存, 落 DRAFT</li>
 *   <li>confirm(): FIFO 扣减批次 → 生成 sale_item(batchId 指派) → SKU 减库存(乐观锁)
 *       → 写流水 → COMPLETED</li>
 *   <li>cancel(): 仅 DRAFT 可取消</li>
 * </ul>
 *
 * <p>W50/W71 教训:
 * <ul>
 *   <li>NOT NULL DEFAULT 列必须在 service 层显式兜底（如 operatorId, createdBy）</li>
 *   <li>SKU.stock 增减用 updateByIdWithVersion（乐观锁）</li>
 *   <li>FIFO 扣减必须在 @Transactional 内, 行锁随事务结束释放</li>
 *   <li>每个 FIFO 扣减都生成对应 sale_item 行(同一 SKU 跨 N 批次 → N 行明细)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcErpSaleServiceImpl implements IOpcErpSaleService {

    /** sale_no 日期格式: yyyyMMdd */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OpcErpSaleMapper saleMapper;
    private final OpcErpSaleItemMapper saleItemMapper;
    private final OpcErpProductSkuMapper skuMapper;
    private final OpcErpInventoryLogMapper inventoryLogMapper;
    private final IOpcErpFifoBatchService fifoBatchService;

    // ============================================================
    // create
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long companyId, Long operatorId, OpcErpSaleDto dto) {
        // 1) 入参校验
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (operatorId == null) {
            throw new ServiceException("operatorId 不能为空");
        }
        if (dto == null) {
            throw new ServiceException("销售单 DTO 不能为空");
        }
        if (dto.getCustomerName() == null || dto.getCustomerName().isBlank()) {
            throw new ServiceException("customerName 不能为空");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new ServiceException("销售明细不能为空,至少 1 个 SKU");
        }

        // 2) 计算 totalAmount + 校验明细 + 预检库存
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OpcErpSaleItem> items = new ArrayList<>(dto.getItems().size());
        Long saleId = SnowflakeIdGenerator.nextId();
        for (OpcErpSaleItemDto itemDto : dto.getItems()) {
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

            // 预检: sku.stock >= item.quantity(只检查总库存, FIFO 在 confirm 时真实扣减)
            OpcErpProductSku sku = skuMapper.selectById(itemDto.getSkuId(), companyId);
            if (sku == null) {
                throw new ServiceException("SKU 不存在或无权访问 id=" + itemDto.getSkuId());
            }
            int currentStock = sku.getStock() == null ? 0 : sku.getStock();
            if (currentStock < itemDto.getQuantity()) {
                throw new ServiceException(String.format(
                        "库存不足(预检): skuId=%d 需要 %d, 可用 %d",
                        itemDto.getSkuId(), itemDto.getQuantity(), currentStock));
            }

            // DRAFT 阶段 batch_id 暂为空, confirm 时由 FIFO 指派
            items.add(OpcErpSaleItem.builder()
                    .id(SnowflakeIdGenerator.nextId())
                    .companyId(companyId)
                    .saleId(saleId)
                    .skuId(itemDto.getSkuId())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(itemDto.getUnitPrice())
                    .subtotal(subtotal)
                    .batchId(null)
                    .build());
        }

        // 3) 生成 sale_no
        LocalDate today = LocalDate.now();
        String saleNo = generateSaleNo(companyId, today);

        // 4) 落库 sale
        OpcErpSale sale = OpcErpSale.builder()
                .id(saleId)
                .companyId(companyId)
                .saleNo(saleNo)
                .customerName(dto.getCustomerName())
                .customerPhone(dto.getCustomerPhone())
                .totalAmount(totalAmount)
                .status(ErpSaleStatus.DRAFT.getCode())
                .operatorId(operatorId)
                .remark(dto.getRemark())
                .createdBy(operatorId)
                .build();
        saleMapper.insert(sale);

        // 5) 批量落库 items (DRAFT 阶段, 无 batch_id)
        saleItemMapper.insertBatch(items);

        log.info("创建销售草稿 id={} saleNo={} customer={} items={} totalAmount={} operator={}",
                saleId, saleNo, dto.getCustomerName(), items.size(), totalAmount, operatorId);
        return saleId;
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
        OpcErpSale sale = saleMapper.selectById(id, companyId);
        if (sale == null) {
            throw new ServiceException("销售单不存在或无权访问 id=" + id);
        }
        ErpSaleStatus current = parseStatus(sale.getStatus());
        if (current != ErpSaleStatus.DRAFT) {
            throw new ServiceException(
                    "销售单状态非法: 期望 DRAFT,实际 " + current.getCode());
        }

        // 2) 加载明细(DRAFT 阶段无 batch_id)
        List<OpcErpSaleItem> draftItems = saleItemMapper.selectBySaleId(companyId, id);
        if (draftItems == null || draftItems.isEmpty()) {
            throw new ServiceException("销售单无明细,无法确认 id=" + id);
        }

        // 3) 清空旧 items(为重写含 batchId 的明细做准备)
        //    注意: 这里假设 confirm 只跑一次;若重跑会先 delete 再 insert
        //    为简洁起见,不在 Mapper 加 deleteBySaleId,通过 DRAFT 状态机保证只跑一次
        saleItemMapper.deleteBySaleId(companyId, id);

        // 4) 对每个明细: FIFO 扣减 + 写新 item + 减 SKU 库存 + 写流水
        LocalDateTime now = LocalDateTime.now();
        List<OpcErpSaleItem> newItems = new ArrayList<>();
        for (OpcErpSaleItem item : draftItems) {
            // 4.1) FIFO 扣减 → (batchId, take) 列表
            List<DeductedBatch> deducted = fifoBatchService.deductFifo(
                    companyId, item.getSkuId(), item.getQuantity());

            // 4.2) 每个 FIFO 扣减 = 1 个新 sale_item(同一 SKU 跨 N 批次 → N 行)
            for (DeductedBatch db : deducted) {
                // 拆分原 subtotal: 按 take / quantity 比例分配
                BigDecimal itemSubtotal = item.getSubtotal() == null
                        ? BigDecimal.ZERO
                        : item.getSubtotal()
                            .multiply(BigDecimal.valueOf(db.getQuantity()))
                            .divide(BigDecimal.valueOf(item.getQuantity()), 2, java.math.RoundingMode.HALF_UP);
                BigDecimal itemUnitPrice = item.getUnitPrice();

                newItems.add(OpcErpSaleItem.builder()
                        .id(SnowflakeIdGenerator.nextId())
                        .companyId(companyId)
                        .saleId(id)
                        .skuId(item.getSkuId())
                        .quantity(db.getQuantity())
                        .unitPrice(itemUnitPrice)
                        .subtotal(itemSubtotal)
                        .batchId(db.getBatchId())
                        .build());
            }

            // 4.3) 减 SKU 库存(乐观锁)
            OpcErpProductSku sku = skuMapper.selectById(item.getSkuId(), companyId);
            if (sku == null) {
                throw new ServiceException("SKU 不存在或无权访问 id=" + item.getSkuId());
            }
            int newStock = (sku.getStock() == null ? 0 : sku.getStock()) - item.getQuantity();
            if (newStock < 0) {
                throw new ServiceException(String.format(
                        "SKU 库存变负数 skuId=%d", item.getSkuId()));
            }
            sku.setStock(newStock);
            int affected = skuMapper.updateByIdWithVersion(sku);
            if (affected == 0) {
                throw new ServiceException(
                        "SKU 库存更新失败(乐观锁冲突) skuId=" + item.getSkuId());
            }

            // 4.4) 写库存流水: 每个 FIFO 扣减一条记录
            for (DeductedBatch db : deducted) {
                OpcErpInventoryLog logRow = OpcErpInventoryLog.builder()
                        .id(SnowflakeIdGenerator.nextId())
                        .companyId(companyId)
                        .skuId(item.getSkuId())
                        .batchId(db.getBatchId())
                        .change(-db.getQuantity())  // 负数出库
                        .type(ErpInventoryLogType.SALE_OUT.getCode())
                        .refType("SALE")
                        .refId(sale.getId())
                        .remark("销售出库: " + sale.getSaleNo())
                        .createdBy(operatorId)
                        .build();
                inventoryLogMapper.insert(logRow);
            }
        }

        // 5) 批量插入新 items(含 batchId)
        if (!newItems.isEmpty()) {
            saleItemMapper.insertBatch(newItems);
        }

        // 6) 更新 sale 状态: DRAFT → COMPLETED(单步)
        sale.setStatus(ErpSaleStatus.COMPLETED.getCode());
        sale.setConfirmedBy(operatorId);
        sale.setConfirmedAt(now);
        sale.setCompletedAt(now);
        saleMapper.updateById(sale);

        log.info("确认销售单 id={} saleNo={} items={} operator={}",
                sale.getId(), sale.getSaleNo(), newItems.size(), operatorId);
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

        OpcErpSale sale = saleMapper.selectById(id, companyId);
        if (sale == null) {
            throw new ServiceException("销售单不存在或无权访问 id=" + id);
        }
        ErpSaleStatus current = parseStatus(sale.getStatus());
        // 仅 DRAFT 可取消, CONFIRMED/COMPLETED 需走退货流程
        if (current != ErpSaleStatus.DRAFT) {
            throw new ServiceException(
                    "销售单状态非法: 期望 DRAFT,实际 " + current.getCode());
        }

        LocalDateTime now = LocalDateTime.now();
        sale.setStatus(ErpSaleStatus.CANCELLED.getCode());
        sale.setOperatorId(operatorId);
        sale.setCancelledAt(now);
        saleMapper.updateById(sale);

        log.info("取消销售单 id={} saleNo={} operator={}",
                sale.getId(), sale.getSaleNo(), operatorId);
    }

    // ============================================================
    // detail / list
    // ============================================================

    @Override
    public OpcErpSale detail(Long id, Long companyId) {
        if (id == null || companyId == null) {
            throw new ServiceException("id/companyId 不能为空");
        }
        OpcErpSale sale = saleMapper.selectById(id, companyId);
        if (sale == null) {
            throw new ServiceException("销售单不存在或无权访问 id=" + id);
        }
        return sale;
    }

    @Override
    public List<OpcErpSale> list(Long companyId, String status, Integer offset, Integer limit) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        int off = offset == null ? 0 : offset;
        int lim = limit == null || limit <= 0 ? Integer.MAX_VALUE : limit;
        if (status == null || status.isBlank()) {
            return saleMapper.selectList(companyId, null, null, off, lim);
        }
        return saleMapper.selectList(companyId, status, null, off, lim);
    }

    @Override
    public OpcErpSale detailBySaleNo(Long companyId, String saleNo) {
        if (companyId == null || saleNo == null || saleNo.isBlank()) {
            throw new ServiceException("companyId/saleNo 不能为空");
        }
        OpcErpSale sale = saleMapper.selectBySaleNo(companyId, saleNo);
        if (sale == null) {
            throw new ServiceException("销售单不存在 saleNo=" + saleNo);
        }
        return sale;
    }

    // ============================================================
    // 内部辅助
    // ============================================================

    /**
     * 生成 sale_no: SO-yyyyMMdd-NNNN(NNNN = 当日序号,从 1 开始 4 位补零)。
     */
    private String generateSaleNo(Long companyId, LocalDate today) {
        String dateStr = today.format(DATE_FORMAT);
        Integer todayCount = saleMapper.countTodaySales(companyId, today);
        int seq = (todayCount == null ? 0 : todayCount) + 1;
        return String.format("SO-%s-%04d", dateStr, seq);
    }

    private ErpSaleStatus parseStatus(String code) {
        if (code == null || code.isBlank()) {
            throw new ServiceException("销售单状态为空");
        }
        try {
            return ErpSaleStatus.of(code);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("未知销售单状态: " + code);
        }
    }
}
