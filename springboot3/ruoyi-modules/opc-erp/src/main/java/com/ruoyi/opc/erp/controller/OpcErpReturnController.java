package com.ruoyi.opc.erp.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.erp.domain.OpcErpReturn;
import com.ruoyi.opc.erp.dto.OpcErpReturnDto;
import com.ruoyi.opc.erp.service.IOpcErpReturnService;
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
 * ERP 退货单 Controller。
 *
 * <p>5 endpoints:
 * <ul>
 *   <li>POST /                  创建草稿</li>
 *   <li>POST /{id}/confirm      确认(双路径: 销退入库 / 采退出库)</li>
 *   <li>POST /{id}/cancel       取消(仅 DRAFT)</li>
 *   <li>GET  /list              列表(可选 returnType + status)</li>
 *   <li>GET  /{id}              详情</li>
 * </ul>
 */
@RestController
@RequestMapping("/opc/erp/return")
@RequiredArgsConstructor
public class OpcErpReturnController {

    private final IOpcErpReturnService returnService;

    @PostMapping
    public R<Long> create(@RequestBody OpcErpReturnDto dto) {
        if (dto.getCompanyId() == null) {
            return R.fail("companyId 不能为空");
        }
        return R.ok(returnService.create(dto.getCompanyId(), getCurrentUserId(), dto));
    }

    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id, @RequestParam Long companyId) {
        returnService.confirm(id, companyId, getCurrentUserId());
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestParam Long companyId) {
        returnService.cancel(id, companyId, getCurrentUserId());
        return R.ok();
    }

    @GetMapping("/list")
    public R<List<OpcErpReturn>> list(@RequestParam Long companyId,
                                      @RequestParam(required = false) String returnType,
                                      @RequestParam(required = false) String status) {
        return R.ok(returnService.list(companyId, returnType, status, 0, Integer.MAX_VALUE));
    }

    @GetMapping("/{id}")
    public R<OpcErpReturn> detail(@PathVariable Long id, @RequestParam Long companyId) {
        return R.ok(returnService.detail(id, companyId));
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