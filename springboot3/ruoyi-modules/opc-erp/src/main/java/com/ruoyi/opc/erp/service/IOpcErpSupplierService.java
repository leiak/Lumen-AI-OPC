package com.ruoyi.opc.erp.service;

import com.ruoyi.opc.erp.domain.OpcErpSupplier;
import com.ruoyi.opc.erp.dto.OpcErpSupplierDto;

import java.util.List;

/**
 * ERP 供应商服务接口。
 *
 * <p>负责供应商的 CRUD、级别过滤、按公司维度隔离。
 * 删除仅允许:无关联采购单 + level=NORMAL。</p>
 */
public interface IOpcErpSupplierService {

    /**
     * 创建供应商(name + companyId 唯一约束)。
     *
     * @return 新供应商 ID
     */
    Long create(Long operatorId, OpcErpSupplierDto dto);

    /**
     * 更新供应商信息。
     */
    int update(Long id, Long companyId, OpcErpSupplierDto dto);

    /**
     * 删除供应商(level 必须为 NORMAL,且无关联采购单)。
     */
    void delete(Long id, Long companyId);

    /**
     * 查询详情。
     */
    OpcErpSupplier detail(Long id, Long companyId);

    /**
     * 按公司列出(可选 level 过滤)。
     */
    List<OpcErpSupplier> list(Long companyId, String level, Integer offset, Integer limit);

    /**
     * 按 (companyId, name) 查询,找不到返回 null。
     */
    OpcErpSupplier getByName(Long companyId, String name);
}