package com.ruoyi.opc.erp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.utils.SnowflakeIdGenerator;
import com.ruoyi.opc.erp.domain.OpcErpProduct;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.dto.OpcErpProductDto;
import com.ruoyi.opc.erp.enums.ErpProductStatus;
import com.ruoyi.opc.erp.mapper.OpcErpProductMapper;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import com.ruoyi.opc.erp.service.IOpcErpProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ERP 商品服务实现
 *
 * <p>核心逻辑: specAttrs JSON 解析 → 笛卡尔积 → 批量插入 SKU。
 *
 * @author OAC
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpcErpProductServiceImpl implements IOpcErpProductService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_THRESHOLD = 10;

    private final OpcErpProductMapper productMapper;
    private final OpcErpProductSkuMapper skuMapper;

    // ============================================================
    // create
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OpcErpProductDto dto, Long operatorId) {
        // 1) 必填校验
        if (dto.getCompanyId() == null) {
            throw new ServiceException("companyId 不能为空");
        }
        if (dto.getSkuRoot() == null || dto.getSkuRoot().isBlank()) {
            throw new ServiceException("skuRoot 不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new ServiceException("name 不能为空");
        }
        if (dto.getSpecAttrs() == null || dto.getSpecAttrs().isBlank()) {
            throw new ServiceException("specAttrs 不能为空,至少 1 个规格项");
        }

        // 2) 解析 specAttrs → 笛卡尔积
        List<Map<String, Object>> specs = parseSpecAttrs(dto.getSpecAttrs());
        if (specs.isEmpty()) {
            throw new ServiceException("specAttrs 至少包含 1 个规格项(name+values[])");
        }
        List<List<String>> valueMatrix = extractValueMatrix(specs);
        List<Map<String, String>> combinations = cartesianProduct(specs, valueMatrix);
        if (combinations.isEmpty()) {
            throw new ServiceException("specAttrs 解析后无有效 SKU 组合");
        }

        // 3) 创建商品根记录
        Long productId = SnowflakeIdGenerator.nextId();
        String status = (dto.getStatus() == null || dto.getStatus().isBlank())
                ? ErpProductStatus.ACTIVE.getCode()
                : dto.getStatus();
        OpcErpProduct product = OpcErpProduct.builder()
                .id(productId)
                .companyId(dto.getCompanyId())
                .skuRoot(dto.getSkuRoot())
                .name(dto.getName())
                .category(dto.getCategory())
                .brand(dto.getBrand())
                .unit(dto.getUnit())
                .description(dto.getDescription())
                .specAttrs(dto.getSpecAttrs())
                .status(status)
                .createdBy(operatorId)
                .build();
        try {
            productMapper.insert(product);
        } catch (DuplicateKeyException e) {
            // uk_company_sku_root 唯一索引冲突
            log.warn("商品创建失败:companyId={} skuRoot={} 已存在", dto.getCompanyId(), dto.getSkuRoot());
            throw new ServiceException("同公司下 skuRoot 已存在: " + dto.getSkuRoot());
        }

        // 4) 生成 SKU 行
        int threshold = DEFAULT_THRESHOLD;
        BigDecimal defaultPrice = BigDecimal.ZERO;
        BigDecimal defaultCost = BigDecimal.ZERO;
        for (OpcErpProductSku existing : skuMapper.selectByProductId(dto.getCompanyId(), productId)) {
            // (留空以便未来扩展: 预填价格/阈值)
        }
        // 注: 默认阈值/价格从 DTO 取不到 (OpcErpProductDto 不含),使用 10/0 占位;
        //     后续 Task 7 InventoryService 入库时会更新 stock/threshold/price
        for (Map<String, String> combo : combinations) {
            OpcErpProductSku sku = OpcErpProductSku.builder()
                    .id(SnowflakeIdGenerator.nextId())
                    .companyId(dto.getCompanyId())
                    .productId(productId)
                    .skuCode(buildSkuCode(dto.getSkuRoot(), combo))
                    .specJson(toJson(combo))
                    .price(defaultPrice)
                    .cost(defaultCost)
                    .stock(0)
                    .threshold(threshold)
                    .version(0L)
                    .status(ErpProductStatus.ACTIVE.getCode())
                    .build();
            skuMapper.insert(sku);
        }

        log.info("创建商品 id={} skuRoot={} 生成 SKU 数={} operator={}",
                productId, dto.getSkuRoot(), combinations.size(), operatorId);
        return productId;
    }

    // ============================================================
    // update
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Long id, Long companyId, OpcErpProductDto dto) {
        OpcErpProduct existing = validateAndGet(id, companyId);
        // 仅更新基础字段,不动 specAttrs (重生 SKU 会丢库存)
        if (dto.getSkuRoot() != null) existing.setSkuRoot(dto.getSkuRoot());
        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getCategory() != null) existing.setCategory(dto.getCategory());
        if (dto.getBrand() != null) existing.setBrand(dto.getBrand());
        if (dto.getUnit() != null) existing.setUnit(dto.getUnit());
        if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
        if (dto.getSpecAttrs() != null) existing.setSpecAttrs(dto.getSpecAttrs());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        return productMapper.updateById(existing);
    }

    // ============================================================
    // delete
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long companyId) {
        OpcErpProduct existing = validateAndGet(id, companyId);
        List<OpcErpProductSku> skus = skuMapper.selectByProductId(companyId, id);
        for (OpcErpProductSku sku : skus) {
            if (sku.getStock() != null && sku.getStock() > 0) {
                throw new ServiceException(
                        "存在库存的 SKU 无法删除 (sku=" + sku.getSkuCode() + ", stock=" + sku.getStock() + ")");
            }
        }
        // 先删 SKU(外键反向)再删 product
        for (OpcErpProductSku sku : skus) {
            skuMapper.deleteById(sku.getId(), companyId);
        }
        productMapper.deleteById(id, companyId);
        log.info("删除商品 id={} skuRoot={} (含 {} 个 SKU)", id, existing.getSkuRoot(), skus.size());
    }

    // ============================================================
    // detail / list
    // ============================================================

    @Override
    public OpcErpProduct detail(Long id, Long companyId) {
        return validateAndGet(id, companyId);
    }

    @Override
    public List<OpcErpProduct> list(Long companyId, String category, Integer offset, Integer limit) {
        if (category == null || category.isBlank()) {
            return productMapper.selectList(companyId, null, null, offset, limit);
        }
        return productMapper.selectListByCategory(companyId, category, offset, limit);
    }

    // ============================================================
    // autoCategory (占位 — Task 8 接 opc-ai-core)
    // ============================================================

    @Override
    public String autoCategory(String productName, String description) {
        if (productName == null || productName.isBlank()) {
            return null;
        }
        // TODO Task 8: 通过 opc-ai-core Feign 调用 LLM 自动分类
        // 当前占位: 简单启发式判断(避免抛异常)
        String cat;
        if (productName.contains("手机") || productName.contains("电脑") || productName.contains("数码")) {
            cat = "电子产品";
        } else if (productName.contains("服") || productName.contains("裤") || productName.contains("鞋")
                || productName.contains("恤") || productName.contains("衫") || productName.contains("裙")
                || productName.contains("帽")) {
            cat = "服装鞋帽";
        } else if (productName.contains("食") || productName.contains("饮")
                || productName.contains("水果") || productName.contains("蔬菜")
                || productName.contains("零食") || productName.contains("茶")) {
            cat = "食品饮料";
        } else {
            cat = "通用商品";
        }
        log.warn("autoCategory 占位实现 name={} → {}", productName, cat);
        return cat;
    }

    // ============================================================
    // 内部辅助
    // ============================================================

    private OpcErpProduct validateAndGet(Long id, Long companyId) {
        OpcErpProduct product = productMapper.selectById(id, companyId);
        if (product == null) {
            throw new ServiceException("商品不存在或无权访问 id=" + id);
        }
        return product;
    }

    /**
     * 解析 specAttrs JSON: [{"name":"颜色","values":["黑","白"]}, ...]
     */
    private List<Map<String, Object>> parseSpecAttrs(String specAttrsJson) {
        try {
            List<Map<String, Object>> list = JSON.readValue(
                    specAttrsJson, new TypeReference<List<Map<String, Object>>>() {});
            return list == null ? Collections.emptyList() : list;
        } catch (JsonProcessingException e) {
            throw new ServiceException("specAttrs JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 从 spec 列表里提取每个 name 的 values 数组(空列表视为非法)。
     */
    @SuppressWarnings("unchecked")
    private List<List<String>> extractValueMatrix(List<Map<String, Object>> specs) {
        List<List<String>> matrix = new ArrayList<>();
        for (Map<String, Object> spec : specs) {
            Object raw = spec.get("values");
            if (!(raw instanceof List)) {
                throw new ServiceException("specAttrs.values 必须为数组");
            }
            List<String> values = ((List<Object>) raw).stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            if (values.isEmpty()) {
                throw new ServiceException("specAttrs.values 不能为空数组");
            }
            matrix.add(values);
        }
        return matrix;
    }

    /**
     * 笛卡尔积递归生成 — 例:
     * <pre>
     *   specs  = [{颜色}, {尺码}]
     *   matrix = [["黑","白"], ["M","L"]]
     *   → [{颜色:黑, 尺码:M}, {颜色:黑, 尺码:L}, {颜色:白, 尺码:M}, {颜色:白, 尺码:L}]
     * </pre>
     */
    private List<Map<String, String>> cartesianProduct(List<Map<String, Object>> specs,
                                                       List<List<String>> valueMatrix) {
        List<Map<String, String>> results = new ArrayList<>();
        generateSkus(specs, valueMatrix, 0, new LinkedHashMap<>(), results);
        return results;
    }

    @SuppressWarnings("unchecked")
    private void generateSkus(List<Map<String, Object>> specs,
                              List<List<String>> valueMatrix,
                              int depth,
                              Map<String, String> current,
                              List<Map<String, String>> results) {
        if (depth == specs.size()) {
            results.add(new LinkedHashMap<>(current));
            return;
        }
        Map<String, Object> spec = specs.get(depth);
        String name = String.valueOf(spec.get("name"));
        List<String> values = valueMatrix.get(depth);
        for (String v : values) {
            current.put(name, v);
            generateSkus(specs, valueMatrix, depth + 1, current, results);
        }
        current.remove(name);
    }

    /**
     * 拼 SKU code: skuRoot + "-" + value1 + "-" + value2 ...
     */
    private String buildSkuCode(String skuRoot, Map<String, String> combo) {
        StringBuilder sb = new StringBuilder(skuRoot);
        for (String v : combo.values()) {
            sb.append('-').append(v);
        }
        return sb.toString();
    }

    private String toJson(Map<String, String> map) {
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new ServiceException("spec JSON 序列化失败: " + e.getMessage());
        }
    }
}