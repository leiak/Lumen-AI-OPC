package com.ruoyi.opc.ai.gateway.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MiniMax embedding 适配器（{@code embo-01}）。
 *
 * <p>接口：{@code POST https://api.minimaxi.com/v1/embeddings}
 * <pre>
 * 请求  {"model":"embo-01","type":"db","texts":["..."]}
 * 响应  {"vectors":[[0.01, ...]],"base_resp":{"status_code":0,"status_msg":"success"}}
 * </pre>
 *
 * <p>与聊天模型分开配置：聊天用 {@code opc.llm.minimax.model}
 * （默认 {@code MiniMax-Text-01}），向量化用 {@code opc.llm.minimax.embedding-model}
 * （默认 {@code embo-01}）。
 *
 * @author OAC
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MiniMaxEmbeddingProvider implements EmbeddingProvider {

    private final HttpLlmClient http;

    @Value("${opc.llm.minimax.api-key:}")
    private String apiKey;

    @Value("${opc.llm.minimax.base-url:https://api.minimaxi.com/v1}")
    private String baseUrl;

    @Value("${opc.llm.minimax.embedding-model:embo-01}")
    private String embeddingModel;

    @Value("${opc.llm.minimax.enabled:true}")
    private boolean enabled;

    @Value("${opc.llm.minimax.embedding-dimension:1536}")
    private int dimension;

    @Value("${opc.llm.timeout-seconds:60}")
    private int timeoutSeconds;

    @Override public String name() { return "minimax"; }
    @Override public String model() { return embeddingModel; }
    @Override public boolean enabled() { return enabled && apiKey != null && !apiKey.isEmpty(); }
    @Override public int priority() { return 5; }
    @Override public int dimension() { return dimension; }

    @Override
    public float[] embed(String text) {
        return embed(text, EmbedType.DOCUMENT);
    }

    @Override
    public float[] embed(String text, EmbedType type) {
        List<float[]> r = embedBatch(List.of(text == null ? "" : text), type);
        if (r.isEmpty()) {
            throw new IllegalStateException("MiniMax embedding 没有返回向量");
        }
        return r.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return embedBatch(texts, EmbedType.DOCUMENT);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts, EmbedType type) {
        if (texts == null || texts.isEmpty()) return List.of();
        String apiType = type == EmbedType.QUERY ? "query" : "db";
        try {
            String raw = http.callEmbeddings(baseUrl, apiKey, embeddingModel, texts, apiType, timeoutSeconds);
            List<float[]> vectors = http.parseEmbeddings(raw);
            if (vectors.size() != texts.size()) {
                throw new IllegalStateException("MiniMax embedding 返回的向量条数(" + vectors.size()
                        + ")与请求条数(" + texts.size() + ")对不上");
            }
            log.debug("[MiniMax-Embedding] model={} type={} texts={} dim={}",
                    embeddingModel, apiType, texts.size(),
                    vectors.isEmpty() ? 0 : vectors.get(0).length);
            return vectors;
        } catch (Exception e) {
            log.error("[MiniMax-Embedding] 调用失败 model={} type={}", embeddingModel, apiType, e);
            throw new IllegalStateException("MiniMax embedding 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 自检用：确认这个 provider 真的能出向量（而不是配置看起来对、实际调不通）。
     */
    public List<float[]> selfCheck() {
        return embedBatch(new ArrayList<>(List.of("opc 记忆系统自检")), EmbedType.QUERY);
    }
}
