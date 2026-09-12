package com.ruoyi.opc.erp.service;

import com.ruoyi.opc.erp.domain.OpcErpProduct;
import com.ruoyi.opc.erp.dto.OpcErpProductDto;

import java.util.List;

/**
 * ERP 商品（SKU 规格 root）服务接口
 *
 * <p>负责商品的 CRUD + 自动生成 SKU（笛卡尔积）。
 * <p>SKU 在 {@link #create} 时一次性生成,后续 {@link #update} 不再重生 SKU(以免丢失库存数据)。
 */
public interface IOpcErpProductService {

    /**
     * 创建商品 + 自动根据 specAttrs JSON 生成所有 SKU 行(Cartesian product)。
     *
     * @param dto        入参(必填: companyId/skuRoot/name/specAttrs)
     * @param operatorId 当前登录用户 ID(写入 created_by)
     * @return 新商品 ID(雪花算法)
     */
    Long create(OpcErpProductDto dto, Long operatorId);

    /**
     * 更新商品基础字段(不会重新生成 SKU,保留历史库存)。
     *
     * @return 受影响行数(0 或 1)
     */
    int update(Long id, Long companyId, OpcErpProductDto dto);

    /**
     * 删除商品 + 关联 SKU 行(仅当所有 SKU.stock == 0 时允许)。
     */
    void delete(Long id, Long companyId);

    /**
     * 查询商品详情(包含 specAttrs JSON)。
     */
    OpcErpProduct detail(Long id, Long companyId);

    /**
     * 分页 + 分类过滤列表(category 可空)。
     */
    List<OpcErpProduct> list(Long companyId, String category, Integer offset, Integer limit);

    /**
     * 调用 opc-ai-core LLM 自动分类商品(category 字符串)。
     *
     * <p>Task 3 阶段为占位实现: 返回 null,后续 Task 8 接 HttpLlmClient。
     *
     * @return 分类字符串(如 "服装" / "电子产品"),失败返回 null
     */
    String autoCategory(String productName, String description);
}