package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.context.SecurityContextHolder;
import com.ruoyi.system.api.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link TokenMeter} 的归属兜底回归测试 —— 3a
 *
 * <p>{@code opc_agent_token_usage.company_id / user_id} 都是 NOT NULL。
 * 归属取不到时，插入必然失败，异常又被 {@code record} 里的 catch 吞掉 ——
 * 结果是这次调用的 token/成本**静默消失**。</p>
 *
 * <p>本测试锁住两件事：
 * <ol>
 *   <li>ctx 里没有归属时，从<b>登录态</b>补齐（而不是丢一条记录）；</li>
 *   <li>补不齐时<b>不写库</b>，并留下可定位的信号（写入本来就必然失败，
 *       与其让 SQL 抛一个语义不明的 NOT NULL 违例，不如明说哪边缺了）。</li>
 * </ol>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class TokenMeterTest {

    @Mock
    private TokenMeter.TokenUsageRecorder recorder;

    private TokenMeter meter;

    @BeforeEach
    void setUp() {
        meter = new TokenMeter(recorder);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.remove();
    }

    private void loginAs(long userId, Long companyId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserid(userId);
        loginUser.setCompanyId(companyId);
        SecurityContextHolder.set(SecurityConstants.LOGIN_USER, loginUser);
        SecurityContextHolder.setUserId(String.valueOf(userId));
    }

    private ChatResponse resp() {
        return ChatResponse.builder()
                .content("ok")
                .success(true)
                .tokenInput(100)
                .tokenOutput(50)
                .build();
    }

    private TokenMeter.TokenUsageRecord captured() {
        ArgumentCaptor<TokenMeter.TokenUsageRecord> captor =
                ArgumentCaptor.forClass(TokenMeter.TokenUsageRecord.class);
        verify(recorder).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("ctx 里有归属 → 原样落库")
    void record_usesContextOwnership() {
        meter.record(LlmGateway.ChatContext.builder().companyId(7L).userId(42L).build(), "deepseek-v3", resp());

        TokenMeter.TokenUsageRecord record = captured();
        assertEquals(7L, record.getCompanyId());
        assertEquals(42L, record.getUserId());
    }

    @Test
    @DisplayName("ctx 里没归属 → 从登录态补齐, 记录不丢")
    void record_backfillsOwnershipFromLoginContext() {
        loginAs(42L, 7L);

        meter.record(LlmGateway.ChatContext.builder().build(), "deepseek-v3", resp());

        TokenMeter.TokenUsageRecord record = captured();
        assertEquals(7L, record.getCompanyId(), "归属缺失时应从登录态补齐, 而不是丢记录");
        assertEquals(42L, record.getUserId());
    }

    @Test
    @DisplayName("ctx 与登录态都没有归属 → 不写库 (写也必然失败), 不再抛 NOT NULL 违例")
    void record_skipsWhenOwnershipCannotBeResolved() {
        meter.record(LlmGateway.ChatContext.builder().build(), "deepseek-v3", resp());

        verify(recorder, never()).save(any());
    }

    @Test
    @DisplayName("ctx 为 null 时也不 NPE")
    void record_toleratesNullContext() {
        loginAs(42L, 7L);

        meter.record(null, "deepseek-v3", resp());

        TokenMeter.TokenUsageRecord record = captured();
        assertEquals(7L, record.getCompanyId());
        assertEquals(42L, record.getUserId());
    }

    @Test
    @DisplayName("用户没加入任何公司 → 归属为 null, 不写库, 不用 0 兜底")
    void record_skipsWhenUserHasNoCompany() {
        loginAs(42L, null);

        meter.record(LlmGateway.ChatContext.builder().build(), "deepseek-v3", resp());

        verify(recorder, never()).save(any());
        LoginUser loginUser = SecurityContextHolder.get(SecurityConstants.LOGIN_USER, LoginUser.class);
        assertNull(loginUser.getCompanyId(), "没有公司时必须保持 null —— 用 0 兜底会造成脏数据/越权");
    }
}
