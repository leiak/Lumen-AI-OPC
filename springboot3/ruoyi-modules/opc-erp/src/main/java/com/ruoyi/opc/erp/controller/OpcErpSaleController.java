package com.ruoyi.opc.erp.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.erp.domain.OpcErpSale;
import com.ruoyi.opc.erp.dto.OpcErpSaleDto;
import com.ruoyi.opc.erp.service.IOpcErpSaleService;
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
 * ERP 销售 Controller。
 *
 * <p>6 endpoints:
 * <ul>
 *   <li>POST /                  创建草稿</li>
 *   <li>POST /{id}/confirm      确认（FIFO 扣减 + 出库 + 完成）</li>
 *   <li>POST /{id}/cancel       取消（仅 DRAFT）</li>
 *   <li>GET  /list              列表</li>
 *   <li>GET  /{id}              详情</li>
 *   <li>GET  /no/{saleNo}       按单号查询</li>
 * </ul>
 */
@RestController
@RequestMapping("/opc/erp/sale")
@RequiredArgsConstructor
public class OpcErpSaleController {

    private final IOpcErpSaleService saleService;

    @PostMapping
    public R<Long> create(@RequestBody OpcErpSaleDto dto) {
        Long operatorId = getCurrentUserId();
        return R.ok(saleService.create(dto.getCompanyId(), operatorId, dto));
    }

    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @RequestParam Long companyId) {
        saleService.confirm(id, companyId, getCurrentUserId());
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam Long companyId) {
        saleService.cancel(id, companyId, getCurrentUserId());
        return R.ok();
    }

    @GetMapping("/list")
    public R<List<OpcErpSale>> list(@RequestParam Long companyId,
                                    @RequestParam(required = false) String status) {
        return R.ok(saleService.list(companyId, status, 0, Integer.MAX_VALUE));
    }

    @GetMapping("/{id}")
    public R<OpcErpSale> detail(@PathVariable Long id, @RequestParam Long companyId) {
        return R.ok(saleService.detail(id, companyId));
    }

    @GetMapping("/no/{saleNo}")
    public R<OpcErpSale> detailBySaleNo(@PathVariable String saleNo,
                                        @RequestParam Long companyId) {
        return R.ok(saleService.detailBySaleNo(companyId, saleNo));
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
