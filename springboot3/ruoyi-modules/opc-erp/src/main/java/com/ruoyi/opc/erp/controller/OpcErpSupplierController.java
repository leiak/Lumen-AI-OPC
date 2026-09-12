package com.ruoyi.opc.erp.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.erp.domain.OpcErpSupplier;
import com.ruoyi.opc.erp.dto.OpcErpSupplierDto;
import com.ruoyi.opc.erp.service.IOpcErpSupplierService;
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

/**
 * ERP 供应商 Controller。
 *
 * <p>5 endpoints:
 * <ul>
 *   <li>POST   /                创建</li>
 *   <li>PUT    /{id}            更新</li>
 *   <li>DELETE /{id}            删除</li>
 *   <li>GET    /list            列表(可选 level)</li>
 *   <li>GET    /{id}            详情</li>
 * </ul>
 */
@RestController
@RequestMapping("/opc/erp/supplier")
@RequiredArgsConstructor
public class OpcErpSupplierController {

    private final IOpcErpSupplierService supplierService;

    @PostMapping
    public R<Long> create(@RequestBody OpcErpSupplierDto dto) {
        return R.ok(supplierService.create(getCurrentUserId(), dto));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody OpcErpSupplierDto dto) {
        Long companyId = dto.getCompanyId();
        if (companyId == null) {
            return R.fail("companyId 不能为空");
        }
        supplierService.update(id, companyId, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id, @RequestParam Long companyId) {
        supplierService.delete(id, companyId);
        return R.ok();
    }

    @GetMapping("/list")
    public R<List<OpcErpSupplier>> list(@RequestParam Long companyId,
                                        @RequestParam(required = false) String level) {
        return R.ok(supplierService.list(companyId, level, 0, Integer.MAX_VALUE));
    }

    @GetMapping("/{id}")
    public R<OpcErpSupplier> detail(@PathVariable Long id, @RequestParam Long companyId) {
        return R.ok(supplierService.detail(id, companyId));
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