package com.ruoyi.opc.erp.service.impl;

import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.erp.domain.OpcErpSupplier;
import com.ruoyi.opc.erp.dto.OpcErpSupplierDto;
import com.ruoyi.opc.erp.enums.ErpSupplierLevel;
import com.ruoyi.opc.erp.mapper.OpcErpPurchaseMapper;
import com.ruoyi.opc.erp.mapper.OpcErpSupplierMapper;
import com.ruoyi.opc.erp.service.IOpcErpSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ERP 供应商服务实现。
 *
 * <p>核心逻辑:
 * <ul>
 *   <li>create(): SnowflakeId 生成,name+companyId 唯一约束校验</li>
 *   <li>update(): 部分字段更新,允许改 contact/phone/email/address</li>
 *   <li>delete(): 仅 NORMAL + 无关联采购单可删;PREFERRED/BLOCKED 保护</li>
 *   <li>list(): 可选 level 过滤</li>
 *   <li>getByName(): 返回 null 而非抛异常(便于上层业务判断)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcErpSupplierServiceImpl implements IOpcErpSupplierService {

    private final OpcErpSupplierMapper supplierMapper;
    private final OpcErpPurchaseMapper purchaseMapper;

    // ============================================================
    // create
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long operatorId, OpcErpSupplierDto dto) {
        if (operatorId == null) {
            throw new ServiceException("operatorId 不能为空");
        }
        if (dto == null) {
            throw new ServiceException("供应商 DTO 不能为空");
        }
        if (dto.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ServiceException("供应商名称不能为空");
        }

        // 查重
        OpcErpSupplier existing = supplierMapper.selectByName(dto.getCompanyId(), dto.getName());
        if (existing != null) {
            throw new ServiceException(
                    "供应商名称已存在(同一公司内唯一) name=" + dto.getName());
        }

        // 校验 level 枚举
        String level = dto.getLevel() == null || dto.getLevel().isBlank()
                ? ErpSupplierLevel.NORMAL.getCode()
                : dto.getLevel();
        try {
            ErpSupplierLevel.of(level);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("未知 supplier level: " + level);
        }

        Long id = SnowflakeIdGenerator.nextId();
        OpcErpSupplier supplier = OpcErpSupplier.builder()
                .id(id)
                .companyId(dto.getCompanyId())
                .name(dto.getName())
                .contact(dto.getContact())
                .phone(dto.getPhone())
                .email(dto.getEmail())
                .address(dto.getAddress())
                .level(level)
                .status(dto.getStatus() == null || dto.getStatus().isBlank()
                        ? "ACTIVE"
                        : dto.getStatus())
                .createdBy(operatorId)
                .build();
        supplierMapper.insert(supplier);

        log.info("创建供应商 id={} companyId={} name={} level={} operator={}",
                id, dto.getCompanyId(), dto.getName(), level, operatorId);
        return id;
    }

    // ============================================================
    // update
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, Long companyId, OpcErpSupplierDto dto) {
        if (id == null || companyId == null) {
            throw new ServiceException("id/companyId 不能为空");
        }
        if (dto == null) {
            throw new ServiceException("供应商 DTO 不能为空");
        }

        OpcErpSupplier existing = supplierMapper.selectById(id, companyId);
        if (existing == null) {
            throw new ServiceException("供应商不存在或无权访问 id=" + id);
        }

        // 部分字段更新(只允许更新联系信息,不改 name/companyId)
        OpcErpSupplier update = OpcErpSupplier.builder()
                .id(id)
                .companyId(companyId)
                .contact(dto.getContact() != null ? dto.getContact() : existing.getContact())
                .phone(dto.getPhone() != null ? dto.getPhone() : existing.getPhone())
                .email(dto.getEmail() != null ? dto.getEmail() : existing.getEmail())
                .address(dto.getAddress() != null ? dto.getAddress() : existing.getAddress())
                .level(dto.getLevel() != null && !dto.getLevel().isBlank()
                        ? dto.getLevel()
                        : existing.getLevel())
                .status(dto.getStatus() != null && !dto.getStatus().isBlank()
                        ? dto.getStatus()
                        : existing.getStatus())
                .build();

        // 校验新 level 合法性
        if (update.getLevel() != null) {
            try {
                ErpSupplierLevel.of(update.getLevel());
            } catch (IllegalArgumentException e) {
                throw new ServiceException("未知 supplier level: " + update.getLevel());
            }
        }

        int affected = supplierMapper.updateById(update);
        log.info("更新供应商 id={} affected={}", id, affected);
        return affected;
    }

    // ============================================================
    // delete
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long companyId) {
        if (id == null || companyId == null) {
            throw new ServiceException("id/companyId 不能为空");
        }

        OpcErpSupplier existing = supplierMapper.selectById(id, companyId);
        if (existing == null) {
            throw new ServiceException("供应商不存在或无权访问 id=" + id);
        }

        // 1) PREFERRED/BLOCKED 不可删
        String level = existing.getLevel();
        if (level != null && !ErpSupplierLevel.NORMAL.getCode().equals(level)) {
            throw new ServiceException(
                    "供应商不可删除: level=" + level + "(仅 NORMAL 可删) id=" + id);
        }

        // 2) 有关联采购单不可删
        Integer purchaseCount = purchaseMapper.countBySupplier(companyId, id);
        if (purchaseCount != null && purchaseCount > 0) {
            throw new ServiceException(
                    "供应商不可删除: 已存在关联采购单 " + purchaseCount + " 条 id=" + id);
        }

        int affected = supplierMapper.deleteById(id, companyId);
        if (affected == 0) {
            throw new ServiceException("供应商删除失败 id=" + id);
        }
        log.info("删除供应商 id={} name={} level={}", id, existing.getName(), level);
    }

    // ============================================================
    // detail / list / getByName
    // ============================================================

    @Override
    public OpcErpSupplier detail(Long id, Long companyId) {
        if (id == null || companyId == null) {
            throw new ServiceException("id/companyId 不能为空");
        }
        OpcErpSupplier supplier = supplierMapper.selectById(id, companyId);
        if (supplier == null) {
            throw new ServiceException("供应商不存在或无权访问 id=" + id);
        }
        return supplier;
    }

    @Override
    public List<OpcErpSupplier> list(Long companyId, String level, Integer offset, Integer limit) {
        if (companyId == null) {
            throw new ServiceException("companyId 不能为空");
        }
        int off = offset == null ? 0 : offset;
        int lim = limit == null || limit <= 0 ? Integer.MAX_VALUE : limit;
        String levelFilter = (level == null || level.isBlank()) ? null : level;
        return supplierMapper.selectList(companyId, levelFilter, null, off, lim);
    }

    @Override
    public OpcErpSupplier getByName(Long companyId, String name) {
        if (companyId == null || name == null || name.isBlank()) {
            return null;
        }
        return supplierMapper.selectByName(companyId, name);
    }
}