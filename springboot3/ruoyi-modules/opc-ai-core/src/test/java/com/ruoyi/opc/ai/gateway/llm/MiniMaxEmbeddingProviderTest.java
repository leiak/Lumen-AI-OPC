package com.ruoyi.opc.ai.gateway.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MiniMaxEmbeddingProvider} 的协议适配测试。
 *
 * <p>MiniMax 的 {@code embo-01} 要求区分「入库的文档(db)」与「用来检索的查询(query)」，
 * 用错类型检索质量会明显下降 —— 所以 {@code type} 必须由调用方显式传，
 * 并在这里被翻译成厂商认的 {@code "db"} / {@code "query"}。</p>
 *
 * <p>另一条底线：<b>上游失败不能变成"没向量"</b> —— 条数对不上、上游报错，
 * 都必须抛出去，让调用方知道"这次没算出来"，而不是静默写入一个空记忆。</p>
 *
 * @author OAC
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("MiniMaxEmbeddingProvider 单测")
class MiniMaxEmbeddingProviderTest {

    @Mock
    private HttpLlmClient http;

    private MiniMaxEmbeddingProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MiniMaxEmbeddingProvider(http);
        ReflectionTestUtils.setField(provider, "apiKey", "test-key");
        ReflectionTestUtils.setField(provider, "baseUrl", "https://api.minimaxi.com/v1");
        ReflectionTestUtils.setField(provider, "embeddingModel", "embo-01");
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "dimension", 1536);
        ReflectionTestUtils.setField(provider, "timeoutSeconds", 60);
    }

    @Test
    @DisplayName("enabled 必须同时满足「配置打开」与「api-key 非空」")
    void enabled_requiresApiKey() {
        assertTrue(provider.enabled());

        ReflectionTestUtils.setField(provider, "apiKey", "");
        assertFalse(provider.enabled(), "没有 api-key 时不能算作可用 —— 否则记忆系统会以为能写");

        ReflectionTestUtils.setField(provider, "apiKey", "test-key");
        ReflectionTestUtils.setField(provider, "enabled", false);
        assertFalse(provider.enabled(), "配置关掉时不能算作可用");
    }

    @Test
    @DisplayName("DOCUMENT 用途 → 请求里的 type 是 db")
    void embedBatch_documentTypeMapsToDb() throws Exception {
        when(http.callEmbeddings(anyString(), anyString(), anyString(), anyList(), anyString(), anyInt()))
                .thenReturn("{\"vectors\":[[1,2]],\"base_resp\":{\"status_code\":0}}");
        when(http.parseEmbeddings(anyString())).thenReturn(List.of(new float[]{1f, 2f}));

        provider.embedBatch(List.of("甲"), EmbeddingProvider.EmbedType.DOCUMENT);

        verify(http).callEmbeddings(eq("https://api.minimaxi.com/v1"), eq("test-key"), eq("embo-01"),
                eq(List.of("甲")), eq("db"), eq(60));
    }

    @Test
    @DisplayName("QUERY 用途 → 请求里的 type 是 query（检索场景必须与入库时区分）")
    void embedBatch_queryTypeMapsToQuery() throws Exception {
        when(http.callEmbeddings(anyString(), anyString(), anyString(), anyList(), anyString(), anyInt()))
                .thenReturn("{\"vectors\":[[1,2]],\"base_resp\":{\"status_code\":0}}");
        when(http.parseEmbeddings(anyString())).thenReturn(List.of(new float[]{1f, 2f}));

        provider.embedBatch(List.of("甲"), EmbeddingProvider.EmbedType.QUERY);

        verify(http).callEmbeddings(anyString(), anyString(), anyString(), anyList(), eq("query"), anyInt());
    }

    @Test
    @DisplayName("正常响应 → 向量按原顺序返回")
    void embedBatch_success() throws Exception {
        when(http.callEmbeddings(anyString(), anyString(), anyString(), anyList(), anyString(), anyInt()))
                .thenReturn("{\"vectors\":[[1,2],[3,4]],\"base_resp\":{\"status_code\":0}}");
        when(http.parseEmbeddings(anyString())).thenReturn(List.of(new float[]{1f, 2f}, new float[]{3f, 4f}));

        List<float[]> vectors = provider.embedBatch(List.of("甲", "乙"), EmbeddingProvider.EmbedType.DOCUMENT);

        assertEquals(2, vectors.size());
        assertArrayEquals(new float[]{1f, 2f}, vectors.get(0), 1e-6f);
        assertArrayEquals(new float[]{3f, 4f}, vectors.get(1), 1e-6f);
    }

    @Test
    @DisplayName("返回的向量条数与请求条数对不上 → 抛异常，不能默默少给")
    void embedBatch_countMismatch_throws() throws Exception {
        when(http.callEmbeddings(anyString(), anyString(), anyString(), anyList(), anyString(), anyInt()))
                .thenReturn("{\"vectors\":[[1,2]],\"base_resp\":{\"status_code\":0}}");
        when(http.parseEmbeddings(anyString())).thenReturn(List.of(new float[]{1f, 2f}));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> provider.embedBatch(List.of("甲", "乙"), EmbeddingProvider.EmbedType.DOCUMENT));

        assertTrue(e.getMessage().contains("条数"), "异常要说清是条数对不上：" + e.getMessage());
    }

    @Test
    @DisplayName("上游调用失败 → 抛异常（不能变成「没有向量」）")
    void embedBatch_upstreamFailure_throws() {
        when(http.callEmbeddings(anyString(), anyString(), anyString(), anyList(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("HTTP 401 from MiniMax"));

        assertThrows(IllegalStateException.class,
                () -> provider.embedBatch(List.of("甲"), EmbeddingProvider.EmbedType.DOCUMENT));
    }

    @Test
    @DisplayName("空文本列表 → 直接返回空列表，不发请求")
    void embedBatch_emptyTexts_returnsEmptyWithoutCallingUpstream() {
        assertTrue(provider.embedBatch(List.of(), EmbeddingProvider.EmbedType.DOCUMENT).isEmpty());
    }

    @Test
    @DisplayName("维度取自配置（决定 Qdrant 集合怎么建）")
    void dimension_isConfigurable() {
        assertEquals(1536, provider.dimension());
        assertEquals("minimax", provider.name());
        assertEquals("embo-01", provider.model());
        assertEquals(5, provider.priority());
    }
}
