package com.ruoyi.opc.ai.memory;

import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.opc.ai.gateway.llm.EmbeddingGateway;
import com.ruoyi.opc.ai.gateway.llm.EmbeddingProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Memory} 长期记忆（Qdrant + Embedding）回归测试。
 *
 * <p><b>这块以前是"假"的，而且全程无报错</b>：</p>
 * <ul>
 *   <li>{@code embed()} 返回 {@code Math.random()} 填的 1536 维"向量" ——
 *       写进 Qdrant 的是噪声，检索出来的也是噪声，但接口一路返回成功；</li>
 *   <li>{@code searchLongTerm} 少了租户过滤条件 = <b>跨租户检索别人的记忆</b>。</li>
 * </ul>
 *
 * <p>本测试钉住三件互相拉扯的事，缺一不可：
 * <ol>
 *   <li><b>没有 provider 就明说没有</b> —— 绝不拿随机向量凑数，也绝不写库；</li>
 *   <li><b>检索必须带 company_id 过滤</b> —— 缺了就等于越权；</li>
 *   <li><b>写进去的 payload 必须带 company_id</b> —— 否则过滤条件永远匹配不到。</li>
 * </ol>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("Memory 长期记忆 单测")
class MemoryLongTermTest {

    private static final String COLLECTION = "opc_memory_test";

    @Mock
    private RedisService redisService;

    @Mock
    private QdrantVectorStore vectorStore;

    @Mock
    private EmbeddingGateway embeddingGateway;

    private Memory memory;

    @BeforeEach
    void setUp() {
        memory = new Memory(redisService, vectorStore, embeddingGateway);
        // @Value 字段：纯单测里没有 Spring 容器，手工塞进去（与 application.yml 的默认值一致）
        ReflectionTestUtils.setField(memory, "longTermCollection", COLLECTION);
        ReflectionTestUtils.setField(memory, "vectorDimension", 1536);
        ReflectionTestUtils.setField(memory, "shortTermTtl", 86400L);
    }

    private float[] vector() {
        return new float[]{0.1f, 0.2f, 0.3f};
    }

    // ─── 没有 embedding provider 时必须失败，而不是写随机向量 ────────────────

    @Test
    @DisplayName("没有可用 embedding provider → 写不进去，且一次都不碰 Qdrant")
    void writeLongTerm_withoutProvider_failsAndNeverWrites() {
        when(embeddingGateway.available4Memory()).thenReturn(false);

        boolean ok = memory.writeLongTerm("sess-1", "company-9", "客户要求把账期改成 90 天", Map.of());

        assertFalse(ok, "没有 provider 时必须明说写不进去 —— "
                + "往 Qdrant 写随机向量等于把噪声存进向量库并假装成功");
        verify(vectorStore, never()).upsert(anyString(), anyString(), any(float[].class), any());
        verify(embeddingGateway, never()).embed(anyString(), any(EmbeddingProvider.EmbedType.class));
    }

    @Test
    @DisplayName("内容为空/空白 → 直接失败，连 provider 都不问")
    void writeLongTerm_blankContent_failsWithoutAskingProvider() {
        assertFalse(memory.writeLongTerm("sess-1", "company-9", null, Map.of()));
        assertFalse(memory.writeLongTerm("sess-1", "company-9", "   ", Map.of()));

        verify(embeddingGateway, never()).available4Memory();
        verify(vectorStore, never()).upsert(anyString(), anyString(), any(float[].class), any());
    }

    @Test
    @DisplayName("embedding 调用抛异常 → 写失败，但不写库、不把异常抛给调用方")
    void writeLongTerm_embeddingThrows_failsWithoutWriting() {
        when(embeddingGateway.available4Memory()).thenReturn(true);
        when(embeddingGateway.embed(anyString(), any(EmbeddingProvider.EmbedType.class)))
                .thenThrow(new RuntimeException("MiniMax 502"));

        boolean ok = memory.writeLongTerm("sess-1", "company-9", "内容", Map.of());

        assertFalse(ok, "embedding 挂了必须如实报告失败，不能吞掉");
        verify(vectorStore, never()).upsert(anyString(), anyString(), any(float[].class), any());
    }

    @Test
    @DisplayName("embedding 返回空向量 → 写失败，且不写库（空向量进 Qdrant 等于毒数据）")
    void writeLongTerm_emptyVector_failsWithoutWriting() {
        when(embeddingGateway.available4Memory()).thenReturn(true);
        when(embeddingGateway.embed(anyString(), any(EmbeddingProvider.EmbedType.class)))
                .thenReturn(new float[0]);

        boolean ok = memory.writeLongTerm("sess-1", "company-9", "内容", Map.of());

        assertFalse(ok, "空向量必须被拒绝");
        verify(vectorStore, never()).upsert(anyString(), anyString(), any(float[].class), any());
    }

    // ─── 写入成功时的内容 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("写入成功 → 向量进 Qdrant，payload 带 company_id/session_id/content")
    void writeLongTerm_success_upsertsWithCompanyPayload() {
        when(embeddingGateway.available4Memory()).thenReturn(true);
        when(embeddingGateway.embed(eq("账期改成 90 天"), eq(EmbeddingProvider.EmbedType.DOCUMENT)))
                .thenReturn(vector());

        boolean ok = memory.writeLongTerm("sess-1", "company-9", "账期改成 90 天", Map.of("tag", "财务"));

        assertTrue(ok, "provider 可用且向量正常时必须真的写进去");

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<float[]> vecCaptor = ArgumentCaptor.forClass(float[].class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        verify(vectorStore).upsert(eq(COLLECTION), idCaptor.capture(), vecCaptor.capture(),
                payloadCaptor.capture());

        assertTrue(idCaptor.getValue().startsWith("sess-1-"),
                "业务 id 必须带上 sessionId 前缀，便于按会话清理：" + idCaptor.getValue());
        assertArrayEquals(vector(), vecCaptor.getValue(), 1e-6f);

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("company-9", payload.get("company_id"),
                "payload 必须带 company_id —— 否则检索时的租户过滤永远匹配不到");
        assertEquals("sess-1", payload.get("session_id"));
        assertEquals("账期改成 90 天", payload.get("content"));
        assertEquals("财务", payload.get("tag"), "调用方传的 metadata 也要带上");
    }

    @Test
    @DisplayName("写入用 DOCUMENT 用途向量化（不是 QUERY）")
    void writeLongTerm_usesDocumentEmbedType() {
        when(embeddingGateway.available4Memory()).thenReturn(true);
        when(embeddingGateway.embed(anyString(), any(EmbeddingProvider.EmbedType.class)))
                .thenReturn(vector());

        memory.writeLongTerm("sess-1", "company-9", "内容", Map.of());

        verify(embeddingGateway).embed(eq("内容"), eq(EmbeddingProvider.EmbedType.DOCUMENT));
    }

    // ─── 检索必须按租户过滤 ───────────────────────────────────────────────────

    @Test
    @DisplayName("companyId=null → 直接返回空，一次都不查 Qdrant（否则就是跨租户检索）")
    void searchLongTerm_nullCompanyId_returnsEmptyAndNeverSearches() {
        when(embeddingGateway.available4Memory()).thenReturn(true);

        List<String> hits = memory.searchLongTerm(null, "上次的账期", 5);

        assertTrue(hits.isEmpty(), "缺 companyId 必须拒绝检索");
        verify(vectorStore, never()).search(anyString(), any(float[].class), anyInt(), anyString(), anyString());
        verify(vectorStore, never()).searchHits(anyString(), any(float[].class), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("companyId 为空白串 → 同样拒绝检索")
    void searchLongTerm_blankCompanyId_returnsEmptyAndNeverSearches() {
        when(embeddingGateway.available4Memory()).thenReturn(true);

        List<String> hits = memory.searchLongTerm("   ", "上次的账期", 5);

        assertTrue(hits.isEmpty());
        verify(vectorStore, never()).search(anyString(), any(float[].class), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("companyId 非空 → 按 company_id 过滤检索，并原样返回命中内容")
    void searchLongTerm_withCompanyId_filtersByCompanyId() {
        when(embeddingGateway.available4Memory()).thenReturn(true);
        when(embeddingGateway.embed(eq("上次的账期"), eq(EmbeddingProvider.EmbedType.QUERY)))
                .thenReturn(new float[]{0.9f});
        when(vectorStore.search(anyString(), any(float[].class), anyInt(), anyString(), anyString()))
                .thenReturn(List.of("上次的账期是 60 天", "合同编号 HT-2026-001"));

        List<String> hits = memory.searchLongTerm("company-9", "上次的账期", 5);

        assertEquals(List.of("上次的账期是 60 天", "合同编号 HT-2026-001"), hits);
        verify(vectorStore).search(eq(COLLECTION), any(float[].class), eq(5),
                eq("company_id"), eq("company-9"));
        verify(vectorStore, never()).searchHits(anyString(), any(float[].class), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("检索用 QUERY 用途向量化（不是 DOCUMENT）")
    void searchLongTerm_usesQueryEmbedType() {
        when(embeddingGateway.available4Memory()).thenReturn(true);
        when(embeddingGateway.embed(anyString(), any(EmbeddingProvider.EmbedType.class)))
                .thenReturn(new float[]{0.9f});
        when(vectorStore.search(anyString(), any(float[].class), anyInt(), anyString(), anyString()))
                .thenReturn(List.of());

        memory.searchLongTerm("company-9", "上次的账期", 5);

        verify(embeddingGateway).embed(eq("上次的账期"), eq(EmbeddingProvider.EmbedType.QUERY));
    }

    @Test
    @DisplayName("没有 provider 时检索也直接跳过，不查 Qdrant")
    void searchLongTerm_withoutProvider_returnsEmptyAndNeverSearches() {
        when(embeddingGateway.available4Memory()).thenReturn(false);

        List<String> hits = memory.searchLongTerm("company-9", "上次的账期", 5);

        assertTrue(hits.isEmpty());
        verify(vectorStore, never()).search(anyString(), any(float[].class), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("查询词为空 / topK<=0 → 返回空，不查 Qdrant")
    void searchLongTerm_invalidArgs_returnsEmptyAndNeverSearches() {
        when(embeddingGateway.available4Memory()).thenReturn(true);

        assertTrue(memory.searchLongTerm("company-9", null, 5).isEmpty());
        assertTrue(memory.searchLongTerm("company-9", "  ", 5).isEmpty());
        assertTrue(memory.searchLongTerm("company-9", "上次的账期", 0).isEmpty());

        verify(vectorStore, never()).search(anyString(), any(float[].class), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("带分数的检索同样必须按 company_id 过滤")
    void searchLongTermHits_filtersByCompanyId() {
        when(embeddingGateway.available4Memory()).thenReturn(true);
        when(embeddingGateway.embed(anyString(), any(EmbeddingProvider.EmbedType.class)))
                .thenReturn(new float[]{0.9f});
        when(vectorStore.searchHits(anyString(), any(float[].class), anyInt(), anyString(), anyString()))
                .thenReturn(List.of(new QdrantVectorStore.MemoryHit("sess-1-1", "uuid-1", "上次的账期是 60 天", 0.87)));

        List<QdrantVectorStore.MemoryHit> hits = memory.searchLongTermHits("company-9", "上次的账期", 3);

        assertEquals(1, hits.size());
        assertEquals("上次的账期是 60 天", hits.get(0).content());
        verify(vectorStore).searchHits(eq(COLLECTION), any(float[].class), eq(3),
                eq("company_id"), eq("company-9"));
    }

    @Test
    @DisplayName("带分数的检索：companyId 缺失 → 返回空，不查 Qdrant")
    void searchLongTermHits_withoutCompanyId_returnsEmptyAndNeverSearches() {
        when(embeddingGateway.available4Memory()).thenReturn(true);

        assertTrue(memory.searchLongTermHits(null, "上次的账期", 3).isEmpty());

        verify(vectorStore, never()).searchHits(anyString(), any(float[].class), anyInt(), anyString(), anyString());
    }

    // ─── 集合初始化不能拖垮服务 ───────────────────────────────────────────────

    @Test
    @DisplayName("启动时按配置建集合（维度取自配置）")
    void initLongTermCollection_usesConfiguredCollectionAndDimension() {
        memory.initLongTermCollection();

        verify(vectorStore).ensureCollection(COLLECTION, 1536L);
    }

    @Test
    @DisplayName("Qdrant 还没起来 → 初始化失败也不能把服务带崩（记忆是软依赖）")
    void initLongTermCollection_qdrantDown_doesNotThrow() {
        when(vectorStore.ensureCollection(anyString(), anyLong()))
                .thenThrow(new RuntimeException("Qdrant 连接 refused"));

        assertDoesNotThrow(() -> memory.initLongTermCollection(),
                "Qdrant 不可用时启动必须继续 —— 记忆是软依赖");
    }

    // ─── 自检读数 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("集合名与维度能被运维读出来（自检用）")
    void exposesCollectionAndDimension() {
        assertEquals(COLLECTION, memory.longTermCollection());
        assertEquals(1536, memory.vectorDimension());
    }

    @Test
    @DisplayName("自检：真的往返一次（写进去再搜回来）才算通")
    void selfCheck_roundTrip_trueWhenEverythingWorks() {
        when(embeddingGateway.available4Memory()).thenReturn(true);
        when(embeddingGateway.primaryName()).thenReturn("minimax");
        when(embeddingGateway.embed(anyString(), any(EmbeddingProvider.EmbedType.class)))
                .thenReturn(new float[]{0.5f, 0.6f});
        when(vectorStore.collectionExists(anyString())).thenReturn(true);
        when(vectorStore.collectionDimension(anyString())).thenReturn(1536L);
        when(vectorStore.searchHits(anyString(), any(float[].class), anyInt(), eq("point_key"), anyString()))
                .thenAnswer(inv -> List.of(new QdrantVectorStore.MemoryHit(
                        inv.getArgument(4, String.class), "uuid-1", "探针", 0.99)));

        Map<String, Object> out = memory.selfCheck();

        assertEquals(Boolean.TRUE, out.get("roundTrip"),
                "写进去又搜得回来才算通 —— 集合存在不等于链路通：" + out);
        assertEquals(Boolean.TRUE, out.get("embeddingAvailable"));
        assertEquals("minimax", out.get("embeddingProvider"));
        verify(vectorStore).delete(eq(COLLECTION), anyString());
    }

    @Test
    @DisplayName("自检：没有 embedding provider → roundTrip=false（如实说不通）")
    void selfCheck_roundTrip_falseWhenEmbeddingUnavailable() {
        when(embeddingGateway.available4Memory()).thenReturn(false);
        when(embeddingGateway.primaryName()).thenReturn("none");
        when(vectorStore.collectionExists(anyString())).thenReturn(true);

        Map<String, Object> out = memory.selfCheck();

        assertEquals(Boolean.FALSE, out.get("roundTrip"));
        assertEquals(Boolean.FALSE, out.get("embeddingAvailable"));
        assertEquals("none", out.get("embeddingProvider"));
    }
}
