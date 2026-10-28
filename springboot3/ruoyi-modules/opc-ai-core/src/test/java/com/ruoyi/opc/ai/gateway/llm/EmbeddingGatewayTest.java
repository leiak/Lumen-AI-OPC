package com.ruoyi.opc.ai.gateway.llm;

import com.ruoyi.opc.common.exception.OpcException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EmbeddingGateway} 的优先级 / fallback 回归测试。
 *
 * <p><b>这个网关以前根本不存在</b> —— 记忆系统里的 {@code embed()} 直接返回
 * {@code Math.random()} 填出来的 1536 维"向量"。于是写进 Qdrant 的是噪声，
 * 检索出来的也是噪声，而接口一路返回成功，日志里什么都看不出来。</p>
 *
 * <p>本测试钉住三件事：
 * <ol>
 *   <li><b>没有 provider 就明说没有</b>（抛 {@link OpcException}），不拿随机数冒充向量；</li>
 *   <li><b>按 priority 升序选</b>，而不是按 Spring 的注入顺序 —— 配置里写着
 *       "MiniMax 优先"而实际先打的是另一个 provider，同样是"配置说了不算"；</li>
 *   <li><b>第一个失败自动回退下一个</b>，全挂才抛异常。</li>
 * </ol>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingGateway 单测")
class EmbeddingGatewayTest {

    @Mock
    private EmbeddingProvider primary;   // priority = 5（优先级最高的）
    @Mock
    private EmbeddingProvider backup;    // priority = 10
    @Mock
    private EmbeddingProvider standby;   // priority = 20
    @Mock
    private EmbeddingProvider disabled;  // enabled() = false

    /** 让三个 mock 处于"启用 + 各自优先级"的状态。 */
    private void enable(EmbeddingProvider p, int priority, String name) {
        when(p.enabled()).thenReturn(true);
        when(p.priority()).thenReturn(priority);
        when(p.name()).thenReturn(name);
    }

    // ─── 没有 provider ────────────────────────────────────────────────────────

    @Test
    @DisplayName("一个 provider 都没有 → 抛 OpcException，消息里说清是 api-key / provider 的问题")
    void noProvider_throwsOpcException() {
        EmbeddingGateway gateway = new EmbeddingGateway(List.of());

        OpcException e = assertThrows(OpcException.class,
                () -> gateway.embedBatch(List.of("一段话"), EmbeddingProvider.EmbedType.DOCUMENT));

        assertTrue(e.getMessage().contains("api-key"),
                "报错要指向可操作的配置项（api-key），而不是笼统地说失败：" + e.getMessage());
        assertTrue(e.getMessage().contains("provider"),
                "报错要说清是 embedding provider 的问题：" + e.getMessage());
    }

    @Test
    @DisplayName("provider 全被禁用 → 同样抛 OpcException（配置存在 ≠ 能用）")
    void allProvidersDisabled_throwsOpcException() {
        when(disabled.enabled()).thenReturn(false);
        EmbeddingGateway gateway = new EmbeddingGateway(List.of(disabled));

        assertThrows(OpcException.class,
                () -> gateway.embedBatch(List.of("一段话"), EmbeddingProvider.EmbedType.DOCUMENT));
        assertFalse(gateway.available4Memory(), "没有可用 provider 时必须如实报告没有");
    }

    @Test
    @DisplayName("available4Memory：有可用 provider 时才为 true")
    void available4Memory_reflectsRealAvailability() {
        when(disabled.enabled()).thenReturn(false);
        EmbeddingGateway empty = new EmbeddingGateway(List.of(disabled));
        assertFalse(empty.available4Memory());

        enable(primary, 5, "minimax");
        EmbeddingGateway ok = new EmbeddingGateway(List.of(primary));
        assertTrue(ok.available4Memory());
    }

    // ─── 优先级排序 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("多个 provider → available() 按 priority 升序，禁用的被剔除")
    void available_sortsByPriorityAscending() {
        enable(primary, 5, "minimax");
        enable(backup, 10, "deepseek");
        enable(standby, 20, "wenxin");
        when(disabled.enabled()).thenReturn(false);

        // 故意按"错"的顺序注入 —— 排序不能依赖注入顺序
        EmbeddingGateway gateway = new EmbeddingGateway(List.of(standby, disabled, backup, primary));

        assertEquals(List.of(primary, backup, standby), gateway.available());
    }

    @Test
    @DisplayName("chat 式调用：priority 最高的 provider 先被用，哪怕它排在注入顺序最后")
    void embed_usesHighestPriorityProviderFirst() {
        enable(primary, 5, "minimax");
        enable(backup, 10, "deepseek");
        when(primary.model()).thenReturn("embo-01");
        when(primary.embedBatch(anyList(), any())).thenReturn(List.of(new float[]{1f, 2f}));

        EmbeddingGateway gateway = new EmbeddingGateway(List.of(backup, primary));

        float[] v = gateway.embed("一段话", EmbeddingProvider.EmbedType.DOCUMENT);

        assertArrayEquals(new float[]{1f, 2f}, v, 1e-6f);
        verify(primary).embedBatch(anyList(), eq(EmbeddingProvider.EmbedType.DOCUMENT));
        verify(backup, never()).embedBatch(anyList(), any());
    }

    // ─── fallback ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("第一个 provider 失败 → 自动回退到下一个")
    void embedBatch_fallsBackToNextProvider() {
        enable(primary, 5, "minimax");
        enable(backup, 10, "deepseek");
        when(primary.embedBatch(anyList(), any())).thenThrow(new RuntimeException("MiniMax 502"));
        when(backup.embedBatch(anyList(), any())).thenReturn(List.of(new float[]{9f}));

        EmbeddingGateway gateway = new EmbeddingGateway(List.of(primary, backup));

        List<float[]> r = gateway.embedBatch(List.of("一段话"), EmbeddingProvider.EmbedType.DOCUMENT);

        assertEquals(1, r.size());
        assertArrayEquals(new float[]{9f}, r.get(0), 1e-6f);
        verify(primary).embedBatch(anyList(), any());
        verify(backup).embedBatch(anyList(), any());
    }

    @Test
    @DisplayName("所有 provider 都失败 → 抛 OpcException，并带上最后一个错误")
    void embedBatch_allProvidersFail_throwsOpcException() {
        enable(primary, 5, "minimax");
        enable(backup, 10, "deepseek");
        when(primary.embedBatch(anyList(), any())).thenThrow(new RuntimeException("boom-1"));
        when(backup.embedBatch(anyList(), any())).thenThrow(new IllegalStateException("boom-2"));

        EmbeddingGateway gateway = new EmbeddingGateway(List.of(primary, backup));

        OpcException e = assertThrows(OpcException.class,
                () -> gateway.embedBatch(List.of("一段话"), EmbeddingProvider.EmbedType.DOCUMENT));

        assertTrue(e.getMessage().contains("boom"),
                "异常消息要带上最后一个真实错误，便于定位：" + e.getMessage());
    }

    @Test
    @DisplayName("provider 返回的向量条数对不上 → 当作失败处理（不能默默少给一条）")
    void embedBatch_vectorCountMismatch_isTreatedAsFailure() {
        enable(primary, 5, "minimax");
        enable(backup, 10, "deepseek");
        // 请求 2 条，只回 1 条
        when(primary.embedBatch(anyList(), any())).thenReturn(List.of(new float[]{1f}));
        when(backup.embedBatch(anyList(), any())).thenReturn(List.of(new float[]{1f}, new float[]{2f}));

        EmbeddingGateway gateway = new EmbeddingGateway(List.of(primary, backup));

        List<float[]> r = gateway.embedBatch(List.of("甲", "乙"), EmbeddingProvider.EmbedType.DOCUMENT);

        assertEquals(2, r.size(), "条数对不上的 provider 不能被采信");
        verify(primary).embedBatch(anyList(), any());
        verify(backup).embedBatch(anyList(), any());
    }

    // ─── 顺序与自述 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("embedBatch 返回的向量顺序与入参顺序一致")
    void embedBatch_preservesInputOrder() {
        enable(primary, 5, "minimax");
        when(primary.embedBatch(anyList(), any())).thenReturn(List.of(
                new float[]{1f}, new float[]{2f}, new float[]{3f}));

        EmbeddingGateway gateway = new EmbeddingGateway(List.of(primary));

        List<float[]> r = gateway.embedBatch(List.of("甲", "乙", "丙"), EmbeddingProvider.EmbedType.DOCUMENT);

        assertEquals(3, r.size());
        assertArrayEquals(new float[]{1f}, r.get(0), 1e-6f);
        assertArrayEquals(new float[]{2f}, r.get(1), 1e-6f);
        assertArrayEquals(new float[]{3f}, r.get(2), 1e-6f);
    }

    @Test
    @DisplayName("embedBatchWithProvider 如实报告是谁算的、什么模型、多少维")
    void embedBatchWithProvider_reportsActualProvider() {
        enable(primary, 5, "minimax");
        when(primary.model()).thenReturn("embo-01");
        when(primary.embedBatch(anyList(), any())).thenReturn(List.of(new float[]{1f, 2f, 3f}));

        EmbeddingGateway gateway = new EmbeddingGateway(List.of(primary));

        EmbeddingGateway.EmbeddingResult result =
                gateway.embedBatchWithProvider(List.of("一段话"), EmbeddingProvider.EmbedType.QUERY);

        assertEquals("minimax", result.provider());
        assertEquals("embo-01", result.model());
        assertEquals(3, result.dimension());
        assertEquals(1, result.vectors().size());
    }

    @Test
    @DisplayName("text 为 null → 当作空串处理，不 NPE")
    void embed_nullText_doesNotThrow() {
        enable(primary, 5, "minimax");
        when(primary.embedBatch(anyList(), any())).thenReturn(List.of(new float[]{0f}));

        EmbeddingGateway gateway = new EmbeddingGateway(List.of(primary));

        assertArrayEquals(new float[]{0f}, gateway.embed(null), 1e-6f);
        verify(primary).embedBatch(eq(List.of("")), any());
    }

    // ─── 自述：这次是谁算的 ───────────────────────────────────────────────────

    @Test
    @DisplayName("primaryName 如实报告首选 provider —— 出问题时要查得到人头上")
    void primaryName_reportsWhoWillCompute() {
        enable(primary, 5, "minimax");
        enable(backup, 10, "deepseek");
        when(disabled.enabled()).thenReturn(false);

        EmbeddingGateway gateway = new EmbeddingGateway(List.of(backup, disabled, primary));

        assertEquals("minimax", gateway.primaryName());
    }

    @Test
    @DisplayName("一个 provider 都没有 → primaryName 是 none（不是空、也不是随便一个）")
    void primaryName_noneWhenNoProvider() {
        EmbeddingGateway gateway = new EmbeddingGateway(List.of());

        assertEquals("none", gateway.primaryName());
    }
}
