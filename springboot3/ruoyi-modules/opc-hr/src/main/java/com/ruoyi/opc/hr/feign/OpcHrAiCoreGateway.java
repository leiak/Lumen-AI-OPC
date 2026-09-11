package com.ruoyi.opc.hr.feign;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.opc.hr.feign.factory.OpcHrAiCoreGatewayFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * opc-hr → opc-ai-core 远程调用（Task 8）。
 *
 * <p>用途：JD 生成、简历解析、候选人评分、候选人对齐重排序（4 个 LLM 场景）。
 * 对端 controller 在 opc-ai-core 模块（9301），走 {@code @InnerAuth}。
 *
 * <p>降级策略：{@link OpcHrAiCoreGatewayFactory} —— LLM 失败应显式感知，
 * 返回 R.fail() 让业务层决定是否走缓存或人工兜底，不静默吞错。
 *
 * @author OAC
 */
@FeignClient(contextId = "opcHrAiCore", name = "opc-ai-core",
        fallbackFactory = OpcHrAiCoreGatewayFactory.class)
public interface OpcHrAiCoreGateway {

    /**
     * 统一 LLM 调用入口（payload 含 messages/temperature/scene 等）。
     *
     * <p>scene 取值：{@code hr_jd_generate} / {@code hr_resume_parse} /
     * {@code hr_candidate_score} / {@code hr_rerank}。
     */
    @PostMapping("/opc/llm/chat")
    R<Map<String, Object>> chat(@RequestBody Map<String, Object> payload);

    /**
     * Embedding 调用（候选人画像 / JD 技能向量化，用于向量召回）。
     *
     * <p>payload: {@code { texts: [...], model: "text-embedding-3-small" }}
     */
    @PostMapping("/opc/llm/embedding")
    R<Map<String, Object>> embedding(@RequestBody Map<String, Object> payload);
}
