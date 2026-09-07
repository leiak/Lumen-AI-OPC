package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.service.IOpcFinanceBankFlowService;
import com.ruoyi.opc.finance.service.IOpcFinanceVoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OPC 财务 Agent API
 *
 * @author OAC
 */
@Tag(name = "OPC 财务 Agent")
@RestController
@RequestMapping("/opc/finance")
@RequiredArgsConstructor
public class OpcFinanceController extends BaseController {

    private final IOpcFinanceVoucherService voucherService;
    private final IOpcFinanceBankFlowService bankFlowService;

    @Operation(summary = "凭证列表")
    @GetMapping("/vouchers")
    public AjaxResult listVouchers(@RequestParam Long companyId,
                                    @RequestParam(required = false) String period,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "20") Integer limit) {
        return success(voucherService.listByCompany(companyId, period, status, limit));
    }

    @Operation(summary = "凭证详情")
    @GetMapping("/voucher/{id}")
    public AjaxResult voucherDetail(@PathVariable Long id) {
        return success(voucherService.getById(id));
    }

    @Operation(summary = "创建凭证")
    @PostMapping("/voucher")
    public AjaxResult createVoucher(@RequestBody OpcFinanceVoucher voucher) {
        Long id = voucherService.create(voucher);
        Map<String, Object> data = new HashMap<>();
        data.put("voucherId", id);
        return success(data);
    }

    @Operation(summary = "更新凭证")
    @PutMapping("/voucher")
    public AjaxResult updateVoucher(@RequestBody OpcFinanceVoucher voucher) {
        return success(voucherService.update(voucher) > 0);
    }

    @Operation(summary = "审核通过")
    @PostMapping("/voucher/{id}/review-pass")
    public AjaxResult reviewPass(@PathVariable Long id) {
        return success(voucherService.reviewPass(id, SecurityUtils.getUsername()) > 0);
    }

    @Operation(summary = "审核拒绝")
    @PostMapping("/voucher/{id}/review-reject")
    public AjaxResult reviewReject(@PathVariable Long id, @RequestParam(required = false) String opinion) {
        return success(voucherService.reviewReject(id, SecurityUtils.getUsername(), opinion) > 0);
    }

    @Operation(summary = "入账")
    @PostMapping("/voucher/{id}/post")
    public AjaxResult post(@PathVariable Long id) {
        return success(voucherService.post(id, SecurityUtils.getUsername()) > 0);
    }

    @Operation(summary = "上传银行流水（批量）")
    @PostMapping("/flows/upload")
    public AjaxResult uploadFlows(@RequestBody List<OpcFinanceBankFlow> flows) {
        int n = bankFlowService.uploadBatch(flows, SecurityUtils.getUsername());
        return success(n);
    }

    @Operation(summary = "待处理流水")
    @GetMapping("/flows/pending")
    public AjaxResult pendingFlows(@RequestParam Long companyId,
                                    @RequestParam(defaultValue = "20") Integer limit) {
        return success(bankFlowService.listPending(companyId, limit));
    }

    @Operation(summary = "AI 提取并生成凭证（异步任务入口）")
    @PostMapping("/flows/extract")
    public AjaxResult extractFlows(@RequestParam Long companyId) {
        // 实际实现：投递到 RabbitMQ -> 调用 AI 中台 AgentRuntime -> 回调写 voucher
        return success(Map.of("taskCode", "EXTRACT-" + System.currentTimeMillis()));
    }

    @Operation(summary = "生成今日日报")
    @PostMapping("/daily-report")
    public AjaxResult dailyReport(@RequestParam Long companyId) {
        // 实际实现：投递异步任务，调 LLM 生成日报
        return success(Map.of("taskCode", "DAILY-" + System.currentTimeMillis(), "date", java.time.LocalDate.now().toString()));
    }

}
