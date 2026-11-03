package com.ruoyi.opc.agent.feign;

import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-agent-hub → opc-insight 的内部调用（工作流把产出物落进业务表）。
 *
 * <p><b>为什么请求体是 {@code Map<String,Object>} 而不是 insight 的 DTO</b>：
 * opc-agent-hub 的 pom 里<b>没有</b> opc-insight 的依赖 —— 为了一个 VO 把
 * opc-insight 引进 agent-hub 会形成依赖环（insight 那边又要调 agent-hub）。
 * 用 Map 接住，Jackson 会把 body 解成 {@code LinkedHashMap}。</p>
 *
 * <p><b>为什么返回值是 {@code R<Long>} 而不是 {@code R<Map<String,Object>>}</b>（裁定 R-98）：
 * {@code R} 的 {@code data} 声明类型是泛型 —— 声明成 {@code Map} 时，
 * Jackson 拿到一个数字（落表的行 id）会抛 {@code MismatchedInputException}，
 * 一次成功的落表会被当成失败。</p>
 *
 * <p><b>失败时响应体仍是 {@code R<Long>} 形状</b>（{@code code}/{@code msg}/{@code data}），
 * 但 {@code code} 不是 200 → <b>必须查 {@code body.code}，不能只看 HTTP 码</b>
 * （CLAUDE.md 坑 #8：这个仓库的业务错误全是 HTTP 200 + {@code body.code=500}）。</p>
 *
 * <p>端点是方法级 {@code @InnerAuth}，靠 {@code FeignRequestInterceptor}
 * 无条件注入的 {@code from-source: inner} 请求头放行 —— 这里不用手写。</p>
 *
 * @author OAC
 */
@FeignClient(contextId = "remoteInsightService", value = ServiceNameConstants.INSIGHT_SERVICE)
public interface RemoteInsightService {

    /**
     * 工作流写入日报（upsert）。
     *
     * @param body 见 {@code DailyReportUpsertRequest}：
     *             {@code companyId / period / summaryMd / adviceMd / kpiJson /
     *             llmUsed / reportStatus / sourceWorkflowRunId}
     *             —— <b>没有 {@code reportSource}</b>：来源由接收侧按端点身份写死，
     *             不由调用方声明。
     * @return 落库后的行 id
     */
    @PostMapping("/opc/insight/internal/daily/upsert")
    R<Long> upsertDailyReport(@RequestBody Map<String, Object> body);

}
