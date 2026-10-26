package com.ruoyi.opc.ai.controller;

import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.ai.gateway.llm.TokenMeter;
import com.ruoyi.opc.ai.security.PromptGuard;
import com.ruoyi.system.api.model.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * OPC LLM 网关对外开放 API
 *
 * @author OAC
 */
@Tag(name = "OPC LLM 网关")
@RestController
@RequestMapping("/opc/llm")
@RequiredArgsConstructor
@Slf4j
public class OpcLlmController {

    private final LlmGateway llmGateway;
    /**
     * W48.6: 注入所有 ChatModelProvider，{@code /models} 端点直接列 enabled=true 的 provider。
     * 之前是硬编码 [deepseek-v3, gpt-4o-mini, wenxin-4.0]，新增 MiniMax 时容易漏改。
     */
    private final List<ChatModelProvider> providers;

    private final PromptGuard promptGuard;

    /**
     * 对 {@code role == USER} 的消息做 Prompt 注入检测。
     *
     * <p>这条路径此前<b>完全不过 PromptGuard</b> —— {@code sanitize()} 在全仓库
     * 唯一的调用点是 {@code AgentRuntime:169}，而 AgentRuntime 不在 {@code /chat} 链路上。
     * 也就是说 messages 从 HTTP body 直穿到 provider，任何登录用户都能让模型无视
     * system prompt；而 system prompt 里现在装着每个数字员工的人设。
     *
     * <p><b>为什么只查 USER、不能无差别扫全量：</b>
     * {@code ContentLlmPrompts:89/146} 的防御句里字面写着
     * {@code "'忽略以上'/'忽略所有指示'/'developer mode'/'jailbreak' 等绕过指令"}，
     * 而 {@code 'jailbreak'} 正好命中 {@code INJECTION_PATTERNS} 的
     * {@code (?i)\bjailbreak\b} —— 无差别过滤会把 opc-content 的短视频脚本和
     * 文章生成全打成 403。assistant 消息是模型输出，同样不该拦。
     *
     * <p>残留风险（本次不处理）：{@code role == TOOL} 的内容是工具返回的外部数据，
     * 属于间接注入面；{@code req.tools} 也允许调用方自带任意工具定义。
     * 当前无服务端工具执行路径，故不构成即时 RCE，留待工具能力真正落地时一并收口。
     */
    private void guardUserMessages(List<ChatMessage> messages) {
        if (messages == null) return;
        for (ChatMessage m : messages) {
            if (m != null && m.getRole() == ChatMessage.Role.USER) {
                // sanitize() 命中即抛 OpcException(403)，通过时原样返回。
                // 这里只用它的「校验」语义，不改写内容 ——
                // 改写会无谓地让内容与 LlmCache.buildKey 的入参产生偏离。
                //
                // 跳过 ACTION_ONLY_RULES：/chat 里模型只能吐文本，没有工具、不写库，
                // 业务动作类规则（如 WITHDRAWAL_BYPASS）在这里只会误杀正常财务闲聊。
                // 注入类规则在任何调用方都拦不掉，仍然生效。
                promptGuard.sanitize(m.getContent(), PromptGuard.ACTION_ONLY_RULES);
            }
        }
    }

    @Operation(summary = "统一聊天（带 fallback + 缓存 + 计量）")
    @PostMapping("/chat")
    public R<ChatResponse> chat(@RequestBody ChatRequest req) {
        // 必须在校验通过之后才触达模型：注入内容连 provider 都不该看见
        guardUserMessages(req.messages);
        // 计量的归属（租户 + 用户）必须来自**登录态**，不能来自 body。
        //
        // 旧实现直接 companyId(req.companyId) / userId(req.userId) 透传 body，
        // 而前端两个调用点（llm/chat.vue、agent/instance.vue）都不发这两个字段
        // → company_id 为 null → 落库撞 opc_agent_token_usage.company_id NOT NULL
        // → 异常被 TokenMeter.record 的 catch 吞掉（只打一行 error）
        // → 结果是**每一次** LLM 调用的用量/成本记录都静默丢失，
        //   而 /opc/finance/agg/token-usage 正好读这张表做成本 KPI —— 于是账单一直是空的。
        //
        // body 里的 companyId/userId 现在只作为「无登录上下文」时的兜底（内部调用/定时任务），
        // 有登录上下文时一律以登录态为准：客户端说什么不算数。
        Long companyId = resolveCompanyId(req.companyId);
        Long userId = resolveUserId(req.userId);
        ChatResponse resp = llmGateway.chat(req.messages, ChatModelProvider.ChatOptions.builder()
                .temperature(req.temperature)
                .maxTokens(req.maxTokens)
                .tools(req.tools)
                .toolChoice(req.toolChoice)
                .user(req.user)
                .build(), LlmGateway.ChatContext.builder()
                .companyId(companyId)
                .userId(userId)
                .instanceId(req.instanceId)
                .taskId(req.taskId)
                .scene(req.scene)
                .build());
        return R.ok(resp);
    }

    /**
     * 解析计量的租户归属。
     *
     * <p>有登录上下文时以登录态为准（{@code LoginUser.companyId} 是登录时
     * 由 {@code UserCompanyResolver} 解析出来、随 token 下发的），body 里带的
     * companyId 只用于对照告警 —— 客户端可以随便填，不能拿它决定写到谁名下。</p>
     *
     * <p>无登录上下文时（内部服务调用 / 定时任务）退回 body 的值，让调用方自己负责；
     * 此时若仍是 null，{@link TokenMeter} 会记一条可定位的告警并跳过落库，
     * 而不是让 SQL 抛一个看不懂的 NOT NULL 违例。</p>
     */
    private Long resolveCompanyId(Long requested) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            log.warn("[LLM] 无登录上下文, 计量租户退回请求体的 companyId={}", requested);
            return requested;
        }
        Long own = loginUser.getCompanyId();
        if (requested != null && !requested.equals(own)) {
            log.warn("[LLM] 请求体的 companyId={} 与登录租户 {} 不一致, 以登录态为准", requested, own);
        }
        return own;
    }

    /**
     * 解析计量的用户归属。有登录上下文时以登录态为准。
     */
    private Long resolveUserId(Long requested) {
        Long own = SecurityUtils.getUserId();
        if (own == null) {
            log.warn("[LLM] 无登录上下文, 计量用户退回请求体的 userId={}", requested);
            return requested;
        }
        if (requested != null && !requested.equals(own)) {
            log.warn("[LLM] 请求体的 userId={} 与登录用户 {} 不一致, 以登录态为准", requested, own);
        }
        return own;
    }

    @Operation(summary = "列出可用模型（数据驱动：所有 enabled=true 的 provider）")
    @GetMapping("/models")
    public R<List<Map<String, Object>>> models() {
        // W48.6: 按 priority() 升序，让用户先看到最优先的 provider
        return R.ok(providers.stream()
                .filter(ChatModelProvider::enabled)
                .sorted(Comparator.comparingInt(ChatModelProvider::priority))
                .map(p -> Map.<String, Object>of(
                        "provider", p.name(),
                        "id", p.model(),
                        "name", p.name() + " / " + p.model(),
                        "priority", p.priority(),
                        "enabled", true))
                .toList());
    }

    @lombok.Data
    public static class ChatRequest {
        public List<ChatMessage> messages;
        public Double temperature;
        public Integer maxTokens;
        public List<Map<String, Object>> tools;
        public String toolChoice;
        public String user;
        public Long companyId;
        public Long userId;
        public Long instanceId;
        public Long taskId;
        public String scene;
    }

    /** 简单 R 包装 */
    public record R<T>(int code, String msg, T data) {
        public static <T> R<T> ok(T data) { return new R<>(200, "OK", data); }
    }

}
