package com.ruoyi.opc.finance.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.finance.domain.OpcFinanceBankFlow;
import com.ruoyi.opc.finance.domain.OpcFinanceTaxReport;
import com.ruoyi.opc.finance.domain.OpcFinanceVoucher;
import com.ruoyi.opc.finance.service.IOpcFinanceBankFlowService;
import com.ruoyi.opc.finance.service.IOpcFinanceTaxReportService;
import com.ruoyi.opc.finance.service.IOpcFinanceTokenUsageService;
import com.ruoyi.opc.finance.service.IOpcFinanceVoucherService;
import com.ruoyi.opc.finance.vo.FlowAggVo;
import com.ruoyi.opc.finance.vo.TokenUsageVo;
import com.ruoyi.opc.finance.vo.VoucherAggVo;
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
    private final IOpcFinanceTaxReportService taxReportService;
    private final IOpcFinanceTokenUsageService tokenUsageService;

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
        // W8 修复：override createdBy — 防止客户端伪造身份 (W6 钉死的漏洞)
        voucher.setCreateBy(SecurityUtils.getUsername());
        Long id = voucherService.create(voucher);
        Map<String, Object> data = new HashMap<>();
        data.put("voucherId", id);
        return success(data);
    }

    @Operation(summary = "更新凭证")
    @PutMapping("/voucher")
    public AjaxResult updateVoucher(@RequestBody OpcFinanceVoucher voucher) {
        // W10.3 审计字段：override updateBy — 防止客户端伪造身份（对称 W8.1 createBy 修复）
        voucher.setUpdateBy(SecurityUtils.getUsername());
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

    // ==================== 聚合端点（M4 Task 1，供 opc-insight 经 Feign 拉取） ====================
    // 返回 R<T> 而非 AjaxResult：Feign 侧声明的是 R<VoucherAggVo> 等强类型，便于直接反序列化。
    //
    // C2 (W11 评审修复)：
    //   1. 全部 4 个端点加 @InnerAuth —— 要求请求头 from-source: inner，网关 AuthFilter
    //      会剥掉外部请求的该请求头，因此这些端点不会被公网触达。仅有 opc-insight 等
    //      服务间 Feign 调用（Feign 客户端会带上 from-source: inner）能命中。
    //   2. companyId 仍由调用方显式传入 —— INSIGHT 已在自己的 controller 层
    //      用 SecurityUtils.getCompanyId() 做过越权校验；这里 + InnerAuth 是双保险。
    //
    // C1 (W11 评审修复)：
    //   全局 GlobalExceptionHandler.handleServiceException 把 OpcException 转成 AjaxResult
    //   （继承链 OpcException → ServiceException）。若任由 advice 兜底，Feign 侧声明的
    //   R<VoucherAggVo> 解析会失败 —— 错误体是 AjaxResult 不是 R<T>。
    //   这里用 controller 级 @ExceptionHandler 把 OpcException 转成 R.fail(code, msg)，
    //   让 4 个 agg 端点的响应体永远是 R<T>。

    @Operation(summary = "凭证月度聚合（INSIGHT KPI）")
    @InnerAuth
    @GetMapping("/agg/voucher")
    public R<VoucherAggVo> voucherAgg(@RequestParam Long companyId, @RequestParam String period) {
        return R.ok(voucherService.aggregateByPeriod(companyId, period));
    }

    @Operation(summary = "银行流水月度聚合（INSIGHT KPI）")
    @InnerAuth
    @GetMapping("/agg/flow")
    public R<FlowAggVo> flowAgg(@RequestParam Long companyId, @RequestParam String period) {
        return R.ok(bankFlowService.aggregateByPeriod(companyId, period));
    }

    @Operation(summary = "当期税务报表（INSIGHT KPI，无报表时 data=null）")
    @InnerAuth
    @GetMapping("/agg/tax-report")
    public R<OpcFinanceTaxReport> taxReport(@RequestParam Long companyId, @RequestParam String period) {
        return R.ok(taxReportService.getByCompanyAndPeriod(companyId, period));
    }

    @Operation(summary = "Token 消耗月度聚合（INSIGHT KPI）")
    @InnerAuth
    @GetMapping("/agg/token-usage")
    public R<TokenUsageVo> tokenUsage(@RequestParam Long companyId, @RequestParam String period) {
        return R.ok(tokenUsageService.aggregateByPeriod(companyId, period));
    }

    /**
     * 把 {@link OpcException} 转成 {@link R#fail(int, String)} —— 让 4 个聚合端点的响应体
     * 形状永远是 {@code R<T>}，不会被全局 advice 转成 {@code AjaxResult}。
     * <p>
     * 仅作用于本 controller —— 其他端点（如 voucher CRUD）继续走 advice 返回 AjaxResult，
     * 不破坏现有约定。
     */
    @ExceptionHandler(OpcException.class)
    public R<Void> handleOpcException(OpcException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

}
