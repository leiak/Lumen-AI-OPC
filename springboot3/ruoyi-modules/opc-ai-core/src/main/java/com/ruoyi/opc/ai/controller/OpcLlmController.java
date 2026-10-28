package com.ruoyi.opc.ai.controller;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.utils.ServletUtils;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.EmbeddingGateway;
import com.ruoyi.opc.ai.gateway.llm.EmbeddingProvider;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.ai.gateway.llm.TokenMeter;
import com.ruoyi.opc.ai.security.PromptGuard;
import com.ruoyi.system.api.model.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
     * 向量化网关。opc-hr 的 {@code OpcHrAiCoreGateway} 早就声明了
     * {@code POST /opc/llm/embedding}，但对端一直没这个路由 —— 现在补上。
     */
    private final EmbeddingGateway embeddingGateway;
    /**
     * 记忆系统。用来做自检 —— 记忆到底通不通，得有个能问的地方。
     */
    private final com.ruoyi.opc.ai.memory.Memory memory;
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
     * <p>无登录上下文时，<b>只有内部调用</b>才认 body 里的值：服务间调用（Feign）
     * 没有浏览器登录态，归属由调用方显式传（工作流按其 companyId、定时任务按 0=系统），
     * 这条链路是服务自己发起的、可信。外部流量一律不认 body —— 否则任何人都能把
     * 用量记到别家公司名下（账单可被别人刷）。</p>
     *
     * <p>两处都取不到时返回 null，{@link TokenMeter} 会记一条可定位的告警并跳过落库，
     * 而不是让 SQL 抛一个看不懂的 NOT NULL 违例。</p>
     */
    private Long resolveCompanyId(Long requested) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null) {
            if (!isInternalCall()) {
                log.warn("[LLM] 无登录上下文且非内部调用, 计量租户留空 (body companyId={} 不作数)", requested);
                return null;
            }
            log.info("[LLM] 内部调用无登录上下文, 计量租户取调用方显式传的 companyId={}", requested);
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
     *
     * <p>内部调用且没有登录用户时（定时任务触发的工作流）记 <b>0（系统）</b> ——
     * {@code opc_agent_token_usage.user_id} 是 NOT NULL，与其把整条记录丢掉
     * （这次调用的 token/成本凭空消失），不如老老实实记成系统发起。</p>
     */
    private Long resolveUserId(Long requested) {
        Long own = SecurityUtils.getUserId();
        if (own == null) {
            if (!isInternalCall()) {
                log.warn("[LLM] 无登录上下文且非内部调用, 计量用户留空 (body userId={} 不作数)", requested);
                return null;
            }
            log.info("[LLM] 内部调用无登录用户, 计量用户记 0(系统) (body userId={})", requested);
            return requested != null ? requested : 0L;
        }
        if (requested != null && !requested.equals(own)) {
            log.warn("[LLM] 请求体的 userId={} 与登录用户 {} 不一致, 以登录态为准", requested, own);
        }
        return own;
    }

    /**
     * 本次请求是不是「服务间内部调用」。
     *
     * <p>判据是 {@code from-source: inner} 请求头：{@code FeignRequestInterceptor}
     * 给**所有**服务间调用注入这个头，而网关的 {@code AuthFilter} 会把**外部流量**
     * 带来的这个头剥掉（{@code AuthFilter.java:75} removeHeader）。再加上服务端口
     * 现在一律只绑 127.0.0.1，外部既连不到服务端口，也过不了网关那一关 ——
     * 所以「带着 from-source: inner」= 「来自 compose 内网的另一个服务」。</p>
     */
    private boolean isInternalCall() {
        HttpServletRequest request = ServletUtils.getRequest();
        if (request == null) {
            return false;
        }
        return SecurityConstants.INNER.equals(request.getHeader(SecurityConstants.FROM_SOURCE));
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

    /**
     * 文本向量化。
     *
     * <p><b>这个端点以前只有"合同"没有"实现"</b>：
     * {@code opc-hr} 的 {@code OpcHrAiCoreGateway} 早就声明了
     * {@code @PostMapping("/opc/llm/embedding")}（还配了降级工厂和单测），
     * 而对端根本没有这个路由 —— 调过去就是 404，然后被 fallback 吞成
     * {@code R.fail()}。候选人画像向量化、JD 技能召回因此从来没跑起来过。
     *
     * <p>协议：
     * <pre>
     * 请求  {"texts": ["..."], "type": "query"|"db"}
     * 响应  {"provider":"minimax","model":"embo-01","dimension":1536,"vectors":[[...]]}
     * </pre>
     */
    @Operation(summary = "文本向量化（embedding）")
    @PostMapping("/embedding")
    public R<Map<String, Object>> embedding(@RequestBody EmbeddingRequest req) {
        List<String> texts = req.getTexts();
        if ((texts == null || texts.isEmpty()) && req.getText() != null && !req.getText().isBlank()) {
            texts = List.of(req.getText());
        }
        if (texts == null || texts.isEmpty()) {
            return R.fail("texts 不能为空");
        }
        EmbeddingProvider.EmbedType type = "query".equalsIgnoreCase(req.getType())
                ? EmbeddingProvider.EmbedType.QUERY
                : EmbeddingProvider.EmbedType.DOCUMENT;

        EmbeddingGateway.EmbeddingResult result;
        try {
            result = embeddingGateway.embedBatchWithProvider(texts, type);
        } catch (Exception e) {
            log.error("[Embedding] 调用失败: {}", e.getMessage());
            return R.fail(e.getMessage());
        }

        List<List<Float>> vectors = new ArrayList<>(result.vectors().size());
        for (float[] v : result.vectors()) {
            List<Float> list = new ArrayList<>(v.length);
            for (float f : v) {
                list.add(f);
            }
            vectors.add(list);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", result.provider());
        data.put("model", result.model());
        data.put("dimension", result.dimension());
        data.put("vectors", vectors);
        return R.ok(data);
    }

    /**
     * 记忆系统自检。
     *
     * <p><b>为什么需要这个端点</b>：记忆系统以前「不记事」这件事，
     * 从外面**看不出来** —— 接口一路返回成功，日志里什么都查不到，
     * 看板上一片绿。而记忆不记事意味着多轮对话没有上下文，
     * 长期检索永远返回空 —— 这两件事都不会报错。
     *
     * <p>这里做的是真的往返：向量化 → 写进 Qdrant → 搜回来 → 删掉探针。
     * 任何一环不通，对应字段就是 false。
     *
     * <p>鉴权跟 {@code /opc/llm/**} 其余端点一致（网关不放行，需要登录）。
     */
    @Operation(summary = "记忆系统自检（短期 + 长期 + 向量库往返）")
    @GetMapping("/memory/health")
    public R<Map<String, Object>> memoryHealth() {
        try {
            return R.ok(memory.selfCheck());
        } catch (Exception e) {
            log.error("[Memory] 自检失败: {}", e.getMessage(), e);
            return R.fail("记忆自检失败: " + e.getMessage());
        }
    }

    @lombok.Data
    public static class EmbeddingRequest {
        /** 待向量化的文本（多条）。 */
        private List<String> texts;
        /** 单条文本（texts 为空时用这个）。 */
        private String text;
        /** {@code query} = 用于检索；{@code db} = 要入库。不填按 db 处理。 */
        private String type;
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
        public static <T> R<T> fail(String msg) { return new R<>(500, msg, null); }
    }

}
