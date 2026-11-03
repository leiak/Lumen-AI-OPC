package com.ruoyi.opc.insight.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.opc.common.exception.OpcException;
import com.ruoyi.opc.insight.service.IDailyReportService;
import com.ruoyi.opc.insight.vo.DailyReportUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * INSIGHT 内部端点 —— 工作流把产出物落进业务表。
 *
 * <p><b>为什么要单独一个 controller</b>：{@link DailyReportController} 的其它端点靠
 * {@code SecurityUtils.getCompanyId()} 取租户，而 {@code @InnerAuth} 调用<b>没有登录态</b>，
 * companyId 只能从 body 里显式取 —— 两者混在一起会写出一堆「有的端点有登录态、有的没有」
 * 的分支。分开写更清楚，也更容易看出哪个端点能被外部直接调。</p>
 *
 * <p><b>为什么 {@code @InnerAuth} 标在方法上而不是类上</b>（裁定 R-88）：
 * {@code CompanyIdBodyAdvice#supports} 查的是<b>方法上</b>的 {@code @InnerAuth}，
 * 标在类上就查不到 → 它会认为这是一个普通端点 → body 里的 {@code companyId}
 * 会被 {@code tenantGuard.resolveAllowed(...)} 改写 —— 而内部调用没有登录态，
 * 改写后的 companyId 可能是别的公司。所以这条注解的位置是硬约束。</p>
 *
 * <p><b>为什么响应体永远是 {@code R<T>}</b>：{@code ruoyi-common-security} 的
 * {@code GlobalExceptionHandler} 会把所有异常转成 {@code AjaxResult}（HTTP 200 +
 * {@code body.code}，见 CLAUDE.md 坑 #8）。{@code AjaxResult} 和 {@code R} 的
 * JSON 形状一样（{@code code}/{@code msg}/{@code data}），但<b>类型</b>不一样 ——
 * Feign 侧声明 {@code R<Map<String,Object>>} 时 Jackson 拿到一个数字会抛
 * {@code MismatchedInputException}。这里用 controller 级 {@code @ExceptionHandler}
 * 把 {@code OpcException} 收成 {@code R<Void>}，并带上业务错误码（400/409）
 * 而不是一律 500。</p>
 *
 * @author OAC
 */
@RestController
@RequestMapping("/opc/insight/internal")
@RequiredArgsConstructor
public class InsightInternalController {

    private final IDailyReportService dailyReportService;

    /**
     * 工作流写入日报（upsert）。
     *
     * @param req 工作流产出的日报内容（companyId 必填，reportStatus 必填）
     * @return 落库后的行 id
     */
    @InnerAuth
    @PostMapping("/daily/upsert")
    public R<Long> upsert(@RequestBody DailyReportUpsertRequest req) {
        return R.ok(dailyReportService.upsertFromWorkflow(req));
    }

    /**
     * 错误体永远是 {@code R<T>} —— Feign 侧声明的是 {@code R<Long>}，
     * 解析 {@code AjaxResult} 会炸（data 的声明类型对不上）。
     */
    @ExceptionHandler(OpcException.class)
    public R<Void> handleOpcException(OpcException e) {
        return R.fail(e.getCode() == null ? 500 : e.getCode().intValue(), e.getMessage());
    }
}
