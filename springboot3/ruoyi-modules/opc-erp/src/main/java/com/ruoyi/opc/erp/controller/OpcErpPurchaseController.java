package com.ruoyi.opc.erp.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.erp.domain.OpcErpPurchase;
import com.ruoyi.opc.erp.dto.OpcErpPurchaseDto;
import com.ruoyi.opc.erp.service.IOpcErpPurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ERP 采购 Controller
 *
 * <p>6 endpoints:
 * <ul>
 *   <li>POST /               创建草稿</li>
 *   <li>POST /{id}/confirm   确认（自动入库 + 完成）</li>
 *   <li>POST /{id}/cancel    取消（仅 DRAFT）</li>
 *   <li>GET  /list           列表</li>
 *   <li>GET  /{id}           详情</li>
 *   <li>GET  /no/{purchaseNo} 按单号查询</li>
 * </ul>
 */
@RestController
@RequestMapping("/opc/erp/purchase")
@RequiredArgsConstructor
public class OpcErpPurchaseController {

    private final IOpcErpPurchaseService purchaseService;

    @PostMapping
    public R<Long> create(@RequestBody OpcErpPurchaseDto dto) {
        Long operatorId = getCurrentUserId();
        return R.ok(purchaseService.create(dto.getCompanyId(), operatorId, dto));
    }

    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @RequestParam Long companyId) {
        purchaseService.confirm(id, companyId, getCurrentUserId());
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam Long companyId) {
        purchaseService.cancel(id, companyId, getCurrentUserId());
        return R.ok();
    }

    @GetMapping("/list")
    public R<List<OpcErpPurchase>> list(@RequestParam Long companyId,
                                        @RequestParam(required = false) String status) {
        return R.ok(purchaseService.list(companyId, status, 0, Integer.MAX_VALUE));
    }

    @GetMapping("/{id}")
    public R<OpcErpPurchase> detail(@PathVariable Long id, @RequestParam Long companyId) {
        return R.ok(purchaseService.detail(id, companyId));
    }

    @GetMapping("/no/{purchaseNo}")
    public R<OpcErpPurchase> detailByPurchaseNo(@PathVariable String purchaseNo,
                                                 @RequestParam Long companyId) {
        return R.ok(purchaseService.detailByPurchaseNo(companyId, purchaseNo));
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
