package com.ruoyi.opc.ai.gateway.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpLlmClient} 的 embedding 协议解析测试。
 *
 * <p>MiniMax 的 {@code /v1/embeddings} <b>不是</b> OpenAI 的
 * {@code input}/{@code data[].embedding} 结构，而且 HTTP 200 不代表业务成功 ——
 * 失败信息藏在 {@code base_resp.status_code} 里。</p>
 *
 * <p>本测试钉住"不说假话"的两条底线：
 * <ol>
 *   <li><b>业务失败必须抛</b> —— {@code status_code != 0} 时不能静默返回空列表，
 *       否则上层会把"向量库检索不到"误判成"确实没有相关记忆"；</li>
 *   <li><b>响应结构不对必须抛</b> —— 缺 {@code vectors} 字段同样不能返回空。</li>
 * </ol>
 *
 * @author OAC
 */
@DisplayName("HttpLlmClient embedding 解析 单测")
class HttpLlmClientEmbeddingTest {

    private HttpLlmClient client;

    @BeforeEach
    void setUp() {
        client = new HttpLlmClient();
    }

    @Test
    @DisplayName("正常响应 → 解析出 2 个向量，维度正确")
    void parseEmbeddings_success() throws Exception {
        String raw = "{\"vectors\":[[1,2],[3,4]],\"base_resp\":{\"status_code\":0,\"status_msg\":\"success\"}}";

        List<float[]> vectors = client.parseEmbeddings(raw);

        assertEquals(2, vectors.size(), "应解析出 2 个向量");
        assertArrayEquals(new float[]{1f, 2f}, vectors.get(0), 1e-6f);
        assertArrayEquals(new float[]{3f, 4f}, vectors.get(1), 1e-6f);
    }

    @Test
    @DisplayName("浮点维度也能解析")
    void parseEmbeddings_floatValues() throws Exception {
        String raw = "{\"vectors\":[[0.1,0.25]],\"base_resp\":{\"status_code\":0,\"status_msg\":\"success\"}}";

        List<float[]> vectors = client.parseEmbeddings(raw);

        assertEquals(1, vectors.size());
        assertEquals(2, vectors.get(0).length);
        assertArrayEquals(new float[]{0.1f, 0.25f}, vectors.get(0), 1e-6f);
    }

    @Test
    @DisplayName("base_resp.status_code != 0 → 抛异常，不能静默返回空向量")
    void parseEmbeddings_businessFailure_throws() {
        String raw = "{\"vectors\":[[1,2]],\"base_resp\":{\"status_code\":1004,\"status_msg\":\"api key invalid\"}}";

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> client.parseEmbeddings(raw));

        assertTrue(e.getMessage().contains("1004"), "异常要带上业务错误码：" + e.getMessage());
        assertTrue(e.getMessage().contains("api key invalid"), "异常要带上业务错误信息：" + e.getMessage());
    }

    @Test
    @DisplayName("响应里没有 vectors 字段 → 抛异常")
    void parseEmbeddings_missingVectors_throws() {
        String raw = "{\"base_resp\":{\"status_code\":0,\"status_msg\":\"success\"}}";

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> client.parseEmbeddings(raw));

        assertTrue(e.getMessage().contains("vectors"), "异常要说清缺了什么：" + e.getMessage());
    }

    @Test
    @DisplayName("vectors 不是数组 → 抛异常")
    void parseEmbeddings_vectorsNotArray_throws() {
        String raw = "{\"vectors\":\"oops\",\"base_resp\":{\"status_code\":0}}";

        assertThrows(IllegalStateException.class, () -> client.parseEmbeddings(raw));
    }

    @Test
    @DisplayName("vectors 是空数组 → 返回空列表（不是异常，语义上就是没有向量）")
    void parseEmbeddings_emptyVectors_returnsEmptyList() throws Exception {
        String raw = "{\"vectors\":[],\"base_resp\":{\"status_code\":0,\"status_msg\":\"success\"}}";

        assertTrue(client.parseEmbeddings(raw).isEmpty());
    }

    @Test
    @DisplayName("没有 base_resp 字段 → 按成功处理（协议变了也不该静默吞掉向量）")
    void parseEmbeddings_withoutBaseResp_stillParses() throws Exception {
        String raw = "{\"vectors\":[[1,2]]}";

        List<float[]> vectors = client.parseEmbeddings(raw);

        assertEquals(1, vectors.size());
        assertArrayEquals(new float[]{1f, 2f}, vectors.get(0), 1e-6f);
    }

    @Test
    @DisplayName("请求文本为空 → 直接拒绝，不发 HTTP")
    void callEmbeddings_emptyTexts_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> client.callEmbeddings("https://api.minimaxi.com/v1", "key", "embo-01",
                        List.of(), "db", 10));
        assertThrows(IllegalArgumentException.class,
                () -> client.callEmbeddings("https://api.minimaxi.com/v1", "key", "embo-01",
                        null, "db", 10));
    }
}
