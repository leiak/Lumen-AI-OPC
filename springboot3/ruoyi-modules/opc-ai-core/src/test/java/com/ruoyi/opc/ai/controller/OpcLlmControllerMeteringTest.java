package com.ruoyi.opc.ai.controller;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.opc.ai.gateway.llm.ChatMessage;
import com.ruoyi.opc.ai.gateway.llm.ChatModelProvider;
import com.ruoyi.opc.ai.gateway.llm.ChatResponse;
import com.ruoyi.opc.ai.gateway.llm.LlmGateway;
import com.ruoyi.opc.ai.security.PromptGuard;
import com.ruoyi.system.api.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpcLlmController#chat} 的计量归属回归测试 —— 3a
 *
 * <p><b>缺陷</b>：{@code /opc/llm/chat} 的 companyId / userId 直接取自 HTTP body，
 * 而前端两个调用点（{@code llm/chat.vue}、{@code agent/instance.vue}）都不发这两个字段
 * → company_id 为 null → 落库撞 {@code opc_agent_token_usage.company_id NOT NULL}
 * → 异常被 {@code TokenMeter.record} 的 catch 吞掉（只打一行 error）
 * → <b>每一次</b> LLM 调用的用量/成本记录都静默丢失，
 * 而 {@code /opc/finance/agg/token-usage} 读的就是这张表 → 账单一直是空的。</p>
 *
 * <p><b>修复</b>：计量归属以<b>登录态</b>为准（{@code LoginUser.companyId} +
 * {@code SecurityUtils.getUserId()}），body 里的同名字段只作对照告警，不再决定写到谁名下。</p>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class OpcLlmControllerMeteringTest {

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private PromptGuard promptGuard;

    @Mock
    private ChatModelProvider provider;

    @InjectMocks
    private OpcLlmController controller;

    @BeforeEach
    void setUp() {
        when(llmGateway.chat(anyList(), any(), any())).thenReturn(
                ChatResponse.builder().content("ok").success(true).build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 模拟一次「服务间内部调用」：请求头带 from-source: inner。
     *
     * <p>这个头由 FeignRequestInterceptor 注入，网关会把外部流量带来的它剥掉，
     * 所以它存在 = 请求来自 compose 内网的另一个服务。</p>
     */
    private void internalCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.FROM_SOURCE, SecurityConstants.INNER);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /** 模拟已登录用户：user_id 头 + LoginUser（含租户）。 */
    private void loginAs(long userId, Long companyId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setCompanyId(companyId);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        SecurityContextHolder.setUserId(String.valueOf(userId));
    }

    private LlmGateway.ChatContext capturedContext() {
        ArgumentCaptor<LlmGateway.ChatContext> captor = ArgumentCaptor.forClass(LlmGateway.ChatContext.class);
        verify(llmGateway).chat(anyList(), any(), captor.capture());
        return captor.getValue();
    }

    private OpcLlmController.ChatRequest request(Long companyId, Long userId) {
        OpcLlmController.ChatRequest req = new OpcLlmController.ChatRequest();
        req.messages = List.of(ChatMessage.user("hi"));
        req.companyId = companyId;
        req.userId = userId;
        return req;
    }

    @Test
    @DisplayName("登录态的租户/用户才是计量归属, body 里的 companyId 一律不作数")
    void meteringOwnership_comesFromLoginContext_notFromBody() {
        loginAs(42L, 7L);

        // body 里带的是别的租户/别的用户 —— 客户端可以随便填, 但不能决定写到谁名下
        controller.chat(request(999L, 12345L));

        LlmGateway.ChatContext ctx = capturedContext();
        assertEquals(7L, ctx.getCompanyId(), "计量租户必须来自登录态, 不能来自 body");
        assertEquals(42L, ctx.getUserId(), "计量用户必须来自登录态, 不能来自 body");
    }

    @Test
    @DisplayName("前端不发 companyId/userId 时也能拿到归属 (前端两个调用点就是这种情况)")
    void meteringOwnership_presentEvenWhenBodyOmitsThem() {
        loginAs(42L, 7L);

        controller.chat(request(null, null));

        LlmGateway.ChatContext ctx = capturedContext();
        assertEquals(7L, ctx.getCompanyId());
        assertEquals(42L, ctx.getUserId());
    }

    @Test
    @DisplayName("内部调用(from-source: inner)且无登录上下文 → 认调用方显式传的归属")
    void meteringOwnership_fallsBackToBodyForInternalCall() {
        internalCall();

        controller.chat(request(5L, 6L));

        LlmGateway.ChatContext ctx = capturedContext();
        assertEquals(5L, ctx.getCompanyId());
        assertEquals(6L, ctx.getUserId());
    }

    @Test
    @DisplayName("内部调用且调用方没传 userId → 记 0(系统), 不把整条记录丢掉")
    void meteringOwnership_internalCallWithoutUserId_recordsSystem() {
        internalCall();

        controller.chat(request(5L, null));

        LlmGateway.ChatContext ctx = capturedContext();
        assertEquals(5L, ctx.getCompanyId());
        assertEquals(0L, ctx.getUserId(), "定时任务没有登录用户 —— 记 0(系统) 而不是丢记录");
    }

    @Test
    @DisplayName("不是内部调用又没有登录上下文 → 不认 body 的 companyId(否则等于让别人替你记账)")
    void meteringOwnership_externalCallWithoutLoginContext_doesNotTrustBody() {
        controller.chat(request(5L, 6L));

        LlmGateway.ChatContext ctx = capturedContext();
        assertNull(ctx.getCompanyId(), "外部流量没有可信归属 —— body 里的 companyId 不作数");
        assertNull(ctx.getUserId());
    }

    @Test
    @DisplayName("登录用户没加入任何公司时归属为 null, 交给 TokenMeter 处理 (而不是拿 0 兜底)")
    void meteringOwnership_nullWhenUserHasNoCompany() {
        loginAs(42L, null);

        controller.chat(request(null, null));

        LlmGateway.ChatContext ctx = capturedContext();
        assertNotNull(ctx);
        org.junit.jupiter.api.Assertions.assertNull(ctx.getCompanyId(),
                "没有公司时必须是 null —— 用 0 兜底会造成脏数据/越权");
        assertEquals(42L, ctx.getUserId());
    }
}
