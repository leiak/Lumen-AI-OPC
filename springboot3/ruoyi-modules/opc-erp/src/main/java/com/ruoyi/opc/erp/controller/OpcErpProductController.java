package com.ruoyi.opc.erp.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.erp.domain.OpcErpProduct;
import com.ruoyi.opc.erp.domain.OpcErpProductSku;
import com.ruoyi.opc.erp.dto.OpcErpProductDto;
import com.ruoyi.opc.erp.mapper.OpcErpProductSkuMapper;
import com.ruoyi.opc.erp.service.IOpcErpProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * ERP 商品 Controller
 *
 * <p>7 endpoints: create / list / detail / update / delete / auto-category / product-sku/list
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/erp/product")
@RequiredArgsConstructor
public class OpcErpProductController {

    private final IOpcErpProductService productService;
    private final OpcErpProductSkuMapper skuMapper;

    @PostMapping
    public R<Long> create(@RequestBody OpcErpProductDto dto) {
        return R.ok(productService.create(dto, getCurrentUserId()));
    }

    @GetMapping("/list")
    public R<List<OpcErpProduct>> list(@RequestParam Long companyId,
                                        @RequestParam(required = false) String category) {
        return R.ok(productService.list(companyId, category, 0, Integer.MAX_VALUE));
    }

    @GetMapping("/{id}")
    public R<OpcErpProduct> detail(@PathVariable Long id, @RequestParam Long companyId) {
        return R.ok(productService.detail(id, companyId));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody OpcErpProductDto dto) {
        productService.update(id, dto.getCompanyId(), dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestParam Long companyId) {
        productService.delete(id, companyId);
        return R.ok();
    }

    @PostMapping("/auto-category")
    public R<String> autoCategory(@RequestBody Map<String, String> req) {
        String name = req.get("name");
        String desc = req.get("description");
        return R.ok(productService.autoCategory(name, desc));
    }

    @GetMapping("/product-sku/list")
    public R<List<OpcErpProductSku>> skuList(@RequestParam Long companyId,
                                              @RequestParam(required = false) Long productId) {
        return R.ok(skuMapper.selectByProductId(companyId, productId));
    }

    /**
     * 获取当前登录用户 ID;测试环境无 SecurityContext 时回退 0L。
     */
    private Long getCurrentUserId() {
        try {
            return SecurityUtils.getUserId();
        } catch (Exception e) {
            return 0L;
        }
    }
}